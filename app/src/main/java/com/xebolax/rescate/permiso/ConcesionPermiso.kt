package com.xebolax.rescate.permiso

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.sistema.Paquetes
import com.xebolax.rescate.sistema.Servicios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Cómo terminó un intento de conceder el permiso avanzado. */
enum class ResultadoConcesion {
	/** Concedido y comprobado con checkSelfPermission. */
	CONCEDIDO,
	/** El móvil no tiene root (no hay su). */
	SIN_ROOT,
	/** Se pidió root y el usuario o el gestor de root lo negaron. */
	ROOT_DENEGADO,
	/** No respondió a tiempo. */
	TIEMPO_AGOTADO,
	/** El comando terminó pero el permiso no quedó concedido. */
	FALLO,
}

/**
 * El «permiso avanzado» (WRITE_SECURE_SETTINGS): qué comando lo concede, si
 * ya está concedido y cómo concederlo con root.
 *
 * Android no deja que una app se lo conceda sola: lo concede el shell de
 * depuración (adb, desde un ordenador o desde el propio móvil con la
 * depuración inalámbrica, ver [AdbInalambrico]) o root. Se concede una vez y
 * se conserva al reiniciar y al actualizar la app; se pierde al desinstalarla
 * o con `pm revoke`.
 */
object ConcesionPermiso {
	const val PERMISO = "android.permission.WRITE_SECURE_SETTINGS"
	private const val ETIQUETA = "ConcesionPermiso"
	private const val ESPERA_ROOT_S = 30L

	/** Si el permiso ya está concedido a esta app. */
	fun concedido(contexto: Context): Boolean = Servicios.puedeEscribir(contexto)

	/** El comando del shell que concede el permiso a esta app (con su paquete real, también el de pruebas). */
	fun comandoShell(contexto: Context): String = "pm grant ${contexto.packageName} $PERMISO"

	/** El comando para escribir en un ordenador con adb. */
	fun comandoAdb(contexto: Context): String = "adb shell ${comandoShell(contexto)}"

	/** El comando que lo quita (por si el usuario quiere revocarlo). */
	fun comandoRevocar(contexto: Context): String = "adb shell pm revoke ${contexto.packageName} $PERMISO"

	/** Si este Android tiene depuración inalámbrica (Android 11 o posterior). */
	fun hayDepuracionInalambrica(): Boolean = Build.VERSION.SDK_INT >= 30

	/** Si las opciones de desarrollador están activadas (null si no se puede saber). */
	fun opcionesDesarrolladorActivas(contexto: Context): Boolean? = try {
		Settings.Global.getInt(contexto.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
	} catch (e: Exception) {
		Registro.w(ETIQUETA, "No se pudo leer si las opciones de desarrollador están activas", e)
		null
	}

	/** Si la depuración inalámbrica está encendida (null si Android no deja leerlo). */
	fun depuracionInalambricaActiva(contexto: Context): Boolean? = try {
		Settings.Global.getInt(contexto.contentResolver, CLAVE_ADB_WIFI, 0) == 1
	} catch (e: Exception) {
		Registro.w(ETIQUETA, "No se pudo leer si la depuración inalámbrica está activa", e)
		null
	}

	/**
	 * Apaga la depuración inalámbrica (ya no hace falta una vez concedido el
	 * permiso). Necesita el permiso avanzado. Devuelve si quedó apagada,
	 * comprobado leyendo el ajuste.
	 */
	fun apagarDepuracionInalambrica(contexto: Context): Boolean {
		if (!concedido(contexto)) return false
		return try {
			Settings.Global.putInt(contexto.contentResolver, CLAVE_ADB_WIFI, 0)
			depuracionInalambricaActiva(contexto) == false
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo apagar la depuración inalámbrica", e)
			false
		}
	}

	/** Si Shizuku está instalado (solo se detecta: el comando se ejecuta desde su shell). */
	fun hayShizuku(contexto: Context): Boolean = try {
		contexto.packageManager.getPackageInfo(Paquetes.SHIZUKU, 0)
		true
	} catch (e: PackageManager.NameNotFoundException) {
		false
	}

	/** Si parece haber root: un ejecutable su en las rutas habituales. */
	fun pareceHaberRoot(): Boolean =
		listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/debug_ramdisk/su", "/system_ext/bin/su")
			.any { runCatching { File(it).exists() }.getOrDefault(false) }

	/**
	 * Concede el permiso con root (`su -c pm grant …`). El gestor de root suele
	 * preguntar en pantalla; se espera como mucho [ESPERA_ROOT_S] segundos. No
	 * bloquea el hilo principal.
	 */
	suspend fun concederConRoot(contexto: Context): ResultadoConcesion = withContext(Dispatchers.IO) {
		if (concedido(contexto)) return@withContext ResultadoConcesion.CONCEDIDO
		val proceso = try {
			Runtime.getRuntime().exec(arrayOf("su", "-c", comandoShell(contexto)))
		} catch (e: java.io.IOException) {
			// Sin su en el PATH: no hay root
			Registro.w(ETIQUETA, "No hay su para conceder el permiso con root", e)
			return@withContext ResultadoConcesion.SIN_ROOT
		}
		try {
			val termino = esperar(proceso, ESPERA_ROOT_S)
			if (!termino) {
				proceso.destroy()
				return@withContext ResultadoConcesion.TIEMPO_AGOTADO
			}
			val codigo = proceso.exitValue()
			val errores = runCatching { proceso.errorStream.bufferedReader().readText().trim() }.getOrDefault("")
			if (errores.isNotEmpty()) Registro.w(ETIQUETA, "su terminó con el código $codigo: $errores")
			when {
				concedido(contexto) -> ResultadoConcesion.CONCEDIDO
				codigo != 0 -> ResultadoConcesion.ROOT_DENEGADO
				else -> ResultadoConcesion.FALLO
			}
		} catch (e: InterruptedException) {
			proceso.destroy()
			ResultadoConcesion.TIEMPO_AGOTADO
		} finally {
			runCatching { proceso.inputStream.close() }
			runCatching { proceso.errorStream.close() }
			runCatching { proceso.outputStream.close() }
		}
	}

	/** Espera a que termine [proceso] como mucho [segundos] (waitFor con tiempo solo existe desde Android 8). */
	private fun esperar(proceso: Process, segundos: Long): Boolean {
		if (Build.VERSION.SDK_INT >= 26) return proceso.waitFor(segundos, TimeUnit.SECONDS)
		val limite = System.currentTimeMillis() + segundos * 1000
		while (System.currentTimeMillis() < limite) {
			try {
				proceso.exitValue()
				return true
			} catch (e: IllegalThreadStateException) {
				// Sigue en marcha
				Thread.sleep(200)
			}
		}
		return false
	}

	/** Settings.Global.ADB_WIFI_ENABLED, oculta en el SDK. */
	const val CLAVE_ADB_WIFI = "adb_wifi_enabled"
}
