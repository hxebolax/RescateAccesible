package com.xebolax.rescate.nucleo

import android.content.Context
import android.content.SharedPreferences
import com.xebolax.rescate.BuildConfig

/**
 * Preferencias de la app, en el almacenamiento protegido del dispositivo (ver
 * [Almacen]) para que el rescate las tenga también antes de desbloquear.
 *
 * Solo guarda configuración propia: nada leído de otras aplicaciones.
 */
class Preferencias(contexto: Context) {
	private val p: SharedPreferences = Almacen.protegido(contexto).getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

	/** Para escuchar cambios (pantalla de ajustes). */
	val crudas: SharedPreferences get() = p

	/** Idioma de la interfaz y de la voz de rescate: "" sigue al sistema, "es" o "en". */
	var idioma: String
		get() = p.getString(IDIOMA, "").orEmpty()
		set(v) = p.edit().putString(IDIOMA, v).apply()

	/** Velocidad de la voz de rescate en por ciento (100 = normal). */
	var velocidad: Int
		get() = p.getInt(VELOCIDAD, 100)
		set(v) = p.edit().putInt(VELOCIDAD, v.coerceIn(VELOCIDAD_MIN, VELOCIDAD_MAX)).apply()

	/** Tono de la voz de rescate en por ciento (100 = normal). */
	var tono: Int
		get() = p.getInt(TONO, 100)
		set(v) = p.edit().putInt(TONO, v.coerceIn(TONO_MIN, TONO_MAX)).apply()

	/** Volumen mínimo garantizado durante el rescate, en por ciento del máximo. */
	var volumenMinimo: Int
		get() = p.getInt(VOLUMEN_MINIMO, 60)
		set(v) = p.edit().putInt(VOLUMEN_MINIMO, v.coerceIn(0, 100)).apply()

	/** Si el rescate sube solo el volumen cuando está demasiado bajo para oírse. */
	var subirVolumen: Boolean
		get() = p.getBoolean(SUBIR_VOLUMEN, true)
		set(v) = p.edit().putBoolean(SUBIR_VOLUMEN, v).apply()

	/** Altavoz del móvil: "auto" (si los auriculares Bluetooth parecen el problema), "siempre" o "nunca". */
	var altavoz: String
		get() = p.getString(ALTAVOZ, "auto").orEmpty()
		set(v) = p.edit().putString(ALTAVOZ, v).apply()

	/** Motor de la voz de rescate: "pico" o "espeak" (incluidos en la app) o el paquete de un motor del sistema probado. */
	var motorRescate: String
		get() = p.getString(MOTOR_RESCATE, MOTOR_PICO).orEmpty()
		set(v) = p.edit().putString(MOTOR_RESCATE, v).apply()

	/** Motor de voz del sistema que se comprobó que funciona (el «conocido bueno»), o "". */
	var motorConocido: String
		get() = p.getString(MOTOR_CONOCIDO, "").orEmpty()
		set(v) = p.edit().putString(MOTOR_CONOCIDO, v).apply()

	/** Lector de pantalla habitual del usuario (componente), para recuperarlo. */
	var lectorHabitual: String
		get() = p.getString(LECTOR_HABITUAL, "").orEmpty()
		set(v) = p.edit().putString(LECTOR_HABITUAL, v).apply()

	/** Lectores y servicios que ofrece el menú (como en Shortcut Menu); null = los lectores de pantalla. */
	var serviciosMenu: Set<String>?
		get() = p.getStringSet(SERVICIOS_MENU, null)
		set(v) = p.edit().putStringSet(SERVICIOS_MENU, v).apply()

	/** Si el asistente de preparación se terminó. */
	var asistenteCompletado: Boolean
		get() = p.getBoolean(ASISTENTE, false)
		set(v) = p.edit().putBoolean(ASISTENTE, v).apply()

	/** Si se practicó el rescate al menos una vez. */
	var ensayoHecho: Boolean
		get() = p.getBoolean(ENSAYO, false)
		set(v) = p.edit().putBoolean(ENSAYO, v).apply()

	/** Segundos sin tocar nada antes de que el modo seguro devuelva el lector que apagó. */
	var esperaModoSeguro: Int
		get() = p.getInt(ESPERA_SEGURO, 30)
		set(v) = p.edit().putInt(ESPERA_SEGURO, v.coerceIn(15, 300)).apply()

	/** Guardián: avisar con la voz de rescate si el lector o la voz del sistema dejan de funcionar. */
	var guardianAvisos: Boolean
		get() = p.getBoolean(GUARDIAN_AVISOS, true)
		set(v) = p.edit().putBoolean(GUARDIAN_AVISOS, v).apply()

	/** Guardián: comprobar la voz del sistema al encender la pantalla (como mucho cada [GUARDIAN_INTERVALO_MIN]). */
	var guardianComprobarVoz: Boolean
		get() = p.getBoolean(GUARDIAN_VOZ, true)
		set(v) = p.edit().putBoolean(GUARDIAN_VOZ, v).apply()

	/** Guardián: con el lector caído, tres pulsaciones rápidas de volumen arriba abren el rescate. */
	var guardianTeclas: Boolean
		get() = p.getBoolean(GUARDIAN_TECLAS, true)
		set(v) = p.edit().putBoolean(GUARDIAN_TECLAS, v).apply()

	var ultimaComprobacionVoz: Long
		get() = p.getLong(ULTIMA_VOZ, 0L)
		set(v) = p.edit().putLong(ULTIMA_VOZ, v).apply()

	/** Versión e idioma de las grabaciones de respaldo ya hechas ("1.0.0|es"). */
	var grabacionesHechas: String
		get() = p.getString(GRABACIONES, "").orEmpty()
		set(v) = p.edit().putString(GRABACIONES, v).apply()

	/** Actualizaciones: buscar al abrir la app (como mucho cada pocas horas). */
	var buscarActualizaciones: Boolean
		get() = p.getBoolean(BUSCAR_ACTUALIZACIONES, true)
		set(v) = p.edit().putBoolean(BUSCAR_ACTUALIZACIONES, v).apply()

	/** Dirección del servidor de actualizaciones (version.json y el APK). */
	var urlActualizaciones: String
		get() = p.getString(URL_ACTUALIZACIONES, null)?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: BuildConfig.URL_ACTUALIZACIONES
		set(v) = p.edit().putString(URL_ACTUALIZACIONES, v.trim().trimEnd('/')).apply()

	var ultimaBusquedaActualizacion: Long
		get() = p.getLong(ULTIMA_ACTUALIZACION, 0L)
		set(v) = p.edit().putLong(ULTIMA_ACTUALIZACION, v).apply()

	var versionOmitida: String
		get() = p.getString(VERSION_OMITIDA, "").orEmpty()
		set(v) = p.edit().putString(VERSION_OMITIDA, v).apply()

	var versionAnterior: Int
		get() = p.getInt(VERSION_ANTERIOR, 0)
		set(v) = p.edit().putInt(VERSION_ANTERIOR, v).apply()

	/** Si el menú de rescate se maneja también con las teclas de volumen (recomendado). */
	var teclasEnMenu: Boolean
		get() = p.getBoolean(TECLAS_MENU, true)
		set(v) = p.edit().putBoolean(TECLAS_MENU, v).apply()

	/** Si el menú dice el número de cada opción («3 de 9»). */
	var decirPosicion: Boolean
		get() = p.getBoolean(DECIR_POSICION, true)
		set(v) = p.edit().putBoolean(DECIR_POSICION, v).apply()

	/** Vibrar al pasar por cada opción y al elegir. */
	var vibrar: Boolean
		get() = p.getBoolean(VIBRAR, true)
		set(v) = p.edit().putBoolean(VIBRAR, v).apply()

	companion object {
		const val ARCHIVO = "preferencias"
		const val MOTOR_PICO = "pico"
		const val MOTOR_ESPEAK = "espeak"
		const val VELOCIDAD_MIN = 50
		const val VELOCIDAD_MAX = 300
		const val TONO_MIN = 50
		const val TONO_MAX = 200
		const val GUARDIAN_INTERVALO_MIN = 30L

		const val IDIOMA = "idioma"
		const val VELOCIDAD = "velocidad"
		const val TONO = "tono"
		const val VOLUMEN_MINIMO = "volumen_minimo"
		const val SUBIR_VOLUMEN = "subir_volumen"
		const val ALTAVOZ = "altavoz"
		const val MOTOR_RESCATE = "motor_rescate"
		const val MOTOR_CONOCIDO = "motor_conocido"
		const val LECTOR_HABITUAL = "lector_habitual"
		const val SERVICIOS_MENU = "servicios_menu"
		const val ASISTENTE = "asistente_completado"
		const val ENSAYO = "ensayo_hecho"
		const val ESPERA_SEGURO = "espera_modo_seguro"
		const val GUARDIAN_AVISOS = "guardian_avisos"
		const val GUARDIAN_VOZ = "guardian_comprobar_voz"
		const val GUARDIAN_TECLAS = "guardian_teclas"
		const val ULTIMA_VOZ = "ultima_comprobacion_voz"
		const val GRABACIONES = "grabaciones_hechas"
		const val BUSCAR_ACTUALIZACIONES = "buscar_actualizaciones"
		const val URL_ACTUALIZACIONES = "url_actualizaciones"
		const val ULTIMA_ACTUALIZACION = "ultima_busqueda_actualizacion"
		const val VERSION_OMITIDA = "version_omitida"
		const val VERSION_ANTERIOR = "version_anterior"
		const val TECLAS_MENU = "teclas_en_menu"
		const val DECIR_POSICION = "decir_posicion"
		const val VIBRAR = "vibrar"
	}
}
