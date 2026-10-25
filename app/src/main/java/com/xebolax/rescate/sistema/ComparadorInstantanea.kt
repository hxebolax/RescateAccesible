package com.xebolax.rescate.sistema

/**
 * Comparación pura (sin Android) entre la configuración guardada y la actual,
 * para poder probarla en el ordenador. [Instantanea] la convierte en frases.
 */
object ComparadorInstantanea {
	/** Claves de los ajustes que se guardan en la instantánea. */
	const val SERVICIOS = "enabled_accessibility_services"
	const val ACCESIBILIDAD = "accessibility_enabled"
	const val MOTOR = "tts_default_synth"
	const val VELOCIDAD = "tts_default_rate"
	const val TONO = "tts_default_pitch"
	const val ATAJO = "accessibility_shortcut_target_service"
	const val BOTON = "accessibility_button_targets"
	const val ATAJO_BLOQUEO = "accessibility_shortcut_on_lock_screen"
	const val IDIOMA = "idioma_sistema"
	const val MOTOR_CONOCIDO = "motor_conocido"

	/** Una diferencia entre lo guardado y lo actual. */
	sealed class Diferencia {
		/** Un servicio que estaba activado y ahora no. */
		data class FaltaServicio(val componente: String) : Diferencia()
		/** Un servicio activado ahora que no lo estaba. */
		data class SobraServicio(val componente: String) : Diferencia()
		/** Un ajuste con otro valor ([clave] es una de las constantes). */
		data class Valor(val clave: String, val antes: String?, val ahora: String?) : Diferencia()
	}

	/** Las claves de valor simple que se comparan (los servicios van aparte, componente a componente). */
	val CLAVES_VALOR = listOf(MOTOR, VELOCIDAD, TONO, ATAJO, BOTON, ATAJO_BLOQUEO, IDIOMA)

	/**
	 * Diferencias entre [guardada] y [actual]. [ignorar] son componentes que no
	 * cuentan (los servicios propios, que se encienden y apagan con el rescate).
	 * Un valor que no se pudo leer (null) en lo actual no cuenta como diferencia.
	 */
	fun comparar(guardada: Map<String, String?>, actual: Map<String, String?>, ignorar: Set<String> = emptySet()): List<Diferencia> {
		val resultado = mutableListOf<Diferencia>()
		val fuera = ignorar.map { ListaServicios.normalizar(it) }.toSet()
		val antes = ListaServicios.leer(guardada[SERVICIOS]).filterNot { ListaServicios.normalizar(it) in fuera }
		val ahora = ListaServicios.leer(actual[SERVICIOS]).filterNot { ListaServicios.normalizar(it) in fuera }
		antes.filterNot { ListaServicios.contiene(ahora, it) }.forEach { resultado += Diferencia.FaltaServicio(it) }
		ahora.filterNot { ListaServicios.contiene(antes, it) }.forEach { resultado += Diferencia.SobraServicio(it) }
		for (clave in CLAVES_VALOR) {
			// Sin el dato guardado o sin poder leer el actual no hay con qué comparar
			if (!guardada.containsKey(clave) || !actual.containsKey(clave)) continue
			// Los atajos no se filtran: tener el atajo asignado a esta app es parte de la configuración
			val a = normalizarValor(clave, guardada[clave])
			val b = normalizarValor(clave, actual[clave])
			if (a != b) resultado += Diferencia.Valor(clave, guardada[clave], actual[clave])
		}
		return resultado
	}

	/** Valores equivalentes escritos distinto («» y null; listas de componentes en otro orden o forma). */
	fun normalizarValor(clave: String, valor: String?, ignorar: Set<String> = emptySet()): String {
		val v = valor?.trim().orEmpty()
		return when (clave) {
			ATAJO, BOTON -> ListaServicios.leer(v).map { ListaServicios.normalizar(it) }.filterNot { it in ignorar }.sorted().joinToString(":")
			// Sin valor, el sistema usa 100 (normal) para la velocidad y el tono
			VELOCIDAD, TONO -> v.ifEmpty { "100" }
			ATAJO_BLOQUEO -> v.ifEmpty { "1" }
			else -> v
		}
	}

	/**
	 * La lista de servicios a escribir al restaurar: la guardada, con los propios
	 * que estén en marcha ahora ([mantener]) para no apagar el rescate a medias.
	 */
	fun serviciosARestaurar(guardada: String?, mantener: List<String>): List<String> {
		val lista = ListaServicios.leer(guardada).toMutableList()
		mantener.forEach { if (!ListaServicios.contiene(lista, it)) lista += it }
		return lista
	}
}
