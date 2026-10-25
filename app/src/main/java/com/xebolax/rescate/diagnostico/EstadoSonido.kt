package com.xebolax.rescate.diagnostico

import android.app.NotificationManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import com.xebolax.rescate.R
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.voz.SalidaAudio

/**
 * El estado del sonido del móvil, leído con API públicas: volúmenes, por dónde
 * sale, modo de timbre, No molestar y audio mono o balance si se pueden leer.
 */
class EstadoSonido(contexto: Context) {
	private val app = contexto.applicationContext
	private val textos = Idioma.envolver(app)
	private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

	/** Volumen de un flujo en por ciento del máximo. */
	fun porCiento(flujo: Int): Int = runCatching {
		val maximo = audio.getStreamMaxVolume(flujo)
		if (maximo <= 0) 0 else audio.getStreamVolume(flujo) * 100 / maximo
	}.getOrDefault(-1)

	/** El volumen de la voz de los lectores (accesibilidad, o multimedia antes de Android 8). */
	fun volumenAccesibilidad(): Int = porCiento(if (Build.VERSION.SDK_INT >= 26) AudioManager.STREAM_ACCESSIBILITY else AudioManager.STREAM_MUSIC)

	fun volumenMultimedia(): Int = porCiento(AudioManager.STREAM_MUSIC)

	fun salidas(): List<AudioDeviceInfo> = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())

	/** Nombres de las salidas Bluetooth conectadas («Auriculares X»). */
	fun bluetooth(): List<String> = salidas().filter { SalidaAudio.esBluetooth(it.type) }
		.map { it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: textos.getString(R.string.sonido_est_bluetooth_sin_nombre) }.distinct()

	fun cable(): Boolean = salidas().any { SalidaAudio.esCable(it.type) }

	/** AudioManager.RINGER_MODE_*. */
	fun modoTimbre(): Int = runCatching { audio.ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)

	/** Si No molestar está activado (cualquier filtro distinto de «todo»). */
	fun noMolestar(): Boolean = runCatching {
		val filtro = app.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
		filtro != NotificationManager.INTERRUPTION_FILTER_ALL && filtro != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
	}.getOrDefault(false)

	/** Audio mono (Settings.System master_mono, oculto pero legible): true, false o null si no se sabe. */
	fun mono(): Boolean? = leerSistema("master_mono")?.toIntOrNull()?.let { it == 1 }

	/** Balance de -1 (todo a la izquierda) a 1 (todo a la derecha), o null si no se sabe. */
	fun balance(): Float? = leerSistema("master_balance")?.toFloatOrNull()

	private fun leerSistema(clave: String): String? =
		try {
			Settings.System.getString(app.contentResolver, clave)
		} catch (e: SecurityException) {
			null
		} catch (e: Exception) {
			null
		}

	/** El estado en frases para decirlo en voz alta. */
	fun resumen(): String {
		val frases = mutableListOf<String>()
		frases += textos.getString(R.string.sonido_est_volumenes, volumenAccesibilidad().coerceAtLeast(0), volumenMultimedia().coerceAtLeast(0))
		val bt = bluetooth()
		frases += when {
			bt.isNotEmpty() -> textos.getString(R.string.sonido_est_bluetooth, bt.joinToString(", "))
			cable() -> textos.getString(R.string.sonido_est_cable)
			else -> textos.getString(R.string.sonido_est_altavoz)
		}
		frases += textos.getString(when (modoTimbre()) {
			AudioManager.RINGER_MODE_SILENT -> R.string.sonido_est_silencio
			AudioManager.RINGER_MODE_VIBRATE -> R.string.sonido_est_vibracion
			else -> R.string.sonido_est_timbre_normal
		})
		if (noMolestar()) frases += textos.getString(R.string.sonido_est_no_molestar)
		if (mono() == true) frases += textos.getString(R.string.sonido_est_mono)
		balance()?.let { b ->
			if (b <= -BALANCE_EXTREMO) frases += textos.getString(R.string.sonido_est_balance_izquierda)
			else if (b >= BALANCE_EXTREMO) frases += textos.getString(R.string.sonido_est_balance_derecha)
		}
		return frases.joinToString(". ") + "."
	}

	companion object {
		/** A partir de aquí el balance deja un oído casi sin sonido. */
		const val BALANCE_EXTREMO = 0.5f
	}
}
