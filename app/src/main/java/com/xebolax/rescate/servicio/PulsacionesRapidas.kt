/*
 * Basado en QuickPresses (MenuActivity.kt) de Shortcut Menu (https://github.com/aaron-gh/shortcut-menu),
 * de aaron-gh, con licencia Apache 2.0.
 */
package com.xebolax.rescate.servicio

/** Cuenta pulsaciones de una tecla que llegan rápido una tras otra. */
class PulsacionesRapidas(private val cuantas: Int, private val separacionMaxMs: Long) {
	private var pulsaciones = 0
	private var ultima = 0L

	/**
	 * Apunta una pulsación en [momento] (milisegundos). Devuelve true cuando hace
	 * [cuantas] seguidas, cada una como mucho [separacionMaxMs] después de la
	 * anterior, y empieza a contar de nuevo.
	 */
	fun pulsar(momento: Long): Boolean {
		pulsaciones = if (pulsaciones > 0 && momento - ultima <= separacionMaxMs) pulsaciones + 1 else 1
		ultima = momento
		if (pulsaciones >= cuantas) {
			pulsaciones = 0
			return true
		}
		return false
	}

	fun reiniciar() {
		pulsaciones = 0
	}
}
