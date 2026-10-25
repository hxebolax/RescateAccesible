package com.xebolax.rescate.guia

import org.junit.Assert.assertEquals
import org.junit.Test

/** Comparación de textos de pantalla para encontrar botones de Ajustes. */
class TextoPantallaTest {
	@Test
	fun normalizaTildesMayusculasYEspacios() {
		assertEquals("forzar detencion", TextoPantalla.normalizar("  Forzar   detención… "))
		assertEquals("", TextoPantalla.normalizar(null))
	}

	@Test
	fun encajeExactoYParcial() {
		assertEquals(2, TextoPantalla.encaje("FORZAR DETENCIÓN", listOf("Forzar detención")))
		assertEquals(1, TextoPantalla.encaje("Usar TalkBack", listOf("TalkBack")))
		assertEquals(0, TextoPantalla.encaje("Desinstalar", listOf("Forzar detención")))
		// Candidatos de menos de tres letras solo valen si coinciden enteros
		assertEquals(0, TextoPantalla.encaje("Book", listOf("OK")))
		assertEquals(2, TextoPantalla.encaje("OK", listOf("OK")))
	}
}
