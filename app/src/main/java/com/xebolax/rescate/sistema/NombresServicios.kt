/*
 * Basado en MenuLabels.kt de Shortcut Menu (https://github.com/aaron-gh/shortcut-menu),
 * de aaron-gh, con licencia Apache 2.0. Cambios para Rescate Accesible: nombres en español.
 */
package com.xebolax.rescate.sistema

/** Nombres de los servicios del menú, distintos cuando dos se llaman igual. */
object NombresServicios {
	/** El nombre de un servicio y el paquete de la app de la que viene. */
	data class Servicio(val nombre: String, val paquete: String)

	/**
	 * Un nombre para cada uno de [servicios], en orden. A un nombre que comparten
	 * dos o más se le añade el fabricante sacado del paquete, como «TalkBack (Samsung)»
	 * para com.samsung.android.accessibility.talkback. Si aun así coinciden, se
	 * añade el paquete entero.
	 */
	fun distintos(servicios: List<Servicio>): List<String> {
		val mismoNombre = servicios.groupingBy { it.nombre }.eachCount()
		val conFabricante = servicios.map { if (mismoNombre.getValue(it.nombre) > 1) "${it.nombre} (${fabricante(it.paquete)})" else it.nombre }
		val mismoFabricante = conFabricante.groupingBy { it }.eachCount()
		return servicios.mapIndexed { i, s ->
			if (mismoFabricante.getValue(conFabricante[i]) > 1) "${s.nombre} (${s.paquete})" else conFabricante[i]
		}
	}

	/**
	 * Si Pico, que solo lee el alfabeto latino, puede decir algo de [nombre]: si
	 * tiene una letra latina o un dígito.
	 */
	fun pronunciable(nombre: String): Boolean =
		nombre.codePoints().anyMatch {
			it in '0'.code..'9'.code ||
				(Character.isLetter(it) && Character.UnicodeScript.of(it) == Character.UnicodeScript.LATIN)
		}

	/** El fabricante en un paquete: la parte tras el dominio, como «Samsung». */
	fun fabricante(paquete: String): String {
		val partes = paquete.split('.').filter { it.isNotEmpty() }
		val nombre = if (partes.size >= 2) partes[1] else partes.firstOrNull() ?: paquete
		return nombre.replaceFirstChar { it.uppercase() }
	}
}
