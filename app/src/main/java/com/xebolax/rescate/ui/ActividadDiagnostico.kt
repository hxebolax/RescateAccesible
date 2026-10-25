package com.xebolax.rescate.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.xebolax.rescate.R
import com.xebolax.rescate.accesibilidad.conEspera
import com.xebolax.rescate.accesibilidad.mensaje
import com.xebolax.rescate.diagnostico.Diagnostico
import com.xebolax.rescate.diagnostico.Gravedad
import com.xebolax.rescate.diagnostico.Guia
import com.xebolax.rescate.diagnostico.Hallazgo
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.sistema.Servicios
import com.xebolax.rescate.voz.VozEmergencia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * El diagnóstico en pantalla, para usarlo con el lector funcionando (o con
 * alguien que ve): los mismos hallazgos y reparaciones que el menú de rescate,
 * con el resultado en la misma pantalla.
 */
class ActividadDiagnostico : ActividadBase(), Guia {
	private lateinit var p: Pantalla
	private var hallazgos: List<Hallazgo> = emptyList()

	override val actividad: Activity get() = this
	override val vozRescate: VozEmergencia? get() = null

	override fun abrirFuera(intent: Intent, explicacion: String): Boolean =
		try {
			startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
			true
		} catch (e: Exception) {
			Registro.w("Diagnostico", "No se pudo abrir ${intent.action}", e)
			mensaje(getString(R.string.ui_diagnostico), getString(R.string.ui_no_se_abre))
			false
		}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		p = pantalla(getString(R.string.ui_diagnostico))
		diagnosticar()
	}

	private fun diagnosticar() {
		lifecycleScope.launch {
			try {
				hallazgos = conEspera(getString(R.string.ui_diagnostico), getString(R.string.pag_diagnosticando)) { Diagnostico(this@ActividadDiagnostico).ejecutar() }
				mostrar()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e("Diagnostico", "Falló el diagnóstico", e)
				mensaje(getString(R.string.ui_diagnostico), getString(R.string.rescate_error_general))
			}
		}
	}

	private fun mostrar() {
		p.vaciar()
		val problemas = hallazgos.filter { it.gravedad == Gravedad.GRAVE || it.gravedad == Gravedad.AVISO }
		p.estadoVivo(if (problemas.isEmpty()) getString(R.string.pag_sin_problemas) else resources.getQuantityString(R.plurals.pag_problemas, problemas.size, problemas.size))
		p.boton(getString(R.string.ui_repetir_diagnostico)) { diagnosticar() }
		val permiso = Servicios(this).puedeEscribir()
		for (g in listOf(Gravedad.GRAVE, Gravedad.AVISO, Gravedad.INFO, Gravedad.BIEN)) {
			val grupo = hallazgos.filter { it.gravedad == g }
			if (grupo.isEmpty()) continue
			p.encabezado(getString(when (g) {
				Gravedad.GRAVE -> R.string.ui_diag_graves
				Gravedad.AVISO -> R.string.ui_diag_avisos
				Gravedad.INFO -> R.string.ui_diag_info
				Gravedad.BIEN -> R.string.ui_diag_bien
			}))
			for (h in grupo) {
				p.estado(h.titulo, when (g) {
					Gravedad.BIEN -> true
					Gravedad.GRAVE, Gravedad.AVISO -> false
					Gravedad.INFO -> null
				})
				if (g != Gravedad.BIEN) p.parrafo(h.detalle)
				for (r in h.reparaciones) {
					val texto = if (r.necesitaPermiso && !permiso) getString(R.string.pag_necesita_permiso, r.texto) else r.texto
					p.botonSecundario(texto) { reparar(h, r) }
				}
			}
		}
	}

	private fun reparar(h: Hallazgo, r: com.xebolax.rescate.diagnostico.Reparacion) {
		if (r.necesitaPermiso && !Servicios(this).puedeEscribir()) {
			mensaje(r.texto, getString(R.string.pag_permiso_explicacion))
			return
		}
		lifecycleScope.launch {
			try {
				val resultado = conEspera(h.titulo) { r.hacer(this@ActividadDiagnostico) }
				if (resultado != null) mensaje(r.texto, resultado) { diagnosticar() }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e("Diagnostico", "Falló una reparación", e)
				mensaje(r.texto, getString(R.string.rescate_error_general))
			}
		}
	}
}
