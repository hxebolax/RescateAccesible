/*
 * Basado en ServiceList.kt de Shortcut Menu (https://github.com/aaron-gh/shortcut-menu),
 * de aaron-gh, con licencia Apache 2.0. Cambios para Rescate Accesible: nombres y
 * comentarios en español y varios servicios propios (atajo y guardián) en lugar de uno.
 */
package com.xebolax.rescate.sistema

/**
 * La lista de servicios de accesibilidad activados tal como la guarda Android en
 * el ajuste seguro: nombres de componente aplanados («com.ejemplo/com.ejemplo.Servicio»)
 * separados por dos puntos. El nombre de la clase puede venir abreviado («com.ejemplo/.Servicio»).
 */
object ListaServicios {
	fun leer(valor: String?): List<String> =
		valor.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }

	fun unir(componentes: List<String>): String = componentes.joinToString(":")

	/** Escribe [componente] con el nombre de clase completo, para que la forma corta y la larga sean iguales. */
	fun normalizar(componente: String): String {
		val barra = componente.indexOf('/')
		if (barra < 0) return componente
		val paquete = componente.substring(0, barra)
		val clase = componente.substring(barra + 1)
		return if (clase.startsWith(".")) "$paquete/$paquete$clase" else componente
	}

	fun paquete(componente: String): String = componente.substringBefore('/')

	fun contiene(componentes: List<String>, componente: String): Boolean {
		val buscado = normalizar(componente)
		return componentes.any { normalizar(it) == buscado }
	}

	/**
	 * Los servicios activados después de que el menú cambie [objetivo]. Los
	 * servicios propios de [salen] (el del atajo, que ya terminó) salen siempre.
	 * Activar un lector de pantalla desactiva los demás [lectores], para que
	 * nunca hablen dos a la vez.
	 */
	fun alternar(
		activados: List<String>,
		objetivo: String,
		salen: Set<String>,
		lectores: Set<String>,
	): List<String> {
		val estaba = contiene(activados, objetivo)
		val fuera = salen.map { normalizar(it) }.toSet()
		val lect = lectores.map { normalizar(it) }.toSet()
		val resultado = activados.filterNot { normalizar(it) in fuera || normalizar(it) == normalizar(objetivo) }
		if (estaba) return resultado
		val quedan = if (normalizar(objetivo) in lect) resultado.filterNot { normalizar(it) in lect } else resultado
		return quedan + objetivo
	}

	/**
	 * Los lectores que tienen que pararse antes de escribir [despues] sobre
	 * [activados]: los que están activos y dejarán de estarlo, cuando [despues]
	 * activa otro lector. Android conecta y desconecta los servicios en cualquier
	 * orden, y un lector que arranca antes de que el viejo pare pierde la
	 * exploración táctil cuando el viejo la suelta. Vacía si no hay que esperar.
	 */
	fun pararAntes(activados: List<String>, despues: List<String>, lectores: Set<String>): List<String> {
		val lect = lectores.map { normalizar(it) }.toSet()
		val desp = despues.map { normalizar(it) }.toSet()
		val act = activados.map { normalizar(it) }.toSet()
		val arranca = desp.any { it in lect && it !in act }
		if (!arranca) return emptyList()
		return activados.filter { normalizar(it) in lect && normalizar(it) !in desp }
	}

	/** Los activados con [lectores] de nuevo y los servicios de [salen] fuera (fin del modo seguro sin lector). */
	fun conLectoresDeVuelta(activados: List<String>, lectores: List<String>, salen: Set<String>): List<String> {
		val fuera = salen.map { normalizar(it) }.toSet()
		val quedan = activados.filterNot { normalizar(it) in fuera }
		return quedan + lectores.filterNot { contiene(quedan, it) }
	}

	/** Los activados sin ninguno de [lectores] (modo seguro). */
	fun sinLectores(activados: List<String>, lectores: Set<String>): List<String> {
		val lect = lectores.map { normalizar(it) }.toSet()
		return activados.filterNot { normalizar(it) in lect }
	}

	/** Los activados con [componente] añadido (si no estaba). */
	fun con(activados: List<String>, componente: String): List<String> =
		if (contiene(activados, componente)) activados else activados + componente

	/** Los activados sin [componente]. */
	fun sin(activados: List<String>, componente: String): List<String> =
		activados.filterNot { normalizar(it) == normalizar(componente) }
}
