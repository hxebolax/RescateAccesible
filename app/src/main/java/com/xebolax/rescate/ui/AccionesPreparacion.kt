package com.xebolax.rescate.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.xebolax.rescate.R
import com.xebolax.rescate.accesibilidad.avisar
import com.xebolax.rescate.accesibilidad.conEspera
import com.xebolax.rescate.accesibilidad.mensaje
import com.xebolax.rescate.diagnostico.ProbadorTts
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.permiso.ActividadPermiso
import com.xebolax.rescate.rescate.SesionRescate
import com.xebolax.rescate.rescate.TextosEsenciales
import com.xebolax.rescate.reparar.ActividadCopias
import com.xebolax.rescate.sistema.Instantanea
import com.xebolax.rescate.sistema.Servicios
import com.xebolax.rescate.voz.Resultado
import com.xebolax.rescate.voz.VozEmergencia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Lo que hace cada paso de la preparación, compartido por la pantalla
 * principal y el asistente. Todo explica antes lo que va a pasar y deja el
 * resultado dicho en la misma pantalla.
 */
class AccionesPreparacion(private val a: ActividadBase, private val alCambiar: () -> Unit) {
	private val prefs = Preferencias(a)
	private val servicios = Servicios(a)

	private val permisoAvisos: ActivityResultLauncher<String> =
		a.registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
			a.avisar(a.getString(if (concedido) R.string.prep_avisos_ok else R.string.prep_avisos_denegado))
			alCambiar()
		}

	fun hacer(r: Requisito) {
		when (r) {
			Requisito.VOZ -> probarVozRescate()
			Requisito.ATAJO, Requisito.ATAJO_UNICO, Requisito.ATAJO_BLOQUEO -> explicarAtajo()
			Requisito.ENSAYO -> ensayar()
			Requisito.GUARDIAN -> explicarGuardian()
			Requisito.PERMISO -> a.startActivity(Intent(a, ActividadPermiso::class.java))
			Requisito.COPIAS -> a.startActivity(Intent(a, ActividadCopias::class.java))
			Requisito.CONFIGURACION -> guardarConfiguracion()
			Requisito.MOTOR_CONOCIDO -> probarMotorSistema()
			Requisito.AVISOS -> pedirAvisos()
			Requisito.BATERIA -> pedirBateria()
		}
	}

	/** Dice una frase con la voz de rescate (Pico o eSpeak), sin depender del motor del sistema. */
	fun probarVozRescate() {
		val voz = SesionRescate.voz(a)
		voz.empezarSesion()
		voz.preparar { motor ->
			val texto = a.getString(when (motor) {
				VozEmergencia.Motor.PICO -> R.string.prep_voz_frase_pico
				VozEmergencia.Motor.ESPEAK -> R.string.prep_voz_frase_espeak
				VozEmergencia.Motor.SISTEMA -> R.string.prep_voz_frase_sistema
				else -> R.string.prep_voz_frase_sin
			})
			voz.hablar(texto) { r ->
				voz.salida.soltarFoco()
				if (r == Resultado.FALLO) a.mensaje(a.getString(R.string.prep_voz), a.getString(R.string.prep_voz_mal))
				SesionRescate.cerrarVozSiLibre()
			}
		}
	}

	/** Explica cómo asignar el atajo de volumen y abre Accesibilidad. */
	fun explicarAtajo() {
		val texto = a.getString(if (Build.VERSION.SDK_INT >= 30) R.string.prep_atajo_pasos_11 else R.string.prep_atajo_pasos_8)
		val dialogo = com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
			.setTitle(R.string.prep_atajo)
			.setMessage(texto)
			.setPositiveButton(R.string.ui_abrir_accesibilidad) { _, _ -> abrirAccesibilidad() }
			.setNegativeButton(R.string.cancelar, null)
		// Con el permiso avanzado, la app lo deja hecho ella misma (y lo comprueba)
		if (servicios.puedeEscribir() && Build.VERSION.SDK_INT >= 26) {
			dialogo.setNeutralButton(R.string.prep_atajo_automatico) { _, _ ->
				val ok = servicios.asignarAtajoVolumen()
				a.mensaje(a.getString(R.string.prep_atajo), a.getString(if (ok) R.string.prep_atajo_automatico_hecho else R.string.prep_atajo_automatico_fallo))
				alCambiar()
			}
		}
		dialogo.show()
	}

	fun explicarGuardian() {
		com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
			.setTitle(R.string.prep_guardian)
			.setMessage(R.string.prep_guardian_pasos)
			.setPositiveButton(R.string.ui_abrir_accesibilidad) { _, _ -> abrirAccesibilidad() }
			.setNegativeButton(R.string.cancelar, null)
			.show()
	}

	fun abrirAccesibilidad() {
		try {
			a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
		} catch (e: Exception) {
			Registro.w("Preparacion", "No se pudieron abrir los ajustes de accesibilidad", e)
			a.mensaje(a.getString(R.string.prep_atajo), a.getString(R.string.ui_no_se_abre))
		}
	}

	/** Abre el menú de rescate para practicar (con el lector funcionando). */
	fun ensayar() {
		prefs.ensayoHecho = true
		SesionRescate.empezar(a, SesionRescate.Origen.APP)
	}

	fun guardarConfiguracion() {
		a.lifecycleScope.launch {
			try {
				val ok = a.conEspera(a.getString(R.string.prep_config)) {
					val guardada = Instantanea(a).guardar()
					// Las grabaciones de respaldo de los textos fijos, con la voz incluida
					grabarRespaldo()
					guardada
				}
				a.mensaje(a.getString(R.string.prep_config), a.getString(if (ok) R.string.prep_config_hecha else R.string.prep_config_fallo))
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e("Preparacion", "No se pudo guardar la configuración", e)
				a.mensaje(a.getString(R.string.prep_config), a.getString(R.string.prep_config_fallo))
			}
			alCambiar()
		}
	}

	/**
	 * Graba los textos fijos del rescate como respaldo, con Pico y con eSpeak, en
	 * español y en inglés, con la velocidad y el tono del usuario (en segundo plano).
	 */
	fun grabarRespaldo() {
		val voz = SesionRescate.voz(a)
		val clave = com.xebolax.rescate.BuildConfig.VERSION_NAME + "|" + prefs.velocidad + "|" + prefs.tono
		if (prefs.grabacionesHechas == clave) return
		voz.grabarRespaldo(TextosEsenciales.idiomas(), { TextosEsenciales.lista(a, it) }) { hechas ->
			Registro.i("Preparacion", "Grabaciones de respaldo: $hechas")
			if (hechas > 0) prefs.grabacionesHechas = clave
			SesionRescate.cerrarVozSiLibre()
		}
	}

	/** Prueba de verdad el motor de voz del sistema y lo guarda como «conocido bueno» si habla. */
	fun probarMotorSistema() {
		a.lifecycleScope.launch {
			try {
				val prueba = a.conEspera(a.getString(R.string.prep_motor)) { ProbadorTts(a).probar(null, Idioma.efectivo(a)) }
				val motor = prueba.motorReal ?: servicios.motorPredeterminado()
				val texto = if (prueba.funciona && motor != null) {
					prefs.motorConocido = motor
					a.getString(R.string.prep_motor_hecho, servicios.motores().firstOrNull { it.paquete == motor }?.nombre ?: motor)
				} else a.getString(R.string.prep_motor_fallo)
				a.mensaje(a.getString(R.string.prep_motor), texto)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e("Preparacion", "No se pudo probar el motor del sistema", e)
			}
			alCambiar()
		}
	}

	fun pedirAvisos() {
		if (Build.VERSION.SDK_INT >= 33) permisoAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
	}

	/** Pide no optimizar la batería de esta app (para que el guardián no se cierre solo). */
	@SuppressLint("BatteryLife")
	fun pedirBateria() {
		try {
			a.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + a.packageName)))
		} catch (e: Exception) {
			Registro.w("Preparacion", "No se pudo pedir la exención de batería", e)
			runCatching { a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
		}
	}
}
