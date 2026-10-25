package com.xebolax.rescate.voz

import android.content.Context
import com.xebolax.rescate.nucleo.Almacen
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

/** Un juego de grabaciones: el motor con que se hizo y el idioma. */
enum class MotorGrabacion(val carpeta: String) { PICO("pico"), ESPEAK("espeak") }

/**
 * Mensajes de respaldo grabados: los textos fijos del rescate (instrucciones y
 * opciones del menú) ya convertidos en sonido, con Pico y con eSpeak, en
 * español y en inglés.
 *
 * Si un día no funciona ninguna síntesis (bibliotecas dañadas, memoria agotada),
 * el rescate todavía puede decir sus instrucciones con estas grabaciones, en el
 * idioma de la app (que, si no se eligió otro, es el del sistema). Se buscan en
 * este orden:
 *
 * 1. Las grabadas en este móvil (con la velocidad y el tono del usuario), del
 *    motor preferido y luego del otro.
 * 2. Las que trae la app dentro del APK (assets/grabaciones/<motor>-<idioma>/),
 *    generadas al compilar con las mismas voces.
 *
 * Formato: µ-law de 8 bits, 16 kHz, mono, sin cabecera (la mitad que PCM y se
 * descodifica sin depender de los códecs del sistema). El nombre del archivo es
 * un resumen del idioma y del texto, así que no se guarda el texto.
 */
class Grabaciones(contexto: Context) {
	private val app = contexto.applicationContext
	private val prefs = Preferencias(app)
	private val carpeta = File(Almacen.protegido(app).noBackupFilesDir, "grabaciones")

	/** El idioma de las grabaciones para [locale]: español o inglés (el resto, español). */
	fun idioma(locale: Locale): String = if (locale.language == "en") "en" else "es"

	fun clave(texto: String, idioma: String): String {
		val resumen = MessageDigest.getInstance("SHA-1").digest((idioma + "|" + normalizar(texto)).toByteArray())
		return resumen.joinToString("") { "%02x".format(it) }.take(24)
	}

	/** El orden de los motores: el preferido por el usuario primero. */
	fun orden(): List<MotorGrabacion> =
		if (prefs.motorRescate == Preferencias.MOTOR_ESPEAK) listOf(MotorGrabacion.ESPEAK, MotorGrabacion.PICO)
		else listOf(MotorGrabacion.PICO, MotorGrabacion.ESPEAK)

	/** Las muestras grabadas de [texto] en el idioma de [locale] (16 kHz), o null si no hay ninguna. */
	fun leer(texto: String, locale: Locale): ShortArray? {
		val idioma = idioma(locale)
		val nombre = clave(texto, idioma) + EXTENSION
		for (motor in orden()) {
			val local = File(File(carpeta, motor.carpeta + "-" + idioma), nombre)
			if (local.exists()) runCatching { return deUlaw(local.readBytes()) }
				.onFailure { Registro.w(ETIQUETA, "Grabación local ilegible", it) }
		}
		for (motor in orden()) {
			try {
				return app.assets.open("grabaciones/${motor.carpeta}-$idioma/$nombre").use { deUlaw(it.readBytes()) }
			} catch (e: java.io.FileNotFoundException) {
				continue
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "Grabación incluida ilegible", e)
			}
		}
		return null
	}

	/** Si hay grabación de [texto] hecha en este móvil con [motor] e [idioma]. */
	fun hayLocal(texto: String, idioma: String, motor: MotorGrabacion): Boolean =
		File(File(carpeta, motor.carpeta + "-" + idioma), clave(texto, idioma) + EXTENSION).exists()

	/** Guarda [muestras] (a [frecuencia] Hz) como grabación de [texto] en [destino] (por defecto, la carpeta de este móvil). */
	fun guardar(texto: String, idioma: String, motor: MotorGrabacion, muestras: ShortArray, frecuencia: Int, destino: File = carpeta) {
		val dir = File(destino, motor.carpeta + "-" + idioma).apply { mkdirs() }
		val archivo = File(dir, clave(texto, idioma) + EXTENSION)
		val parcial = File(dir, archivo.name + ".parcial")
		parcial.writeBytes(aUlaw(remuestrear(recortarSilencio(muestras), frecuencia, FRECUENCIA)))
		archivo.delete()
		parcial.renameTo(archivo)
	}

	/** Cuántas grabaciones hay hechas en este móvil. */
	fun cuantas(): Int = carpeta.walkTopDown().count { it.isFile && it.name.endsWith(EXTENSION) }

	fun borrarTodas() {
		carpeta.deleteRecursively()
	}

	companion object {
		private const val ETIQUETA = "Grabaciones"
		const val FRECUENCIA = 16000
		const val EXTENSION = ".ulaw"

		/** El mismo texto con otros espacios o mayúsculas es la misma grabación. */
		fun normalizar(texto: String): String = Marcado.limpiar(texto).lowercase().replace(Regex("\\s+"), " ").trim()

		/** Quita el silencio del principio y del final (ocupa sitio y retrasa el aviso). */
		fun recortarSilencio(m: ShortArray, umbral: Int = 200): ShortArray {
			var inicio = 0
			while (inicio < m.size && abs(m[inicio].toInt()) < umbral) inicio++
			var fin = m.size
			while (fin > inicio && abs(m[fin - 1].toInt()) < umbral) fin--
			if (inicio >= fin) return m
			val margen = FRECUENCIA / 20
			return m.copyOfRange((inicio - margen).coerceAtLeast(0), (fin + margen).coerceAtMost(m.size))
		}

		/** Cambia la frecuencia por interpolación lineal (eSpeak da 22 050 Hz; se guarda todo a 16 kHz). */
		fun remuestrear(m: ShortArray, de: Int, a: Int): ShortArray {
			if (de == a || m.isEmpty()) return m
			val n = (m.size.toLong() * a / de).toInt()
			return ShortArray(n) { i ->
				val pos = i.toDouble() * de / a
				val j = pos.toInt()
				val f = pos - j
				val x = m[min(j, m.lastIndex)].toInt()
				val y = m[min(j + 1, m.lastIndex)].toInt()
				(x + (y - x) * f).toInt().toShort()
			}
		}

		/** Codifica PCM de 16 bits en µ-law (G.711). */
		fun aUlaw(m: ShortArray): ByteArray = ByteArray(m.size) { aUlaw(m[it].toInt()) }

		private fun aUlaw(muestra: Int): Byte {
			val sesgo = 0x84
			val tope = 32635
			var s = muestra
			val signo = if (s < 0) 0x80 else 0
			if (s < 0) s = -s
			if (s > tope) s = tope
			s += sesgo
			var exponente = 7
			var mascara = 0x4000
			while (exponente > 0 && (s and mascara) == 0) {
				exponente--
				mascara = mascara shr 1
			}
			val mantisa = (s shr (exponente + 3)) and 0x0F
			return (signo or (exponente shl 4) or mantisa).inv().toByte()
		}

		/** Descodifica µ-law (G.711) a PCM de 16 bits. */
		fun deUlaw(b: ByteArray): ShortArray = ShortArray(b.size) { i ->
			val u = b[i].toInt().inv() and 0xFF
			val signo = u and 0x80
			val exponente = (u shr 4) and 0x07
			val mantisa = u and 0x0F
			var s = ((mantisa shl 3) + 0x84) shl exponente
			s -= 0x84
			(if (signo != 0) -s else s).toShort()
		}

		@Suppress("unused")
		private fun decibelios(x: Double) = 20 * ln(x) / ln(10.0)
	}
}
