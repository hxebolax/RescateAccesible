package com.xebolax.rescate.voz

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Señales sonoras y de vibración del rescate. */
enum class Senal {
	/** Al pasar a otra opción. */
	MOVER,
	/** Al elegir una opción. */
	ELEGIR,
	/** Algo salió bien. */
	CORRECTO,
	/** Algo falló. */
	ERROR,
	/** Atención: empieza el rescate o hay un aviso. */
	ATENCION,
}

/**
 * Pitidos generados en el momento (sin archivos) y vibraciones: el último
 * recurso si ninguna voz funciona, y la confirmación de cada gesto. Con los
 * pitidos se puede contar la posición de una opción (tres pitidos, opción tres).
 */
class Pitidos(contexto: Context, private val cola: ColaAudio, private val salida: SalidaAudio) {
	private val app = contexto.applicationContext
	private val prefs = Preferencias(app)
	private val vibrador: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
		app.getSystemService(VibratorManager::class.java)?.defaultVibrator
	} else {
		@Suppress("DEPRECATION")
		app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
	}

	fun tocar(senal: Senal, vaciar: Boolean = false, alTerminar: ((Resultado) -> Unit)? = null) {
		vibrar(senal)
		val muestras = when (senal) {
			Senal.MOVER -> tono(1200.0, 25)
			Senal.ELEGIR -> unir(tono(900.0, 50), silencio(30), tono(1300.0, 70))
			Senal.CORRECTO -> unir(tono(660.0, 90), silencio(40), tono(990.0, 140))
			Senal.ERROR -> unir(tono(330.0, 180), silencio(60), tono(250.0, 260))
			Senal.ATENCION -> unir(tono(523.0, 120), silencio(50), tono(659.0, 120), silencio(50), tono(784.0, 200))
		}
		reproducir(muestras, vaciar, alTerminar)
	}

	/** [n] pitidos cortos (para decir una posición sin voz). */
	fun contar(n: Int, vaciar: Boolean = true, alTerminar: ((Resultado) -> Unit)? = null) {
		val partes = mutableListOf<ShortArray>()
		repeat(n.coerceIn(1, 20)) {
			if (it > 0) partes += silencio(if (it % 5 == 0) 350 else 160)
			partes += tono(880.0, 70)
		}
		reproducir(unir(*partes.toTypedArray()), vaciar, alTerminar)
	}

	private fun reproducir(muestras: ShortArray, vaciar: Boolean, alTerminar: ((Resultado) -> Unit)?) {
		cola.encolar(vaciar, { sigue ->
			if (salida.reproducir(muestras, FRECUENCIA, sigue)) Resultado.HABLADO else Resultado.FALLO
		}, alTerminar)
	}

	fun vibrar(senal: Senal) {
		if (!prefs.vibrar) return
		val patron = when (senal) {
			Senal.MOVER -> longArrayOf(0, 15)
			Senal.ELEGIR -> longArrayOf(0, 40, 60, 40)
			Senal.CORRECTO -> longArrayOf(0, 60)
			Senal.ERROR -> longArrayOf(0, 300)
			Senal.ATENCION -> longArrayOf(0, 120, 100, 120, 100, 120)
		}
		vibrarPatron(patron)
	}

	/** [n] vibraciones cortas: la posición de una opción sin sonido. */
	fun vibrarCuenta(n: Int) {
		if (!prefs.vibrar) return
		val patron = mutableListOf(0L)
		repeat(n.coerceIn(1, 20)) {
			if (it > 0) patron += if (it % 5 == 0) 450L else 200L
			patron += 60L
		}
		vibrarPatron(patron.toLongArray())
	}

	private fun vibrarPatron(patron: LongArray) {
		val v = vibrador ?: return
		try {
			if (!v.hasVibrator()) return
			if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(patron, -1))
			else @Suppress("DEPRECATION") v.vibrate(patron, -1)
		} catch (e: Exception) {
			Registro.w("Pitidos", "No se pudo vibrar", e)
		}
	}

	companion object {
		const val FRECUENCIA = 22050

		/** Un tono senoidal con entrada y salida suaves (sin chasquidos). */
		fun tono(frecuencia: Double, ms: Int, volumen: Double = 0.55): ShortArray {
			val n = FRECUENCIA * ms / 1000
			val rampa = min(n / 4, FRECUENCIA * 8 / 1000)
			return ShortArray(n) { i ->
				val envolvente = when {
					i < rampa -> i.toDouble() / rampa
					i > n - rampa -> (n - i).toDouble() / rampa
					else -> 1.0
				}
				(sin(2 * PI * frecuencia * i / FRECUENCIA) * envolvente * volumen * Short.MAX_VALUE).toInt().toShort()
			}
		}

		fun silencio(ms: Int): ShortArray = ShortArray(FRECUENCIA * ms / 1000)

		fun unir(vararg partes: ShortArray): ShortArray {
			val total = ShortArray(partes.sumOf { it.size })
			var i = 0
			for (p in partes) {
				p.copyInto(total, i)
				i += p.size
			}
			return total
		}
	}
}
