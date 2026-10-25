package com.xebolax.rescate.servicio

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.xebolax.rescate.R

/**
 * Lo que el lector de rescate dice de un elemento de la pantalla: nombre, papel
 * y estado, como TalkBack («Wifi, interruptor, activado»).
 *
 * Nunca lee el contenido de los campos de contraseña. El texto solo se dice: no
 * se guarda ni se registra en ningún sitio.
 */
object DescripcionNodo {
	private const val MAXIMO = 400

	fun describir(contexto: Context, nodo: AccessibilityNodeInfo): String {
		val compat = AccessibilityNodeInfoCompat.wrap(nodo)
		val partes = mutableListOf<String>()
		val nombre = nombre(nodo)
		if (nombre.isNotBlank()) partes += nombre
		papel(contexto, nodo, compat)?.let { partes += it }
		partes += estados(contexto, nodo, compat)
		if (partes.isEmpty()) return ""
		return partes.joinToString(", ").take(MAXIMO)
	}

	/** El nombre del elemento: su descripción o su texto, o el de sus hijos si no tiene. */
	fun nombre(nodo: AccessibilityNodeInfo): String {
		if (nodo.isPassword) return nodo.contentDescription?.toString().orEmpty()
		val propio = listOfNotNull(
			nodo.contentDescription?.toString()?.takeIf { it.isNotBlank() },
			nodo.text?.toString()?.takeIf { it.isNotBlank() && nodo.contentDescription.isNullOrBlank() },
		).joinToString(" ")
		if (propio.isNotBlank()) return propio
		if (Build.VERSION.SDK_INT >= 26) nodo.hintText?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
		// Contenedor pulsable sin texto propio: el texto de sus hijos (como hace TalkBack)
		val sb = StringBuilder()
		textoHijos(nodo, sb, 0)
		return sb.toString().trim()
	}

	private fun textoHijos(nodo: AccessibilityNodeInfo, sb: StringBuilder, nivel: Int) {
		if (nivel > 4 || sb.length > MAXIMO) return
		for (i in 0 until nodo.childCount) {
			val hijo = runCatching { nodo.getChild(i) }.getOrNull() ?: continue
			if (!hijo.isVisibleToUser) continue
			// Los hijos que se pueden enfocar por sí mismos se leen aparte
			if (nivel > 0 && (hijo.isClickable || hijo.isFocusable)) continue
			val texto = if (hijo.isPassword) hijo.contentDescription else hijo.contentDescription ?: hijo.text
			if (!texto.isNullOrBlank()) sb.append(texto).append(' ')
			else textoHijos(hijo, sb, nivel + 1)
		}
	}

	private fun papel(contexto: Context, nodo: AccessibilityNodeInfo, compat: AccessibilityNodeInfoCompat): String? {
		compat.roleDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
		val clase = nodo.className?.toString().orEmpty()
		val id = when {
			clase.endsWith("Switch") || clase.endsWith("SwitchCompat") || clase.endsWith("SwitchMaterial") || clase.endsWith("MaterialSwitch") -> R.string.papel_interruptor
			clase.endsWith("ToggleButton") -> R.string.papel_interruptor
			clase.endsWith("CheckBox") || clase.endsWith("CheckedTextView") && nodo.isCheckable -> R.string.papel_casilla
			clase.endsWith("RadioButton") -> R.string.papel_opcion
			clase.endsWith("EditText") || nodo.isEditable -> if (nodo.isPassword) R.string.papel_contrasena else R.string.papel_campo
			clase.endsWith("SeekBar") || clase.endsWith("RatingBar") || clase.endsWith("Slider") -> R.string.papel_deslizador
			clase.endsWith("Spinner") -> R.string.papel_desplegable
			clase.endsWith("ImageButton") || clase.endsWith("Button") -> R.string.papel_boton
			clase.endsWith("ImageView") && !nodo.isClickable -> R.string.papel_imagen
			clase.endsWith("ProgressBar") -> R.string.papel_progreso
			clase.endsWith("TabWidget") -> R.string.papel_pestanas
			else -> null
		}
		val papel = id?.let { contexto.getString(it) }
		val encabezado = Build.VERSION.SDK_INT >= 28 && nodo.isHeading
		return when {
			encabezado && papel != null -> "$papel, ${contexto.getString(R.string.papel_encabezado)}"
			encabezado -> contexto.getString(R.string.papel_encabezado)
			else -> papel
		}
	}

	private fun estados(contexto: Context, nodo: AccessibilityNodeInfo, compat: AccessibilityNodeInfoCompat): List<String> {
		val estados = mutableListOf<String>()
		val descrito = compat.stateDescription?.toString()?.takeIf { it.isNotBlank() }
		if (descrito != null) estados += descrito
		else if (nodo.isCheckable) estados += contexto.getString(if (nodo.isChecked) R.string.estado_activado else R.string.estado_desactivado)
		if (nodo.isSelected) estados += contexto.getString(R.string.estado_seleccionado)
		if (!nodo.isEnabled) estados += contexto.getString(R.string.estado_no_disponible)
		nodo.rangeInfo?.let { rango ->
			if (descrito == null && rango.max > rango.min) {
				val porCiento = ((rango.current - rango.min) * 100f / (rango.max - rango.min)).toInt()
				estados += contexto.getString(R.string.estado_por_ciento, porCiento)
			}
		}
		return estados
	}

	/** Si el elemento es algo que el lector puede enfocar por sí mismo. */
	fun enfocable(nodo: AccessibilityNodeInfo): Boolean =
		nodo.isVisibleToUser && (nodo.isClickable || nodo.isLongClickable || nodo.isCheckable || nodo.isFocusable || nodo.isEditable ||
			nodo.isScrollable && nodo.childCount == 0 ||
			!nodo.text.isNullOrBlank() || !nodo.contentDescription.isNullOrBlank())

	/** Si se puede pulsar (y entonces sus hijos se leen dentro de él). */
	fun accionable(nodo: AccessibilityNodeInfo): Boolean =
		nodo.isClickable || nodo.isLongClickable || nodo.isCheckable || nodo.isEditable
}
