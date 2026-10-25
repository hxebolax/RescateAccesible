package com.xebolax.rescate.reparar

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import com.xebolax.rescate.R
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Registro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** En qué punto está una instalación. */
enum class FaseInstalacion { NADA, PREPARANDO, ESPERANDO_CONFIRMACION, BIEN, MAL }

/**
 * El estado de la última instalación pedida, para que la pantalla que la lanzó
 * diga el resultado en el mismo sitio. [confirmar] es el diálogo del sistema
 * que hay que abrir si Android no dejó abrirlo desde el receptor.
 */
data class EstadoInstalacion(
	val fase: FaseInstalacion = FaseInstalacion.NADA,
	val paquete: String? = null,
	val mensaje: String? = null,
	val confirmar: Intent? = null,
)

/** El estado compartido de las instalaciones (lo actualiza [ResultadoInstalacionReceiver]). */
object Instalaciones {
	private val estadoInterno = MutableStateFlow(EstadoInstalacion())
	val estado: StateFlow<EstadoInstalacion> = estadoInterno.asStateFlow()

	fun poner(estado: EstadoInstalacion) {
		estadoInterno.value = estado
	}

	/** Olvida el resultado ya mostrado. */
	fun limpiar() {
		estadoInterno.value = EstadoInstalacion()
	}
}

/** Un fallo antes de instalar, con un mensaje claro para el usuario. */
class ReinstalacionException(mensaje: String) : IOException(mensaje)

/**
 * Reinstala una app desde una copia de seguridad ([CopiaApk]) o desde un archivo
 * que elige el usuario (.apk, o .apks, .xapk o .apkm sin cifrar, que son zip con
 * varios APK), con el instalador del sistema (PackageInstaller).
 *
 * Android siempre pide confirmar la instalación, comprueba que la firma es la
 * de la app instalada y no deja volver a una versión más antigua: para eso hay
 * que «Desinstalar actualizaciones» antes (apps de sistema como TalkBack). En
 * Android 14 o posterior puede salir además un aviso si otra tienda (Play) es
 * la dueña de las actualizaciones de esa app.
 */
class Reinstalador(contexto: Context) {
	private val app = contexto.applicationContext
	private val textos get() = Idioma.envolver(app)

	/** Si Android deja a esta app instalar aplicaciones (Android 8+ lo pide una vez). */
	fun puedeInstalar(): Boolean = Build.VERSION.SDK_INT < 26 || app.packageManager.canRequestPackageInstalls()

	/** La pantalla del sistema para permitir instalar aplicaciones desde esta app. */
	fun intentPermiso(): Intent =
		if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
		else Intent(Settings.ACTION_SECURITY_SETTINGS)

	/**
	 * Un problema que impide instalar [copia] sobre lo instalado, como texto para
	 * el usuario, o null si se puede intentar.
	 */
	fun problemaCon(copia: CopiaApk): String? {
		val instalada = CopiasApk(app).instalada(copia.paquete) ?: return null
		val codigo = CopiasApk.codigoVersion(instalada)
		if (codigo > copia.versionCode) {
			return textos.getString(R.string.instalar_version_antigua, copia.versionName, instalada.versionName.orEmpty())
		}
		return null
	}

	/** Reinstala [copia]. Comprueba antes que los archivos siguen enteros. */
	suspend fun desdeCopia(copia: CopiaApk) {
		Instalaciones.poner(EstadoInstalacion(FaseInstalacion.PREPARANDO, copia.paquete))
		val integra = withContext(Dispatchers.IO) { CopiasApk(app).integra(copia) }
		if (!integra) throw ReinstalacionException(textos.getString(R.string.instalar_copia_danada))
		problemaCon(copia)?.let { throw ReinstalacionException(it) }
		instalar(copia.apks(), copia.paquete)
	}

	/**
	 * Instala lo que contiene el archivo [uri] elegido por el usuario. Si se
	 * indica [paqueteEsperado], tiene que ser esa app. Devuelve el paquete.
	 */
	suspend fun desdeArchivo(uri: Uri, paqueteEsperado: String? = null): String {
		Instalaciones.poner(EstadoInstalacion(FaseInstalacion.PREPARANDO, paqueteEsperado))
		val apks = withContext(Dispatchers.IO) { extraer(uri) }
		val base = baseDe(apks) ?: throw ReinstalacionException(textos.getString(R.string.instalar_no_es_apk))
		val info = app.packageManager.getPackageArchiveInfo(base.path, 0)
			?: throw ReinstalacionException(textos.getString(R.string.instalar_no_es_apk))
		if (paqueteEsperado != null && info.packageName != paqueteEsperado) {
			throw ReinstalacionException(textos.getString(R.string.instalar_otra_app, info.packageName))
		}
		// El base primero; el resto, las partes divididas
		instalar(listOf(base) + apks.filter { it != base }, info.packageName)
		return info.packageName
	}

	/** Copia el archivo elegido a la caché y saca sus APK (si es un paquete de varios). */
	private fun extraer(uri: Uri): List<File> {
		val dir = File(app.cacheDir, "instalar").apply {
			deleteRecursively()
			mkdirs()
		}
		val nombre = nombreDe(uri) ?: "archivo.apk"
		val copiado = File(dir, "elegido.bin")
		val entrada = app.contentResolver.openInputStream(uri) ?: throw ReinstalacionException(textos.getString(R.string.instalar_no_se_lee))
		entrada.use { e -> copiado.outputStream().use { e.copyTo(it) } }
		val zip = try {
			ZipFile(copiado)
		} catch (e: IOException) {
			Registro.w(ETIQUETA, "El archivo $nombre no es un zip", e)
			throw ReinstalacionException(textos.getString(R.string.instalar_no_es_apk))
		}
		return zip.use { z ->
			// Un APK suelto lleva su AndroidManifest.xml en la raíz
			if (z.getEntry("AndroidManifest.xml") != null) {
				val apk = File(dir, "base.apk")
				copiado.renameTo(apk)
				return listOf(apk)
			}
			val apks = mutableListOf<File>()
			for (entrada2 in z.entries()) {
				if (entrada2.isDirectory || !entrada2.name.endsWith(".apk", ignoreCase = true)) continue
				val destino = File(dir, File(entrada2.name).name)
				// Nunca fuera de la carpeta (nombres con ../)
				if (!destino.canonicalPath.startsWith(dir.canonicalPath)) continue
				z.getInputStream(entrada2).use { e -> destino.outputStream().use { e.copyTo(it) } }
				apks += destino
			}
			copiado.delete()
			if (apks.isEmpty()) throw ReinstalacionException(textos.getString(R.string.instalar_sin_apks))
			apks
		}
	}

	/** El APK base de un paquete dividido: base.apk, o el que no es una parte de configuración. */
	private fun baseDe(apks: List<File>): File? {
		apks.firstOrNull { it.name.equals("base.apk", ignoreCase = true) }?.let { return it }
		val candidatos = apks.filterNot { it.name.startsWith("split_", true) || it.name.startsWith("config.", true) }
		return (candidatos.ifEmpty { apks }).firstOrNull { app.packageManager.getPackageArchiveInfo(it.path, 0) != null }
	}

	private fun nombreDe(uri: Uri): String? =
		runCatching {
			app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
				if (c.moveToFirst()) c.getString(0) else null
			}
		}.getOrNull()

	/**
	 * Entrega [apks] (el base primero) al instalador del sistema en una sola
	 * sesión. El resultado llega a [ResultadoInstalacionReceiver].
	 */
	suspend fun instalar(apks: List<File>, paquete: String) = withContext(Dispatchers.IO) {
		if (!puedeInstalar()) throw ReinstalacionException(textos.getString(R.string.instalar_sin_permiso))
		val instalador = app.packageManager.packageInstaller
		val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
			setAppPackageName(paquete)
			if (Build.VERSION.SDK_INT >= 26) setInstallReason(PackageManager.INSTALL_REASON_USER)
			// Android 14+: no se pide ser el dueño de las actualizaciones; la tienda que lo sea puede pedir otra confirmación
		}
		val id = instalador.createSession(parametros)
		try {
			instalador.openSession(id).use { sesion ->
				apks.forEachIndexed { i, apk ->
					ensureActive()
					val nombre = if (i == 0) "base.apk" else apk.name
					apk.inputStream().use { entrada ->
						sesion.openWrite(nombre, 0, apk.length()).use { salida ->
							entrada.copyTo(salida, 256 * 1024)
							sesion.fsync(salida)
						}
					}
				}
				val intent = Intent(app, ResultadoInstalacionReceiver::class.java).putExtra(EXTRA_PAQUETE, paquete)
				// Mutable: el instalador añade el estado al intent
				val banderas = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
				val pendiente = PendingIntent.getBroadcast(app, id, intent, banderas)
				Instalaciones.poner(EstadoInstalacion(FaseInstalacion.PREPARANDO, paquete))
				sesion.commit(pendiente.intentSender)
			}
		} catch (e: Exception) {
			runCatching { instalador.abandonSession(id) }
			throw e
		}
	}

	companion object {
		private const val ETIQUETA = "Reinstalador"
		const val EXTRA_PAQUETE = "com.xebolax.rescate.PAQUETE"
	}
}

/**
 * Recibe el resultado del instalador: abre la confirmación que pide Android o
 * deja dicho en [Instalaciones] si salió bien o por qué falló, en lenguaje claro.
 */
class ResultadoInstalacionReceiver : BroadcastReceiver() {
	override fun onReceive(contexto: Context, intent: Intent) {
		val textos = Idioma.envolver(contexto)
		val paquete = intent.getStringExtra(Reinstalador.EXTRA_PAQUETE)
		val estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
		val detalle = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
		when (estado) {
			PackageInstaller.STATUS_PENDING_USER_ACTION -> {
				val confirmar = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
				else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
				Instalaciones.poner(EstadoInstalacion(FaseInstalacion.ESPERANDO_CONFIRMACION, paquete, textos.getString(R.string.instalar_confirma), confirmar))
				if (confirmar != null) {
					try {
						contexto.startActivity(confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
					} catch (e: Exception) {
						// En segundo plano Android puede no dejar abrirlo: la pantalla lo abrirá con «confirmar»
						Registro.w("Instalacion", "No se pudo abrir la confirmación del instalador", e)
					}
				}
			}
			PackageInstaller.STATUS_SUCCESS -> {
				Registro.i("Instalacion", "Instalado $paquete")
				Instalaciones.poner(EstadoInstalacion(FaseInstalacion.BIEN, paquete, textos.getString(R.string.instalar_bien)))
			}
			else -> {
				Registro.w("Instalacion", "Instalación de $paquete fallida: estado $estado, $detalle")
				val motivo = when (estado) {
					PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.instalar_error_cancelada
					PackageInstaller.STATUS_FAILURE_BLOCKED -> R.string.instalar_error_bloqueada
					PackageInstaller.STATUS_FAILURE_CONFLICT -> R.string.instalar_error_conflicto
					PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> R.string.instalar_error_incompatible
					PackageInstaller.STATUS_FAILURE_INVALID -> R.string.instalar_error_invalida
					PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.instalar_error_espacio
					else -> R.string.instalar_error_general
				}
				val texto = textos.getString(motivo) + if (detalle.isNotBlank()) " (" + detalle + ")" else ""
				Instalaciones.poner(EstadoInstalacion(FaseInstalacion.MAL, paquete, texto))
			}
		}
	}
}
