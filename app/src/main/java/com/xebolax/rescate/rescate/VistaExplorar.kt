/*
 * Basado en ExploreMenuView.kt de Shortcut Menu (https://github.com/aaron-gh/shortcut-menu),
 * de aaron-gh, con licencia Apache 2.0. Cambios para Rescate Accesible: opción resaltada
 * también con las teclas de volumen, texto en varias líneas para nombres largos, ayuda al
 * dejar el dedo quieto, colores de alto contraste y comentarios en español.
 */
package com.xebolax.rescate.rescate

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.xebolax.rescate.R

/**
 * El menú para cuando no hay ningún lector de pantalla, como el menú de atajos
 * de accesibilidad del iPhone. Las opciones llenan la pantalla en filas, así que
 * siempre hay una bajo el dedo. Tocar o arrastrar dice la opción que hay debajo
 * y levantar el dedo la elige. Dejar el dedo quieto un momento dice su ayuda.
 */
@SuppressLint("ViewConstructor")
class VistaExplorar(
	contexto: Context,
	private val textos: List<String>,
	private val alPasar: (Int) -> Unit,
	private val alElegir: (Int) -> Unit,
	private val alQuieto: (Int) -> Unit,
) : View(contexto) {
	/** La fila resaltada (por el dedo o por las teclas), o -1. */
	var actual = -1
		set(valor) {
			field = valor
			invalidate()
		}

	private var tocando = false
	private val manejador = Handler(Looper.getMainLooper())
	private val quieto = Runnable { if (tocando && actual >= 0) alQuieto(actual) }

	private val pintura = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = contexto.getColor(R.color.menu_texto)
		textSize = sp(26f)
	}
	private val pinturaResaltada = TextPaint(pintura).apply {
		color = contexto.getColor(R.color.menu_texto_resaltado)
		isFakeBoldText = true
	}
	private val linea = Paint().apply {
		color = contexto.getColor(R.color.menu_linea)
		strokeWidth = sp(1f)
	}
	private val resaltado = Paint().apply { color = contexto.getColor(R.color.menu_resaltado) }

	init {
		setBackgroundColor(contexto.getColor(R.color.menu_fondo))
		isHapticFeedbackEnabled = true
		// Sin lector de pantalla la vista habla sola; para un lector externo no es un elemento
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
	}

	/** La fila en [y], con las filas repartiéndose la altura por igual. */
	fun filaEn(y: Float): Int = FilasMenu.filaEn(y, height, textos.size)

	override fun onLayout(cambiado: Boolean, izquierda: Int, arriba: Int, derecha: Int, abajo: Int) {
		super.onLayout(cambiado, izquierda, arriba, derecha, abajo)
		// Que el gesto Atrás del sistema no se quede con un arrastre que empieza en el borde
		if (Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
	}

	override fun onDraw(lienzo: Canvas) {
		if (textos.isEmpty()) return
		val alto = height.toFloat() / textos.size
		val margen = sp(12f).toInt()
		textos.forEachIndexed { i, texto ->
			val arriba = i * alto
			if (i == actual) lienzo.drawRect(0f, arriba, width.toFloat(), arriba + alto, resaltado)
			if (i > 0) lienzo.drawLine(0f, arriba, width.toFloat(), arriba, linea)
			val p = TextPaint(if (i == actual) pinturaResaltada else pintura)
			val ancho = (width - 2 * margen).coerceAtLeast(1)
			// Letra más pequeña hasta que el texto quepa en su franja (con muchas opciones o fuente grande)
			var disposicion = disponer(texto, p, ancho)
			while (disposicion.height > alto * 0.92f && p.textSize > sp(12f)) {
				p.textSize = p.textSize - sp(2f)
				disposicion = disponer(texto, p, ancho)
			}
			lienzo.save()
			lienzo.translate(margen.toFloat(), arriba + (alto - disposicion.height) / 2)
			disposicion.draw(lienzo)
			lienzo.restore()
		}
	}

	private fun disponer(texto: String, p: TextPaint, ancho: Int): StaticLayout =
		StaticLayout.Builder.obtain(texto, 0, texto.length, p, ancho)
			.setAlignment(Layout.Alignment.ALIGN_CENTER)
			.setMaxLines(4)
			.setEllipsize(android.text.TextUtils.TruncateAt.END)
			.build()

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(evento: MotionEvent): Boolean {
		when (evento.actionMasked) {
			MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
				tocando = true
				val fila = filaEn(evento.y)
				// Al poner el dedo se dice siempre la opción, aunque ya estuviera resaltada con las teclas
				if (fila != actual || evento.actionMasked == MotionEvent.ACTION_DOWN) {
					actual = fila
					manejador.removeCallbacks(quieto)
					manejador.postDelayed(quieto, QUIETO_MS)
					performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
					alPasar(fila)
				}
			}
			MotionEvent.ACTION_UP -> {
				tocando = false
				manejador.removeCallbacks(quieto)
				val fila = actual
				if (fila >= 0) {
					performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
					alElegir(fila)
				}
			}
			MotionEvent.ACTION_CANCEL -> {
				tocando = false
				manejador.removeCallbacks(quieto)
			}
		}
		return true
	}

	override fun onDetachedFromWindow() {
		manejador.removeCallbacks(quieto)
		super.onDetachedFromWindow()
	}

	private fun sp(valor: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, valor, resources.displayMetrics)

	companion object {
		private const val QUIETO_MS = 1800L
	}
}

/** Dónde están las filas del menú. */
object FilasMenu {
	/** La fila en [y] de una vista de [alto] píxeles con [cuantas] filas, o -1 sin filas. */
	fun filaEn(y: Float, alto: Int, cuantas: Int): Int {
		if (cuantas <= 0 || alto <= 0) return -1
		return (y * cuantas / alto).toInt().coerceIn(0, cuantas - 1)
	}
}
