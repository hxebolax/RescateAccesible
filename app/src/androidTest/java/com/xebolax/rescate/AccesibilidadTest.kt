package com.xebolax.rescate

import android.accessibilityservice.AccessibilityService
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid
import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.BitmapImage
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Recorre las pantallas de la app con TalkBack activo, como quien desliza el
 * dedo de elemento en elemento: pone el foco de accesibilidad en cada uno
 * (TalkBack lo lee de verdad; con su registro detallado se ve qué dice), prueba
 * los deslizadores y pasa las comprobaciones del Accessibility Test Framework
 * de Google (las de Accessibility Scanner: etiquetas, tamaño de los objetivos,
 * contraste, duplicados...). Todo queda en el registro con las etiquetas
 * «Recorrido», «ATF», «Foco», «Eventos» y «Menu».
 *
 * Por defecto no falla: es un informe para revisar. Con el argumento
 * estricto=true falla si hay elementos sin nombre o errores del ATF.
 *
 * Se pasa con TalkBack encendido en el emulador (ver referencias/pruebas.md):
 *
 *     gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xebolax.rescate.AccesibilidadTest
 *     adb logcat -d -s Recorrido ATF Foco Eventos Menu
 *
 * Dependencias (androidTestImplementation): androidx.test:runner, androidx.test:core,
 * androidx.test.ext:junit y com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework.
 *
 * Adaptada a Rescate Accesible: todas las pantallas de la app y el menú de rescate.
 */
@RunWith(AndroidJUnit4::class)
class AccesibilidadTest {

	private val instrumentacion = InstrumentationRegistry.getInstrumentation()
	private lateinit var app: Context
	private lateinit var ui: UiAutomation
	private val estricto = InstrumentationRegistry.getArguments().getString("estricto") == "true"
	private val problemas = mutableListOf<String>()

	/** Pantallas que se recorren además de la de inicio: nombre para el registro y cómo abrirla. */
	private val pantallas: List<Pair<String, (Context) -> Intent>> = listOf(
		"Asistente" to { c -> Intent(c, com.xebolax.rescate.ui.ActividadAsistente::class.java) },
		"Diagnóstico" to { c -> Intent(c, com.xebolax.rescate.ui.ActividadDiagnostico::class.java) },
		"Ajustes" to { c -> Intent(c, com.xebolax.rescate.ui.ActividadAjustes::class.java) },
		"Copias" to { c -> Intent(c, com.xebolax.rescate.reparar.ActividadCopias::class.java) },
		"Permiso avanzado" to { c -> Intent(c, com.xebolax.rescate.permiso.ActividadPermiso::class.java) },
		"Pantalla para quien ve" to { c -> Intent(c, com.xebolax.rescate.ui.ActividadAyudante::class.java) },
		"Créditos" to { c -> Intent(c, com.xebolax.rescate.ui.ActividadCreditos::class.java) },
		"Manual" to { c -> Intent(c, com.xebolax.rescate.ui.ActividadManual::class.java) },
		"Motor de voz propio" to { c -> Intent(c, com.xebolax.rescate.tts.ActividadAjustesTts::class.java) },
		"Menú de rescate" to { c -> Intent(c, com.xebolax.rescate.rescate.ActividadRescate::class.java) },
	)

	@Before
	fun preparar() {
		app = instrumentacion.targetContext
		// Sin suprimir TalkBack: tiene que seguir hablando mientras se recorre
		ui = instrumentacion.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		// La app en español, como la usará el usuario (quita esta línea si la app no tiene idioma por app)
		if (Build.VERSION.SDK_INT >= 33) ui.executeShellCommand("cmd locale set-app-locales ${app.packageName} --locales es-ES").close()
		// Sin el aviso del permiso de notificaciones tapando la pantalla
		if (Build.VERSION.SDK_INT >= 33) ui.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
		esperar(1500)
		// Aquí: crear datos de muestra (elementos en las listas, un documento, un audio...)
	}

	// --- Utilidades ----------------------------------------------------------------------------

	private fun esperar(ms: Long) = Thread.sleep(ms)

	private fun inicio(): Intent = requireNotNull(app.packageManager.getLaunchIntentForPackage(app.packageName)) { "La app no tiene pantalla de inicio" }

	private fun abrir(intent: Intent, espera: Long = 4000) {
		app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
		esperar(espera)
	}

	private fun raiz(): AccessibilityNodeInfo? {
		repeat(10) {
			ui.rootInActiveWindow?.let { return it }
			esperar(300)
		}
		return null
	}

	/** Lo que diría TalkBack de un nodo: su nombre y su estado. */
	private fun texto(n: AccessibilityNodeInfo): String {
		val partes = listOfNotNull(
			n.contentDescription?.toString()?.takeIf { it.isNotBlank() } ?: n.text?.toString()?.takeIf { it.isNotBlank() },
			if (Build.VERSION.SDK_INT >= 30) n.stateDescription?.toString()?.takeIf { it.isNotBlank() } else null,
		)
		return partes.joinToString(", ")
	}

	/** Su texto o, en una fila, el de lo que tiene dentro. */
	private fun nombre(n: AccessibilityNodeInfo): String = texto(n).ifBlank {
		(0 until n.childCount).mapNotNull { n.getChild(it)?.let { h -> nombre(h) } }.filter { it.isNotBlank() }.joinToString(", ")
	}

	/** Toca el primer elemento con ese texto o descripción (como un doble toque de TalkBack). */
	private fun tocar(texto: String): Boolean {
		val nodo = elemento(texto) ?: return false
		val ok = nodo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
		esperar(2500)
		return ok
	}

	/** El elemento pulsable visible que se llama exactamente así (o, si no hay, el que lo contiene). */
	private fun elemento(texto: String): AccessibilityNodeInfo? {
		val todos = raiz()?.findAccessibilityNodeInfosByText(texto)?.filter { it.isVisibleToUser }.orEmpty()
		val n = todos.firstOrNull { (it.contentDescription ?: it.text)?.toString()?.trim() == texto } ?: todos.firstOrNull() ?: return null
		var p: AccessibilityNodeInfo? = n
		while (p != null && !p.isClickable && !p.isLongClickable) p = p.parent
		return p ?: n
	}

	/** ¿TalkBack se pararía en este elemento al deslizar? (tiene acción, o texto sin un padre con acción) */
	private fun enfocable(n: AccessibilityNodeInfo): Boolean {
		if (!n.isVisibleToUser) return false
		// TalkBack no se para en los contenedores con desplazamiento que tienen elementos dentro
		val clase = n.className?.toString().orEmpty()
		if ((n.isScrollable || clase.endsWith("ScrollView") || clase.endsWith("RecyclerView")) && n.childCount > 0) return false
		if (n.isClickable || n.isLongClickable || n.isFocusable || (Build.VERSION.SDK_INT >= 28 && n.isScreenReaderFocusable)) return true
		if (n.text.isNullOrBlank() && n.contentDescription.isNullOrBlank()) return false
		var p = n.parent
		while (p != null) {
			if (p.isClickable || p.isFocusable || (Build.VERSION.SDK_INT >= 28 && p.isScreenReaderFocusable)) return false
			p = p.parent
		}
		return true
	}

	private fun recoger(n: AccessibilityNodeInfo, lista: MutableList<AccessibilityNodeInfo>) {
		// Lo que TalkBack no visita (una vista marcada como no importante dentro de un control propio) no cuenta
		if (enfocable(n) && n.isImportantForAccessibility) lista += n
		for (i in 0 until n.childCount) n.getChild(i)?.let { recoger(it, lista) }
	}

	/** Recorre la pantalla con el foco de TalkBack y pasa las comprobaciones de accesibilidad. */
	private fun revisar(pantalla: String, maximo: Int = 45) {
		val r = raiz() ?: run { Log.w("Recorrido", "$pantalla | SIN VENTANA"); return }
		Log.i("Recorrido", "=== $pantalla ===")
		val nodos = mutableListOf<AccessibilityNodeInfo>()
		recoger(r, nodos)
		nodos.take(maximo).forEachIndexed { i, n ->
			val clase = n.className?.toString()?.substringAfterLast('.').orEmpty()
			val sinNombre = nombre(n).isBlank()
			if (sinNombre) problemas += "$pantalla: elemento ${i + 1} ($clase) sin nombre"
			Log.i("Recorrido", "$pantalla | ${i + 1} | $clase | ${if (sinNombre) "¡SIN NOMBRE!" else nombre(n)}" +
				(if (n.isClickable) " | pulsable" else "") + (if (n.actionList.size > 6) " | ${n.actionList.size} acciones" else ""))
			n.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
			esperar(700)
			// Deslizadores: deslizar arriba como con TalkBack, ver qué valor dice y volver
			if (clase == "SeekBar" && n.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }) {
				val antes = texto(n)
				n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
				esperar(900)
				n.refresh()
				Log.i("Recorrido", "$pantalla | DESLIZADOR | $antes  →  ${texto(n)}")
				n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
				esperar(600)
			}
		}
		if (nodos.size > maximo) Log.i("Recorrido", "$pantalla | ... ${nodos.size - maximo} más")
		comprobar(pantalla, r)
	}

	/**
	 * Accessibility Test Framework sobre la jerarquía y una captura (para el
	 * contraste). Falsos avisos de contraste conocidos: el recuadro de foco de
	 * TalkBack y el oscurecido de detrás de un cajón abierto.
	 */
	private fun comprobar(pantalla: String, r: AccessibilityNodeInfo) {
		runCatching {
			val jerarquia = AccessibilityHierarchyAndroid.newBuilder(r, app).build()
			val parametros = Parameters()
			ui.takeScreenshot()?.let { parametros.putScreenCapture(BitmapImage(it)) }
			val comprobaciones = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(AccessibilityCheckPreset.LATEST)
			for (c in comprobaciones) {
				for (res in c.runCheckOnHierarchy(jerarquia, null, parametros)) {
					if (res.type != AccessibilityCheckResultType.ERROR && res.type != AccessibilityCheckResultType.WARNING) continue
					val e = res.element
					val quien = listOfNotNull(e?.contentDescription?.toString(), e?.text?.toString(), e?.className?.toString()?.substringAfterLast('.'))
						.firstOrNull { it.isNotBlank() }.orEmpty()
					val linea = "$pantalla | ${res.type} | ${c.javaClass.simpleName} | $quien | ${res.getMessage(Locale.getDefault())}"
					Log.w("ATF", linea)
					if (res.type == AccessibilityCheckResultType.ERROR) problemas += linea
				}
			}
		}.onFailure { Log.e("ATF", "$pantalla | no se pudo comprobar", it) }
	}

	private fun comprobarEstricto() {
		problemas.forEach { Log.w("Recorrido", "PROBLEMA | $it") }
		if (estricto) assertTrue("Problemas de accesibilidad:\n" + problemas.joinToString("\n"), problemas.isEmpty())
	}

	// --- El recorrido ----------------------------------------------------------------------------

	@Test
	fun recorrerTodo() {
		abrir(inicio())
		revisar("Inicio")
		atras()
		revisar("Principal")
		for ((titulo, abrirla) in pantallas) {
			abrir(abrirla(app))
			revisar(titulo)
		}
		comprobarEstricto()
	}

	// --- El foco vuelve a su sitio al cerrar ventanas ------------------------------------------

	private fun conFoco(): AccessibilityNodeInfo? = raiz()?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

	private fun atras() {
		ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
		esperar(1500)
	}

	/** Pone el foco en el elemento, abre algo encima, lo cierra y mira si el foco ha vuelto al mismo sitio. */
	private fun vuelta(prueba: String, n: AccessibilityNodeInfo?, abrirlo: (AccessibilityNodeInfo) -> Unit, cerrarlo: () -> Unit) {
		if (n == null) { Log.w("Foco", "$prueba | NO ENCONTRADO"); return }
		n.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
		esperar(1000)
		val antes = nombre(n)
		abrirlo(n)
		esperar(2500)
		val encima = raiz()?.let { r -> mutableListOf<AccessibilityNodeInfo>().also { recoger(r, it) }.take(4).joinToString(" / ") { nombre(it) } }
		// Qué eventos llegan a TalkBack al cerrar (un TYPE_WINDOW_STATE_CHANGED de la pantalla de debajo repetiría su título)
		ui.setOnAccessibilityEventListener { e ->
			if (e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
				Log.i("Eventos", "$prueba | ${AccessibilityEvent.eventTypeToString(e.eventType)} | ${e.className} | ${e.text}")
		}
		cerrarlo()
		esperar(2500)
		ui.setOnAccessibilityEventListener(null)
		val despues = conFoco()
		val bien = despues != null && (despues == n || nombre(despues) == antes)
		if (!bien) problemas += "$prueba: el foco no volvió (antes: $antes, después: ${despues?.let { nombre(it) } ?: "nada"})"
		Log.i("Foco", "$prueba | ${if (bien) "BIEN" else "MAL"} | antes: $antes | encima: $encima | después: ${despues?.let { nombre(it) } ?: "nada"}")
	}

	@Test
	fun focoVuelveTrasVentanas() {
		abrir(inicio())
		atras()
		// Un diálogo de la pantalla principal (explicación del atajo) y volver: el foco tiene que quedar en la fila
		vuelta("Diálogo del atajo", raiz()?.let { r -> mutableListOf<AccessibilityNodeInfo>().also { recoger(r, it) }.firstOrNull { it.isClickable && nombre(it).contains(app.getString(R.string.ui_falta)) } },
			{ it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }, ::atras)
		comprobarEstricto()
	}
}
