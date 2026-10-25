package com.xebolax.rescate.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.voz.Marcado
import com.xebolax.rescate.voz.PicoNativo
import com.xebolax.rescate.voz.VozPico
import java.util.Locale

/**
 * «Voz de rescate (Pico)» como motor de voz del sistema.
 *
 * Así TalkBack (o cualquier app) puede usar siempre una voz sin Internet que
 * viene dentro de esta app: si el motor habitual se rompe, basta con elegir
 * este en los ajustes de síntesis de voz (o que el rescate lo ponga, con el
 * permiso avanzado). Tiene las voces de Pico en español, inglés de EE. UU. e
 * inglés británico.
 *
 * Android llama a la síntesis siempre desde el mismo hilo, así que Pico se abre
 * y se usa solo ahí; [onStop] llega desde otro hilo y solo levanta una marca.
 */
class ServicioTtsPropio : TextToSpeechService() {
	/** Solo desde el hilo de síntesis. */
	private var pico: PicoNativo? = null
	@Volatile private var parar = false
	@Volatile private var vozCargada: VozPico = VozPico.ESPANOL

	override fun onDestroy() {
		super.onDestroy()
		// La síntesis ya ha terminado: el servicio se destruye después
		pico?.cerrar()
		pico = null
	}

	override fun onIsLanguageAvailable(idioma: String?, pais: String?, variante: String?): Int = disponibilidad(idioma, pais)

	override fun onGetLanguage(): Array<String> {
		val l = vozCargada.locale
		return arrayOf(l.isO3Language, l.isO3Country, "")
	}

	override fun onLoadLanguage(idioma: String?, pais: String?, variante: String?): Int {
		val resultado = disponibilidad(idioma, pais)
		if (resultado >= TextToSpeech.LANG_AVAILABLE) vozCargada = elegir(idioma, pais)
		return resultado
	}

	override fun onStop() {
		parar = true
	}

	override fun onGetVoices(): List<Voice> = VozPico.entries.map { vozDe(it) }

	override fun onIsValidVoiceName(nombre: String?): Int =
		if (VozPico.entries.any { nombreVoz(it) == nombre }) TextToSpeech.SUCCESS else TextToSpeech.ERROR

	override fun onLoadVoice(nombre: String?): Int {
		val voz = VozPico.entries.firstOrNull { nombreVoz(it) == nombre } ?: return TextToSpeech.ERROR
		vozCargada = voz
		return TextToSpeech.SUCCESS
	}

	override fun onGetDefaultVoiceNameFor(idioma: String?, pais: String?, variante: String?): String? {
		if (disponibilidad(idioma, pais) < TextToSpeech.LANG_AVAILABLE) return null
		return nombreVoz(elegir(idioma, pais))
	}

	override fun onSynthesizeText(peticion: SynthesisRequest, respuesta: SynthesisCallback) {
		parar = false
		// Un idioma que Pico no tiene (el móvil en francés, por ejemplo): mejor hablar en el
		// idioma de la app que callar, porque este es el motor del rescate
		val voz = peticion.voiceName?.let { n -> VozPico.entries.firstOrNull { nombreVoz(it) == n } }
			?: if (disponibilidad(peticion.language, peticion.country) >= TextToSpeech.LANG_AVAILABLE) elegir(peticion.language, peticion.country)
			else VozPico.para(com.xebolax.rescate.nucleo.Idioma.efectivo(this))
		sintesisPedidas++
		// Solo la longitud, nunca el texto: para saber que el sistema (TalkBack) usa este motor
		android.util.Log.d(ETIQUETA, "Síntesis pedida: ${peticion.charSequenceText?.length ?: 0} caracteres, idioma ${peticion.language}")
		val motor = motor(voz)
		if (motor == null) {
			respuesta.error(TextToSpeech.ERROR_SYNTHESIS)
			return
		}
		if (respuesta.start(PicoNativo.FRECUENCIA, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
		val texto = peticion.charSequenceText?.toString() ?: @Suppress("DEPRECATION") peticion.text.orEmpty()
		// 100 es lo normal tanto en la petición como en el marcado de Pico
		val velocidad = peticion.speechRate
		val tono = peticion.pitch
		val maximo = respuesta.maxBufferSize.coerceAtLeast(1024)
		try {
			for (frase in Marcado.frases(Marcado.limpiar(texto))) {
				if (parar) break
				val muestras = motor.sintetizar(Marcado.preparar(frase, velocidad, tono))
				if (muestras == null) {
					motor.reiniciar()
					respuesta.error(TextToSpeech.ERROR_SYNTHESIS)
					return
				}
				val bytes = ByteArray(muestras.size * 2)
				for (i in muestras.indices) {
					val m = muestras[i].toInt()
					bytes[2 * i] = (m and 0xFF).toByte()
					bytes[2 * i + 1] = (m shr 8 and 0xFF).toByte()
				}
				var desde = 0
				while (desde < bytes.size && !parar) {
					val n = minOf(maximo, bytes.size - desde)
					if (respuesta.audioAvailable(bytes, desde, n) != TextToSpeech.SUCCESS) {
						parar = true
						break
					}
					desde += n
				}
			}
			if (parar) motor.reiniciar()
			respuesta.done()
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "Fallo al sintetizar como motor del sistema", e)
			respuesta.error(TextToSpeech.ERROR_SYNTHESIS)
		}
	}

	/** Pico abierto con [voz]. Solo desde el hilo de síntesis. */
	private fun motor(voz: VozPico): PicoNativo? {
		pico?.let { if (it.voz == voz) return it else it.cerrar() }
		pico = PicoNativo.abrir(this, voz)
		return pico
	}

	companion object {
		private const val ETIQUETA = "ServicioTtsPropio"

		/** Cuántas síntesis ha pedido el sistema (TalkBack u otra app) desde que arrancó el proceso. */
		@Volatile var sintesisPedidas = 0
			private set

		/** Nombre estable de cada voz («es-ES-pico»). */
		fun nombreVoz(voz: VozPico): String = voz.codigo + "-pico"

		fun vozDe(voz: VozPico): Voice = Voice(
			nombreVoz(voz),
			voz.locale,
			Voice.QUALITY_NORMAL,
			Voice.LATENCY_LOW,
			false,
			emptySet(),
		)

		/** El idioma de Android viene en ISO 639-2 (spa, eng) o en dos letras; aquí se acepta cualquiera. */
		private fun idiomaCorto(idioma: String?): String =
			when (idioma?.lowercase(Locale.ROOT)) {
				"spa", "es" -> "es"
				"eng", "en" -> "en"
				else -> idioma?.lowercase(Locale.ROOT).orEmpty()
			}

		private fun paisCorto(pais: String?): String =
			when (pais?.uppercase(Locale.ROOT)) {
				"USA", "US" -> "US"
				"GBR", "GB" -> "GB"
				"ESP", "ES" -> "ES"
				else -> pais?.uppercase(Locale.ROOT).orEmpty()
			}

		fun disponibilidad(idioma: String?, pais: String?): Int {
			val i = idiomaCorto(idioma)
			val p = paisCorto(pais)
			return when (i) {
				"es" -> if (p == "ES") TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
				"en" -> if (p == "US" || p == "GB") TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
				else -> TextToSpeech.LANG_NOT_SUPPORTED
			}
		}

		fun elegir(idioma: String?, pais: String?): VozPico {
			val i = idiomaCorto(idioma)
			val p = paisCorto(pais)
			return when {
				i == "en" && p == "US" -> VozPico.INGLES_EEUU
				i == "en" -> VozPico.INGLES_GB
				else -> VozPico.ESPANOL
			}
		}
	}
}
