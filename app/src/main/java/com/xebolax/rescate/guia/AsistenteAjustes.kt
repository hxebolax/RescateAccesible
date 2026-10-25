package com.xebolax.rescate.guia

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.xebolax.rescate.R
import com.xebolax.rescate.diagnostico.Guia
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.permiso.ShellRescate
import com.xebolax.rescate.rescate.ActividadRescate
import com.xebolax.rescate.rescate.SesionRescate
import com.xebolax.rescate.servicio.LectorRescate
import com.xebolax.rescate.servicio.ServicioBase
import com.xebolax.rescate.sistema.ListaServicios
import com.xebolax.rescate.sistema.Servicios
import com.xebolax.rescate.voz.Senal
import com.xebolax.rescate.voz.VozEmergencia
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Guía por los Ajustes del sistema con el servicio de accesibilidad propio: abre
 * la pantalla, busca el elemento, dice lo que encuentra, pide confirmación con
 * las teclas de volumen (arriba sí, abajo no) antes de lo importante, lo pulsa
 * y comprueba el resultado leyendo el estado real del sistema.
 *
 * Es lo mismo que haría el usuario con TalkBack, sin ningún permiso especial:
 * solo pulsa cuando el usuario lo ha pedido. Si no hay servicio propio en
 * marcha o algo no aparece (cada fabricante cambia sus Ajustes), deja abierta
 * la pantalla correcta y dice con precisión qué tocar.
 *
 * Cada método devuelve el texto final que dirá el menú, o null si ya lo dijo él
 * (por ejemplo, al activar un lector, que pasa a hablar por su cuenta).
 */
class AsistenteAjustes(private val guia: Guia) {
	private val contexto: Context = guia.actividad
	private val textos = Idioma.envolver(contexto)
	private val servicios = Servicios(contexto.applicationContext)
	private val cadenas = CadenasSistema(contexto)
	private val servicio: ServicioBase? get() = SesionRescate.servicio
	private val voz: VozEmergencia get() = guia.vozRescate ?: SesionRescate.voz(contexto)

	// --- Servicios de accesibilidad -------------------------------------------------------

	/** Activa el servicio [componente] en Ajustes de accesibilidad (incluido el diálogo «Permitir»). */
	suspend fun activarServicio(componente: String, nombre: String): String? {
		if (servicios.puedeEscribir()) {
			val ok = suspendCancellableCoroutine { c -> servicios.activar(componente) { c.resume(it) } }
			return comprobarActivado(componente, nombre, ok, conPermiso = true)
		}
		if (estaActivo(componente) && enMarcha(componente)) return textos.getString(R.string.guia_ya_activado, nombre)
		val s = servicio ?: return abrirSinServicio(intentAccesibilidad(componente), textos.getString(R.string.guia_instrucciones_activar, nombre))
		if (!abrir(intentAccesibilidad(componente), textos.getString(R.string.guia_abriendo_accesibilidad))) return textos.getString(R.string.guia_no_se_abre)
		silenciarLector()
		val fila = buscarFilaServicio(s, nombre) ?: return dejarInstrucciones(textos.getString(R.string.guia_no_encuentro_servicio, nombre))
		Registro.i(ETIQUETA, "Fila del servicio encontrada")
		if (!pulsar(fila)) return dejarInstrucciones(textos.getString(R.string.guia_no_encuentro_servicio, nombre))
		val interruptor = esperarInterruptorDe(fila, nombre)
			?: return dejarInstrucciones(textos.getString(R.string.guia_no_encuentro_interruptor, nombre))
		Registro.i(ETIQUETA, "Interruptor encontrado (activado: ${interruptor.isChecked})")
		if (interruptor.isChecked) {
			if (enMarcha(componente)) {
				volverAlMenu()
				return textos.getString(R.string.guia_ya_activado, nombre)
			}
			// Activado pero caído: apagarlo y encenderlo lo arranca de nuevo
			decir(textos.getString(R.string.guia_caido_reiniciar, nombre))
			pulsar(interruptor)
			pulsarDialogo(Textos.DETENER)
			delay(ESPERA_CAMBIO_MS)
		}
		if (!confirmar(textos.getString(R.string.guia_confirmar_activar, nombre))) {
			volverAlMenu()
			return textos.getString(R.string.guia_cancelado)
		}
		val esLector = ListaServicios.contiene(servicios.todosLectores().toList(), componente)
		// Si va a hablar otro lector, el de rescate se calla para que no sean dos
		if (esLector) s.ponerModoLector(LectorRescate.Modo.APAGADO)
		pulsar(esperarNodo(1000) { r -> buscarInterruptor(r, nombre) } ?: interruptor)
		decir(textos.getString(R.string.guia_pulsando_permitir))
		pulsarDialogo(Textos.PERMITIR)
		return comprobarActivado(componente, nombre, true, conPermiso = false)
	}

	/** Comprueba de verdad que [componente] quedó activado; si es un lector, lo deja hablar y cierra el rescate. */
	private suspend fun comprobarActivado(componente: String, nombre: String, pedido: Boolean, conPermiso: Boolean): String? {
		val activo = pedido && esperar(ESPERA_ESTADO_MS) { estaActivo(componente) }
		if (!activo) {
			Registro.w(ETIQUETA, "No quedó activado $componente")
			return if (conPermiso) textos.getString(R.string.guia_no_activado, nombre) else dejarInstrucciones(textos.getString(R.string.guia_no_activado_restringido, nombre))
		}
		val esLector = ListaServicios.contiene(servicios.todosLectores().toList(), componente)
		if (esLector) {
			decir(textos.getString(R.string.guia_lector_activado, nombre))
			// El lector toma el relevo: el rescate se cierra para que no hablen dos voces
			(guia.actividad as? ActividadRescate)?.cerrar()
			return null
		}
		volverAlMenu()
		return textos.getString(R.string.guia_activado, nombre)
	}

	/** Desactiva el servicio [componente] en Ajustes de accesibilidad (incluido el diálogo «Detener»). */
	suspend fun desactivarServicio(componente: String, nombre: String): String? {
		if (servicios.puedeEscribir()) {
			val ok = servicios.desactivar(componente) && esperar(ESPERA_ESTADO_MS) { !estaActivo(componente) }
			return textos.getString(if (ok) R.string.guia_desactivado else R.string.guia_no_desactivado, nombre)
		}
		if (!estaActivo(componente)) return textos.getString(R.string.guia_ya_desactivado, nombre)
		val s = servicio ?: return abrirSinServicio(intentAccesibilidad(componente), textos.getString(R.string.guia_instrucciones_desactivar, nombre))
		if (!abrir(intentAccesibilidad(componente), textos.getString(R.string.guia_abriendo_accesibilidad))) return textos.getString(R.string.guia_no_se_abre)
		silenciarLector()
		val fila = buscarFilaServicio(s, nombre) ?: return dejarInstrucciones(textos.getString(R.string.guia_no_encuentro_servicio, nombre))
		if (!pulsar(fila)) return dejarInstrucciones(textos.getString(R.string.guia_no_encuentro_servicio, nombre))
		val interruptor = esperarInterruptorDe(fila, nombre)
			?: return dejarInstrucciones(textos.getString(R.string.guia_no_encuentro_interruptor, nombre))
		if (!interruptor.isChecked) {
			volverAlMenu()
			return textos.getString(R.string.guia_ya_desactivado, nombre)
		}
		if (!confirmar(textos.getString(R.string.guia_confirmar_desactivar, nombre))) {
			volverAlMenu()
			return textos.getString(R.string.guia_cancelado)
		}
		pulsar(interruptor)
		pulsarDialogo(Textos.DETENER)
		val ok = esperar(ESPERA_ESTADO_MS) { !estaActivo(componente) }
		volverAlMenu()
		return textos.getString(if (ok) R.string.guia_desactivado else R.string.guia_no_desactivado, nombre)
	}

	// --- Información de la app ----------------------------------------------------------

	/**
	 * Fuerza la detención de [paquete]. Android quita entonces sus servicios de
	 * accesibilidad de la lista de activados: se avisa antes y, si era un
	 * lector, se ofrece volver a activarlo.
	 */
	suspend fun forzarDetencion(paquete: String, nombre: String): String? {
		val antes = servicios.activados().filter { ListaServicios.paquete(it) == paquete }
		porShell("am force-stop $paquete") { detenida(paquete) }?.let { detenida ->
			// Forzar la detención desactiva sus servicios de accesibilidad: con el permiso se vuelven a activar
			val perdidos = antes.filterNot { estaActivo(it) }
			if (perdidos.isNotEmpty() && servicios.puedeEscribir()) {
				for (c in perdidos) suspendCancellableCoroutine { k -> servicios.activar(c) { k.resume(it) } }
			}
			val siguenFuera = antes.filterNot { estaActivo(it) }
			return textos.getString(if (detenida) R.string.guia_detenida else R.string.guia_no_detenida, nombre) +
				if (perdidos.isNotEmpty() && siguenFuera.isEmpty()) " " + textos.getString(R.string.guia_servicios_reactivados, nombre) else if (siguenFuera.isNotEmpty()) " " + textos.getString(R.string.guia_servicios_apagados, nombre) else ""
		}
		val s = servicio ?: return abrirSinServicio(intentInfo(paquete), textos.getString(R.string.guia_instrucciones_forzar, nombre))
		if (!abrir(intentInfo(paquete), textos.getString(R.string.guia_abriendo_info, nombre))) return textos.getString(R.string.guia_no_se_abre)
		silenciarLector()
		val boton = esperarNodo(ESPERA_PANTALLA_MS) { r -> BuscadorNodos.buscar(r, cadenas.para(Textos.FORZAR_DETENCION)) }
			?: return dejarInstrucciones(textos.getString(R.string.guia_instrucciones_forzar, nombre))
		val pulsable = BuscadorNodos.pulsable(boton)
		// Con el botón desactivado no hay ancestro pulsable: la app ya está detenida
		if (pulsable == null) {
			volverAlMenu()
			return textos.getString(R.string.guia_ya_detenida, nombre)
		}
		val aviso = if (antes.isNotEmpty()) textos.getString(R.string.guia_aviso_forzar_servicios, nombre) + " " else ""
		if (!confirmar(aviso + textos.getString(R.string.guia_confirmar_forzar, nombre))) {
			volverAlMenu()
			return textos.getString(R.string.guia_cancelado)
		}
		pulsar(boton)
		pulsarDialogo(Textos.ACEPTAR, Textos.FORZAR_DETENCION, excepto = boton)
		val detenida = esperar(ESPERA_ESTADO_MS) { detenida(paquete) }
		s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
		val resultado = StringBuilder(textos.getString(if (detenida) R.string.guia_detenida else R.string.guia_no_detenida, nombre))
		// Comprobado en AOSP: forzar la detención desactiva sus servicios de accesibilidad
		val perdidos = antes.filterNot { estaActivo(it) }
		val lector = perdidos.firstOrNull { ListaServicios.contiene(servicios.todosLectores().toList(), it) }
		if (lector != null) {
			decir(resultado.toString())
			if (confirmar(textos.getString(R.string.guia_reactivar_lector, nombre))) return activarServicio(lector, nombre)
			resultado.clear()
			resultado.append(textos.getString(R.string.guia_lector_queda_apagado, nombre))
		} else if (perdidos.isNotEmpty()) {
			resultado.append(' ').append(textos.getString(R.string.guia_servicios_apagados, nombre))
		}
		volverAlMenu()
		return resultado.toString()
	}

	/** Habilita [paquete] si está deshabilitado (botón «Habilitar» de su información). */
	suspend fun habilitarApp(paquete: String, nombre: String): String? {
		if (habilitada(paquete)) return textos.getString(R.string.guia_ya_habilitada, nombre)
		porShell("pm enable $paquete") { habilitada(paquete) }?.let { ok ->
			return textos.getString(if (ok) R.string.guia_habilitada else R.string.guia_no_habilitada, nombre)
		}
		servicio ?: return abrirSinServicio(intentInfo(paquete), textos.getString(R.string.guia_instrucciones_habilitar, nombre))
		if (!abrir(intentInfo(paquete), textos.getString(R.string.guia_abriendo_info, nombre))) return textos.getString(R.string.guia_no_se_abre)
		silenciarLector()
		val boton = esperarNodo(ESPERA_PANTALLA_MS) { r -> BuscadorNodos.buscar(r, cadenas.para(Textos.HABILITAR), soloExacto = true) }
			?: return dejarInstrucciones(textos.getString(R.string.guia_instrucciones_habilitar, nombre))
		decir(textos.getString(R.string.guia_pulsando, BuscadorNodos.pulsable(boton)?.text ?: boton.text ?: nombre))
		pulsar(boton)
		pulsarDialogo(Textos.ACEPTAR)
		val ok = esperar(ESPERA_ESTADO_MS) { habilitada(paquete) }
		volverAlMenu()
		return textos.getString(if (ok) R.string.guia_habilitada else R.string.guia_no_habilitada, nombre)
	}

	/** Desinstala las actualizaciones de una app de sistema (vuelve a la versión de fábrica). */
	suspend fun desinstalarActualizaciones(paquete: String, nombre: String): String? {
		if (tieneActualizaciones(paquete)) porShell("cmd package uninstall-system-updates $paquete") { !tieneActualizaciones(paquete) }?.let { ok ->
			return textos.getString(if (ok) R.string.guia_actualizaciones_quitadas else R.string.guia_actualizaciones_no_quitadas, nombre)
		}
		if (!tieneActualizaciones(paquete)) return textos.getString(R.string.guia_sin_actualizaciones, nombre)
		val s = servicio ?: return abrirSinServicio(intentInfo(paquete), textos.getString(R.string.guia_instrucciones_desinstalar, nombre))
		if (!abrir(intentInfo(paquete), textos.getString(R.string.guia_abriendo_info, nombre))) return textos.getString(R.string.guia_no_se_abre)
		silenciarLector()
		val desinstalar = cadenas.para(Textos.DESINSTALAR_ACTUALIZACIONES)
		esperar(ESPERA_PANTALLA_MS) { raices().isNotEmpty() }
		// En unos móviles es un botón; en otros está en el menú «Más opciones»
		var boton = esperarNodo(2000) { r -> BuscadorNodos.buscar(r, desinstalar) }
		if (boton == null) {
			val mas = BuscadorNodos.buscar(raices(), cadenas.para(Textos.MAS_OPCIONES))
				?: return dejarInstrucciones(textos.getString(R.string.guia_instrucciones_desinstalar, nombre))
			pulsar(mas)
			boton = esperarNodo(ESPERA_PANTALLA_MS) { r -> BuscadorNodos.buscar(r, desinstalar) }
		}
		if (boton == null) return dejarInstrucciones(textos.getString(R.string.guia_instrucciones_desinstalar, nombre))
		if (!confirmar(textos.getString(R.string.guia_confirmar_desinstalar, nombre))) {
			s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
			volverAlMenu()
			return textos.getString(R.string.guia_cancelado)
		}
		pulsar(boton)
		pulsarDialogo(Textos.ACEPTAR, Textos.DESINSTALAR_ACTUALIZACIONES, excepto = boton)
		val ok = esperar(ESPERA_DESINSTALAR_MS) { !tieneActualizaciones(paquete) }
		volverAlMenu()
		return textos.getString(if (ok) R.string.guia_desinstaladas else R.string.guia_no_desinstaladas, nombre)
	}

	/** Borra la caché de [paquete] (Información de la app > Almacenamiento > Borrar caché). */
	suspend fun borrarCache(paquete: String, nombre: String): String? {
		if (android.os.Build.VERSION.SDK_INT >= 33 && ShellRescate.posible(contexto)) {
			val salida = ShellRescate.ejecutar(contexto, "pm clear --cache-only $paquete")
			if (salida != null && salida.contains("Success", ignoreCase = true)) return textos.getString(R.string.guia_cache_borrada, nombre)
		}
		val s = servicio ?: return abrirSinServicio(intentInfo(paquete), textos.getString(R.string.guia_instrucciones_cache, nombre))
		if (!abrir(intentInfo(paquete), textos.getString(R.string.guia_abriendo_info, nombre))) return textos.getString(R.string.guia_no_se_abre)
		silenciarLector()
		val almacenamiento = buscarDesplazando(cadenas.para(Textos.ALMACENAMIENTO))
			?: return dejarInstrucciones(textos.getString(R.string.guia_instrucciones_cache, nombre))
		pulsar(almacenamiento)
		val boton = esperarNodo(ESPERA_PANTALLA_MS) { r -> BuscadorNodos.buscar(r, cadenas.para(Textos.BORRAR_CACHE)) }
			?: return dejarInstrucciones(textos.getString(R.string.guia_instrucciones_cache, nombre))
		val pulsable = BuscadorNodos.pulsable(boton)
		if (pulsable == null) {
			s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
			volverAlMenu()
			return textos.getString(R.string.guia_cache_vacia, nombre)
		}
		val ok = pulsar(boton)
		delay(ESPERA_CAMBIO_MS)
		s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
		volverAlMenu()
		// El tamaño de la caché de otra app no se puede leer sin permisos: se dice solo que se pulsó
		return textos.getString(if (ok) R.string.guia_cache_pulsado else R.string.guia_cache_no_pulsado, nombre)
	}

	// --- Motor de voz ---------------------------------------------------------------------

	/** Elige [paquete] como motor de voz preferido en los ajustes de síntesis de voz. */
	suspend fun elegirMotorPredeterminado(paquete: String, nombre: String): String? {
		if (servicios.puedeEscribir()) {
			val ok = servicios.ponerMotorPredeterminado(paquete)
			return textos.getString(if (ok) R.string.guia_motor_hecho else R.string.guia_motor_no, nombre)
		}
		if (servicios.motorPredeterminado() == paquete) return textos.getString(R.string.guia_motor_ya, nombre)
		val intent = Intent(ACCION_AJUSTES_TTS)
		val hayPantalla = intent.resolveActivity(contexto.packageManager) != null
		servicio ?: return abrirSinServicio(if (hayPantalla) intent else Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), textos.getString(R.string.guia_instrucciones_motor, nombre))
		if (!hayPantalla) return abrirSinServicio(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), textos.getString(R.string.guia_instrucciones_motor, nombre))
		if (!abrir(intent, textos.getString(R.string.guia_abriendo_voz))) return textos.getString(R.string.guia_no_se_abre)
		silenciarLector()
		val preferido = esperarNodo(ESPERA_PANTALLA_MS) { r -> BuscadorNodos.buscar(r, cadenas.para(Textos.MOTOR_PREFERIDO)) }
		if (preferido != null) {
			pulsar(preferido)
			delay(ESPERA_CAMBIO_MS)
		}
		// Algunas versiones listan los motores en la misma pantalla; otras, en la de «Motor preferido»
		val motor = esperarNodo(ESPERA_PANTALLA_MS) { r -> BuscadorNodos.buscar(r, listOf(nombre), soloExacto = true) }
			?: buscarDesplazando(listOf(nombre))
			?: return dejarInstrucciones(textos.getString(R.string.guia_instrucciones_motor, nombre))
		decir(textos.getString(R.string.guia_eligiendo_motor, nombre))
		pulsar(motor)
		// Aviso de Android para motores de otras apps («puede recopilar el texto…»): Aceptar
		pulsarDialogo(Textos.ACEPTAR)
		val ok = esperar(ESPERA_ESTADO_MS) { servicios.motorPredeterminado() == paquete }
		volverAlMenu()
		return textos.getString(if (ok) R.string.guia_motor_hecho else R.string.guia_motor_no, nombre)
	}

	// --- Instalador -----------------------------------------------------------------------

	/**
	 * Con el diálogo del instalador de Android en pantalla, lo lee y, si el
	 * usuario confirma con volumen arriba, pulsa Instalar o Actualizar. El
	 * resultado de la instalación lo dice quien lanzó la instalación.
	 */
	suspend fun confirmarInstalacion(): String? {
		servicio ?: return textos.getString(R.string.guia_instrucciones_instalar)
		val instalar = cadenas.para(Textos.INSTALAR)
		val boton = esperarNodo(ESPERA_INSTALADOR_MS) { r -> BuscadorNodos.buscar(r, instalar, soloExacto = true) }
		if (boton == null) {
			modoLectura()
			return textos.getString(R.string.guia_instrucciones_instalar)
		}
		silenciarLector()
		val dialogo = raices().firstOrNull()?.let { BuscadorNodos.textoDe(it) }.orEmpty()
		val pregunta = if (dialogo.isNotBlank()) textos.getString(R.string.guia_instalador_dice, dialogo) else ""
		if (!confirmar(pregunta + " " + textos.getString(R.string.guia_confirmar_instalar, boton.text ?: boton.contentDescription ?: ""))) {
			modoLectura()
			return textos.getString(R.string.guia_instalar_cancelado)
		}
		if (!pulsar(boton)) {
			modoLectura()
			return textos.getString(R.string.guia_instrucciones_instalar)
		}
		voz.senal(Senal.CORRECTO)
		decir(textos.getString(R.string.guia_instalando))
		// Al terminar, el instalador se cierra o muestra «Hecho»: entonces se vuelve al rescate
		esperar(ESPERA_FIN_INSTALACION_MS) { BuscadorNodos.buscar(raices(), instalar, soloExacto = true) == null && hecho() }
		pulsarDialogo(Textos.ACEPTAR, espera = 1500)
		delay(ESPERA_RESULTADO_MS)
		volverAlMenu()
		return null
	}

	/** Si el instalador ya no está o muestra su botón de terminar («Hecho», «Abrir»). */
	private fun hecho(): Boolean {
		val raices = raices()
		val paquetes = raices.mapNotNull { it.packageName?.toString() }
		return paquetes.none { it.contains("packageinstaller") } ||
			BuscadorNodos.buscar(raices, listOf("Hecho", "Listo", "Done", "Abrir", "Open")) != null
	}

	// --- Herramientas -----------------------------------------------------------------------

	/**
	 * Abre la pantalla de Ajustes de accesibilidad. El extra no documentado
	 * «:settings:fragment_args_key» hace que muchas versiones resalten la fila del
	 * servicio; si no lo entienden, se ignora.
	 */
	private fun intentAccesibilidad(componente: String): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
		.putExtra(":settings:fragment_args_key", componente)

	private fun intentInfo(paquete: String): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$paquete"))

	/**
	 * Abre la pantalla y espera a que su ventana aparezca de verdad antes de
	 * buscar nada en ella (si no, se buscaría en la barra de estado o en la
	 * pantalla anterior).
	 */
	private suspend fun abrir(intent: Intent, explicacion: String): Boolean {
		val destino = runCatching { intent.resolveActivity(contexto.packageManager)?.packageName }.getOrNull()
		if (!guia.abrirFuera(intent, explicacion)) return false
		if (destino != null && !esperar(ESPERA_PANTALLA_MS) { raices().any { it.packageName?.toString() == destino } }) {
			Registro.w(ETIQUETA, "La ventana de $destino no apareció a tiempo")
		}
		// Un momento para que la lista termine de dibujarse
		delay(ESPERA_DIBUJO_MS)
		return true
	}

	/** Sin servicio propio no se puede pulsar nada: se abre la pantalla y se dice qué hacer. */
	private fun abrirSinServicio(intent: Intent, instrucciones: String): String {
		val abierta = guia.abrirFuera(intent, "")
		return if (abierta) instrucciones else textos.getString(R.string.guia_no_se_abre)
	}

	/** No se encontró algo: el lector de rescate vuelve a leer la pantalla y se dice qué tocar. */
	private fun dejarInstrucciones(instrucciones: String): String {
		// Solo textos propios: qué paso de la guía no se pudo completar y qué ventanas había
		Registro.w(ETIQUETA, "La guía no pudo terminar: $instrucciones (ventanas: ${raices().joinToString { it.packageName?.toString().orEmpty() }})")
		// Solo en la compilación de pruebas: qué ve el servicio, para depurar la guía (nunca en la de uso)
		if (com.xebolax.rescate.BuildConfig.DEBUG) raices().forEach { r ->
			val sb = StringBuilder()
			fun volcar(n: AccessibilityNodeInfo, nivel: Int) {
				if (nivel > 25) return
				sb.append("  ".repeat(nivel)).append(n.className?.toString()?.substringAfterLast('.')).append(" «").append(n.text ?: n.contentDescription ?: "")
					.append("» visible=").append(n.isVisibleToUser).append(" marcable=").append(n.isCheckable).append(" pulsable=").appendLine(n.isClickable)
				for (i in 0 until n.childCount) runCatching { n.getChild(i) }.getOrNull()?.let { volcar(it, nivel + 1) }
			}
			volcar(r, 0)
			android.util.Log.d(ETIQUETA, "Árbol de ${r.packageName}: $sb")
		}
		modoLectura()
		voz.senal(Senal.ERROR)
		return instrucciones + " " + textos.getString(R.string.guia_volver_menu)
	}

	/** Mientras el asistente maneja la pantalla, el lector de rescate se calla para no pisarle. */
	private fun silenciarLector() {
		servicio?.ponerModoLector(LectorRescate.Modo.APAGADO)
	}

	/** El lector de rescate vuelve a leer la pantalla (eco si hay otro lector, propio si no). */
	private fun modoLectura() {
		val s = servicio ?: return
		s.ponerModoLector(if (servicios.otroLectorHablando()) LectorRescate.Modo.ECO else LectorRescate.Modo.PROPIO)
		s.filtrarTeclas(true)
	}

	/** Vuelve al menú de rescate (o a la pantalla de la app que pidió la ayuda). */
	private fun volverAlMenu() {
		servicio?.ponerModoLector(LectorRescate.Modo.APAGADO)
		val actividad = guia.actividad
		try {
			if (actividad is ActividadRescate) {
				SesionRescate.empezar(contexto, SesionRescate.origen)
			} else {
				actividad.startActivity(Intent(actividad, actividad.javaClass).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK))
			}
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo volver a la app", e)
		}
	}

	/** Las raíces de las ventanas visibles: la activa primero, sin la propia app ni capas de accesibilidad. */
	private fun raices(): List<AccessibilityNodeInfo> {
		val s = servicio ?: return emptyList()
		val lista = mutableListOf<AccessibilityNodeInfo>()
		val propia = contexto.packageName
		runCatching { s.rootInActiveWindow }.getOrNull()?.let { if (it.packageName?.toString() != propia) lista += it }
		// Refrescar evita trabajar con una pantalla vieja de la caché de nodos
		lista.forEach { runCatching { it.refresh() } }
		val ventanas = runCatching { s.windows }.getOrNull().orEmpty()
		for (v in ventanas) {
			if (v.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
			val raiz = runCatching { v.root }.getOrNull() ?: continue
			if (raiz.packageName?.toString() == propia) continue
			if (lista.none { it == raiz }) lista += raiz
		}
		return lista
	}

	/** Espera (sondeando) a que [buscar] encuentre algo en las ventanas, o null pasado [ms]. */
	private suspend fun esperarNodo(ms: Long, buscar: (List<AccessibilityNodeInfo>) -> AccessibilityNodeInfo?): AccessibilityNodeInfo? {
		var encontrado: AccessibilityNodeInfo? = null
		withTimeoutOrNull(ms) {
			while (encontrado == null) {
				val r = raices()
				if (r.isNotEmpty()) encontrado = runCatching { buscar(r) }.getOrNull()
				if (encontrado == null) delay(SONDEO_MS)
			}
		}
		return encontrado
	}

	/** Espera a que [nodo] desaparezca de la pantalla (se abrió otra); devuelve si cambió antes de [ms]. */
	private suspend fun esperarCambio(nodo: AccessibilityNodeInfo, ms: Long = ESPERA_PANTALLA_MS): Boolean {
		val ventana = nodo.windowId
		val texto = BuscadorNodos.textoDe(nodo, 200)
		// Cambió si el elemento ya no se ve, si su ventana ya no está entre las visibles o si
		// su vista se reutilizó con otro contenido (Ajustes de Android 15 cambia de página en la
		// misma ventana y la lista recicla sus filas)
		return esperar(ms) {
			!runCatching { nodo.refresh() && nodo.isVisibleToUser }.getOrDefault(false) ||
				BuscadorNodos.textoDe(nodo, 200) != texto ||
				raices().none { it.windowId == ventana }
		}
	}

	/** Espera a que [condicion] se cumpla; devuelve si se cumplió antes de [ms]. */
	private suspend fun esperar(ms: Long, condicion: () -> Boolean): Boolean =
		withTimeoutOrNull(ms) {
			while (!runCatching(condicion).getOrDefault(false)) delay(SONDEO_MS)
			true
		} ?: false

	/** Busca [candidatos] desplazando la lista principal hasta encontrarlo (como hace una persona). */
	private suspend fun buscarDesplazando(candidatos: List<String>, exacto: Boolean = false): AccessibilityNodeInfo? {
		esperar(ESPERA_PANTALLA_MS) { raices().isNotEmpty() }
		repeat(MAXIMO_DESPLAZAMIENTOS) {
			BuscadorNodos.buscar(raices(), candidatos, soloExacto = exacto)?.let { return it }
			val lista = BuscadorNodos.desplazable(raices()) ?: return null
			if (!lista.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return null
			delay(ESPERA_DESPLAZAR_MS)
		}
		return BuscadorNodos.buscar(raices(), candidatos, soloExacto = exacto)
	}

	/**
	 * La fila del servicio [nombre] en Ajustes de accesibilidad. Si no está en la
	 * primera pantalla, se busca dentro de «Aplicaciones descargadas» o similares.
	 */
	private suspend fun buscarFilaServicio(s: ServicioBase, nombre: String): AccessibilityNodeInfo? {
		buscarDesplazando(listOf(nombre), exacto = true)?.let { return it }
		val categoria = buscarDesplazando(cadenas.para(Textos.SERVICIOS_INSTALADOS)) ?: return null
		if (!pulsar(categoria)) return null
		delay(ESPERA_CAMBIO_MS)
		return buscarDesplazando(listOf(nombre), exacto = true) ?: run {
			s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
			null
		}
	}

	/**
	 * Tras pulsar la fila de un servicio, espera a su pantalla y devuelve su
	 * interruptor principal («Usar TalkBack»). Se busca por su texto, que es
	 * fiable aunque Android cambie de página en la misma ventana y recicle la
	 * fila pulsada; si el fabricante no usa ese texto, el primer interruptor de
	 * la pantalla nueva.
	 */
	private suspend fun esperarInterruptorDe(fila: AccessibilityNodeInfo, nombre: String): AccessibilityNodeInfo? {
		esperarNodo(ESPERA_PANTALLA_MS) { r ->
			BuscadorNodos.buscar(r, cadenas.para(Textos.USAR_SERVICIO, nombre))?.let { BuscadorNodos.interruptorDe(it) }
		}?.let {
			Registro.i(ETIQUETA, "Interruptor del servicio encontrado por su nombre")
			return it
		}
		if (!esperarCambio(fila, ESPERA_CAMBIO_MS)) {
			Registro.w(ETIQUETA, "No se encuentra la pantalla del servicio tras pulsar su fila")
			return null
		}
		return esperarNodo(ESPERA_CAMBIO_MS) { r -> BuscadorNodos.primero(r) { it.isCheckable && it.isEnabled } }
	}

	/** El interruptor principal de la pantalla de un servicio («Usar TalkBack»). */
	private fun buscarInterruptor(raices: List<AccessibilityNodeInfo>, nombre: String): AccessibilityNodeInfo? {
		BuscadorNodos.buscar(raices, cadenas.para(Textos.USAR_SERVICIO, nombre))?.let { fila -> BuscadorNodos.interruptorDe(fila)?.let { return it } }
		return BuscadorNodos.primero(raices) { it.isCheckable && it.isEnabled }
	}

	/**
	 * Pulsa [nodo] (o su ancestro pulsable). Si Android no acepta la acción (la
	 * lista se redibujó y el elemento quedó viejo, o la app no la atiende), se
	 * refresca y se reintenta, y al final se toca su centro con un gesto real,
	 * como lo haría un dedo. Devuelve si se pudo pulsar.
	 */
	private suspend fun pulsar(nodo: AccessibilityNodeInfo): Boolean {
		val destino = BuscadorNodos.pulsable(nodo) ?: nodo
		var ok = runCatching { destino.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
		if (!ok && runCatching { destino.refresh() }.getOrDefault(false)) {
			delay(300)
			ok = runCatching { destino.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
		}
		if (!ok) {
			Registro.w(ETIQUETA, "Android no aceptó la pulsación por accesibilidad: se toca con un gesto")
			ok = tocarCentro(destino)
		}
		if (ok) voz.senal(Senal.ELEGIR) else Registro.w(ETIQUETA, "No se pudo pulsar el elemento")
		return ok
	}

	/** Toca el centro del elemento con un gesto (como un dedo). Devuelve si Android completó el gesto. */
	private suspend fun tocarCentro(nodo: AccessibilityNodeInfo): Boolean {
		val s = servicio ?: return false
		if (Build.VERSION.SDK_INT < 24) return false
		val zona = Rect()
		nodo.getBoundsInScreen(zona)
		if (zona.isEmpty) return false
		val camino = Path().apply { moveTo(zona.exactCenterX(), zona.exactCenterY()) }
		val gesto = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(camino, 0, 60)).build()
		val hecho = CompletableDeferred<Boolean>()
		val aceptado = runCatching {
			s.dispatchGesture(gesto, object : AccessibilityService.GestureResultCallback() {
				override fun onCompleted(g: GestureDescription?) {
					hecho.complete(true)
				}

				override fun onCancelled(g: GestureDescription?) {
					hecho.complete(false)
				}
			}, null)
		}.getOrDefault(false)
		if (!aceptado) return false
		return withTimeoutOrNull(2000) { hecho.await() } ?: false
	}

	/**
	 * Pulsa el botón de confirmar de un diálogo, si aparece: primero el botón
	 * positivo estándar (android:id/button1) y si no, por su texto.
	 */
	private suspend fun pulsarDialogo(vararg botones: Textos.Boton, excepto: AccessibilityNodeInfo? = null, espera: Long = ESPERA_DIALOGO_MS): Boolean {
		val candidatos = botones.flatMap { cadenas.para(it) }
		val boton = esperarNodo(espera) { r ->
			BuscadorNodos.porId(r, "android:id/button1")
				?: BuscadorNodos.buscar(r, candidatos, soloExacto = true, excepto = excepto)?.takeIf { BuscadorNodos.pulsable(it) != null }
		} ?: return false
		return pulsar(boton)
	}

	/**
	 * Pregunta con la voz y espera la respuesta con las teclas de volumen:
	 * arriba sí, abajo no. Sin respuesta en [ESPERA_RESPUESTA_MS], no.
	 */
	private suspend fun confirmar(pregunta: String): Boolean {
		val s = servicio ?: return false
		val respuesta = CompletableDeferred<Boolean>()
		var arriba = false
		var abajo = false
		s.oyenteTeclas = { e ->
			val pulsada = e.action == KeyEvent.ACTION_DOWN
			if (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP) arriba = pulsada else abajo = pulsada
			if (e.action == KeyEvent.ACTION_UP && !arriba && !abajo && !respuesta.isCompleted) {
				respuesta.complete(e.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
			}
			// Las dos a la vez son el atajo de accesibilidad: se dejan pasar
			!(arriba && abajo)
		}
		s.refrescarTeclas()
		Registro.i(ETIQUETA, "Esperando la confirmación con las teclas de volumen")
		// El panel de confirmación recibe las teclas aunque el filtro de teclas de accesibilidad no llegue
		s.panel.confirmar(pregunta, textos.getString(R.string.panel_si), textos.getString(R.string.panel_no)) { si ->
			if (!respuesta.isCompleted) respuesta.complete(si)
		}
		try {
			voz.senal(Senal.ATENCION)
			decir(pregunta + " " + textos.getString(R.string.guia_como_responder))
			return withTimeoutOrNull(ESPERA_RESPUESTA_MS) { respuesta.await() }?.also { Registro.i(ETIQUETA, "Respuesta: ${if (it) "sí" else "no"}") } ?: run {
				Registro.i(ETIQUETA, "Sin respuesta a tiempo")
				decir(textos.getString(R.string.guia_sin_respuesta))
				false
			}
		} finally {
			s.panel.quitarConfirmacion()
			s.oyenteTeclas = null
			s.refrescarTeclas()
		}
	}

	/** Dice [texto] con la voz de rescate y espera a que termine (con un límite por si la voz falla). */
	private suspend fun decir(texto: String) {
		if (texto.isBlank()) return
		val fin = CompletableDeferred<Unit>()
		voz.hablar(texto, vaciar = false) { fin.complete(Unit) }
		withTimeoutOrNull((3000L + texto.length * 90L).coerceAtMost(40_000L)) { fin.await() }
	}

	private fun estaActivo(componente: String): Boolean = ListaServicios.contiene(servicios.activados(), componente)

	private fun enMarcha(componente: String): Boolean = ListaServicios.contiene(servicios.enMarcha(), componente)

	private fun info(paquete: String): ApplicationInfo? = try {
		contexto.packageManager.getApplicationInfo(paquete, PackageManager.MATCH_DISABLED_COMPONENTS)
	} catch (e: PackageManager.NameNotFoundException) {
		null
	}

	private fun habilitada(paquete: String): Boolean = info(paquete)?.enabled == true

	/**
	 * Si hay shell (root o la depuración inalámbrica integrada con el permiso
	 * avanzado), hace [comando] y devuelve si [comprobacion] se cumple después
	 * (leyendo el estado real). Null si no hay shell: entonces se guía por los Ajustes.
	 */
	private suspend fun porShell(comando: String, comprobacion: () -> Boolean): Boolean? {
		if (!ShellRescate.posible(contexto)) return null
		ShellRescate.ejecutar(contexto, comando) ?: return null
		decir(textos.getString(R.string.guia_por_shell))
		return esperar(ESPERA_ESTADO_MS) { comprobacion() }
	}

	private fun detenida(paquete: String): Boolean = info(paquete)?.let { it.flags and ApplicationInfo.FLAG_STOPPED != 0 } == true

	private fun tieneActualizaciones(paquete: String): Boolean = info(paquete)?.let { it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 } == true

	companion object {
		private const val ETIQUETA = "AsistenteAjustes"
		const val ACCION_AJUSTES_TTS = "com.android.settings.TTS_SETTINGS"
		private const val SONDEO_MS = 250L
		private const val ESPERA_PANTALLA_MS = 6000L
		private const val ESPERA_DIBUJO_MS = 700L
		private const val ESPERA_DIALOGO_MS = 3500L
		private const val ESPERA_ESTADO_MS = 6000L
		private const val ESPERA_CAMBIO_MS = 1200L
		private const val ESPERA_DESPLAZAR_MS = 450L
		private const val ESPERA_DESINSTALAR_MS = 20_000L
		private const val ESPERA_INSTALADOR_MS = 15_000L
		private const val ESPERA_FIN_INSTALACION_MS = 120_000L
		private const val ESPERA_RESULTADO_MS = 4000L
		private const val ESPERA_RESPUESTA_MS = 30_000L
		private const val MAXIMO_DESPLAZAMIENTOS = 12
	}
}
