package com.xebolax.rescate.permiso

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.xebolax.rescate.R
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Registro
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * El código de vinculación escrito en una notificación, como hace Shizuku.
 *
 * La ventana «Vincular dispositivo con código de vinculación» de los Ajustes se
 * cierra si se cambia a otra app, y entonces el código deja de valer. Con esta
 * notificación el usuario no sale de los Ajustes: abre la ventana del código,
 * baja las notificaciones, contesta a la de Rescate Accesible escribiendo el
 * código y la app vincula y concede el permiso mientras la ventana sigue abierta.
 */
object NotificacionVinculacion {
	private const val CANAL = "permiso"
	private const val ID = 2
	const val CLAVE_CODIGO = "codigo"
	const val ACCION = "com.xebolax.rescate.permiso.CODIGO"

	private fun crearCanal(contexto: Context) {
		if (Build.VERSION.SDK_INT < 26) return
		val ctx = Idioma.envolver(contexto)
		val canal = NotificationChannel(CANAL, ctx.getString(R.string.perm_canal), NotificationManager.IMPORTANCE_HIGH).apply {
			description = ctx.getString(R.string.perm_canal_descripcion)
		}
		contexto.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
	}

	/** Muestra la notificación para escribir el código. Devuelve si se pudo (hace falta el permiso de notificaciones). */
	fun mostrar(contexto: Context, texto: String? = null): Boolean {
		val ctx = Idioma.envolver(contexto)
		if (!NotificationManagerCompat.from(contexto).areNotificationsEnabled()) return false
		return try {
			crearCanal(contexto)
			val entrada = RemoteInput.Builder(CLAVE_CODIGO).setLabel(ctx.getString(R.string.perm_notif_campo)).build()
			val banderas = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
			val respuesta = PendingIntent.getBroadcast(
				contexto, 0, Intent(contexto, ReceptorCodigoVinculacion::class.java).setAction(ACCION), banderas,
			)
			val accion = NotificationCompat.Action.Builder(0, ctx.getString(R.string.perm_notif_accion), respuesta)
				.addRemoteInput(entrada)
				.build()
			val abrir = PendingIntent.getActivity(contexto, 0, Intent(contexto, ActividadPermiso::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
			val cuerpo = texto ?: ctx.getString(R.string.perm_notif_texto)
			val aviso = NotificationCompat.Builder(contexto, CANAL)
				.setSmallIcon(R.drawable.ic_aviso)
				.setContentTitle(ctx.getString(R.string.perm_notif_titulo))
				.setContentText(cuerpo)
				.setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setOnlyAlertOnce(false)
				.setContentIntent(abrir)
				.addAction(accion)
				.build()
			@Suppress("MissingPermission")
			NotificationManagerCompat.from(contexto).notify(ID, aviso)
			true
		} catch (e: Exception) {
			Registro.w("NotificacionVinculacion", "No se pudo mostrar la notificación del código", e)
			false
		}
	}

	/** Cambia la notificación por un resultado (sin campo para escribir). */
	fun resultado(contexto: Context, texto: String, terminado: Boolean) {
		if (!terminado) {
			mostrar(contexto, texto)
			return
		}
		try {
			crearCanal(contexto)
			val ctx = Idioma.envolver(contexto)
			val abrir = PendingIntent.getActivity(contexto, 0, Intent(contexto, ActividadPermiso::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
			val aviso = NotificationCompat.Builder(contexto, CANAL)
				.setSmallIcon(R.drawable.ic_aviso)
				.setContentTitle(ctx.getString(R.string.perm_notif_titulo))
				.setContentText(texto)
				.setStyle(NotificationCompat.BigTextStyle().bigText(texto))
				.setContentIntent(abrir)
				.setAutoCancel(true)
				.build()
			@Suppress("MissingPermission")
			NotificationManagerCompat.from(contexto).notify(ID, aviso)
		} catch (e: Exception) {
			Registro.w("NotificacionVinculacion", "No se pudo mostrar el resultado", e)
		}
	}

	fun quitar(contexto: Context) {
		NotificationManagerCompat.from(contexto).cancel(ID)
	}
}

/** Recibe el código escrito en la notificación, vincula y concede el permiso. */
class ReceptorCodigoVinculacion : BroadcastReceiver() {
	override fun onReceive(contexto: Context, intent: Intent) {
		val codigo = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NotificacionVinculacion.CLAVE_CODIGO)?.toString()
			?.filter { it.isDigit() }.orEmpty()
		val app = contexto.applicationContext
		val ctx = Idioma.envolver(app)
		if (codigo.length != 6) {
			NotificacionVinculacion.resultado(app, ctx.getString(R.string.perm_codigo_invalido), terminado = false)
			return
		}
		NotificacionVinculacion.resultado(app, ctx.getString(R.string.perm_vinculando), terminado = false)
		val pendiente = goAsync()
		val terminado = AtomicBoolean(false)
		val soltar = { if (terminado.compareAndSet(false, true)) pendiente.finish() }
		// Android da unos segundos al receptor: se le suelta antes, y el trabajo sigue en el proceso de la app
		ambito.launch {
			delay(SOLTAR_MS)
			soltar()
		}
		ambito.launch {
			try {
				val resultado = withTimeoutOrNull(ESPERA_MS) { AdbInalambrico(app).vincularYConceder(codigo, null, null) }
				val texto = if (resultado == null) ctx.getString(R.string.perm_res_tiempo) else TextosPermiso.resultado(ctx, resultado)
				NotificacionVinculacion.resultado(app, texto, terminado = resultado?.tipo == TipoResultadoAdb.CONCEDIDO)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e("ReceptorCodigo", "Falló la vinculación desde la notificación", e)
				NotificacionVinculacion.resultado(app, ctx.getString(R.string.perm_res_fallo, e.message.orEmpty()), terminado = false)
			} finally {
				soltar()
			}
		}
	}

	private companion object {
		const val ESPERA_MS = 40_000L
		const val SOLTAR_MS = 8_000L
		val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	}
}
