package com.xebolax.rescate.servicio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.xebolax.rescate.R
import com.xebolax.rescate.diagnostico.ProbadorTts
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.rescate.ActividadRescate
import com.xebolax.rescate.rescate.Avisos
import com.xebolax.rescate.rescate.SesionRescate
import com.xebolax.rescate.sistema.Servicios
import com.xebolax.rescate.voz.Senal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * El guardián (opcional): un servicio que el usuario deja activado y que vigila,
 * sin leer la pantalla, si su lector y la voz del sistema siguen funcionando.
 *
 * - Si el lector de pantalla se apaga o se cae, lo dice con la voz de rescate y
 *   deja abrir el rescate con tres pulsaciones rápidas de volumen arriba.
 * - Al encender la pantalla comprueba (como mucho cada media hora, en silencio)
 *   que la voz del sistema habla. Si no, pone la voz de rescate a lo que lee el
 *   lector (modo eco) y lo avisa: el usuario oye la pantalla sin hacer nada.
 *
 * Solo mira las teclas de volumen cuando no hay lector que hable, y no lee el
 * contenido de la pantalla salvo en modo eco o propio.
 */
class ServicioGuardian : ServicioBase() {
	private val principal = Handler(Looper.getMainLooper())
	private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private lateinit var servicios: Servicios
	private lateinit var prefs: Preferencias
	private val tresVolumenArriba = PulsacionesRapidas(3, 600L)
	private var lectorAntes = false
	private var vozRota = false
	private var comprobando: Job? = null
	private var ultimoAviso = 0L

	private val alCambiarTacto = AccessibilityManager.TouchExplorationStateChangeListener { revisarPronto() }

	private val observador = object : ContentObserver(principal) {
		override fun onChange(propio: Boolean) = revisarPronto()
	}

	private val pantalla = object : BroadcastReceiver() {
		override fun onReceive(c: Context, intent: Intent) {
			if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) comprobarVoz(forzar = false)
		}
	}

	override fun onServiceConnected() {
		super.onServiceConnected()
		instancia = this
		servicios = Servicios(this)
		prefs = Preferencias(this)
		lectorAntes = servicios.hayLector(servicios.enMarcha())
		val gestor = getSystemService(AccessibilityManager::class.java)
		gestor.addTouchExplorationStateChangeListener(alCambiarTacto)
		contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observador)
		contentResolver.registerContentObserver(Settings.Secure.getUriFor(Servicios.CLAVE_MOTOR), false, observador)
		val filtro = IntentFilter().apply {
			addAction(Intent.ACTION_SCREEN_ON)
			addAction(Intent.ACTION_USER_PRESENT)
		}
		ContextCompat.registerReceiver(this, pantalla, filtro, ContextCompat.RECEIVER_NOT_EXPORTED)
		Registro.i(ETIQUETA, "Guardián conectado")
		revisar()
	}

	override fun onUnbind(intent: Intent?): Boolean {
		instancia = null
		limpiar()
		return super.onUnbind(intent)
	}

	override fun onDestroy() {
		limpiar()
		super.onDestroy()
	}

	private fun limpiar() {
		runCatching { getSystemService(AccessibilityManager::class.java).removeTouchExplorationStateChangeListener(alCambiarTacto) }
		runCatching { contentResolver.unregisterContentObserver(observador) }
		runCatching { unregisterReceiver(pantalla) }
		principal.removeCallbacksAndMessages(null)
		ambito.cancel()
	}

	private fun revisarPronto() {
		principal.removeCallbacks(revisarTarea)
		// Un momento: al cambiar de lector, uno se apaga antes de que el otro arranque
		principal.postDelayed(revisarTarea, ESPERA_CAMBIO_MS)
	}

	private val revisarTarea = Runnable { revisar() }

	/** Mira el estado y avisa si el lector se ha ido o está activado pero caído. */
	private fun revisar() {
		if (!::servicios.isInitialized) return
		val activados = servicios.activados()
		val enMarcha = servicios.enMarcha()
		val lectorAhora = servicios.hayLector(enMarcha)
		val lectorActivado = servicios.hayLector(activados)
		val sinLector = !lectorAhora && !SesionRescate.activa
		filtrarTeclas(prefs.guardianTeclas && (sinLector || vozRota))
		if (SesionRescate.activa || SesionRescate.modoSeguro) {
			lectorAntes = lectorAhora
			return
		}
		if (lectorAntes && !lectorAhora) {
			val ctx = Idioma.envolver(this)
			val texto = if (lectorActivado) ctx.getString(R.string.guardian_lector_caido) else ctx.getString(R.string.guardian_lector_apagado)
			avisar(texto)
		}
		if (lectorAhora && !lectorAntes) {
			vozRota = false
			if (lector.modo == LectorRescate.Modo.ECO) ponerModoLector(LectorRescate.Modo.APAGADO)
			comprobarVoz(forzar = true)
		}
		lectorAntes = lectorAhora
	}

	/**
	 * Comprueba en silencio que la voz del sistema habla, si hay un lector en
	 * marcha. Si no habla, pone el modo eco y lo avisa con la voz de rescate.
	 */
	fun comprobarVoz(forzar: Boolean) {
		if (!prefs.guardianComprobarVoz || SesionRescate.activa) return
		if (!servicios.hayLector(servicios.enMarcha())) return
		val ahora = System.currentTimeMillis()
		if (!forzar && ahora - prefs.ultimaComprobacionVoz < Preferencias.GUARDIAN_INTERVALO_MIN * 60_000L) return
		if (comprobando?.isActive == true) return
		comprobando = ambito.launch {
			prefs.ultimaComprobacionVoz = ahora
			val prueba = ProbadorTts(this@ServicioGuardian).probar(null, Idioma.efectivo(this@ServicioGuardian))
			if (prueba.funciona) {
				if (vozRota) {
					vozRota = false
					if (lector.modo == LectorRescate.Modo.ECO) {
						ponerModoLector(LectorRescate.Modo.APAGADO)
						avisar(Idioma.envolver(this@ServicioGuardian).getString(R.string.guardian_voz_vuelve))
					}
					filtrarTeclas(false)
				}
			} else {
				Registro.w(ETIQUETA, "La voz del sistema no habla (arranca=${prueba.arranca}, idioma=${prueba.idioma}, error=${prueba.error})")
				vozRota = true
				filtrarTeclas(prefs.guardianTeclas)
				if (prefs.guardianAvisos && lector.modo == LectorRescate.Modo.APAGADO) {
					ponerModoLector(LectorRescate.Modo.ECO)
					avisar(Idioma.envolver(this@ServicioGuardian).getString(R.string.guardian_voz_rota))
				}
			}
		}
	}

	private fun avisar(texto: String) {
		if (!prefs.guardianAvisos) return
		val ahora = SystemClock.elapsedRealtime()
		if (ahora - ultimoAviso < ESPERA_AVISOS_MS) return
		ultimoAviso = ahora
		val voz = SesionRescate.voz(this)
		voz.empezarSesion()
		voz.senal(Senal.ATENCION)
		voz.hablar(texto, vaciar = false)
		Avisos.mostrarRescate(this, texto)
		// Panel con el foco: volumen arriba (o tocar «Abrir el rescate») lo abre aunque
		// el filtro de teclas de accesibilidad no entregue las teclas de volumen
		panel.mostrarAviso(texto, alAbrir = { SesionRescate.empezar(this, SesionRescate.Origen.GUARDIAN) })
	}

	/** Tres pulsaciones rápidas de volumen arriba (solo se miran con el lector caído o mudo) abren el rescate. */
	override fun alTecla(evento: KeyEvent): Boolean {
		if (evento.keyCode == KeyEvent.KEYCODE_VOLUME_UP && evento.action == KeyEvent.ACTION_UP && !ActividadRescate.visible) {
			if (tresVolumenArriba.pulsar(evento.eventTime)) {
				Registro.i(ETIQUETA, "Rescate abierto con tres pulsaciones de volumen arriba")
				SesionRescate.empezar(this, SesionRescate.Origen.GUARDIAN)
			}
		}
		return false
	}

	/** El rescate terminó: se vuelve a vigilar desde el estado actual. */
	fun alTerminarRescate() {
		principal.postDelayed({
			lectorAntes = servicios.hayLector(servicios.enMarcha())
			revisar()
			if (vozRota && prefs.guardianAvisos && lector.modo == LectorRescate.Modo.APAGADO && lectorAntes) ponerModoLector(LectorRescate.Modo.ECO)
		}, ESPERA_CAMBIO_MS)
	}

	companion object {
		private const val ETIQUETA = "Guardian"
		private const val ESPERA_CAMBIO_MS = 3000L
		private const val ESPERA_AVISOS_MS = 20_000L

		@Volatile
		var instancia: ServicioGuardian? = null
			private set
	}
}
