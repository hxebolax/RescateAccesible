package com.xebolax.rescate.reparar

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.xebolax.rescate.R
import com.xebolax.rescate.accesibilidad.Accion
import com.xebolax.rescate.accesibilidad.accionesAccesibles
import com.xebolax.rescate.accesibilidad.avisar
import com.xebolax.rescate.accesibilidad.conEspera
import com.xebolax.rescate.accesibilidad.configurarBarra
import com.xebolax.rescate.accesibilidad.confirmar
import com.xebolax.rescate.accesibilidad.dp
import com.xebolax.rescate.accesibilidad.elegir
import com.xebolax.rescate.accesibilidad.etiquetaPulsar
import com.xebolax.rescate.accesibilidad.mensaje
import com.xebolax.rescate.accesibilidad.sinFocoPropio
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.nucleo.Registro
import com.xebolax.rescate.sistema.Servicios
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Copias de seguridad y reinstalación de TalkBack, la voz de Google y los demás
 * lectores y motores de voz instalados.
 *
 * Cada app es una fila de una sola parada que lo dice todo (estado primero);
 * al pulsarla salen sus acciones, que también están en el menú de acciones de
 * TalkBack de la fila. El resultado de copiar o instalar sale en esta pantalla.
 */
class ActividadCopias : AppCompatActivity() {
	private lateinit var copias: CopiasApk
	private lateinit var reinstalador: Reinstalador
	private val adaptador = Adaptador()
	/** Paquete para el que se eligió «Instalar desde un archivo…» (null = cualquiera). */
	private var paqueteArchivo: String? = null
	/** Lo que hay que hacer cuando el usuario vuelva de permitir instalar apps. */
	private var trasPermiso: (() -> Unit)? = null

	private val elegirArchivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) instalarArchivo(uri, paqueteArchivo)
	}

	private val permisoInstalar = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		val pendiente = trasPermiso
		trasPermiso = null
		if (reinstalador.puedeInstalar()) pendiente?.invoke()
		else mensaje(getString(R.string.copias_titulo), getString(R.string.instalar_sin_permiso))
	}

	/** Una fila: una app que se puede copiar o reinstalar. */
	private data class Fila(
		val paquete: String,
		val nombre: String,
		val instalada: Boolean,
		val versionInstalada: String?,
		val copia: CopiaApk?,
		val resumen: String,
	)

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(Idioma.envolver(base))
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		copias = CopiasApk(this)
		reinstalador = Reinstalador(this)
		val m = dp(this, 16)
		val barra = MaterialToolbar(this)
		val explicacion = TextView(this).apply {
			text = getString(R.string.copias_explicacion)
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
			setPadding(m, m / 2, m, m / 2)
		}
		val botones = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(m, 0, m, 0)
			addView(boton(getString(R.string.copias_copiar_esenciales)) { copiarEsenciales() })
			addView(boton(getString(R.string.copias_desde_archivo)) { pedirArchivo(null) })
		}
		val lista = RecyclerView(this).apply {
			layoutManager = LinearLayoutManager(this@ActividadCopias)
			adapter = adaptador
			sinFocoPropio()
			clipToPadding = false
			setPadding(m, m / 2, m, m)
		}
		val raiz = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			addView(barra, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
			addView(explicacion)
			addView(botones)
			addView(lista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
		}
		ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
			val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
			WindowInsetsCompat.CONSUMED
		}
		setContentView(raiz)
		configurarBarra(barra, getString(R.string.copias_titulo), conArriba = true)

		// El resultado del instalador se dice aquí mismo
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				Instalaciones.estado.collect { e ->
					when (e.fase) {
						FaseInstalacion.BIEN, FaseInstalacion.MAL -> {
							Instalaciones.limpiar()
							mensaje(getString(R.string.copias_titulo), e.mensaje.orEmpty())
							refrescar()
						}
						else -> Unit
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		refrescar()
	}

	private fun boton(texto: String, accion: () -> Unit) = MaterialButton(this).apply {
		text = texto
		minHeight = dp(this@ActividadCopias, 56)
		setOnClickListener { accion() }
	}

	private fun tamano(bytes: Long) = Formatter.formatShortFileSize(this, bytes)

	/** Vuelve a leer qué hay instalado y qué copias hay, fuera del hilo principal. */
	private fun refrescar() {
		lifecycleScope.launch {
			val filas = withContext(Dispatchers.IO) { leerFilas() }
			adaptador.poner(filas)
		}
	}

	private fun leerFilas(): List<Fila> {
		val servicios = Servicios(this)
		val paquetes = LinkedHashMap<String, String?>()
		copias.sugeridos().forEach { paquetes[it] = null }
		runCatching { servicios.lectores() }.getOrDefault(emptyList()).forEach { if (it.paquete !in paquetes) paquetes[it.paquete] = it.nombre }
		runCatching { servicios.motores() }.getOrDefault(emptyList()).forEach { if (it.paquete !in paquetes) paquetes[it.paquete] = it.nombre }
		val todas = copias.lista()
		todas.forEach { if (it.paquete !in paquetes) paquetes[it.paquete] = it.nombre }
		paquetes.remove(packageName)
		return paquetes.map { (paquete, nombreConocido) ->
			val info = copias.instalada(paquete)
			val copia = todas.filter { it.paquete == paquete }.maxByOrNull { it.versionCode }
			val nombre = info?.applicationInfo?.loadLabel(packageManager)?.toString() ?: nombreConocido ?: copia?.nombre ?: paquete
			Fila(paquete, nombre, info != null, info?.versionName, copia, resumen(paquete, nombre, info?.versionName, info != null, copia))
		}
	}

	/** Todo lo de la fila en una línea, el estado primero. */
	private fun resumen(paquete: String, nombre: String, version: String?, instalada: Boolean, copia: CopiaApk?): String {
		val partes = mutableListOf(nombre)
		partes += when {
			!instalada -> getString(R.string.copias_no_instalada)
			Tiendas.deshabilitada(this, paquete) -> getString(R.string.copias_deshabilitada, version.orEmpty())
			Tiendas.tieneActualizaciones(this, paquete) -> getString(R.string.copias_sistema_actualizada, version.orEmpty())
			Tiendas.esDeSistema(this, paquete) -> getString(R.string.copias_sistema, version.orEmpty())
			else -> getString(R.string.copias_instalada, version.orEmpty())
		}
		partes += if (copia != null) getString(R.string.copias_con_copia, copia.versionName, tamano(copia.tamano), copia.fecha)
		else getString(R.string.copias_sin_copia)
		return partes.joinToString(". ")
	}

	/** Las acciones de una fila, según su estado (nunca se ofrece algo ya hecho). */
	private fun acciones(f: Fila): List<Accion> {
		val lista = mutableListOf<Accion>()
		if (f.instalada) {
			val misma = f.copia != null && f.copia.versionName == f.versionInstalada
			lista += getString(if (misma) R.string.copias_rehacer else R.string.copias_hacer, f.nombre) to { hacerCopia(f.paquete, f.nombre) }
		}
		f.copia?.let { c ->
			lista += getString(R.string.copias_reinstalar, f.nombre, c.versionName) to { reinstalar(c) }
			lista += getString(R.string.copias_borrar, f.nombre) to { borrar(c) }
		}
		lista += getString(R.string.copias_archivo_de, f.nombre) to { pedirArchivo(f.paquete) }
		lista += getString(R.string.copias_tienda, f.nombre) to {
			if (!Tiendas.abrirEnTienda(this, f.paquete)) mensaje(getString(R.string.copias_titulo), getString(R.string.tienda_ninguna))
		}
		if (f.instalada) lista += getString(R.string.copias_informacion, f.nombre) to {
			if (!Tiendas.abrirInformacion(this, f.paquete)) mensaje(getString(R.string.copias_titulo), getString(R.string.tienda_ninguna))
		}
		return lista
	}

	private fun hacerCopia(paquete: String, nombre: String) {
		val necesario = copias.espacioNecesario(paquete)
		lifecycleScope.launch {
			try {
				val copia = conEspera(getString(R.string.copias_copiando_titulo), getString(R.string.copias_copiando, nombre, tamano(necesario))) {
					copias.hacer(paquete)
				}
				mensaje(getString(R.string.copias_titulo), getString(R.string.copias_hecha, copia.nombre, copia.versionName, tamano(copia.tamano)))
			} catch (e: CancellationException) {
				throw e
			} catch (e: CopiasApk.SinEspacioException) {
				mensaje(getString(R.string.copias_titulo), getString(R.string.copias_sin_espacio, tamano(e.necesario)))
			} catch (e: Exception) {
				Registro.e("Copias", "No se pudo copiar $paquete", e)
				mensaje(getString(R.string.copias_titulo), getString(R.string.copias_error, nombre))
			}
			refrescar()
		}
	}

	/** Copia TalkBack, la voz de Google y las variantes de Samsung que haya, una tras otra. */
	private fun copiarEsenciales() {
		val paquetes = copias.sugeridos().filter { copias.instalada(it) != null }
		if (paquetes.isEmpty()) {
			mensaje(getString(R.string.copias_titulo), getString(R.string.copias_ninguna_esencial))
			return
		}
		lifecycleScope.launch {
			val hechas = mutableListOf<String>()
			val fallidas = mutableListOf<String>()
			try {
				conEspera(getString(R.string.copias_copiando_titulo), getString(R.string.copias_copiando_varias)) {
					for (p in paquetes) {
						try {
							hechas += copias.hacer(p).nombre
						} catch (e: CancellationException) {
							throw e
						} catch (e: Exception) {
							Registro.e("Copias", "No se pudo copiar $p", e)
							fallidas += p
						}
					}
				}
				val texto = buildString {
					append(getString(R.string.copias_hechas_varias, hechas.joinToString(", ")))
					if (fallidas.isNotEmpty()) append(" ").append(getString(R.string.copias_fallidas_varias, fallidas.size))
				}
				mensaje(getString(R.string.copias_titulo), texto)
			} catch (e: CancellationException) {
				throw e
			}
			refrescar()
		}
	}

	private fun borrar(c: CopiaApk) {
		confirmar(getString(R.string.copias_borrar_pregunta, c.nombre, c.versionName), getString(R.string.copias_borrar, c.nombre)) {
			copias.borrar(c)
			avisar(getString(R.string.copias_borrada))
			refrescar()
		}
	}

	/** Pide el permiso de instalar si falta y luego hace [accion]. */
	private fun conPermiso(accion: () -> Unit) {
		if (reinstalador.puedeInstalar()) {
			accion()
			return
		}
		confirmar(getString(R.string.instalar_pedir_permiso), getString(R.string.instalar_abrir_permiso)) {
			trasPermiso = accion
			permisoInstalar.launch(reinstalador.intentPermiso())
		}
	}

	private fun reinstalar(c: CopiaApk) {
		reinstalador.problemaCon(c)?.let { problema ->
			mensaje(getString(R.string.copias_titulo), problema)
			return
		}
		confirmar(getString(R.string.copias_reinstalar_pregunta, c.nombre, c.versionName), getString(R.string.copias_reinstalar, c.nombre, c.versionName)) {
			conPermiso {
				lifecycleScope.launch {
					try {
						conEspera(getString(R.string.instalar_preparando_titulo), getString(R.string.instalar_preparando, c.nombre)) {
							reinstalador.desdeCopia(c)
						}
						avisar(getString(R.string.instalar_confirma))
					} catch (e: CancellationException) {
						throw e
					} catch (e: Exception) {
						Registro.e("Copias", "No se pudo reinstalar ${c.paquete}", e)
						mensaje(getString(R.string.copias_titulo), if (e is ReinstalacionException) e.message.orEmpty() else getString(R.string.instalar_error_general))
					}
				}
			}
		}
	}

	private fun pedirArchivo(paquete: String?) {
		paqueteArchivo = paquete
		try {
			elegirArchivo.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "application/octet-stream", "*/*"))
		} catch (e: Exception) {
			Registro.e("Copias", "No hay selector de archivos", e)
			mensaje(getString(R.string.copias_titulo), getString(R.string.instalar_sin_selector))
		}
	}

	private fun instalarArchivo(uri: Uri, paquete: String?) {
		conPermiso {
			lifecycleScope.launch {
				try {
					conEspera(getString(R.string.instalar_preparando_titulo), getString(R.string.instalar_leyendo_archivo)) {
						reinstalador.desdeArchivo(uri, paquete)
					}
					avisar(getString(R.string.instalar_confirma))
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					Registro.e("Copias", "No se pudo instalar desde el archivo", e)
					mensaje(getString(R.string.copias_titulo), if (e is ReinstalacionException) e.message.orEmpty() else getString(R.string.instalar_error_general))
				}
			}
		}
	}

	private inner class Adaptador : RecyclerView.Adapter<Adaptador.Celda>() {
		private var filas: List<Fila> = emptyList()

		init {
			setHasStableIds(true)
		}

		fun poner(nuevas: List<Fila>) {
			filas = nuevas
			@Suppress("NotifyDataSetChanged")
			notifyDataSetChanged()
		}

		inner class Celda(val tarjeta: MaterialCardView, val texto: TextView) : RecyclerView.ViewHolder(tarjeta)

		override fun getItemCount() = filas.size

		override fun getItemId(posicion: Int): Long = filas[posicion].paquete.hashCode().toLong()

		override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): Celda {
			val m = dp(padre.context, 16)
			val texto = TextView(padre.context).apply {
				setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
				setPadding(m, m, m, m)
				minHeight = dp(padre.context, 48)
				importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
			}
			val tarjeta = MaterialCardView(padre.context).apply {
				layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
					bottomMargin = dp(padre.context, 8)
				}
				isClickable = true
				isFocusable = true
				addView(texto)
			}
			return Celda(tarjeta, texto)
		}

		override fun onBindViewHolder(celda: Celda, posicion: Int) {
			val f = filas[posicion]
			celda.texto.text = f.resumen
			// Una sola parada: la tarjeta lo dice todo y el texto no se lee aparte
			celda.tarjeta.contentDescription = f.resumen
			val acciones = acciones(f)
			celda.tarjeta.setOnClickListener { elegir(f.nombre, acciones(f)) }
			celda.tarjeta.etiquetaPulsar(getString(R.string.copias_ver_acciones))
			celda.tarjeta.accionesAccesibles(acciones)
		}
	}
}
