package com.xebolax.rescate.guia

import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import java.util.Locale

/**
 * Comparación de textos de la pantalla sin Android (para probarla en el
 * ordenador): sin mayúsculas, tildes, espacios de más ni puntos suspensivos.
 */
object TextoPantalla {
	fun normalizar(texto: CharSequence?): String {
		if (texto == null) return ""
		val sinTildes = Normalizer.normalize(texto.toString(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
		return sinTildes.lowercase(Locale.ROOT)
			.replace(' ', ' ')
			.replace("…", "")
			.replace("...", "")
			.replace(Regex("[\"“”«»]"), "")
			.replace(Regex("\\s+"), " ")
			.trim()
	}

	/**
	 * Cómo de bien encaja [texto] con alguno de [candidatos]: 2 si es igual, 1 si
	 * lo contiene (y el candidato tiene al menos 3 letras), 0 si no.
	 */
	fun encaje(texto: CharSequence?, candidatos: Collection<String>): Int {
		val t = normalizar(texto)
		if (t.isEmpty()) return 0
		var mejor = 0
		for (c in candidatos) {
			val n = normalizar(c)
			if (n.isEmpty()) continue
			if (t == n) return 2
			if (n.length >= 3 && t.contains(n)) mejor = 1
		}
		return mejor
	}
}

/** Búsqueda de elementos en los árboles de accesibilidad de las ventanas. */
object BuscadorNodos {
	private const val MAXIMO_NODOS = 3000

	/** El mejor nodo de [raices] cuyo texto o descripción encaja con [candidatos] (igual antes que contiene). */
	fun buscar(raices: List<AccessibilityNodeInfo>, candidatos: Collection<String>, soloExacto: Boolean = false, excepto: AccessibilityNodeInfo? = null): AccessibilityNodeInfo? {
		var parcial: AccessibilityNodeInfo? = null
		for (raiz in raices) {
			var encontrado: AccessibilityNodeInfo? = null
			recorrer(raiz) { nodo ->
				if (excepto != null && nodo == excepto) return@recorrer false
				if (!nodo.isVisibleToUser) return@recorrer false
				val e = maxOf(TextoPantalla.encaje(nodo.text, candidatos), TextoPantalla.encaje(nodo.contentDescription, candidatos))
				if (e == 2) {
					encontrado = nodo
					true
				} else {
					if (e == 1 && parcial == null) parcial = nodo
					false
				}
			}
			encontrado?.let { return it }
		}
		return if (soloExacto) null else parcial
	}

	/** El nodo con el identificador de vista [id] («android:id/button1»), si está visible. */
	fun porId(raices: List<AccessibilityNodeInfo>, id: String): AccessibilityNodeInfo? {
		for (raiz in raices) {
			val lista = runCatching { raiz.findAccessibilityNodeInfosByViewId(id) }.getOrNull().orEmpty()
			lista.firstOrNull { it.isVisibleToUser }?.let { return it }
		}
		return null
	}

	/** El primer nodo de [raices] que cumple [condicion]. */
	fun primero(raices: List<AccessibilityNodeInfo>, condicion: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
		for (raiz in raices) {
			var encontrado: AccessibilityNodeInfo? = null
			recorrer(raiz) { nodo ->
				if (nodo.isVisibleToUser && condicion(nodo)) {
					encontrado = nodo
					true
				} else false
			}
			encontrado?.let { return it }
		}
		return null
	}

	/** El ancestro pulsable más cercano (o el propio nodo). */
	fun pulsable(nodo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		var actual: AccessibilityNodeInfo? = nodo
		var nivel = 0
		while (actual != null && nivel < 10) {
			if (actual.isClickable && actual.isEnabled) return actual
			actual = runCatching { actual!!.parent }.getOrNull()
			nivel++
		}
		return null
	}

	/** Un interruptor o casilla dentro de la fila de [nodo] (su ancestro pulsable), o el propio nodo si lo es. */
	fun interruptorDe(nodo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		if (nodo.isCheckable) return nodo
		val fila = pulsable(nodo) ?: nodo
		if (fila.isCheckable) return fila
		var encontrado: AccessibilityNodeInfo? = null
		recorrer(fila) { n ->
			if (n.isCheckable) {
				encontrado = n
				true
			} else false
		}
		return encontrado
	}

	/** El elemento desplazable más grande (la lista principal de la pantalla). */
	fun desplazable(raices: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
		var mejor: AccessibilityNodeInfo? = null
		var area = -1
		val rect = android.graphics.Rect()
		for (raiz in raices) recorrer(raiz) { n ->
			if (n.isScrollable && n.isVisibleToUser) {
				n.getBoundsInScreen(rect)
				val a = rect.width() * rect.height()
				if (a > area) {
					area = a
					mejor = n
				}
			}
			false
		}
		return mejor
	}

	/** Todo el texto visible de [raiz] que no es un botón, para leer un diálogo. */
	fun textoDe(raiz: AccessibilityNodeInfo, maximo: Int = 600): String {
		val sb = StringBuilder()
		recorrer(raiz) { n ->
			if (n.isVisibleToUser && !n.isClickable && !n.text.isNullOrBlank()) {
				if (sb.isNotEmpty()) sb.append(". ")
				sb.append(n.text)
			}
			sb.length > maximo
		}
		return sb.toString().take(maximo)
	}

	/**
	 * Recorre [raiz] en profundidad. [visita] devuelve true para parar. Límite de
	 * nodos para no colgarse con pantallas enormes.
	 */
	fun recorrer(raiz: AccessibilityNodeInfo, visita: (AccessibilityNodeInfo) -> Boolean) {
		val pila = ArrayDeque<AccessibilityNodeInfo>()
		pila.addLast(raiz)
		var vistos = 0
		while (pila.isNotEmpty() && vistos < MAXIMO_NODOS) {
			val nodo = pila.removeLast()
			vistos++
			if (visita(nodo)) return
			for (i in nodo.childCount - 1 downTo 0) {
				runCatching { nodo.getChild(i) }.getOrNull()?.let { pila.addLast(it) }
			}
		}
	}
}
