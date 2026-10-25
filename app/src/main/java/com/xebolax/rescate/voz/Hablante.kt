package com.xebolax.rescate.voz

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.xebolax.rescate.nucleo.Registro
import java.util.concurrent.atomic.AtomicInteger

/** Cómo terminó una frase. */
enum class Resultado {
	/** Se dijo entera. */
	HABLADO,
	/** La cortó otra frase o [Hablante.callar]. */
	CORTADO,
	/** El motor no pudo decirla: hay que probar con otro. */
	FALLO,
}

/** Algo que habla: la voz de Pico, un motor del sistema, las grabaciones o los pitidos. */
interface Hablante {
	/** Nombre para el usuario («Voz de rescate Pico», «Google»…). */
	val nombre: String

	/**
	 * Dice [texto], después de lo que esté diciendo o en su lugar si [vaciar].
	 * Luego llama a [alTerminar] en el hilo principal con cómo terminó.
	 */
	fun hablar(texto: String, vaciar: Boolean = true, alTerminar: ((Resultado) -> Unit)? = null)

	/** Corta lo que esté diciendo y lo pendiente. */
	fun callar()

	fun cerrar()
}

/**
 * Cola de sonido en un hilo propio: cada tarea reproduce algo de principio a fin
 * y una nueva «generación» (al vaciar) corta lo que suena y descarta lo pendiente.
 * Así cortar es solo cuestión de subir un contador, sin carreras entre hilos.
 *
 * La idea de la generación y de una pista por frase viene de PicoSpeaker, de
 * Shortcut Menu (aaron-gh, Apache License 2.0).
 */
class ColaAudio(nombreHilo: String = "rescate-audio") {
	private val principal = Handler(Looper.getMainLooper())
	private val hilo = HandlerThread(nombreHilo).apply { start() }
	private val trabajo = Handler(hilo.looper)
	private val generacion = AtomicInteger()
	@Volatile private var cerrada = false

	/**
	 * Encola [tarea]. La tarea recibe una función que dice si debe seguir
	 * sonando y devuelve su [Resultado]; [alTerminar] llega al hilo principal.
	 */
	fun encolar(vaciar: Boolean, tarea: (sigue: () -> Boolean) -> Resultado, alTerminar: ((Resultado) -> Unit)?) {
		if (cerrada) {
			alTerminar?.let { principal.post { it(Resultado.FALLO) } }
			return
		}
		val actual = if (vaciar) generacion.incrementAndGet() else generacion.get()
		trabajo.post {
			if (generacion.get() != actual) {
				alTerminar?.let { principal.post { it(Resultado.CORTADO) } }
				return@post
			}
			val resultado = try {
				tarea { generacion.get() == actual && !cerrada }
			} catch (e: Exception) {
				Registro.e("ColaAudio", "Fallo en una tarea de sonido", e)
				Resultado.FALLO
			}
			val final = if (resultado == Resultado.HABLADO && generacion.get() != actual) Resultado.CORTADO else resultado
			alTerminar?.let { principal.post { it(final) } }
		}
	}

	/** Ejecuta [accion] en el hilo de sonido, en orden con lo encolado, sin cortar nada. */
	fun enHilo(accion: () -> Unit) {
		if (!cerrada) trabajo.post {
			try {
				accion()
			} catch (e: Exception) {
				Registro.e("ColaAudio", "Fallo en el hilo de sonido", e)
			}
		}
	}

	/**
	 * Ejecuta [calculo] en el hilo de sonido, en orden pero sin que un vaciado lo
	 * descarte (comprobaciones), y entrega su resultado en el hilo principal.
	 */
	fun <T> calcular(calculo: () -> T, alTerminar: (T) -> Unit) {
		if (cerrada) return
		trabajo.post {
			val r = calculo()
			principal.post { alTerminar(r) }
		}
	}

	fun vaciar() {
		generacion.incrementAndGet()
	}

	/** Cierra la cola después de [alFinal] (que corre en el hilo de sonido, para liberar lo suyo). */
	fun cerrar(alFinal: (() -> Unit)? = null) {
		generacion.incrementAndGet()
		trabajo.post {
			try {
				alFinal?.invoke()
			} catch (e: Exception) {
				Registro.e("ColaAudio", "Fallo al cerrar el sonido", e)
			}
			cerrada = true
			hilo.quitSafely()
		}
	}
}
