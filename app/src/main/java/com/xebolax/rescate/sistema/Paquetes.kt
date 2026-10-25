package com.xebolax.rescate.sistema

/**
 * Paquetes conocidos de lectores de pantalla, motores de voz y tiendas.
 *
 * No hace falta que estén instalados: sirven para reconocerlos en el
 * diagnóstico, ofrecer sus copias de seguridad y su reinstalación.
 */
object Paquetes {
	const val TALKBACK = "com.google.android.marvin.talkback"
	const val TALKBACK_SAMSUNG = "com.samsung.android.accessibility.talkback"
	const val TALKBACK_SAMSUNG_ANTIGUO = "com.samsung.android.app.talkback"
	const val TTS_GOOGLE = "com.google.android.tts"
	const val TTS_SAMSUNG = "com.samsung.SMT"
	const val TTS_ESPEAK = "com.reecedunn.espeak"
	const val TTS_RHVOICE = "com.github.olga_yakovleva.rhvoice.android"
	const val TTS_PICO_AOSP = "com.svox.pico"
	const val PLAY_STORE = "com.android.vending"
	const val FDROID = "org.fdroid.fdroid"
	const val AJUSTES = "com.android.settings"
	const val SHIZUKU = "moe.shizuku.privileged.api"

	/** Lectores de pantalla conocidos (paquete → nombre). */
	val LECTORES = linkedMapOf(
		TALKBACK to "TalkBack",
		TALKBACK_SAMSUNG to "TalkBack (Samsung)",
		TALKBACK_SAMSUNG_ANTIGUO to "TalkBack (Samsung)",
		"com.nirenr.talkman" to "Jieshuo",
		"com.prudence.reader" to "Prudence Screen Reader",
		"com.bjbyhd.screenreader_huawei" to "ScreenReader (Huawei)",
		"com.dianming.phoneapp" to "Dianming",
		"com.android.accessibility.talkback" to "TalkBack",
	)

	/** Motores de voz conocidos (paquete → nombre). */
	val MOTORES = linkedMapOf(
		TTS_GOOGLE to "Servicios de voz de Google",
		TTS_SAMSUNG to "Samsung TTS",
		TTS_ESPEAK to "eSpeak NG",
		TTS_RHVOICE to "RHVoice",
		"es.codefactory.vocalizertts" to "Vocalizer TTS",
		"com.acapelagroup.android.tts" to "Acapela TTS",
		"org.nobody.multitts" to "MultiTTS",
		"org.woheller69.ttsengine" to "SherpaTTS",
		TTS_PICO_AOSP to "Pico TTS",
		"com.huawei.hiai" to "Huawei TTS",
		"com.iflytek.speechcloud" to "iFlytek",
		"com.xiaomi.mibrain.speech" to "Xiaomi TTS",
	)

	/** Los dos componentes de Google que más a menudo hay que reparar o reinstalar. */
	val ESENCIALES = listOf(TALKBACK, TTS_GOOGLE)
}
