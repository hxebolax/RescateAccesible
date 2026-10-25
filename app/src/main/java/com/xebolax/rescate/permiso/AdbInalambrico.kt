package com.xebolax.rescate.permiso

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.delay
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.xebolax.rescate.nucleo.Registro
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Qué pasó al intentar conceder el permiso con la depuración inalámbrica. */
enum class TipoResultadoAdb {
	/** Permiso concedido y comprobado. */
	CONCEDIDO,
	/** Este Android no tiene depuración inalámbrica (antes de Android 11). */
	NO_DISPONIBLE,
	/** No hay wifi: la depuración inalámbrica la necesita. */
	SIN_WIFI,
	/** No se encontró el puerto de vinculación (la ventana del código no está abierta). */
	SIN_PUERTO_VINCULACION,
	/** El código no es válido o la ventana de vinculación ya se cerró. */
	VINCULACION_FALLIDA,
	/** No se encontró el puerto de conexión (la depuración inalámbrica está apagada). */
	SIN_PUERTO_CONEXION,
	/** No se pudo conectar o Android no aceptó la clave: hay que vincular otra vez. */
	NO_AUTORIZADO,
	/** Se ejecutó el comando pero el permiso no quedó concedido. */
	FALLO,
}

/** Resultado con el detalle técnico (para el registro y para quien ayude). */
data class ResultadoAdb(val tipo: TipoResultadoAdb, val detalle: String = "")

/**
 * Cliente ADB integrado para concederse el permiso avanzado desde el propio
 * móvil, sin ordenador, con la depuración inalámbrica de Android 11 o posterior.
 *
 * Usa libadb-android (Muntashir Al-Islam, GPL-3.0-or-later o Apache-2.0, a
 * elegir), que implementa el protocolo ADB, la vinculación con código (SPAKE2)
 * y la búsqueda de los puertos por mDNS. Solo se conecta a este mismo móvil y
 * solo ejecuta `pm grant` para esta app. La clave y el certificado de la app
 * se crean una vez y se guardan en su almacenamiento interno.
 *
 * Pasos para el usuario: activar las opciones de desarrollador, activar la
 * depuración inalámbrica (con wifi), abrir «Vincular dispositivo con código de
 * vinculación» y escribir aquí (o en la notificación) el código de seis cifras.
 */
class AdbInalambrico(contexto: Context) {
	private val app = contexto.applicationContext

	/** Si se puede usar en este móvil (Android 11 o posterior). */
	fun disponible(): Boolean = Build.VERSION.SDK_INT >= 30

	/** Si el móvil está conectado a una red wifi (la depuración inalámbrica la necesita). */
	fun hayWifi(): Boolean = try {
		val gestor = app.getSystemService(ConnectivityManager::class.java)
		val capacidades = gestor?.getNetworkCapabilities(gestor.activeNetwork)
		capacidades?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
	} catch (e: Exception) {
		Registro.w(ETIQUETA, "No se pudo saber si hay wifi", e)
		// Si no se sabe, se intenta igualmente
		true
	}

	/**
	 * Busca por mDNS el puerto de vinculación (solo existe con la ventana del
	 * código abierta) o el de conexión. Solo acepta servicios de este mismo móvil
	 * (libadb comprueba que la dirección sea una de las suyas). Null si no aparece
	 * en [esperaMs].
	 */
	@RequiresApi(30)
	suspend fun buscarPuerto(vinculacion: Boolean, esperaMs: Long = ESPERA_MDNS_MS): Int? {
		val tipo = if (vinculacion) AdbMdns.SERVICE_TYPE_TLS_PAIRING else AdbMdns.SERVICE_TYPE_TLS_CONNECT
		var mdns: AdbMdns? = null
		try {
			return withTimeoutOrNull(esperaMs) {
				suspendCancellableCoroutine { continuacion ->
					// El aviso puede llegar varias veces y desde otro hilo: solo vale el primero
					val hecho = AtomicBoolean(false)
					val buscador = AdbMdns(app, tipo) { _, puerto ->
						if (puerto > 0 && hecho.compareAndSet(false, true)) continuacion.resume(puerto)
					}
					mdns = buscador
					continuacion.invokeOnCancellation { runCatching { buscador.stop() } }
					buscador.start()
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "Falló la búsqueda mDNS del puerto ($tipo)", e)
			return null
		} finally {
			mdns?.let { runCatching { it.stop() } }
		}
	}

	/**
	 * Todo de una vez: vincula con [codigo] (en [puertoVinculacion], o el que se
	 * encuentre), conecta (en [puertoConexion], o el que se encuentre) y concede
	 * el permiso. Si la app ya estaba vinculada y no hay código, solo conecta.
	 */
	suspend fun vincularYConceder(codigo: String?, puertoVinculacion: Int?, puertoConexion: Int?): ResultadoAdb {
		if (Build.VERSION.SDK_INT < 30) return ResultadoAdb(TipoResultadoAdb.NO_DISPONIBLE)
		if (ConcesionPermiso.concedido(app)) return ResultadoAdb(TipoResultadoAdb.CONCEDIDO)
		if (!hayWifi()) return ResultadoAdb(TipoResultadoAdb.SIN_WIFI)
		return cerrojo.withLock {
			val limpio = codigo?.filter { it.isDigit() }.orEmpty()
			if (limpio.isNotEmpty()) {
				val vinculado = vincular(limpio, puertoVinculacion)
				if (vinculado != null) return@withLock vinculado
			}
			conceder(puertoConexion)
		}
	}

	/** Vincula la app con la depuración inalámbrica. Devuelve null si fue bien, o el error. */
	@RequiresApi(30)
	private suspend fun vincular(codigo: String, puertoDado: Int?): ResultadoAdb? {
		val puerto = puertoDado?.takeIf { it in 1..65535 } ?: buscarPuerto(vinculacion = true)
			?: return ResultadoAdb(TipoResultadoAdb.SIN_PUERTO_VINCULACION)
		return withContext(Dispatchers.IO) {
			try {
				gestor().pair(HOST, puerto, codigo)
				Registro.i(ETIQUETA, "Vinculada con la depuración inalámbrica")
				marcarVinculada(app)
				null
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Código equivocado, caducado o ventana cerrada: libadb lo dice con una excepción
				Registro.w(ETIQUETA, "La vinculación falló", e)
				ResultadoAdb(TipoResultadoAdb.VINCULACION_FALLIDA, e.message.orEmpty())
			}
		}
	}

	/** Conecta, ejecuta `pm grant` y comprueba que el permiso quedó concedido. */
	@RequiresApi(30)
	private suspend fun conceder(puertoDado: Int?): ResultadoAdb = withContext(Dispatchers.IO) {
		val gestor = try {
			gestor()
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo preparar la clave de la app", e)
			return@withContext ResultadoAdb(TipoResultadoAdb.FALLO, e.message.orEmpty())
		}
		try {
			val conectado = try {
				if (puertoDado != null && puertoDado in 1..65535) gestor.connect(HOST, puertoDado)
				else gestor.connectTls(app, ESPERA_MDNS_MS)
			} catch (e: AdbPairingRequiredException) {
				Registro.w(ETIQUETA, "Hace falta vincular primero", e)
				return@withContext ResultadoAdb(TipoResultadoAdb.NO_AUTORIZADO, e.message.orEmpty())
			} catch (e: AdbAuthenticationFailedException) {
				Registro.w(ETIQUETA, "La depuración no aceptó la clave de la app", e)
				return@withContext ResultadoAdb(TipoResultadoAdb.NO_AUTORIZADO, e.message.orEmpty())
			} catch (e: InterruptedException) {
				// connectTls agota su tiempo buscando el puerto
				return@withContext ResultadoAdb(TipoResultadoAdb.SIN_PUERTO_CONEXION)
			}
			if (!conectado && !gestor.isConnected) {
				return@withContext ResultadoAdb(if (puertoDado == null) TipoResultadoAdb.SIN_PUERTO_CONEXION else TipoResultadoAdb.NO_AUTORIZADO)
			}
			val salida = ejecutar(gestor, "shell:" + ConcesionPermiso.comandoShell(app))
			if (ConcesionPermiso.concedido(app)) {
				Registro.i(ETIQUETA, "Permiso avanzado concedido con la depuración inalámbrica")
				ResultadoAdb(TipoResultadoAdb.CONCEDIDO)
			} else {
				Registro.w(ETIQUETA, "pm grant no concedió el permiso: $salida")
				ResultadoAdb(TipoResultadoAdb.FALLO, salida)
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "Falló la conexión con la depuración inalámbrica", e)
			ResultadoAdb(TipoResultadoAdb.NO_AUTORIZADO, e.message.orEmpty())
		} finally {
			// disconnect y no close: close destruye la clave privada de la app
			runCatching { gestor.disconnect() }.onFailure { Registro.w(ETIQUETA, "Fallo al desconectar", it) }
		}
	}

	/**
	 * Ejecuta [comando] en el shell del propio móvil (usuario shell de adb), sin
	 * que el usuario haga nada, si la app ya se vinculó una vez con la
	 * depuración inalámbrica. Con el permiso avanzado enciende la depuración
	 * inalámbrica si está apagada y la vuelve a apagar al terminar. Necesita
	 * wifi y que esa red esté permitida para la depuración («Permitir siempre en
	 * esta red», lo que Android pregunta la primera vez). Devuelve la salida, o
	 * null si no se pudo.
	 */
	suspend fun shell(comando: String): String? {
		if (Build.VERSION.SDK_INT < 30 || !vinculada(app) || !hayWifi()) return null
		return cerrojo.withLock { shellConCerrojo(comando) }
	}

	@RequiresApi(30)
	private suspend fun shellConCerrojo(comando: String): String? = withContext(Dispatchers.IO) {
		val estabaActiva = ConcesionPermiso.depuracionInalambricaActiva(app) == true
		var encendida = false
		var desarrolloEncendido = false
		try {
			// Sin las opciones de desarrollador encendidas no hay depuración: se encienden un momento
			if (!estabaActiva && ConcesionPermiso.concedido(app) &&
				Settings.Global.getInt(app.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 0
			) {
				Settings.Global.putInt(app.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
				desarrolloEncendido = true
			}
			if (!estabaActiva) {
				if (!ConcesionPermiso.concedido(app)) return@withContext null
				// Con el permiso avanzado (ajuste global): sin abrir Opciones de desarrollador
				Settings.Global.putInt(app.contentResolver, ConcesionPermiso.CLAVE_ADB_WIFI, 1)
				encendida = true
				val limite = System.currentTimeMillis() + ESPERA_ENCENDIDO_MS
				while (ConcesionPermiso.depuracionInalambricaActiva(app) != true && System.currentTimeMillis() < limite) delay(250)
				if (ConcesionPermiso.depuracionInalambricaActiva(app) != true) {
					Registro.w(ETIQUETA, "La depuración inalámbrica no se encendió (¿red no permitida?)")
					return@withContext null
				}
				// Un momento para que adbd anuncie su puerto
				delay(1500)
			}
			val gestor = gestor()
			try {
				val conectado = try {
					gestor.connectTls(app, ESPERA_MDNS_MS)
				} catch (e: InterruptedException) {
					false
				}
				if (!conectado && !gestor.isConnected) {
					Registro.w(ETIQUETA, "No se pudo conectar con el shell del móvil")
					return@withContext null
				}
				val salida = ejecutar(gestor, "shell:$comando")
				Registro.i(ETIQUETA, "Orden de shell hecha (${comando.substringBefore(' ')}…)")
				salida
			} finally {
				runCatching { gestor.disconnect() }.onFailure { Registro.w(ETIQUETA, "Fallo al desconectar", it) }
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "Falló una orden de shell", e)
			null
		} finally {
			if (encendida) runCatching { Settings.Global.putInt(app.contentResolver, ConcesionPermiso.CLAVE_ADB_WIFI, 0) }
				.onFailure { Registro.w(ETIQUETA, "No se pudo volver a apagar la depuración inalámbrica", it) }
			if (desarrolloEncendido) runCatching { Settings.Global.putInt(app.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) }
				.onFailure { Registro.w(ETIQUETA, "No se pudieron volver a apagar las opciones de desarrollador", it) }
		}
	}

	/** Abre el servicio [destino] y lee lo que devuelve hasta que termina (con tiempo límite). */
	private suspend fun ejecutar(gestor: AbsAdbConnectionManager, destino: String): String {
		val flujo: AdbStream = runInterruptible(Dispatchers.IO) { gestor.openStream(destino) }
		try {
			return withTimeoutOrNull(ESPERA_COMANDO_MS) {
				runInterruptible(Dispatchers.IO) {
					flujo.openInputStream().bufferedReader(Charsets.UTF_8).readText().trim()
				}
			} ?: "tiempo agotado"
		} finally {
			runCatching { flujo.close() }
		}
	}

	/** Borra la clave y el certificado de la app (para empezar de cero si la vinculación se estropea). */
	fun olvidarVinculacion() {
		synchronized(Companion) {
			instancia = null
			carpeta(app).deleteRecursively()
		}
	}

	private fun gestor(): Gestor = synchronized(Companion) {
		instancia ?: crearGestor(app).also { instancia = it }
	}

	/** El gestor de conexión de libadb con la clave y el certificado de la app. */
	private class Gestor(private val clave: PrivateKey, private val certificado: Certificate) : AbsAdbConnectionManager() {
		init {
			// Versión de Android del adbd al que se conecta: el propio móvil
			setApi(Build.VERSION.SDK_INT)
			setHostAddress(HOST)
			setTimeout(ESPERA_CONEXION_S, TimeUnit.SECONDS)
			// Si Android no reconoce la clave, que falle enseguida en vez de esperar una pregunta en pantalla
			setThrowOnUnauthorised(true)
		}

		override fun getPrivateKey(): PrivateKey = clave
		override fun getCertificate(): Certificate = certificado
		override fun getDeviceName(): String = NOMBRE_DISPOSITIVO
	}

	companion object {
		private const val ETIQUETA = "AdbInalambrico"
		/** Siempre este mismo móvil. */
		const val HOST = "127.0.0.1"
		const val NOMBRE_DISPOSITIVO = "RescateAccesible"
		const val ESPERA_MDNS_MS = 15_000L
		private const val ESPERA_COMANDO_MS = 20_000L
		private const val ESPERA_CONEXION_S = 15L
		private val cerrojo = Mutex()
		@Volatile private var instancia: Gestor? = null

		private fun carpeta(contexto: Context) = File(contexto.noBackupFilesDir, "adb")
		private const val ESPERA_ENCENDIDO_MS = 6000L

		/** Si la app se vinculó alguna vez con la depuración inalámbrica de este móvil. */
		fun vinculada(contexto: Context): Boolean = File(carpeta(contexto.applicationContext), "vinculada").exists()

		private fun marcarVinculada(contexto: Context) {
			runCatching { File(carpeta(contexto), "vinculada").apply { parentFile?.mkdirs() }.writeText("1") }
		}

		/** Carga la clave y el certificado guardados o los crea (RSA 2048, autofirmado con BouncyCastle). */
		private fun crearGestor(contexto: Context): Gestor {
			val dir = carpeta(contexto).apply { mkdirs() }
			val archivoClave = File(dir, "clave.pk8")
			val archivoCertificado = File(dir, "certificado.der")
			if (archivoClave.exists() && archivoCertificado.exists()) {
				try {
					val clave = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(archivoClave.readBytes()))
					val certificado = archivoCertificado.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
					return Gestor(clave, certificado)
				} catch (e: Exception) {
					Registro.w(ETIQUETA, "La clave guardada no sirve; se crea otra (habrá que vincular de nuevo)", e)
				}
			}
			val generador = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
			val par = generador.generateKeyPair()
			val nombre = X500Name("CN=$NOMBRE_DISPOSITIVO")
			val ahora = Date()
			val hasta = Date(ahora.time + 3650L * 24 * 60 * 60 * 1000)
			val firmante = JcaContentSignerBuilder("SHA256withRSA").build(par.private)
			val portador = JcaX509v3CertificateBuilder(nombre, BigInteger.valueOf(ahora.time), ahora, hasta, nombre, par.public).build(firmante)
			val certificado = JcaX509CertificateConverter().getCertificate(portador)
			// Se escriben enteros antes de ocupar su sitio (una clave a medias no serviría)
			archivoClave.delete()
			archivoCertificado.delete()
			File(dir, "clave.pk8.parcial").apply { writeBytes(par.private.encoded) }.renameTo(archivoClave)
			File(dir, "certificado.der.parcial").apply { writeBytes(certificado.encoded) }.renameTo(archivoCertificado)
			return Gestor(par.private, certificado)
		}
	}
}
