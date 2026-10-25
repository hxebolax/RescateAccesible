package com.xebolax.rescate.reparar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.sistema.Paquetes

/**
 * Tiendas e información de las apps, sin Google Play Services: todo se consulta
 * con PackageManager y se abre con intents públicos, con alternativa si el
 * móvil no tiene Play Store (Huawei, ROM libres…).
 */
object Tiendas {
	/** Paquete de los servicios de Google Play (solo para saber si están, nunca se usan). */
	private const val SERVICIOS_GOOGLE = "com.google.android.gms"
	private const val WEB_PLAY = "https://play.google.com/store/apps/details?id="
	private const val WEB_FDROID = "https://f-droid.org/packages/"

	/** Paquetes que se pueden instalar desde F-Droid. */
	private val EN_FDROID = setOf(Paquetes.TTS_ESPEAK, Paquetes.TTS_RHVOICE)

	fun instalada(contexto: Context, paquete: String): Boolean = info(contexto, paquete) != null

	private fun info(contexto: Context, paquete: String): ApplicationInfo? =
		try {
			if (Build.VERSION.SDK_INT >= 33) contexto.packageManager.getApplicationInfo(paquete, PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
			else @Suppress("DEPRECATION") contexto.packageManager.getApplicationInfo(paquete, PackageManager.MATCH_DISABLED_COMPONENTS)
		} catch (e: PackageManager.NameNotFoundException) {
			null
		}

	/** Si el móvil tiene Play Store instalada y activa. */
	fun hayPlayStore(contexto: Context): Boolean = info(contexto, Paquetes.PLAY_STORE)?.enabled == true

	/** Si el móvil tiene los servicios de Google Play (solo informativo). */
	fun hayServiciosGoogle(contexto: Context): Boolean = info(contexto, SERVICIOS_GOOGLE)?.enabled == true

	fun hayFdroid(contexto: Context): Boolean = info(contexto, Paquetes.FDROID)?.enabled == true

	/** Si [paquete] viene con el sistema (preinstalada). */
	fun esDeSistema(contexto: Context, paquete: String): Boolean =
		info(contexto, paquete)?.let { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 } == true

	/** Si [paquete] es de sistema y tiene actualizaciones instaladas encima (se pueden desinstalar). */
	fun tieneActualizaciones(contexto: Context, paquete: String): Boolean =
		info(contexto, paquete)?.let { it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 } == true

	/** Si [paquete] está instalado pero deshabilitado (no funciona hasta habilitarlo en su información). */
	fun deshabilitada(contexto: Context, paquete: String): Boolean = info(contexto, paquete)?.enabled == false

	/**
	 * Abre la ficha de [paquete] en la tienda que haya: Play Store, F-Droid (si la
	 * app está allí) o la web de Play en el navegador. Devuelve si pudo abrir algo.
	 */
	fun abrirEnTienda(contexto: Context, paquete: String): Boolean {
		val intentos = mutableListOf<Intent>()
		if (hayPlayStore(contexto)) {
			intentos += Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$paquete")).setPackage(Paquetes.PLAY_STORE)
		}
		if (paquete in EN_FDROID) {
			if (hayFdroid(contexto)) intentos += Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$paquete")).setPackage(Paquetes.FDROID)
			intentos += Intent(Intent.ACTION_VIEW, Uri.parse(WEB_FDROID + paquete))
		}
		intentos += Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$paquete"))
		intentos += Intent(Intent.ACTION_VIEW, Uri.parse(WEB_PLAY + paquete))
		return abrirPrimero(contexto, intentos)
	}

	/** Abre la información de [paquete] (forzar detención, desinstalar actualizaciones, habilitar, almacenamiento). */
	fun abrirInformacion(contexto: Context, paquete: String): Boolean = abrirPrimero(
		contexto,
		listOf(
			Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$paquete")),
			Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
			Intent(Settings.ACTION_SETTINGS),
		),
	)

	/** Abre el primer intent que alguna app pueda atender. */
	fun abrirPrimero(contexto: Context, intentos: List<Intent>): Boolean {
		for (intent in intentos) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			try {
				contexto.startActivity(intent)
				return true
			} catch (e: ActivityNotFoundException) {
				// Se prueba el siguiente
			} catch (e: SecurityException) {
				Registro.w("Tiendas", "Android no dejó abrir ${intent.action}", e)
			}
		}
		Registro.w("Tiendas", "Nada pudo abrir ninguna de las ${intentos.size} opciones")
		return false
	}
}
