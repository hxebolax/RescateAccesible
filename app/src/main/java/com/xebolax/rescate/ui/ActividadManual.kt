package com.xebolax.rescate.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.xebolax.rescate.R
import com.xebolax.rescate.accesibilidad.configurarBarra
import com.xebolax.rescate.nucleo.Idioma

/**
 * El manual de uso (o, con [EXTRA_INFORME], el informe de pruebas y simulaciones de averías), en HTML con encabezados (assets/manual-es.html o
 * manual-en.html): TalkBack lo recorre por encabezados y enlaces. Sin Internet.
 */
class ActividadManual : ActividadBase() {
	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		enableEdgeToEdge()
		val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		val barra = MaterialToolbar(this)
		raiz.addView(barra)
		val web = WebView(this).apply {
			webViewClient = WebViewClient()
			settings.javaScriptEnabled = false
			settings.allowFileAccess = false
			settings.textZoom = 100
		}
		raiz.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
		setContentView(raiz)
		val informe = intent.getBooleanExtra(EXTRA_INFORME, false)
		configurarBarra(barra, getString(if (informe) R.string.aj_informe else R.string.ui_manual))
		ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
			val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			v.updatePadding(left = barras.left, top = barras.top, right = barras.right, bottom = barras.bottom)
			WindowInsetsCompat.CONSUMED
		}
		val idioma = if (Idioma.efectivo(this).language == "en") "en" else "es"
		web.loadUrl(if (informe) "file:///android_asset/informe-pruebas.html" else "file:///android_asset/manual-$idioma.html")
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (web.canGoBack()) web.goBack() else finish()
			}
		})
	}

	companion object {
		/** Abrir el informe de pruebas y simulaciones en lugar del manual. */
		const val EXTRA_INFORME = "informe"
	}
}
