package com.xebolax.rescate.diagnostico

import android.app.Activity
import android.content.Intent
import com.xebolax.rescate.voz.VozEmergencia

/** Lo grave que es un hallazgo del diagnóstico. */
enum class Gravedad {
	/** Impide oír el móvil: se arregla primero. */
	GRAVE,
	/** Puede causar problemas. */
	AVISO,
	/** Para saber, sin problema. */
	INFO,
	/** Comprobado y bien. */
	BIEN,
}

/**
 * Lo que necesita una reparación para hacerse: la pantalla desde la que se
 * pide, la voz de rescate y cómo abrir pantallas de fuera (Ajustes) sin perder
 * el rescate. Lo implementan el menú de rescate y la pantalla de diagnóstico.
 */
interface Guia {
	val actividad: Activity
	val vozRescate: VozEmergencia?

	/** Abre una pantalla de fuera explicándola; devuelve si se abrió. */
	fun abrirFuera(intent: Intent, explicacion: String): Boolean
}

/**
 * Una forma de arreglar un hallazgo. [hacer] devuelve el texto del resultado,
 * que se dice o se muestra (nunca afirma un cambio que no se comprobó).
 */
class Reparacion(
	val texto: String,
	val ayuda: String = "",
	/** Necesita el permiso avanzado (WRITE_SECURE_SETTINGS). */
	val necesitaPermiso: Boolean = false,
	val hacer: suspend (Guia) -> String?,
)

/** Un hallazgo del diagnóstico: qué pasa, por qué importa y cómo se arregla. */
class Hallazgo(
	/** Identificador estable («volumen_accesibilidad_cero»). */
	val id: String,
	val gravedad: Gravedad,
	/** Frase corta para el menú («El volumen de accesibilidad está a cero»). */
	val titulo: String,
	/** Explicación completa en lenguaje claro. */
	val detalle: String,
	val reparaciones: List<Reparacion> = emptyList(),
)
