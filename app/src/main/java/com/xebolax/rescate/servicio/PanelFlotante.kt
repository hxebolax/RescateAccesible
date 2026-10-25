package com.xebolax.rescate.servicio

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.xebolax.rescate.R
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Registro

/**
 * Paneles flotantes del servicio de accesibilidad (ventanas TYPE_ACCESSIBILITY_OVERLAY),
 * encima de cualquier app y sin permiso de superposición.
 *
 * Sirven para lo que no se puede hacer con el filtro de teclas de accesibilidad,
 * que no todos los móviles entregan para las teclas de volumen: una ventana con
 * el foco recibe las teclas de volumen como cualquier app, y además sus botones
 * grandes se pueden tocar o leer con un lector.
 *
 * - **Confirmación** (pantalla completa): volumen arriba o la mitad de arriba, sí;
 *   volumen abajo o la mitad de abajo, no.
 * - **Volver al rescate** (botón pequeño arriba a la derecha, sin quitar el foco
 *   a la app de debajo) mientras se está fuera del menú durante un rescate.
 * - **Aviso** del guardián (medio panel): volumen arriba o «Abrir el rescate»
 *   lo abre; volumen abajo o «Cerrar el aviso» lo cierra; se cierra solo.
 */
class PanelFlotante(private val servicio: AccessibilityService) {
	private val gestor = servicio.getSystemService(Context.WINDOW_SERVICE) as WindowManager
	private val principal = Handler(Looper.getMainLooper())
	private var confirmacion: View? = null
	private var volver: View? = null
	private var aviso: View? = null

	private fun textos(): Context = Idioma.envolver(servicio)

	/** El contexto con el tema de la app (Material) y su idioma: el del servicio no tiene tema. */
	private fun contextoConTema(): Context = ContextThemeWrapper(Idioma.envolver(servicio), R.style.Tema_Rescate_Menu)

	private fun dp(valor: Int): Int =
		TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), servicio.resources.displayMetrics).toInt()

	private fun parametros(alto: Int, gravedad: Int, foco: Boolean, ancho: Int = WindowManager.LayoutParams.MATCH_PARENT): WindowManager.LayoutParams =
		WindowManager.LayoutParams(
			ancho,
			alto,
			WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
			(if (foco) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
				WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
				WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
			PixelFormat.TRANSLUCENT,
		).apply { gravity = gravedad }

	/** Un contenedor que recibe las teclas de volumen cuando tiene el foco. */
	@SuppressLint("ViewConstructor")
	private class Contenedor(contexto: Context, private val alTecla: (Boolean) -> Unit) : LinearLayout(contexto) {
		init {
			orientation = VERTICAL
			isFocusable = true
			isFocusableInTouchMode = true
		}

		override fun dispatchKeyEvent(evento: KeyEvent): Boolean {
			val codigo = evento.keyCode
			if (codigo != KeyEvent.KEYCODE_VOLUME_UP && codigo != KeyEvent.KEYCODE_VOLUME_DOWN) return super.dispatchKeyEvent(evento)
			if (evento.action == KeyEvent.ACTION_UP) alTecla(codigo == KeyEvent.KEYCODE_VOLUME_UP)
			return true
		}
	}

	private fun boton(contexto: Context, texto: String, tamanoSp: Float, accion: () -> Unit): MaterialButton =
		MaterialButton(contexto).apply {
			text = texto
			setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanoSp)
			minHeight = dp(56)
			setOnClickListener { accion() }
		}

	/**
	 * Pregunta a pantalla completa con dos botones que ocupan cada uno media
	 * pantalla. [alResponder] recibe true para sí; se llama una sola vez.
	 */
	fun confirmar(pregunta: String, textoSi: String, textoNo: String, alResponder: (Boolean) -> Unit) {
		quitarConfirmacion()
		var respondido = false
		val responder = { si: Boolean ->
			if (!respondido) {
				respondido = true
				quitarConfirmacion()
				alResponder(si)
			}
		}
		val contexto = contextoConTema()
		val raiz = Contenedor(contexto) { si -> responder(si) }.apply {
			setBackgroundColor(contexto.getColor(R.color.menu_fondo))
			ViewCompat.setAccessibilityPaneTitle(this, pregunta)
		}
		val pesos = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
			val m = dp(12)
			setMargins(m, m, m, m)
		}
		raiz.addView(boton(contexto, textoSi, 28f) { responder(true) }, pesos)
		raiz.addView(boton(contexto, textoNo, 28f) { responder(false) }, LinearLayout.LayoutParams(pesos))
		if (anadir(raiz, parametros(WindowManager.LayoutParams.MATCH_PARENT, Gravity.CENTER, foco = true))) {
			confirmacion = raiz
			raiz.post { raiz.requestFocus() }
		} else {
			responder(false)
		}
	}

	fun quitarConfirmacion() {
		confirmacion?.let { quitar(it) }
		confirmacion = null
	}

	/** El botón «Volver al rescate», pequeño y arriba, sin quitar el foco a la app de debajo. */
	fun mostrarVolver(alPulsar: () -> Unit) {
		if (volver != null) return
		val contexto = contextoConTema()
		val marco = FrameLayout(contexto)
		marco.addView(boton(contexto, textos().getString(R.string.panel_volver_rescate), 18f) {
			quitarVolver()
			alPulsar()
		})
		if (anadir(marco, parametros(WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END, foco = false, ancho = WindowManager.LayoutParams.WRAP_CONTENT).apply { y = dp(48) })) {
			volver = marco
		}
	}

	fun quitarVolver() {
		volver?.let { quitar(it) }
		volver = null
	}

	/**
	 * Aviso del guardián en la mitad de abajo de la pantalla, con el foco para
	 * recibir las teclas de volumen. Se cierra solo pasados [duracionMs].
	 */
	fun mostrarAviso(texto: String, alAbrir: () -> Unit, duracionMs: Long = 60_000L) {
		quitarAviso()
		val contexto = contextoConTema()
		val t = textos()
		var hecho = false
		val raiz = Contenedor(contexto) { si ->
			if (!hecho) {
				hecho = true
				quitarAviso()
				if (si) alAbrir()
			}
		}.apply {
			setBackgroundColor(contexto.getColor(R.color.menu_fondo))
			ViewCompat.setAccessibilityPaneTitle(this, texto)
			val m = dp(12)
			setPadding(m, m, m, m)
		}
		val pesos = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) }
		raiz.addView(boton(contexto, t.getString(R.string.panel_abrir_rescate), 26f) {
			if (!hecho) {
				hecho = true
				quitarAviso()
				alAbrir()
			}
		}, pesos)
		raiz.addView(boton(contexto, t.getString(R.string.panel_cerrar_aviso), 22f) {
			hecho = true
			quitarAviso()
		}, LinearLayout.LayoutParams(pesos))
		val alto = servicio.resources.displayMetrics.heightPixels / 2
		if (anadir(raiz, parametros(alto, Gravity.BOTTOM, foco = true))) {
			aviso = raiz
			raiz.post { raiz.requestFocus() }
			principal.postDelayed({ if (aviso === raiz) quitarAviso() }, duracionMs)
		}
	}

	fun quitarAviso() {
		aviso?.let { quitar(it) }
		aviso = null
	}

	fun quitarTodo() {
		quitarConfirmacion()
		quitarVolver()
		quitarAviso()
	}

	private fun anadir(vista: View, p: WindowManager.LayoutParams): Boolean =
		try {
			gestor.addView(vista, p)
			true
		} catch (e: Exception) {
			Registro.e("PanelFlotante", "No se pudo mostrar un panel flotante", e)
			false
		}

	private fun quitar(vista: View) {
		try {
			if (vista.isAttachedToWindow) gestor.removeView(vista)
		} catch (e: Exception) {
			Registro.w("PanelFlotante", "No se pudo quitar un panel flotante", e)
		}
	}
}
