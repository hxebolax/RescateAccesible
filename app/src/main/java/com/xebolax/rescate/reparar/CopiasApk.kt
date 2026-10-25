package com.xebolax.rescate.reparar

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.sistema.Paquetes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un archivo APK guardado en una copia (el base o una parte dividida). */
data class ArchivoCopia(val nombre: String, val tamano: Long, val sha256: String)

/** Una copia de seguridad de los APK de una app instalada, tal como estaba cuando funcionaba. */
data class CopiaApk(
	val paquete: String,
	val nombre: String,
	val versionName: String,
	val versionCode: Long,
	val archivos: List<ArchivoCopia>,
	val fecha: String,
	/** App de sistema con actualizaciones instaladas cuando se hizo la copia. */
	val sistemaActualizada: Boolean,
	val carpeta: File,
) {
	val tamano: Long get() = archivos.sumOf { it.tamano }

	/** Los archivos APK de la copia, el base primero. */
	fun apks(): List<File> = archivos.map { File(carpeta, it.nombre) }
}

/**
 * Copias de seguridad de los APK de TalkBack, de la voz de Google y de cualquier
 * otra app, hechas mientras funcionan.
 *
 * Cualquier app puede leer los APK instalados (ApplicationInfo.sourceDir y sus
 * partes divididas), así que se guardan en el almacenamiento interno de la app
 * (sin copia en la nube) para reinstalarlos sin Internet ni tienda si un día
 * se corrompen o una actualización los rompe. Se conservan como mucho
 * [MAXIMO_VERSIONES] versiones por app.
 */
class CopiasApk(contexto: Context) {
	private val app = contexto.applicationContext
	private val pm = app.packageManager
	private val raiz = File(app.noBackupFilesDir, "copias")

	/** Los paquetes que se ofrecen por defecto: TalkBack, la voz de Google y las variantes de Samsung si están. */
	fun sugeridos(): List<String> {
		val lista = Paquetes.ESENCIALES.toMutableList()
		for (p in listOf(Paquetes.TALKBACK_SAMSUNG, Paquetes.TALKBACK_SAMSUNG_ANTIGUO, Paquetes.TTS_SAMSUNG)) {
			if (instalada(p) != null) lista += p
		}
		return lista
	}

	/** La información de [paquete] si está instalado (también deshabilitado), o null. */
	fun instalada(paquete: String): PackageInfo? =
		try {
			if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(paquete, PackageManager.PackageInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
			else @Suppress("DEPRECATION") pm.getPackageInfo(paquete, PackageManager.MATCH_DISABLED_COMPONENTS)
		} catch (e: PackageManager.NameNotFoundException) {
			null
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo consultar $paquete", e)
			null
		}

	/** Todas las copias guardadas, de la más nueva a la más antigua dentro de cada paquete. */
	fun lista(): List<CopiaApk> {
		val copias = mutableListOf<CopiaApk>()
		raiz.listFiles()?.filter { it.isDirectory }?.forEach { dirPaquete ->
			dirPaquete.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
				leer(dir)?.let { copias += it }
			}
		}
		return copias.sortedWith(compareBy<CopiaApk> { it.nombre.lowercase() }.thenByDescending { it.versionCode })
	}

	/** Las copias de [paquete], la más nueva primero. */
	fun de(paquete: String): List<CopiaApk> = lista().filter { it.paquete == paquete }.sortedByDescending { it.versionCode }

	/** Lo que ocupan los APK instalados de [paquete] (lo que hace falta para copiarlos), o 0 si no está. */
	fun espacioNecesario(paquete: String): Long = archivosInstalados(paquete).sumOf { it.length() }

	/** Espacio libre en el almacenamiento interno de la app. */
	fun espacioLibre(): Long = runCatching { StatFs(app.noBackupFilesDir.path).availableBytes }.getOrDefault(0L)

	private fun archivosInstalados(paquete: String): List<File> {
		val info = instalada(paquete)?.applicationInfo ?: return emptyList()
		val lista = mutableListOf(File(info.sourceDir))
		info.splitSourceDirs?.forEach { lista += File(it) }
		return lista.filter { it.exists() && it.canRead() }
	}

	/**
	 * Copia los APK instalados de [paquete]. [progreso] recibe los bytes copiados
	 * y el total. Si ya hay una copia de esa versión, se rehace. Lanza
	 * [IOException] si no hay espacio o no se puede leer.
	 */
	suspend fun hacer(paquete: String, progreso: (Long, Long) -> Unit = { _, _ -> }): CopiaApk = withContext(Dispatchers.IO) {
		val info = instalada(paquete) ?: throw IOException("no instalado")
		val ai = info.applicationInfo ?: throw IOException("sin información")
		val origenes = archivosInstalados(paquete)
		if (origenes.isEmpty()) throw IOException("sin APK legibles")
		val total = origenes.sumOf { it.length() }
		// Margen para no dejar el móvil sin espacio
		if (espacioLibre() < total + MARGEN_LIBRE) throw SinEspacioException(total)
		val codigo = codigoVersion(info)
		val destino = File(File(raiz, paquete), codigo.toString())
		val temporal = File(File(raiz, paquete), "$codigo.parcial")
		temporal.deleteRecursively()
		temporal.mkdirs()
		var copiados = 0L
		val archivos = mutableListOf<ArchivoCopia>()
		try {
			origenes.forEachIndexed { i, origen ->
				// El base se llama base.apk; las partes conservan su nombre (split_config.es.apk...)
				val nombre = if (i == 0) "base.apk" else origen.name.let { if (it.endsWith(".apk")) it else "$it.apk" }
				val resumen = MessageDigest.getInstance("SHA-256")
				origen.inputStream().use { entrada ->
					File(temporal, nombre).outputStream().use { salida ->
						val bufer = ByteArray(256 * 1024)
						while (true) {
							ensureActive()
							val n = entrada.read(bufer)
							if (n < 0) break
							salida.write(bufer, 0, n)
							resumen.update(bufer, 0, n)
							copiados += n
							progreso(copiados, total)
						}
					}
				}
				archivos += ArchivoCopia(nombre, File(temporal, nombre).length(), resumen.digest().joinToString("") { "%02x".format(it) })
			}
			val copia = CopiaApk(
				paquete = paquete,
				nombre = ai.loadLabel(pm).toString(),
				versionName = info.versionName.orEmpty(),
				versionCode = codigo,
				archivos = archivos,
				fecha = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date()),
				sistemaActualizada = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
				carpeta = destino,
			)
			File(temporal, MANIFIESTO).writeText(aJson(copia).toString(1))
			destino.deleteRecursively()
			if (!temporal.renameTo(destino)) throw IOException("no se pudo guardar la copia")
			recortar(paquete)
			copia
		} catch (e: Exception) {
			temporal.deleteRecursively()
			throw e
		}
	}

	/** Borra [copia]. */
	fun borrar(copia: CopiaApk) {
		copia.carpeta.deleteRecursively()
		copia.carpeta.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
	}

	/**
	 * Comprueba que los archivos de [copia] siguen enteros (tamaño y SHA-256).
	 * Bloquea: llámese desde un hilo de trabajo.
	 */
	fun integra(copia: CopiaApk): Boolean = copia.archivos.all { a ->
		val f = File(copia.carpeta, a.nombre)
		f.exists() && f.length() == a.tamano && sha256(f) == a.sha256
	}

	/** Deja solo las [MAXIMO_VERSIONES] copias más nuevas de [paquete]. */
	private fun recortar(paquete: String) {
		de(paquete).drop(MAXIMO_VERSIONES).forEach { borrar(it) }
	}

	private fun leer(dir: File): CopiaApk? {
		val manifiesto = File(dir, MANIFIESTO)
		if (!manifiesto.exists()) return null
		return try {
			val o = JSONObject(manifiesto.readText())
			val lista = o.getJSONArray("archivos")
			val archivos = (0 until lista.length()).map {
				val a = lista.getJSONObject(it)
				ArchivoCopia(a.getString("nombre"), a.getLong("tamano"), a.optString("sha256"))
			}
			CopiaApk(
				paquete = o.getString("paquete"),
				nombre = o.optString("nombre", o.getString("paquete")),
				versionName = o.optString("versionName"),
				versionCode = o.optLong("versionCode"),
				archivos = archivos,
				fecha = o.optString("fecha"),
				sistemaActualizada = o.optBoolean("sistemaActualizada"),
				carpeta = dir,
			)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "Copia dañada en ${dir.name}", e)
			null
		}
	}

	private fun aJson(c: CopiaApk): JSONObject = JSONObject()
		.put("paquete", c.paquete)
		.put("nombre", c.nombre)
		.put("versionName", c.versionName)
		.put("versionCode", c.versionCode)
		.put("fecha", c.fecha)
		.put("sistemaActualizada", c.sistemaActualizada)
		.put("archivos", JSONArray().apply {
			c.archivos.forEach { put(JSONObject().put("nombre", it.nombre).put("tamano", it.tamano).put("sha256", it.sha256)) }
		})

	/** Lanzada cuando no hay espacio para la copia; [necesario] son los bytes que ocupa. */
	class SinEspacioException(val necesario: Long) : IOException("sin espacio")

	companion object {
		private const val ETIQUETA = "CopiasApk"
		private const val MANIFIESTO = "copia.json"
		const val MAXIMO_VERSIONES = 2
		private const val MARGEN_LIBRE = 200L * 1024 * 1024

		fun codigoVersion(info: PackageInfo): Long =
			if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

		fun sha256(archivo: File): String {
			val resumen = MessageDigest.getInstance("SHA-256")
			archivo.inputStream().use { entrada ->
				val bufer = ByteArray(1024 * 1024)
				while (true) {
					val n = entrada.read(bufer)
					if (n < 0) break
					resumen.update(bufer, 0, n)
				}
			}
			return resumen.digest().joinToString("") { "%02x".format(it) }
		}
	}
}
