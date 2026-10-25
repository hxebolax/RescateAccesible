package com.xebolax.rescate.rescate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xebolax.rescate.R
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Registro

/**
 * Notificaciones del rescate: el aviso del guardián (con «Abrir el rescate»).
 * Son una vía más, nunca la única: el aviso principal es la voz de rescate.
 * Si el usuario no dio permiso de notificaciones, simplemente no se muestran.
 */
object Avisos {
	private const val CANAL = "rescate"
	private const val ID_RESCATE = 1

	private fun crearCanal(contexto: Context) {
		if (Build.VERSION.SDK_INT < 26) return
		val ctx = Idioma.envolver(contexto)
		val canal = NotificationChannel(CANAL, ctx.getString(R.string.aviso_canal), NotificationManager.IMPORTANCE_HIGH).apply {
			description = ctx.getString(R.string.aviso_canal_descripcion)
		}
		contexto.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
	}

	fun puedeAvisar(contexto: Context): Boolean =
		Build.VERSION.SDK_INT < 33 || contexto.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

	/** Muestra el aviso [texto] con un toque que abre el rescate. */
	fun mostrarRescate(contexto: Context, texto: String) {
		if (!puedeAvisar(contexto)) return
		try {
			crearCanal(contexto)
			val ctx = Idioma.envolver(contexto)
			val abrir = PendingIntent.getActivity(contexto, 0, SesionRescate.intentMenu(contexto), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
			val aviso = NotificationCompat.Builder(contexto, CANAL)
				.setSmallIcon(R.drawable.ic_aviso)
				.setContentTitle(ctx.getString(R.string.app_name))
				.setContentText(texto)
				.setStyle(NotificationCompat.BigTextStyle().bigText(texto))
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setCategory(NotificationCompat.CATEGORY_ERROR)
				.setContentIntent(abrir)
				.addAction(0, ctx.getString(R.string.aviso_abrir_rescate), abrir)
				.setAutoCancel(true)
				.build()
			@Suppress("MissingPermission")
			NotificationManagerCompat.from(contexto).notify(ID_RESCATE, aviso)
		} catch (e: Exception) {
			Registro.w("Avisos", "No se pudo mostrar el aviso", e)
		}
	}
}
