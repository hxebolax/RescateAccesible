# Plan de pruebas

## 1. Pruebas automáticas

| Tipo | Dónde | Qué cubre | Cómo |
|---|---|---|---|
| Unitarias (JVM) | PC | Lista de servicios y modo seguro (de Shortcut Menu), nombres, pulsaciones rápidas, voz Pico por idioma, marcado y frases, µ-law, remuestreo, pitidos, filas del menú, versiones, fuentes de actualización (servidor propio y GitHub), reglas de diagnóstico, comparación de instantáneas, textos de pantalla | `compilar.bat` opción 7 (`testDebugUnitTest`) |
| Voces | Emulador | Pico con es-ES, en-US y en-GB; eSpeak NG en es, en-US y en-GB; el motor propio usado por el sistema (es y en); grabaciones | `herramientas\emulador_pruebas.ps1 -Que voz` |
| Accesibilidad | Emulador | Recorrido de todas las pantallas y del menú de rescate con el Accessibility Test Framework (nombres, objetivos de 48 dp, contraste, duplicados) | `-Que accesibilidad` |
| Simulación de averías | Emulador | 15 escenarios: se rompe algo de verdad, se arregla con la app como una persona ciega y se comprueba el estado real | `compilar.bat` opción 8; informe en la app (Ajustes) y en `docs/informe_simulaciones.md` |

La simulación **nunca** se pasa en un móvil real: cambia servicios y ajustes de accesibilidad.

## 2. Matriz de pruebas manuales (móvil real, con TalkBack)

Cada caso se hace con la pantalla tapada o el «oscurecer pantalla» de TalkBack, solo con el oído.

| Caso | Pasos | Resultado esperado |
|---|---|---|
| Preparación completa | Asistente de principio a fin | Todo «Listo»; el atajo abre el rescate |
| Primer uso del atajo | Mantener las dos teclas con TalkBack activo | Android pregunta; TalkBack la lee; tras aceptar se abre el menú |
| Atajo con el móvil bloqueado | Bloquear, mantener las dos teclas | El menú se abre sobre la pantalla de bloqueo y habla |
| Tras reiniciar sin desbloquear | Reiniciar y usar el atajo | El menú habla (arranque directo) |
| Motor TTS roto | Elegir un motor sin voces; TalkBack calla | Rescate → Voz del sistema → Usar la voz de rescate: TalkBack habla |
| Sin Internet | Modo avión | Todo el rescate funciona |
| Bluetooth | Auriculares conectados y apagados | La voz de rescate sale por el altavoz (automático) |
| Volumen de accesibilidad a 0 | Bajarlo con TalkBack hablando | El rescate lo sube y lo dice |
| Idioma no instalado | Poner el móvil en un idioma sin voz | El rescate sigue en su idioma; el diagnóstico lo dice |
| TalkBack suspendido | Gesto de suspender TalkBack | Diagnóstico y Lectores lo detectan; reiniciarlo lo recupera |
| Proceso cerrado | Forzar la detención de esta app | El atajo sigue abriendo el rescate (el guardián habrá que reactivarlo) |
| Servicio desactivado | Quitar el atajo | El diagnóstico (desde la app) lo dice; icono y mosaico siguen |
| Dos lectores | Activar TalkBack de Samsung y de Google | El menú permite dejar uno |
| Permiso avanzado por wifi | Permiso avanzado → depuración inalámbrica | El permiso queda concedido |
| Reinstalar desde copia | Copia → Reparar → Reinstalar | Android pide confirmar; la guía pulsa Instalar con volumen arriba |
| Desinstalar actualizaciones | Reparar → Desinstalar actualizaciones | Vuelve la versión de fábrica |
| Guardián | Activar guardián; apagar TalkBack | Aviso hablado; tres veces volumen arriba abre el rescate |
| Sin Play Services | Móvil Huawei o ROM sin GMS | Todo funciona; la tienda no se ofrece o se ofrece F-Droid |

## 3. Versiones y fabricantes

| Android | Qué mirar |
|---|---|
| 7.x (API 24-25) | Sin atajo; icono, mosaico y guardián; volumen multimedia |
| 8.0-10 (API 26-29) | Atajo con un solo servicio; volumen de accesibilidad; gestos del lector propio |
| 11-12 (API 30-32) | Atajo con varios destinos; pregunta del atajo hablada; depuración inalámbrica |
| 13 (API 33) | Idioma por app; permiso de notificaciones |
| 14 (API 34) | Mosaico con PendingIntent; propiedad de actualizaciones |
| 15-16 (API 35-36) | Borde a borde; páginas de 16 KB |

Fabricantes: Google Pixel (Android puro), Samsung (One UI, TalkBack y TTS propios), Xiaomi (MIUI/HyperOS, inicio automático), OnePlus (OxygenOS), OPPO/realme (ColorOS), vivo, Huawei (sin GMS), Motorola.

## 4. Lo comprobado hasta ahora (25 de octubre de 2026, emulador Pixel 6, Android 15, TalkBack 15)

| Prueba | Resultado |
|---|---|
| Unitarias (6 clases) | 42 de 42 superadas |
| Voces nativas, motor propio del sistema y grabaciones | 5 de 5 superadas |
| Recorrido de accesibilidad de todas las pantallas (ATF) | sin errores ni avisos |
| Simulación de averías | 15 de 15 escenarios, 61 de 61 comprobaciones |

Detalle de cada escenario (qué se rompió, teclas, toques y gestos, cómo se recuperó y qué se comprobó) en `docs/informe_simulaciones.md` y en la app (Ajustes > Informe de pruebas y simulaciones).

Pendiente en móviles reales: la depuración inalámbrica integrada (el emulador no tiene wifi), la guía por los Ajustes de cada fabricante y oír de verdad las voces.
