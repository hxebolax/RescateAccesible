package com.xebolax.rescate.rescate

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import com.xebolax.rescate.R
import com.xebolax.rescate.diagnostico.Diagnostico
import com.xebolax.rescate.diagnostico.EstadoSonido
import com.xebolax.rescate.diagnostico.Gravedad
import com.xebolax.rescate.diagnostico.Hallazgo
import com.xebolax.rescate.diagnostico.ProbadorTts
import com.xebolax.rescate.guia.AsistenteAjustes
import com.xebolax.rescate.nucleo.Idioma
import com.xebolax.rescate.reparar.PaginaReparar
import com.xebolax.rescate.servicio.LectorRescate
import com.xebolax.rescate.sistema.EntradaServicio
import com.xebolax.rescate.sistema.Instantanea
import com.xebolax.rescate.sistema.ListaServicios
import com.xebolax.rescate.sistema.MotorTts
import com.xebolax.rescate.tts.ServicioTtsPropio
import com.xebolax.rescate.ui.ActividadAyudante
import com.xebolax.rescate.voz.HablanteSistema
import com.xebolax.rescate.voz.Resultado
import com.xebolax.rescate.voz.Senal

/**
 * Las páginas del menú de rescate. Cada opción dice lo que hace y, si cambia
 * algo, dice después lo que de verdad ha pasado (comprobado, no supuesto).
 */
object PaginasRescate {

	fun principal(a: ActividadRescate): Pagina = Pagina(
		a.getString(R.string.app_name),
		a.getString(R.string.pag_principal_intro),
		{ act ->
			listOf(
				Opcion(act.getString(R.string.pag_diagnostico), act.getString(R.string.pag_diagnostico_ayuda)) { diagnosticar(it) },
				Opcion(act.getString(R.string.pag_lectores), act.getString(R.string.pag_lectores_ayuda)) { it.abrirPagina(lectores(it)) },
				Opcion(act.getString(R.string.pag_voz), act.getString(R.string.pag_voz_ayuda)) { it.abrirPagina(vozSistema(it)) },
				Opcion(act.getString(R.string.pag_sonido), act.getString(R.string.pag_sonido_ayuda)) { it.abrirPagina(sonido(it)) },
				Opcion(act.getString(R.string.pag_reparar), act.getString(R.string.pag_reparar_ayuda)) { it.abrirPagina(PaginaReparar.pagina(it)) },
				opcionLeerPantalla(act),
				Opcion(act.getString(R.string.pag_restaurar), act.getString(R.string.pag_restaurar_ayuda)) { it.abrirPagina(restaurar(it)) },
				Opcion(act.getString(R.string.pag_ajustes_accesibilidad), act.getString(R.string.pag_ajustes_accesibilidad_ayuda)) {
					it.abrirFuera(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), it.getString(R.string.pag_abriendo_accesibilidad))
				},
				Opcion(act.getString(R.string.pag_ayudante), act.getString(R.string.pag_ayudante_ayuda)) {
					it.abrirFuera(Intent(it, ActividadAyudante::class.java), it.getString(R.string.pag_ayudante_abierto))
				},
				Opcion(act.getString(R.string.pag_ajustes_rescate), act.getString(R.string.pag_ajustes_rescate_ayuda)) { it.abrirPagina(ajustesRescate(it)) },
				Opcion(act.getString(R.string.pag_ayuda), act.getString(R.string.pag_ayuda_ayuda)) { it.abrirPagina(ayuda(it)) },
			)
		},
		principal = true,
	)

	/** Lo más grave del diagnóstico rápido, para decirlo al abrir el rescate (o null). */
	suspend fun avisoRapido(a: ActividadRescate): String? {
		val hallazgo = Diagnostico(a).avisoRapido() ?: return null
		return a.getString(R.string.pag_aviso_detectado, hallazgo.titulo)
	}

	// --- Diagnóstico --------------------------------------------------------------------

	private fun diagnosticar(a: ActividadRescate) {
		a.trabajar(a.getString(R.string.pag_diagnosticando)) {
			val hallazgos = Diagnostico(a).ejecutar()
			val problemas = hallazgos.filter { it.gravedad == Gravedad.GRAVE || it.gravedad == Gravedad.AVISO }
			a.abrirPagina(paginaHallazgos(a, hallazgos), aviso = if (problemas.isEmpty()) a.getString(R.string.pag_sin_problemas)
			else a.resources.getQuantityString(R.plurals.pag_problemas, problemas.size, problemas.size))
			null
		}
	}

	private fun paginaHallazgos(a: ActividadRescate, hallazgos: List<Hallazgo>): Pagina {
		val orden = hallazgos.sortedBy { it.gravedad.ordinal }.filter { it.gravedad != Gravedad.BIEN }
		val bien = hallazgos.filter { it.gravedad == Gravedad.BIEN }
		return Pagina(a.getString(R.string.pag_diagnostico), a.getString(R.string.pag_hallazgos_intro), { act ->
			orden.map { h ->
				val gravedad = act.getString(when (h.gravedad) {
					Gravedad.GRAVE -> R.string.gravedad_grave
					Gravedad.AVISO -> R.string.gravedad_aviso
					else -> R.string.gravedad_info
				})
				Opcion("$gravedad: ${h.titulo}", h.detalle) { it.abrirPagina(paginaHallazgo(it, h), aviso = h.detalle) }
			} + if (bien.isEmpty()) emptyList() else listOf(
				Opcion(act.resources.getQuantityString(R.plurals.pag_lo_que_va_bien, bien.size, bien.size)) { it.decir(bien.joinToString(". ") { b -> b.titulo }) },
			)
		})
	}

	private fun paginaHallazgo(a: ActividadRescate, h: Hallazgo): Pagina = Pagina(h.titulo, a.getString(R.string.pag_hallazgo_intro), { act ->
		val permiso = act.servicios.puedeEscribir()
		listOf(Opcion(act.getString(R.string.pag_explicacion)) { it.decir(h.detalle) }) +
			h.reparaciones.map { r ->
				val texto = if (r.necesitaPermiso && !permiso) act.getString(R.string.pag_necesita_permiso, r.texto) else r.texto
				Opcion(texto, r.ayuda) { actividad ->
					if (r.necesitaPermiso && !permiso) {
						actividad.decir(actividad.getString(R.string.pag_permiso_explicacion))
					} else {
						actividad.trabajar(actividad.getString(R.string.rescate_un_momento)) { r.hacer(actividad) }
					}
				}
			}
	})

	// --- Lectores de pantalla ------------------------------------------------------------

	private fun estadoLector(a: ActividadRescate, e: EntradaServicio): String = a.getString(when {
		e.activo && e.enMarcha -> R.string.lector_activo
		e.activo -> R.string.lector_caido
		else -> R.string.lector_apagado
	}, e.nombre)

	private fun lectores(a: ActividadRescate): Pagina = Pagina(a.getString(R.string.pag_lectores), a.getString(R.string.pag_lectores_intro), { act ->
		val lectores = act.servicios.lectores()
		val otros = act.servicios.instalados().filter { !it.lector && it.activo }
		val lista = mutableListOf<Opcion>()
		if (lectores.isEmpty()) lista += Opcion(act.getString(R.string.pag_sin_lectores), act.getString(R.string.pag_sin_lectores_ayuda)) {
			it.abrirPagina(PaginaReparar.pagina(it))
		}
		lectores.forEach { e -> lista += Opcion(estadoLector(act, e)) { it.abrirPagina(lector(it, e)) } }
		lista += Opcion(act.getString(R.string.pag_modo_seguro), act.getString(R.string.pag_modo_seguro_ayuda)) { it.entrarModoSeguro() }
		if (otros.isNotEmpty()) lista += Opcion(act.resources.getQuantityString(R.plurals.pag_otros_servicios, otros.size, otros.size), act.getString(R.string.pag_otros_servicios_ayuda)) {
			it.abrirPagina(otrosServicios(it))
		}
		lista
	})

	private fun lector(a: ActividadRescate, e: EntradaServicio): Pagina = Pagina(e.nombre, estadoLector(a, e), { act ->
		val actual = act.servicios.lectores().firstOrNull { it.componente == e.componente } ?: e
		val lista = mutableListOf<Opcion>()
		if (!actual.activo || !actual.enMarcha) {
			lista += Opcion(act.getString(if (actual.activo) R.string.lector_reiniciar else R.string.lector_activar, actual.nombre)) { activar(it, actual) }
		}
		if (actual.activo) {
			if (actual.enMarcha) lista += Opcion(act.getString(R.string.lector_reiniciar, actual.nombre), act.getString(R.string.lector_reiniciar_ayuda)) { reiniciar(it, actual) }
			lista += Opcion(act.getString(R.string.lector_desactivar, actual.nombre)) { desactivar(it, actual) }
		}
		if (act.prefs.lectorHabitual != actual.componente) lista += Opcion(act.getString(R.string.lector_habitual, actual.nombre), act.getString(R.string.lector_habitual_ayuda)) {
			it.prefs.lectorHabitual = actual.componente
			it.refrescar(it.getString(R.string.lector_habitual_hecho, actual.nombre))
		}
		lista += Opcion(act.getString(R.string.lector_forzar_detencion, actual.nombre), act.getString(R.string.lector_forzar_detencion_ayuda)) {
			it.trabajar(it.getString(R.string.rescate_un_momento)) { AsistenteAjustes(it).forzarDetencion(actual.paquete, actual.nombre) }
		}
		lista += Opcion(act.getString(R.string.lector_reparar, actual.nombre)) { it.abrirPagina(PaginaReparar.pagina(it, actual.paquete)) }
		lista
	})

	/** Activa un lector: con el permiso, directamente (y cierra el rescate para que hable); sin él, guiando por los Ajustes. */
	private fun activar(a: ActividadRescate, e: EntradaServicio) {
		if (a.servicios.puedeEscribir()) {
			a.decir(a.getString(R.string.lector_activando, e.nombre)) {
				a.servicios.activar(e.componente) { ok ->
					if (ok && ListaServicios.contiene(a.servicios.activados(), e.componente)) {
						// El lector toma el relevo: el rescate se cierra para no hablar a la vez
						a.cerrar()
					} else {
						a.decir(a.getString(R.string.lector_no_activado, e.nombre))
					}
				}
			}
		} else {
			a.trabajar(a.getString(R.string.lector_guiando, e.nombre)) { AsistenteAjustes(a).activarServicio(e.componente, e.nombre) }
		}
	}

	private fun desactivar(a: ActividadRescate, e: EntradaServicio) {
		if (a.servicios.puedeEscribir()) {
			val ok = a.servicios.desactivar(e.componente)
			a.refrescar(a.getString(if (ok) R.string.lector_desactivado else R.string.lector_no_desactivado, e.nombre))
		} else {
			a.trabajar(a.getString(R.string.lector_guiando, e.nombre)) { AsistenteAjustes(a).desactivarServicio(e.componente, e.nombre) }
		}
	}

	private fun reiniciar(a: ActividadRescate, e: EntradaServicio) {
		if (a.servicios.puedeEscribir()) {
			a.decir(a.getString(R.string.lector_reiniciando, e.nombre)) {
				a.servicios.reiniciar(e.componente) { ok ->
					if (ok) a.cerrar() else a.decir(a.getString(R.string.lector_no_activado, e.nombre))
				}
			}
		} else {
			a.trabajar(a.getString(R.string.lector_guiando, e.nombre)) {
				val asistente = AsistenteAjustes(a)
				asistente.desactivarServicio(e.componente, e.nombre)
				asistente.activarServicio(e.componente, e.nombre)
			}
		}
	}

	private fun otrosServicios(a: ActividadRescate): Pagina = Pagina(a.getString(R.string.pag_otros_servicios_titulo), a.getString(R.string.pag_otros_servicios_ayuda), { act ->
		act.servicios.instalados().filter { !it.lector && it.activo }.map { e ->
			Opcion(act.getString(R.string.servicio_desactivar_otro, e.nombre)) { desactivar(it, e) }
		}
	})

	// --- Leer la pantalla con la voz de rescate ---------------------------------------

	private fun opcionLeerPantalla(a: ActividadRescate): Opcion {
		val servicio = SesionRescate.servicio
		val modo = servicio?.lector?.modo ?: LectorRescate.Modo.APAGADO
		if (modo != LectorRescate.Modo.APAGADO) {
			return Opcion(a.getString(R.string.pag_dejar_de_leer)) {
				SesionRescate.servicio?.ponerModoLector(LectorRescate.Modo.APAGADO)
				it.refrescar(it.getString(R.string.pag_lectura_apagada))
			}
		}
		return Opcion(a.getString(R.string.pag_leer_pantalla), a.getString(R.string.pag_leer_pantalla_ayuda)) { act ->
			val s = SesionRescate.servicio
			if (s == null) {
				act.decir(act.getString(R.string.pag_leer_sin_servicio))
				return@Opcion
			}
			if (act.servicios.otroLectorHablando()) {
				s.ponerModoLector(LectorRescate.Modo.ECO)
				act.refrescar(act.getString(R.string.rescate_eco_activado))
			} else {
				s.ponerModoLector(LectorRescate.Modo.PROPIO)
				s.filtrarTeclas(true)
				act.decir(act.getString(R.string.pag_lector_propio)) { act.salirAExplorar() }
			}
		}
	}

	// --- Voz del sistema ----------------------------------------------------------------

	private fun vozSistema(a: ActividadRescate): Pagina = Pagina(a.getString(R.string.pag_voz), a.getString(R.string.pag_voz_intro), { act ->
		val lista = mutableListOf<Opcion>()
		val predeterminado = act.servicios.motorPredeterminado()
		val nombrePred = act.servicios.motores().firstOrNull { it.predeterminado }?.nombre ?: predeterminado ?: act.getString(R.string.voz_desconocido)
		lista += Opcion(act.getString(R.string.voz_probar_predeterminado, nombrePred)) { probarMotor(it, null, nombrePred) }
		lista += Opcion(act.getString(R.string.voz_motores), act.getString(R.string.voz_motores_ayuda)) { it.abrirPagina(motores(it)) }
		if (predeterminado != act.packageName) {
			lista += Opcion(act.getString(R.string.voz_usar_propia), act.getString(R.string.voz_usar_propia_ayuda)) {
				ponerPredeterminado(it, MotorTts(it.packageName, it.getString(R.string.tts_nombre), false))
			}
		}
		val conocido = act.prefs.motorConocido
		if (conocido.isNotEmpty() && conocido != predeterminado) {
			val nombre = act.servicios.motores().firstOrNull { it.paquete == conocido }?.nombre ?: conocido
			lista += Opcion(act.getString(R.string.voz_volver_conocido, nombre), act.getString(R.string.voz_volver_conocido_ayuda)) {
				ponerPredeterminado(it, MotorTts(conocido, nombre, false))
			}
		}
		lista += Opcion(act.getString(R.string.voz_normalizar), act.getString(R.string.voz_normalizar_ayuda)) { normalizar(it) }
		lista += Opcion(act.getString(R.string.voz_abrir_ajustes)) { abrirAjustesVoz(it) }
		lista
	})

	private fun motores(a: ActividadRescate): Pagina = Pagina(a.getString(R.string.voz_motores), a.getString(R.string.voz_motores_intro), { act ->
		act.servicios.motores { n -> act.getString(R.string.voz_motor_numero, n) }.map { m ->
			Opcion(if (m.predeterminado) act.getString(R.string.voz_motor_predeterminado, m.nombre) else m.nombre) { it.abrirPagina(motor(it, m)) }
		}
	})

	private fun motor(a: ActividadRescate, m: MotorTts): Pagina = Pagina(m.nombre, a.getString(R.string.voz_motor_intro), { act ->
		val lista = mutableListOf(
			Opcion(act.getString(R.string.voz_probar, m.nombre)) { probarMotor(it, m.paquete, m.nombre) },
		)
		if (!m.predeterminado) lista += Opcion(act.getString(R.string.voz_hacer_predeterminado, m.nombre)) { ponerPredeterminado(it, m) }
		lista += Opcion(act.getString(R.string.voz_instalar_datos, m.nombre), act.getString(R.string.voz_instalar_datos_ayuda)) {
			val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(m.paquete)
			it.abrirFuera(intent, it.getString(R.string.voz_instalar_datos_abierto, m.nombre))
		}
		if (m.paquete != act.packageName) lista += Opcion(act.getString(R.string.voz_reparar, m.nombre)) { it.abrirPagina(PaginaReparar.pagina(it, m.paquete)) }
		lista
	})

	/**
	 * Prueba un motor de verdad: primero en silencio (arranca, idioma, produce
	 * sonido) y, si va bien, dice una frase con él para que el usuario la oiga.
	 */
	private fun probarMotor(a: ActividadRescate, paquete: String?, nombre: String) {
		a.trabajar(a.getString(R.string.voz_probando, nombre)) {
			val locale = Idioma.efectivo(a)
			val prueba = ProbadorTts(a).probar(paquete, locale)
			when {
				!prueba.arranca -> a.getString(R.string.voz_no_arranca, nombre)
				prueba.idioma != null && prueba.idioma < TextToSpeech.LANG_AVAILABLE -> a.getString(R.string.voz_sin_idioma, nombre)
				!prueba.habla -> a.getString(R.string.voz_no_habla, nombre)
				else -> {
					val real = prueba.motorReal ?: paquete
					if (real != null) a.prefs.motorConocido = real
					a.decir(a.getString(R.string.voz_funciona_oir, nombre)) {
						val sistema = HablanteSistema(a, paquete, a.voz.salida)
						sistema.hablar(a.getString(R.string.voz_frase_motor, nombre)) { r ->
							sistema.cerrar()
							a.decir(a.getString(if (r == Resultado.HABLADO) R.string.voz_oida else R.string.voz_no_habla, nombre))
						}
					}
					null
				}
			}
		}
	}

	/** Cambia el motor predeterminado y lo comprueba leyendo el ajuste; sin permiso, guía por los Ajustes. */
	private fun ponerPredeterminado(a: ActividadRescate, m: MotorTts) {
		if (a.servicios.puedeEscribir()) {
			val ok = a.servicios.ponerMotorPredeterminado(m.paquete)
			a.refrescar(a.getString(if (ok) R.string.voz_predeterminado_hecho else R.string.voz_predeterminado_fallo, m.nombre))
		} else {
			a.trabajar(a.getString(R.string.voz_guiando, m.nombre)) { AsistenteAjustes(a).elegirMotorPredeterminado(m.paquete, m.nombre) }
		}
	}

	private fun normalizar(a: ActividadRescate) {
		if (a.servicios.puedeEscribir()) {
			val ok = a.servicios.normalizarVozSistema()
			a.decir(a.getString(if (ok) R.string.voz_normalizada else R.string.voz_no_normalizada))
		} else {
			abrirAjustesVoz(a, a.getString(R.string.voz_normalizar_guia))
		}
	}

	fun abrirAjustesVoz(a: ActividadRescate, explicacion: String = a.getString(R.string.voz_ajustes_abiertos)) {
		val intent = Intent(ACCION_AJUSTES_TTS)
		if (intent.resolveActivity(a.packageManager) != null) a.abrirFuera(intent, explicacion)
		else a.abrirFuera(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), a.getString(R.string.voz_ajustes_alternativa))
	}

	// --- Sonido ---------------------------------------------------------------------------

	private fun sonido(a: ActividadRescate): Pagina = Pagina(a.getString(R.string.pag_sonido), a.getString(R.string.pag_sonido_intro), { act ->
		val altavoz = act.getString(when (act.prefs.altavoz) {
			"siempre" -> R.string.sonido_altavoz_siempre
			"nunca" -> R.string.sonido_altavoz_nunca
			else -> R.string.sonido_altavoz_auto
		})
		listOf(
			Opcion(act.getString(R.string.sonido_probar)) {
				it.voz.senal(Senal.CORRECTO)
				it.decir(it.getString(R.string.sonido_prueba_dicha))
			},
			Opcion(act.getString(R.string.sonido_estado)) { it.decir(EstadoSonido(it).resumen()) },
			Opcion(act.getString(R.string.sonido_subir)) { it.decir(it.getString(R.string.sonido_volumen, it.voz.salida.pasoVolumen(true))) },
			Opcion(act.getString(R.string.sonido_bajar)) { it.decir(it.getString(R.string.sonido_volumen, it.voz.salida.pasoVolumen(false))) },
			Opcion(altavoz, act.getString(R.string.sonido_altavoz_ayuda)) {
				it.prefs.altavoz = when (it.prefs.altavoz) {
					"auto" -> "siempre"
					"siempre" -> "nunca"
					else -> "auto"
				}
				it.refrescar(it.getString(R.string.sonido_altavoz_cambiado))
			},
			Opcion(act.getString(R.string.sonido_bluetooth), act.getString(R.string.sonido_bluetooth_ayuda)) {
				it.abrirFuera(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), it.getString(R.string.sonido_bluetooth_abierto))
			},
			Opcion(act.getString(R.string.sonido_ajustes)) {
				it.abrirFuera(Intent(Settings.ACTION_SOUND_SETTINGS), it.getString(R.string.sonido_ajustes_abiertos))
			},
		)
	})

	// --- Restaurar la configuración guardada -----------------------------------------------

	private fun restaurar(a: ActividadRescate): Pagina = Pagina(a.getString(R.string.pag_restaurar), a.getString(R.string.pag_restaurar_intro), { act ->
		val instantanea = Instantanea(act)
		if (!instantanea.existe()) return@Pagina listOf(Opcion(act.getString(R.string.restaurar_no_hay)) { it.decir(it.getString(R.string.restaurar_no_hay_ayuda)) })
		val diferencias = instantanea.diferencias()
		val lista = mutableListOf<Opcion>()
		lista += Opcion(act.resources.getQuantityString(R.plurals.restaurar_diferencias, diferencias.size, diferencias.size, instantanea.fechaTexto())) {
			it.decir(if (diferencias.isEmpty()) it.getString(R.string.restaurar_igual) else diferencias.joinToString(". "))
		}
		if (diferencias.isNotEmpty()) lista += Opcion(act.getString(R.string.restaurar_hacer), act.getString(R.string.restaurar_hacer_ayuda)) { actividad ->
			if (!actividad.servicios.puedeEscribir()) {
				actividad.decir(actividad.getString(R.string.pag_permiso_explicacion))
			} else {
				actividad.confirmar(actividad.getString(R.string.restaurar_pregunta), actividad.getString(R.string.restaurar_hacer)) { x ->
					x.trabajar(x.getString(R.string.rescate_un_momento)) { Instantanea(x).restaurar() }
				}
			}
		}
		lista
	})

	// --- Ajustes del rescate --------------------------------------------------------------

	private fun ajustesRescate(a: ActividadRescate): Pagina = Pagina(a.getString(R.string.pag_ajustes_rescate), a.getString(R.string.pag_ajustes_rescate_intro), { act ->
		val p = act.prefs
		listOf(
			Opcion(act.getString(R.string.ajr_mas_rapido, p.velocidad)) {
				it.prefs.velocidad = it.prefs.velocidad + 20
				it.refrescar(it.getString(R.string.ajr_velocidad, it.prefs.velocidad))
			},
			Opcion(act.getString(R.string.ajr_mas_lento, p.velocidad)) {
				it.prefs.velocidad = it.prefs.velocidad - 20
				it.refrescar(it.getString(R.string.ajr_velocidad, it.prefs.velocidad))
			},
			Opcion(act.getString(R.string.ajr_tono_agudo, p.tono)) {
				it.prefs.tono = it.prefs.tono + 10
				it.refrescar(it.getString(R.string.ajr_tono, it.prefs.tono))
			},
			Opcion(act.getString(R.string.ajr_tono_grave, p.tono)) {
				it.prefs.tono = it.prefs.tono - 10
				it.refrescar(it.getString(R.string.ajr_tono, it.prefs.tono))
			},
			Opcion(act.getString(if (p.decirPosicion) R.string.ajr_posicion_si else R.string.ajr_posicion_no)) {
				it.prefs.decirPosicion = !it.prefs.decirPosicion
				it.refrescar(it.getString(R.string.ajr_cambiado))
			},
			Opcion(act.getString(if (p.vibrar) R.string.ajr_vibrar_si else R.string.ajr_vibrar_no)) {
				it.prefs.vibrar = !it.prefs.vibrar
				it.refrescar(it.getString(R.string.ajr_cambiado))
			},
			Opcion(act.getString(R.string.ajr_idioma), act.getString(R.string.ajr_idioma_ayuda)) {
				val nuevo = if (Idioma.efectivo(it).language == "es") "en" else "es"
				Idioma.cambiar(it, nuevo)
				it.recreate()
			},
		)
	})

	// --- Ayuda ----------------------------------------------------------------------------

	private fun ayuda(a: ActividadRescate): Pagina = Pagina(a.getString(R.string.pag_ayuda), a.getString(R.string.pag_ayuda_intro), { act ->
		listOf(
			R.string.ayuda_moverse_titulo to R.string.ayuda_moverse,
			R.string.ayuda_volver_titulo to R.string.ayuda_volver,
			R.string.ayuda_seguro_titulo to R.string.ayuda_seguro,
			R.string.ayuda_eco_titulo to R.string.ayuda_eco,
			R.string.ayuda_gestos_titulo to R.string.ayuda_gestos,
			R.string.ayuda_permiso_titulo to R.string.ayuda_permiso,
			R.string.ayuda_menu_titulo to R.string.ayuda_menu,
		).map { (titulo, texto) -> Opcion(act.getString(titulo)) { it.decir(it.getString(texto)) } } +
			Opcion(act.getString(R.string.ayuda_opciones)) { actividad ->
				actividad.decir(principal(actividad).opciones(actividad).joinToString(". ") { o: Opcion -> o.texto + ": " + o.ayuda })
			}
	})

	/** La acción del sistema para los ajustes de síntesis de voz (no está en el SDK). */
	const val ACCION_AJUSTES_TTS = "com.android.settings.TTS_SETTINGS"

	/** El componente del motor de voz propio, por si hace falta nombrarlo. */
	fun motorPropio(a: ActividadRescate): ComponentName = ComponentName(a, ServicioTtsPropio::class.java)
}
