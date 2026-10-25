package com.xebolax.rescate.servicio

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.rescate.SesionRescate

/**
 * Lo común a los dos servicios de accesibilidad de la app (atajo y guardián):
 * el lector de rescate, el filtro de teclas de volumen y la configuración
 * dinámica (exploración táctil y teclas solo cuando hacen falta).
 */
abstract class ServicioBase : AccessibilityService() {
	lateinit var lector: LectorRescate
		private set

	/** Paneles flotantes: confirmaciones, «Volver al rescate» y avisos (reciben las teclas de volumen). */
	val panel: PanelFlotante by lazy { PanelFlotante(this) }

	/** Banderas declaradas en el XML del servicio, a las que se suman las dinámicas. */
	private var banderasBase = 0

	/** Si ahora se reciben las teclas de volumen (solo en rescate o con el lector caído). */
	var filtrandoTeclas = false
		private set

	/**
	 * Quien quiere las teclas de volumen antes que nadie (el asistente de ajustes
	 * esperando una confirmación). Devuelve true si se queda con la tecla.
	 */
	var oyenteTeclas: ((KeyEvent) -> Boolean)? = null

	override fun onServiceConnected() {
		super.onServiceConnected()
		lector = LectorRescate(this) { SesionRescate.voz(this) }
		banderasBase = serviceInfo?.flags ?: 0
		actualizarInfo()
	}

	/** Cambia el modo del lector de rescate y la configuración que necesita. */
	fun ponerModoLector(modo: LectorRescate.Modo) {
		if (!::lector.isInitialized) return
		lector.ponerModo(modo)
		actualizarInfo()
	}

	/** Recibir o no las teclas de volumen. */
	fun filtrarTeclas(si: Boolean) {
		if (filtrandoTeclas == si) return
		filtrandoTeclas = si
		actualizarInfo()
	}

	/**
	 * Aplica la configuración del servicio según el estado: la exploración táctil
	 * solo en el modo propio del lector, las teclas solo cuando hacen falta y los
	 * eventos mínimos de cada modo.
	 */
	protected fun actualizarInfo() {
		val info = serviceInfo ?: return
		var banderas = banderasBase
		if (lector.modo == LectorRescate.Modo.PROPIO) banderas = banderas or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
		else banderas = banderas and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
		if (filtrandoTeclas || oyenteTeclas != null) banderas = banderas or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
		else banderas = banderas and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
		info.flags = banderas
		if (com.xebolax.rescate.BuildConfig.DEBUG) android.util.Log.d("ServicioBase", "Configuración: teclas=${banderas and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0} tacto=${banderas and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0} capacidades=${info.capabilities}")
		info.eventTypes = lector.tiposEvento() or eventosPropios()
		try {
			serviceInfo = info
		} catch (e: Exception) {
			Registro.e("ServicioBase", "No se pudo actualizar la configuración del servicio", e)
		}
	}

	/**
	 * Eventos que el servicio necesita además de los del lector. Durante un
	 * rescate, los cambios de ventana y de contenido: la caché de nodos de
	 * Android solo se actualiza con los eventos que recibe el servicio, y sin
	 * ellos la guía por los Ajustes vería pantallas viejas.
	 */
	protected open fun eventosPropios(): Int = if (SesionRescate.activa) EVENTOS_VENTANA else 0

	/** Para que [oyenteTeclas] tenga efecto enseguida. */
	fun refrescarTeclas() = actualizarInfo()

	override fun onAccessibilityEvent(evento: AccessibilityEvent?) {
		if (evento == null || !::lector.isInitialized) return
		lector.alEvento(evento)
		alEvento(evento)
	}

	protected open fun alEvento(evento: AccessibilityEvent) {}

	override fun onKeyEvent(evento: KeyEvent): Boolean {
		// Solo las teclas de volumen; las demás siguen su camino sin mirarlas
		if (com.xebolax.rescate.BuildConfig.DEBUG) android.util.Log.d("ServicioBase", "Tecla ${evento.keyCode} ${evento.action} (oyente: ${oyenteTeclas != null})")
		if (evento.keyCode != KeyEvent.KEYCODE_VOLUME_UP && evento.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
		oyenteTeclas?.let { if (it(evento)) return true }
		return alTecla(evento)
	}

	/** Teclas de volumen para el servicio concreto. Devuelve true si se queda con ella. */
	protected open fun alTecla(evento: KeyEvent): Boolean = false

	@Deprecated("Obsoleto en Java (API 33)")
	override fun onGesture(gesto: Int): Boolean {
		if (!::lector.isInitialized) return false
		// Con el lector de rescate fuera del menú: izquierda y luego derecha vuelve al menú
		if (gesto == GESTURE_SWIPE_LEFT_AND_RIGHT && SesionRescate.activa) {
			SesionRescate.empezar(this, SesionRescate.origen)
			return true
		}
		return lector.alGesto(gesto)
	}

	override fun onUnbind(intent: android.content.Intent?): Boolean {
		runCatching { panel.quitarTodo() }
		return super.onUnbind(intent)
	}

	override fun onInterrupt() {
		SesionRescate.vozSiExiste()?.callar()
	}

	companion object {
		val EVENTOS_VENTANA: Int = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
			AccessibilityEvent.TYPE_WINDOWS_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED

		/** Bandera FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK (API 30). */
		val DIALOGO_HABLADO: Int = if (Build.VERSION.SDK_INT >= 30) AccessibilityServiceInfo.FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK else 0
	}
}
