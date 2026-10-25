package com.xebolax.rescate.diagnostico

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.xebolax.rescate.nucleo.Registro
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/** Resultado de probar un motor de voz. */
data class PruebaMotor(
	/** Paquete probado (null = el predeterminado). */
	val paquete: String?,
	/** El motor que de verdad respondió (puede no ser el pedido si este falta). */
	val motorReal: String?,
	val arranca: Boolean,
	/** TextToSpeech.LANG_*: disponible, falta de datos, no admitido… null si no se supo. */
	val idioma: Int?,
	val voces: Int,
	/** Produjo sonido de verdad (síntesis a archivo o habla silenciosa terminada). */
	val habla: Boolean,
	val bytes: Long,
	val milisegundos: Long,
	val error: Int? = null,
) {
	/** Funciona para [locale]: arranca, tiene el idioma y produce sonido. */
	val funciona: Boolean get() = arranca && habla && (idioma == null || idioma >= TextToSpeech.LANG_AVAILABLE)
}

/**
 * Prueba un motor de voz del sistema de verdad, sin sonar: lo arranca, mira si
 * tiene el idioma y le pide sintetizar una frase a un archivo (o decirla con el
 * volumen a cero si no sabe). Solo cuenta como bueno si termina y produce sonido.
 *
 * Así se distingue «el motor arranca» de «el motor habla», y una prueba buena
 * de «es el motor predeterminado del sistema», que es otra cosa.
 */
class ProbadorTts(contexto: Context) {
	private val app = contexto.applicationContext

	suspend fun probar(paquete: String?, locale: Locale, frase: String = "Prueba de voz"): PruebaMotor = withContext(Dispatchers.Main) {
		val inicio = SystemClock.elapsedRealtime()
		val arrancado = CompletableDeferred<Int>()
		var tts: TextToSpeech? = null
		try {
			tts = TextToSpeech(app, { arrancado.complete(it) }, paquete)
			val estado = withTimeoutOrNull(ESPERA_ARRANQUE_MS) { arrancado.await() }
			val t = tts
			if (estado != TextToSpeech.SUCCESS) {
				return@withContext PruebaMotor(paquete, null, false, null, 0, false, 0, SystemClock.elapsedRealtime() - inicio)
			}
			val real = runCatching { t.defaultEngine }.getOrNull()
			val motorReal = paquete ?: real
			val idioma = runCatching { t.isLanguageAvailable(locale) }.getOrNull()
			if (idioma != null && idioma >= TextToSpeech.LANG_AVAILABLE) runCatching { t.language = locale }
			val voces = runCatching { t.voices?.size ?: 0 }.getOrDefault(0)
			val (habla, bytes, error) = sintetizar(t, frase)
			PruebaMotor(paquete, motorReal, true, idioma, voces, habla, bytes, SystemClock.elapsedRealtime() - inicio, error)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "Fallo al probar el motor $paquete", e)
			PruebaMotor(paquete, null, false, null, 0, false, 0, SystemClock.elapsedRealtime() - inicio)
		} finally {
			runCatching { tts?.shutdown() }
		}
	}

	/** Pide sintetizar a un archivo; si el motor no lo admite, habla en silencio. */
	private suspend fun sintetizar(t: TextToSpeech, frase: String): Triple<Boolean, Long, Int?> {
		val archivo = File(app.cacheDir, "prueba_tts.wav")
		archivo.delete()
		val fin = CompletableDeferred<Int?>()
		t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
			override fun onStart(id: String) {}
			override fun onDone(id: String) {
				fin.complete(null)
			}

			@Deprecated("Obsoleto en Java")
			override fun onError(id: String) {
				fin.complete(TextToSpeech.ERROR)
			}

			override fun onError(id: String, codigo: Int) {
				fin.complete(codigo)
			}
		})
		val pedido = runCatching { t.synthesizeToFile(frase, Bundle(), archivo, "prueba-archivo") }.getOrDefault(TextToSpeech.ERROR)
		if (pedido == TextToSpeech.SUCCESS) {
			val error = withTimeoutOrNull(ESPERA_HABLA_MS) { fin.await() } ?: if (fin.isCompleted) null else TIEMPO_AGOTADO
			val bytes = withContext(Dispatchers.IO) { archivo.length() }
			archivo.delete()
			// Una cabecera WAV sola son 44 bytes: hace falta sonido de verdad
			if (error == null && bytes > MINIMO_BYTES) return Triple(true, bytes, null)
			if (error != null && error != TIEMPO_AGOTADO) return Triple(false, bytes, error)
		}
		// Hablar con el volumen a cero: algunos motores no saben sintetizar a archivo
		val fin2 = CompletableDeferred<Int?>()
		t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
			override fun onStart(id: String) {}
			override fun onDone(id: String) {
				fin2.complete(null)
			}

			@Deprecated("Obsoleto en Java")
			override fun onError(id: String) {
				fin2.complete(TextToSpeech.ERROR)
			}

			override fun onError(id: String, codigo: Int) {
				fin2.complete(codigo)
			}
		})
		val parametros = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0f) }
		val pedido2 = runCatching { t.speak(frase, TextToSpeech.QUEUE_FLUSH, parametros, "prueba-silencio") }.getOrDefault(TextToSpeech.ERROR)
		if (pedido2 != TextToSpeech.SUCCESS) return Triple(false, 0, TextToSpeech.ERROR)
		val error2 = withTimeoutOrNull(ESPERA_HABLA_MS) { fin2.await() } ?: if (fin2.isCompleted) null else TIEMPO_AGOTADO
		return Triple(error2 == null, 0, error2)
	}

	companion object {
		private const val ETIQUETA = "ProbadorTts"
		const val ESPERA_ARRANQUE_MS = 8000L
		const val ESPERA_HABLA_MS = 10000L
		const val MINIMO_BYTES = 2000L
		/** Código propio: el motor no terminó a tiempo. */
		const val TIEMPO_AGOTADO = -100
	}
}
