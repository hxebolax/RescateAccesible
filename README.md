# Rescate Accesible

**Recupera tú solo el lector de pantalla, la voz y la accesibilidad de tu Android, sin ayuda de nadie y sin Internet.**

*Accessible Rescue: get your Android screen reader, voice and accessibility back on your own, with no sighted help and no Internet. [English summary below](#english-summary).*

Rescate Accesible es una app para personas ciegas o con baja visión que usan TalkBack u otro lector de pantalla. Cuando algo se rompe (TalkBack se apaga o se queda mudo, el motor de voz deja de hablar, el volumen se queda a cero, una actualización estropea la voz…), el móvil deja de hablar y, sin ver la pantalla, es casi imposible arreglarlo. Rescate Accesible tiene **su propia voz**, que no depende del sistema ni de Internet, y te guía hablando hasta dejarlo todo como estaba.

Está en **español e inglés** y funciona **sin Google Play Services**.

---

## Para quien la usa

### Qué te ofrece

- **Siempre a mano.** Mantén pulsadas a la vez las dos teclas de volumen unos segundos: se abre el menú de rescate, también con el móvil bloqueado y justo después de reiniciar. También desde el icono, su acceso directo, el mosaico «Rescate» de los ajustes rápidos o el aviso del guardián.
- **Un menú que habla solo.** Desliza el dedo por la pantalla para oír las opciones y levántalo para elegir. O usa las teclas de volumen: abajo, siguiente; arriba, anterior; mantener pulsada, elegir. Funciona aunque TalkBack esté activo pero mudo.
- **Voces propias sin Internet.** Pico (español, inglés de EE. UU. y británico) y eSpeak NG. Si una falla, habla la otra; si fallan las dos, mensajes grabados; y si no, pitidos y vibraciones.
- **Tu lector de pantalla, de vuelta.** Activa, desactiva o reinicia TalkBack u otro lector. Modo seguro: tres pulsaciones rápidas de volumen arriba apagan el lector mudo y el menú habla con su voz; si no eliges otro, tu lector vuelve solo. Modo eco: la voz de rescate lee lo que enfoca TalkBack cuando no se oye.
- **La voz del sistema, de vuelta.** Prueba de verdad cada motor de voz, elige el predeterminado, vuelve al último que funcionó o usa la propia voz de rescate como voz del sistema para que TalkBack hable aunque fallen los demás. Velocidad y tono normales con una opción.
- **Sonido.** Sube el volumen si estaba tan bajo que no se oiría y puede hablar por el altavoz del móvil si unos auriculares Bluetooth se llevan el sonido.
- **Reparar y reinstalar.** Guarda copias de TalkBack y de la voz de Google para reinstalarlas sin Internet ni tienda; quita actualizaciones que fallan, habilita apps deshabilitadas y borra cachés.
- **Diagnóstico automático.** Comprueba lector, voz, sonido, atajos, batería y copias, y te propone cómo arreglar cada cosa. Al abrir el rescate te dice lo más grave que encuentre.
- **Guía por los Ajustes.** Si la app no puede cambiar algo directamente, abre la pantalla de Ajustes adecuada, te dice lo que hay y lo pulsa por ti cuando lo confirmas con volumen arriba.
- **Consola del sistema automática.** Con el permiso avanzado y la depuración inalámbrica emparejada una vez (o con root), arregla también lo que una app normal no puede: audio mono, balance, habilitar apps, quitar actualizaciones, borrar cachés.
- **Siempre una vuelta al rescate.** Fuera del menú, un botón flotante «Volver al rescate»; en Android 11+, el menú también se puede poner en el botón o el gesto de accesibilidad.
- **Restaurar tu configuración** buena guardada.
- **Guardián (opcional).** Vigila tu lector y la voz del sistema y, si fallan, te avisa con la voz de rescate.
- **Pantalla para quien ve.** Brillo al máximo y, en letra grande, qué pasa y qué tocar, por si alguien te ayuda.

### Cómo empezar

1. Descarga el APK de la última versión publicada (las versiones se publican en las *Releases* de este repositorio y en el servidor de actualizaciones de la app) e instálalo.
2. Ábrelo con tu lector funcionando: un **asistente** te explica cada permiso y lo deja todo preparado (atajo, voz, copias, configuración guardada).
3. Practica una vez: mantén pulsadas las dos teclas de volumen y recorre el menú.

El **manual de uso** completo está dentro de la app y en [`app/src/main/assets/manual-es.html`](app/src/main/assets/manual-es.html).

### El permiso avanzado (opcional)

Android solo deja que una app active o desactive lectores y cambie el motor de voz con un permiso que se concede una vez (`WRITE_SECURE_SETTINGS`). Con él, el rescate hace esos cambios al momento; sin él, te guía por los Ajustes. Se puede conceder desde el propio móvil (depuración inalámbrica, Android 11 o posterior), con un ordenador o con root:

```
adb shell pm grant com.xebolax.rescate android.permission.WRITE_SECURE_SETTINGS
```

### Privacidad

Nada de lo que la app lee en la pantalla se guarda, se registra ni se envía. No hay publicidad ni analíticas. Solo se conecta a Internet para buscar actualizaciones cuando tú quieres. Más detalle en [`docs/privacidad_y_declaracion.md`](docs/privacidad_y_declaracion.md).

---

## Para desarrolladores

### Requisitos

- Windows (el entorno portátil está pensado para él; en otros sistemas basta con un Android SDK normal).
- Unos 10 GB libres para el entorno (JDK 17, Gradle 8.11.1, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1, emulador).

### Compilar

```
git clone https://github.com/hxebolax/RescateAccesible.git
cd RescateAccesible
compilar.bat
```

`compilar.bat` prepara la primera vez todo el entorno dentro de la carpeta `entorno\` (no instala nada en Windows) y ofrece un menú: compilar el APK, instalarlo en el móvil, abrir el emulador de pruebas, pasar las pruebas, **simular averías** en el emulador, regenerar las grabaciones de respaldo, publicar una actualización y cerrar todo lo abierto. Sin menú: `compilar.bat compilar | instalar | todo | emulador | probar | pruebas | simulacion | grabaciones | publicar | cerrar`.

En cualquier sistema con un Android SDK propio (con `ANDROID_HOME` apuntando a él; Gradle instala solo el NDK 27.2 y CMake 3.22.1 si faltan), con el *Gradle wrapper* y JDK 17:

```
./gradlew assembleDebug                         # APK de pruebas (com.xebolax.rescate.pruebas)
./gradlew assembleRelease                       # APK de publicación (firma en firma/firma.properties)
./gradlew assembleRelease -PincluirEspeak=false # sin eSpeak NG: todo el código queda con licencia Apache 2.0
./gradlew testDebugUnitTest                     # pruebas en el PC
```

La firma de publicación se crea la primera vez en `firma\` (no está en el repositorio).

### Pruebas

| Qué | Dónde | Cómo |
|---|---|---|
| Unitarias: lista de servicios, voz, grabaciones, diagnóstico, instantáneas | PC | `gradle testDebugUnitTest` |
| Voces nativas (Pico ×3, eSpeak), motor TTS propio usado por el sistema, grabaciones | Emulador | `herramientas\emulador_pruebas.ps1 -Que voz` |
| Recorrido de accesibilidad de todas las pantallas (Accessibility Test Framework) | Emulador | `-Que accesibilidad` |
| **Simulación de averías**: se rompe algo de verdad y se arregla manejando la app como una persona ciega | Emulador | `-Que simulacion` |

La simulación cambia servicios y ajustes de accesibilidad: **solo se ejecuta en el emulador**, nunca en un móvil. Sus resultados se ven en la app (Ajustes > Informe de pruebas y simulaciones) y en [`docs/informe_simulaciones.md`](docs/informe_simulaciones.md).

### Estructura

```
app/src/main/java/com/xebolax/rescate/
  voz/              Pico, eSpeak NG, cadena de voces de emergencia, grabaciones, pitidos, salida de audio
  servicio/         servicios de accesibilidad (atajo y guardián), lector de rescate, paneles flotantes
  rescate/          menú hablado, páginas, sesión, mosaico, avisos
  diagnostico/      diagnóstico, reglas, prueba de motores TTS, estado del sonido
  guia/             guía automática por los Ajustes del sistema
  reparar/          copias de APK, reinstalación, tiendas
  sistema/          servicios y ajustes de accesibilidad, configuración guardada
  permiso/          permiso avanzado (adb, depuración inalámbrica integrada, root, Shizuku)
  tts/              la voz de rescate como motor TTS del sistema
  actualizaciones/  actualizador desde el servidor propio
  ui/, nucleo/, accesibilidad/
app/src/main/cpp/   SVOX Pico y eSpeak NG con sus puentes JNI (CMake propio, sin descargas)
docs/               investigación, arquitectura, plan de pruebas, informe de simulaciones, limitaciones, privacidad
herramientas/       scripts del entorno, publicación, pruebas en el emulador e informe
agentes/            el agente «Android accesible» con el que se ha hecho la app
```

### El agente de accesibilidad

La app se ha hecho con el agente **Android accesible** para Claude Code (Material 3, TalkBack, baja visión, pruebas con el Accessibility Test Framework). Está en [`agentes/`](agentes/README.md) por si quieres usarlo en tus propias apps.

Más detalle en [`docs/arquitectura.md`](docs/arquitectura.md). Las limitaciones conocidas están en [`docs/limitaciones.md`](docs/limitaciones.md).

### Publicar una versión

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Añade la sección `== <versión>` con las novedades en `NOVEDADES.txt`.
3. `compilar.bat publicar`: deja el APK y `version.json` (versión, tamaño, SHA-256, novedades) en `dist\` y los copia a la carpeta del servidor de actualizaciones. La app comprueba tamaño, SHA-256, paquete, versión y firma antes de instalar.

### Traducir

Copia los `strings*.xml` de `app/src/main/res/values/` a `values-XX/`, tradúcelos y añade el idioma a `Idioma.DISPONIBLES` y a `arrays.xml`. El manual está en `assets/manual-es.html`.

---

## Créditos

- **[Shortcut Menu](https://github.com/aaron-gh/shortcut-menu)**, de **aaron-gh** (Apache 2.0). De él vienen el menú de atajos que habla solo, el modo seguro, el cambio de lector y de motor de voz con el permiso avanzado y el uso de Pico. Rescate Accesible incluye código modificado de Shortcut Menu; cada archivo derivado lo indica en su cabecera. **Todos los derechos sobre esa solución son de su autor.**
- **SVOX Pico** (SVOX AG, Android Open Source Project), Apache 2.0.
- **[eSpeak NG](https://github.com/espeak-ng/espeak-ng)** 1.52.0 (Reece H. Dunn y colaboradores), GPL-3.0-or-later.
- [libadb-android](https://github.com/MuntashirAkon/libadb-android) (Apache-2.0 o GPL-3.0), Bouncy Castle (MIT), spake2-android (LGPL-3.0), Conscrypt, AndroidX, Material Components y Kotlin (Apache 2.0).

## Licencia

Como incluye eSpeak NG, Rescate Accesible se distribuye con licencia **GPL-3.0-or-later** ([`LICENSE`](LICENSE)). El código de terceros conserva sus licencias ([`NOTICE`](NOTICE)). Compilado sin eSpeak NG (`-PincluirEspeak=false`) puede distribuirse con Apache 2.0.

---

## English summary

Accessible Rescue is an Android app for blind and low-vision people who use TalkBack or another screen reader. When something breaks (the screen reader turns off or goes silent, the speech engine stops working, the volume is at zero, an update breaks the voice), it lets you fix it **on your own**:

- Opens by holding both volume keys (also on the lock screen and right after a restart), from the icon, a quick settings tile or the guardian's alert.
- A self-speaking menu: drag your finger and lift it to choose, or use the volume keys (down next, up previous, hold to choose). Works even when TalkBack is on but silent.
- Built-in offline voices (Pico and eSpeak NG), recorded backup messages, and beeps and vibration as a last resort.
- Turn screen readers on, off or restart them; safe mode and echo mode; test and switch speech engines, or use the rescue voice as the system engine; raise the volume; reinstall TalkBack and Google's voice from saved backups; automatic diagnosis; guided Settings navigation; restore saved settings; optional guardian; a large-print screen for a sighted helper.
- Spanish and English. No Google Play Services needed. No data collection.

Build with `compilar.bat` (Windows, portable environment) or `./gradlew assembleDebug` with JDK 17 and Android SDK 35 (NDK 27.2.12479018). Licensed under GPL-3.0-or-later. Based in part on [Shortcut Menu](https://github.com/aaron-gh/shortcut-menu) by aaron-gh (Apache 2.0), to whom all rights to that solution belong.
