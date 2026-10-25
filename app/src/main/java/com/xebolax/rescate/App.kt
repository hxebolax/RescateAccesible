package com.xebolax.rescate

import android.app.Application
import android.content.Intent
import android.os.UserManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.xebolax.rescate.accesibilidad.DeslizadorAjuste
import com.xebolax.rescate.accesibilidad.MemoriaFoco
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Preferencias
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.rescate.ActividadRescate

/**
 * Arranque del proceso: registro técnico, foco que vuelve en toda la app y el
 * acceso directo «Rescate» del icono (mantener pulsado el icono de la app).
 *
 * Puede arrancar antes de desbloquear el móvil (servicios de arranque directo):
 * aquí solo se usa el almacenamiento protegido del dispositivo.
 */
class App : Application() {
	override fun onCreate() {
		super.onCreate()
		Registro.iniciar(this)
		registerActivityLifecycleCallbacks(MemoriaFoco)
		DeslizadorAjuste.formatos[Preferencias.VELOCIDAD] = DeslizadorAjuste.porCiento
		DeslizadorAjuste.formatos[Preferencias.TONO] = DeslizadorAjuste.porCiento
		DeslizadorAjuste.formatos[Preferencias.VOLUMEN_MINIMO] = DeslizadorAjuste.porCiento
		DeslizadorAjuste.formatos[Preferencias.ESPERA_SEGURO] = DeslizadorAjuste.segundos
		Thread.setDefaultUncaughtExceptionHandler(manejadorFallos(Thread.getDefaultUncaughtExceptionHandler()))
		ponerAccesoDirecto()
	}

	/** Los cierres inesperados quedan en el registro técnico (sin datos privados) antes de que Android cierre la app. */
	private fun manejadorFallos(anterior: Thread.UncaughtExceptionHandler?) = Thread.UncaughtExceptionHandler { hilo, error ->
		Registro.e("Cierre", "Cierre inesperado en el hilo ${hilo.name}", error)
		anterior?.uncaughtException(hilo, error)
	}

	/** Acceso directo dinámico al menú de rescate (el estático no admite la variante de pruebas). */
	private fun ponerAccesoDirecto() {
		try {
			if (getSystemService(UserManager::class.java)?.isUserUnlocked == false) return
			val textos = Idioma.envolver(this)
			val acceso = ShortcutInfoCompat.Builder(this, "rescate")
				.setShortLabel(textos.getString(R.string.acceso_rescate_corto))
				.setLongLabel(textos.getString(R.string.acceso_rescate_largo))
				.setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
				.setIntent(Intent(this, ActividadRescate::class.java).setAction(Intent.ACTION_VIEW))
				.build()
			ShortcutManagerCompat.pushDynamicShortcut(this, acceso)
		} catch (e: Exception) {
			Registro.w("App", "No se pudo crear el acceso directo", e)
		}
	}
}
