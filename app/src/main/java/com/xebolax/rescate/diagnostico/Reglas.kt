package com.xebolax.rescate.diagnostico

/**
 * Reglas del diagnóstico sin Android (se prueban en el ordenador): a partir de
 * datos ya leídos, deciden si algo es un problema y cuán grave.
 */
object Reglas {
	/** Por debajo de este volumen (por ciento) se avisa; a cero es grave. */
	const val VOLUMEN_BAJO = 25
	/** Velocidad y tono del sistema fuera de estos márgenes (100 = normal) suenan mal. */
	val VELOCIDAD_NORMAL = 50..300
	val TONO_NORMAL = 50..200
	/** Escala de letra a partir de la que se menciona (información). */
	const val LETRA_GRANDE = 1.6f

	/** Estado de un lector según los ajustes y el sistema. */
	enum class EstadoLector { APAGADO, ACTIVO, CAIDO }

	fun estadoLector(activado: Boolean, enMarcha: Boolean): EstadoLector = when {
		activado && enMarcha -> EstadoLector.ACTIVO
		activado -> EstadoLector.CAIDO
		else -> EstadoLector.APAGADO
	}

	/** La gravedad del volumen: null si está bien. */
	fun gravedadVolumen(porCiento: Int): Gravedad? = when {
		porCiento < 0 -> null
		porCiento == 0 -> Gravedad.GRAVE
		porCiento < VOLUMEN_BAJO -> Gravedad.AVISO
		else -> null
	}

	/** Si la velocidad del sistema (en por ciento, null si no se sabe) suena mal. */
	fun velocidadExtrema(valor: Int?): Boolean = valor != null && valor !in VELOCIDAD_NORMAL

	fun tonoExtremo(valor: Int?): Boolean = valor != null && valor !in TONO_NORMAL

	/** Cómo está asignado el atajo de volumen respecto al servicio propio [propio]. */
	enum class EstadoAtajo { SIN_ASIGNAR, OTRO, SOLO_NUESTRO, COMPARTIDO }

	fun estadoAtajo(objetivos: List<String>, propio: String, normalizar: (String) -> String = { it }): EstadoAtajo {
		val nuestro = objetivos.any { normalizar(it) == normalizar(propio) }
		return when {
			objetivos.isEmpty() -> EstadoAtajo.SIN_ASIGNAR
			!nuestro -> EstadoAtajo.OTRO
			objetivos.size == 1 -> EstadoAtajo.SOLO_NUESTRO
			else -> EstadoAtajo.COMPARTIDO
		}
	}

	/**
	 * El atajo en la pantalla de bloqueo: la clave vale 1 o 0; sin valor, Android
	 * lo deja activo cuando ya se mostró la pregunta de la primera vez.
	 */
	fun atajoEnBloqueo(valor: String?, preguntaMostrada: Boolean): Boolean = when (valor?.trim()) {
		"1" -> true
		"0" -> false
		else -> preguntaMostrada
	}

	/** El balance (de -1 a 1) deja un oído casi sin sonido. */
	fun balanceExtremo(valor: Float?): Boolean = valor != null && kotlin.math.abs(valor) >= EstadoSonido.BALANCE_EXTREMO

	/**
	 * El idioma del sistema es un problema si no es el de la app ni uno que se
	 * pueda oír con la voz de rescate (español o inglés), o si cambió desde la
	 * configuración guardada.
	 */
	fun idiomaRaro(sistema: String, app: String, guardado: String?): Boolean {
		val s = sistema.substringBefore('-').lowercase()
		val g = guardado?.substringBefore('-')?.lowercase()
		if (g != null && g.isNotEmpty() && g != s) return true
		return s != app.lowercase() && s !in setOf("es", "en")
	}

	/** El primer hallazgo grave de [hallazgos] por orden de prioridad, o null. */
	fun masGrave(hallazgos: List<Hallazgo>): Hallazgo? = hallazgos.firstOrNull { it.gravedad == Gravedad.GRAVE }
}
