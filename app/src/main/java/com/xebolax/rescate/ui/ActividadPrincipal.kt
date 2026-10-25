package com.xebolax.rescate.ui

import android.content.Intent
import android.os.Bundle
import com.xebolax.rescate.R
import com.xebolax.rescate.actualizaciones.GestorActualizaciones
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.permiso.ActividadPermiso
import com.xebolax.rescate.reparar.ActividadCopias

/**
 * Pantalla principal (modo Preparación): qué falta para que el rescate esté
 * listo, con cada cosa como un botón que la arregla, y lo que se usa a menudo.
 * Todo lo demás está en el menú lateral («Abrir menú», arriba a la izquierda),
 * como en Lector Universal. La primera vez lleva al asistente de preparación.
 */
class ActividadPrincipal : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var acciones: AccionesPreparacion
	private lateinit var actualizaciones: GestorActualizaciones

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		actualizaciones = GestorActualizaciones(this)
		acciones = AccionesPreparacion(this) { construir() }
		p = pantalla(getString(R.string.app_name), destinoActual = R.id.lateral_inicio) { abrir(it) }
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
		p.boton(getString(R.string.ui_probar_voz)) { acciones.probarVozRescate() }
		p.boton(getString(R.string.ui_guardar_config)) { acciones.guardarConfiguracion() }
		p.parrafo(getString(R.string.ui_mas_en_menu))
	}

	/** Las opciones del menú lateral. Inicio es esta pantalla: no hace falta abrir nada. */
	private fun abrir(opcion: Int) {
		val destino = when (opcion) {
			R.id.lateral_diagnostico -> ActividadDiagnostico::class.java
			R.id.lateral_copias -> ActividadCopias::class.java
			R.id.lateral_permiso -> ActividadPermiso::class.java
			R.id.lateral_asistente -> ActividadAsistente::class.java
			R.id.lateral_ayudante -> ActividadAyudante::class.java
			R.id.lateral_ajustes -> ActividadAjustes::class.java
			R.id.lateral_manual -> ActividadManual::class.java
			R.id.lateral_acerca -> ActividadCreditos::class.java
			else -> return
		}
		startActivity(Intent(this, destino))
	}
}
