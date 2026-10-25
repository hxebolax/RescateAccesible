package com.xebolax.rescate.ui

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.xebolax.rescate.BuildConfig
import com.xebolax.rescate.R
import com.xebolax.rescate.accesibilidad.DeslizadorAjuste
import com.xebolax.rescate.accesibilidad.avisar
import com.xebolax.rescate.accesibilidad.configurarBarra
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.sistema.Servicios
import com.xebolax.rescate.voz.EspeakNativo
import com.xebolax.rescate.voz.Grabaciones

/** Ajustes de la app (androidx.preference en el almacenamiento protegido, como [Preferencias]). */
class ActividadAjustes : ActividadBase() {
	/** Se crea en onCreate: registra un permiso y no se puede crear más tarde. */
	lateinit var acciones: AccionesPreparacion
		private set

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		acciones = AccionesPreparacion(this) {}
		enableEdgeToEdge()
		val raiz = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
		val barra = MaterialToolbar(this)
		raiz.addView(barra)
		val marco = FrameLayout(this).apply { id = R.id.ajustes_contenido }
		raiz.addView(marco, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
		setContentView(raiz)
		configurarBarra(barra, getString(R.string.ui_ajustes))
		ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
			val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
			v.updatePadding(left = barras.left, top = barras.top, right = barras.right, bottom = barras.bottom)
			WindowInsetsCompat.CONSUMED
		}
		if (estado == null) supportFragmentManager.beginTransaction().replace(R.id.ajustes_contenido, Fragmento()).commit()
	}

	class Fragmento : PreferenceFragmentCompat() {
		override fun onCreatePreferences(estado: Bundle?, clave: String?) {
			// Las mismas preferencias que usa el rescate, también antes de desbloquear
			preferenceManager.setStorageDeviceProtected()
			preferenceManager.sharedPreferencesName = Preferencias.ARCHIVO
			DeslizadorAjuste.formatos[Preferencias.VELOCIDAD] = DeslizadorAjuste.porCiento
			DeslizadorAjuste.formatos[Preferencias.TONO] = DeslizadorAjuste.porCiento
			DeslizadorAjuste.formatos[Preferencias.VOLUMEN_MINIMO] = DeslizadorAjuste.porCiento
			DeslizadorAjuste.formatos[Preferencias.ESPERA_SEGURO] = DeslizadorAjuste.segundos
			setPreferencesFromResource(R.xml.ajustes, clave)
			val c = requireContext()

			findPreference<ListPreference>(Preferencias.IDIOMA)?.setOnPreferenceChangeListener { _, valor ->
				Idioma.cambiar(c, valor as String)
				activity?.recreate()
				true
			}

			findPreference<ListPreference>(Preferencias.MOTOR_RESCATE)?.let { lista ->
				val nombres = mutableListOf(getString(R.string.aj_motor_pico))
				val valores = mutableListOf(Preferencias.MOTOR_PICO)
				if (EspeakNativo.disponible()) {
					nombres += getString(R.string.aj_motor_espeak)
					valores += Preferencias.MOTOR_ESPEAK
				}
				Servicios(c).motores().filter { it.paquete != c.packageName }.forEach {
					nombres += getString(R.string.aj_motor_sistema, it.nombre)
					valores += it.paquete
				}
				lista.entries = nombres.toTypedArray()
				lista.entryValues = valores.toTypedArray()
			}

			// La dirección solo se pide (y solo se ve) con un servidor personalizado
			val prefs = Preferencias(c)
			val url = findPreference<EditTextPreference>(Preferencias.URL_ACTUALIZACIONES)?.also {
				if (it.text.isNullOrBlank()) it.text = BuildConfig.URL_ACTUALIZACIONES
				it.isVisible = prefs.origenActualizaciones == Preferencias.ORIGEN_PERSONALIZADO
				it.setOnPreferenceChangeListener { _, valor ->
					val texto = (valor as String).trim()
					val valida = texto.startsWith("https://") || texto.startsWith("http://")
					if (!valida) activity?.avisar(getString(R.string.aj_servidor_no_valido))
					valida
				}
			}
			findPreference<ListPreference>(Preferencias.ORIGEN_ACTUALIZACIONES)?.let { lista ->
				lista.value = prefs.origenActualizaciones
				lista.setOnPreferenceChangeListener { _, valor ->
					url?.isVisible = valor == Preferencias.ORIGEN_PERSONALIZADO
					true
				}
			}

			findPreference<Preference>("probar_voz")?.setOnPreferenceClickListener {
				(activity as? ActividadAjustes)?.acciones?.probarVozRescate()
				true
			}
			findPreference<Preference>("regrabar")?.setOnPreferenceClickListener {
				Grabaciones(c).borrarTodas()
				Preferencias(c).grabacionesHechas = ""
				(activity as? ActividadAjustes)?.let { a ->
					a.acciones.grabarRespaldo()
					a.avisar(getString(R.string.aj_regrabando))
				}
				true
			}
			findPreference<Preference>("informe_pruebas")?.setOnPreferenceClickListener {
				startActivity(android.content.Intent(c, ActividadManual::class.java).putExtra(ActividadManual.EXTRA_INFORME, true))
				true
			}
			findPreference<Preference>("borrar_registro")?.setOnPreferenceClickListener {
				Registro.borrar()
				activity?.avisar(getString(R.string.aj_registro_borrado))
				true
			}
		}
	}
}
