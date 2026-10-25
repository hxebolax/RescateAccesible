package com.xebolax.rescate.nucleo

import android.content.Context

/**
 * Almacenamiento protegido del dispositivo.
 *
 * El rescate tiene que funcionar también justo después de reiniciar, antes de
 * desbloquear el móvil por primera vez (arranque directo), que es cuando más
 * falta hace si TalkBack no arranca. En ese momento solo se puede leer el
 * almacenamiento «protegido del dispositivo», así que los ajustes, las voces
 * de Pico copiadas y las grabaciones viven ahí desde el principio.
 */
object Almacen {
	fun protegido(contexto: Context): Context {
		val app = contexto.applicationContext ?: contexto
		return app.createDeviceProtectedStorageContext()
	}
}
