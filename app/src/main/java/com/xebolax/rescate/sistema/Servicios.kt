/*
 * Basado en Services.kt de Shortcut Menu (https://github.com/aaron-gh/shortcut-menu),
 * de aaron-gh, con licencia Apache 2.0. Cambios para Rescate Accesible: dos servicios
 * propios (atajo y guardián), reiniciar un lector, activar y desactivar uno concreto,
 * servicios activados pero caídos, ajustes de voz del sistema, atajos y comentarios en español.
 */
package com.xebolax.rescate.sistema

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.servicio.ServicioAtajo
import com.xebolax.rescate.servicio.ServicioGuardian
import java.util.Locale

/** Un servicio de accesibilidad que el menú puede activar o desactivar. */
data class EntradaServicio(
	val componente: String,
	val nombre: String,
	/** Activado en los ajustes. */
	val activo: Boolean,
	/** Conectado de verdad ahora (un servicio activado puede estar caído). */
	val enMarcha: Boolean,
	val lector: Boolean,
) {
	val paquete: String get() = ListaServicios.paquete(componente)
}

/** Un motor de voz del sistema. */
data class MotorTts(val paquete: String, val nombre: String, val predeterminado: Boolean)

/**
 * Lee y cambia qué servicios de accesibilidad están activos y qué ofrece el menú.
 *
 * Cambiar servicios o el motor de voz predeterminado necesita el permiso
 * WRITE_SECURE_SETTINGS, que el usuario concede una vez (adb, depuración
 * inalámbrica o root). Sin él, todo lo de leer sigue funcionando y el rescate
 * guía por los Ajustes.
 */
class Servicios(private val contexto: Context) {
	private val gestor = contexto.getSystemService(AccessibilityManager::class.java)
	private val prefs = Preferencias(contexto)

	/** El servicio del atajo de esta app, que nunca aparece en el menú. */
	val atajo: String = ComponentName(contexto, ServicioAtajo::class.java).flattenToString()

	/** El servicio guardián de esta app. */
	val guardian: String = ComponentName(contexto, ServicioGuardian::class.java).flattenToString()

	val propios: Set<String> get() = setOf(atajo, guardian)

	/** Si el usuario concedió el permiso para cambiar servicios y ajustes seguros. */
	fun puedeEscribir(): Boolean = puedeEscribir(contexto)

	private fun esPropio(componente: String): Boolean =
		propios.any { ListaServicios.normalizar(it) == ListaServicios.normalizar(componente) }

	/**
	 * Todos los servicios instalados menos los propios, por nombre. A los que se
	 * llaman igual (TalkBack de Google y de Samsung) se les añade el fabricante.
	 * Con [enIngles], el nombre en inglés si lo tiene (para nombres que Pico no lee).
	 */
	fun instalados(enIngles: Boolean = false): List<EntradaServicio> {
		val activados = activados()
		val enMarcha = enMarcha().map { ListaServicios.normalizar(it) }.toSet()
		val infos = runCatching { gestor.installedAccessibilityServiceList }.getOrDefault(emptyList())
			.filterNot { esPropio(componenteDe(it)) }
		val nombres = NombresServicios.distintos(infos.map { info ->
			NombresServicios.Servicio(
				(if (enIngles) nombreIngles(info.resolveInfo) else null) ?: info.resolveInfo.loadLabel(contexto.packageManager).toString(),
				info.resolveInfo.serviceInfo.packageName,
			)
		})
		return infos.mapIndexed { i, info ->
			val componente = componenteDe(info)
			EntradaServicio(
				componente = componente,
				nombre = nombres[i],
				activo = ListaServicios.contiene(activados, componente),
				enMarcha = ListaServicios.normalizar(componente) in enMarcha,
				lector = esLector(info),
			)
		}.sortedBy { it.nombre.lowercase() }
	}

	/** Los lectores de pantalla instalados (sin los propios). */
	fun lectores(): List<EntradaServicio> = instalados().filter { it.lector }

	/** Los servicios que el usuario eligió para el menú. Mientras no elija, los lectores de pantalla. */
	fun elegidos(): Set<String> = prefs.serviciosMenu ?: instalados().filter { it.lector }.map { it.componente }.toSet()

	fun ponerElegidos(componentes: Set<String>) {
		prefs.serviciosMenu = componentes
	}

	/** Los servicios del menú. */
	fun entradasMenu(): List<EntradaServicio> {
		val elegidos = elegidos().map { ListaServicios.normalizar(it) }.toSet()
		return instalados().filter { ListaServicios.normalizar(it.componente) in elegidos }
	}

	/**
	 * Los servicios del menú en modo seguro, con nombres que Pico puede decir: el
	 * suyo, el inglés o, si está en un alfabeto que Pico no lee, «Lector 1»…
	 */
	fun entradasModoSeguro(nombreLector: (Int) -> String, nombreServicio: (Int) -> String): List<EntradaServicio> {
		val elegidos = elegidos().map { ListaServicios.normalizar(it) }.toSet()
		var lectores = 0
		var otros = 0
		val propias = instalados().associateBy { ListaServicios.normalizar(it.componente) }
		return instalados(enIngles = true)
			.filter { ListaServicios.normalizar(it.componente) in elegidos }
			.map { entrada ->
				val local = propias[ListaServicios.normalizar(entrada.componente)]?.nombre
				when {
					local != null && NombresServicios.pronunciable(local) -> entrada.copy(nombre = local)
					NombresServicios.pronunciable(entrada.nombre) -> entrada
					entrada.lector -> entrada.copy(nombre = nombreLector(++lectores))
					else -> entrada.copy(nombre = nombreServicio(++otros))
				}
			}
	}

	/** El paquete del motor de voz predeterminado del sistema, o null si no se sabe. */
	fun motorPredeterminado(): String? = leerSeguro(CLAVE_MOTOR)

	/**
	 * Todos los motores de voz instalados, por nombre. Con [nombreIlegible], los
	 * que Pico no puede leer se llaman «Motor 1»…
	 */
	fun motores(nombreIlegible: ((Int) -> String)? = null): List<MotorTts> {
		val predeterminado = motorPredeterminado()
		val infos = runCatching { contexto.packageManager.queryIntentServices(Intent(ACCION_TTS), 0) }.getOrDefault(emptyList())
			.filter { it.serviceInfo != null }
		val nombres = NombresServicios.distintos(infos.map {
			NombresServicios.Servicio(it.loadLabel(contexto.packageManager).toString(), it.serviceInfo.packageName)
		})
		var ilegibles = 0
		return infos.mapIndexed { i, info -> info.serviceInfo.packageName to nombres[i] }
			.sortedBy { it.second.lowercase() }
			.map { (paquete, nombre) ->
				val dicho = if (nombreIlegible != null && !NombresServicios.pronunciable(nombre)) nombreIlegible(++ilegibles) else nombre
				MotorTts(paquete, dicho, paquete == predeterminado)
			}
	}

	/** Hace de [paquete] el motor de voz predeterminado. Devuelve si pudo (comprobado leyendo el ajuste). */
	fun ponerMotorPredeterminado(paquete: String): Boolean =
		escribirSeguro(CLAVE_MOTOR, paquete) && motorPredeterminado() == paquete

	/** Velocidad y tono de la voz del sistema en por ciento (100 = normal), o null si no se pueden leer. */
	fun velocidadSistema(): Int? = leerSeguro(CLAVE_VELOCIDAD)?.toIntOrNull()
	fun tonoSistema(): Int? = leerSeguro(CLAVE_TONO)?.toIntOrNull()

	/** Devuelve la velocidad y el tono de la voz del sistema a lo normal. */
	fun normalizarVozSistema(): Boolean =
		escribirSeguro(CLAVE_VELOCIDAD, "100") && escribirSeguro(CLAVE_TONO, "100")

	/** El nombre de [componente] en inglés, si está instalado y Pico puede decirlo. */
	fun nombreDicho(componente: String): String? =
		instalados().firstOrNull { ListaServicios.normalizar(it.componente) == ListaServicios.normalizar(componente) }
			?.nombre?.takeIf { NombresServicios.pronunciable(it) }
			?: instalados(enIngles = true).firstOrNull { ListaServicios.normalizar(it.componente) == ListaServicios.normalizar(componente) }
				?.nombre?.takeIf { NombresServicios.pronunciable(it) }

	private fun nombreIngles(resolve: ResolveInfo): String? {
		val servicio = resolve.serviceInfo
		val recursos = recursosIngles(servicio.packageName) ?: return null
		fun nombre(id: Int, fijo: CharSequence?): String? =
			fijo?.toString() ?: id.takeIf { it != 0 }?.let {
				try {
					recursos.getText(it).toString()
				} catch (e: Resources.NotFoundException) {
					null
				}
			}
		return nombre(resolve.labelRes, resolve.nonLocalizedLabel)
			?: nombre(servicio.labelRes, servicio.nonLocalizedLabel)
			?: servicio.applicationInfo?.let { nombre(it.labelRes, it.nonLocalizedLabel) }
	}

	private fun recursosIngles(paquete: String): Resources? =
		try {
			val configuracion = Configuration(contexto.resources.configuration).apply { setLocale(Locale.ENGLISH) }
			contexto.createPackageContext(paquete, 0).createConfigurationContext(configuracion).resources
		} catch (e: PackageManager.NameNotFoundException) {
			null
		} catch (e: SecurityException) {
			null
		}

	/** Los servicios activados en los ajustes. */
	fun activados(): List<String> = ListaServicios.leer(leerSeguro(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))

	/** Los servicios conectados de verdad ahora mismo (los activados pueden estar caídos). */
	fun enMarcha(): List<String> =
		runCatching { gestor.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).map { componenteDe(it) } }
			.getOrDefault(emptyList())

	/** Si hay algún lector de pantalla (que no sea de esta app) en [componentes]. */
	fun hayLector(componentes: List<String>): Boolean {
		val lect = todosLectores().map { ListaServicios.normalizar(it) }.toSet()
		return componentes.any { ListaServicios.normalizar(it) in lect }
	}

	/** Si hay otro lector de pantalla conectado y explorando por tacto ahora mismo. */
	fun otroLectorHablando(): Boolean = hayLector(enMarcha()) && runCatching { gestor.isTouchExplorationEnabled }.getOrDefault(false)

	/** Lo que quedará activado tras alternar [entrada]. */
	fun despuesDeAlternar(entrada: EntradaServicio): List<String> =
		ListaServicios.alternar(activados(), entrada.componente, setOf(atajo), lectoresElegidos())

	/**
	 * Activa o desactiva [entrada] y apaga el servicio del atajo; luego llama a
	 * [hecho] con si se pudo. Cuando un lector sustituye a otro, primero se apaga
	 * el viejo y el nuevo solo se activa cuando el viejo ha soltado la exploración
	 * táctil. El atajo sigue activo entretanto para que la app siga en marcha.
	 */
	fun alternar(entrada: EntradaServicio, hecho: (Boolean) -> Unit) {
		val activados = activados()
		val despues = despuesDeAlternar(entrada)
		val antes = ListaServicios.pararAntes(activados, despues, lectoresElegidos())
		if (antes.isEmpty()) {
			hecho(escribir(despues))
			return
		}
		val parando = antes.map { ListaServicios.normalizar(it) }.toSet()
		if (!escribir(activados.filterNot { ListaServicios.normalizar(it) in parando })) {
			hecho(false)
			return
		}
		esperarParada(parando) { hecho(escribir(despuesDeAlternar(entrada))) }
	}

	/** Activa [componente] (y, si es un lector, apaga los demás). Devuelve si pudo. */
	fun activar(componente: String, hecho: (Boolean) -> Unit) {
		val lect = todosLectores()
		val esLector = ListaServicios.contiene(lect.toList(), componente)
		val activados = activados()
		val despues = if (esLector) ListaServicios.con(ListaServicios.sinLectores(activados, lect - componente), componente)
		else ListaServicios.con(activados, componente)
		val antes = ListaServicios.pararAntes(activados, despues, lect)
		if (antes.isEmpty()) {
			hecho(escribir(despues))
			return
		}
		val parando = antes.map { ListaServicios.normalizar(it) }.toSet()
		if (!escribir(activados.filterNot { ListaServicios.normalizar(it) in parando })) {
			hecho(false)
			return
		}
		esperarParada(parando) { hecho(escribir(ListaServicios.con(activados().filterNot { ListaServicios.normalizar(it) in parando }, componente))) }
	}

	/** Desactiva [componente]. Devuelve si pudo. */
	fun desactivar(componente: String): Boolean = escribir(ListaServicios.sin(activados(), componente))

	/**
	 * Reinicia [componente]: lo apaga, espera a que pare de verdad y lo vuelve a
	 * activar. Arregla muchos «TalkBack se ha quedado mudo o colgado».
	 */
	fun reiniciar(componente: String, hecho: (Boolean) -> Unit) {
		if (!puedeEscribir()) {
			hecho(false)
			return
		}
		if (!desactivar(componente)) {
			hecho(false)
			return
		}
		esperarParada(setOf(ListaServicios.normalizar(componente))) {
			hecho(escribir(ListaServicios.con(activados(), componente)))
		}
	}

	/**
	 * Apaga todos los lectores para el modo seguro y llama a [hecho] con los que
	 * apagó, cuando han parado y soltado la exploración táctil. Si no había
	 * ninguno o no se pueden apagar, enseguida y sin ninguno.
	 */
	fun apagarLectores(hecho: (List<String>) -> Unit) {
		val activados = activados()
		val despues = ListaServicios.sinLectores(activados, todosLectores())
		if (despues.size == activados.size || !escribir(despues)) {
			hecho(emptyList())
			return
		}
		val quedan = despues.map { ListaServicios.normalizar(it) }.toSet()
		val apagados = activados.filterNot { ListaServicios.normalizar(it) in quedan }
		esperarParada(apagados.map { ListaServicios.normalizar(it) }.toSet()) { hecho(apagados) }
	}

	/** Vuelve a activar [lectores] y apaga el atajo, cuando el modo seguro termina sin lector. */
	fun devolverLectores(lectores: List<String>): Boolean =
		escribir(ListaServicios.conLectoresDeVuelta(activados(), lectores, setOf(atajo)))

	/**
	 * Ejecuta [luego] cuando ninguno de [componentes] está en marcha y la
	 * exploración táctil está apagada (un momento después, para que el lector
	 * termine de soltarla), o pasados [ESPERA_PARADA_MS] en cualquier caso.
	 */
	private fun esperarParada(componentes: Set<String>, luego: () -> Unit) {
		val manejador = Handler(Looper.getMainLooper())
		val limite = SystemClock.uptimeMillis() + ESPERA_PARADA_MS
		val comprobar = object : Runnable {
			override fun run() {
				val enMarcha = enMarcha().map { ListaServicios.normalizar(it) }
				val parado = componentes.none { it in enMarcha } && !runCatching { gestor.isTouchExplorationEnabled }.getOrDefault(false)
				if (parado || SystemClock.uptimeMillis() >= limite) manejador.postDelayed(luego, ASENTAR_MS)
				else manejador.postDelayed(this, SONDEO_MS)
			}
		}
		manejador.post(comprobar)
	}

	/** Apaga el servicio del atajo (el rescate terminó). */
	fun apagarAtajo() {
		val servicio = ServicioAtajo.instancia
		if (servicio != null) {
			servicio.disableSelf()
		} else if (puedeEscribir() && ListaServicios.contiene(activados(), atajo)) {
			escribir(ListaServicios.sin(activados(), atajo))
		}
	}

	/** Lo asignado a un atajo, del ajuste seguro [clave] (como [CLAVE_ATAJO_VOLUMEN]). */
	fun objetivosAtajo(clave: String): List<String> = ListaServicios.leer(leerSeguro(clave))

	/**
	 * Con el permiso avanzado, deja el atajo de las teclas de volumen solo para
	 * Rescate Accesible, disponible en la pantalla de bloqueo y con la pregunta
	 * de la primera vez contestada. Devuelve si quedó así (comprobado leyendo).
	 */
	fun asignarAtajoVolumen(): Boolean {
		if (!puedeEscribir()) return false
		val ok = escribirSeguro(CLAVE_ATAJO_VOLUMEN, atajo) &&
			escribirSeguro(CLAVE_ATAJO_BLOQUEO, "1") &&
			escribirSeguro(CLAVE_DIALOGO_ATAJO, "1")
		return ok && objetivosAtajo(CLAVE_ATAJO_VOLUMEN).let { it.size == 1 && ListaServicios.contiene(it, atajo) }
	}

	/** Si el atajo de volumen tiene asignado el servicio del atajo de esta app. */
	fun atajoVolumenEsNuestro(): Boolean = ListaServicios.contiene(objetivosAtajo(CLAVE_ATAJO_VOLUMEN), atajo)

	/**
	 * Marca como contestada la pregunta de Android la primera vez que se usa el
	 * atajo de volumen («¿Usar atajo de accesibilidad?»). Sin lector, es un diálogo
	 * que un usuario ciego puede no oír. Devuelve si ya está marcada.
	 */
	fun saltarPreguntaAtajo(): Boolean {
		val resolver = contexto.contentResolver
		if (runCatching { Settings.Secure.getInt(resolver, CLAVE_DIALOGO_ATAJO, 0) }.getOrDefault(0) == 1) return true
		return escribirSeguro(CLAVE_DIALOGO_ATAJO, "1")
	}

	/** El nombre de [componente] si es un servicio instalado; si no, el componente. */
	fun nombreDe(componente: String): String =
		instalados().firstOrNull { ListaServicios.normalizar(it.componente) == ListaServicios.normalizar(componente) }?.nombre
			?: runCatching {
				val pm = contexto.packageManager
				pm.getApplicationInfo(ListaServicios.paquete(componente), 0).loadLabel(pm).toString()
			}.getOrDefault(componente)

	private fun escribir(componentes: List<String>): Boolean {
		if (!puedeEscribir()) return false
		val resolver = contexto.contentResolver
		return try {
			Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ListaServicios.unir(componentes))
			Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (componentes.isEmpty()) 0 else 1)
			true
		} catch (e: SecurityException) {
			Registro.e(ETIQUETA, "Android no dejó cambiar los servicios de accesibilidad", e)
			false
		}
	}

	private fun leerSeguro(clave: String): String? = leerSeguro(contexto, clave)

	private fun escribirSeguro(clave: String, valor: String): Boolean = escribirSeguro(contexto, clave, valor)

	/**
	 * Los lectores que el menú intercambia y apaga en modo seguro: los que el
	 * usuario eligió para el menú. Otros servicios que dicen ser lectores no se tocan.
	 */
	private fun lectoresElegidos(): Set<String> {
		val elegidos = elegidos().map { ListaServicios.normalizar(it) }.toSet()
		return todosLectores().filter { ListaServicios.normalizar(it) in elegidos }.toSet()
	}

	/** Todos los servicios instalados que son lectores de pantalla, elegidos o no (sin los propios). */
	fun todosLectores(): Set<String> =
		runCatching { gestor.installedAccessibilityServiceList }.getOrDefault(emptyList())
			.filter { esLector(it) }
			.map { componenteDe(it) }
			.filterNot { esPropio(it) }
			.toSet()

	companion object {
		private const val ETIQUETA = "Servicios"
		/** Settings.Secure.TTS_DEFAULT_SYNTH, TTS_DEFAULT_RATE y TTS_DEFAULT_PITCH. */
		const val CLAVE_MOTOR = "tts_default_synth"
		const val CLAVE_VELOCIDAD = "tts_default_rate"
		const val CLAVE_TONO = "tts_default_pitch"
		const val ACCION_TTS = "android.intent.action.TTS_SERVICE"
		/** Ajustes seguros ocultos en el SDK, pero legibles. */
		const val CLAVE_ATAJO_VOLUMEN = "accessibility_shortcut_target_service"
		const val CLAVE_BOTON = "accessibility_button_targets"
		const val CLAVE_ATAJO_BLOQUEO = "accessibility_shortcut_on_lock_screen"
		const val CLAVE_DIALOGO_ATAJO = "accessibility_shortcut_dialog_shown"
		private const val SONDEO_MS = 50L
		private const val ASENTAR_MS = 250L
		private const val ESPERA_PARADA_MS = 3000L

		fun puedeEscribir(contexto: Context): Boolean =
			contexto.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

		/** Lee un ajuste seguro; null si no existe o Android no deja leerlo. */
		fun leerSeguro(contexto: Context, clave: String): String? =
			try {
				Settings.Secure.getString(contexto.contentResolver, clave)
			} catch (e: SecurityException) {
				Registro.w(ETIQUETA, "Android no deja leer el ajuste $clave", e)
				null
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "Fallo al leer el ajuste $clave", e)
				null
			}

		/** Escribe un ajuste seguro si hay permiso. Devuelve si pudo. */
		fun escribirSeguro(contexto: Context, clave: String, valor: String): Boolean {
			if (!puedeEscribir(contexto)) return false
			return try {
				Settings.Secure.putString(contexto.contentResolver, clave, valor)
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "Android no dejó cambiar el ajuste $clave", e)
				false
			}
		}

		fun componenteDe(info: AccessibilityServiceInfo): String =
			ComponentName(info.resolveInfo.serviceInfo.packageName, info.resolveInfo.serviceInfo.name).flattenToString()

		/**
		 * Un lector de pantalla se queda con el tacto, así que no puede haber dos a la
		 * vez. TalkBack y sus variantes solo encienden la exploración táctil mientras
		 * funcionan, así que lo que marca a uno apagado es la capacidad de pedirla.
		 */
		fun esLector(info: AccessibilityServiceInfo): Boolean =
			info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION != 0 ||
				info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0
	}
}
