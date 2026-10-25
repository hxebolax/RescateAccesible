package com.xebolax.rescate.permiso

import android.content.Context
import com.xebolax.rescate.R

/** Los resultados de conceder el permiso, en lenguaje claro (sin el texto técnico, que va al registro). */
object TextosPermiso {
	fun resultado(contexto: Context, r: ResultadoAdb): String = contexto.getString(when (r.tipo) {
		TipoResultadoAdb.CONCEDIDO -> R.string.perm_res_concedido
		TipoResultadoAdb.NO_DISPONIBLE -> R.string.perm_res_no_disponible
		TipoResultadoAdb.SIN_WIFI -> R.string.perm_res_sin_wifi
		TipoResultadoAdb.SIN_PUERTO_VINCULACION -> R.string.perm_res_sin_puerto_vinculacion
		TipoResultadoAdb.VINCULACION_FALLIDA -> R.string.perm_res_vinculacion_fallida
		TipoResultadoAdb.SIN_PUERTO_CONEXION -> R.string.perm_res_sin_puerto_conexion
		TipoResultadoAdb.NO_AUTORIZADO -> R.string.perm_res_no_autorizado
		TipoResultadoAdb.FALLO -> R.string.perm_res_no_concedido
	})

	fun root(contexto: Context, r: ResultadoConcesion): String = contexto.getString(when (r) {
		ResultadoConcesion.CONCEDIDO -> R.string.perm_res_concedido
		ResultadoConcesion.SIN_ROOT -> R.string.perm_root_no_hay
		ResultadoConcesion.ROOT_DENEGADO -> R.string.perm_root_denegado
		ResultadoConcesion.TIEMPO_AGOTADO -> R.string.perm_res_tiempo
		ResultadoConcesion.FALLO -> R.string.perm_res_no_concedido
	})
}
