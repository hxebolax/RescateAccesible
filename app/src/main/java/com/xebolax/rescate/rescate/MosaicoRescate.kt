package com.xebolax.rescate.rescate

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.xebolax.rescate.nucleo.Registro

/**
 * Mosaico «Rescate» de los ajustes rápidos: otra forma de abrir el rescate
 * (con el lector funcionando, o con alguien que ve la pantalla).
 */
class MosaicoRescate : TileService() {
	override fun onStartListening() {
		super.onStartListening()
		qsTile?.let {
			it.state = Tile.STATE_INACTIVE
			it.updateTile()
		}
	}

	@SuppressLint("StartActivityAndCollapseDeprecated")
	override fun onClick() {
		super.onClick()
		SesionRescate.empezarSinAbrir(SesionRescate.Origen.MOSAICO)
		val intent = SesionRescate.intentMenu(this)
		try {
			if (Build.VERSION.SDK_INT >= 34) {
				// Android 14 pide un PendingIntent para abrir una actividad desde el mosaico
				startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
			} else {
				@Suppress("DEPRECATION")
				startActivityAndCollapse(intent)
			}
		} catch (e: Exception) {
			Registro.e("Mosaico", "No se pudo abrir el rescate desde el mosaico", e)
		}
	}
}
