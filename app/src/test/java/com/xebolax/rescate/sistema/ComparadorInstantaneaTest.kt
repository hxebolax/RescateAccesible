package com.xebolax.rescate.sistema

import com.xebolax.rescate.sistema.ComparadorInstantanea.Diferencia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comparación de la configuración guardada con la actual. */
class ComparadorInstantaneaTest {
	private val talkback = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
	private val talkbackCorto = "com.google.android.marvin.talkback/.TalkBackService"
	private val interruptores = "com.google.android.marvin.talkback/com.android.switchaccess.SwitchAccessService"
	private val atajo = "com.xebolax.rescate/com.xebolax.rescate.servicio.ServicioAtajo"

	private fun mapa(vararg pares: Pair<String, String?>): Map<String, String?> = linkedMapOf(*pares)

	@Test
	fun igualesAunqueCambieLaFormaDeEscribirlos() {
		val guardada = mapa(ComparadorInstantanea.SERVICIOS to talkback, ComparadorInstantanea.VELOCIDAD to null, ComparadorInstantanea.ATAJO to "$atajo")
		val actual = mapa(ComparadorInstantanea.SERVICIOS to talkbackCorto, ComparadorInstantanea.VELOCIDAD to "100", ComparadorInstantanea.ATAJO to atajo)
		assertTrue(ComparadorInstantanea.comparar(guardada, actual).isEmpty())
	}

	@Test
	fun serviciosQueFaltanYSobran() {
		val guardada = mapa(ComparadorInstantanea.SERVICIOS to talkback)
		val actual = mapa(ComparadorInstantanea.SERVICIOS to interruptores)
		val d = ComparadorInstantanea.comparar(guardada, actual)
		assertEquals(listOf(Diferencia.FaltaServicio(talkback), Diferencia.SobraServicio(interruptores)), d)
	}

	@Test
	fun elServicioDelAtajoNoCuentaComoCambio() {
		val guardada = mapa(ComparadorInstantanea.SERVICIOS to talkback)
		val actual = mapa(ComparadorInstantanea.SERVICIOS to "$talkback:$atajo")
		assertTrue(ComparadorInstantanea.comparar(guardada, actual, setOf(atajo)).isEmpty())
	}

	@Test
	fun motorYVelocidadDistintos() {
		val guardada = mapa(ComparadorInstantanea.MOTOR to "com.google.android.tts", ComparadorInstantanea.VELOCIDAD to "100")
		val actual = mapa(ComparadorInstantanea.MOTOR to "com.samsung.SMT", ComparadorInstantanea.VELOCIDAD to "400")
		val d = ComparadorInstantanea.comparar(guardada, actual)
		assertEquals(2, d.size)
		assertEquals(Diferencia.Valor(ComparadorInstantanea.MOTOR, "com.google.android.tts", "com.samsung.SMT"), d[0])
	}

	@Test
	fun sinDatoGuardadoOActualNoHayDiferencia() {
		val guardada = mapa(ComparadorInstantanea.MOTOR to "com.google.android.tts")
		assertTrue(ComparadorInstantanea.comparar(guardada, mapa()).isEmpty())
		assertTrue(ComparadorInstantanea.comparar(mapa(), mapa(ComparadorInstantanea.MOTOR to "x")).isEmpty())
	}

	@Test
	fun restaurarConservaLosServiciosPropios() {
		val lista = ComparadorInstantanea.serviciosARestaurar(talkback, listOf(atajo, talkbackCorto))
		assertEquals(listOf(talkback, atajo), lista)
	}
}
