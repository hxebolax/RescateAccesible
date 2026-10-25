/*
 * Parte de la idea de SystemSpeaker (Speech.kt) de Shortcut Menu (https://github.com/aaron-gh/shortcut-menu),
 * de aaron-gh, con licencia Apache 2.0. Rehecho para Rescate Accesible: motor concreto, idioma,
 * tiempos de espera y detección de fallos para cambiar a la voz propia.
 */
package com.xebolax.rescate.voz

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import java.util.concurrent.atomic.AtomicInteger

/**
 * Habla con un motor de voz del sistema (TextToSpeech). Sirve para el
 * diagnóstico y como voz de rescate opcional si el usuario elige un motor que
 * se comprobó que funciona; si falla (no arranca, no empieza a hablar a
 * tiempo o da error), devuelve [Resultado.FALLO] para que se cambie a Pico.
 *
 * Nunca se da por bueno un motor solo porque se inicializó: cuenta que empiece
 * a hablar de verdad (onStart) y termine (onDone).
 */
class HablanteSistema(
	contexto: Context,
	/** Paquete del motor, o null para el predeterminado del sistema. */
	val motor: String?,
	private val salida: SalidaAudio? = null,
) : Hablante {
	private val app = contexto.applicationContext
	private val prefs = Preferencias(app)
	private val principal = Handler(Looper.getMainLooper())
	private val contador = AtomicInteger()
	private val pendientes = HashMap<String, (Resultado) -> Unit>()
	private val empezadas = HashSet<String>()
	private var estado = Estado.INICIANDO
	private val enEspera = mutableListOf<Triple<String, Boolean, ((Resultado) -> Unit)?>>()
	private val listos = mutableListOf<(Boolean) -> Unit>()
	private var tts: TextToSpeech? = null

	private enum class Estado { INICIANDO, LISTO, FALLO, CERRADO }

	override val nombre: String get() = motor ?: "sistema"

	init {
		tts = try {
			TextToSpeech(app, { estado -> principal.post { alIniciar(estado) } }, motor)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo crear el motor de voz $motor", e)
			null
		}
		if (tts == null) estado = Estado.FALLO
		else principal.postDelayed({ if (estado == Estado.INICIANDO) {
			Registro.w(ETIQUETA, "El motor $motor no terminó de arrancar a tiempo")
			fallar()
		} }, ESPERA_INICIO_MS)
	}

	/** [listo] recibe si el motor arrancó (no si habla: eso lo dice [Resultado]). */
	fun alEstarListo(listo: (Boolean) -> Unit) {
		when (estado) {
			Estado.LISTO -> listo(true)
			Estado.FALLO, Estado.CERRADO -> listo(false)
			Estado.INICIANDO -> listos += listo
		}
	}

	private fun alIniciar(resultado: Int) {
		if (estado != Estado.INICIANDO) return
		val t = tts
		if (resultado != TextToSpeech.SUCCESS || t == null) {
			Registro.w(ETIQUETA, "El motor $motor no arrancó (código $resultado)")
			fallar()
			return
		}
		try {
			salida?.let { t.setAudioAttributes(it.atributos) }
			val locale = Idioma.efectivo(app)
			if (t.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) t.language = locale
			t.setSpeechRate(prefs.velocidad / 100f)
			t.setPitch(prefs.tono / 100f)
			t.setOnUtteranceProgressListener(oyente)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "El motor $motor falló al configurarse", e)
			fallar()
			return
		}
		estado = Estado.LISTO
		listos.forEach { it(true) }
		listos.clear()
		val cola = enEspera.toList()
		enEspera.clear()
		cola.forEach { (texto, vaciar, fin) -> hablar(texto, vaciar, fin) }
	}

	private fun fallar() {
		estado = Estado.FALLO
		listos.forEach { it(false) }
		listos.clear()
		val cola = enEspera.toList()
		enEspera.clear()
		cola.forEach { it.third?.invoke(Resultado.FALLO) }
	}

	private val oyente = object : UtteranceProgressListener() {
		override fun onStart(id: String) {
			principal.post { empezadas += id }
		}

		override fun onDone(id: String) = terminar(id, Resultado.HABLADO)

		@Deprecated("Obsoleto en Java")
		override fun onError(id: String) = terminar(id, Resultado.FALLO)

		override fun onError(id: String, codigo: Int) {
			Registro.w(ETIQUETA, "El motor $motor dio el error $codigo")
			terminar(id, Resultado.FALLO)
		}

		override fun onStop(id: String, interrumpida: Boolean) = terminar(id, Resultado.CORTADO)
	}

	private fun terminar(id: String, resultado: Resultado) {
		principal.post {
			empezadas -= id
			pendientes.remove(id)?.invoke(resultado)
		}
	}

	override fun hablar(texto: String, vaciar: Boolean, alTerminar: ((Resultado) -> Unit)?) {
		when (estado) {
			Estado.INICIANDO -> {
				if (vaciar) {
					enEspera.forEach { it.third?.invoke(Resultado.CORTADO) }
					enEspera.clear()
				}
				enEspera += Triple(texto, vaciar, alTerminar)
				return
			}
			Estado.FALLO, Estado.CERRADO -> {
				alTerminar?.let { principal.post { it(Resultado.FALLO) } }
				return
			}
			Estado.LISTO -> Unit
		}
		val t = tts ?: return
		val id = "rescate-" + contador.incrementAndGet()
		if (vaciar) {
			val cortadas = pendientes.values.toList()
			pendientes.clear()
			cortadas.forEach { it(Resultado.CORTADO) }
		}
		pendientes[id] = alTerminar ?: {}
		val parametros = Bundle()
		val codigo = try {
			t.speak(texto, if (vaciar) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, parametros, id)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "El motor $motor falló al hablar", e)
			TextToSpeech.ERROR
		}
		if (codigo != TextToSpeech.SUCCESS) {
			terminar(id, Resultado.FALLO)
			return
		}
		// Si no empieza a sonar a tiempo, el motor está colgado: fallo
		principal.postDelayed({
			if (pendientes.containsKey(id) && id !in empezadas) {
				Registro.w(ETIQUETA, "El motor $motor no empezó a hablar a tiempo")
				pendientes.remove(id)?.invoke(Resultado.FALLO)
			}
		}, ESPERA_HABLAR_MS)
	}

	override fun callar() {
		runCatching { tts?.stop() }
	}

	override fun cerrar() {
		estado = Estado.CERRADO
		pendientes.clear()
		enEspera.clear()
		try {
			tts?.stop()
			tts?.shutdown()
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "Fallo al cerrar el motor $motor", e)
		}
		tts = null
	}

	companion object {
		private const val ETIQUETA = "HablanteSistema"
		const val ESPERA_INICIO_MS = 8000L
		const val ESPERA_HABLAR_MS = 5000L
	}
}
