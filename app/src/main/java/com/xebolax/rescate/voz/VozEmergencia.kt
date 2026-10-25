package com.xebolax.rescate.voz

import android.content.Context
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import java.util.Locale

/**
 * La voz del rescate (EmergencySpeechEngine): habla siempre, pase lo que pase,
 * bajando de escalón cuando un motor falla.
 *
 * 1. El motor del sistema que el usuario eligió como voz de rescate, si eligió
 *    uno y se comprobó que funcionaba (opcional).
 * 2. Pico, incluido en la app: no depende del motor del sistema ni de Internet.
 * 3. eSpeak NG, también incluido y con otro código y otros datos que Pico (si
 *    el usuario lo prefiere, va antes que Pico).
 * 4. Las grabaciones de respaldo de los textos fijos, hechas cuando Pico iba bien.
 * 5. Pitidos y vibraciones (siempre funcionan si hay altavoz o vibrador).
 *
 * Al empezar una sesión pide el foco de audio y sube el volumen si está tan
 * bajo que no se oiría.
 */
class VozEmergencia(contexto: Context) : Hablante {
	enum class Motor { SISTEMA, PICO, ESPEAK, GRABACIONES, PITIDOS }

	private val app = contexto.applicationContext
	private val prefs = Preferencias(app)
	val salida = SalidaAudio(app)
	private val cola = ColaAudio()
	val pico = HablantePico(app, cola, salida)
	val espeak = HablanteEspeak(app, cola, salida)
	val grabaciones = Grabaciones(app)
	val pitidos = Pitidos(app, cola, salida)
	private var sistema: HablanteSistema? = prefs.motorRescate
		.takeIf { it != Preferencias.MOTOR_PICO && it != Preferencias.MOTOR_ESPEAK && it.isNotEmpty() }
		?.let { HablanteSistema(app, it, salida) }

	/** El orden en que se prueban los motores: el del sistema elegido, luego los incluidos según la preferencia. */
	private val orden: List<Motor> = buildList {
		if (sistema != null) add(Motor.SISTEMA)
		if (prefs.motorRescate == Preferencias.MOTOR_ESPEAK) {
			add(Motor.ESPEAK)
			add(Motor.PICO)
		} else {
			add(Motor.PICO)
			add(Motor.ESPEAK)
		}
		add(Motor.GRABACIONES)
		add(Motor.PITIDOS)
	}

	/** El motor con el que se habla ahora. */
	var motor: Motor = orden.first()
		private set

	/** El motor que sigue a [m] en el orden de respaldo. */
	private fun siguiente(m: Motor): Motor = orden.getOrNull(orden.indexOf(m) + 1) ?: Motor.PITIDOS

	/** Se llama cuando un motor falla y se pasa al siguiente (para explicarlo al usuario). */
	var alCambiarMotor: ((Motor) -> Unit)? = null

	override val nombre: String get() = motor.name

	/** Comprueba los motores antes de hablar y deja elegido el primero que funciona; [listo] recibe cuál. */
	fun preparar(listo: (Motor) -> Unit) {
		val s = sistema
		if (s != null) {
			s.alEstarListo { ok ->
				if (ok) {
					motor = Motor.SISTEMA
					listo(motor)
				} else {
					Registro.w(ETIQUETA, "La voz de rescate del sistema no arranca: se usa Pico")
					s.cerrar()
					sistema = null
					comprobarPico(listo)
				}
			}
		} else {
			comprobarPico(listo)
		}
	}

	/** Comprueba los motores incluidos en su orden y se queda con el primero que habla. */
	private fun comprobarPico(listo: (Motor) -> Unit) {
		val incluidos = orden.filter { it == Motor.PICO || it == Motor.ESPEAK }
		fun probar(i: Int) {
			val m = incluidos.getOrNull(i)
			if (m == null) {
				Registro.e(ETIQUETA, "Ni Pico ni eSpeak funcionan: se usan las grabaciones y los pitidos")
				motor = Motor.GRABACIONES
				listo(motor)
				return
			}
			val alProbar: (Boolean) -> Unit = { ok ->
				if (ok) {
					motor = m
					listo(motor)
				} else {
					Registro.w(ETIQUETA, "La voz incluida $m no funciona")
					probar(i + 1)
				}
			}
			if (m == Motor.PICO) pico.comprobar(alProbar) else espeak.comprobar(alProbar)
		}
		probar(0)
	}

	/** Empieza una sesión de rescate: foco de audio y volumen suficiente. Devuelve si subió el volumen. */
	fun empezarSesion(): Boolean {
		salida.pedirFoco()
		return salida.asegurarVolumen()
	}

	override fun hablar(texto: String, vaciar: Boolean, alTerminar: ((Resultado) -> Unit)?) {
		decirCon(motor, texto, vaciar, alTerminar)
	}

	private fun decirCon(m: Motor, texto: String, vaciar: Boolean, alTerminar: ((Resultado) -> Unit)?) {
		when (m) {
			Motor.SISTEMA -> {
				val s = sistema
				if (s == null) {
					decirCon(siguiente(Motor.SISTEMA), texto, vaciar, alTerminar)
					return
				}
				if (vaciar) cola.vaciar()
				s.hablar(texto, vaciar) { r ->
					if (r == Resultado.FALLO && motor == Motor.SISTEMA) {
						Registro.w(ETIQUETA, "La voz del sistema falló: se pasa a Pico")
						s.cerrar()
						sistema = null
						val otro = siguiente(Motor.SISTEMA)
						bajar(otro)
						decirCon(otro, texto, true, alTerminar)
					} else alTerminar?.invoke(r)
				}
			}
			Motor.PICO, Motor.ESPEAK -> (if (m == Motor.PICO) pico else espeak).hablar(texto, vaciar) { r ->
				if (r == Resultado.FALLO) {
					val otro = siguiente(m)
					Registro.w(ETIQUETA, "La voz $m falló: se pasa a $otro")
					bajar(otro)
					decirCon(otro, texto, true, alTerminar)
				} else alTerminar?.invoke(r)
			}
			Motor.GRABACIONES -> {
				val locale = Idioma.efectivo(app)
				cola.encolar(vaciar, { sigue ->
					val muestras = grabaciones.leer(texto, locale) ?: return@encolar Resultado.FALLO
					if (salida.reproducir(muestras, Grabaciones.FRECUENCIA, sigue)) Resultado.HABLADO else Resultado.FALLO
				}) { r ->
					// Sin grabación de este texto: un aviso sonoro en su lugar (no cambia de motor: otros textos sí la tienen)
					if (r == Resultado.FALLO) pitidos.tocar(Senal.ATENCION, vaciar = false, alTerminar = alTerminar)
					else alTerminar?.invoke(r)
				}
			}
			Motor.PITIDOS -> pitidos.tocar(Senal.ATENCION, vaciar, alTerminar)
		}
	}

	private fun bajar(nuevo: Motor) {
		if (orden.indexOf(nuevo) > orden.indexOf(motor)) {
			motor = nuevo
			alCambiarMotor?.invoke(nuevo)
		}
	}

	/** Una señal sonora y de vibración, sin cortar la voz. */
	fun senal(s: Senal) = pitidos.tocar(s)

	override fun callar() {
		cola.vaciar()
		espeak.callar()
		sistema?.callar()
	}

	override fun cerrar() {
		sistema?.cerrar()
		sistema = null
		pico.cerrar()
		cola.cerrar()
		salida.soltarFoco()
	}

	/**
	 * Graba los textos fijos como respaldo con las dos voces incluidas (Pico y
	 * eSpeak) y en los dos idiomas, en el hilo de sonido (sin cortar lo que
	 * suene). [textos] da los textos de cada idioma; [alTerminar] recibe cuántas
	 * grabaciones hay hechas (nuevas o ya existentes).
	 */
	fun grabarRespaldo(idiomas: List<Locale>, textos: (Locale) -> List<String>, alTerminar: (Int) -> Unit) {
		val velocidad = prefs.velocidad
		val tono = prefs.tono
		val porIdioma = idiomas.associateWith { textos(it) }
		cola.enHilo {
			var hechas = 0
			for ((locale, lista) in porIdioma) {
				val idioma = grabaciones.idioma(locale)
				for (texto in lista.distinct()) {
					for (motor in MotorGrabacion.entries) {
						if (grabaciones.hayLocal(texto, idioma, motor)) {
							hechas++
							continue
						}
						val muestras = when (motor) {
							MotorGrabacion.PICO -> pico.muestras(texto, VozPico.para(locale), velocidad, tono)
							MotorGrabacion.ESPEAK -> sintetizarEspeak(texto, locale, velocidad, tono)
						}
						val frecuencia = if (motor == MotorGrabacion.PICO) PicoNativo.FRECUENCIA else EspeakNativo.frecuenciaMuestras
						if (muestras == null || muestras.isEmpty() || frecuencia <= 0) continue
						try {
							grabaciones.guardar(texto, idioma, motor, muestras, frecuencia)
							hechas++
						} catch (e: Exception) {
							Registro.e(ETIQUETA, "No se pudo guardar una grabación de respaldo", e)
						}
					}
				}
			}
			android.os.Handler(android.os.Looper.getMainLooper()).post { alTerminar(hechas) }
		}
	}

	/** Las muestras de eSpeak de un texto entero (por frases), o null si eSpeak no está. */
	private fun sintetizarEspeak(texto: String, locale: Locale, velocidad: Int, tono: Int): ShortArray? {
		if (!EspeakNativo.disponible()) return null
		val partes = Marcado.frases(Marcado.limpiar(texto)).map { EspeakNativo.sintetizar(app, it, locale, velocidad, tono) ?: return null }
		return HablantePico.unir(partes)
	}

	companion object {
		private const val ETIQUETA = "VozEmergencia"
	}
}
