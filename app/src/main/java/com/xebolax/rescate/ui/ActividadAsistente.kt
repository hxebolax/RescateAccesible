package com.xebolax.rescate.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.xebolax.rescate.R
import com.xebolax.rescate.accesibilidad.conEspera
import com.xebolax.rescate.accesibilidad.mensaje
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.reparar.CopiasApk
import com.xebolax.rescate.voz.EspeakNativo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Asistente de preparación: la primera vez guía paso a paso, explicando cada
 * permiso (para qué sirve, qué no hace y cómo se quita), hasta dejar el rescate
 * listo. Se puede repetir cuando se quiera desde la pantalla principal.
 *
 * Navegación lineal: cada paso es una página con su encabezado («Paso 3 de 11»),
 * su explicación, sus botones y Anterior / Siguiente. Al cambiar de paso, el
 * foco de TalkBack va al encabezado.
 */
class ActividadAsistente : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var acciones: AccionesPreparacion
	private lateinit var prefs: Preferencias
	private var paso = 0

	private class Paso(val titulo: Int, val texto: Int, val requisito: Requisito?, val botones: List<Pair<Int, () -> Unit>>)

	private val pasos: List<Paso> by lazy {
		listOf(
			Paso(R.string.asis_bienvenida, R.string.asis_bienvenida_texto, null, listOf(
				R.string.asis_idioma_es to { cambiarIdioma("es") },
				R.string.asis_idioma_en to { cambiarIdioma("en") },
			)),
			Paso(R.string.asis_voz, R.string.asis_voz_texto, Requisito.VOZ, listOfNotNull(
				R.string.asis_voz_probar to { acciones.probarVozRescate() },
				if (EspeakNativo.disponible()) R.string.asis_voz_pico to { elegirVoz(Preferencias.MOTOR_PICO) } else null,
				if (EspeakNativo.disponible()) R.string.asis_voz_espeak to { elegirVoz(Preferencias.MOTOR_ESPEAK) } else null,
			)),
			Paso(R.string.asis_atajo, if (Build.VERSION.SDK_INT >= 30) R.string.prep_atajo_pasos_11 else R.string.prep_atajo_pasos_8, Requisito.ATAJO, listOf(
				R.string.ui_abrir_accesibilidad to { acciones.abrirAccesibilidad() },
			)),
			Paso(R.string.asis_permiso_accesibilidad, R.string.asis_permiso_accesibilidad_texto, null, emptyList()),
			Paso(R.string.asis_ensayo, R.string.asis_ensayo_texto, Requisito.ENSAYO, listOf(
				R.string.ui_ensayar to { acciones.ensayar() },
			)),
			Paso(R.string.asis_permiso, R.string.asis_permiso_texto, Requisito.PERMISO, listOf(
				R.string.ui_permiso to { acciones.hacer(Requisito.PERMISO) },
			)),
			Paso(R.string.asis_guardian, R.string.prep_guardian_pasos, Requisito.GUARDIAN, listOf(
				R.string.ui_abrir_accesibilidad to { acciones.abrirAccesibilidad() },
			)),
			Paso(R.string.asis_avisos, R.string.asis_avisos_texto, Requisito.BATERIA, listOfNotNull(
				if (Build.VERSION.SDK_INT >= 33) R.string.asis_avisos_pedir to { acciones.pedirAvisos() } else null,
				R.string.asis_bateria_pedir to { acciones.pedirBateria() },
			)),
			Paso(R.string.asis_copias, R.string.asis_copias_texto, Requisito.COPIAS, listOf(
				R.string.asis_copias_hacer to { hacerCopias() },
				R.string.ui_copias to { acciones.hacer(Requisito.COPIAS) },
			)),
			Paso(R.string.asis_motor, R.string.asis_motor_texto, Requisito.MOTOR_CONOCIDO, listOf(
				R.string.asis_motor_probar to { acciones.probarMotorSistema() },
			)),
			Paso(R.string.asis_config, R.string.asis_config_texto, Requisito.CONFIGURACION, listOf(
				R.string.ui_guardar_config to { acciones.guardarConfiguracion() },
			)),
			Paso(R.string.asis_fin, R.string.asis_fin_texto, null, emptyList()),
		)
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		prefs = Preferencias(this)
		acciones = AccionesPreparacion(this) { construir(enfocar = false) }
		paso = estado?.getInt(CLAVE_PASO) ?: 0
		p = pantalla(getString(R.string.asis_titulo))
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (paso > 0) irA(paso - 1) else finish()
			}
		})
	}

	override fun onResume() {
		super.onResume()
		construir(enfocar = false)
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		estado.putInt(CLAVE_PASO, paso)
	}

	private fun irA(nuevo: Int) {
		paso = nuevo.coerceIn(0, pasos.lastIndex)
		construir(enfocar = true)
	}

	private fun construir(enfocar: Boolean) {
		val actual = pasos[paso]
		p.vaciar()
		val titulo = p.encabezado(getString(R.string.asis_paso, paso + 1, pasos.size, getString(actual.titulo)))
		p.parrafo(getString(actual.texto))
		actual.requisito?.let { r ->
			Preparacion(this).estado(r)?.let { e -> p.estado(e.texto, e.bien) }
		}
		if (paso == pasos.lastIndex) {
			val preparacion = Preparacion(this)
			p.estadoVivo(getString(if (preparacion.lista()) R.string.ui_estado_listo else R.string.ui_estado_no_listo))
			preparacion.estados().forEach { e -> p.estado(e.texto, e.bien) { acciones.hacer(e.requisito) } }
		}
		actual.botones.forEach { (texto, accion) -> p.boton(getString(texto)) { accion() } }
		p.separador()
		if (paso < pasos.lastIndex) p.boton(getString(R.string.asis_siguiente)) { irA(paso + 1) }
		else p.boton(getString(R.string.asis_terminar)) { terminar() }
		if (paso > 0) p.botonSecundario(getString(R.string.asis_anterior)) { irA(paso - 1) }
		if (paso < pasos.lastIndex) p.botonSecundario(getString(R.string.asis_saltar)) { terminar() }
		if (enfocar) titulo.post {
			titulo.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
			titulo.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
		}
		title = getString(actual.titulo)
		titulo.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
	}

	private fun cambiarIdioma(codigo: String) {
		Idioma.cambiar(this, codigo)
		recreate()
	}

	private fun elegirVoz(motor: String) {
		prefs.motorRescate = motor
		acciones.probarVozRescate()
	}

	/** Copia de seguridad de TalkBack y la voz de Google (y Samsung, si están). */
	private fun hacerCopias() {
		lifecycleScope.launch {
			try {
				val copias = CopiasApk(this@ActividadAsistente)
				val hechas = conEspera(getString(R.string.asis_copias)) {
					copias.sugeridos().filter { copias.instalada(it) != null }.mapNotNull { paquete ->
						try {
							copias.hacer(paquete).nombre
						} catch (e: CancellationException) {
							throw e
						} catch (e: Exception) {
							Registro.e("Asistente", "No se pudo copiar $paquete", e)
							null
						}
					}
				}
				mensaje(getString(R.string.asis_copias), if (hechas.isEmpty()) getString(R.string.asis_copias_ninguna) else getString(R.string.asis_copias_hechas, hechas.joinToString(", ")))
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e("Asistente", "Fallo al hacer las copias", e)
			}
			construir(enfocar = false)
		}
	}

	private fun terminar() {
		prefs.asistenteCompletado = true
		// Las grabaciones de respaldo se hacen solas al terminar (si Pico funciona)
		acciones.grabarRespaldo()
		if (!Preparacion(this).lista()) {
			mensaje(getString(R.string.asis_titulo), getString(R.string.asis_no_listo)) { finish() }
		} else {
			finish()
		}
	}

	companion object {
		private const val CLAVE_PASO = "paso"

		fun abrir(a: ActividadBase) = a.startActivity(Intent(a, ActividadAsistente::class.java))
	}
}
