package com.xebolax.rescate.voz

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import kotlin.math.roundToInt

/**
 * La salida de sonido del rescate: reproduce muestras PCM, controla el foco de
 * audio, el volumen y por dónde sale el sonido.
 *
 * Usa el uso de audio de accesibilidad (el volumen que el usuario tiene subido
 * para su lector). Si una app de accesibilidad de esta app está activa, Android
 * le deja cambiar ese volumen; si no, sigue al volumen multimedia. Si hay
 * auriculares Bluetooth conectados (a menudo apagados o lejos: una causa muy
 * común de «el móvil no habla»), puede mandar el sonido al altavoz del móvil.
 */
class SalidaAudio(contexto: Context) {
	private val app = contexto.applicationContext
	private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	private val prefs = Preferencias(app)
	private var foco: AudioFocusRequest? = null

	val atributos: AudioAttributes = AudioAttributes.Builder()
		.setUsage(if (Build.VERSION.SDK_INT >= 26) AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY else AudioAttributes.USAGE_MEDIA)
		.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
		.build()

	/** El flujo de volumen que corresponde a [atributos]. */
	val flujo: Int get() = if (Build.VERSION.SDK_INT >= 26) AudioManager.STREAM_ACCESSIBILITY else AudioManager.STREAM_MUSIC

	/** Si el sonido debe ir al altavoz del móvil según la preferencia y lo conectado. */
	fun usarAltavoz(): Boolean = when (prefs.altavoz) {
		"siempre" -> true
		"nunca" -> false
		else -> bluetoothConectado()
	}

	/** Si hay una salida de audio Bluetooth conectada (auriculares, altavoz, coche). */
	fun bluetoothConectado(): Boolean = salidas().any { esBluetooth(it.type) }

	fun salidas(): List<AudioDeviceInfo> =
		runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())

	private fun altavoz(): AudioDeviceInfo? = salidas().firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

	/**
	 * Sube el volumen de la voz de rescate (y el multimedia, del que depende en
	 * algunos móviles) si está por debajo del mínimo elegido. Devuelve si cambió algo.
	 */
	fun asegurarVolumen(): Boolean {
		if (!prefs.subirVolumen) return false
		var cambiado = false
		val flujos = mutableListOf(flujo)
		if (flujo != AudioManager.STREAM_MUSIC) flujos += AudioManager.STREAM_MUSIC
		for (f in flujos) {
			try {
				val maximo = audio.getStreamMaxVolume(f)
				val minimo = (maximo * prefs.volumenMinimo / 100f).roundToInt().coerceAtLeast(1)
				if (audio.getStreamVolume(f) < minimo) {
					audio.setStreamVolume(f, minimo, 0)
					cambiado = cambiado || audio.getStreamVolume(f) >= minimo
				}
			} catch (e: SecurityException) {
				// No Molestar o una política del sistema impiden cambiarlo: el diagnóstico lo explica
				Registro.w(ETIQUETA, "No se pudo subir el volumen del flujo $f", e)
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "Fallo al comprobar el volumen del flujo $f", e)
			}
		}
		return cambiado
	}

	/** Volumen actual en por ciento de un flujo. */
	fun porCiento(f: Int): Int = runCatching {
		val maximo = audio.getStreamMaxVolume(f)
		if (maximo <= 0) 0 else audio.getStreamVolume(f) * 100 / maximo
	}.getOrDefault(0)

	/** Cambia un paso el volumen de la voz de rescate (y el multimedia). Devuelve el nuevo por ciento. */
	fun pasoVolumen(subir: Boolean): Int {
		val direccion = if (subir) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
		for (f in setOf(flujo, AudioManager.STREAM_MUSIC)) {
			runCatching { audio.adjustStreamVolume(f, direccion, 0) }
				.onFailure { Registro.w(ETIQUETA, "No se pudo ajustar el volumen del flujo $f", it) }
		}
		return porCiento(flujo)
	}

	/** Pide el foco de audio mientras dura el rescate: la música se pausa o baja. */
	fun pedirFoco() {
		try {
			if (Build.VERSION.SDK_INT >= 26) {
				val peticion = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
					.setAudioAttributes(atributos)
					.setOnAudioFocusChangeListener { }
					.build()
				foco = peticion
				audio.requestAudioFocus(peticion)
			} else {
				@Suppress("DEPRECATION")
				audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
			}
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo pedir el foco de audio", e)
		}
	}

	fun soltarFoco() {
		try {
			if (Build.VERSION.SDK_INT >= 26) foco?.let { audio.abandonAudioFocusRequest(it) }
			else @Suppress("DEPRECATION") audio.abandonAudioFocus(null)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo soltar el foco de audio", e)
		}
		foco = null
	}

	/**
	 * Reproduce [muestras] (16 bits, mono, a [frecuencia] Hz) hasta el final o
	 * hasta que [sigue] devuelva false. Bloquea: llámese desde un hilo de trabajo.
	 * Devuelve false si no se pudo reproducir.
	 */
	fun reproducir(muestras: ShortArray, frecuencia: Int, sigue: () -> Boolean): Boolean {
		if (muestras.isEmpty()) return true
		val pista = try {
			AudioTrack.Builder()
				.setAudioAttributes(atributos)
				.setAudioFormat(
					AudioFormat.Builder()
						.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
						.setSampleRate(frecuencia)
						.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
						.build()
				)
				.setTransferMode(AudioTrack.MODE_STATIC)
				.setBufferSizeInBytes(muestras.size * 2)
				.build()
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo crear la pista de audio", e)
			return false
		}
		try {
			if (usarAltavoz()) altavoz()?.let { pista.setPreferredDevice(it) }
			if (pista.write(muestras, 0, muestras.size) < 0) return false
			pista.play()
			val limite = SystemClock.uptimeMillis() + muestras.size * 1000L / frecuencia + HOLGURA_MS
			while (sigue() && pista.playbackHeadPosition < muestras.size && SystemClock.uptimeMillis() < limite) {
				SystemClock.sleep(SONDEO_MS)
			}
			if (!sigue()) runCatching { pista.pause(); pista.flush() }
			return true
		} catch (e: IllegalStateException) {
			Registro.e(ETIQUETA, "La pista de audio falló al reproducir", e)
			return false
		} finally {
			pista.release()
		}
	}

	companion object {
		private const val ETIQUETA = "SalidaAudio"
		private const val SONDEO_MS = 10L
		private const val HOLGURA_MS = 1000L

		fun esBluetooth(tipo: Int): Boolean =
			tipo == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || tipo == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
				(Build.VERSION.SDK_INT >= 31 && (tipo == AudioDeviceInfo.TYPE_BLE_HEADSET || tipo == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
				(Build.VERSION.SDK_INT >= 33 && tipo == AudioDeviceInfo.TYPE_BLE_BROADCAST)

		fun esCable(tipo: Int): Boolean =
			tipo == AudioDeviceInfo.TYPE_WIRED_HEADSET || tipo == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
				(Build.VERSION.SDK_INT >= 26 && tipo == AudioDeviceInfo.TYPE_USB_HEADSET)
	}
}
