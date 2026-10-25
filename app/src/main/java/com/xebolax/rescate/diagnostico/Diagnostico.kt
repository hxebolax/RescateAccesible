package com.xebolax.rescate.diagnostico

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import com.xebolax.rescate.R
import com.xebolax.rescate.guia.AsistenteAjustes
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.reparar.CopiasApk
import com.xebolax.rescate.reparar.ReinstalacionException
import com.xebolax.rescate.reparar.Reinstalador
import com.xebolax.rescate.reparar.Tiendas
import com.xebolax.rescate.rescate.ActividadRescate
import com.xebolax.rescate.sistema.ComparadorInstantanea
import com.xebolax.rescate.sistema.EntradaServicio
import com.xebolax.rescate.sistema.Instantanea
import com.xebolax.rescate.sistema.ListaServicios
import com.xebolax.rescate.sistema.Paquetes
import com.xebolax.rescate.sistema.Servicios
import com.xebolax.rescate.voz.SalidaAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * El diagnóstico: comprueba con API públicas todo lo que suele dejar mudo un
 * móvil Android para una persona ciega (ver docs/investigacion) y propone
 * cómo arreglar cada cosa, con el permiso avanzado o guiando por los Ajustes.
 *
 * Ninguna reparación dice que algo cambió sin comprobarlo leyendo después el
 * estado real del sistema.
 */
class Diagnostico(contexto: Context) {
	private val app = contexto.applicationContext
	private val textos = Idioma.envolver(app)
	private val servicios = Servicios(app)
	private val prefs = Preferencias(app)
	private val sonido = EstadoSonido(app)

	/** El diagnóstico completo, por orden de importancia. Tarda unos segundos (prueba la voz del sistema). */
	suspend fun ejecutar(): List<Hallazgo> {
		val lista = mutableListOf<Hallazgo>()
		seccion(lista, "lectores") { lectores() }
		seccion(lista, "voz") { vozSistema(probar = true) }
		seccion(lista, "volumen") { volumen() }
		seccion(lista, "componentes") { componentesGoogle() }
		seccion(lista, "otros servicios") { otrosServicios() }
		seccion(lista, "atajo") { atajo() }
		seccion(lista, "sonido") { sonidoExtra() }
		seccion(lista, "idioma") { idioma() }
		seccion(lista, "batería") { bateria() }
		seccion(lista, "preparación") { preparacion(sinGraves = lista.none { it.gravedad == Gravedad.GRAVE }) }
		seccion(lista, "pantalla") { pantalla() }
		// Lo grave primero; dentro de cada gravedad, el orden de las comprobaciones
		return lista.sortedBy { it.gravedad.ordinal }
	}

	/**
	 * Lo más grave que se ve en un vistazo (menos de un segundo y medio, sin
	 * probar la voz del sistema), para decirlo al abrir el rescate; o null.
	 */
	suspend fun avisoRapido(): Hallazgo? = withTimeoutOrNull(RAPIDO_MS) {
		val lista = mutableListOf<Hallazgo>()
		seccion(lista, "lectores") { lectores() }
		Reglas.masGrave(lista)?.let { return@withTimeoutOrNull it }
		seccion(lista, "volumen") { volumen() }
		Reglas.masGrave(lista)?.let { return@withTimeoutOrNull it }
		seccion(lista, "voz") { vozSistema(probar = false) }
		Reglas.masGrave(lista)
	}

	private suspend fun seccion(lista: MutableList<Hallazgo>, nombre: String, comprobar: suspend () -> List<Hallazgo>) {
		try {
			lista += comprobar()
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "Falló la comprobación de $nombre", e)
		}
	}

	private fun t(id: Int, vararg args: Any): String = textos.getString(id, *args)

	// --- Lectores de pantalla ------------------------------------------------------------

	private fun lectores(): List<Hallazgo> {
		val hallazgos = mutableListOf<Hallazgo>()
		val lectores = servicios.lectores()
		if (lectores.isEmpty()) {
			hallazgos += Hallazgo("sin_lectores_instalados", Gravedad.GRAVE, t(R.string.diag_sin_lectores), t(R.string.diag_sin_lectores_detalle),
				reparacionesReinstalar(Paquetes.TALKBACK, "TalkBack"))
			return hallazgos
		}
		for (e in lectores) {
			if (Reglas.estadoLector(e.activo, e.enMarcha) == Reglas.EstadoLector.CAIDO) {
				hallazgos += Hallazgo("lector_caido_${e.paquete}", Gravedad.GRAVE, t(R.string.diag_lector_caido, e.nombre), t(R.string.diag_lector_caido_detalle, e.nombre),
					listOfNotNull(
						reparacionReiniciar(e),
						Reparacion(t(R.string.diag_rep_forzar, e.nombre), t(R.string.diag_rep_forzar_ayuda)) { g -> AsistenteAjustes(g).forzarDetencion(e.paquete, e.nombre) },
					) + reparacionesReinstalar(e.paquete, e.nombre, soloCopia = true))
			}
		}
		val activos = lectores.filter { it.activo && it.enMarcha }
		if (activos.size > 1) {
			hallazgos += Hallazgo("dos_lectores", Gravedad.GRAVE, t(R.string.diag_dos_lectores), t(R.string.diag_dos_lectores_detalle, activos.joinToString(", ") { it.nombre }),
				activos.map { e -> reparacionDesactivar(e) })
		}
		if (lectores.none { it.activo }) {
			val elegido = lectorPreferido(lectores)
			// Grave si el usuario tiene un lector habitual o la configuración guardada tenía uno
			val usaLector = prefs.lectorHabitual.isNotEmpty() || ListaServicios.leer(Instantanea(app).guardada()[ComparadorInstantanea.SERVICIOS]).let { servicios.hayLector(it) }
			hallazgos += Hallazgo("ningun_lector", if (usaLector) Gravedad.GRAVE else Gravedad.AVISO, t(R.string.diag_ningun_lector), t(R.string.diag_ningun_lector_detalle, elegido.nombre),
				listOf(reparacionActivar(elegido)))
		} else if (activos.size == 1 && hallazgos.isEmpty()) {
			hallazgos += Hallazgo("lector_bien", Gravedad.BIEN, t(R.string.diag_lector_bien, activos[0].nombre), "")
		}
		return hallazgos
	}

	/** El lector que conviene activar: el habitual, si no TalkBack de Google, si no el primero. */
	private fun lectorPreferido(lectores: List<EntradaServicio>): EntradaServicio =
		lectores.firstOrNull { prefs.lectorHabitual.isNotEmpty() && ListaServicios.normalizar(it.componente) == ListaServicios.normalizar(prefs.lectorHabitual) }
			?: lectores.firstOrNull { it.paquete == Paquetes.TALKBACK }
			?: lectores.first()

	private fun reparacionActivar(e: EntradaServicio) = Reparacion(t(R.string.diag_rep_activar, e.nombre), t(R.string.diag_rep_activar_ayuda)) { g ->
		if (servicios.puedeEscribir()) {
			val ok = suspendCancellableCoroutine { c -> servicios.activar(e.componente) { c.resume(it) } }
			val activo = ok && esperar { ListaServicios.contiene(servicios.activados(), e.componente) }
			if (activo) cerrarRescateTras(g, t(R.string.diag_hecho_activado, e.nombre)) else t(R.string.diag_no_activado, e.nombre)
		} else {
			AsistenteAjustes(g).activarServicio(e.componente, e.nombre)
		}
	}

	private fun reparacionDesactivar(e: EntradaServicio) = Reparacion(t(R.string.diag_rep_desactivar, e.nombre)) { g ->
		if (servicios.puedeEscribir()) {
			val ok = servicios.desactivar(e.componente) && esperar { !ListaServicios.contiene(servicios.activados(), e.componente) }
			t(if (ok) R.string.diag_hecho_desactivado else R.string.diag_no_desactivado, e.nombre)
		} else {
			AsistenteAjustes(g).desactivarServicio(e.componente, e.nombre)
		}
	}

	private fun reparacionReiniciar(e: EntradaServicio) = Reparacion(t(R.string.diag_rep_reiniciar, e.nombre), t(R.string.diag_rep_reiniciar_ayuda)) { g ->
		if (servicios.puedeEscribir()) {
			val ok = suspendCancellableCoroutine { c -> servicios.reiniciar(e.componente) { c.resume(it) } }
			val enMarcha = ok && esperar(8000) { ListaServicios.contiene(servicios.enMarcha(), e.componente) }
			if (enMarcha) cerrarRescateTras(g, t(R.string.diag_hecho_reiniciado, e.nombre)) else t(R.string.diag_no_reiniciado, e.nombre)
		} else {
			val asistente = AsistenteAjustes(g)
			asistente.desactivarServicio(e.componente, e.nombre)
			asistente.activarServicio(e.componente, e.nombre)
		}
	}

	/**
	 * Reinstalar [paquete]: desde la copia guardada si la hay (sin Internet) y,
	 * salvo [soloCopia], desde la tienda.
	 */
	private fun reparacionesReinstalar(paquete: String, nombre: String, soloCopia: Boolean = false): List<Reparacion> {
		val lista = mutableListOf<Reparacion>()
		val copia = runCatching { CopiasApk(app).de(paquete).firstOrNull() }.getOrNull()
		if (copia != null) {
			lista += Reparacion(t(R.string.diag_rep_reinstalar_copia, nombre, copia.versionName), t(R.string.diag_rep_reinstalar_copia_ayuda)) { g ->
				val reinstalador = Reinstalador(g.actividad)
				if (!reinstalador.puedeInstalar()) {
					abrirCon(g, t(R.string.diag_permiso_instalar)) {
						g.actividad.startActivity(reinstalador.intentPermiso().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
						true
					}
					return@Reparacion null
				}
				reinstalador.problemaCon(copia)?.let { return@Reparacion it }
				try {
					reinstalador.desdeCopia(copia)
					AsistenteAjustes(g).confirmarInstalacion()
				} catch (e: ReinstalacionException) {
					e.message ?: t(R.string.diag_no_reinstalado, nombre)
				}
			}
		}
		if (!soloCopia) {
			lista += Reparacion(t(R.string.diag_rep_tienda, nombre), t(R.string.diag_rep_tienda_ayuda)) { g ->
				val abierta = abrirCon(g, t(R.string.diag_tienda_abierta, nombre)) { Tiendas.abrirEnTienda(g.actividad, paquete) }
				if (abierta) null else t(R.string.diag_sin_tienda)
			}
		}
		return lista
	}

	// --- Voz del sistema -----------------------------------------------------------------

	private suspend fun vozSistema(probar: Boolean): List<Hallazgo> {
		val hallazgos = mutableListOf<Hallazgo>()
		val motores = servicios.motores()
		val ajenos = motores.filter { it.paquete != app.packageName }
		val predeterminado = servicios.motorPredeterminado()
		val nombrePred = motores.firstOrNull { it.paquete == predeterminado }?.nombre ?: predeterminado.orEmpty()
		val reparacionesVoz = reparacionesVoz(predeterminado)
		if (ajenos.isEmpty()) {
			hallazgos += Hallazgo("sin_motores", Gravedad.AVISO, t(R.string.diag_sin_motores), t(R.string.diag_sin_motores_detalle), reparacionesVoz + reparacionesReinstalar(Paquetes.TTS_GOOGLE, Paquetes.MOTORES[Paquetes.TTS_GOOGLE] ?: "Google"))
		}
		if (!predeterminado.isNullOrBlank() && motores.none { it.paquete == predeterminado }) {
			hallazgos += Hallazgo("motor_desinstalado", Gravedad.GRAVE, t(R.string.diag_motor_desinstalado), t(R.string.diag_motor_desinstalado_detalle, predeterminado), reparacionesVoz)
		} else if (!predeterminado.isNullOrBlank() && Tiendas.deshabilitada(app, predeterminado)) {
			hallazgos += Hallazgo("motor_deshabilitado", Gravedad.GRAVE, t(R.string.diag_motor_deshabilitado, nombrePred), t(R.string.diag_motor_deshabilitado_detalle, nombrePred),
				listOf(Reparacion(t(R.string.diag_rep_habilitar, nombrePred)) { g -> AsistenteAjustes(g).habilitarApp(predeterminado, nombrePred) }) + reparacionesVoz)
		}
		val velocidad = servicios.velocidadSistema()
		val tono = servicios.tonoSistema()
		if (Reglas.velocidadExtrema(velocidad) || Reglas.tonoExtremo(tono)) {
			hallazgos += Hallazgo("voz_extrema", Gravedad.AVISO, t(R.string.diag_voz_extrema), t(R.string.diag_voz_extrema_detalle, velocidad ?: 100, tono ?: 100),
				listOf(
					Reparacion(t(R.string.diag_rep_normalizar), necesitaPermiso = true) { _ ->
						val ok = servicios.normalizarVozSistema() && servicios.velocidadSistema() == 100 && servicios.tonoSistema() == 100
						t(if (ok) R.string.diag_hecho_normalizado else R.string.diag_no_normalizado)
					},
					reparacionAbrirVoz(t(R.string.diag_instrucciones_normalizar)),
				))
		}
		if (!probar) return hallazgos
		val locale = Instantanea.idiomaSistema()
		val prueba = ProbadorTts(app).probar(null, locale)
		val nombre = nombrePred.ifEmpty { t(R.string.diag_el_motor) }
		when {
			!prueba.arranca -> hallazgos += Hallazgo("motor_no_arranca", Gravedad.GRAVE, t(R.string.diag_motor_no_arranca, nombre), t(R.string.diag_motor_no_arranca_detalle, nombre), reparacionesVoz + reparacionesReparar(predeterminado, nombre))
			prueba.idioma != null && prueba.idioma < TextToSpeech.LANG_AVAILABLE -> hallazgos += Hallazgo("motor_sin_idioma", Gravedad.GRAVE, t(R.string.diag_motor_sin_idioma, nombre, locale.getDisplayLanguage(Idioma.efectivo(app))),
				t(R.string.diag_motor_sin_idioma_detalle, nombre), listOfNotNull(reparacionInstalarVoces(predeterminado, nombre)) + reparacionesVoz)
			!prueba.habla -> hallazgos += Hallazgo("motor_no_habla", Gravedad.GRAVE, t(R.string.diag_motor_no_habla, nombre), t(R.string.diag_motor_no_habla_detalle, nombre), reparacionesVoz + reparacionesReparar(predeterminado, nombre))
			else -> {
				(prueba.motorReal ?: predeterminado)?.let { if (it != app.packageName) prefs.motorConocido = it }
				hallazgos += Hallazgo("voz_bien", Gravedad.BIEN, t(R.string.diag_voz_bien, nombre), "")
			}
		}
		return hallazgos
	}

	/** Lo que arregla casi siempre una voz rota: usar la voz incluida, volver a la conocida o abrir los ajustes. */
	private fun reparacionesVoz(predeterminado: String?): List<Reparacion> {
		val lista = mutableListOf<Reparacion>()
		if (predeterminado != app.packageName) lista += reparacionMotor(app.packageName, t(R.string.diag_voz_propia_nombre), t(R.string.diag_rep_voz_propia), t(R.string.diag_rep_voz_propia_ayuda))
		val conocido = prefs.motorConocido
		if (conocido.isNotEmpty() && conocido != predeterminado && servicios.motores().any { it.paquete == conocido }) {
			val nombre = servicios.motores().first { it.paquete == conocido }.nombre
			lista += reparacionMotor(conocido, nombre, t(R.string.diag_rep_voz_conocida, nombre), t(R.string.diag_rep_voz_conocida_ayuda))
		}
		lista += reparacionAbrirVoz(t(R.string.diag_instrucciones_voz))
		return lista
	}

	private fun reparacionMotor(paquete: String, nombre: String, texto: String, ayuda: String) = Reparacion(texto, ayuda) { g ->
		if (servicios.puedeEscribir()) {
			val ok = servicios.ponerMotorPredeterminado(paquete)
			t(if (ok) R.string.diag_hecho_motor else R.string.diag_no_motor, nombre)
		} else {
			AsistenteAjustes(g).elegirMotorPredeterminado(paquete, nombre)
		}
	}

	private fun reparacionAbrirVoz(instrucciones: String) = Reparacion(t(R.string.diag_rep_abrir_voz)) { g ->
		val intent = Intent(AsistenteAjustes.ACCION_AJUSTES_TTS)
		val destino = if (intent.resolveActivity(app.packageManager) != null) intent else Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
		if (g.abrirFuera(destino, instrucciones)) null else t(R.string.diag_no_se_abre)
	}

	private fun reparacionInstalarVoces(paquete: String?, nombre: String): Reparacion? {
		if (paquete.isNullOrBlank()) return null
		return Reparacion(t(R.string.diag_rep_instalar_voces, nombre), t(R.string.diag_rep_instalar_voces_ayuda)) { g ->
			val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(paquete)
			if (intent.resolveActivity(app.packageManager) != null && g.abrirFuera(intent, t(R.string.diag_voces_abiertas, nombre))) null
			else t(R.string.diag_sin_pantalla_voces, nombre)
		}
	}

	/** Reparaciones del paquete del motor: desinstalar actualizaciones si las tiene y reinstalar la copia. */
	private fun reparacionesReparar(paquete: String?, nombre: String): List<Reparacion> {
		if (paquete.isNullOrBlank() || paquete == app.packageName) return emptyList()
		val lista = mutableListOf<Reparacion>()
		if (Tiendas.tieneActualizaciones(app, paquete)) {
			lista += Reparacion(t(R.string.diag_rep_desinstalar_act, nombre), t(R.string.diag_rep_desinstalar_act_ayuda)) { g -> AsistenteAjustes(g).desinstalarActualizaciones(paquete, nombre) }
		}
		lista += Reparacion(t(R.string.diag_rep_borrar_cache, nombre)) { g -> AsistenteAjustes(g).borrarCache(paquete, nombre) }
		lista += reparacionesReinstalar(paquete, nombre, soloCopia = true)
		return lista
	}

	// --- Volumen y sonido ------------------------------------------------------------------

	private fun volumen(): List<Hallazgo> {
		val hallazgos = mutableListOf<Hallazgo>()
		val accesibilidad = sonido.volumenAccesibilidad()
		val multimedia = sonido.volumenMultimedia()
		val gA = Reglas.gravedadVolumen(accesibilidad)
		val gM = Reglas.gravedadVolumen(multimedia)
		if (gA != null || gM != null) {
			val gravedad = if (gA == Gravedad.GRAVE || gM == Gravedad.GRAVE) Gravedad.GRAVE else Gravedad.AVISO
			hallazgos += Hallazgo("volumen_bajo", gravedad, t(R.string.diag_volumen_bajo), t(R.string.diag_volumen_bajo_detalle, accesibilidad.coerceAtLeast(0), multimedia.coerceAtLeast(0)),
				listOf(Reparacion(t(R.string.diag_rep_subir_volumen), t(R.string.diag_rep_subir_volumen_ayuda)) { _ ->
					SalidaAudio(app).asegurarVolumen()
					val a = sonido.volumenAccesibilidad()
					val m = sonido.volumenMultimedia()
					if (Reglas.gravedadVolumen(a) == null && Reglas.gravedadVolumen(m) == null) t(R.string.diag_hecho_volumen, a, m)
					else t(R.string.diag_no_volumen, a, m)
				}))
		}
		return hallazgos
	}

	private fun sonidoExtra(): List<Hallazgo> {
		val hallazgos = mutableListOf<Hallazgo>()
		val bt = sonido.bluetooth()
		if (bt.isNotEmpty()) {
			hallazgos += Hallazgo("bluetooth", Gravedad.AVISO, t(R.string.diag_bluetooth, bt.joinToString(", ")), t(R.string.diag_bluetooth_detalle),
				listOf(
					Reparacion(t(R.string.diag_rep_altavoz)) { _ ->
						prefs.altavoz = "siempre"
						t(R.string.diag_hecho_altavoz)
					},
					Reparacion(t(R.string.diag_rep_ajustes_bluetooth)) { g ->
						if (g.abrirFuera(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), t(R.string.diag_bluetooth_abierto))) null else t(R.string.diag_no_se_abre)
					},
				))
		} else if (sonido.cable()) {
			hallazgos += Hallazgo("cable", Gravedad.INFO, t(R.string.diag_cable), t(R.string.diag_cable_detalle))
		}
		if (sonido.modoTimbre() != AudioManager.RINGER_MODE_NORMAL || sonido.noMolestar()) {
			hallazgos += Hallazgo("silencio", Gravedad.INFO, t(R.string.diag_silencio), t(R.string.diag_silencio_detalle))
		}
		if (Reglas.balanceExtremo(sonido.balance())) {
			hallazgos += Hallazgo("balance", Gravedad.AVISO, t(R.string.diag_balance), t(R.string.diag_balance_detalle),
				listOfNotNull(reparacionShell(t(R.string.diag_rep_centrar_balance), "settings put system master_balance 0.0") { sonido.balance()?.let { kotlin.math.abs(it) < 0.01f } == true },
					reparacionAbrirAccesibilidad(t(R.string.diag_instrucciones_balance))))
		}
		if (sonido.mono() == true) {
			hallazgos += Hallazgo("mono", Gravedad.INFO, t(R.string.diag_mono), t(R.string.diag_mono_detalle),
				listOfNotNull(reparacionShell(t(R.string.diag_rep_quitar_mono), "settings put system master_mono 0") { sonido.mono() == false },
					reparacionAbrirAccesibilidad(t(R.string.diag_instrucciones_mono))))
		}
		return hallazgos
	}

	/**
	 * Una reparación por shell (root o la depuración inalámbrica integrada), solo
	 * si está disponible; comprueba el resultado leyendo el estado real.
	 */
	private fun reparacionShell(texto: String, comando: String, comprobacion: () -> Boolean): Reparacion? {
		if (!com.xebolax.rescate.permiso.ShellRescate.posible(app)) return null
		return Reparacion(texto, t(R.string.diag_rep_shell_ayuda)) { _ ->
			val salida = com.xebolax.rescate.permiso.ShellRescate.ejecutar(app, comando)
			t(if (salida != null && comprobacion()) R.string.diag_hecho_shell else R.string.diag_no_cambiado)
		}
	}

	private fun reparacionAbrirAccesibilidad(instrucciones: String) = Reparacion(t(R.string.diag_rep_abrir_accesibilidad)) { g ->
		if (g.abrirFuera(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), instrucciones)) null else t(R.string.diag_no_se_abre)
	}

	// --- TalkBack y la voz de Google como apps ----------------------------------------------

	private fun componentesGoogle(): List<Hallazgo> {
		val hallazgos = mutableListOf<Hallazgo>()
		for (paquete in listOf(Paquetes.TALKBACK, Paquetes.TTS_GOOGLE, Paquetes.TALKBACK_SAMSUNG, Paquetes.TTS_SAMSUNG)) {
			if (!Tiendas.instalada(app, paquete)) continue
			val nombre = nombreApp(paquete)
			if (Tiendas.deshabilitada(app, paquete)) {
				hallazgos += Hallazgo("deshabilitada_$paquete", Gravedad.GRAVE, t(R.string.diag_app_deshabilitada, nombre), t(R.string.diag_app_deshabilitada_detalle, nombre),
					listOf(Reparacion(t(R.string.diag_rep_habilitar, nombre)) { g -> AsistenteAjustes(g).habilitarApp(paquete, nombre) }) + reparacionesReinstalar(paquete, nombre, soloCopia = true))
			} else if (Tiendas.tieneActualizaciones(app, paquete) && paquete in Paquetes.ESENCIALES) {
				hallazgos += Hallazgo("actualizada_$paquete", Gravedad.INFO, t(R.string.diag_app_actualizada, nombre), t(R.string.diag_app_actualizada_detalle, nombre),
					listOf(Reparacion(t(R.string.diag_rep_desinstalar_act, nombre), t(R.string.diag_rep_desinstalar_act_ayuda)) { g -> AsistenteAjustes(g).desinstalarActualizaciones(paquete, nombre) }))
			}
		}
		if (!Tiendas.instalada(app, Paquetes.TALKBACK) && servicios.lectores().isNotEmpty()) {
			hallazgos += Hallazgo("sin_talkback", Gravedad.INFO, t(R.string.diag_sin_talkback), t(R.string.diag_sin_talkback_detalle), reparacionesReinstalar(Paquetes.TALKBACK, "TalkBack"))
		}
		return hallazgos
	}

	private fun nombreApp(paquete: String): String = runCatching {
		val pm = app.packageManager
		pm.getApplicationInfo(paquete, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS).loadLabel(pm).toString()
	}.getOrNull() ?: Paquetes.LECTORES[paquete] ?: Paquetes.MOTORES[paquete] ?: paquete

	/** Servicios activos que no son lectores y pueden quedarse con el tacto o las teclas (Acceso con interruptores…). */
	private fun otrosServicios(): List<Hallazgo> {
		val otros = servicios.instalados().filter { !it.lector && it.activo }
		if (otros.isEmpty()) return emptyList()
		return listOf(Hallazgo("otros_servicios", Gravedad.INFO, t(R.string.diag_otros_servicios, otros.joinToString(", ") { it.nombre }), t(R.string.diag_otros_servicios_detalle),
			otros.map { reparacionDesactivar(it) }))
	}

	// --- Atajo y servicios propios ---------------------------------------------------------

	private fun atajo(): List<Hallazgo> {
		val hallazgos = mutableListOf<Hallazgo>()
		if (Build.VERSION.SDK_INT < 26) {
			hallazgos += Hallazgo("sin_atajo_android", Gravedad.INFO, t(R.string.diag_sin_atajo_android), t(R.string.diag_sin_atajo_android_detalle))
		} else {
			val objetivos = servicios.objetivosAtajo(Servicios.CLAVE_ATAJO_VOLUMEN)
			when (Reglas.estadoAtajo(objetivos, servicios.atajo, ListaServicios::normalizar)) {
				Reglas.EstadoAtajo.SIN_ASIGNAR, Reglas.EstadoAtajo.OTRO -> hallazgos += Hallazgo("atajo_no_nuestro", Gravedad.AVISO, t(R.string.diag_atajo_no_nuestro),
					t(R.string.diag_atajo_no_nuestro_detalle), listOf(reparacionAtajoSoloNuestro(), reparacionAbrirAccesibilidad(t(R.string.diag_instrucciones_atajo))))
				Reglas.EstadoAtajo.COMPARTIDO -> hallazgos += Hallazgo("atajo_compartido", Gravedad.AVISO, t(R.string.diag_atajo_compartido),
					t(R.string.diag_atajo_compartido_detalle), listOf(reparacionAtajoSoloNuestro(), reparacionAbrirAccesibilidad(t(R.string.diag_instrucciones_atajo))))
				Reglas.EstadoAtajo.SOLO_NUESTRO -> Unit
			}
			val dialogo = Servicios.leerSeguro(app, Servicios.CLAVE_DIALOGO_ATAJO) == "1"
			if (!Reglas.atajoEnBloqueo(Servicios.leerSeguro(app, Servicios.CLAVE_ATAJO_BLOQUEO), dialogo)) {
				hallazgos += Hallazgo("atajo_bloqueo", Gravedad.AVISO, t(R.string.diag_atajo_bloqueo), t(R.string.diag_atajo_bloqueo_detalle),
					listOf(Reparacion(t(R.string.diag_rep_atajo_bloqueo), necesitaPermiso = true) { _ ->
						val ok = Servicios.escribirSeguro(app, Servicios.CLAVE_ATAJO_BLOQUEO, "1") && Servicios.leerSeguro(app, Servicios.CLAVE_ATAJO_BLOQUEO) == "1"
						t(if (ok) R.string.diag_hecho_atajo_bloqueo else R.string.diag_no_cambiado)
					}))
			}
			if (!dialogo) {
				hallazgos += Hallazgo("atajo_pregunta", Gravedad.INFO, t(R.string.diag_atajo_pregunta), t(R.string.diag_atajo_pregunta_detalle),
					listOf(Reparacion(t(R.string.diag_rep_atajo_pregunta), necesitaPermiso = true) { _ ->
						t(if (servicios.saltarPreguntaAtajo()) R.string.diag_hecho_atajo_pregunta else R.string.diag_no_cambiado)
					}))
			}
		}
		if (!ListaServicios.contiene(servicios.activados(), servicios.guardian)) {
			hallazgos += Hallazgo("guardian_apagado", Gravedad.INFO, t(R.string.diag_guardian), t(R.string.diag_guardian_detalle),
				listOf(Reparacion(t(R.string.diag_rep_guardian)) { g ->
					val nombre = t(R.string.diag_guardian_nombre)
					if (servicios.puedeEscribir()) {
						val ok = suspendCancellableCoroutine { c -> servicios.activar(servicios.guardian) { c.resume(it) } } &&
							esperar { ListaServicios.contiene(servicios.activados(), servicios.guardian) }
						t(if (ok) R.string.diag_hecho_activado else R.string.diag_no_activado, nombre)
					} else {
						AsistenteAjustes(g).activarServicio(servicios.guardian, servicioNombre(servicios.guardian) ?: nombre)
					}
				}))
		}
		if (!servicios.puedeEscribir()) {
			hallazgos += Hallazgo("sin_permiso", Gravedad.INFO, t(R.string.diag_sin_permiso), t(R.string.diag_sin_permiso_detalle))
		}
		return hallazgos
	}

	/** El nombre con el que Ajustes muestra un servicio propio (su etiqueta del manifiesto). */
	private fun servicioNombre(componente: String): String? = runCatching {
		val cn = ComponentName.unflattenFromString(componente) ?: return null
		val pm = app.packageManager
		pm.getServiceInfo(cn, 0).loadLabel(pm).toString()
	}.getOrNull()

	/** Deja el atajo de volumen solo para Rescate Accesible (con el permiso), comprobándolo. */
	private fun reparacionAtajoSoloNuestro() = Reparacion(t(R.string.diag_rep_atajo_nuestro), t(R.string.diag_rep_atajo_nuestro_ayuda), necesitaPermiso = true) { _ ->
		val ok = Servicios.escribirSeguro(app, Servicios.CLAVE_ATAJO_VOLUMEN, servicios.atajo) &&
			Reglas.estadoAtajo(servicios.objetivosAtajo(Servicios.CLAVE_ATAJO_VOLUMEN), servicios.atajo, ListaServicios::normalizar) == Reglas.EstadoAtajo.SOLO_NUESTRO
		if (ok) servicios.saltarPreguntaAtajo()
		t(if (ok) R.string.diag_hecho_atajo else R.string.diag_no_cambiado)
	}

	// --- Idioma, batería, preparación y pantalla ------------------------------------------

	private fun idioma(): List<Hallazgo> {
		val sistema = Instantanea.idiomaSistema()
		val guardado = Instantanea(app).guardada()[ComparadorInstantanea.IDIOMA]
		if (!Reglas.idiomaRaro(sistema.language, Idioma.efectivo(app).language, guardado)) return emptyList()
		val nombre = sistema.getDisplayName(Idioma.efectivo(app))
		return listOf(Hallazgo("idioma", Gravedad.AVISO, t(R.string.diag_idioma, nombre), t(R.string.diag_idioma_detalle, nombre),
			listOf(Reparacion(t(R.string.diag_rep_idioma), t(R.string.diag_rep_idioma_ayuda)) { g ->
				if (g.abrirFuera(Intent(Settings.ACTION_LOCALE_SETTINGS), t(R.string.diag_instrucciones_idioma))) null else t(R.string.diag_no_se_abre)
			})))
	}

	private fun bateria(): List<Hallazgo> {
		val hallazgos = mutableListOf<Hallazgo>()
		val energia = app.getSystemService(PowerManager::class.java) ?: return hallazgos
		val vigilar = LinkedHashSet<String>()
		servicios.lectores().filter { it.activo }.forEach { vigilar += it.paquete }
		servicios.motorPredeterminado()?.takeIf { it.isNotBlank() && Tiendas.instalada(app, it) }?.let { vigilar += it }
		vigilar += app.packageName
		val optimizados = vigilar.filter { p -> runCatching { !energia.isIgnoringBatteryOptimizations(p) }.getOrDefault(false) }
		if (optimizados.isNotEmpty()) {
			hallazgos += Hallazgo("bateria", Gravedad.INFO, t(R.string.diag_bateria), t(R.string.diag_bateria_detalle, optimizados.joinToString(", ") { nombreApp(it) }),
				listOfNotNull(
					Reparacion(t(R.string.diag_rep_bateria)) { g ->
						if (g.abrirFuera(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), t(R.string.diag_instrucciones_bateria))) null else t(R.string.diag_no_se_abre)
					},
					pantallaFabricante()?.let { intent ->
						Reparacion(t(R.string.diag_rep_autoarranque), t(R.string.diag_rep_autoarranque_ayuda)) { g ->
							if (g.abrirFuera(intent, t(R.string.diag_instrucciones_autoarranque))) null else t(R.string.diag_no_se_abre)
						}
					},
				))
		}
		if (runCatching { energia.isPowerSaveMode }.getOrDefault(false)) {
			hallazgos += Hallazgo("ahorro", Gravedad.INFO, t(R.string.diag_ahorro), t(R.string.diag_ahorro_detalle))
		}
		return hallazgos
	}

	/** La pantalla de inicio automático o batería del fabricante que exista en este móvil, o null. */
	private fun pantallaFabricante(): Intent? {
		for (componente in PANTALLAS_FABRICANTE) {
			val cn = ComponentName.unflattenFromString(componente) ?: continue
			val intent = Intent().setComponent(cn)
			if (runCatching { intent.resolveActivity(app.packageManager) }.getOrNull() != null) return intent
		}
		val accion = Intent("com.android.settings.action.BACKGROUND_OPTIMIZE")
		return accion.takeIf { runCatching { it.resolveActivity(app.packageManager) }.getOrNull() != null }
	}

	private fun preparacion(sinGraves: Boolean): List<Hallazgo> {
		val hallazgos = mutableListOf<Hallazgo>()
		val copias = CopiasApk(app)
		for (paquete in Paquetes.ESENCIALES) {
			if (!Tiendas.instalada(app, paquete) || Tiendas.deshabilitada(app, paquete)) continue
			if (runCatching { copias.de(paquete).isEmpty() }.getOrDefault(false)) {
				val nombre = nombreApp(paquete)
				hallazgos += Hallazgo("sin_copia_$paquete", Gravedad.INFO, t(R.string.diag_sin_copia, nombre), t(R.string.diag_sin_copia_detalle, nombre),
					if (!sinGraves) emptyList() else listOf(Reparacion(t(R.string.diag_rep_copia, nombre)) { _ ->
						try {
							val copia = copias.hacer(paquete)
							t(R.string.diag_hecho_copia, copia.nombre, copia.versionName)
						} catch (e: CopiasApk.SinEspacioException) {
							t(R.string.diag_sin_espacio)
						}
					}))
			}
		}
		val instantanea = Instantanea(app)
		if (!instantanea.existe()) {
			hallazgos += Hallazgo("sin_instantanea", Gravedad.INFO, t(R.string.diag_sin_instantanea), t(R.string.diag_sin_instantanea_detalle),
				if (!sinGraves) emptyList() else listOf(Reparacion(t(R.string.diag_rep_guardar_instantanea), t(R.string.diag_rep_guardar_instantanea_ayuda)) { _ ->
					t(if (instantanea.guardar()) R.string.diag_hecho_instantanea else R.string.diag_no_instantanea)
				}))
		} else {
			val diferencias = instantanea.diferencias()
			if (diferencias.isNotEmpty()) {
				hallazgos += Hallazgo("instantanea_distinta", Gravedad.AVISO, textos.resources.getQuantityString(R.plurals.diag_instantanea_distinta, diferencias.size, diferencias.size),
					diferencias.joinToString(". "),
					listOf(Reparacion(t(R.string.diag_rep_restaurar), t(R.string.diag_rep_restaurar_ayuda), necesitaPermiso = true) { _ -> instantanea.restaurar() }))
			}
		}
		if (!Tiendas.hayPlayStore(app)) {
			hallazgos += Hallazgo("sin_play", Gravedad.INFO, t(R.string.diag_sin_play), t(R.string.diag_sin_play_detalle))
		}
		return hallazgos
	}

	private fun pantalla(): List<Hallazgo> {
		val hallazgos = mutableListOf<Hallazgo>()
		if (Servicios.leerSeguro(app, CLAVE_AMPLIACION) == "1") {
			hallazgos += Hallazgo("ampliacion", Gravedad.INFO, t(R.string.diag_ampliacion), t(R.string.diag_ampliacion_detalle),
				listOf(reparacionAbrirAccesibilidad(t(R.string.diag_instrucciones_ampliacion))))
		}
		if (Servicios.leerSeguro(app, CLAVE_INVERSION) == "1") {
			hallazgos += Hallazgo("inversion", Gravedad.INFO, t(R.string.diag_inversion), t(R.string.diag_inversion_detalle))
		}
		val escala = runCatching { Settings.System.getFloat(app.contentResolver, Settings.System.FONT_SCALE) }.getOrNull()
		if (escala != null && escala >= Reglas.LETRA_GRANDE) {
			hallazgos += Hallazgo("letra", Gravedad.INFO, t(R.string.diag_letra, (escala * 100).toInt()), t(R.string.diag_letra_detalle))
		}
		return hallazgos
	}

	// --- Herramientas -----------------------------------------------------------------------

	/** Espera a que [condicion] se cumpla (sondeando), como mucho [ms]. */
	private suspend fun esperar(ms: Long = 5000, condicion: () -> Boolean): Boolean =
		withTimeoutOrNull(ms) {
			while (!runCatching(condicion).getOrDefault(false)) delay(250)
			true
		} ?: false

	/**
	 * Abre algo de fuera sin cerrar el rescate: desde el menú, con su «salirPara»
	 * (que mantiene la sesión y enciende el lector de rescate); desde la app, tal cual.
	 */
	private fun abrirCon(g: Guia, explicacion: String, abrir: () -> Boolean): Boolean {
		val rescate = g.actividad as? ActividadRescate
		return rescate?.salirPara(explicacion, abrir) ?: runCatching(abrir).getOrDefault(false)
	}

	/** Dice [texto] y cierra el rescate (un lector acaba de activarse y va a hablar él). Devuelve null. */
	private fun cerrarRescateTras(g: Guia, texto: String): String? {
		val rescate = g.actividad as? ActividadRescate ?: return texto
		rescate.decir(texto) { rescate.cerrar() }
		return null
	}

	companion object {
		private const val ETIQUETA = "Diagnostico"
		private const val RAPIDO_MS = 1500L
		/** Claves seguras ocultas pero legibles (ver docs/investigacion, 3.12). */
		private const val CLAVE_AMPLIACION = "accessibility_display_magnification_enabled"
		private const val CLAVE_INVERSION = "accessibility_display_inversion_enabled"

		/** Pantallas de inicio automático y batería de cada fabricante (AutoStarter, MIT; dontkillmyapp.com). */
		val PANTALLAS_FABRICANTE = listOf(
			"com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
			"com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity",
			"com.huawei.systemmanager/.optimize.process.ProtectActivity",
			"com.coloros.safecenter/.permission.startup.StartupAppListActivity",
			"com.coloros.safecenter/.startupapp.StartupAppListActivity",
			"com.oppo.safe/.permission.startup.StartupAppListActivity",
			"com.iqoo.secure/.ui.phoneoptimize.AddWhiteListActivity",
			"com.iqoo.secure/.ui.phoneoptimize.BgStartUpManager",
			"com.vivo.permissionmanager/.activity.BgStartUpManagerActivity",
			"com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity",
			"com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity",
			"com.oneplus.security/.chainlaunch.view.ChainLaunchAppListActivity",
			"com.asus.mobilemanager/.autostart.AutoStartActivity",
			"com.asus.mobilemanager/.powersaver.PowerSaverSettings",
			"com.evenwell.powersaving.g3/.exception.PowerSaverExceptionActivity",
			"com.letv.android.letvsafe/.AutobootManageActivity",
		)
	}
}
