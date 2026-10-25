package com.xebolax.rescate.ui

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.xebolax.rescate.R
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.rescate.Avisos
import com.xebolax.rescate.reparar.CopiasApk
import com.xebolax.rescate.reparar.Tiendas
import com.xebolax.rescate.sistema.ListaServicios
import com.xebolax.rescate.sistema.Paquetes
import com.xebolax.rescate.sistema.Instantanea
import com.xebolax.rescate.sistema.Servicios
import com.xebolax.rescate.voz.EspeakNativo
import com.xebolax.rescate.voz.PicoNativo

/** Qué hay que preparar para que el rescate funcione, y si ya está. */
enum class Requisito(val imprescindible: Boolean) {
	VOZ(true),
	ATAJO(true),
	ATAJO_UNICO(true),
	ATAJO_BLOQUEO(false),
	ENSAYO(true),
	GUARDIAN(false),
	PERMISO(false),
	COPIAS(false),
	CONFIGURACION(false),
	MOTOR_CONOCIDO(false),
	AVISOS(false),
	BATERIA(false),
}

/** El estado de un requisito: [bien] null si no aplica o es opcional y no está. */
data class EstadoRequisito(val requisito: Requisito, val texto: String, val bien: Boolean?)

/**
 * Comprueba la preparación del rescate. Solo con API públicas y sin tocar nada:
 * lo usan la pantalla principal (con el aviso si falta algo imprescindible) y el
 * asistente.
 */
class Preparacion(private val contexto: Context) {
	private val servicios = Servicios(contexto)
	private val prefs = Preferencias(contexto)

	fun estados(): List<EstadoRequisito> = Requisito.entries.mapNotNull { estado(it) }

	/** Si lo imprescindible está listo. */
	fun lista(): Boolean = estados().filter { it.requisito.imprescindible }.all { it.bien != false }

	fun estado(r: Requisito): EstadoRequisito? {
		val c = contexto
		return when (r) {
			Requisito.VOZ -> {
				val ok = PicoNativo.bibliotecaDisponible() || EspeakNativo.disponible()
				EstadoRequisito(r, c.getString(if (ok) R.string.prep_voz_ok else R.string.prep_voz_mal), ok)
			}
			Requisito.ATAJO -> if (Build.VERSION.SDK_INT < 26) {
				EstadoRequisito(r, c.getString(R.string.prep_atajo_sin), null)
			} else {
				val ok = servicios.atajoVolumenEsNuestro()
				EstadoRequisito(r, c.getString(if (ok) R.string.prep_atajo_ok else R.string.prep_atajo_mal), ok)
			}
			Requisito.ATAJO_UNICO -> {
				if (Build.VERSION.SDK_INT < 26 || !servicios.atajoVolumenEsNuestro()) return null
				val ok = servicios.objetivosAtajo(Servicios.CLAVE_ATAJO_VOLUMEN).size <= 1
				EstadoRequisito(r, c.getString(if (ok) R.string.prep_unico_ok else R.string.prep_unico_mal), ok)
			}
			Requisito.ATAJO_BLOQUEO -> {
				if (Build.VERSION.SDK_INT < 26) return null
				// Sin valor, Android lo permite en la pantalla de bloqueo una vez contestada su pregunta
				val ok = com.xebolax.rescate.diagnostico.Reglas.atajoEnBloqueo(
					Servicios.leerSeguro(c, Servicios.CLAVE_ATAJO_BLOQUEO),
					Servicios.leerSeguro(c, Servicios.CLAVE_DIALOGO_ATAJO) == "1",
				)
				EstadoRequisito(r, c.getString(if (ok) R.string.prep_bloqueo_ok else R.string.prep_bloqueo_mal), if (ok) true else null)
			}
			Requisito.ENSAYO -> EstadoRequisito(r, c.getString(if (prefs.ensayoHecho) R.string.prep_ensayo_ok else R.string.prep_ensayo_mal), prefs.ensayoHecho)
			Requisito.GUARDIAN -> {
				val ok = ListaServicios.contiene(servicios.activados(), servicios.guardian)
				EstadoRequisito(r, c.getString(if (ok) R.string.prep_guardian_ok else R.string.prep_guardian_no), if (ok) true else null)
			}
			Requisito.PERMISO -> {
				val ok = servicios.puedeEscribir()
				EstadoRequisito(r, c.getString(if (ok) R.string.prep_permiso_ok else R.string.prep_permiso_no), if (ok) true else null)
			}
			Requisito.COPIAS -> {
				val instalados = Paquetes.ESENCIALES.filter { Tiendas.instalada(c, it) }
				if (instalados.isEmpty()) return null
				val conCopia = CopiasApk(c).lista().map { it.paquete }.toSet()
				val faltan = instalados.filterNot { it in conCopia }
				EstadoRequisito(r, c.getString(if (faltan.isEmpty()) R.string.prep_copias_ok else R.string.prep_copias_no), if (faltan.isEmpty()) true else null)
			}
			Requisito.CONFIGURACION -> {
				val ok = Instantanea(c).existe()
				EstadoRequisito(r, c.getString(if (ok) R.string.prep_config_ok else R.string.prep_config_no), if (ok) true else null)
			}
			Requisito.MOTOR_CONOCIDO -> {
				val ok = prefs.motorConocido.isNotEmpty()
				EstadoRequisito(r, c.getString(if (ok) R.string.prep_motor_ok else R.string.prep_motor_no), if (ok) true else null)
			}
			Requisito.AVISOS -> {
				if (Build.VERSION.SDK_INT < 33) return null
				val ok = Avisos.puedeAvisar(c)
				EstadoRequisito(r, c.getString(if (ok) R.string.prep_avisos_ok else R.string.prep_avisos_no), if (ok) true else null)
			}
			Requisito.BATERIA -> {
				val ok = runCatching { c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName) }.getOrDefault(false)
				EstadoRequisito(r, c.getString(if (ok) R.string.prep_bateria_ok else R.string.prep_bateria_no), if (ok) true else null)
			}
		}
	}
}
