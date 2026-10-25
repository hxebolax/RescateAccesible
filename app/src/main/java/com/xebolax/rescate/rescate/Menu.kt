package com.xebolax.rescate.rescate

/**
 * Una opción del menú de rescate.
 *
 * [texto] es lo que se dice al pasar por ella (corto, empieza por lo que hace);
 * [ayuda], lo que se dice si el dedo se queda quieto encima o se pide la ayuda;
 * [anuncio], lo que se dice al elegirla antes de hacerla (si hace falta).
 */
class Opcion(
	val texto: String,
	val ayuda: String = "",
	val anuncio: String? = null,
	val accion: (ActividadRescate) -> Unit,
)

/**
 * Una página del menú: título, frase de entrada y opciones. Las opciones se
 * construyen cada vez que se muestra la página, así siempre dicen el estado
 * actual («TalkBack, activado»).
 */
class Pagina(
	val titulo: String,
	val intro: String,
	val opciones: (ActividadRescate) -> List<Opcion>,
	/** La principal no tiene «Volver»: tiene «Salir del rescate». */
	val principal: Boolean = false,
)
