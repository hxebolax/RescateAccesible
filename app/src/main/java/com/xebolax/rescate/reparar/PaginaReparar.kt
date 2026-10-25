package com.xebolax.rescate.reparar

import android.content.pm.PackageManager
import androidx.lifecycle.lifecycleScope
import com.xebolax.rescate.R
import com.xebolax.rescate.guia.AsistenteAjustes
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.rescate.ActividadRescate
import com.xebolax.rescate.rescate.Opcion
import com.xebolax.rescate.rescate.Pagina
import com.xebolax.rescate.rescate.SesionRescate
import com.xebolax.rescate.sistema.ListaServicios
import com.xebolax.rescate.sistema.Paquetes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * «Reparar o reinstalar» del menú de rescate: TalkBack, la voz de Google y los
 * demás lectores y motores instalados. Reinstalar desde la copia guardada no
 * necesita Internet ni la tienda; quitar actualizaciones, forzar la detención o
 * habilitar la app se hacen guiando por los Ajustes.
 */
object PaginaReparar {

	/** La lista de componentes, o directamente la página de [paquete]. */
	fun pagina(a: ActividadRescate, paquete: String? = null): Pagina {
		if (paquete != null) return paginaPaquete(a, paquete)
		return Pagina(a.getString(R.string.pag_reparar), a.getString(R.string.rep_intro), { act ->
			candidatos(act).map { p -> Opcion(estado(act, p)) { it.abrirPagina(paginaPaquete(it, p)) } }
		})
	}

	/** TalkBack y la voz de Google primero, después los lectores y motores instalados y los que tienen copia. */
	private fun candidatos(a: ActividadRescate): List<String> {
		val lista = LinkedHashSet<String>()
		lista += Paquetes.ESENCIALES
		a.servicios.lectores().forEach { lista += ListaServicios.paquete(it.componente) }
		a.servicios.motores().map { it.paquete }.filter { it != a.packageName }.forEach { lista += it }
		CopiasApk(a).lista().forEach { lista += it.paquete }
		return lista.toList()
	}

	fun nombre(a: ActividadRescate, paquete: String): String =
		runCatching {
			val pm = a.packageManager
			pm.getApplicationInfo(paquete, PackageManager.MATCH_DISABLED_COMPONENTS).loadLabel(pm).toString()
		}.getOrNull() ?: Paquetes.LECTORES[paquete] ?: Paquetes.MOTORES[paquete] ?: paquete

	private fun estado(a: ActividadRescate, paquete: String): String {
		val nombre = nombre(a, paquete)
		val copias = CopiasApk(a).de(paquete)
		val version = CopiasApk(a).instalada(paquete)?.versionName.orEmpty()
		val estado = when {
			!Tiendas.instalada(a, paquete) -> a.getString(R.string.copias_no_instalada)
			Tiendas.deshabilitada(a, paquete) -> a.getString(R.string.copias_deshabilitada, version)
			Tiendas.tieneActualizaciones(a, paquete) -> a.getString(R.string.copias_sistema_actualizada, version)
			Tiendas.esDeSistema(a, paquete) -> a.getString(R.string.copias_sistema, version)
			else -> a.getString(R.string.copias_instalada, version)
		}
		val copia = if (copias.isEmpty()) a.getString(R.string.rep_sin_copia) else a.getString(R.string.rep_con_copia, copias.first().versionName)
		return "$nombre, $estado, $copia"
	}

	private fun paginaPaquete(a: ActividadRescate, paquete: String) = Pagina(nombre(a, paquete), estado(a, paquete), { act ->
		val nombre = nombre(act, paquete)
		val instalada = Tiendas.instalada(act, paquete)
		val copias = CopiasApk(act).de(paquete)
		val lista = mutableListOf<Opcion>()
		copias.firstOrNull()?.let { copia ->
			lista += Opcion(act.getString(R.string.rep_reinstalar_copia, nombre, copia.versionName), act.getString(R.string.rep_reinstalar_copia_ayuda)) {
				reinstalar(it, copia)
			}
		}
		if (instalada && Tiendas.deshabilitada(act, paquete)) {
			lista += Opcion(act.getString(R.string.rep_habilitar, nombre), act.getString(R.string.rep_habilitar_ayuda)) {
				it.trabajar(it.getString(R.string.rescate_un_momento)) { AsistenteAjustes(it).habilitarApp(paquete, nombre) }
			}
		}
		if (instalada && Tiendas.tieneActualizaciones(act, paquete)) {
			lista += Opcion(act.getString(R.string.rep_desinstalar_actualizaciones, nombre), act.getString(R.string.rep_desinstalar_actualizaciones_ayuda)) { x ->
				x.confirmar(x.getString(R.string.rep_desinstalar_pregunta, nombre), x.getString(R.string.rep_desinstalar_si)) { y ->
					y.trabajar(y.getString(R.string.rescate_un_momento)) { AsistenteAjustes(y).desinstalarActualizaciones(paquete, nombre) }
				}
			}
		}
		if (instalada) {
			lista += Opcion(act.getString(R.string.lector_forzar_detencion, nombre), act.getString(R.string.lector_forzar_detencion_ayuda)) {
				it.trabajar(it.getString(R.string.rescate_un_momento)) { AsistenteAjustes(it).forzarDetencion(paquete, nombre) }
			}
			lista += Opcion(act.getString(R.string.rep_borrar_cache, nombre), act.getString(R.string.rep_borrar_cache_ayuda)) {
				it.trabajar(it.getString(R.string.rescate_un_momento)) { AsistenteAjustes(it).borrarCache(paquete, nombre) }
			}
			lista += Opcion(act.getString(R.string.rep_hacer_copia, nombre), act.getString(R.string.rep_hacer_copia_ayuda)) { x ->
				x.trabajar(x.getString(R.string.rep_copiando, nombre)) {
					try {
						val copia = CopiasApk(x).hacer(paquete)
						x.getString(R.string.rep_copia_hecha, copia.nombre, copia.versionName)
					} catch (e: CopiasApk.SinEspacioException) {
						x.getString(R.string.rep_sin_espacio)
					}
				}
			}
		}
		lista += Opcion(act.getString(R.string.rep_tienda, nombre), act.getString(R.string.rep_tienda_ayuda)) {
			it.salirPara(it.getString(R.string.rep_tienda_abierta, nombre)) { Tiendas.abrirEnTienda(it, paquete) }
		}
		if (instalada) lista += Opcion(act.getString(R.string.rep_informacion, nombre)) {
			it.salirPara(it.getString(R.string.rep_informacion_abierta, nombre)) { Tiendas.abrirInformacion(it, paquete) }
		}
		lista
	})

	/**
	 * Reinstala desde la copia: Android pide confirmar con su propio diálogo; el
	 * lector de rescate lo lee y el asistente puede pulsar «Instalar» si el
	 * usuario lo confirma con las teclas. Al terminar se dice el resultado.
	 */
	private fun reinstalar(a: ActividadRescate, copia: CopiaApk) {
		val reinstalador = Reinstalador(a)
		if (!reinstalador.puedeInstalar()) {
			a.salirPara(a.getString(R.string.rep_permiso_instalar)) {
				a.startActivity(reinstalador.intentPermiso())
				true
			}
			return
		}
		reinstalador.problemaCon(copia)?.let { problema ->
			a.decir(problema)
			return
		}
		Instalaciones.limpiar()
		a.trabajar(a.getString(R.string.rep_reinstalando, copia.nombre)) {
			reinstalador.desdeCopia(copia)
			// El diálogo de confirmación de Android: el asistente lo lee y ofrece pulsar Instalar
			val espera = withTimeoutOrNull(20_000) {
				Instalaciones.estado.first { it.fase != FaseInstalacion.NADA && it.fase != FaseInstalacion.PREPARANDO }
			}
			if (espera?.fase == FaseInstalacion.ESPERANDO_CONFIRMACION) {
				espera.confirmar?.let { intent ->
					a.salirPara(a.getString(R.string.rep_confirmar_instalacion)) {
						runCatching { a.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
					}
				}
				escucharResultado(a)
				AsistenteAjustes(a).confirmarInstalacion()
			} else {
				espera?.mensaje ?: a.getString(R.string.rep_sin_respuesta)
			}
		}
	}

	/** Dice el resultado de la instalación cuando llegue, aunque el menú esté detrás del instalador. */
	private fun escucharResultado(a: ActividadRescate) {
		a.lifecycleScope.launch {
			try {
				val final = withTimeoutOrNull(10 * 60_000L) {
					Instalaciones.estado.first { it.fase == FaseInstalacion.BIEN || it.fase == FaseInstalacion.MAL }
				} ?: return@launch
				val voz = SesionRescate.vozSiExiste() ?: return@launch
				final.mensaje?.let { voz.hablar(it, vaciar = false) }
			} catch (e: kotlinx.coroutines.CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w("PaginaReparar", "No se pudo esperar el resultado de la instalación", e)
			}
		}
	}
}
