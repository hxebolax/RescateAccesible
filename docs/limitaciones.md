# Limitaciones técnicas y mejoras futuras

Revisado tras la investigación de `docs/investigacion/soluciones_limitaciones.md` (AOSP, documentación oficial, Shizuku, LADB, libadb-android, AutoStarter, dontkillmyapp). Cada limitación dice ahora qué hace la app para evitarla o reducirla y qué sigue siendo imposible.

## Resueltas o reducidas en la app

| Limitación | Qué hace ahora la app | Necesita |
|---|---|---|
| Activar un lector apagado o cambiar el motor de voz | Al momento con el permiso avanzado; sin él, la **guía por los Ajustes** lo hace pulsando por el usuario tras confirmar (probado en la simulación S04) | Permiso avanzado o nada |
| Asignar el atajo de volumen a la app | Con el permiso avanzado lo asigna ella sola, solo para la app, en la pantalla de bloqueo y con la pregunta de la primera vez contestada («Asignarlo yo» en el asistente y en el diagnóstico) | Permiso avanzado (Android 8+) |
| Atajo compartido o quitado | El diagnóstico lo detecta y lo arregla; además el menú puede ponerse en el **botón o el gesto de accesibilidad** como destino de atajo propio, sin activar ningún servicio | Android 11+ para el destino de atajo |
| Volver al rescate fuera del menú | Botón flotante «Volver al rescate», gesto del lector de rescate (izquierda y luego derecha) y, donde llega, tres pulsaciones de volumen arriba | Nada |
| Teclas de volumen que el filtro de accesibilidad no entrega (comprobado en el emulador) | Las confirmaciones y el aviso del guardián son **paneles flotantes con el foco**, que reciben las teclas como cualquier ventana y se pueden tocar | Nada |
| Audio mono y balance | Se cambian con la **consola del sistema** de la app (`settings put system master_mono 0`, `master_balance 0.0`) | Permiso avanzado + depuración inalámbrica emparejada (o root) |
| Habilitar apps, quitar actualizaciones, borrar caché, forzar la detención | Por consola directamente (`pm enable`, `cmd package uninstall-system-updates`, `pm clear --cache-only`, `am force-stop`) y comprobando el estado real; al forzar la detención se reactivan los servicios de accesibilidad que Android apaga | Consola (o guía por los Ajustes si no la hay) |
| Consola sin intervención del usuario | Con el permiso avanzado la app enciende un momento las opciones de desarrollador y la depuración inalámbrica, se conecta a sí misma con la clave emparejada y las vuelve a apagar | Android 11+, wifi, red marcada «Permitir siempre», emparejada una vez |
| TalkBack «activado pero caído» | Se detecta (activado en los ajustes pero no conectado) y se reinicia en dos escrituras o por la guía | Nada / permiso avanzado |
| Limpiadores y ahorro de batería | Diagnóstico de optimización de batería y apertura de la pantalla de inicio automático del fabricante (tabla de AutoStarter) | Lo confirma el usuario |
| Ajustes distintos por fabricante | La guía busca primero los textos del propio paquete de Ajustes del móvil (en su idioma), luego listas de respaldo; espera a la ventana correcta, reintenta y toca con un gesto si la pulsación por accesibilidad falla; solo pregunta con un panel propio | Nada |
| Ningún motor de voz funciona | Voces propias Pico y eSpeak NG, grabaciones de respaldo de las dos en español e inglés, pitidos y vibraciones; la app es además motor de voz del sistema y habla en el idioma de la app si le piden uno que no tiene | Nada |

## Lo que sigue sin poder hacerse

- **Que un limpiador no apague el guardián**: forzar la detención de una app quita sus servicios de accesibilidad de los activados (AOSP `onPackagesForceStoppedLocked`). La app lo explica y lo detecta, pero no puede impedirlo.
- **Contestar por el usuario** al diálogo de «control total» con PIN, al de «ajustes restringidos» (Android 13+, apps instaladas fuera de una tienda) o al de instalación de paquetes: los lee y los pulsa tras confirmar, pero si Android pide PIN o huella, lo pone el usuario.
- **Marcar la red wifi como permitida** para la depuración: lo hace el usuario una vez.
- **Botón de encendido**: ninguna app puede asignarse la triple pulsación del botón de encendido; solo el usuario en los ajustes de su fabricante.
- **Bajar a una versión anterior** de una app no depurable sin perder datos: necesita root.
- **Ajustes internos de TalkBack** (su velocidad propia, la verbosidad): no son accesibles sin root. «TalkBack suspendido» ya no existe desde TalkBack 13.
- **Idioma del sistema**: cambiarlo por consola funciona de forma desigual según la versión; la app guía hasta el ajuste.
- **Antes de desbloquear** tras reiniciar, muchas pantallas de Ajustes no se abren (el menú, las voces y el lector de rescate sí funcionan).

## Pendiente de comprobar en móviles reales

- La depuración inalámbrica integrada (emparejar, conceder el permiso y la consola automática): el emulador de pruebas no tiene wifi. Probar en el OnePlus 6 (Android 11) y en Android 12, 13, 14 y 15.
- La guía por los Ajustes en Samsung One UI, MIUI/HyperOS, OxygenOS, ColorOS y EMUI (probada en Android 15 puro).
- Que el filtro de teclas de accesibilidad entregue las teclas de volumen (en el emulador no llega ninguna tecla; los paneles con foco lo sustituyen).

## Mejoras futuras

- Voz de alta calidad opcional con sherpa-onnx (Apache 2.0) y voces sin GPL (Coqui CSS10 español, BSD-3; o Piper, que exige un fonetizador GPL), descargable con conexión y nunca durante una emergencia.
- Más idiomas en la interfaz y en la voz.
- Integración con Shizuku por su API de servicio de usuario.
- Perfiles de guía por fabricante, alimentados por las pruebas en móviles reales.
