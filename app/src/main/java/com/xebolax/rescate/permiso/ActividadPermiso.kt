package com.xebolax.rescate.permiso

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.xebolax.rescate.R
import com.xebolax.rescate.accesibilidad.avisar
import com.xebolax.rescate.accesibilidad.campoTexto
import com.xebolax.rescate.accesibilidad.conEspera
import com.xebolax.rescate.accesibilidad.configurarBarra
import com.xebolax.rescate.accesibilidad.copiar
import com.xebolax.rescate.accesibilidad.dp
import com.xebolax.rescate.accesibilidad.encabezado
import com.xebolax.rescate.accesibilidad.mensaje
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.sistema.Servicios
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * El permiso avanzado: qué es, si está concedido y cuatro formas de concederlo
 * (depuración inalámbrica desde el propio móvil, ordenador con adb, root y
 * Shizuku). Cada método es una sección con encabezado, pasos numerados y
 * botones que dicen exactamente lo que hacen. El estado se anuncia con una
 * región viva educada cuando cambia.
 */
class ActividadPermiso : AppCompatActivity() {
	private lateinit var estado: TextView
	private lateinit var campoCodigo: EditText
	private lateinit var campoPuerto: EditText
	private lateinit var campoPuertoConexion: EditText
	private lateinit var contenedorCodigo: TextInputLayout
	private lateinit var despues: LinearLayout
	private lateinit var botonApagarDepuracion: MaterialButton
	private val adb by lazy { AdbInalambrico(this) }
	private var ultimoEstado: Boolean? = null

	private val pedirNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
		if (concedido) mostrarNotificacion()
		else mensaje(getString(R.string.perm_titulo), getString(R.string.perm_notif_sin_permiso))
	}

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(Idioma.envolver(base))
	}

	override fun onCreate(guardado: Bundle?) {
		super.onCreate(guardado)
		val m = dp(this, 16)
		val barra = MaterialToolbar(this)
		val contenido = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(m, m / 2, m, m * 2)
		}

		// Estado
		estado = texto("").apply {
			// Se anuncia sin mover el foco cuando cambia (al volver de los Ajustes, por ejemplo)
			accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
		}
		contenido.addView(estado)
		contenido.addView(boton(getString(R.string.perm_comprobar)) { actualizar(anunciar = true) })

		// Qué permite y cómo se quita
		contenido.addView(titulo(R.string.perm_que_es_titulo))
		contenido.addView(texto(getString(R.string.perm_que_es)))
		contenido.addView(texto(getString(R.string.perm_revocar, ConcesionPermiso.comandoRevocar(this))).apply { setTextIsSelectable(true) })

		// Método 1: depuración inalámbrica
		contenido.addView(titulo(R.string.perm_m1_titulo))
		if (!adb.disponible()) {
			contenido.addView(texto(getString(R.string.perm_m1_no_disponible)))
		} else {
			contenido.addView(texto(getString(R.string.perm_m1_intro)))
			contenido.addView(paso(1, R.string.perm_m1_paso1))
			contenido.addView(boton(getString(R.string.perm_abrir_info_telefono)) { abrir(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) })
			contenido.addView(paso(2, R.string.perm_m1_paso2))
			contenido.addView(boton(getString(R.string.perm_abrir_desarrollador)) { abrir(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) })
			contenido.addView(paso(3, R.string.perm_m1_paso3))
			contenido.addView(texto(getString(R.string.perm_m1_aviso_ventana)))
			contenido.addView(subtitulo(R.string.perm_m1_opcion_notificacion))
			contenido.addView(texto(getString(R.string.perm_m1_notificacion_explica)))
			contenido.addView(boton(getString(R.string.perm_m1_recibir_notificacion)) { pedirNotificacion() })
			contenido.addView(subtitulo(R.string.perm_m1_opcion_aqui))
			contenido.addView(texto(getString(R.string.perm_m1_aqui_explica)))
			val (cajaCodigo, codigo) = campoTexto(getString(R.string.perm_campo_codigo))
			codigo.inputType = InputType.TYPE_CLASS_NUMBER
			contenedorCodigo = cajaCodigo
			campoCodigo = codigo
			contenido.addView(cajaCodigo, anchoCompleto())
			val (cajaPuerto, puerto) = campoTexto(getString(R.string.perm_campo_puerto))
			puerto.inputType = InputType.TYPE_CLASS_NUMBER
			cajaPuerto.helperText = getString(R.string.perm_campo_puerto_ayuda)
			campoPuerto = puerto
			contenido.addView(cajaPuerto, anchoCompleto())
			contenido.addView(boton(getString(R.string.perm_buscar_puerto)) { buscarPuerto() })
			val (cajaConexion, conexion) = campoTexto(getString(R.string.perm_campo_puerto_conexion))
			conexion.inputType = InputType.TYPE_CLASS_NUMBER
			cajaConexion.helperText = getString(R.string.perm_campo_puerto_conexion_ayuda)
			campoPuertoConexion = conexion
			contenido.addView(cajaConexion, anchoCompleto())
			contenido.addView(boton(getString(R.string.perm_vincular_conceder)) { vincularYConceder() })
			contenido.addView(boton(getString(R.string.perm_olvidar_vinculacion)) {
				adb.olvidarVinculacion()
				avisar(getString(R.string.perm_vinculacion_olvidada))
			})
		}

		// Método 2: ordenador con adb
		contenido.addView(titulo(R.string.perm_m2_titulo))
		contenido.addView(texto(getString(R.string.perm_m2_intro)))
		contenido.addView(texto(ConcesionPermiso.comandoAdb(this)).apply {
			setTextIsSelectable(true)
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
			typeface = android.graphics.Typeface.MONOSPACE
		})
		contenido.addView(boton(getString(R.string.perm_copiar_comando)) { copiar(ConcesionPermiso.comandoAdb(this)) })
		contenido.addView(boton(getString(R.string.perm_compartir_comando)) { compartirComando() })

		// Método 3: root
		contenido.addView(titulo(R.string.perm_m3_titulo))
		contenido.addView(texto(getString(if (ConcesionPermiso.pareceHaberRoot()) R.string.perm_m3_intro_hay else R.string.perm_m3_intro)))
		contenido.addView(boton(getString(R.string.perm_conceder_root)) { concederConRoot() })

		// Método 4: Shizuku
		contenido.addView(titulo(R.string.perm_m4_titulo))
		contenido.addView(texto(getString(if (ConcesionPermiso.hayShizuku(this)) R.string.perm_m4_instalado else R.string.perm_m4_no_instalado, ConcesionPermiso.comandoShell(this))).apply {
			setTextIsSelectable(true)
		})
		contenido.addView(boton(getString(R.string.perm_copiar_comando_shell)) { copiar(ConcesionPermiso.comandoShell(this)) })

		// Después de concederlo
		despues = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		despues.addView(titulo(R.string.perm_despues_titulo))
		despues.addView(texto(getString(R.string.perm_despues_intro)))
		despues.addView(boton(getString(R.string.perm_saltar_pregunta)) {
			val ok = Servicios(this).saltarPreguntaAtajo()
			mensaje(getString(R.string.perm_titulo), getString(if (ok) R.string.perm_pregunta_saltada else R.string.perm_pregunta_no_saltada))
		})
		botonApagarDepuracion = boton(getString(R.string.perm_apagar_depuracion)) {
			val ok = ConcesionPermiso.apagarDepuracionInalambrica(this)
			mensaje(getString(R.string.perm_titulo), getString(if (ok) R.string.perm_depuracion_apagada else R.string.perm_depuracion_no_apagada))
			actualizar(anunciar = false)
		}
		despues.addView(botonApagarDepuracion)
		contenido.addView(despues)

		val desplazable = ScrollView(this).apply {
			isFocusable = false
			addView(contenido)
		}
		val raiz = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			addView(barra, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
			addView(desplazable, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
		}
		ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
			val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
			v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
			WindowInsetsCompat.CONSUMED
		}
		setContentView(raiz)
		configurarBarra(barra, getString(R.string.perm_titulo), conArriba = true)
		actualizar(anunciar = false)
	}

	override fun onResume() {
		super.onResume()
		// Al volver de los Ajustes o de la notificación, el estado puede haber cambiado
		actualizar(anunciar = true)
	}

	/** Pone el estado; si cambió y [anunciar], la región viva lo dice. */
	private fun actualizar(anunciar: Boolean) {
		val concedido = ConcesionPermiso.concedido(this)
		val texto = getString(if (concedido) R.string.perm_estado_concedido else R.string.perm_estado_no_concedido)
		if (!anunciar || ultimoEstado != concedido || estado.text.isEmpty()) estado.text = texto
		ultimoEstado = concedido
		despues.visibility = if (concedido) View.VISIBLE else View.GONE
		botonApagarDepuracion.visibility = if (concedido && ConcesionPermiso.depuracionInalambricaActiva(this) != false) View.VISIBLE else View.GONE
		if (concedido) NotificacionVinculacion.quitar(this)
	}

	private fun abrir(intent: Intent) {
		try {
			startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo abrir ${intent.action}", e)
			// Algunos fabricantes no tienen esa pantalla: los Ajustes generales
			try {
				startActivity(Intent(Settings.ACTION_SETTINGS))
				avisar(getString(R.string.perm_pantalla_no_existe))
			} catch (e2: Exception) {
				Registro.e(ETIQUETA, "No se pudieron abrir los Ajustes", e2)
				mensaje(getString(R.string.perm_titulo), getString(R.string.perm_pantalla_no_existe))
			}
		}
	}

	/** La notificación para escribir el código sin salir de los Ajustes (Android 13+ pide permiso de notificaciones). */
	private fun pedirNotificacion() {
		if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
			pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
		} else {
			mostrarNotificacion()
		}
	}

	private fun mostrarNotificacion() {
		if (NotificacionVinculacion.mostrar(this)) mensaje(getString(R.string.perm_titulo), getString(R.string.perm_notif_lista))
		else mensaje(getString(R.string.perm_titulo), getString(R.string.perm_notif_sin_permiso))
	}

	private fun buscarPuerto() {
		lifecycleScope.launch {
			try {
				val puerto = conEspera(getString(R.string.perm_buscar_puerto), getString(R.string.perm_buscando_puerto)) {
					if (Build.VERSION.SDK_INT >= 30) adb.buscarPuerto(vinculacion = true) else null
				}
				if (puerto != null) {
					campoPuerto.setText(puerto.toString())
					mensaje(getString(R.string.perm_titulo), getString(R.string.perm_puerto_encontrado, puerto))
				} else {
					mensaje(getString(R.string.perm_titulo), getString(R.string.perm_res_sin_puerto_vinculacion))
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "Falló la búsqueda del puerto", e)
				mensaje(getString(R.string.perm_titulo), getString(R.string.perm_res_fallo, e.message.orEmpty()))
			}
		}
	}

	private fun vincularYConceder() {
		val codigo = campoCodigo.text?.toString()?.filter { it.isDigit() }.orEmpty()
		if (codigo.isNotEmpty() && codigo.length != 6) {
			contenedorCodigo.error = getString(R.string.perm_codigo_invalido)
			campoCodigo.requestFocus()
			return
		}
		contenedorCodigo.error = null
		val puerto = campoPuerto.text?.toString()?.toIntOrNull()
		val puertoConexion = campoPuertoConexion.text?.toString()?.toIntOrNull()
		lifecycleScope.launch {
			try {
				val resultado = conEspera(getString(R.string.perm_vincular_conceder), getString(R.string.perm_vinculando)) {
					adb.vincularYConceder(codigo.ifEmpty { null }, puerto, puertoConexion)
				}
				actualizar(anunciar = false)
				mensaje(getString(R.string.perm_titulo), TextosPermiso.resultado(this@ActividadPermiso, resultado))
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "Falló la vinculación", e)
				mensaje(getString(R.string.perm_titulo), getString(R.string.perm_res_fallo, e.message.orEmpty()))
			}
		}
	}

	private fun concederConRoot() {
		lifecycleScope.launch {
			try {
				val resultado = conEspera(getString(R.string.perm_conceder_root), getString(R.string.perm_root_esperando)) {
					ConcesionPermiso.concederConRoot(this@ActividadPermiso)
				}
				actualizar(anunciar = false)
				mensaje(getString(R.string.perm_titulo), TextosPermiso.root(this@ActividadPermiso, resultado))
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "Falló la concesión con root", e)
				mensaje(getString(R.string.perm_titulo), getString(R.string.perm_res_fallo, e.message.orEmpty()))
			}
		}
	}

	private fun compartirComando() {
		val intent = Intent(Intent.ACTION_SEND)
			.setType("text/plain")
			.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.perm_compartir_asunto))
			.putExtra(Intent.EXTRA_TEXT, getString(R.string.perm_compartir_texto, ConcesionPermiso.comandoAdb(this)))
		try {
			startActivity(Intent.createChooser(intent, getString(R.string.perm_compartir_comando)))
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo compartir el comando", e)
			copiar(ConcesionPermiso.comandoAdb(this))
		}
	}

	// --- Piezas de la pantalla -----------------------------------------------------------

	private fun titulo(id: Int): TextView = texto(getString(id)).apply {
		setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
		setPadding(0, dp(this@ActividadPermiso, 24), 0, dp(this@ActividadPermiso, 4))
		encabezado()
	}

	private fun subtitulo(id: Int): TextView = texto(getString(id)).apply {
		setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
		setPadding(0, dp(this@ActividadPermiso, 16), 0, dp(this@ActividadPermiso, 4))
		encabezado()
	}

	private fun paso(numero: Int, id: Int): TextView = texto(getString(R.string.perm_paso, numero, getString(id)))

	private fun texto(t: String): TextView = TextView(this).apply {
		text = t
		setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
		setPadding(0, dp(this@ActividadPermiso, 4), 0, dp(this@ActividadPermiso, 4))
	}

	private fun boton(t: String, accion: () -> Unit): MaterialButton =
		MaterialButton(this).apply {
			text = t
			minHeight = dp(this@ActividadPermiso, 48)
			setOnClickListener { accion() }
			layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
				topMargin = dp(this@ActividadPermiso, 8)
			}
		}

	private fun anchoCompleto() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
		topMargin = dp(this@ActividadPermiso, 8)
	}

	private companion object {
		const val ETIQUETA = "ActividadPermiso"
	}
}
