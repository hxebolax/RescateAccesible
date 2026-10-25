/*
 * Basado en MenuActivity.kt de Shortcut Menu (https://github.com/aaron-gh/shortcut-menu),
 * de aaron-gh, con licencia Apache 2.0: el menú que habla solo, la lista para lectores,
 * el modo seguro con tres pulsaciones de volumen arriba (apaga los lectores, habla con
 * Pico y los devuelve si se acaba sin ninguno) y el cierre al pulsar otra vez el atajo.
 * Cambios para Rescate Accesible: páginas y submenús de rescate, manejo completo con las
 * teclas de volumen, cadena de voces de emergencia, modo eco sin permisos, trabajos
 * lentos con aviso sonoro, pantalla de bloqueo y comentarios en español.
 */
package com.xebolax.rescate.rescate

import com.xebolax.rescate.accesibilidad.recorribleConLector
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.xebolax.rescate.R
import com.xebolax.rescate.diagnostico.Guia
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.servicio.LectorRescate
import com.xebolax.rescate.servicio.PulsacionesRapidas
import com.xebolax.rescate.sistema.Servicios
import com.xebolax.rescate.voz.Senal
import com.xebolax.rescate.voz.VozEmergencia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * El menú de rescate.
 *
 * - **Sin lector de pantalla**, habla solo: arrastrar el dedo dice cada opción
 *   y levantarlo la elige ([VistaExplorar]).
 * - **Con un lector** (TalkBack o el lector de rescate), es una lista de botones
 *   que el lector lee.
 * - **Siempre**, con las teclas de volumen y la voz de rescate: volumen abajo
 *   pasa a la siguiente opción, volumen arriba a la anterior y mantener pulsada
 *   cualquiera de las dos elige la opción. Funciona aunque TalkBack esté activo
 *   pero mudo, que es cuando no sirve tocar la pantalla.
 *
 * Tres pulsaciones rápidas de volumen arriba activan el modo seguro: con el
 * permiso avanzado apaga los lectores (y los devuelve si se termina sin
 * ninguno); sin él, pone la voz de rescate a lo que lee el lector (modo eco).
 */
class ActividadRescate : AppCompatActivity(), Guia {
	override val actividad: Activity get() = this
	override val vozRescate: VozEmergencia? get() = voz

	lateinit var voz: VozEmergencia
		private set
	lateinit var servicios: Servicios
		private set
	lateinit var prefs: Preferencias
		private set

	private val pila = ArrayDeque<Pagina>()
	private var opciones: List<Opcion> = emptyList()
	private var actual = -1
	private var vista: VistaExplorar? = null
	private var botones: List<MaterialButton> = emptyList()
	private var hablaSolo = false
	private val manejador = Handler(Looper.getMainLooper())
	private var terminado = false
	/** Se sale del menú a propósito (a los Ajustes, a explorar): la sesión sigue. */
	private var saliendoAProposito = false
	private var trabajo: Job? = null
	private var alHablado: (() -> Unit)? = null

	// Teclas de volumen
	private var arribaPulsada = false
	private var abajoPulsada = false
	private var combinacion = false
	private var largaHecha = false
	private val pulsacionLarga = Runnable {
		largaHecha = true
		elegir(actual)
	}
	private val tresArriba = PulsacionesRapidas(3, 600L)

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(Idioma.envolver(base))
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		abierta = WeakReference(this)
		// Abierto desde el icono, un acceso directo o el mosaico: también es un rescate
		SesionRescate.empezarSinAbrir(SesionRescate.Origen.APP)
		prefs = Preferencias(this)
		servicios = Servicios(applicationContext)
		voz = SesionRescate.voz(this)
		voz.alCambiarMotor = { motor -> alCambiarMotor(motor) }
		mostrarSobreBloqueo()
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() = volver()
		})
		val subido = voz.empezarSesion()
		pila.clear()
		pila.addLast(PaginasRescate.principal(this))
		mostrar(primeraVez = true, aviso = if (subido) getString(R.string.rescate_volumen_subido) else null)
		voz.preparar { motor ->
			if (motor == VozEmergencia.Motor.GRABACIONES || motor == VozEmergencia.Motor.PITIDOS) Registro.w("Rescate", "Rescate sin síntesis: $motor")
		}
		// Diagnóstico rápido al entrar: lo más grave se dice tras la presentación
		lifecycleScope.launch {
			try {
				val aviso = PaginasRescate.avisoRapido(this@ActividadRescate)
				if (aviso != null && !terminado) decir(aviso, vaciar = false)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e("Rescate", "Falló el diagnóstico rápido", e)
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		// Vuelta al menú (tres pulsaciones de volumen, el atajo o la notificación)
		saliendoAProposito = false
		mostrar(primeraVez = false, aviso = getString(R.string.rescate_de_vuelta))
	}

	override fun onResume() {
		super.onResume()
		visible = true
		SesionRescate.servicio?.panel?.quitarVolver()
		saliendoAProposito = false
		SesionRescate.servicio?.lector?.ignorarPaquete = if (hablaSolo) packageName else null
	}

	override fun onPause() {
		visible = false
		super.onPause()
	}

	override fun onStop() {
		super.onStop()
		// Salir del menú (inicio, apagar la pantalla) lo cierra, salvo que se salga a propósito
		if (!isChangingConfigurations && !saliendoAProposito) cerrar()
	}

	override fun onDestroy() {
		if (abierta?.get() == this) abierta = null
		manejador.removeCallbacksAndMessages(null)
		trabajo?.cancel()
		if (!isChangingConfigurations && !saliendoAProposito && !terminado) cerrar()
		super.onDestroy()
	}

	private fun mostrarSobreBloqueo() {
		if (Build.VERSION.SDK_INT >= 27) {
			setShowWhenLocked(true)
			setTurnScreenOn(true)
		} else {
			@Suppress("DEPRECATION")
			window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
		}
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
	}

	// --- Páginas ------------------------------------------------------------------------

	val pagina: Pagina get() = pila.last()

	/** Abre [p] encima de la actual. */
	fun abrirPagina(p: Pagina, aviso: String? = null) {
		pila.addLast(p)
		mostrar(primeraVez = false, aviso = aviso)
	}

	/** Vuelve a la página anterior (o, en la principal, pregunta si salir). */
	fun volver() {
		if (pila.size > 1) {
			pila.removeLast()
			mostrar(primeraVez = false, aviso = null)
		} else {
			salir()
		}
	}

	/** Vuelve a la página principal. */
	fun alPrincipio(aviso: String? = null) {
		while (pila.size > 1) pila.removeLast()
		mostrar(primeraVez = false, aviso = aviso)
	}

	/** Reconstruye la página actual (su estado cambió) sin presentarla de nuevo, salvo [aviso]. */
	fun refrescar(aviso: String? = null) {
		val antes = actual
		construir()
		actual = antes.coerceAtMost(opciones.lastIndex)
		vista?.actual = actual
		if (aviso != null) decir(aviso)
	}

	private fun mostrar(primeraVez: Boolean, aviso: String?) {
		if (terminado) return
		construir()
		actual = -1
		vista?.actual = -1
		val intro = buildString {
			if (aviso != null) append(aviso).append(". ")
			append(if (primeraVez) presentacion() else pagina.intro)
		}
		Registro.i("Rescate", "Página «${pagina.titulo}» con ${opciones.size} opciones, habla sola: $hablaSolo, voz: ${voz.motor}")
		decir(intro)
		armarEsperaSeguro()
	}

	/** Lo que se dice al abrir el rescate: qué es y cómo se maneja ahora mismo. */
	private fun presentacion(): String {
		val manejo = getString(if (hablaSolo) R.string.rescate_manejo_dedo else R.string.rescate_manejo_lector)
		val teclas = if (prefs.teclasEnMenu) " " + getString(R.string.rescate_manejo_teclas) else ""
		return getString(R.string.rescate_presentacion) + " " + manejo + teclas
	}

	private fun construir() {
		val p = pagina
		val lista = p.opciones(this).toMutableList()
		if (p.principal) {
			lista += Opcion(getString(R.string.rescate_salir), getString(R.string.rescate_salir_ayuda), getString(R.string.rescate_saliendo)) { it.salir() }
		} else {
			lista += Opcion(getString(R.string.rescate_volver), anuncio = null) { it.volver() }
		}
		opciones = lista
		val lectorFuera = otroLectorActivo() || SesionRescate.servicio?.lector?.modo == LectorRescate.Modo.PROPIO
		hablaSolo = SesionRescate.modoSeguro || !lectorFuera
		SesionRescate.servicio?.lector?.ignorarPaquete = if (hablaSolo) packageName else null
		title = p.titulo
		setContentView(if (hablaSolo) vistaQueHabla() else vistaLista())
	}

	private fun otroLectorActivo(): Boolean =
		runCatching { getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled }.getOrDefault(false)

	/**
	 * El menú que habla solo. Va entre las barras de estado y de navegación en
	 * lugar de ocultarlas: ocultarlas hace que Android muestre la primera vez
	 * «Viendo a pantalla completa», un aviso que no se lee y tapa las opciones.
	 */
	private fun vistaQueHabla(): View {
		val textos = opciones.map { it.texto }
		val v = VistaExplorar(this, textos,
			alPasar = { i ->
				armarEsperaSeguro()
				actual = i
				decirOpcion(i)
			},
			alElegir = { i -> elegir(i) },
			alQuieto = { i -> opciones.getOrNull(i)?.ayuda?.takeIf { it.isNotBlank() }?.let { decir(it, vaciar = false) } },
		)
		vista = v
		botones = emptyList()
		return FrameLayout(this).apply {
			fitsSystemWindows = true
			setBackgroundColor(getColor(R.color.menu_fondo))
			addView(v)
			aplicarMargenes(this)
		}
	}

	/** El menú como lista de botones, para un lector de pantalla. */
	private fun vistaLista(): View {
		vista = null
		val margen = dp(16)
		val lista = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(margen, margen, margen, margen)
		}
		lista.addView(TextView(this).apply {
			text = pagina.titulo
			setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
			setTextColor(getColor(R.color.menu_texto))
			ViewCompat.setAccessibilityHeading(this, true)
		})
		lista.addView(TextView(this).apply {
			text = pagina.intro
			setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
			setTextColor(getColor(R.color.menu_texto))
			setPadding(0, dp(8), 0, dp(8))
		})
		botones = opciones.mapIndexed { i, opcion ->
			MaterialButton(this).apply {
				text = opcion.texto
				setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
				minHeight = dp(64)
				gravity = Gravity.CENTER
				setOnClickListener { elegir(i) }
				if (opcion.ayuda.isNotBlank()) ViewCompat.setTooltipText(this, opcion.ayuda)
				lista.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
			}
		}
		return ScrollView(this).recorribleConLector().apply {
			setBackgroundColor(getColor(R.color.menu_fondo))
			addView(lista)
			aplicarMargenes(this)
		}
	}

	private fun aplicarMargenes(v: View) {
		ViewCompat.setOnApplyWindowInsetsListener(v) { vista, insets ->
			val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			vista.updatePadding(left = barras.left, top = barras.top, right = barras.right, bottom = barras.bottom)
			WindowInsetsCompat.CONSUMED
		}
	}

	// --- Elegir y hablar -------------------------------------------------------------

	private fun decirOpcion(i: Int) {
		val opcion = opciones.getOrNull(i) ?: return
		voz.pitidos.vibrar(Senal.MOVER)
		val texto = if (prefs.decirPosicion) getString(R.string.rescate_posicion, opcion.texto, i + 1, opciones.size) else opcion.texto
		// Sin síntesis: la grabación del texto si la hay, y si no, tantos pitidos como la posición
		if (voz.motor == VozEmergencia.Motor.GRABACIONES || voz.motor == VozEmergencia.Motor.PITIDOS) {
			voz.hablar(opcion.texto)
			voz.pitidos.vibrarCuenta(i + 1)
			return
		}
		voz.hablar(texto)
	}

	private fun mover(paso: Int) {
		if (opciones.isEmpty()) return
		actual = when {
			actual < 0 -> if (paso > 0) 0 else opciones.lastIndex
			else -> (actual + paso).mod(opciones.size)
		}
		vista?.actual = actual
		botones.forEachIndexed { i, b -> b.isSelected = i == actual; b.strokeWidth = if (i == actual) dp(4) else 0 }
		decirOpcion(actual)
	}

	fun elegir(i: Int) {
		if (terminado) return
		val opcion = opciones.getOrNull(i) ?: return
		// Solo textos propios del menú: nunca nada de otras apps
		Registro.i("Rescate", "Elegida la opción ${i + 1} de ${opciones.size} en «${pagina.titulo}»")
		armarEsperaSeguro()
		voz.pitidos.vibrar(Senal.ELEGIR)
		val anuncio = opcion.anuncio
		if (anuncio != null) decir(anuncio) { opcion.accion(this) } else opcion.accion(this)
	}

	/** Dice [texto] con la voz de rescate y luego ejecuta [luego] (o pasado un tiempo, si no termina). */
	fun decir(texto: String, vaciar: Boolean = true, luego: (() -> Unit)? = null) {
		if (luego != null) {
			alHablado = luego
			manejador.removeCallbacksAndMessages(ESPERA_HABLA)
			manejador.postDelayed({ hablado() }, ESPERA_HABLA, (ESPERA_HABLA_MS + texto.length * 90L).coerceAtMost(30_000L))
		}
		voz.hablar(texto, vaciar) { if (luego != null) hablado() }
	}

	private fun hablado() {
		val luego = alHablado ?: return
		alHablado = null
		manejador.removeCallbacksAndMessages(ESPERA_HABLA)
		luego()
	}

	private fun alCambiarMotor(motor: VozEmergencia.Motor) {
		val texto = when (motor) {
			VozEmergencia.Motor.PICO, VozEmergencia.Motor.ESPEAK -> getString(R.string.rescate_cambio_a_pico)
			else -> null
		}
		texto?.let { voz.hablar(it, vaciar = false) }
	}

	/**
	 * Hace algo lento sin bloquear: lo dice, da un pitido suave cada pocos
	 * segundos mientras trabaja (así se sabe que sigue) y luego habla el resultado.
	 */
	fun trabajar(texto: String, tarea: suspend () -> String?) {
		trabajo?.cancel()
		decir(texto)
		trabajo = lifecycleScope.launch {
			val latido = launch {
				delay(3000)
				while (isActive) {
					voz.senal(Senal.MOVER)
					delay(2500)
				}
			}
			try {
				val resultado = tarea()
				latido.cancel()
				if (resultado != null && !terminado) decir(resultado)
			} catch (e: CancellationException) {
				latido.cancel()
				throw e
			} catch (e: Exception) {
				latido.cancel()
				Registro.e("Rescate", "Falló un trabajo del rescate", e)
				voz.senal(Senal.ERROR)
				if (!terminado) decir(getString(R.string.rescate_error_general))
			}
		}
	}

	/** Pregunta con dos opciones: [textoSi] hace [alSi]; «Cancelar» vuelve. */
	fun confirmar(pregunta: String, textoSi: String, alSi: (ActividadRescate) -> Unit) {
		abrirPagina(Pagina(getString(R.string.rescate_confirmar_titulo), pregunta, { _ ->
			listOf(Opcion(textoSi) { a ->
				// La pregunta se quita antes de hacerlo: lo que se haga vuelve a la página de antes
				a.pila.removeLast()
				a.construir()
				alSi(a)
			})
		}))
	}

	/**
	 * Abre una pantalla de fuera (Ajustes) sin terminar el rescate. Si no hay un
	 * lector que hable, enciende el lector de rescate para que se oiga esa
	 * pantalla. Tres pulsaciones rápidas de volumen arriba vuelven al menú.
	 */
	override fun abrirFuera(intent: Intent, explicacion: String): Boolean =
		salirPara(explicacion) {
			startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
			true
		}

	/**
	 * Sale del menú para [abrir] otra pantalla sin terminar el rescate. Si no hay
	 * un lector que hable, enciende el lector de rescate para que se oiga esa
	 * pantalla. Tres pulsaciones rápidas de volumen arriba vuelven al menú.
	 */
	fun salirPara(explicacion: String, abrir: () -> Boolean): Boolean {
		val servicio = SesionRescate.servicio
		if (servicio != null) {
			if (servicio.lector.modo == LectorRescate.Modo.APAGADO) {
				servicio.ponerModoLector(if (otroLectorActivo()) LectorRescate.Modo.ECO else LectorRescate.Modo.PROPIO)
			}
			servicio.filtrarTeclas(true)
		}
		val texto = explicacion + " " + getString(if (servicio != null) R.string.rescate_volver_con_teclas else R.string.rescate_volver_sin_servicio)
		// Un botón flotante «Volver al rescate», por si las teclas no llegan al servicio
		servicio?.panel?.mostrarVolver { SesionRescate.empezar(applicationContext, SesionRescate.origen) }
		saliendoAProposito = true
		val abierto = try {
			abrir()
		} catch (e: Exception) {
			Registro.w("Rescate", "No se pudo abrir otra pantalla", e)
			false
		}
		if (!abierto) {
			saliendoAProposito = false
			decir(getString(R.string.rescate_no_se_abre))
			return false
		}
		voz.hablar(texto)
		return true
	}

	/** Deja el menú para explorar el móvil con el lector de rescate (la sesión sigue). */
	fun salirAExplorar() {
		SesionRescate.servicio?.panel?.mostrarVolver { SesionRescate.empezar(applicationContext, SesionRescate.origen) }
		saliendoAProposito = true
		moveTaskToBack(true)
	}

	// --- Teclas de volumen ----------------------------------------------------------

	override fun dispatchKeyEvent(evento: KeyEvent): Boolean {
		val codigo = evento.keyCode
		if (codigo != KeyEvent.KEYCODE_VOLUME_UP && codigo != KeyEvent.KEYCODE_VOLUME_DOWN) return super.dispatchKeyEvent(evento)
		val abajo = evento.action == KeyEvent.ACTION_DOWN
		if (codigo == KeyEvent.KEYCODE_VOLUME_UP) arribaPulsada = abajo else abajoPulsada = abajo
		// Las dos a la vez son el atajo de accesibilidad: no se tocan
		if (arribaPulsada && abajoPulsada) {
			combinacion = true
			manejador.removeCallbacks(pulsacionLarga)
			tresArriba.reiniciar()
			return true
		}
		if (combinacion) {
			if (!arribaPulsada && !abajoPulsada) combinacion = false
			return true
		}
		armarEsperaSeguro()
		if (!prefs.teclasEnMenu) {
			// Como en Shortcut Menu: solo cuentan las pulsaciones para el modo seguro y el volumen cambia
			if (codigo == KeyEvent.KEYCODE_VOLUME_UP && !abajo && tresArriba.pulsar(evento.eventTime)) entrarModoSeguro()
			return super.dispatchKeyEvent(evento)
		}
		if (abajo) {
			if (evento.repeatCount == 0) {
				largaHecha = false
				manejador.removeCallbacks(pulsacionLarga)
				manejador.postDelayed(pulsacionLarga, LARGA_MS)
			} else if (evento.isLongPress && !largaHecha) {
				// Android ya marcó la pulsación larga (algunos teclados y mandos la mandan así)
				manejador.removeCallbacks(pulsacionLarga)
				pulsacionLarga.run()
			}
			return true
		}
		manejador.removeCallbacks(pulsacionLarga)
		if (largaHecha) {
			largaHecha = false
			return true
		}
		if (codigo == KeyEvent.KEYCODE_VOLUME_DOWN) {
			tresArriba.reiniciar()
			mover(+1)
		} else {
			if (tresArriba.pulsar(evento.eventTime)) entrarModoSeguro() else mover(-1)
		}
		return true
	}

	// --- Modo seguro ------------------------------------------------------------------

	/**
	 * Modo seguro. Con el permiso avanzado, como en Shortcut Menu: comprueba que
	 * Pico habla, apaga los lectores y el menú habla solo; si se termina sin
	 * lector, devuelve el que apagó. Sin el permiso: modo eco (la voz de rescate
	 * lee lo que enfoca el lector activo).
	 */
	fun entrarModoSeguro() {
		if (terminado || SesionRescate.modoSeguro) return
		voz.senal(Senal.ATENCION)
		if (!servicios.puedeEscribir()) {
			val servicio = SesionRescate.servicio
			if (servicio != null && otroLectorActivo()) {
				servicio.ponerModoLector(LectorRescate.Modo.ECO)
				decir(getString(R.string.rescate_eco_activado))
			} else {
				decir(getString(R.string.rescate_seguro_sin_permiso))
			}
			return
		}
		voz.pico.comprobar { funciona ->
			if (terminado) return@comprobar
			if (!funciona) {
				decir(getString(R.string.rescate_seguro_sin_pico))
				return@comprobar
			}
			decir(getString(R.string.rescate_seguro))
			SesionRescate.modoSeguro = true
			servicios.apagarLectores { apagados ->
				SesionRescate.lectoresApagados = apagados
				if (terminado) {
					SesionRescate.terminar(applicationContext)
					return@apagarLectores
				}
				val texto = when {
					apagados.size > 1 -> getString(R.string.rescate_seguro_apagados_varios, prefs.esperaModoSeguro)
					apagados.size == 1 -> getString(R.string.rescate_seguro_apagado, servicios.nombreDicho(apagados[0]) ?: getString(R.string.rescate_tu_lector), prefs.esperaModoSeguro)
					else -> getString(R.string.rescate_seguro_sin_lectores)
				}
				alPrincipio(texto)
			}
		}
	}

	/** Empieza (o reinicia) la espera tras la que el modo seguro se rinde y devuelve los lectores. */
	private fun armarEsperaSeguro() {
		if (!SesionRescate.modoSeguro || SesionRescate.lectoresApagados.isEmpty()) return
		manejador.removeCallbacksAndMessages(ESPERA_SEGURO)
		manejador.postDelayed({
			decir(getString(R.string.rescate_seguro_tiempo)) { cerrar() }
		}, ESPERA_SEGURO, prefs.esperaModoSeguro * 1000L)
	}

	// --- Salir --------------------------------------------------------------------------

	/** «Salir del rescate»: lo dice y cierra. */
	fun salir() {
		decir(getString(R.string.rescate_saliendo)) { cerrar() }
	}

	/**
	 * Cierra el menú y termina el rescate: devuelve los lectores que apagó el
	 * modo seguro si no ha quedado ninguno y apaga el servicio del atajo.
	 */
	fun cerrar() {
		if (terminado) return
		terminado = true
		manejador.removeCallbacksAndMessages(null)
		trabajo?.cancel()
		if (!prefs.ensayoHecho) prefs.ensayoHecho = true
		SesionRescate.terminar(applicationContext)
		finish()
	}

	fun dp(valor: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), resources.displayMetrics).toInt()

	companion object {
		private val ESPERA_HABLA = Any()
		private const val ESPERA_HABLA_MS = 4000L
		private val ESPERA_SEGURO = Any()
		private const val LARGA_MS = 650L

		@Volatile var visible = false
			private set
		private var abierta: WeakReference<ActividadRescate>? = null

		fun abrir(contexto: Context) {
			contexto.startActivity(SesionRescate.intentMenu(contexto))
		}

		/** Cierra el menú abierto, si lo hay, sin cambiar nada más. */
		fun cerrarAbierto() {
			abierta?.get()?.cerrar()
		}

		/** Si el menú está abierto (aunque no se vea). */
		fun estaAbierto(): Boolean = abierta?.get() != null

		/** Lo que muestra el menú ahora (para las pruebas y la simulación de averías). */
		data class EstadoMenu(val titulo: String, val opciones: List<String>, val actual: Int, val hablaSolo: Boolean, val zona: android.graphics.Rect?)

		/** El estado del menú abierto, o null. Solo lectura: lo usan las pruebas instrumentadas. */
		fun estadoMenu(): EstadoMenu? {
			val a = abierta?.get() ?: return null
			if (a.terminado || a.pila.isEmpty()) return null
			val zona = a.vista?.let { v ->
				val pos = IntArray(2)
				v.getLocationOnScreen(pos)
				android.graphics.Rect(pos[0], pos[1], pos[0] + v.width, pos[1] + v.height)
			}
			return EstadoMenu(a.pagina.titulo, a.opciones.map { it.texto }, a.actual, a.hablaSolo, zona)
		}
	}
}
