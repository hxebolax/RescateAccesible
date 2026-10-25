package com.xebolax.rescate.rescate

import android.content.Context
import android.content.res.Configuration
import com.xebolax.rescate.R
import com.xebolax.rescate.nucleo.Idioma
import java.util.Locale

/**
 * Los textos fijos del rescate que se graban como respaldo ([com.xebolax.rescate.voz.Grabaciones]):
 * la presentación, las instrucciones de manejo, las opciones del menú principal y
 * los avisos del guardián. Si un día no funciona ninguna síntesis, el rescate aún
 * puede decir esto.
 */
object TextosEsenciales {
	private val IDS = intArrayOf(
		R.string.rescate_presentacion,
		R.string.rescate_manejo_dedo,
		R.string.rescate_manejo_lector,
		R.string.rescate_manejo_teclas,
		R.string.rescate_salir,
		R.string.rescate_saliendo,
		R.string.rescate_volver,
		R.string.rescate_un_momento,
		R.string.rescate_volver_con_teclas,
		R.string.rescate_seguro,
		R.string.rescate_eco_activado,
		R.string.rescate_error_general,
		R.string.pag_principal_intro,
		R.string.pag_diagnostico,
		R.string.pag_lectores,
		R.string.pag_voz,
		R.string.pag_sonido,
		R.string.pag_reparar,
		R.string.pag_leer_pantalla,
		R.string.pag_restaurar,
		R.string.pag_ajustes_accesibilidad,
		R.string.pag_ayudante,
		R.string.pag_ajustes_rescate,
		R.string.pag_ayuda,
		R.string.pag_modo_seguro,
		R.string.voz_usar_propia,
		R.string.guardian_lector_apagado,
		R.string.guardian_lector_caido,
		R.string.guardian_voz_rota,
		R.string.app_name,
	)

	/** Los textos en el idioma de la app. */
	fun lista(contexto: Context): List<String> = lista(contexto, Idioma.efectivo(contexto))

	/** Los textos en [locale] (para grabar los juegos de español y de inglés). */
	fun lista(contexto: Context, locale: Locale): List<String> {
		val configuracion = Configuration(contexto.resources.configuration)
		configuracion.setLocale(locale)
		val c = contexto.createConfigurationContext(configuracion)
		return IDS.map { c.getString(it) }
	}

	/** Los dos idiomas que se graban: español de España e inglés (el del sistema si es inglés; si no, británico). */
	fun idiomas(): List<Locale> {
		val sistema = Locale.getDefault()
		val ingles = if (sistema.language == "en") sistema else Locale.UK
		return listOf(Locale("es", "ES"), ingles)
	}
}
