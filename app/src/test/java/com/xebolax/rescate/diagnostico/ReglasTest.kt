package com.xebolax.rescate.diagnostico

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas del diagnóstico con datos simulados. */
class ReglasTest {
	@Test
	fun estadoDelLector() {
		assertEquals(Reglas.EstadoLector.ACTIVO, Reglas.estadoLector(activado = true, enMarcha = true))
		assertEquals(Reglas.EstadoLector.CAIDO, Reglas.estadoLector(activado = true, enMarcha = false))
		assertEquals(Reglas.EstadoLector.APAGADO, Reglas.estadoLector(activado = false, enMarcha = false))
	}

	@Test
	fun volumenCeroEsGraveYBajoEsAviso() {
		assertEquals(Gravedad.GRAVE, Reglas.gravedadVolumen(0))
		assertEquals(Gravedad.AVISO, Reglas.gravedadVolumen(10))
		assertNull(Reglas.gravedadVolumen(60))
		// -1: no se pudo leer, no es un problema
		assertNull(Reglas.gravedadVolumen(-1))
	}

	@Test
	fun velocidadYTonoExtremos() {
		assertFalse(Reglas.velocidadExtrema(null))
		assertFalse(Reglas.velocidadExtrema(100))
		assertTrue(Reglas.velocidadExtrema(20))
		assertTrue(Reglas.velocidadExtrema(500))
		assertFalse(Reglas.tonoExtremo(150))
		assertTrue(Reglas.tonoExtremo(250))
	}

	@Test
	fun atajoDeVolumen() {
		val propio = "com.xebolax.rescate/com.xebolax.rescate.servicio.ServicioAtajo"
		val corto = "com.xebolax.rescate/.servicio.ServicioAtajo"
		val talkback = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
		val normalizar = com.xebolax.rescate.sistema.ListaServicios::normalizar
		assertEquals(Reglas.EstadoAtajo.SIN_ASIGNAR, Reglas.estadoAtajo(emptyList(), propio, normalizar))
		assertEquals(Reglas.EstadoAtajo.OTRO, Reglas.estadoAtajo(listOf(talkback), propio, normalizar))
		assertEquals(Reglas.EstadoAtajo.SOLO_NUESTRO, Reglas.estadoAtajo(listOf(corto), propio, normalizar))
		assertEquals(Reglas.EstadoAtajo.COMPARTIDO, Reglas.estadoAtajo(listOf(talkback, propio), propio, normalizar))
	}

	@Test
	fun atajoEnPantallaDeBloqueo() {
		assertTrue(Reglas.atajoEnBloqueo("1", preguntaMostrada = false))
		assertFalse(Reglas.atajoEnBloqueo("0", preguntaMostrada = true))
		// Sin valor, como Android: activo si ya se mostró la pregunta de la primera vez
		assertTrue(Reglas.atajoEnBloqueo(null, preguntaMostrada = true))
		assertFalse(Reglas.atajoEnBloqueo(null, preguntaMostrada = false))
	}

	@Test
	fun balance() {
		assertFalse(Reglas.balanceExtremo(null))
		assertFalse(Reglas.balanceExtremo(0.1f))
		assertTrue(Reglas.balanceExtremo(-0.8f))
		assertTrue(Reglas.balanceExtremo(1f))
	}

	@Test
	fun idiomaDelSistema() {
		assertFalse(Reglas.idiomaRaro("es", "es", null))
		assertFalse(Reglas.idiomaRaro("en", "es", null))
		assertTrue(Reglas.idiomaRaro("zh", "es", null))
		// Cambió desde la configuración guardada, aunque sea un idioma que la voz de rescate habla
		assertTrue(Reglas.idiomaRaro("en", "es", "es-ES"))
		assertFalse(Reglas.idiomaRaro("es", "es", "es-ES"))
	}

	@Test
	fun elMasGraveEsElPrimeroGrave() {
		val lista = listOf(
			Hallazgo("a", Gravedad.AVISO, "a", ""),
			Hallazgo("b", Gravedad.GRAVE, "b", ""),
			Hallazgo("c", Gravedad.GRAVE, "c", ""),
		)
		assertEquals("b", Reglas.masGrave(lista)?.id)
		assertNull(Reglas.masGrave(lista.take(1)))
	}
}
