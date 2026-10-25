package com.xebolax.rescate.voz

import com.xebolax.rescate.rescate.FilasMenu
import com.xebolax.rescate.servicio.PulsacionesRapidas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/** Pruebas de la voz de rescate, el menú y las grabaciones (lógica sin Android). */
class VozTest {
	@Test
	fun eligeLaVozPicoPorIdioma() {
		assertEquals(VozPico.ESPANOL, VozPico.para(Locale("es", "MX")))
		assertEquals(VozPico.INGLES_EEUU, VozPico.para(Locale.US))
		assertEquals(VozPico.INGLES_GB, VozPico.para(Locale("en", "IE")))
		// Un idioma que Pico no tiene: español, el idioma por defecto de la app
		assertEquals(VozPico.ESPANOL, VozPico.para(Locale.CHINA))
	}

	@Test
	fun elMarcadoNoDejaPasarEtiquetas() {
		assertEquals("a b", Marcado.limpiar("a<b"))
		assertEquals("Hola. adiós", Marcado.limpiar("Hola\nadiós"))
		assertEquals("texto", Marcado.preparar("texto", 100, 100))
		assertEquals("<speed level='150'><pitch level='100'>texto</pitch></speed>", Marcado.preparar("texto", 150, 100))
		// Fuera de los límites de Pico se recorta
		assertTrue(Marcado.preparar("t", 900, 10).contains("level='500'"))
	}

	@Test
	fun partePorFrasesSinPerderTexto() {
		val texto = "Primera frase. Segunda frase: con dos puntos; y más. " + "palabra ".repeat(100)
		val frases = Marcado.frases(texto, 120)
		assertTrue(frases.all { it.length <= 121 })
		assertEquals(texto.replace(Regex("\\s+"), " ").trim(), frases.joinToString(" ").replace(Regex("\\s+"), " ").trim())
	}

	@Test
	fun ulawIdaYVueltaConPocoError() {
		val muestras = ShortArray(2000) { (8000 * kotlin.math.sin(it / 10.0)).toInt().toShort() }
		val vuelta = Grabaciones.deUlaw(Grabaciones.aUlaw(muestras))
		assertEquals(muestras.size, vuelta.size)
		for (i in muestras.indices) assertTrue(abs(muestras[i] - vuelta[i]) <= 260)
	}

	@Test
	fun remuestreaDe22050A16000() {
		val m = ShortArray(22050) { 1000 }
		val r = Grabaciones.remuestrear(m, 22050, 16000)
		assertEquals(16000, r.size)
		assertTrue(r.all { it.toInt() == 1000 })
	}

	@Test
	fun recortaElSilencio() {
		val m = ShortArray(3200) + ShortArray(1600) { 5000 } + ShortArray(3200)
		val r = Grabaciones.recortarSilencio(m)
		assertTrue(r.size < m.size)
		assertTrue(r.size >= 1600)
	}

	@Test
	fun pitidosSinChasquidos() {
		val t = Pitidos.tono(440.0, 100)
		assertEquals(Pitidos.FRECUENCIA / 10, t.size)
		assertEquals(0, t.first().toInt())
		assertTrue(abs(t.last().toInt()) < 2000)
	}

	@Test
	fun filaBajoElDedo() {
		assertEquals(0, FilasMenu.filaEn(0f, 1000, 4))
		assertEquals(3, FilasMenu.filaEn(999f, 1000, 4))
		assertEquals(3, FilasMenu.filaEn(5000f, 1000, 4))
		assertEquals(-1, FilasMenu.filaEn(10f, 1000, 0))
	}

	/* Basado en QuickPressesTest.kt de Shortcut Menu (aaron-gh, Apache License 2.0). */
	@Test
	fun tresPulsacionesRapidas() {
		val p = PulsacionesRapidas(3, 600)
		assertFalse(p.pulsar(0))
		assertFalse(p.pulsar(500))
		assertTrue(p.pulsar(1000))
		assertFalse(p.pulsar(1500))
		// Demasiado separadas: vuelve a contar
		assertFalse(p.pulsar(3000))
		assertFalse(p.pulsar(3500))
		assertTrue(p.pulsar(4000))
	}

	@Test
	fun versionesDelServidor() {
		val comparar = com.xebolax.rescate.actualizaciones.Actualizador::compararVersiones
		assertTrue(comparar("1.2.10", "1.2.9") > 0)
		assertTrue(comparar("1.0", "1.0.0") == 0)
		assertTrue(comparar("0.9.9", "1.0") < 0)
	}
}
