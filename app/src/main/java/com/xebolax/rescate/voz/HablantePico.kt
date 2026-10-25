/*
 * Basado en PicoSpeaker (Speech.kt) de Shortcut Menu (https://github.com/aaron-gh/shortcut-menu),
 * de aaron-gh, con licencia Apache 2.0. Cambios para Rescate Accesible: voz según el idioma
 * (español incluido), frases cortas para cortar y empezar antes, velocidad y tono, salida
 * por el altavoz si los auriculares Bluetooth son el problema y aviso de fallo para cambiar
 * de motor.
 */
package com.xebolax.rescate.voz

import android.content.Context
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro

/**
 * Habla con Pico, el motor incluido en la app: no depende del motor de voz del
 * sistema, de Google ni de Internet. Sintetiza cada frase en el hilo de sonido
 * (más rápido de lo que suena) y la reproduce en su propia pista.
 */
class HablantePico(
	contexto: Context,
	private val cola: ColaAudio,
	private val salida: SalidaAudio,
	/** La voz; null = la del idioma de la app en cada momento. */
	private val vozFija: VozPico? = null,
) : Hablante {
	private val app = contexto.applicationContext
	private val prefs = Preferencias(app)
	/** Solo se toca desde el hilo de sonido. */
	private var pico: PicoNativo? = null

	override val nombre: String get() = "Pico"

	private fun voz(): VozPico = vozFija ?: VozPico.para(com.xebolax.rescate.nucleo.Idioma.efectivo(app))

	/** Pico abierto con la voz que toca. Solo desde el hilo de sonido. */
	private fun motor(): PicoNativo? {
		val voz = voz()
		pico?.let { if (it.voz == voz) return it else it.cerrar() }
		pico = PicoNativo.abrir(app, voz)
		return pico
	}

	/** Comprueba en el hilo de sonido que Pico abre y produce sonido; [listo] llega al hilo principal. */
	fun comprobar(listo: (Boolean) -> Unit) {
		// Sin generación: una tecla pulsada mientras tanto no debe dar la prueba por fallida
		cola.calcular({
			try {
				val muestras = motor()?.sintetizar(Marcado.preparar(FRASE_PRUEBA, 100, 100))
				muestras != null && muestras.isNotEmpty()
			} catch (e: Exception) {
				Registro.e("HablantePico", "Fallo al comprobar Pico", e)
				false
			}
		}, listo)
	}

	override fun hablar(texto: String, vaciar: Boolean, alTerminar: ((Resultado) -> Unit)?) {
		val velocidad = prefs.velocidad
		val tono = prefs.tono
		cola.encolar(vaciar, { sigue -> decir(texto, velocidad, tono, sigue) }, alTerminar)
	}

	private fun decir(texto: String, velocidad: Int, tono: Int, sigue: () -> Boolean): Resultado {
		val motor = motor() ?: return Resultado.FALLO
		for (frase in Marcado.frases(Marcado.limpiar(texto))) {
			if (!sigue()) return Resultado.CORTADO
			val muestras = motor.sintetizar(Marcado.preparar(frase, velocidad, tono))
			if (muestras == null) {
				Registro.w("HablantePico", "Pico no devolvió sonido")
				motor.reiniciar()
				return Resultado.FALLO
			}
			if (!salida.reproducir(muestras, PicoNativo.FRECUENCIA, sigue)) return Resultado.FALLO
		}
		return if (sigue()) Resultado.HABLADO else Resultado.CORTADO
	}

	/**
	 * Las muestras de [texto] con [voz] sin reproducirlas (para grabar los
	 * mensajes de respaldo), a [velocidad] y [tono]. Solo desde el hilo de sonido.
	 */
	fun muestras(texto: String, voz: VozPico, velocidad: Int = prefs.velocidad, tono: Int = prefs.tono): ShortArray? {
		val actual = pico
		val motor = if (actual != null && actual.voz == voz) actual else PicoNativo.abrir(app, voz) ?: return null
		try {
			val partes = Marcado.frases(Marcado.limpiar(texto)).map { motor.sintetizar(Marcado.preparar(it, velocidad, tono)) ?: return null }
			return unir(partes)
		} finally {
			if (motor !== actual) motor.cerrar()
		}
	}

	override fun callar() = cola.vaciar()

	override fun cerrar() {
		cola.enHilo {
			pico?.cerrar()
			pico = null
		}
	}

	companion object {
		const val FRASE_PRUEBA = "Rescate"

		fun unir(partes: List<ShortArray>): ShortArray {
			val todo = ShortArray(partes.sumOf { it.size })
			var i = 0
			for (p in partes) {
				p.copyInto(todo, i)
				i += p.size
			}
			return todo
		}
	}
}
