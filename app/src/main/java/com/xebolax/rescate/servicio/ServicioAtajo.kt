/*
 * Basado en ShortcutMenuService.kt de Shortcut Menu (https://github.com/aaron-gh/shortcut-menu),
 * de aaron-gh, con licencia Apache 2.0. Cambios para Rescate Accesible: lector de rescate,
 * teclas de volumen durante el rescate y sesión compartida con el guardián.
 */
package com.xebolax.rescate.servicio

import android.content.Intent
import android.view.KeyEvent
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.rescate.ActividadRescate
import com.xebolax.rescate.rescate.SesionRescate

/**
 * El servicio que enciende y apaga el atajo de accesibilidad (por ejemplo,
 * mantener pulsadas las dos teclas de volumen).
 *
 * El atajo lo enciende y abre el menú de rescate. Al terminar el rescate se
 * apaga solo ([disableSelf], API pública, sin permisos), y así la siguiente
 * pulsación del atajo lo vuelve a encender y abre el menú otra vez. Pulsar el
 * atajo con el menú abierto lo apaga, y eso cierra el menú.
 *
 * Mientras está encendido es también el lector de rescate (modo eco o propio)
 * y el que guía por los Ajustes.
 */
class ServicioAtajo : ServicioBase() {
	private val tresVolumenArriba = PulsacionesRapidas(3, 600L)

	override fun onServiceConnected() {
		super.onServiceConnected()
		instancia = this
		Registro.i("ServicioAtajo", "Conectado por el atajo")
		SesionRescate.empezar(this, SesionRescate.Origen.ATAJO)
	}

	override fun onUnbind(intent: Intent?): Boolean {
		instancia = null
		Registro.i("ServicioAtajo", "Desconectado")
		// Pulsar el atajo con el menú abierto lo cierra; el rescate termina sin tocar nada más
		ActividadRescate.cerrarAbierto()
		if (SesionRescate.activa && ServicioGuardian.instancia == null) SesionRescate.terminar(this, apagarAtajo = false)
		return super.onUnbind(intent)
	}

	/**
	 * Con el lector de rescate propio (explorando el móvil fuera del menú), tres
	 * pulsaciones rápidas de volumen arriba vuelven al menú de rescate.
	 */
	override fun alTecla(evento: KeyEvent): Boolean {
		if (evento.keyCode == KeyEvent.KEYCODE_VOLUME_UP && evento.action == KeyEvent.ACTION_UP && !ActividadRescate.visible) {
			if (tresVolumenArriba.pulsar(evento.eventTime)) {
				SesionRescate.empezar(this, SesionRescate.Origen.ATAJO)
			}
		}
		return false
	}

	companion object {
		/** El servicio en marcha, mientras está encendido. */
		@Volatile
		var instancia: ServicioAtajo? = null
			private set
	}
}
