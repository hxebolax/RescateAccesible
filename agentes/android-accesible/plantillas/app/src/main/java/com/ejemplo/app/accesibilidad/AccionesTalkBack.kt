package com.ejemplo.app.accesibilidad

import android.content.Context
import android.view.View
import com.ejemplo.app.R

/**
 * Acciones de TalkBack personalizables: en cada zona de la app (elementos,
 * carpetas, mensajes, canciones...) el usuario elige qué acciones salen al
 * deslizar arriba o abajo y en qué orden (Ajustes, Acciones de TalkBack).
 * Lo que no se elige sigue en el menú de la fila (doble toque mantenido o
 * pulsación larga): solo se quita de las acciones rápidas.
 *
 * Las acciones se reconocen por su texto: cada entrada del catálogo dice con
 * qué textos aparece (por ejemplo «Añadir a favoritos» y «Quitar de
 * favoritos» son la misma entrada, porque la acción cambia según el estado).
 * Una acción que no está en el catálogo (un enlace, una opción que depende
 * del contenido) sale siempre, al final.
 *
 * Cómo se usa en una lista nueva:
 *  1. Una zona nueva en ZonaAcciones, con su título en strings.
 *  2. Sus entradas en entradas(): e(clave, textos...) si sale por defecto,
 *     o(clave, textos...) si solo sale cuando el usuario la añade. Todas las
 *     variantes de texto de una acción van en la MISMA entrada.
 *  3. En el bind de la fila: fila.accionesAccesibles(acciones, ZonaAcciones.X),
 *     con las acciones ya decididas según el estado («Quitar de favoritos» si
 *     ya lo está; nunca ofrecer algo que ya está hecho).
 *  4. La pulsación larga abre elegir(titulo, acciones) con TODAS las acciones.
 *
 * Las claves se guardan; no las cambies una vez publicadas (perderías lo que
 * eligió el usuario). Las zonas y textos de aquí son de ejemplo.
 */
enum class ZonaAcciones(val titulo: Int) {
	ELEMENTO(R.string.zona_elemento),
	CARPETA(R.string.zona_carpeta),
}

/** Una acción del catálogo: clave guardada, textos con los que aparece, si sale sin tocar nada y su nombre en Ajustes. */
class EntradaAccion(val clave: String, val textos: List<Int>, val porDefecto: Boolean = true, val nombre: String? = null)

object AccionesTalkBack {

	private const val ARCHIVO = "acciones_talkback"

	private fun e(clave: String, vararg textos: Int) = EntradaAccion(clave, textos.toList())
	/** Entrada que no sale si el usuario no la añade (para no cambiar lo que había al añadir acciones nuevas). */
	private fun o(clave: String, vararg textos: Int) = EntradaAccion(clave, textos.toList(), porDefecto = false)

	/** El catálogo de cada zona, en el orden por defecto. */
	@Suppress("UNUSED_PARAMETER")
	fun entradas(c: Context, zona: ZonaAcciones): List<EntradaAccion> = when (zona) {
		ZonaAcciones.ELEMENTO -> listOf(
			e("abrir", R.string.accion_abrir),
			e("favorito", R.string.accion_anadir_favoritos, R.string.accion_quitar_favoritos),
			e("compartir", R.string.accion_compartir),
			e("borrar", R.string.accion_borrar_x),
			o("orden", R.string.accion_subir, R.string.accion_bajar),
		)
		ZonaAcciones.CARPETA -> listOf(
			e("abrir", R.string.accion_abrir),
			e("renombrar", R.string.accion_renombrar),
			e("borrar", R.string.accion_borrar_x),
			o("orden", R.string.accion_subir, R.string.accion_bajar),
		)
	}

	/** Cómo se llama la entrada en Ajustes: su nombre o sus textos («Añadir a favoritos / Quitar de favoritos»). */
	fun nombre(c: Context, e: EntradaAccion): String =
		e.nombre ?: e.textos.map { plantilla(c, it).replace(PARAMETRO, "…") }.distinct().joinToString(" / ")

	private fun preferencias(c: Context) = c.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

	private fun clavePreferencia(zona: ZonaAcciones) = "zona_${zona.name.lowercase()}"

	/** Las claves elegidas en esa zona, en su orden (o las de por defecto si el usuario no ha tocado nada). */
	fun elegidas(c: Context, zona: ZonaAcciones): List<String> {
		preferencias(c).getString(clavePreferencia(zona), null)?.let { guardado -> return guardado.split(',').filter { it.isNotBlank() } }
		return entradas(c, zona).filter { it.porDefecto }.map { it.clave }
	}

	fun guardar(c: Context, zona: ZonaAcciones, claves: List<String>) {
		preferencias(c).edit().putString(clavePreferencia(zona), claves.joinToString(",")).apply()
	}

	fun restablecer(c: Context, zona: ZonaAcciones) {
		preferencias(c).edit().remove(clavePreferencia(zona)).apply()
	}

	private val PARAMETRO = Regex("""%(\d+\$)?[sd]""")

	/** El texto de la cadena sin rellenar («Borrar %1$s»). */
	private fun plantilla(c: Context, id: Int): String = c.resources.getText(id).toString()

	/** Caché por idioma y zona. Se usa desde el hilo principal (bind de las filas). */
	private val reconocedores = HashMap<String, List<Pair<Regex, String>>>()

	/** Para reconocer de qué entrada es cada texto (las cadenas con datos, por su parte fija). */
	private fun reconocedor(c: Context, zona: ZonaAcciones): List<Pair<Regex, String>> {
		val idioma = c.resources.configuration.locales[0].toLanguageTag()
		return reconocedores.getOrPut("$idioma/${zona.name}") {
			entradas(c, zona).flatMap { e ->
				e.textos.map { id ->
					val partes = plantilla(c, id).split(PARAMETRO).map { Regex.escape(it) }
					Regex(partes.joinToString(".*"), RegexOption.DOT_MATCHES_ALL) to e.clave
				}
			}
		}
	}

	fun claveDe(c: Context, zona: ZonaAcciones, texto: String): String? =
		reconocedor(c, zona).firstOrNull { it.first.matches(texto) }?.second

	/** Deja solo las acciones elegidas en esa zona, en el orden elegido; las desconocidas, al final. */
	fun aplicar(c: Context, zona: ZonaAcciones, acciones: List<Accion>): List<Accion> {
		val orden = elegidas(c, zona)
		val elegidas = ArrayList<Pair<Int, Accion>>()
		val otras = ArrayList<Accion>()
		for (a in acciones) {
			val clave = claveDe(c, zona, a.first)
			if (clave == null) otras += a
			else orden.indexOf(clave).takeIf { it >= 0 }?.let { elegidas += it to a }
		}
		// sortedBy es estable: las de una misma entrada (subir y bajar) siguen en su orden
		return elegidas.sortedBy { it.first }.map { it.second } + otras
	}
}

/** Pone las acciones de TalkBack de una fila según lo que el usuario eligió para esa zona (null: todas, sin filtrar). */
fun View.accionesAccesibles(acciones: List<Accion>, zona: ZonaAcciones?) =
	accionesAccesibles(if (zona == null) acciones else AccionesTalkBack.aplicar(context, zona, acciones))
