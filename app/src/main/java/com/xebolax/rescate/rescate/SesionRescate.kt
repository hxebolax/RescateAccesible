package com.xebolax.rescate.rescate

import android.content.Context
import android.content.Intent
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.servicio.LectorRescate
import com.xebolax.rescate.servicio.ServicioAtajo
import com.xebolax.rescate.servicio.ServicioBase
import com.xebolax.rescate.servicio.ServicioGuardian
import com.xebolax.rescate.sistema.Servicios
import com.xebolax.rescate.voz.VozEmergencia

/**
 * El estado de una sesión de rescate, compartido por el menú y los servicios.
 *
 * Hay una sola voz de rescate en el proceso: así el menú, el lector de rescate
 * y el guardián nunca hablan a la vez con dos voces.
 */
object SesionRescate {
	enum class Origen { ATAJO, GUARDIAN, APP, MOSAICO }

	@Volatile var activa = false
		private set
	var origen = Origen.APP
		private set

	/** Lectores que apagó el modo seguro, para devolverlos si termina sin ninguno. */
	var lectoresApagados: List<String> = emptyList()
	var modoSeguro = false

	private var voz: VozEmergencia? = null

	/** El servicio propio que está en marcha (el del atajo primero), o null. */
	val servicio: ServicioBase? get() = ServicioAtajo.instancia ?: ServicioGuardian.instancia

	fun voz(contexto: Context): VozEmergencia = synchronized(this) {
		voz ?: VozEmergencia(contexto.applicationContext).also { voz = it }
	}

	fun vozSiExiste(): VozEmergencia? = voz

	/** Cierra la voz si nadie la usa (sin sesión ni lector en marcha). */
	fun cerrarVozSiLibre() = synchronized(this) {
		val enUso = activa || servicio?.lector?.modo?.let { it != LectorRescate.Modo.APAGADO } == true
		if (!enUso) {
			voz?.cerrar()
			voz = null
		}
	}

	/** Empieza (o retoma) el rescate y abre su menú. */
	fun empezar(contexto: Context, desde: Origen) {
		empezarSinAbrir(desde)
		ActividadRescate.abrir(contexto)
	}

	/** Marca el rescate como empezado (quien llama abre el menú). */
	fun empezarSinAbrir(desde: Origen) {
		if (activa) return
		activa = true
		origen = desde
		lectoresApagados = emptyList()
		modoSeguro = false
		Registro.i("Sesion", "Empieza un rescate desde $desde")
		// Los servicios reciben ya los cambios de ventana (ver ServicioBase.eventosPropios)
		servicio?.refrescarTeclas()
	}

	/**
	 * Termina el rescate: devuelve los lectores que apagó el modo seguro si no ha
	 * quedado ninguno, apaga el lector de rescate y el servicio del atajo.
	 */
	fun terminar(contexto: Context, apagarAtajo: Boolean = true) {
		if (!activa) return
		activa = false
		val servicios = Servicios(contexto.applicationContext)
		var devueltos = false
		if (lectoresApagados.isNotEmpty() && !servicios.hayLector(servicios.activados())) {
			devueltos = servicios.devolverLectores(lectoresApagados)
		}
		lectoresApagados = emptyList()
		modoSeguro = false
		servicio?.let { s ->
			runCatching { s.panel.quitarTodo() }
			s.oyenteTeclas = null
			s.ponerModoLector(LectorRescate.Modo.APAGADO)
			s.filtrarTeclas(false)
			s.lector.ignorarPaquete = null
		}
		ServicioGuardian.instancia?.alTerminarRescate()
		if (apagarAtajo && !devueltos) servicios.apagarAtajo()
		voz?.callar()
		voz?.salida?.soltarFoco()
		cerrarVozSiLibre()
		Registro.i("Sesion", "Termina el rescate")
	}

	/** Intent para abrir el menú desde fuera de una actividad (servicio, mosaico, notificación). */
	fun intentMenu(contexto: Context): Intent = Intent(contexto, ActividadRescate::class.java)
		.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
}
