package com.xebolax.rescate.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import com.xebolax.rescate.BuildConfig
import com.xebolax.rescate.R
import com.xebolax.rescate.accesibilidad.mensaje
import com.xebolax.rescate.actualizaciones.GestorActualizaciones
import com.xebolax.rescate.nucleo.Registro
import java.io.File

/**
 * Acerca de y actualizaciones (se abre desde el menú lateral): la versión,
 * buscar actualizaciones y compartir el registro de fallos, y después los
 * créditos y licencias. Rescate Accesible se apoya en el trabajo de otros y lo
 * dice claro: Shortcut Menu de aaron-gh (la idea y el código del menú de atajos,
 * el modo seguro y la voz Pico), SVOX Pico de AOSP, eSpeak NG y las bibliotecas
 * de AndroidX y Material. Cada licencia se puede leer entera.
 */
class ActividadCreditos : ActividadBase() {
	/** Se crea en onCreate: registra el permiso de instalar y no se puede crear más tarde. */
	private lateinit var actualizaciones: GestorActualizaciones

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		actualizaciones = GestorActualizaciones(this)
		val p = pantalla(getString(R.string.ui_acerca))
		p.parrafo(getString(R.string.cred_intro, BuildConfig.VERSION_NAME))
		p.boton(getString(R.string.ui_buscar_actualizaciones)) { actualizaciones.comprobarAMano() }
		p.parrafo(getString(R.string.ui_origen_en_ajustes))
		p.botonSecundario(getString(R.string.ui_compartir_registro)) { compartirRegistro() }

		p.encabezado(getString(R.string.ui_creditos))

		p.encabezado(getString(R.string.cred_shortcut_titulo))
		p.parrafo(getString(R.string.cred_shortcut))
		p.botonSecundario(getString(R.string.cred_ver_licencia, "Apache 2.0")) { licencia("licencias/shortcut-menu-LICENSE.txt", "Shortcut Menu") }
		p.botonSecundario(getString(R.string.cred_abrir_web, "Shortcut Menu")) { web("https://github.com/aaron-gh/shortcut-menu") }

		p.encabezado(getString(R.string.cred_pico_titulo))
		p.parrafo(getString(R.string.cred_pico))
		p.botonSecundario(getString(R.string.cred_ver_licencia, "SVOX Pico")) { licencia("licencias/pico-NOTICE.txt", "SVOX Pico") }

		if (BuildConfig.INCLUIR_ESPEAK) {
			p.encabezado(getString(R.string.cred_espeak_titulo))
			p.parrafo(getString(R.string.cred_espeak))
			p.botonSecundario(getString(R.string.cred_ver_licencia, "GPL 3")) { licencia("licencias/GPL-3.0.txt", "eSpeak NG") }
			p.botonSecundario(getString(R.string.cred_abrir_web, "eSpeak NG")) { web("https://github.com/espeak-ng/espeak-ng") }
		}

		p.encabezado(getString(R.string.cred_bibliotecas_titulo))
		p.parrafo(getString(R.string.cred_bibliotecas))
		p.botonSecundario(getString(R.string.cred_ver_licencia, "Apache 2.0")) { licencia("licencias/Apache-2.0.txt", "Apache 2.0") }
		p.botonSecundario(getString(R.string.cred_ver_otras)) { licencia("licencias/TERCEROS.txt", getString(R.string.cred_ver_otras)) }

		p.encabezado(getString(R.string.cred_app_titulo))
		p.parrafo(getString(if (BuildConfig.INCLUIR_ESPEAK) R.string.cred_app_gpl else R.string.cred_app_apache))
	}

	/** Comparte el registro técnico (sin datos privados) para enviar un informe. */
	private fun compartirRegistro() {
		val texto = Registro.cabecera(BuildConfig.VERSION_NAME) + Registro.texto()
		if (texto.isBlank()) {
			mensaje(getString(R.string.ui_compartir_registro), getString(R.string.ui_registro_vacio))
			return
		}
		try {
			val archivo = File(File(cacheDir, "compartir").apply { mkdirs() }, "registro-rescate.txt")
			archivo.writeText(texto)
			val uri = FileProvider.getUriForFile(this, "$packageName.archivos", archivo)
			val intent = Intent(Intent.ACTION_SEND)
				.setType("text/plain")
				.putExtra(Intent.EXTRA_STREAM, uri)
				.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ui_registro_asunto))
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			startActivity(Intent.createChooser(intent, getString(R.string.ui_compartir_registro)))
		} catch (e: Exception) {
			Registro.e("Creditos", "No se pudo compartir el registro", e)
			mensaje(getString(R.string.ui_compartir_registro), getString(R.string.ui_no_se_abre))
		}
	}

	private fun licencia(ruta: String, titulo: String) {
		val texto = try {
			assets.open(ruta).bufferedReader().use { it.readText() }
		} catch (e: Exception) {
			Registro.w("Creditos", "No se encuentra $ruta", e)
			getString(R.string.cred_no_encontrada)
		}
		mensaje(titulo, texto)
	}

	private fun web(url: String) {
		try {
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
		} catch (e: Exception) {
			mensaje(url, getString(R.string.ui_no_se_abre))
		}
	}
}
