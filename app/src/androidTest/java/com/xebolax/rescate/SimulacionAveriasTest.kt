package com.xebolax.rescate

import android.app.KeyguardManager
import android.app.UiAutomation
import android.media.AudioManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xebolax.rescate.diagnostico.ProbadorTts
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.rescate.ActividadRescate
import com.xebolax.rescate.rescate.SesionRescate
import com.xebolax.rescate.servicio.LectorRescate
import com.xebolax.rescate.servicio.ServicioGuardian
import com.xebolax.rescate.sistema.Instantanea
import com.xebolax.rescate.sistema.ListaServicios
import com.xebolax.rescate.sistema.Servicios
import com.xebolax.rescate.tts.ServicioTtsPropio
import com.xebolax.rescate.voz.EspeakNativo
import com.xebolax.rescate.voz.Grabaciones
import com.xebolax.rescate.voz.PicoNativo
import com.xebolax.rescate.voz.VozEmergencia
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simulación de averías de accesibilidad, SOLO PARA EL EMULADOR.
 *
 * Cada escenario rompe algo de verdad en el emulador (TalkBack apagado, motor de
 * voz roto, volumen a cero, servicios que roban el control…), después lo arregla
 * manejando Rescate Accesible como lo haría una persona ciega (las teclas de
 * volumen, arrastrar el dedo por el menú que habla y levantarlo, confirmar con
 * volumen arriba) y comprueba el estado real del sistema leyendo los ajustes y
 * los servicios. Cada paso, gesto y comprobación queda en un informe JSON
 * (Android/data/<paquete>/files/simulacion/informe.json) con el que se genera el
 * informe que se ve en Ajustes.
 *
 * El atajo de accesibilidad (mantener las dos teclas de volumen) no se puede
 * pulsar desde una prueba: se simula activando el servicio del atajo, que es
 * exactamente lo que hace Android al detectarlo.
 *
 * NUNCA se pasa en un móvil real: cambia servicios y ajustes de accesibilidad.
 */
@RunWith(AndroidJUnit4::class)
class SimulacionAveriasTest {
	private val inst = InstrumentationRegistry.getInstrumentation()
	private val app = inst.targetContext
	private val ui: UiAutomation = inst.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
	private val p = app.packageName
	private val atajo = "$p/com.xebolax.rescate.servicio.ServicioAtajo"
	private val guardian = "$p/com.xebolax.rescate.servicio.ServicioGuardian"
	private val talkback = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
	private val seleccionar = "com.google.android.marvin.talkback/com.google.android.accessibility.selecttospeak.SelectToSpeakService"
	private val vozGoogle = "com.google.android.tts"
	private val servicios = Servicios(app)
	private val prefs = Preferencias(app)
	private lateinit var t: android.content.Context
	private val informe = JSONArray()
	private val carpeta = File(app.getExternalFilesDir(null), "simulacion").apply { mkdirs() }

	private class Escenario(val id: String, val titulo: String, val averia: String) {
		val pasos = mutableListOf<String>()
		val comprobaciones = mutableListOf<Pair<String, Boolean>>()
		val inicio = SystemClock.elapsedRealtime()
	}

	private var e: Escenario? = null

	// --- Utilidades ----------------------------------------------------------------------

	private fun sh(comando: String): String {
		val pfd: ParcelFileDescriptor = ui.executeShellCommand(comando)
		return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }.trim()
	}

	private fun paso(texto: String) {
		e?.pasos?.add(texto)
		Log.i("Simulacion", "PASO | $texto")
	}

	private fun comprobar(texto: String, ok: Boolean): Boolean {
		e?.comprobaciones?.add(texto to ok)
		Log.i("Simulacion", (if (ok) "BIEN | " else "MAL  | ") + texto)
		return ok
	}

	private fun esperar(ms: Long, condicion: () -> Boolean): Boolean {
		val limite = SystemClock.elapsedRealtime() + ms
		while (SystemClock.elapsedRealtime() < limite) {
			if (runCatching(condicion).getOrDefault(false)) return true
			SystemClock.sleep(250)
		}
		return runCatching(condicion).getOrDefault(false)
	}

	private fun dormir(ms: Long) = SystemClock.sleep(ms)

	private fun activados() = servicios.activados()
	private fun activo(c: String) = ListaServicios.contiene(activados(), c)
	private fun enMarcha(c: String) = ListaServicios.contiene(servicios.enMarcha(), c)

	private fun ponerServicios(lista: List<String>) {
		sh("settings put secure enabled_accessibility_services " + (if (lista.isEmpty()) "\"\"" else lista.joinToString(":")))
		sh("settings put secure accessibility_enabled " + if (lista.isEmpty()) "0" else "1")
	}

	private fun secure(clave: String): String = sh("settings get secure $clave")

	private fun tecla(codigo: String, veces: Int = 1) {
		repeat(veces) {
			sh("input keyevent $codigo")
			dormir(450)
		}
	}

	/** Tres pulsaciones rápidas de volumen arriba (en una sola orden: cada orden aparte tarda demasiado). */
	private fun tresVecesArriba() {
		sh("input keyevent KEYCODE_VOLUME_UP KEYCODE_VOLUME_UP KEYCODE_VOLUME_UP")
		dormir(1200)
	}

	private fun mantener(codigo: String) {
		sh("input keyevent --longpress $codigo")
		dormir(800)
	}

	private fun menu() = ActividadRescate.estadoMenu()

	private fun s(id: Int, vararg args: Any): String = t.getString(id, *args)

	/** El atajo de accesibilidad: lo que hace Android al mantener las dos teclas de volumen. */
	private fun abrirRescate(): Boolean {
		paso("Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]")
		ponerServicios(activados().filterNot { ListaServicios.normalizar(it) == ListaServicios.normalizar(atajo) } + atajo)
		val ok = esperar(15_000) { menu() != null }
		dormir(1500)
		menu()?.let { paso("Se abre el menú de rescate («${it.titulo}», ${it.opciones.size} opciones). " + if (it.hablaSolo) "Sin lector activo: el menú habla solo con la voz de rescate." else "Con un lector activo: el menú es una lista de botones.") }
		return comprobar("El menú de rescate se abre con el atajo", ok)
	}

	/** Elige una opción con las teclas de volumen, como haría el usuario con su lector mudo. */
	private fun elegirConTeclas(contiene: String): Boolean {
		if (!esperar(12_000) { menu()?.opciones?.any { it.contains(contiene, ignoreCase = true) } == true }) {
			return comprobar("Encuentra la opción «$contiene» en «${menu()?.titulo}» (opciones: ${menu()?.opciones?.joinToString(" | ")})", false)
		}
		val m = menu()!!
		val indice = m.opciones.indexOfFirst { it.contains(contiene, ignoreCase = true) }
		var pulsaciones = 0
		while ((menu()?.actual ?: -1) != indice && pulsaciones < 40) {
			tecla("KEYCODE_VOLUME_DOWN")
			pulsaciones++
		}
		val texto = m.opciones[indice]
		paso("Pulsa volumen abajo $pulsaciones ${if (pulsaciones == 1) "vez" else "veces"} hasta oír «$texto» y mantiene pulsado volumen abajo para elegirla.")
		mantener("KEYCODE_VOLUME_DOWN")
		dormir(1200)
		return true
	}

	/** Elige una opción con el dedo en el menú que habla solo: arrastrar hasta oírla y levantar. */
	private fun elegirTocando(contiene: String): Boolean {
		if (!esperar(12_000) { menu()?.opciones?.any { it.contains(contiene, ignoreCase = true) } == true && menu()?.zona != null }) {
			return comprobar("Encuentra la opción «$contiene» en la pantalla que habla", false)
		}
		val m = menu()!!
		val zona = m.zona!!
		val indice = m.opciones.indexOfFirst { it.contains(contiene, ignoreCase = true) }
		val alto = zona.height().toFloat() / m.opciones.size
		val x = zona.centerX()
		val y = (zona.top + alto * indice + alto / 2).toInt()
		// Se empieza en la primera opción, no en el borde: desde el borde Android abre la cortina de notificaciones
		val desde = (zona.top + alto / 2).toInt()
		if (y == desde) sh("input swipe $x $desde ${x + 40} $y 600") else sh("input swipe $x $desde $x $y 900")
		paso("Pone un dedo en la primera opción, lo desliza hacia abajo oyendo cada opción al pasar y lo levanta al oír «${m.opciones[indice]}».")
		dormir(1500)
		return true
	}

	/** Pulsa volumen arriba cuando el asistente de Ajustes pregunta (sí). */
	private fun confirmarConVolumenArriba(ms: Long = 60_000): Boolean {
		val pregunta = esperar(ms) { SesionRescate.servicio?.oyenteTeclas != null }
		if (!pregunta) return comprobar("La guía por los Ajustes pide confirmación con las teclas", false)
		dormir(1500)
		tecla("KEYCODE_VOLUME_UP")
		paso("Oye la pregunta de la guía (volumen arriba sí, volumen abajo no) y pulsa volumen arriba.")
		return true
	}

	private fun cerrarRescate() {
		inst.runOnMainSync { ActividadRescate.cerrarAbierto() }
		esperar(5000) { menu() == null }
		dormir(1000)
	}

	private fun salirConTeclas() {
		if (menu() == null) return
		tecla("KEYCODE_BACK", 4)
		esperar(6000) { menu() == null }
		if (menu() != null) cerrarRescate()
	}

	/** El idioma en que la voz de Google de este emulador habla de verdad (sin Internet puede no tener el español). */
	private var idiomaVoz: Locale = Locale("es", "ES")

	private fun probarVozSistema(): Boolean = runBlocking { ProbadorTts(app).probar(null, idiomaVoz).funciona }

	/** Vuelve al menú principal con Atrás (sin salir del rescate). */
	private fun alPrincipio() {
		var veces = 0
		while (menu() != null && menu()?.titulo != s(R.string.app_name) && veces < 6) {
			tecla("KEYCODE_BACK")
			dormir(800)
			veces++
		}
		if (veces > 0) paso("Pulsa Atrás $veces ${if (veces == 1) "vez" else "veces"} para volver al menú principal (Atrás vuelve a la página anterior).")
	}

	/** Pone un volumen en por ciento: con AudioManager y, si Android no lo deja (accesibilidad), desde el shell. */
	private fun fijarVolumen(flujo: Int, porCiento: Int) {
		val a = app.getSystemService(AudioManager::class.java)
		val valor = a.getStreamMaxVolume(flujo) * porCiento / 100
		runCatching { a.setStreamVolume(flujo, valor, 0) }
		if (a.getStreamVolume(flujo) != valor) sh("cmd media_session volume --stream $flujo --set $valor")
		dormir(300)
	}

	private fun volumen(flujo: Int): Int {
		val a = app.getSystemService(AudioManager::class.java)
		return a.getStreamVolume(flujo) * 100 / a.getStreamMaxVolume(flujo).coerceAtLeast(1)
	}

	/** Deja el emulador como un móvil sano: TalkBack activo, voz de Google, volumen normal, sin averías simuladas. */
	private fun sano(conPermiso: Boolean = true) {
		cerrarRescate()
		sh("cmd statusbar collapse")
		// TalkBack pide el permiso de notificaciones al arrancar tras una detención forzada: su
		// diálogo se quedaría con el foco y taparía el menú en el escenario siguiente
		if (Build.VERSION.SDK_INT >= 33) sh("pm grant com.google.android.marvin.talkback android.permission.POST_NOTIFICATIONS")
		if (sh("dumpsys window").lines().any { it.contains("mCurrentFocus") && it.contains("GrantPermissionsActivity") }) sh("input keyevent KEYCODE_BACK")
		PicoNativo.simularFallo = false
		EspeakNativo.simularFallo = false
		sh("pm enable $vozGoogle")
		if (conPermiso) sh("pm grant $p android.permission.WRITE_SECURE_SETTINGS")
		else sh("pm revoke $p android.permission.WRITE_SECURE_SETTINGS")
		sh("settings put secure tts_default_synth $vozGoogle")
		sh("settings put secure tts_default_rate 100")
		sh("settings put secure tts_default_pitch 100")
		fijarVolumen(AudioManager.STREAM_MUSIC, 70)
		if (Build.VERSION.SDK_INT >= 26) fijarVolumen(AudioManager.STREAM_ACCESSIBILITY, 70)
		ponerServicios(listOf(talkback))
		esperar(15_000) { enMarcha(talkback) }
		dormir(2000)
	}

	private fun escenario(id: String, titulo: String, averia: String, cuerpo: () -> Unit) {
		val esc = Escenario(id, titulo, averia)
		e = esc
		Log.i("Simulacion", "=== $id: $titulo ===")
		try {
			cuerpo()
		} catch (x: Throwable) {
			Log.e("Simulacion", "Fallo en $id", x)
			comprobar("El escenario termina sin errores inesperados (${x.javaClass.simpleName}: ${x.message})", false)
		}
		val o = JSONObject()
			.put("id", id)
			.put("titulo", titulo)
			.put("averia", averia)
			.put("segundos", (SystemClock.elapsedRealtime() - esc.inicio) / 1000)
			.put("pasos", JSONArray(esc.pasos))
			.put("comprobaciones", JSONArray(esc.comprobaciones.map { JSONObject().put("texto", it.first).put("bien", it.second) }))
			.put("superado", esc.comprobaciones.isNotEmpty() && esc.comprobaciones.all { it.second })
		informe.put(o)
		guardar()
		e = null
	}

	private fun guardar() {
		val raiz = JSONObject()
			.put("fecha", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date()))
			.put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
			.put("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
			.put("version_app", BuildConfig.VERSION_NAME)
			.put("talkback", runCatching { app.packageManager.getPackageInfo("com.google.android.marvin.talkback", 0).versionName }.getOrNull() ?: "")
			.put("escenarios", informe)
		File(carpeta, "informe.json").writeText(raiz.toString(2))
	}

	// --- La simulación -------------------------------------------------------------------

	@Test
	fun simularAverias() {
		Idioma.cambiar(app, "es")
		t = Idioma.envolver(app)
		prefs.teclasEnMenu = true
		prefs.decirPosicion = true
		prefs.esperaModoSeguro = 15
		prefs.motorRescate = Preferencias.MOTOR_PICO
		sh("settings put secure accessibility_shortcut_dialog_shown 1")
		sano()
		prefs.motorConocido = vozGoogle
		// Sin Internet, la voz de Google del emulador puede no tener el español: se prueba en el idioma que sí tenga
		idiomaVoz = listOf(Locale("es", "ES"), Locale.US, Locale.UK).firstOrNull { l -> runBlocking { ProbadorTts(app).probar(null, l).funciona } } ?: Locale("es", "ES")
		Log.i("Simulacion", "La voz de Google del emulador habla en $idiomaVoz")

		escenario("S01", "Ningún motor de voz del sistema funciona",
			"Se deshabilita la app Servicios de voz de Google (el motor predeterminado) y se avería el motor Pico de la app. TalkBack sigue activo pero no tiene ninguna voz: el usuario no oye nada.") {
			sh("pm disable-user --user 0 $vozGoogle")
			PicoNativo.simularFallo = true
			dormir(2000)
			comprobar("Avería real: la voz del sistema no habla", !probarVozSistema())
			abrirRescate()
			esperar(15_000) { SesionRescate.vozSiExiste()?.motor == VozEmergencia.Motor.ESPEAK }
			comprobar("El rescate habla con su segunda voz incluida (eSpeak NG), sin el motor del sistema ni Pico", SesionRescate.vozSiExiste()?.motor == VozEmergencia.Motor.ESPEAK)
			PicoNativo.simularFallo = false
			paso("(La avería simulada de Pico se retira aquí: solo quería demostrar que el rescate habla aunque fallen todas las demás voces.)")
			elegirConTeclas(s(R.string.pag_reparar))
			elegirConTeclas("Google")
			elegirConTeclas(s(R.string.rep_habilitar, "").trim())
			paso("La guía abre la información de la app en Ajustes, dice lo que encuentra y pulsa «Habilitar» (habilitar no es irreversible: no pide confirmación) y vuelve al menú de rescate.")
			comprobar("La app Servicios de voz de Google vuelve a estar habilitada", esperar(40_000) { app.packageManager.getApplicationInfo(vozGoogle, 0).enabled })
			comprobar("El motor predeterminado sigue siendo la voz de Google (leído del ajuste del sistema)", secure("tts_default_synth") == vozGoogle)
			if (menu() == null) abrirRescate()
			alPrincipio()
			elegirConTeclas(s(R.string.pag_voz))
			elegirConTeclas(s(R.string.voz_probar_predeterminado, "").substringBefore(":"))
			paso("Oye el resultado de la prueba y una frase dicha con la voz de Google.")
			salirConTeclas()
			comprobar("La voz del sistema vuelve a hablar (prueba real de síntesis en ${idiomaVoz.displayName})", esperar(20_000) { probarVozSistema() })
		}

		sano()
		escenario("S02", "Se eligió un motor de voz que falla y TalkBack se calla",
			"Se elige como motor predeterminado la voz de Google y después se deshabilita (como un motor estropeado o desinstalado a medias). TalkBack activo.") {
			sh("settings put secure tts_default_synth $vozGoogle")
			sh("pm disable-user --user 0 $vozGoogle")
			dormir(3000)
			abrirRescate()
			elegirConTeclas(s(R.string.pag_voz))
			elegirConTeclas(s(R.string.voz_usar_propia))
			comprobar("El motor predeterminado es ahora la voz de rescate de la app (leído del ajuste del sistema)", esperar(10_000) { secure("tts_default_synth") == p })
			val antes = ServicioTtsPropio.sintesisPedidas
			salirConTeclas()
			tecla("KEYCODE_HOME")
			dormir(3000)
			tecla("KEYCODE_APP_SWITCH")
			dormir(3000)
			tecla("KEYCODE_HOME")
			comprobar("TalkBack vuelve a hablar: el sistema pidió ${ServicioTtsPropio.sintesisPedidas - antes} frases al motor de rescate", esperar(15_000) { ServicioTtsPropio.sintesisPedidas > antes })
		}

		sano()
		escenario("S03", "TalkBack desactivado (sin ningún lector)",
			"Se desactiva TalkBack. El móvil no tiene ningún lector de pantalla: no habla nada.") {
			ponerServicios(emptyList())
			esperar(10_000) { !enMarcha(talkback) }
			abrirRescate()
			comprobar("Sin lector, el menú habla solo y se maneja con el dedo", menu()?.hablaSolo == true)
			elegirTocando(s(R.string.pag_lectores))
			elegirTocando("TalkBack")
			elegirTocando(s(R.string.lector_activar, "TalkBack"))
			comprobar("TalkBack activado y en marcha", esperar(20_000) { activo(talkback) && enMarcha(talkback) })
			comprobar("El rescate se cierra solo para que hable TalkBack", esperar(10_000) { menu() == null })
		}

		sano(conPermiso = false)
		escenario("S04", "TalkBack desactivado y sin permiso avanzado",
			"Como S03, pero la app no tiene el permiso avanzado: no puede activar TalkBack directamente y tiene que guiar por los Ajustes del sistema.") {
			ponerServicios(emptyList())
			esperar(10_000) { !enMarcha(talkback) }
			abrirRescate()
			elegirConTeclas(s(R.string.pag_lectores))
			elegirConTeclas("TalkBack")
			elegirConTeclas(s(R.string.lector_activar, "TalkBack"))
			paso("La guía abre Ajustes de accesibilidad, lee lo que encuentra con la voz de rescate, entra en TalkBack y busca su interruptor.")
			confirmarConVolumenArriba()
			comprobar("TalkBack activado desde los Ajustes del sistema (la guía pulsó el interruptor y «Permitir»)", esperar(40_000) { activo(talkback) })
			comprobar("TalkBack en marcha", esperar(15_000) { enMarcha(talkback) })
			cerrarRescate()
		}

		sano()
		escenario("S05", "Volumen a cero",
			"Se bajan a cero el volumen multimedia y el de accesibilidad: aunque todo funcione, no se oye nada.") {
			fijarVolumen(AudioManager.STREAM_MUSIC, 0)
			if (Build.VERSION.SDK_INT >= 26) fijarVolumen(AudioManager.STREAM_ACCESSIBILITY, 0)
			dormir(1000)
			comprobar("Avería real: volumen multimedia al 0 %", volumen(AudioManager.STREAM_MUSIC) == 0)
			abrirRescate()
			paso("Al abrirse, el rescate comprueba el volumen y lo sube solo hasta el mínimo elegido (60 %), y lo dice.")
			comprobar("Volumen multimedia subido (${volumen(AudioManager.STREAM_MUSIC)} %)", esperar(5000) { volumen(AudioManager.STREAM_MUSIC) >= 50 })
			if (Build.VERSION.SDK_INT >= 26) comprobar("Volumen de accesibilidad subido (${volumen(AudioManager.STREAM_ACCESSIBILITY)} %)", volumen(AudioManager.STREAM_ACCESSIBILITY) >= 50)
			elegirConTeclas(s(R.string.pag_sonido))
			elegirConTeclas(s(R.string.sonido_subir))
			paso("Oye «Volumen al … por ciento».")
			salirConTeclas()
		}

		sano()
		escenario("S06", "Voz demasiado rápida y aguda",
			"Se pone la velocidad de la voz del sistema al 600 % y el tono al 50 %: TalkBack habla de forma ininteligible.") {
			sh("settings put secure tts_default_rate 600")
			sh("settings put secure tts_default_pitch 50")
			abrirRescate()
			elegirConTeclas(s(R.string.pag_voz))
			elegirConTeclas(s(R.string.voz_normalizar))
			comprobar("Velocidad de la voz del sistema al 100 %", esperar(8000) { secure("tts_default_rate") == "100" })
			comprobar("Tono de la voz del sistema al 100 %", secure("tts_default_pitch") == "100")
			salirConTeclas()
		}

		sano()
		escenario("S07", "Modo seguro: apagar el lector mudo y recuperarlo solo",
			"TalkBack está activo pero se supone mudo. El usuario necesita que el menú hable con su propia voz sin TalkBack, y que TalkBack vuelva solo si no hace nada.") {
			abrirRescate()
			tresVecesArriba()
			paso("Pulsa tres veces rápido volumen arriba (modo seguro de Shortcut Menu).")
			comprobar("Modo seguro: TalkBack apagado", esperar(10_000) { !activo(talkback) })
			comprobar("El menú pasa a hablar solo con la voz de rescate", esperar(5000) { menu()?.hablaSolo == true })
			paso("No toca nada durante 15 segundos (la espera del modo seguro).")
			comprobar("Pasada la espera, TalkBack vuelve a activarse solo", esperar(40_000) { activo(talkback) })
			comprobar("TalkBack en marcha y el rescate cerrado", esperar(15_000) { enMarcha(talkback) && menu() == null })
		}

		sano(conPermiso = false)
		escenario("S08", "Modo eco: TalkBack activo pero mudo, sin permiso avanzado",
			"Sin el permiso avanzado no se puede apagar TalkBack. El usuario quiere oír lo que TalkBack enfoca con la voz de rescate.") {
			abrirRescate()
			tresVecesArriba()
			paso("Pulsa tres veces rápido volumen arriba.")
			comprobar("Modo eco: la voz de rescate lee lo que enfoca TalkBack, sin tocar TalkBack", esperar(8000) { SesionRescate.servicio?.lector?.modo == LectorRescate.Modo.ECO })
			comprobar("TalkBack sigue activado", activo(talkback))
			salirConTeclas()
			comprobar("Al salir del rescate se apaga el modo eco", esperar(8000) { menu() == null })
		}

		sano()
		escenario("S09", "Otro servicio de accesibilidad roba el control",
			"Se activa «Seleccionar para pronunciar» junto a TalkBack, un servicio que el usuario no quiere y que interfiere.") {
			ponerServicios(listOf(talkback, seleccionar))
			esperar(10_000) { enMarcha(seleccionar) }
			abrirRescate()
			elegirConTeclas(s(R.string.pag_lectores))
			elegirConTeclas(t.resources.getQuantityString(R.plurals.pag_otros_servicios, 1, 1).take(8))
			elegirConTeclas(s(R.string.servicio_desactivar_otro, "").trim())
			comprobar("El servicio que molestaba queda desactivado", esperar(10_000) { !activo(seleccionar) })
			comprobar("TalkBack sigue activado", activo(talkback))
			salirConTeclas()
		}

		sano()
		escenario("S10", "TalkBack cerrado a la fuerza (limpiador o ahorro de batería)",
			"Se fuerza la detención de TalkBack, como hacen algunos limpiadores y el ahorro de batería de muchos fabricantes.") {
			sh("am force-stop com.google.android.marvin.talkback")
			dormir(3000)
			comprobar("Avería real: TalkBack no está en marcha (Android ${if (activo(talkback)) "lo deja activado pero caído" else "además lo quita de los servicios activados"})", !enMarcha(talkback))
			abrirRescate()
			elegirConTeclas(s(R.string.pag_lectores))
			elegirConTeclas("TalkBack")
			val opciones = menu()?.opciones.orEmpty()
			elegirConTeclas(opciones.firstOrNull() ?: "TalkBack")
			comprobar("TalkBack activado y en marcha otra vez", esperar(40_000) { activo(talkback) && enMarcha(talkback) })
			cerrarRescate()
		}

		sano()
		escenario("S11", "Restaurar la configuración buena guardada",
			"Con todo funcionando se guarda la configuración. Después se rompen varias cosas a la vez: TalkBack apagado, motor de voz cambiado y velocidad al 300 %.") {
			comprobar("Configuración buena guardada", Instantanea(app).guardar())
			ponerServicios(emptyList())
			sh("settings put secure tts_default_synth $p")
			sh("settings put secure tts_default_rate 300")
			esperar(10_000) { !enMarcha(talkback) }
			abrirRescate()
			elegirConTeclas(s(R.string.pag_restaurar))
			elegirConTeclas(s(R.string.restaurar_hacer))
			elegirConTeclas(s(R.string.restaurar_hacer))
			paso("Confirma «${s(R.string.restaurar_hacer)}» en la página de confirmación.")
			comprobar("TalkBack activado de nuevo", esperar(20_000) { activo(talkback) })
			comprobar("Motor de voz de Google de nuevo", esperar(10_000) { secure("tts_default_synth") == vozGoogle })
			comprobar("Velocidad al 100 % de nuevo", secure("tts_default_rate") == "100")
			cerrarRescate()
		}

		sano()
		escenario("S12", "Fallan las voces incluidas: grabaciones y pitidos",
			"Se averían a la vez Pico y eSpeak NG dentro de la app (bibliotecas dañadas o sin memoria).") {
			PicoNativo.simularFallo = true
			abrirRescate()
			comprobar("Con Pico averiado el rescate pasa a eSpeak NG", esperar(15_000) { SesionRescate.vozSiExiste()?.motor == VozEmergencia.Motor.ESPEAK })
			cerrarRescate()
			EspeakNativo.simularFallo = true
			abrirRescate()
			comprobar("Con las dos voces averiadas el rescate usa las grabaciones de respaldo", esperar(20_000) { SesionRescate.vozSiExiste()?.motor == VozEmergencia.Motor.GRABACIONES })
			val principal = s(R.string.pag_lectores)
			comprobar("Hay grabación de «$principal» en español (Pico o eSpeak) para decirla sin síntesis", Grabaciones(app).leer(principal, Locale("es", "ES")) != null)
			comprobar("Hay grabación de «${Idioma.envolver(app).let { s(R.string.rescate_manejo_teclas).take(30) }}…»", Grabaciones(app).leer(s(R.string.rescate_manejo_teclas), Locale("es", "ES")) != null)
			elegirConTeclas(s(R.string.pag_lectores))
			paso("Cada opción suena con su grabación y, además, vibra tantas veces como su número.")
			comprobar("El menú sigue funcionando sin ninguna síntesis", menu()?.titulo == s(R.string.pag_lectores))
			PicoNativo.simularFallo = false
			EspeakNativo.simularFallo = false
			cerrarRescate()
		}

		sano()
		escenario("S13", "El guardián detecta que la voz del sistema se ha roto",
			"El guardián está activado. Se deshabilita la voz de Google y se avería el motor Pico: TalkBack queda mudo sin que el usuario haya hecho nada.") {
			ponerServicios(listOf(talkback, guardian))
			esperar(10_000) { ServicioGuardian.instancia != null }
			sh("pm disable-user --user 0 $vozGoogle")
			PicoNativo.simularFallo = true
			prefs.ultimaComprobacionVoz = 0L
			sh("input keyevent KEYCODE_SLEEP")
			dormir(2000)
			sh("input keyevent KEYCODE_WAKEUP")
			paso("El usuario enciende la pantalla. El guardián comprueba en silencio la voz del sistema.")
			comprobar("El guardián detecta la voz rota y activa el modo eco (la voz de rescate lee lo que enfoca TalkBack)", esperar(40_000) { ServicioGuardian.instancia?.lector?.modo == LectorRescate.Modo.ECO })
			comprobar("El guardián muestra su aviso con «Abrir el rescate»", esperar(10_000) { ServicioGuardian.instancia != null })
			dormir(2000)
			tecla("KEYCODE_VOLUME_UP")
			paso("Oye el aviso del guardián y pulsa volumen arriba (o toca «Abrir el rescate» en la mitad de abajo de la pantalla).")
			comprobar("Se abre el rescate (incluso sobre la pantalla de bloqueo)", esperar(15_000) { menu() != null })
			val bloqueado = app.getSystemService(KeyguardManager::class.java).isKeyguardLocked
			paso("Pantalla de bloqueo ${if (bloqueado) "activa" else "no activa"} al abrirse el rescate.")
			PicoNativo.simularFallo = false
			elegirConTeclas(s(R.string.pag_voz))
			elegirConTeclas(s(R.string.voz_usar_propia))
			comprobar("La voz de rescate es el motor del sistema", esperar(10_000) { secure("tts_default_synth") == p })
			salirConTeclas()
		}

		sano()
		escenario("S14", "El guardián avisa de que TalkBack se ha apagado",
			"Con el guardián activado, TalkBack se apaga (por error, por un fallo o por otra app).") {
			ponerServicios(listOf(talkback, guardian))
			esperar(10_000) { ServicioGuardian.instancia != null }
			dormir(3000)
			ponerServicios(listOf(guardian))
			paso("TalkBack se apaga. El guardián lo dice con la voz de rescate: «Atención: tu lector de pantalla se ha apagado…».")
			esperar(20_000) { ServicioGuardian.instancia?.filtrandoTeclas == true }
			dormir(3000)
			tecla("KEYCODE_VOLUME_UP")
			paso("Oye el aviso del guardián y pulsa volumen arriba (o toca «Abrir el rescate» en la mitad de abajo de la pantalla).")
			comprobar("Se abre el rescate desde el guardián", esperar(15_000) { menu() != null })
			elegirConTeclas(s(R.string.pag_lectores))
			elegirConTeclas("TalkBack")
			elegirConTeclas(s(R.string.lector_activar, "TalkBack"))
			comprobar("TalkBack activado y en marcha", esperar(20_000) { activo(talkback) && enMarcha(talkback) })
			comprobar("El guardián sigue activado vigilando", activo(guardian))
			cerrarRescate()
		}

		sano()
		escenario("S15", "Diagnóstico automático",
			"Se rompen varias cosas a la vez (velocidad extrema, volumen a cero, servicio extra activado) y se pide el diagnóstico.") {
			sh("settings put secure tts_default_rate 500")
			fijarVolumen(AudioManager.STREAM_MUSIC, 0)
			ponerServicios(listOf(talkback, seleccionar))
			abrirRescate()
			elegirConTeclas(s(R.string.pag_diagnostico))
			comprobar("El diagnóstico termina y presenta sus hallazgos", esperar(30_000) { menu()?.titulo == s(R.string.pag_diagnostico) && (menu()?.opciones?.size ?: 0) > 1 })
			val hallazgos = menu()?.opciones.orEmpty()
			paso("Oye los hallazgos: ${hallazgos.joinToString(" | ")}")
			comprobar("Detecta la velocidad de voz extrema", hallazgos.any { it.contains("velocidad", true) || it.contains("rápid", true) })
			comprobar("Detecta el servicio que interfiere", hallazgos.any { it.contains("Seleccionar", true) || it.contains("servicio", true) })
			salirConTeclas()
		}

		// Todo como estaba: TalkBack, voz de Google y el permiso concedido
		sano()
		ponerServicios(listOf(talkback))
		guardar()
		Log.i("Simulacion", "Informe en ${File(carpeta, "informe.json")}")
	}
}
