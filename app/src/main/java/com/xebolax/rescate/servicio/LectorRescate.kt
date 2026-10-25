package com.xebolax.rescate.servicio

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.rescate.R
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.voz.Senal
import com.xebolax.rescate.voz.VozEmergencia

/**
 * El lector de pantalla de rescate, básico, con la voz de rescate.
 *
 * Tiene tres modos:
 *
 * - **Apagado**: no lee nada; solo habla el menú de rescate.
 * - **Eco**: hay otro lector activo (TalkBack) pero su voz no se oye. El lector
 *   de rescate pone voz a lo que ese lector enfoca: el usuario sigue usando sus
 *   gestos de siempre y oye la voz de rescate. No necesita ningún permiso
 *   especial y no toca el otro lector.
 * - **Propio**: no hay ningún lector. Este pide la exploración táctil y es el
 *   lector: tocar o arrastrar dice lo que hay debajo, doble toque lo activa,
 *   deslizar a la derecha o a la izquierda pasa al siguiente o al anterior.
 *
 * Protecciones: no habla dos veces lo mismo seguido, corta lo anterior al
 * tocar, limita los anuncios por segundo (tormentas de eventos) y nunca guarda
 * ni registra el texto que lee.
 */
class LectorRescate(private val servicio: AccessibilityService, private val voz: () -> VozEmergencia) {
	enum class Modo { APAGADO, ECO, PROPIO }

	var modo: Modo = Modo.APAGADO
		private set

	/** Para ignorar los eventos de la propia app mientras el menú habla por sí mismo. */
	var ignorarPaquete: String? = null

	private var ultimoTexto = ""
	private var ultimoMomento = 0L
	private var ventanaAnuncios = 0L
	private var anunciosEnVentana = 0

	private fun contexto(): Context = Idioma.envolver(servicio)

	/** Cambia de modo. Quien llama actualiza la configuración del servicio (exploración táctil). */
	fun ponerModo(nuevo: Modo) {
		if (nuevo == modo) return
		modo = nuevo
		Registro.i(ETIQUETA, "Lector de rescate en modo $nuevo")
	}

	/** Los tipos de evento que necesita cada modo (el mínimo, por privacidad y batería). */
	fun tiposEvento(): Int = when (modo) {
		Modo.APAGADO -> AccessibilityEvent.TYPE_ANNOUNCEMENT
		Modo.ECO -> AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
			AccessibilityEvent.TYPE_ANNOUNCEMENT or AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
		Modo.PROPIO -> AccessibilityEvent.TYPE_VIEW_HOVER_ENTER or AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED or
			AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_ANNOUNCEMENT or AccessibilityEvent.TYPE_VIEW_CLICKED or
			AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or AccessibilityEvent.TYPE_VIEW_SCROLLED
	}

	fun alEvento(evento: AccessibilityEvent) {
		if (modo == Modo.APAGADO) return
		val paquete = evento.packageName?.toString()
		if (paquete != null && paquete == ignorarPaquete) return
		try {
			when (evento.eventType) {
				AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> voz().callar()
				AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> if (modo == Modo.PROPIO) evento.source?.let { enfocarBajoDedo(it) }
				AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> evento.source?.let { decir(DescripcionNodo.describir(contexto(), it), cortar = true) }
				AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
					val titulo = evento.text.joinToString(" ").ifBlank { evento.contentDescription?.toString().orEmpty() }
					if (titulo.isNotBlank()) decir(titulo, cortar = true)
				}
				AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
					val texto = evento.text.joinToString(" ")
					if (texto.isNotBlank()) decir(texto, cortar = false)
				}
				AccessibilityEvent.TYPE_VIEW_CLICKED -> {
					val nodo = evento.source ?: return
					// Un interruptor o una casilla: decir cómo ha quedado
					if (nodo.isCheckable) {
						nodo.refresh()
						decir(contexto().getString(if (nodo.isChecked) R.string.estado_activado else R.string.estado_desactivado), cortar = true)
					}
				}
				AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (modo == Modo.PROPIO) voz().senal(Senal.MOVER)
			}
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "Fallo al leer un evento", e)
		}
	}

	/** En modo propio, al tocar: pone el foco en el elemento bajo el dedo (y eso hace que se lea). */
	private fun enfocarBajoDedo(origen: AccessibilityNodeInfo) {
		var nodo: AccessibilityNodeInfo? = origen
		var elegido: AccessibilityNodeInfo? = null
		var nivel = 0
		while (nodo != null && nivel < 12) {
			if (DescripcionNodo.accionable(nodo)) {
				elegido = nodo
				break
			}
			if (elegido == null && DescripcionNodo.enfocable(nodo)) elegido = nodo
			nodo = runCatching { nodo!!.parent }.getOrNull()
			nivel++
		}
		val destino = elegido ?: origen
		if (destino.isAccessibilityFocused) return
		if (!destino.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)) {
			// Algunos elementos no aceptan el foco: se leen igual
			decir(DescripcionNodo.describir(contexto(), destino), cortar = true)
		}
	}

	/** Gestos en modo propio. Devuelve si lo atendió. */
	fun alGesto(gesto: Int): Boolean {
		if (modo != Modo.PROPIO) return false
		return when (gesto) {
			AccessibilityService.GESTURE_SWIPE_RIGHT -> mover(true)
			AccessibilityService.GESTURE_SWIPE_LEFT -> mover(false)
			AccessibilityService.GESTURE_SWIPE_DOWN -> desplazar(true)
			AccessibilityService.GESTURE_SWIPE_UP -> desplazar(false)
			AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT -> global(AccessibilityService.GLOBAL_ACTION_BACK)
			AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT -> global(AccessibilityService.GLOBAL_ACTION_HOME)
			AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
			AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
			AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN -> leerTodo()
			else -> false
		}
	}

	private fun global(accion: Int): Boolean {
		voz().senal(Senal.ELEGIR)
		return servicio.performGlobalAction(accion)
	}

	/** Pasa al elemento siguiente o anterior de la ventana activa, en orden de lectura. */
	fun mover(adelante: Boolean): Boolean {
		val raiz = servicio.rootInActiveWindow ?: run {
			voz().senal(Senal.ERROR)
			return true
		}
		val lista = mutableListOf<AccessibilityNodeInfo>()
		recorrer(raiz, lista, 0)
		if (lista.isEmpty()) {
			voz().senal(Senal.ERROR)
			return true
		}
		val actual = raiz.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
		val i = actual?.let { a -> lista.indexOfFirst { it == a } } ?: -1
		val siguiente = when {
			i < 0 -> if (adelante) 0 else lista.lastIndex
			adelante -> i + 1
			else -> i - 1
		}
		if (siguiente !in lista.indices) {
			// Fin de la pantalla: un pitido, como el borde de TalkBack
			voz().senal(Senal.ERROR)
			return true
		}
		val destino = lista[siguiente]
		if (!destino.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)) {
			decir(DescripcionNodo.describir(contexto(), destino), cortar = true)
		}
		return true
	}

	/** Los elementos que se leen, en orden: los pulsables con sus hijos dentro, y el texto suelto. */
	private fun recorrer(nodo: AccessibilityNodeInfo, lista: MutableList<AccessibilityNodeInfo>, nivel: Int) {
		if (nivel > 40 || lista.size > 500 || !nodo.isVisibleToUser) return
		if (DescripcionNodo.accionable(nodo)) {
			lista += nodo
			return
		}
		if (!nodo.text.isNullOrBlank() || !nodo.contentDescription.isNullOrBlank()) lista += nodo
		for (i in 0 until nodo.childCount) {
			val hijo = runCatching { nodo.getChild(i) }.getOrNull() ?: continue
			recorrer(hijo, lista, nivel + 1)
		}
	}

	/** Desplaza la lista que contiene el foco (o la primera que se pueda) hacia delante o atrás. */
	private fun desplazar(adelante: Boolean): Boolean {
		val raiz = servicio.rootInActiveWindow ?: return false
		var nodo: AccessibilityNodeInfo? = raiz.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
		while (nodo != null && !nodo.isScrollable) nodo = nodo.parent
		val desplazable = nodo ?: buscarDesplazable(raiz, 0)
		val accion = if (adelante) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
		if (desplazable?.performAction(accion) == true) {
			voz().senal(Senal.MOVER)
		} else {
			voz().senal(Senal.ERROR)
		}
		return true
	}

	private fun buscarDesplazable(nodo: AccessibilityNodeInfo, nivel: Int): AccessibilityNodeInfo? {
		if (nivel > 30) return null
		if (nodo.isScrollable && nodo.isVisibleToUser) return nodo
		for (i in 0 until nodo.childCount) {
			val hijo = runCatching { nodo.getChild(i) }.getOrNull() ?: continue
			buscarDesplazable(hijo, nivel + 1)?.let { return it }
		}
		return null
	}

	/** Lee toda la ventana activa de arriba abajo. */
	private fun leerTodo(): Boolean {
		val raiz = servicio.rootInActiveWindow ?: return false
		val lista = mutableListOf<AccessibilityNodeInfo>()
		recorrer(raiz, lista, 0)
		val texto = lista.joinToString(". ") { DescripcionNodo.describir(contexto(), it) }
		decir(texto, cortar = true, sinLimite = true)
		return true
	}

	/** Lo que hay en la pantalla ahora, para «¿Qué hay en pantalla?» del menú. */
	fun resumenPantalla(): String {
		val raiz = servicio.rootInActiveWindow ?: return ""
		val lista = mutableListOf<AccessibilityNodeInfo>()
		recorrer(raiz, lista, 0)
		return lista.take(40).joinToString(". ") { DescripcionNodo.describir(contexto(), it) }
	}

	private fun decir(texto: String, cortar: Boolean, sinLimite: Boolean = false) {
		if (texto.isBlank()) return
		val ahora = SystemClock.uptimeMillis()
		// Lo mismo otra vez enseguida (eventos repetidos): no se repite
		if (texto == ultimoTexto && ahora - ultimoMomento < REPETIDO_MS) return
		if (!sinLimite) {
			if (ahora - ventanaAnuncios > VENTANA_MS) {
				ventanaAnuncios = ahora
				anunciosEnVentana = 0
			}
			// Tormenta de anuncios: se deja de hablar hasta que se calme
			if (++anunciosEnVentana > MAXIMO_POR_VENTANA) return
		}
		ultimoTexto = texto
		ultimoMomento = ahora
		voz().hablar(texto, vaciar = cortar)
	}

	companion object {
		private const val ETIQUETA = "LectorRescate"
		private const val REPETIDO_MS = 700L
		private const val VENTANA_MS = 1000L
		private const val MAXIMO_POR_VENTANA = 6
	}
}
