package com.xebolax.rescate.ui

import android.content.Intent
import android.os.Bundle
import androidx.core.content.FileProvider
import com.xebolax.rescate.BuildConfig
import com.xebolax.rescate.R
import com.xebolax.rescate.accesibilidad.mensaje
import com.xebolax.rescate.actualizaciones.GestorActualizaciones
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.permiso.ActividadPermiso
import com.xebolax.rescate.reparar.ActividadCopias
import java.io.File

/**
 * Pantalla principal (modo Preparación): qué falta para que el rescate esté
 * listo, con cada cosa como un botón que la arregla, y el acceso a todo lo demás.
 * La primera vez lleva al asistente de preparación.
 */
class ActividadPrincipal : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var acciones: AccionesPreparacion
	private lateinit var actualizaciones: GestorActualizaciones

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		actualizaciones = GestorActualizaciones(this)
		acciones = AccionesPreparacion(this) { construir() }
		p = pantalla(getString(R.string.app_name), conArriba = false)
		if (!Preferencias(this).asistenteCompletado && estado == null) {
			startActivity(Intent(this, ActividadAsistente::class.java))
		}
		actualizaciones.alIniciar()
	}

	override fun onResume() {
		super.onResume()
		// Al volver de los Ajustes del sistema el estado puede haber cambiado
		construir()
	}

	private fun construir() {
		p.vaciar()
		val preparacion = Preparacion(this)
		val estados = preparacion.estados()
		val lista = preparacion.lista()
		p.encabezado(getString(R.string.ui_estado))
		p.estadoVivo(getString(if (lista) R.string.ui_estado_listo else R.string.ui_estado_no_listo))
		estados.forEach { e -> p.estado(e.texto, e.bien) { acciones.hacer(e.requisito) } }

		p.encabezado(getString(R.string.ui_rescate))
		p.boton(getString(R.string.ui_ensayar)) { acciones.ensayar() }
		p.parrafo(getString(R.string.ui_ensayar_explicacion))
		p.boton(getString(R.string.ui_asistente)) { startActivity(Intent(this, ActividadAsistente::class.java)) }
		p.boton(getString(R.string.ui_diagnostico)) { startActivity(Intent(this, ActividadDiagnostico::class.java)) }

		p.encabezado(getString(R.string.ui_herramientas))
		p.boton(getString(R.string.ui_copias)) { startActivity(Intent(this, ActividadCopias::class.java)) }
		p.boton(getString(R.string.ui_permiso)) { startActivity(Intent(this, ActividadPermiso::class.java)) }
		p.boton(getString(R.string.ui_guardar_config)) { acciones.guardarConfiguracion() }
		p.boton(getString(R.string.ui_probar_voz)) { acciones.probarVozRescate() }
		p.boton(getString(R.string.ui_ayudante)) { startActivity(Intent(this, ActividadAyudante::class.java)) }

		p.encabezado(getString(R.string.ui_app))
		p.boton(getString(R.string.ui_ajustes)) { startActivity(Intent(this, ActividadAjustes::class.java)) }
		p.boton(getString(R.string.ui_manual)) { startActivity(Intent(this, ActividadManual::class.java)) }
		p.boton(getString(R.string.ui_buscar_actualizaciones)) { actualizaciones.comprobarAMano() }
		p.boton(getString(R.string.ui_compartir_registro)) { compartirRegistro() }
		p.boton(getString(R.string.ui_creditos)) { startActivity(Intent(this, ActividadCreditos::class.java)) }
		p.parrafo(getString(R.string.ui_version, BuildConfig.VERSION_NAME))
	}

	/** Comparte el registro técnico (sin datos privados) para enviar un informe. */
	private fun compartirRegistro() {
		val texto = Registro.cabecera(BuildConfig.VERSION_NAME) + Registro.texto()
		if (texto.isBlank()) {
			mensaje(getString(R.string.ui_compartir_registro), getString(R.string.ui_registro_vacio))
			return
		}
		try {
			val archivo = File(File(cacheDir, "compartir").apply { mkdirs() }, "registro-rescate.txt")
			archivo.writeText(texto)
			val uri = FileProvider.getUriForFile(this, "$packageName.archivos", archivo)
			val intent = Intent(Intent.ACTION_SEND)
				.setType("text/plain")
				.putExtra(Intent.EXTRA_STREAM, uri)
				.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ui_registro_asunto))
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			startActivity(Intent.createChooser(intent, getString(R.string.ui_compartir_registro)))
		} catch (e: Exception) {
			Registro.e("Principal", "No se pudo compartir el registro", e)
			mensaje(getString(R.string.ui_compartir_registro), getString(R.string.ui_no_se_abre))
		}
	}
}
