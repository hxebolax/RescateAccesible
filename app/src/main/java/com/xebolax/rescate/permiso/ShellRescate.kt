package com.xebolax.rescate.permiso

import android.content.Context
import com.xebolax.rescate.nucleo.Registro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Órdenes de shell para reparar lo que una app normal no puede: audio mono y
 * balance, habilitar apps, quitar actualizaciones, borrar cachés, forzar la
 * detención. Primero con root (si el móvil lo tiene y lo permite) y si no con
 * la depuración inalámbrica integrada, que con el permiso avanzado se enciende
 * y se apaga sola. Si nada de eso está disponible, devuelve null y quien llama
 * guía por los Ajustes.
 */
object ShellRescate {
	private const val ETIQUETA = "ShellRescate"
	private const val ESPERA_ROOT_MS = 15_000L

	/** Si hay alguna forma de ejecutar órdenes de shell sin el usuario. */
	fun posible(contexto: Context): Boolean =
		ConcesionPermiso.pareceHaberRoot() || (AdbInalambrico.vinculada(contexto) && ConcesionPermiso.concedido(contexto))

	/** Ejecuta [comando] y devuelve su salida, o null si no se pudo. */
	suspend fun ejecutar(contexto: Context, comando: String): String? {
		if (ConcesionPermiso.pareceHaberRoot()) {
			conRoot(comando)?.let { return it }
		}
		return AdbInalambrico(contexto).shell(comando)
	}

	private suspend fun conRoot(comando: String): String? = withContext(Dispatchers.IO) {
		try {
			withTimeoutOrNull(ESPERA_ROOT_MS) {
				val proceso = ProcessBuilder("su", "-c", comando).redirectErrorStream(true).start()
				val salida = proceso.inputStream.bufferedReader().readText().trim()
				if (proceso.waitFor() == 0) salida else null
			}
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo usar root", e)
			null
		}
	}
}
