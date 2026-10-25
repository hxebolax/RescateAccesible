/*
 * Basado en ServiceListTest.kt y MenuLabelsTest.kt de Shortcut Menu
 * (https://github.com/aaron-gh/shortcut-menu), de aaron-gh, con licencia Apache 2.0.
 */
package com.xebolax.rescate.sistema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListaServiciosTest {
	private val atajo = "com.xebolax.rescate/com.xebolax.rescate.servicio.ServicioAtajo"
	private val backtalk = "fyi.quin.backtalk/com.google.android.marvin.talkback.TalkBackService"
	private val talkback = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
	private val seleccionar = "com.google.android.marvin.talkback/com.google.android.accessibility.selecttospeak.SelectToSpeakService"
	private val lectores = setOf(backtalk, talkback)

	@Test
	fun leeYUne() {
		assertEquals(listOf("a/b", "c/d"), ListaServicios.leer("a/b:c/d"))
		assertEquals(emptyList<String>(), ListaServicios.leer(null))
		assertEquals(emptyList<String>(), ListaServicios.leer(""))
		assertEquals("a/b:c/d", ListaServicios.unir(listOf("a/b", "c/d")))
	}

	@Test
	fun laFormaCortaEsIgualALaLarga() {
		assertTrue(ListaServicios.contiene(listOf("com.ejemplo/.Servicio"), "com.ejemplo/com.ejemplo.Servicio"))
		assertFalse(ListaServicios.contiene(listOf("com.ejemplo/.Otro"), "com.ejemplo/com.ejemplo.Servicio"))
	}

	@Test
	fun activarAnadeElServicioYQuitaElAtajo() {
		assertEquals(listOf(backtalk), ListaServicios.alternar(listOf(atajo), backtalk, setOf(atajo), lectores))
	}

	@Test
	fun desactivarQuitaElServicioYElAtajo() {
		assertEquals(listOf(seleccionar), ListaServicios.alternar(listOf(backtalk, atajo, seleccionar), backtalk, setOf(atajo), lectores))
	}

	@Test
	fun activarUnLectorApagaElOtro() {
		assertEquals(listOf(seleccionar, talkback), ListaServicios.alternar(listOf(backtalk, seleccionar, atajo), talkback, setOf(atajo), lectores))
	}

	@Test
	fun activarOtroServicioConservaElLector() {
		assertEquals(listOf(backtalk, seleccionar), ListaServicios.alternar(listOf(backtalk, atajo), seleccionar, setOf(atajo), lectores))
	}

	@Test
	fun cambiarDeLectorParaPrimeroElViejo() {
		val activados = listOf(backtalk, seleccionar, atajo)
		val despues = ListaServicios.alternar(activados, talkback, setOf(atajo), lectores)
		assertEquals(listOf(backtalk), ListaServicios.pararAntes(activados, despues, lectores))
	}

	@Test
	fun arrancarUnLectorSinNingunoNoEspera() {
		val activados = listOf(seleccionar, atajo)
		val despues = ListaServicios.alternar(activados, backtalk, setOf(atajo), lectores)
		assertEquals(emptyList<String>(), ListaServicios.pararAntes(activados, despues, lectores))
	}

	@Test
	fun modoSeguroApagaLosLectoresYLosDevuelve() {
		val activados = listOf(talkback, seleccionar, atajo)
		val sin = ListaServicios.sinLectores(activados, lectores)
		assertEquals(listOf(seleccionar, atajo), sin)
		assertEquals(listOf(seleccionar, talkback), ListaServicios.conLectoresDeVuelta(sin, listOf(talkback), setOf(atajo)))
	}

	@Test
	fun nombresRepetidosLlevanElFabricante() {
		val nombres = NombresServicios.distintos(listOf(
			NombresServicios.Servicio("TalkBack", "com.google.android.marvin.talkback"),
			NombresServicios.Servicio("TalkBack", "com.samsung.android.accessibility.talkback"),
			NombresServicios.Servicio("Jieshuo", "com.nirenr.talkman"),
		))
		assertEquals(listOf("TalkBack (Google)", "TalkBack (Samsung)", "Jieshuo"), nombres)
	}

	@Test
	fun picoSoloLeeElAlfabetoLatino() {
		assertTrue(NombresServicios.pronunciable("TalkBack"))
		assertTrue(NombresServicios.pronunciable("Lector 2"))
		assertFalse(NombresServicios.pronunciable("读屏"))
	}
}
