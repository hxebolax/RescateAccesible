package com.xebolax.rescate.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.xebolax.rescate.voz.VozPico

/**
 * Responde a las preguntas del sistema sobre los datos de voz del motor propio,
 * sin pantalla: todas las voces vienen dentro de la app, así que siempre están.
 *
 * - CHECK_TTS_DATA: devuelve las voces disponibles (español, inglés de EE. UU. y británico).
 * - INSTALL_TTS_DATA: no hay nada que descargar; abre la pantalla del motor, que lo explica.
 */
class ActividadDatosVoz : Activity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		when (intent?.action) {
			TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA -> {
				startActivity(Intent(this, ActividadAjustesTts::class.java).putExtra(ActividadAjustesTts.EXTRA_INSTALAR, true))
				setResult(RESULT_OK)
			}
			else -> {
				val disponibles = ArrayList(VozPico.entries.map { "${it.locale.isO3Language}-${it.locale.isO3Country}" })
				val resultado = Intent()
					.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, disponibles)
					.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList())
				setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, resultado)
			}
		}
		finish()
	}
}
