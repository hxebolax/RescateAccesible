package com.xebolax.rescate.sistema

import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import com.xebolax.rescate.R
import com.xebolax.rescate.nucleo.Almacen
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.sistema.ComparadorInstantanea.Diferencia
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * La configuración «buena conocida»: los ajustes de accesibilidad y de voz tal
 * como estaban cuando todo funcionaba, para compararlos y restaurarlos.
 *
 * Se guarda en JSON en el almacenamiento protegido del dispositivo (funciona
 * antes de desbloquear tras reiniciar). Solo guarda ajustes del sistema, nunca
 * nada leído de otras apps.
 */
class Instantanea(contexto: Context) {
	private val app = contexto.applicationContext
	private val textos = Idioma.envolver(app)
	private val archivo = File(Almacen.protegido(app).filesDir, ARCHIVO)
	private val servicios = Servicios(app)

	fun existe(): Boolean = archivo.exists()

	/** Cuándo se guardó, en el formato del idioma de la app («25 de octubre de 2026»), o "". */
	fun fechaTexto(): String {
		val momento = leer()?.optLong(FECHA, 0L) ?: 0L
		if (momento <= 0L) return ""
		return DateFormat.getDateInstance(DateFormat.LONG, Idioma.efectivo(app)).format(Date(momento))
	}

	/** Los valores actuales de los ajustes que se guardan (null = no se pudo leer). */
	fun actual(): Map<String, String?> {
		val mapa = linkedMapOf<String, String?>()
		for (clave in listOf(
			ComparadorInstantanea.SERVICIOS, ComparadorInstantanea.ACCESIBILIDAD, ComparadorInstantanea.MOTOR,
			ComparadorInstantanea.VELOCIDAD, ComparadorInstantanea.TONO, ComparadorInstantanea.ATAJO,
			ComparadorInstantanea.BOTON, ComparadorInstantanea.ATAJO_BLOQUEO,
		)) {
			mapa[clave] = Servicios.leerSeguro(app, clave)
		}
		// El servicio del atajo solo está activo durante un rescate: no forma parte de la configuración
		mapa[ComparadorInstantanea.SERVICIOS] = ListaServicios.unir(
			ListaServicios.leer(mapa[ComparadorInstantanea.SERVICIOS]).filterNot { ListaServicios.normalizar(it) == ListaServicios.normalizar(servicios.atajo) },
		)
		mapa[ComparadorInstantanea.IDIOMA] = idiomaSistema().toLanguageTag()
		mapa[ComparadorInstantanea.MOTOR_CONOCIDO] = Preferencias(app).motorConocido
		return mapa
	}

	/** Guarda la configuración actual como buena conocida. Devuelve si pudo. */
	fun guardar(): Boolean {
		return try {
			val json = JSONObject()
			json.put(FECHA, System.currentTimeMillis())
			for ((clave, valor) in actual()) json.put(clave, valor ?: JSONObject.NULL)
			val parcial = File(archivo.parentFile, "$ARCHIVO.parcial")
			parcial.writeText(json.toString(1))
			archivo.delete()
			parcial.renameTo(archivo)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo guardar la configuración", e)
			false
		}
	}

	fun borrar() {
		archivo.delete()
	}

	private fun leer(): JSONObject? {
		if (!archivo.exists()) return null
		return try {
			JSONObject(archivo.readText())
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "La configuración guardada está dañada", e)
			null
		}
	}

	/** Lo guardado como mapa de claves (sin la fecha). */
	fun guardada(): Map<String, String?> {
		val json = leer() ?: return emptyMap()
		val mapa = linkedMapOf<String, String?>()
		for (clave in json.keys()) {
			if (clave == FECHA) continue
			mapa[clave] = if (json.isNull(clave)) null else json.optString(clave)
		}
		return mapa
	}

	/** Las diferencias como datos (para el diagnóstico). */
	fun diferenciasDatos(): List<Diferencia> {
		val guardada = guardada()
		if (guardada.isEmpty()) return emptyList()
		return ComparadorInstantanea.comparar(guardada, actual(), setOf(servicios.atajo))
	}

	/** Las diferencias con lo guardado, en frases claras para decir. */
	fun diferencias(): List<String> = diferenciasDatos().map { frase(it) }

	private fun nombreComponente(componente: String): String = servicios.nombreDe(componente)

	private fun nombreMotor(paquete: String?): String {
		if (paquete.isNullOrBlank()) return textos.getString(R.string.inst_ninguno)
		return servicios.motores().firstOrNull { it.paquete == paquete }?.nombre ?: Paquetes.MOTORES[paquete] ?: paquete
	}

	private fun lista(valor: String?): String {
		val componentes = ListaServicios.leer(valor)
		if (componentes.isEmpty()) return textos.getString(R.string.inst_ninguno)
		return componentes.joinToString(", ") { nombreComponente(it) }
	}

	private fun idiomaNombre(etiqueta: String?): String {
		if (etiqueta.isNullOrBlank()) return textos.getString(R.string.inst_desconocido)
		val app = Idioma.efectivo(this.app)
		return Locale.forLanguageTag(etiqueta).getDisplayName(app)
	}

	/** Una diferencia dicha en una frase. */
	fun frase(d: Diferencia): String = when (d) {
		is Diferencia.FaltaServicio -> textos.getString(R.string.inst_falta_servicio, nombreComponente(d.componente))
		is Diferencia.SobraServicio -> textos.getString(R.string.inst_sobra_servicio, nombreComponente(d.componente))
		is Diferencia.Valor -> when (d.clave) {
			ComparadorInstantanea.MOTOR -> textos.getString(R.string.inst_motor, nombreMotor(d.antes), nombreMotor(d.ahora))
			ComparadorInstantanea.VELOCIDAD -> textos.getString(R.string.inst_velocidad, d.antes ?: "100", d.ahora ?: "100")
			ComparadorInstantanea.TONO -> textos.getString(R.string.inst_tono, d.antes ?: "100", d.ahora ?: "100")
			ComparadorInstantanea.ATAJO -> textos.getString(R.string.inst_atajo, lista(d.antes), lista(d.ahora))
			ComparadorInstantanea.BOTON -> textos.getString(R.string.inst_boton, lista(d.antes), lista(d.ahora))
			ComparadorInstantanea.ATAJO_BLOQUEO -> textos.getString(if (d.ahora == "0") R.string.inst_bloqueo_quitado else R.string.inst_bloqueo_puesto)
			ComparadorInstantanea.IDIOMA -> textos.getString(R.string.inst_idioma, idiomaNombre(d.antes), idiomaNombre(d.ahora))
			else -> textos.getString(R.string.inst_otro, d.clave)
		}
	}

	/**
	 * Restaura lo guardado (necesita el permiso avanzado). Primero apaga los
	 * lectores que sobran y luego escribe la lista guardada, para que nunca haya
	 * dos lectores a la vez ni uno arranque sin exploración táctil. Después lo lee
	 * todo otra vez y dice qué quedó restaurado y qué no.
	 */
	suspend fun restaurar(): String {
		if (!servicios.puedeEscribir()) return textos.getString(R.string.inst_sin_permiso)
		val guardada = guardada()
		if (guardada.isEmpty()) return textos.getString(R.string.inst_no_hay)
		val antes = diferenciasDatos()
		if (antes.isEmpty()) return textos.getString(R.string.inst_ya_igual)
		val fallos = mutableListOf<String>()

		// 1. Servicios de accesibilidad, conservando los propios que estén en marcha (el rescate)
		val enMarchaPropios = servicios.activados().filter { c -> servicios.propios.any { ListaServicios.normalizar(it) == ListaServicios.normalizar(c) } }
		val objetivo = ComparadorInstantanea.serviciosARestaurar(guardada[ComparadorInstantanea.SERVICIOS], enMarchaPropios)
		if (antes.any { it is Diferencia.FaltaServicio || it is Diferencia.SobraServicio }) {
			val lectores = servicios.todosLectores()
			val sinLectores = ListaServicios.sinLectores(servicios.activados(), lectores)
			// Se apagan los lectores, se espera a que suelten la exploración táctil y se escribe lo guardado
			Servicios.escribirSeguro(app, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ListaServicios.unir(sinLectores))
			delay(ESPERA_LECTORES_MS)
			val ok = Servicios.escribirSeguro(app, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ListaServicios.unir(objetivo)) &&
				Servicios.escribirSeguro(app, Settings.Secure.ACCESSIBILITY_ENABLED, if (objetivo.isEmpty()) "0" else "1")
			if (!ok) fallos += textos.getString(R.string.inst_parte_servicios)
		}

		// 2. Motor de voz (solo si sigue instalado), velocidad, tono y atajos
		for (d in antes.filterIsInstance<Diferencia.Valor>()) {
			val valor = d.antes
			when (d.clave) {
				ComparadorInstantanea.IDIOMA -> fallos += textos.getString(R.string.inst_parte_idioma)
				ComparadorInstantanea.MOTOR -> {
					if (valor.isNullOrBlank() || servicios.motores().none { it.paquete == valor }) fallos += textos.getString(R.string.inst_parte_motor_falta, nombreMotor(valor))
					else if (!servicios.ponerMotorPredeterminado(valor)) fallos += textos.getString(R.string.inst_parte_motor)
				}
				else -> if (!Servicios.escribirSeguro(app, d.clave, valor.orEmpty())) fallos += frase(d)
			}
		}

		// 3. Comprobación: lo que sigue distinto no se da por restaurado
		delay(ESPERA_COMPROBAR_MS)
		val despues = diferenciasDatos()
		val restauradas = antes.size - despues.size
		Registro.i(ETIQUETA, "Restauración: ${antes.size} diferencias antes, ${despues.size} después")
		return buildString {
			if (despues.isEmpty()) append(textos.getString(R.string.inst_restaurado_todo))
			else {
				append(textos.resources.getQuantityString(R.plurals.inst_restauradas, restauradas.coerceAtLeast(0), restauradas.coerceAtLeast(0)))
				append(' ')
				append(textos.getString(R.string.inst_quedan, despues.joinToString(". ") { frase(it) }))
			}
			if (fallos.isNotEmpty() && despues.isNotEmpty()) append(' ').append(textos.getString(R.string.inst_fallos, fallos.distinct().joinToString(". ")))
		}
	}

	companion object {
		private const val ETIQUETA = "Instantanea"
		private const val ARCHIVO = "configuracion_buena.json"
		private const val FECHA = "fecha"
		private const val ESPERA_LECTORES_MS = 1500L
		private const val ESPERA_COMPROBAR_MS = 1500L

		/** El idioma del sistema (no el de la app). */
		fun idiomaSistema(): Locale =
			runCatching { Resources.getSystem().configuration.locales[0] }.getOrNull() ?: Locale.getDefault()
	}
}
