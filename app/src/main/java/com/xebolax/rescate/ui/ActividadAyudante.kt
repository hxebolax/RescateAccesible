package com.xebolax.rescate.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.xebolax.rescate.R
import com.xebolax.rescate.diagnostico.Diagnostico
import com.xebolax.rescate.diagnostico.Gravedad
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.rescate.SesionRescate
import com.xebolax.rescate.sistema.Servicios
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Ayuda para quien ve la pantalla: brillo al máximo (solo en esta ventana, sin
 * permisos), letra grande y, en pocas frases, qué le pasa al móvil y qué hay
 * que tocar para devolverle la voz. Pensada para enseñársela a alguien.
 */
class ActividadAyudante : ActividadBase() {
	private lateinit var p: Pantalla

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		p = pantalla(getString(R.string.ayu_titulo))
		construir(emptyList())
		lifecycleScope.launch {
			try {
				val problemas = Diagnostico(this@ActividadAyudante).ejecutar()
					.filter { it.gravedad == Gravedad.GRAVE || it.gravedad == Gravedad.AVISO }
					.map { it.titulo + ". " + it.detalle }
				construir(problemas)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e("Ayudante", "No se pudo diagnosticar", e)
			}
		}
	}

	private fun construir(problemas: List<String>) {
		p.vaciar()
		p.tamanoTexto(p.parrafo(getString(R.string.ayu_intro)), 24f)
		p.encabezado(getString(R.string.ayu_que_pasa))
		if (problemas.isEmpty()) p.tamanoTexto(p.parrafo(getString(R.string.ayu_comprobando)), 22f)
		problemas.forEach { p.tamanoTexto(p.parrafo("• $it"), 22f) }
		p.encabezado(getString(R.string.ayu_como_ayudar))
		val lectores = Servicios(this).lectores()
		val nombre = lectores.firstOrNull()?.nombre ?: "TalkBack"
		listOf(
			getString(R.string.ayu_paso_1, nombre),
			getString(R.string.ayu_paso_2),
			getString(R.string.ayu_paso_3),
			getString(R.string.ayu_paso_4),
		).forEach { p.tamanoTexto(p.parrafo(it), 22f) }
		p.boton(getString(R.string.ayu_abrir_accesibilidad)) {
			runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
		}
		p.boton(getString(R.string.ayu_abrir_voz)) {
			val intent = android.content.Intent(com.xebolax.rescate.rescate.PaginasRescate.ACCION_AJUSTES_TTS)
			runCatching { startActivity(intent) }.onFailure { runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
		}
		p.boton(getString(R.string.ayu_volver_rescate)) {
			SesionRescate.empezar(this, SesionRescate.Origen.APP)
			finish()
		}
	}
}
