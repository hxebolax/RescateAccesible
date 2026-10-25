package com.xebolax.rescate.guia

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import com.xebolax.rescate.nucleo.Registro

/**
 * Los textos de los botones de Ajustes en el idioma actual del móvil, sacados
 * de los recursos del propio paquete de Ajustes (o de Android, o del
 * instalador). Así el asistente encuentra «Forzar detención» aunque el idioma
 * del móvil sea otro. Si un fabricante cambió los nombres, se usan además las
 * listas de respaldo en español e inglés de [Textos].
 */
class CadenasSistema(private val contexto: Context) {
	private val cache = HashMap<String, Resources?>()

	private fun recursos(paquete: String): Resources? = cache.getOrPut(paquete) {
		try {
			if (paquete == "android") Resources.getSystem() else contexto.packageManager.getResourcesForApplication(paquete)
		} catch (e: PackageManager.NameNotFoundException) {
			null
		} catch (e: Exception) {
			Registro.w("CadenasSistema", "No se pudieron leer los recursos de $paquete", e)
			null
		}
	}

	/**
	 * Los textos de los recursos [nombres] de [paquete] (los que existan). Si un
	 * texto lleva un hueco («Usar %1$s»), se rellena con [argumento].
	 */
	fun de(paquete: String, nombres: List<String>, argumento: String? = null): List<String> {
		val r = recursos(paquete) ?: return emptyList()
		val lista = mutableListOf<String>()
		for (nombre in nombres) {
			try {
				val id = r.getIdentifier(nombre, "string", paquete)
				if (id == 0) continue
				var texto = r.getString(id)
				if (texto.contains('%')) {
					texto = if (argumento != null) runCatching { String.format(texto, argumento) }.getOrDefault(texto) else continue
				}
				if (texto.isNotBlank()) lista += texto
			} catch (e: Resources.NotFoundException) {
				// Ese nombre no existe en esta versión de Ajustes: se prueba el siguiente
			} catch (e: Exception) {
				Registro.w("CadenasSistema", "Fallo al leer el recurso $nombre de $paquete", e)
			}
		}
		return lista
	}

	/** Los textos del sistema para [que], seguidos de los de respaldo en español e inglés. */
	fun para(que: Textos.Boton, argumento: String? = null): List<String> {
		val lista = LinkedHashSet<String>()
		for ((paquete, nombres) in que.recursos) lista += de(paquete, nombres, argumento)
		lista += que.respaldo.map { if (argumento != null) it.replace("%s", argumento) else it }.filterNot { it.contains("%s") }
		return lista.toList()
	}
}

/** Lo que el asistente busca en pantalla, con los recursos del sistema y textos de respaldo. */
object Textos {
	const val AJUSTES = "com.android.settings"
	const val ANDROID = "android"
	private val INSTALADORES = listOf("com.google.android.packageinstaller", "com.android.packageinstaller")

	class Boton(val recursos: List<Pair<String, List<String>>>, val respaldo: List<String>)

	val FORZAR_DETENCION = Boton(listOf(AJUSTES to listOf("force_stop")), listOf("Forzar detención", "Forzar cierre", "Forzar detener", "Force stop"))
	val DESINSTALAR_ACTUALIZACIONES = Boton(listOf(AJUSTES to listOf("app_factory_reset")), listOf("Desinstalar actualizaciones", "Uninstall updates"))
	val HABILITAR = Boton(listOf(AJUSTES to listOf("enable_text")), listOf("Habilitar", "Activar", "Enable", "Turn on"))
	val ALMACENAMIENTO = Boton(
		listOf(AJUSTES to listOf("storage_settings_for_app_title", "storage_label", "storage_settings", "storage_use")),
		listOf("Almacenamiento y caché", "Almacenamiento", "Storage & cache", "Storage and cache", "Storage"),
	)
	val BORRAR_CACHE = Boton(listOf(AJUSTES to listOf("clear_cache_btn_text")), listOf("Borrar caché", "Vaciar caché", "Borrar la caché", "Clear cache"))
	val MAS_OPCIONES = Boton(listOf(ANDROID to listOf("action_menu_overflow_description")), listOf("Más opciones", "More options"))
	val ACEPTAR = Boton(
		listOf(AJUSTES to listOf("dlg_ok"), ANDROID to listOf("ok", "yes")),
		listOf("Aceptar", "OK", "Sí", "Yes"),
	)
	val PERMITIR = Boton(
		listOf(ANDROID to listOf("accessibility_dialog_button_allow"), AJUSTES to listOf("accessibility_dialog_button_allow", "allow")),
		listOf("Permitir", "Allow", "Aceptar", "OK"),
	)
	val DETENER = Boton(
		listOf(ANDROID to listOf("accessibility_dialog_button_stop"), AJUSTES to listOf("accessibility_dialog_button_stop", "disable_service_stop", "stop")),
		listOf("Detener", "Desactivar", "Stop", "Turn off", "Aceptar", "OK"),
	)
	val USAR_SERVICIO = Boton(
		listOf(AJUSTES to listOf("accessibility_service_primary_switch_title", "accessibility_service_master_switch_title")),
		listOf("Usar %s", "Use %s"),
	)
	val SERVICIOS_INSTALADOS = Boton(
		listOf(AJUSTES to listOf("user_installed_services_category_title", "accessibility_services_title", "accessibility_screen_reader_category_title")),
		listOf("Aplicaciones descargadas", "Aplicaciones instaladas", "Servicios instalados", "Servicios descargados", "Lectores de pantalla", "Downloaded apps", "Installed apps", "Installed services", "Downloaded services", "Screen readers"),
	)
	val MOTOR_PREFERIDO = Boton(
		listOf(AJUSTES to listOf("tts_engine_preference_title", "tts_engine_preference_section_title")),
		listOf("Motor preferido", "Motor de texto a voz preferido", "Motor TTS preferido", "Preferred engine"),
	)
	val INSTALAR = Boton(
		INSTALADORES.map { it to listOf("install", "update", "reinstall") },
		listOf("Instalar", "Actualizar", "Reinstalar", "Install", "Update", "Reinstall"),
	)
}
