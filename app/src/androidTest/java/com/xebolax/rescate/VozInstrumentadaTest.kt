package com.xebolax.rescate

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xebolax.rescate.diagnostico.ProbadorTts
import com.xebolax.rescate.rescate.TextosEsenciales
import com.xebolax.rescate.voz.EspeakNativo
import com.xebolax.rescate.voz.Grabaciones
import com.xebolax.rescate.voz.HablantePico
import com.xebolax.rescate.voz.Marcado
import com.xebolax.rescate.voz.MotorGrabacion
import com.xebolax.rescate.voz.PicoNativo
import com.xebolax.rescate.voz.VozPico
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Las voces incluidas funcionan de verdad en el dispositivo, sin el motor de voz
 * del sistema: Pico con sus tres voces, eSpeak NG en español e inglés, el motor
 * de voz propio que ve el sistema, y las grabaciones de respaldo.
 */
@RunWith(AndroidJUnit4::class)
class VozInstrumentadaTest {
	private val app = InstrumentationRegistry.getInstrumentation().targetContext

	@Test
	fun picoHablaConLasTresVoces() {
		for (voz in VozPico.entries) {
			val pico = PicoNativo.abrir(app, voz)
			assertNotNull("Pico no abre ${voz.codigo}", pico)
			val muestras = pico!!.sintetizar(Marcado.preparar("Rescate Accesible. Prueba de voz.", 100, 100))
			pico.cerrar()
			assertTrue("Pico ${voz.codigo} no produce sonido", (muestras?.size ?: 0) > PicoNativo.FRECUENCIA / 2)
			Log.i("Voz", "Pico ${voz.codigo}: ${muestras!!.size} muestras")
		}
	}

	@Test
	fun espeakHablaEnEspanolEIngles() {
		if (!BuildConfig.INCLUIR_ESPEAK) return
		assertTrue("eSpeak no arranca", EspeakNativo.iniciar(app))
		for (locale in listOf(Locale("es", "ES"), Locale.US, Locale.UK)) {
			val muestras = EspeakNativo.sintetizar(app, "Rescate Accesible. Prueba de voz.", locale, 100, 100)
			assertTrue("eSpeak $locale no produce sonido", (muestras?.size ?: 0) > EspeakNativo.frecuenciaMuestras / 2)
			Log.i("Voz", "eSpeak $locale: ${muestras!!.size} muestras a ${EspeakNativo.frecuenciaMuestras} Hz")
		}
	}

	/** El sistema ve el motor de voz propio y habla con él (lo mismo que haría TalkBack). */
	@Test
	fun motorPropioDelSistemaHabla() = runBlocking {
		for (locale in listOf(Locale("es", "ES"), Locale.US)) {
			val prueba = ProbadorTts(app).probar(app.packageName, locale, "Prueba del motor de rescate")
			Log.i("Voz", "Motor propio $locale: $prueba")
			assertTrue("El motor propio no arranca", prueba.arranca)
			assertTrue("El motor propio no habla en $locale", prueba.funciona)
		}
	}

	@Test
	fun grabacionesIdaYVuelta() {
		val grabaciones = Grabaciones(app)
		val texto = "Prueba de grabación de respaldo"
		val pico = PicoNativo.abrir(app, VozPico.ESPANOL)!!
		val muestras = pico.sintetizar(Marcado.preparar(texto, 100, 100))!!
		pico.cerrar()
		grabaciones.guardar(texto, "es", MotorGrabacion.PICO, muestras, PicoNativo.FRECUENCIA)
		val leidas = grabaciones.leer(texto, Locale("es", "ES"))
		assertTrue("La grabación no se lee", (leidas?.size ?: 0) > 0)
	}

	/**
	 * Genera las grabaciones de respaldo que viajan dentro de la app (velocidad y
	 * tono normales), con Pico y con eSpeak, en español y en inglés. Quedan en
	 * Android/data/<paquete>/files/grabaciones-generadas/ y las recoge
	 * herramientas\generar_grabaciones.ps1 para ponerlas en assets/grabaciones.
	 */
	@Test
	fun generarGrabacionesIncluidas() {
		val destino = File(app.getExternalFilesDir(null), "grabaciones-generadas").apply { deleteRecursively(); mkdirs() }
		val grabaciones = Grabaciones(app)
		var total = 0
		for (locale in listOf(Locale("es", "ES"), Locale.US)) {
			val idioma = grabaciones.idioma(locale)
			val textos = TextosEsenciales.lista(app, locale).distinct()
			val pico = PicoNativo.abrir(app, VozPico.para(locale))!!
			for (texto in textos) {
				val m = HablantePico.unir(Marcado.frases(Marcado.limpiar(texto)).map { pico.sintetizar(Marcado.preparar(it, 100, 100))!! })
				grabaciones.guardar(texto, idioma, MotorGrabacion.PICO, m, PicoNativo.FRECUENCIA, destino)
				total++
				if (BuildConfig.INCLUIR_ESPEAK) {
					val e = HablantePico.unir(Marcado.frases(Marcado.limpiar(texto)).map { EspeakNativo.sintetizar(app, it, locale, 100, 100)!! })
					grabaciones.guardar(texto, idioma, MotorGrabacion.ESPEAK, e, EspeakNativo.frecuenciaMuestras, destino)
					total++
				}
			}
			pico.cerrar()
		}
		Log.i("Grabaciones", "Generadas $total grabaciones en $destino")
		assertTrue(total > 0)
	}
}
