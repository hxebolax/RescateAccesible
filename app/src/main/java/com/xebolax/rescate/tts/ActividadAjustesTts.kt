package com.xebolax.rescate.tts

import com.xebolax.rescate.accesibilidad.recorribleConLector
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.xebolax.rescate.R
import com.xebolax.rescate.accesibilidad.configurarBarra
import com.xebolax.rescate.accesibilidad.dp
import com.xebolax.rescate.accesibilidad.encabezado
import com.xebolax.rescate.accesibilidad.mensaje
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.reparar.Tiendas
import com.xebolax.rescate.voz.VozPico

/**
 * La pantalla del motor de voz propio (la que abre «Ajustes» del motor en los
 * ajustes de síntesis de voz del sistema): qué voces tiene, probarlo y cómo
 * elegirlo como motor preferido.
 */
class ActividadAjustesTts : AppCompatActivity() {
	private var prueba: TextToSpeech? = null

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(Idioma.envolver(base))
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		val m = dp(this, 16)
		val barra = MaterialToolbar(this)
		val contenido = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(m, m, m, m)
		}
		val raiz = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			addView(barra, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
			addView(ScrollView(this@ActividadAjustesTts).recorribleConLector().apply {
				addView(contenido)
			}, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
		}
		ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
			val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
			WindowInsetsCompat.CONSUMED
		}
		setContentView(raiz)
		configurarBarra(barra, getString(R.string.tts_titulo), conArriba = true)

		contenido.addView(texto(getString(R.string.tts_explicacion)))
		contenido.addView(texto(getString(R.string.tts_voces_titulo)).apply { encabezado() })
		val nombres = VozPico.entries.joinToString("\n") { it.locale.getDisplayName(Idioma.efectivo(this@ActividadAjustesTts)) }
		contenido.addView(texto(nombres))
		contenido.addView(boton(getString(R.string.tts_probar)) { probar() })
		contenido.addView(boton(getString(R.string.tts_abrir_ajustes)) { abrirAjustesVoz() })
		contenido.addView(texto(getString(R.string.tts_como_elegir)))

		if (intent?.getBooleanExtra(EXTRA_INSTALAR, false) == true) {
			mensaje(getString(R.string.tts_titulo), getString(R.string.tts_nada_que_instalar))
		}
	}

	private fun texto(t: String) = TextView(this).apply {
		text = t
		setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
		setPadding(0, dp(this@ActividadAjustesTts, 8), 0, dp(this@ActividadAjustesTts, 8))
	}

	private fun boton(t: String, accion: () -> Unit) = MaterialButton(this).apply {
		text = t
		minHeight = dp(this@ActividadAjustesTts, 56)
		setOnClickListener { accion() }
	}

	/** Dice una frase con este mismo motor, a través del sistema (como lo usaría TalkBack). */
	private fun probar() {
		prueba?.shutdown()
		prueba = TextToSpeech(this, TextToSpeech.OnInitListener { estado ->
			val t = prueba ?: return@OnInitListener
			if (estado == TextToSpeech.SUCCESS) {
				t.language = Idioma.efectivo(this)
				t.speak(getString(R.string.tts_frase_prueba), TextToSpeech.QUEUE_FLUSH, null, "prueba")
			} else {
				mensaje(getString(R.string.tts_titulo), getString(R.string.tts_prueba_fallo))
			}
		}, packageName)
	}

	private fun abrirAjustesVoz() {
		val abierto = Tiendas.abrirPrimero(this, listOf(Intent("com.android.settings.TTS_SETTINGS"), Intent(android.provider.Settings.ACTION_SETTINGS)))
		if (!abierto) Registro.w("AjustesTts", "No se pudieron abrir los ajustes de voz")
	}

	override fun onDestroy() {
		prueba?.shutdown()
		prueba = null
		super.onDestroy()
	}

	companion object {
		const val EXTRA_INSTALAR = "com.xebolax.rescate.INSTALAR"
	}
}
