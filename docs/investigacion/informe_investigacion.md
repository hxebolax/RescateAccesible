# Informe de investigación — Rescate Accesible

Fecha de la investigación: 25 de octubre de 2026.
Objetivo del producto: que una persona ciega que configuró la app con antelación pueda recuperar por sí sola el lector de pantalla (TalkBack u otro), el motor de síntesis de voz (TTS) y el resto de la accesibilidad, sin ayuda de una persona que vea.

Convenciones del informe:

- **[VERIFICADO]**: comprobado en el código fuente de AOSP (rama `main` salvo que se diga otra), en documentación oficial o descargando metadatos del archivo.
- **[FUENTE SECUNDARIA]**: dicho por foros, blogs o guías de terceros; no comprobado en código.
- **[SIN VERIFICAR]**: dato conocido o probable, pero que no he podido confirmar. Hay que probarlo en dispositivo.
- «WSS» = permiso `android.permission.WRITE_SECURE_SETTINGS`.
- «Shell ADB» = comandos ejecutados con el UID `shell` (2000) a través de un cliente ADB integrado (depuración inalámbrica) o de Shizuku.

## 0. Metodología y limitaciones

- El código de AOSP se ha leído directamente desde `android.googlesource.com` (formato `?format=TEXT`). Los ficheros consultados se citan en cada apartado.
- **Reddit no ha sido accesible**: el buscador lo rechaza para este agente y la API JSON devuelve «Blocked». No hay citas de r/Blind, r/TalkBack ni r/Android. Conviene que una persona revise esos subforos a mano.
- **groups.io** (blindandroidusers, androidaccessibility, Eyes-Free) devuelve HTTP 402 en la búsqueda. Solo se han podido usar hilos sueltos que aparecían en buscadores.
- **AppleVis** devuelve HTTP 403 al descargar hilos. Solo se citan títulos y extractos del buscador.
- La frecuencia de cada problema se da de forma **cualitativa** (cuántas fuentes independientes lo mencionan). No hay datos cuantitativos públicos.

---

## 1. Problemas que reportan las personas ciegas usuarias de Android

### 1.0 Tabla resumen

| # | Problema | Evidencia | Solución sin WSS | Solución con WSS | Solución con shell ADB |
|---|---|---|---|---|---|
| 1 | TalkBack se desactiva (por error, por el atajo, por el sistema) | Alta | Atajo de volumen asignado a nuestro servicio; navegación guiada en Ajustes | Escribir `enabled_accessibility_services` | Igual |
| 2 | Forzar detención o «limpiar todo» de OEM desactiva el servicio | Media + código AOSP | Pedir exenciones de batería; detectar y avisar | Reactivar al momento | `cmd deviceidle whitelist +pkg`, `appops` |
| 3 | TalkBack «no funciona» (se colgó) | Media | Navegación guiada: apagar y encender | Quitar y volver a poner en la lista | Igual; reinstalar |
| 4 | Motor TTS roto, sin voz del idioma, deshabilitado o desinstalado | Alta | Nuestra voz embebida; pantalla guiada de TTS | `tts_default_synth` = nuestro motor | `pm enable`, `pm install-existing`, reinstalar copia |
| 5 | Actualización de TalkBack o del TTS de Google que rompe la voz | Media | Cambiar a nuestro motor | Igual | Reinstalar la copia guardada; `pm uninstall-system-updates` |
| 6 | Volumen de accesibilidad o multimedia muy bajo | Media | Nuestro servicio sube `STREAM_ACCESSIBILITY` | — | `cmd media_session volume` |
| 7 | El audio sale por un auricular Bluetooth lejano o apagado | Media | Nuestra voz con `setPreferredDevice(altavoz)` | — | Apagar Bluetooth |
| 8 | Silenciar todos los sonidos, audio mono o balance extremo | Media | Navegación guiada | — (son claves `System` ocultas) | `settings put system master_mono 0` / `master_balance 0` |
| 9 | Velocidad o tono de habla extremos | Media | Navegación guiada | `tts_default_rate`/`pitch` (no afecta a la velocidad propia de TalkBack) | Igual |
| 10 | Idioma del sistema cambiado por error | Media | `ACTION_LOCALE_SETTINGS` + navegación guiada | — | Solo vía API ocultas [SIN VERIFICAR] |
| 11 | Atajo con varias funciones: menú de elección que no se lee | Media (app «Shortcut Menu») | Nuestro servicio lee el menú si está activo | Dejar nuestro servicio como único destino | Igual |
| 12 | Atajo desactivado en la pantalla de bloqueo | Baja/media | — | `accessibility_shortcut_on_lock_screen=1` | Igual |
| 13 | Dos lectores a la vez (Samsung + Google, Jieshuo + TalkBack) | Alta | Navegación guiada | Dejar uno solo en la lista | Igual |
| 14 | Acceso con interruptores o Control de interacción bloquean el tacto | Media | Navegación guiada | Quitar servicio o clave | Igual |
| 15 | Ampliación activada por error | Baja/media | `MagnificationController.reset()` | Claves de ampliación | Igual |
| 16 | Pantalla oculta o atenuada (para quien ayuda viendo) | Media | Volver a darle brillo desde nuestro servicio | `reduce_bright_colors_activated=0` [SIN VERIFICAR] | `settings put system screen_brightness` |
| 17 | Tras reiniciar, no habla hasta desbloquear (PIN sin voz) | Media | Servicio y motor TTS `directBootAware` | — | — |
| 18 | En la configuración inicial no habla (Samsung, idioma sin voz) | Media | Fuera de alcance (la app aún no está instalada) | — | — |
| 19 | Retrasos o cortes de voz con el TTS de Google | Media | Cambiar de motor | `tts_default_synth` | Igual |
| 20 | No molestar | Baja | No afecta a `USAGE_ASSISTANCE_ACCESSIBILITY` en AOSP | — | — |
| 21 | Ocultar o desinstalar TalkBack o el TTS con un «debloater» | Baja/media | Reinstalar la copia con PackageInstaller | — | `pm install-existing`, `pm enable` |

Las fichas siguientes dan el detalle, las fuentes y la causa técnica.

### 1.1 TalkBack desactivado (por error, por el atajo o por el sistema)

- **Descripción**: la persona apaga TalkBack sin querer y se queda sin voz. Las vías típicas son el atajo de las dos teclas de volumen, que en TalkBack *alterna* (enciende y apaga), o un toque mal hecho en Ajustes. Sin un atajo activo, no puede volver a encenderlo.
- **Evidencia**:
  - En el grupo «Android Accesible» (español), una persona apagó TalkBack por error y el atajo de volumen no le funcionaba. La respuesta fue que sin atajo no se puede, salvo con ADB y `settings put secure enabled_accessibility_services`: <https://groups.google.com/g/android_accesible/c/atvT267sFaY> (mayo de 2021).
  - Google recomienda pulsar las dos teclas de volumen unos segundos y desactivar el atajo después, para que no vuelva a activarse por accidente: <https://support.google.com/accessibility/android/answer/14252950?hl=en>
  - Muchas guías de fabricantes repiten el atajo (Xiaomi, Redmi, Samsung), lo que confirma que es la vía de entrada y salida más usada.
- **Causa técnica [VERIFICADO]**: TalkBack declara `flagRequestShortcutWarningDialogSpokenFeedback`, pero **no** `flagRequestAccessibilityButton` (`talkback/src/main/res/xml/accessibilityservice.xml` en <https://github.com/google/talkback>). Por eso el atajo de hardware lo **alterna**. Lo explica `performAccessibilityShortcutTargetService` en `AccessibilityManagerService.java`: «4) a) Turns on / off the accessibility service, if service does not request accessibility button».
- **Soluciones**:
  - **Sin WSS**: asignar el atajo de volumen a *nuestro* servicio, como único destino, y diseñarlo para que el atajo solo pueda *encenderlo* (ver 3.1). Una vez activo, nuestro servicio habla con su propia voz y lleva a la persona hasta Ajustes > Accesibilidad > TalkBack. Allí pulsa el interruptor y el botón «Permitir» con `performAction(ACTION_CLICK)` (ver 3.6).
  - **Con WSS**: añadir `com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService` a `Settings.Secure.enabled_accessibility_services`. El sistema actualiza `accessibility_enabled` por sí solo (`updateAccessibilityEnabledSettingLocked`) y concede la exploración táctil sin diálogo (`canRequestAndRequestsTouchExplorationLocked`: desde JB-MR2 basta la capacidad declarada) [VERIFICADO].
  - **Con shell ADB**: lo mismo, con `settings put secure enabled_accessibility_services ...`.

### 1.2 «Forzar detención» o el limpiador del fabricante desactiva el servicio de accesibilidad

- **Descripción**: el servicio de accesibilidad (TalkBack o el nuestro) desaparece de la lista de activos después de «limpiar todo» en recientes, de un optimizador del fabricante o de «Forzar detención».
- **Evidencia [FUENTE SECUNDARIA]**:
  - En un hilo de la comunidad de Sophos, el ajuste de accesibilidad se apagaba justo cuando la app salía de memoria: <https://community.sophos.com/sophos-mobile/f/discussions/84224/web-filtering-sophos-accessibility-service-repeatedly-turned-off>
  - En la comunidad de Samsung, un usuario cuenta que se pierden permisos de accesibilidad y otro responde que forzar la detención los apaga: <https://r1.community.samsung.com/t5/galaxy-s/accessibility-keeps-turning-itself-off/td-p/22542025>
  - AppBlock lo documenta para su propia app: <https://appblock.app/my-accessibility-keeps-turning-off-what-can-i-do>
  - Sobre la gestión agresiva de batería de cada fabricante: <https://dontkillmyapp.com/>
- **Causa técnica [VERIFICADO]**: `AccessibilityManagerService.onPackagesForceStoppedLocked()` está documentado como «Handles a package or packages being force stopped. Will disable any relevant services». El método quita los componentes del paquete detenido de `mEnabledServices`, persiste `ENABLED_ACCESSIBILITY_SERVICES` y, si el servicio pedía el botón de accesibilidad, también quita sus destinos del botón. Es decir: **cualquier «force-stop» desactiva de forma permanente los servicios de accesibilidad de ese paquete**. Los limpiadores de los fabricantes suelen usar force-stop.
- **Soluciones**:
  - **Sin WSS**: pedir `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) y llevar a la persona a las pantallas de inicio automático del fabricante (ver 5.3). Detectar el fallo cuando se reactive nuestro servicio con el atajo.
  - **Con WSS**: con un `ContentObserver` sobre `enabled_accessibility_services`, volver a añadir TalkBack. Ojo: si el que se detiene es nuestro propio paquete, no queda nada vivo para observar. Ahí el atajo de volumen es la única red de seguridad.
  - **Con shell ADB**: `cmd deviceidle whitelist +<pkg>` y `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow` [SIN VERIFICAR en cada fabricante].

### 1.3 TalkBack «No funciona. Toca para ver información» (servicio colgado)

- **Descripción**: el servicio sigue marcado como activo, pero no responde. Ajustes lo muestra como «no funciona».
- **Evidencia**: hay reportes generales de TalkBack que «se detiene» en la comunidad de Samsung tras pasar a Android 15 ([Samsung talkback problems – InnovationLab](https://r2.community.samsung.com/t5/Tech-Talk/Samsung-talkback-problems-InnovationLab/td-p/13540013), [Accessibility issue for such devices](https://r2.community.samsung.com/t5/Galaxy-A/Accessibility-issue-for-such-devices/td-p/20342287)) [FUENTE SECUNDARIA].
- **Causa técnica [VERIFICADO]**: en `AccessibilityServiceConnection.binderDied()` el sistema marca `mAccessibilityServiceInfo.crashed = true` y mete el componente en `mCrashedServices`. `updateServicesLocked` se salta los servicios de esa lista («Skip the component since it may be in process or crashed»). La lista solo se limpia cuando se actualiza o cambia el paquete (`onPackageUpdateFinished` / `onSomePackagesChanged`), cuando se elimina o cuando se fuerza su detención.
- **Soluciones**:
  - **Sin WSS**: navegación guiada para apagar y volver a encender TalkBack desde Ajustes.
  - **Con WSS**: quitar el componente de `enabled_accessibility_services`, esperar y volver a añadirlo [SIN VERIFICAR que basta para limpiar `mCrashedServices`; el código la limpia en `onPackagesForceStoppedLocked`, `onPackageRemovedLocked` y en las actualizaciones de paquete].
  - **Con shell ADB**: `am force-stop com.google.android.marvin.talkback`, que limpia la lista de servicios caídos, seguido de volver a escribir la clave. Si no basta, reinstalar la copia guardada del APK (1.5).

### 1.4 Motor TTS roto, sin voz para el idioma, deshabilitado o desinstalado

- **Descripción**: TalkBack está activo pero mudo. Las causas típicas son:
  - el motor por defecto no tiene descargada la voz del idioma del sistema;
  - el motor está deshabilitado o desinstalado;
  - las voces dependen de la red y no hay conexión;
  - una actualización deja el motor sin datos.
- **Evidencia**:
  - Accessible Android explica que el TTS de Samsung trae pocos idiomas: si el idioma de la configuración inicial no tiene voz, TalkBack no habla, y durante la configuración no se pueden descargar voces: <https://accessibleandroid.com/the-matter-with-samsung-setup-wizard-how-to-solve-the-issue-of-no-speech-when-on-setup/>
  - En la comunidad de Sony, las opciones de TTS aparecían en gris («Checking») y sin habla; se arregló instalando el TTS de Google y una voz: <https://community.sony.ba/t5/z-series/tts-not-working/m-p/3401426>
  - En el grupo «Android Accesible» (septiembre de 2025), con TalkBack 16, hubo retrasos y cortes con el TTS de Google; cambiar a RHVoice (voz Mateo) lo resolvió: <https://groups.google.com/g/android_accesible/c/JMgIlpeEAsg>
  - En el grupo androidaccessibility de groups.io, la salida de TalkBack no iba al auricular aunque la multimedia sí: <https://groups.io/g/androidaccessibility/topic/talkback_output_not_going_to/102009835>
- **Causa técnica [VERIFICADO]**:
  - `TtsEngines.getDefaultEngine()` devuelve `tts_default_synth` **solo si el motor está instalado**. Si no, devuelve `getHighestRankedEngineName()`, que **solo considera motores del sistema** (`engines.get(0).system`). Un motor de terceros, como el nuestro, nunca es el de reserva automático: hay que fijarlo con `tts_default_synth`.
  - TalkBack usa `FailoverTextToSpeech` (`utils/.../output/FailoverTextToSpeech.java`). Observa `TTS_DEFAULT_SYNTH`, `TTS_DEFAULT_RATE` y `TTS_DEFAULT_PITCH` y cambia de motor en cuanto cambia la clave. Tras `MAX_TTS_FAILURES = 3` fallos de *inicialización* pasa al siguiente motor instalado, **incluidos los de terceros**: `TextToSpeechUtils.reloadInstalledTtsEngines` añade todos los servicios que responden a `TTS_SERVICE`.
  - Esa conmutación por fallo **no salta** si el motor arranca bien pero no tiene voz o no produce audio. En ese caso hace falta una acción externa.
- **Soluciones**:
  - **Sin WSS**:
    - Nuestro propio lector habla con su voz embebida (Pico) y lleva a la persona a `com.android.settings.TTS_SETTINGS`, una actividad exportada sin permiso (verificado en el manifiesto de Settings). Allí nuestro servicio elige «Motor preferido» = Rescate Accesible.
    - Con nuestra app instalada como motor TTS, TalkBack podrá *conmutar* a ella si el motor por defecto falla al arrancar.
  - **Con WSS**: `Settings.Secure.putString(cr, "tts_default_synth", "<nuestro.paquete>")`. TalkBack cambia al momento porque observa la clave.
  - **Con shell ADB**:
    - `pm enable com.google.android.tts` si estaba deshabilitado.
    - `pm install-existing com.google.android.tts` si se «desinstaló» para el usuario 0.
    - Reinstalar la copia guardada (1.5).
    - Las voces descargadas del TTS de Google **no van en el APK**, así que una copia del APK no las restaura [SIN VERIFICAR la ubicación exacta de los datos de voz].

### 1.5 Una actualización de TalkBack o del TTS de Google rompe la voz

- **Evidencia**:
  - Accessible Android analiza la actualización del TTS de Google V20241125.02 (10 de diciembre de 2024). Llegó con voces nuevas que TalkBack sí usa, pero que Jieshuo, Prudence, Auto TTS y otras apps no pueden usar. Un comentario de julio de 2025 dice que a velocidades altas suenan demasiado bajas: <https://accessibleandroid.com/google-tts-update-high-quality-voices-hidden-features-and-lingering-mysteries/>
  - En el blog de fireborn (15 de octubre de 2026), la exploración táctil deja de funcionar en todo el sistema y solo se arregla reiniciando. El autor lo ve en TalkBack 16 a 17 y Android 15 a 17, y cree que el fallo está en el sistema, no en TalkBack: <https://fireborn.mataroa.blog/blog/have-you-tried-turning-talkback-off-and-on-again/>
  - Versiones a la fecha: TalkBack 17.1, del 17 de septiembre de 2026; el TTS de Google se actualizó en Play el 13 de octubre de 2026 (según el buscador; [Play Store](https://play.google.com/store/apps/details?id=com.google.android.tts)) [FUENTE SECUNDARIA].
- **Soluciones**:
  - Guardar copias de `ApplicationInfo.sourceDir` y `splitSourceDirs` de TalkBack y del TTS de Google cuando funcionan, y reinstalarlas con `PackageInstaller` (ver 3.9).
  - Una **versión anterior no se puede instalar** sobre una más nueva: `pm install -d` dice «allow version code downgrade (debuggable packages only)», verificado en `PackageManagerShellCommand.java`.
  - Para volver atrás en una app del sistema existe `pm uninstall-system-updates <pkg>` (shell): «Removes updates to the given system application and falls back to its /system version» [VERIFICADO].
  - Como alternativa inmediata, cambiar `tts_default_synth` a nuestro motor.

### 1.6 Volumen de accesibilidad o multimedia muy bajo

- **Evidencia**:
  - En un Galaxy A06 (diciembre de 2025), el volumen de accesibilidad no baja de cerca del 7 %; es un problema de mínimo, no de silencio: [Samsung Community](https://r2.community.samsung.com/t5/Galaxy-A/Accessibility-problem/m-p/21272348).
  - En Android Central, TalkBack callaba porque el volumen del altavoz estaba bajado: <https://forums.androidcentral.com/threads/talkback-issue.640717>
  - La propia Google explica que con TalkBack activo las teclas de volumen cambian el volumen multimedia, y que el de la voz se cambia manteniendo un dedo en la pantalla mientras se pulsa una tecla de volumen: <https://support.google.com/accessibility/android/answer/6283655?hl=en> [FUENTE SECUNDARIA].
- **Hechos técnicos [VERIFICADO]** (`AudioService.java`):
  - `MIN_STREAM_VOLUME[STREAM_ACCESSIBILITY] = 1` y `MAX = 15`: el flujo de accesibilidad **no baja de 1** con los valores de AOSP. Los fabricantes pueden cambiarlo.
  - `STREAM_ACCESSIBILITY` solo es independiente si algún servicio enlazado pide `FLAG_ENABLE_ACCESSIBILITY_VOLUME` (`updateA11yVolumeAlias(accessibilityManager.isAccessibilityVolumeStreamActive())`). Si ninguno lo pide, es un alias de `STREAM_MUSIC`, que **sí puede estar a 0**.
  - Solo pueden cambiarlo quienes tengan `CHANGE_ACCESSIBILITY_VOLUME` (permiso `signature`) o el **UID de un servicio de accesibilidad enlazado** (`canChangeAccessibilityVolume()` mira `mAccessibilityServiceUids`, que se rellena con los UID de *todos* los servicios enlazados en `updateServicesLocked`).
- **Solución**: nuestro servicio, mientras está activo, puede llamar a `AudioManager.setStreamVolume(STREAM_ACCESSIBILITY, max, 0)` y subir también `STREAM_MUSIC`. Con shell ADB: `cmd media_session volume --stream 10 --set 15` [SIN VERIFICAR la sintaxis en cada versión].

### 1.7 El audio sale por un auricular Bluetooth lejano o apagado

- **Evidencia**:
  - En el grupo androidaccessibility, la voz de TalkBack dejó de ir al auricular. Se arregló revisando los perfiles de Bluetooth, volviendo a emparejar o apagando y encendiendo TalkBack: <https://groups.io/g/androidaccessibility/topic/talkback_output_not_going_to/102009835>
  - En la comunidad de Samsung, con dispositivos Bluetooth durante las llamadas, TalkBack no anuncia: [perfil de usuario en Samsung Community](https://r2.community.samsung.com/t5/user/viewprofilepage/user-id/41628554) [FUENTE SECUNDARIA, extracto del buscador].
- **Solución**:
  - Para nuestra propia voz, usar `AudioTrack` con `AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY` y `AudioTrack.setPreferredDevice()` hacia `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` (API pública, API 23 o superior). Así se ofrece «hablar por el altavoz del teléfono».
  - Para TalkBack no hay API pública que cambie la ruta global. `BluetoothAdapter.disable()` está obsoleto y no hace nada en apps con targetSdk 33 o superior [SIN VERIFICAR exactamente]. Con shell ADB se puede apagar con `cmd bluetooth_manager disable` [SIN VERIFICAR].
  - Alternativa: navegación guiada a Ajustes > Bluetooth (`Settings.ACTION_BLUETOOTH_SETTINGS`).

### 1.8 «Silenciar todos los sonidos», audio mono o balance izquierda/derecha extremo

- **Evidencia**:
  - Samsung documenta que con «Silenciar todos los sonidos» se silencia todo y no se puede ajustar el volumen: <https://www.samsung.com/us/accessibility/galaxy-mobile>, <https://www.samsung.com/au/support/mobile-devices/no-sound-on-samsung-galaxy-device/>
  - Audio mono y balance están en el mismo menú «Mejoras de audición» [FUENTE SECUNDARIA].
- **Hechos técnicos [VERIFICADO]**:
  - `Settings.System.MASTER_MONO` y `MASTER_BALANCE` son `@hide @Readable`, así que **se pueden leer**.
  - **No se pueden escribir desde una app de terceros, ni siquiera con WSS**: `SettingsProvider.enforceRestrictedSystemSettingsMutationForCallingPackage()` solo permite claves de `Settings.System.PUBLIC_SETTINGS` a apps no privilegiadas. Para el resto lanza `IllegalArgumentException("You cannot change private secure settings.")` si targetSdk > 22.
  - Solo el **UID shell, root o system** se libra de esa restricción. Por tanto, **solo vía shell ADB**: `settings put system master_mono 0` y `settings put system master_balance 0.0`.
  - «Silenciar todos los sonidos» de Samsung es una clave propia de Samsung [SIN VERIFICAR el nombre]. Hay que usar navegación guiada.

### 1.9 Velocidad o tono de habla extremos

- **Evidencia**: en la comunidad de Samsung, un usuario con un S21 cuenta que la velocidad cambia sola, a veces muy lenta y a veces muy rápida, y que el gesto para cambiarla desapareció: <https://eu.community.samsung.com/t5/accessibility/galaxy-s1-talk-back-speech-rate/td-p/10699998> [FUENTE SECUNDARIA].
- **Hechos técnicos**:
  - `tts_default_rate` y `tts_default_pitch` son `@Readable` y públicas (escribirlas exige WSS) [VERIFICADO].
  - TalkBack observa `TTS_DEFAULT_RATE` y `TTS_DEFAULT_PITCH` y combina su propio multiplicador de velocidad con el del sistema (`effectiveRate`) [VERIFICADO en `FailoverTextToSpeech.java`].
  - El ajuste «velocidad» del menú de TalkBack se guarda en las preferencias privadas de TalkBack y **no se puede tocar desde fuera** [SIN VERIFICAR la clave exacta].
- **Solución**:
  - **Con WSS**: poner `tts_default_rate=100` y `tts_default_pitch=100`.
  - **Sin WSS**: navegación guiada a `com.android.settings.TTS_SETTINGS` o a los ajustes de TalkBack.

### 1.10 Idioma del sistema cambiado por error

- **Evidencia**: guías de Huawei explican que el idioma de la voz de TalkBack va separado del idioma del sistema (<https://www.techbone.de/huawei/smartphone/talkback-option-zur-sprachaenderung-im-talkback-menue-einblenden-oder-ausblenden>). En la configuración inicial de Samsung hay que cambiar de idioma para recuperar la voz (artículo de 1.4) [FUENTE SECUNDARIA].
- **Hechos técnicos [VERIFICADO]**:
  - `CHANGE_CONFIGURATION` tiene nivel de protección `signature|privileged|development|role`, así que **se puede conceder con `pm grant`**.
  - La API para cambiar el idioma del sistema (`LocaleManager.setSystemLocales`, `@SystemApi`, o `ActivityManager.updatePersistentConfiguration`) está oculta. Usarla exige saltarse las restricciones de API ocultas [SIN VERIFICAR que funcione en Android 14 a 17].
- **Solución recomendada**: navegación guiada a `Settings.ACTION_LOCALE_SETTINGS`. Nuestro lector habla siempre con la voz es-ES o en-US elegida en la configuración, sin depender del idioma del sistema.

### 1.11 Atajo con varias funciones: el menú de elección no se lee

- **Evidencia**:
  - Google: si hay más de una función asignada, al mantener las dos teclas sale un menú para elegir: <https://support.google.com/accessibility/android/answer/7650693?hl=en>
  - La app **Shortcut Menu** (paquete `io.github.aaron_gh.shortcutmenu`, código fuente en <https://github.com/aaron-gh/shortcut-menu>) existe precisamente porque ese menú no se lee sin lector activo: <https://accessibleandroid.com/app/shortcut-menu/>. Hay una copia local en `E:\Antigravity\RescateAccesible\adjuntos\shortcut-menu`, que ya integra Pico (inglés) en `PicoTts.kt`.
- **Solución**: que nuestro servicio sea el **único** destino de `accessibility_shortcut_target_service` (con WSS o configurado a mano en la primera puesta en marcha). Desde Android 11 la clave es una lista separada por `:`; en Android 8 a 10 admite un solo componente [VERIFICADO: en Android 10 `mServiceToEnableWithShortcut` es único].

### 1.12 Atajo desactivado en la pantalla de bloqueo

- **Hechos técnicos [VERIFICADO]** (`AccessibilityShortcutController.onSettingsChanged`):
  - `mEnabledOnLockScreen = Secure.getInt(ACCESSIBILITY_SHORTCUT_ON_LOCK_SCREEN, dialogAlreadyShown) == 1`. Por defecto el atajo funciona en la pantalla de bloqueo una vez que se ha mostrado el diálogo de primera vez.
  - Si la persona lo desactivó, la clave vale 0.
  - La clave es `@hide @Readable`, así que se puede leer y, con WSS, escribir.
- **Solución**: comprobarla en la configuración inicial y ofrecer ponerla a 1.

### 1.13 Dos lectores de pantalla a la vez

- **Evidencia**:
  - Accessible Android recomienda desinstalar el TalkBack de Samsung si se usa el de Google por los conflictos: <https://accessibleandroid.com/a-beginners-guide-to-samsung-talkback-and-how-it-differs-from-googles-version/>
  - Opinión sobre la separación entre ambas versiones: <https://accessibleandroid.com/opinion-why-samsung-google-talkback-separation-is-nothing-but-a-headache/>
  - Samsung sustituye su TalkBack por el de Google en la beta de One UI 9 (14 de mayo de 2026): <https://accessibleandroid.com/samsung-replaces-its-talkback-version-with-googles-in-one-ui-9-beta/>
  - Shortcut Menu apaga el lector activo antes de encender otro.
- **Solución**: detectar los lectores activos, es decir, los servicios con `CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION` o `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` (el criterio que usa Shortcut Menu en `Services.isScreenReader`). Con WSS, dejar solo uno; sin WSS, navegación guiada.

### 1.14 Acceso con interruptores o Control de interacción (Samsung) bloquean el tacto

- **Evidencia**:
  - Google documenta cómo desactivar Acceso con interruptores, pero solo con la pantalla funcionando: <https://support.google.com/accessibility/android/answer/6301498>
  - Samsung documenta Control de interacción («bloquea interacciones», con atajo lateral + subir volumen): <https://www.samsung.com/ca/support/mobile-devices/galaxy-phone-interaction-and-dexterity-enhancements>, <https://ushl.samsung.com/ar/support/mobile-devices/galaxy-s20-how-to-activate-interaction-control>
- **Solución**:
  - **Con WSS**: quitar el servicio de Acceso con interruptores de `enabled_accessibility_services`. El componente exacto hay que leerlo en el dispositivo, ver 5.1.
  - Control de interacción de Samsung: [SIN VERIFICAR] el servicio o clave. Probable navegación guiada.

### 1.15 Ampliación activada por error

- **Solución**:
  - Nuestro servicio con `android:canControlMagnification="true"` puede llamar a `getMagnificationController().reset(true)` (API 24 o superior) [documentación pública].
  - **Con WSS**: poner a 0 `accessibility_display_magnification_enabled` (`@Readable @TestApi`) y quitar el componente de ampliación de los destinos de atajo [VERIFICADO que la clave existe y se puede leer].

### 1.16 Pantalla oculta o atenuada (para quien ayuda viendo)

- **Evidencia**: Google describe «Ocultar pantalla» o «Atenuar pantalla» de TalkBack, con atajo de tres pulsaciones de ambas teclas de volumen en TalkBack 8.2 o anterior: <https://support.google.com/accessibility/android/answer/6006589?hl=en>
- **Solución**:
  - Nuestra app puede tener un modo «ayudante» que suba el brillo. `Settings.System.SCREEN_BRIGHTNESS` exige `WRITE_SETTINGS`, que es un appop: con shell, `appops set <pkg> WRITE_SETTINGS allow`.
  - Desactivar «Atenuación extra» (`reduce_bright_colors_activated`) con WSS [SIN VERIFICAR la clave exacta].
  - La atenuación propia de TalkBack es una capa de TalkBack: se quita desde su menú o apagando TalkBack.

### 1.17 Tras reiniciar no habla hasta desbloquear (PIN sin voz)

- **Hechos técnicos [VERIFICADO]**:
  - `AccessibilityManagerService.updateServicesLocked`: «Ignore non-encryption-aware services until user is unlocked». Antes del primer desbloqueo solo se enlazan los servicios con `android:directBootAware="true"`.
  - TalkBack lo declara (`talkback/src/main/AndroidManifest.xml`), así que es capaz de funcionar antes del desbloqueo.
  - eSpeak NG 1.52 añadió «directBoot support» en Android (ChangeLog). Por tanto, el motor TTS **también** tiene que ser compatible con el arranque directo.
  - Si el TTS por defecto no lo es, TalkBack arranca mudo en la pantalla del PIN [inferencia razonable, SIN VERIFICAR con el TTS de Google].
- **Solución**: declarar `directBootAware="true"` en nuestro servicio de accesibilidad **y** en nuestro `TextToSpeechService`, y copiar los `.bin` de Pico al almacenamiento protegido del dispositivo (`createDeviceProtectedStorageContext()`).
- **Evidencia [FUENTE SECUNDARIA]**: reportes de pantallas de bloqueo inutilizables con TalkBack (Fairphone/e/OS, Sony): <https://community.e.foundation/t/talk-back-fos-froze-my-fairphone/70723>

### 1.18 La configuración inicial no habla

- Ver la evidencia de 1.4 (asistente de Samsung). Nuestra app no puede ayudar antes de estar instalada. Fuera de alcance, pero conviene documentarlo en la guía de usuario.

### 1.19 Retrasos o cortes con el TTS de Google

- En el grupo «Android Accesible» (septiembre de 2025, Pixel 8 Pro, Android 16, TalkBack 16), la voz se corta y el teclado tarda de 2 a 3 s en sonar. Con RHVoice se arregló: <https://groups.google.com/g/android_accesible/c/JMgIlpeEAsg>
- Solución: ofrecer el cambio de motor en un paso (`tts_default_synth`).

### 1.20 No molestar

- **[VERIFICADO]** En `AudioAttributes.java`, `SUPPRESSIBLE_USAGES.put(USAGE_ASSISTANCE_ACCESSIBILITY, SUPPRESSIBLE_NEVER)`. En AOSP, No molestar **nunca** silencia el audio de accesibilidad.
- Sí silencia `USAGE_MEDIA` (`SUPPRESSIBLE_MEDIA`) en «Silencio total». Nuestra voz debe usar siempre `USAGE_ASSISTANCE_ACCESSIBILITY`.
- Algún fabricante podría comportarse distinto [SIN VERIFICAR].

### 1.21 TalkBack o el TTS ocultos o desinstalados (debloaters, «Desactivar»)

- **Con shell ADB [VERIFICADO que existen los comandos]**: `pm install-existing <pkg>`, `pm enable <pkg>` y `pm uninstall-system-updates <pkg>`.
- **Sin shell**: reinstalar la copia del APK con `PackageInstaller` (necesita confirmación del usuario, ver 3.9) o navegación guiada a `ACTION_APPLICATION_DETAILS_SETTINGS` para pulsar «Habilitar».

### 1.22 Ajustes restringidos (Android 13 o superior) al instalar fuera de una tienda

- Android 13 bloquea activar la accesibilidad de apps instaladas con instaladores que no usan sesiones (navegador, gestor de archivos). Hay que ir a Información de la app > menú > «Permitir ajustes restringidos». Android 15 lo amplía con «Enhanced Confirmation Mode»:
  - <https://cleanbrowsing.org/support/mobile/disable-restricted-settings-android>
  - <https://androidauthority.com/android-15-enhanced-confirmation-mode-3436697>
- **[VERIFICADO]** `AccessibilityDetailsSettingsFragment.isServiceAllowed()` llama a `RestrictedLockUtilsInternal.isEnhancedConfirmationRestricted(..., OPSTR_BIND_ACCESSIBILITY_SERVICE)`.
- Impacto: distribuir por Play o instalar con un instalador basado en sesiones. Explicarlo en la guía.
- No está verificado si escribir `enabled_accessibility_services` con WSS esquiva esta restricción. Es probable, porque la comprobación está en la interfaz de Ajustes [SIN VERIFICAR].

---

## 2. Motores TTS para integrar dentro de la app

### 2.1 SVOX Pico (AOSP) — recomendado como voz base

**Ficheros es-ES y en confirmados [VERIFICADO]** en `platform/external/svox`, rama `main`, directorio `pico/lang` (último commit del repositorio en febrero de 2018).

| Fichero | Tamaño (bytes) | SHA-256 | Blob git |
|---|---:|---|---|
| `es-ES_ta.bin` | 256 744 | `b18c60baf249466bf8785d333d8cb4cf1f6b62e5545986f621c66568f1851961` | `3442eed85cdd55207ae2e4bc762aded8a472edc1` |
| `es-ES_zl0_sg.bin` | 605 280 | `82e724d3c788767ec95f0d22b0ab738e677ad397f26ec0bbbf2c07aafdf29000` | `c70257cf1b1475c82a5e623adf81f53dab038b5b` |
| `en-US_ta.bin` | 650 668 | `3a2a4d23de2a44dbbff50f69dcb9486be53b6d0a871d77cdc35f45c3e25e82aa` | `943f57ebab9c7a6a389430da3477419eeda40806` |
| `en-US_lh0_sg.bin` | 777 396 | `d6946526696d9eb085f7b23b1fbdf7a9986caca658b645e9eb73c6514fd4789d` | `b2dfc3949b4b761f5f3b66e749428a2e1e8d50c3` |
| `en-GB_ta.bin` | 412 248 | `57998f6423236dea25cd572a73b13f371e64155840e31b571eba7fca0b521b7e` | `5050583f3531fa8cf1274e8d4336ab53020518a4` |
| `en-GB_kh0_sg.bin` | 584 436 | `8f9f5d13a920a9f3371517178d42ce89b835f1e587760c2db280beb593c388fa` | `57c544a12293c01b46921303009ec6d94bc2fbce` |

El directorio también tiene de-DE, fr-FR e it-IT. es-ES ocupa unos 0,86 MB; es-ES + en-US, unos 2,29 MB.

**URLs de descarga** (Gitiles sirve los binarios en base64 con `?format=TEXT`; hay que decodificarlos):

- <https://android.googlesource.com/platform/external/svox/+/refs/heads/main/pico/lang/es-ES_ta.bin?format=TEXT>
- <https://android.googlesource.com/platform/external/svox/+/refs/heads/main/pico/lang/es-ES_zl0_sg.bin?format=TEXT>
- <https://android.googlesource.com/platform/external/svox/+/refs/heads/main/pico/lang/en-US_ta.bin?format=TEXT>
- <https://android.googlesource.com/platform/external/svox/+/refs/heads/main/pico/lang/en-US_lh0_sg.bin?format=TEXT>
- Directorio completo en tar.gz (binario directo, comprobado HTTP 200): <https://android.googlesource.com/platform/external/svox/+archive/refs/heads/main/pico/lang.tar.gz>
- Motor en C: <https://android.googlesource.com/platform/external/svox/+archive/refs/heads/main/pico/lib.tar.gz> (64 ficheros, `picoapi.c` y demás).
- Ejemplo: `curl -s "<url>?format=TEXT" | base64 -d > es-ES_ta.bin`, y comprobar el SHA-256 de la tabla.
- El mirror `aosp-mirror/platform_external_svox` de GitHub ya no existe (404).

**Licencia [VERIFICADO]**:

- `pico/lib/*` lleva la cabecera Apache 2.0 de SVOX AG y hay un fichero `MODULE_LICENSE_APACHE2`.
- Debian empaqueta `svox` en **non-free** porque los ficheros `pico/lang/*` «Claims to be Apache-2.0 but the source code is not included. At least, it is redistributable». Ver <https://metadata.ftp-master.debian.org/changelogs/non-free/s/svox/unstable_copyright> y <https://issuetracker.google.com/issues/36910001>.
- Conclusión: se pueden redistribuir dentro de una APK con licencia Apache 2.0. Hay que incluir el NOTICE.

**Datos técnicos [VERIFICADO en `pico/tts/com_svox_picottsengine.cpp`]**:

- Memoria de trabajo `PICO_MEM_SIZE 2500000` (2,5 MB).
- Velocidad de 20 a 500 (por defecto 100), con etiquetas en el texto: `<speed level='%d'>`, `<pitch level='%d'>` y `<volume level='%d'>`.
- Flujo de llamadas: `pico_initialize` → `pico_loadResource(ta)` + `pico_loadResource(sg)` → `pico_createVoiceDefinition` / `pico_addResourceToVoiceDefinition` → `pico_newEngine` → bucle de `pico_putTextUtf8` / `pico_getData` (búfer de salida de 128 bytes).
- Salida: PCM de 16 kHz, 16 bits, mono.
- Hay un arreglo de 2018 («CTS cases fail due to NE in SVOX TTS library»): un fallo en `picokpr_getAttrValArrInt32` según el compilador. **Hay que usar el código de `main`**, que lo incluye.
- El `PicoService` de AOSP usa la capa `compat` (`SynthProxy`, interfaz antigua `TtsEngine` en C++). **No conviene reutilizarlo**: es mejor escribir un JNI propio sobre `picoapi.h` (lo que hace Shortcut Menu en `PicoTts.kt`) y un `TextToSpeechService` moderno.
- Debian compila `libttspico` para amd64 y arm64, lo que indica que el motor funciona en 64 bits.

### 2.2 eSpeak NG

- **Versión estable más reciente: 1.52.0** (12 de diciembre de 2024). La API de GitHub no muestra versiones posteriores a la fecha de este informe [VERIFICADO].
- **CMake [VERIFICADO]**: el ChangeLog de 1.52.0 dice «added cmake build system» y avisa: «this is the last release witch support the autoconf build system». La app Android (`android/build.gradle`) usa `externalNativeBuild { cmake { path "jni/CMakeLists.txt"; version "3.22.1" } }` con `-DUSE_ASYNC:BOOL=OFF -DUSE_MBROLA:BOOL=OFF` y los targets `ttsespeak` y `espeak-data`, con NDK 26.1.10909125.
  - `android/jni/CMakeLists.txt` hace `add_subdirectory(../../ espeakng)`.
  - El target `espeak-data` lanza una **compilación de host** (`cmake -B... ; cmake --build ... --target data`) para generar los datos. Esto exige compilar en una máquina de escritorio (Linux, macOS o WSL).
  - Opciones de `cmake/config.cmake`: `USE_MBROLA`, `USE_LIBSONIC` (si no está, `deps.cmake` descarga sonic con FetchContent), `USE_LIBPCAUDIO`, `USE_KLATT=ON`, `USE_SPEECHPLAYER=ON`, `USE_ASYNC`. Para integrarlo: `USE_LIBPCAUDIO=OFF`, `USE_ASYNC=OFF`, `USE_MBROLA=OFF`.
- **Compatibilidad de datos [VERIFICADO]**: `synthdata.c` exige `version_phdata = 0x014801`. Aun así, los diccionarios y `phondata` deben salir de **la misma compilación** que la biblioteca, porque los índices de fonemas cambian entre versiones.
- **Datos compilados ya hechos (sin compilar en el host)**:
  1. **APK oficial 1.52.0**: <https://github.com/espeak-ng/espeak-ng/releases/download/1.52.0/espeak-1.52.0-signed.apk> (10 446 659 bytes). Dentro, `res/raw/espeakdata.zip` aparece ofuscado como **`res/t8.zip`** (9 211 957 bytes, 401 entradas, 18,4 MB sin comprimir). Contenido [VERIFICADO leyendo el directorio central del zip por HTTP Range]:

     | Fichero | Bytes |
     |---|---:|
     | `espeak-ng-data/phontab` | 58 652 |
     | `espeak-ng-data/phondata` | 554 740 |
     | `espeak-ng-data/phonindex` | 43 316 |
     | `espeak-ng-data/intonations` | 2 312 |
     | `espeak-ng-data/phondata-manifest` | 21 866 |
     | `espeak-ng-data/es_dict` | 49 285 |
     | `espeak-ng-data/en_dict` | 168 204 |
     | `espeak-ng-data/lang/roa/es`, `lang/roa/es-419`, `lang/gmw/en`, `lang/gmw/en-US` | (pequeños) |
     | `espeak-ng-data/voices/!v/` | (variantes) |

     El subconjunto mínimo es+en ocupa unos 0,9 MB. La APK trae además `lib/<abi>/libttsespeak.so` (arm64 681 376 bytes) y está firmada por el proyecto.
  2. **Debian** `espeak-ng-data_1.52.0+dfsg-5_amd64.deb` (8 653 552 bytes): <http://ftp.us.debian.org/debian/pool/main/e/espeak-ng/espeak-ng-data_1.52.0+dfsg-5_amd64.deb>. Ojo: Debian aplica parches (`piper`, `th_dict`, `bufsize`…), así que los datos pueden no coincidir con la 1.52.0 original.
  3. **sherpa-onnx** `espeak-ng-data.tar.bz2` (7 252 012 bytes) y `.zip` (9 037 020 bytes): <https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2>. Vienen del fork de k2-fsa; versión exacta [SIN VERIFICAR], así que solo deben usarse con la biblioteca de ese fork.
  4. **PyPI `espeakng-loader` 0.2.4** (licencia MIT del envoltorio; trae la biblioteca y los datos para escritorio): <https://pypi.org/project/espeakng-loader/>. Versión de eSpeak dentro [SIN VERIFICAR]. No es útil para Android, salvo por los datos.
- **Código fuente**: <https://github.com/espeak-ng/espeak-ng/archive/refs/tags/1.52.0.tar.gz> (comprobado HTTP 200).
- **Licencia**: GPL-3.0-or-later (`COPYING`), con partes bajo Apache, BSD-2 y UCD. Si se enlaza `libespeak-ng` dentro de nuestra APK, el conjunto se distribuye como obra derivada: **toda la app tendría que publicarse bajo GPLv3** (o compatible) con su código fuente. Si la app va a ser abierta bajo GPLv3, no hay problema (libadb-android también admite GPL-3.0). Si no, eSpeak NG no se puede integrar.
- **Paquete Android oficial**: `com.reecedunn.espeak` (en F-Droid; `directBootAware="true"` en su manifiesto 1.52.0).

### 2.3 RHVoice

- **Español**: hay voz **Mateo** (en `data/voices/Mateo` del repositorio). En Arch se empaqueta como `rhvoice-voice-mateo`, con licencia **Unlicense** en 1.18.4 pero **CC-BY-SA-4.0** en 1.16.5 (contradictorio, [SIN VERIFICAR]). El paquete de idioma `rhvoice-language-spanish` es LGPL-2.1:
  - <https://archlinux.org/packages/extra/x86_64/rhvoice-language-spanish/>
  - <https://archlinux.org/packages/extra-testing/x86_64/rhvoice-voice-mateo/>
  - <https://rhvoice.org/es-voices/>
- Leticia-F123 es **portugués de Brasil**, no español (CC-BY-SA-4.0).
- Núcleo LGPL-2.1-or-later; algunas voces llevan otras licencias. Debian pone las voces en non-free.
- App Android: `com.github.olga_yakovleva.rhvoice.android` (F-Droid 1.18.4).
- Hay evidencia de uso real en español (1.4 y 1.19). Integrable con LGPL (enlace dinámico), pero el tamaño y la licencia de Mateo deben verificarse.

### 2.4 sherpa-onnx + Piper

- **sherpa-onnx v2.0.0** (23 de octubre de 2026), licencia Apache-2.0. AAR oficiales:
  - `sherpa-onnx-2.0.0.aar` (49,2 MB): <https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.0/sherpa-onnx-2.0.0.aar>
  - `sherpa-onnx-static-link-onnxruntime-2.0.0.aar` (38,5 MB)
- **Cambio importante [VERIFICADO]**: el PR #4055 (fusionado el 20 de octubre de 2026) «Remove espeak-ng and piper-phonemize from sherpa-onnx». Desde la 2.0.0, sherpa-onnx «is GPL-free». Los modelos Piper (VITS) necesitan fonemas que hay que calcular fuera (`phoneme_codepoints` o `tokens`) con piper-phonemize o eSpeak NG, ambos GPL. Con las versiones 1.12.x, eSpeak va incluido, y la app queda bajo GPL.
- **Voces es (paquetes preparados para sherpa-onnx, tamaño del .tar.bz2) [VERIFICADO en la release `tts-models`]**:

  | Voz | Licencia del dataset | fp32 | int8 |
  |---|---|---:|---:|
  | es_ES-davefx-medium | CC0 | 67,2 MB | 21,2 MB |
  | es_ES-carlfm-x_low | dominio público | 26,5 MB | 13,4 MB |
  | es_ES-sharvard-medium | CC-BY 3.0 | 80,3 MB | 23,5 MB |
  | es_ES-miro-high | [SIN VERIFICAR] | 67,2 MB | 21,3 MB |
  | es_MX-ald-medium | Unlicense | 67,2 MB | 21,3 MB |
  | es_MX-claude-high | Apache-2.0 | 67,2 MB | 21,2 MB |
  | es_AR-daniela-high | [SIN VERIFICAR] | 115,6 MB | 35,1 MB |

  Licencias según los MODEL_CARD de <https://huggingface.co/rhasspy/piper-voices>.
- Inglés: en_US-lessac usa la licencia Blizzard 2013 (restrictiva) y en_US-ryan es CC BY-NC-SA 4.0 (**no comercial**). en_US-libritts_r es CC BY 4.0.
- La app SherpaTTS (`org.woheller69.ttsengine`, F-Droid 3.4) demuestra que funciona como motor de sistema.
- **Valoración**: calidad alta, pero tamaño (más de 20 MB por voz más el runtime), consumo de CPU y latencia lo hacen inadecuado como voz **de rescate**. Como mucho, un módulo descargable opcional.

### 2.5 Flite

- Licencia tipo BSD (permisiva): <https://github.com/festvox/flite>. No he encontrado ninguna voz española oficial de CMU [SIN VERIFICAR que exista alguna de calidad]. Descartado para es-ES.

### 2.6 Recomendación

1. **Voz base integrada: SVOX Pico** con es-ES y en-US (unos 2,3 MB), con licencia Apache 2.0 y PCM de 16 kHz.
   - Una sola instancia en C, accesible por JNI.
   - La usan **tanto** nuestro servicio de accesibilidad, que reproduce con su propio `AudioTrack` (`USAGE_ASSISTANCE_ACCESSIBILITY`, sin pasar por el TTS del sistema, para no depender de nada roto), **como** un `TextToSpeechService` exportado. Así la app se puede poner como motor del sistema para TalkBack.
   - Copiar los `.bin` al almacenamiento protegido del dispositivo y declarar ambos servicios `directBootAware`.
2. **eSpeak NG solo si la app se publica bajo GPLv3.** En ese caso, usar los datos de `res/t8.zip` de la APK 1.52.0, o compilarlos en el host con CMake desde la etiqueta 1.52.0, y compilar `libespeak-ng` con el NDK mediante `add_subdirectory`, igual que `android/jni/CMakeLists.txt`.
3. **RHVoice y Piper**: opcionales y descargables. No forman parte del núcleo de rescate.

---

## 3. Hechos verificados de la API de Android

### 3.1 Atajo de accesibilidad (teclas de volumen) sobre un AccessibilityService

Fuente: `services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java` (`main`) y la misma clase en `android10-release`.

- **Android 8 a 10** [VERIFICADO en `android10-release`]: `performAccessibilityShortcut()` toma `mServiceToEnableWithShortcut` (un solo destino). Si el servicio no está conectado lo **habilita**; si lo está, lo **deshabilita**. Es siempre un alternador.
- **Android 11 o superior** (`performAccessibilityShortcutTargetService`), según los comentarios del propio código:
  1. Botón de software y servicio con targetSdk ≤ Q: llama al servicio si está enlazado y pide el botón.
  2. Atajo de hardware y targetSdk ≤ Q: lo enciende o apaga.
  3. Atajo de hardware, targetSdk > Q y con `FLAG_REQUEST_ACCESSIBILITY_BUTTON`: **lo enciende si no está activo**. Si ya lo está, llama a `notifyAccessibilityButtonClickedLocked` y se recibe `AccessibilityButtonController.AccessibilityButtonCallback.onClicked()`. **Nunca lo apaga.**
  4. targetSdk > Q sin el flag: lo enciende o apaga.
- **Trampa crítica [VERIFICADO]**: `removeShortcutTargetForUnboundServiceLocked()`. Cuando un servicio con `mRequestAccessibilityButton` y targetSdk > Q pasa a estar **no habilitado** (se desenlaza en `updateServicesLocked`), el sistema **lo quita de TODOS los atajos**: HARDWARE, SOFTWARE, QUICK_SETTINGS, GESTURE y KEY_GESTURE.
  - Además, `migrateAccessibilityButtonSettingsIfNecessaryLocked` asigna al botón de software todo servicio habilitado que pida el botón y no esté en ningún atajo, de modo que aparece el botón flotante.
  - Consecuencia: si el servicio de rescate pide el botón y algo lo desactiva (force-stop, `disableSelf`, la persona), **se pierde la asignación de las teclas de volumen** y ya no se puede rescatar. El comentario de `shortcut_menu_service.xml` de Shortcut Menu describe justo esto.
- **Recomendación de diseño**:
  - El servicio que se asigna a las teclas de volumen **no** debe declarar `flagRequestAccessibilityButton`.
  - Debe vivir solo mientras dura el rescate y terminar con `disableSelf()`, como Shortcut Menu. Así cada pulsación del atajo lo *enciende*.
  - Si se quiere el comportamiento de `onClicked`, se puede usar un *segundo* servicio, pero no como red de seguridad.
- **Tiempos [VERIFICADO en `ViewConfiguration.java`]**: `A11Y_SHORTCUT_KEY_TIMEOUT = 3000` ms la primera vez y `A11Y_SHORTCUT_KEY_TIMEOUT_AFTER_CONFIRMATION = 1000` ms después.
- **Diálogo de primera vez [VERIFICADO en `AccessibilityShortcutController.java`]**:
  - Mientras `accessibility_shortcut_dialog_shown` = 0, la primera pulsación **muestra un aviso** en lugar de ejecutar el atajo.
  - Si el destino declara `FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK` (API 29), el aviso se **lee con TTS** (`performTtsPrompt`). Esto necesita que el TTS del sistema funcione.
  - Con WSS se puede dejar `accessibility_shortcut_dialog_shown=1` para saltar el aviso.
  - Si `accessibility_shortcut_target_service` es `null` (nunca se configuró), se usa `config_defaultAccessibilityService` del fabricante. La cadena vacía significa «desactivado por el usuario».
  - En el código actual el aviso se ejecuta con `enableDefaultHardwareShortcut`.

### 3.2 `AccessibilityService.disableSelf()`

- API 24 o superior. Documentación: «Disables the service. After calling this method, the service will be disabled and settings will show that it is turned off.»
- Implementación [VERIFICADO en `AccessibilityServiceConnection.disableSelf()`]: quita el componente de `mEnabledServices` y persiste `ENABLED_ACCESSIBILITY_SERVICES`, sin necesitar ningún permiso (`@RequiresNoPermission`).

### 3.3 `FLAG_REQUEST_FILTER_KEY_EVENTS`

- Documentación [VERIFICADO]: «the accessibility service will receive the key events before applications allowing it implement global shortcuts». Exige `android:canRequestFilterKeyEvents="true"`.
- **Límite [VERIFICADO en `AccessibilityInputFilter.onInputEvent`]**: si el evento no lleva `FLAG_PASS_TO_USER`, el filtro de accesibilidad no lo procesa. Con la pantalla apagada, la política suele consumir las teclas de volumen sin pasarlas al usuario, así que no llegan a `onKeyEvent` [SIN VERIFICAR en todos los casos].
- La combinación del atajo de accesibilidad se detecta en la política de ventanas (PhoneWindowManager), **antes** del filtro. Nuestro servicio **no puede impedir** que el sistema ejecute el atajo [inferencia del orden del flujo de entrada; SIN VERIFICAR con prueba].

### 3.4 `FLAG_ENABLE_ACCESSIBILITY_VOLUME` y `STREAM_ACCESSIBILITY`

- Ver 1.6. Resumen [VERIFICADO]:
  - El flag hace que `USAGE_ASSISTANCE_ACCESSIBILITY` se controle con `STREAM_ACCESSIBILITY`.
  - Cualquier servicio de accesibilidad **enlazado** (con el flag o sin él) puede llamar a `setStreamVolume`/`adjustStreamVolume` sobre `STREAM_ACCESSIBILITY`, porque su UID está en `mAccessibilityServiceUids`.
  - El mínimo de AOSP es 1.

### 3.5 `isAccessibilityTool` (API 31) y `accessibilityDataSensitive` (API 34)

- `AccessibilityServiceInfo.isAccessibilityTool()`: «Indicates if the service is used to assist users with disabilities» [VERIFICADO]. Se declara con `android:isAccessibilityTool="true"` en el XML del servicio.
- `View.isAccessibilityDataSensitive()` [VERIFICADO en `View.java`]: restringe el acceso a los servicios que tienen `isAccessibilityTool=true`. Una vista es sensible si se marca así a mano o si usa `filterTouchesWhenObscured`, y lo heredan sus descendientes.
- Efecto práctico: un servicio sin `isAccessibilityTool` **no ve ni puede actuar** sobre esas vistas, como diálogos de permisos o botones de instalar con protección contra superposición. **Nuestro servicio debe declararlo.**
- Google Play exige que solo lo declaren herramientas de accesibilidad de verdad, con revisión [FUENTE SECUNDARIA: política de Play; comprobar al publicar].

### 3.6 ¿Puede nuestro servicio pulsar «Permitir control total» o los interruptores de Ajustes?

- El diálogo de aviso (`com/android/internal/accessibility/dialog/AccessibilityServiceWarning.java`) [VERIFICADO]:
  - El botón «Permitir» tiene un `OnTouchListener` que **descarta los toques** marcados con `FLAG_WINDOW_IS_OBSCURED` o `FLAG_WINDOW_IS_PARTIALLY_OBSCURED`, y la ventana usa `SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS`.
  - Eso bloquea los ataques de superposición (*tapjacking*), pero **no bloquea `performAction(ACTION_CLICK)`** de un servicio de accesibilidad, que es como lo pulsa TalkBack.
  - Las vistas con `filterTouchesWhenObscured` pasan a ser `accessibilityDataSensitive`. Desde Android 14, solo un servicio con `isAccessibilityTool=true` puede verlas y actuar sobre ellas [VERIFICADO en `View.calculateAccessibilityDataSensitive`].
- Conclusión: **sí, con `isAccessibilityTool=true` y `ACTION_CLICK`** [inferencia del código; SIN VERIFICAR en capas de fabricante]. Conviene buscar por texto o id de vista y comprobar el resultado.
- Los fabricantes (One UI, HyperOS) usan sus propias pantallas de Ajustes, así que la navegación guiada necesita heurísticas por fabricante.

### 3.7 Servicios `directBootAware`

- Ver 1.17 [VERIFICADO]. Hay que declarar `android:directBootAware="true"` en el `<service>` y guardar los datos en el almacenamiento protegido del dispositivo.

### 3.8 Requisitos de `TextToSpeechService` (registrarse como motor del sistema)

[VERIFICADO en `TextToSpeechService.java`, `TtsEngines.java` y en el manifiesto de Pico de AOSP]

- Métodos abstractos: `onIsLanguageAvailable`, `onGetLanguage` (solo se llama antes de API 18), `onLoadLanguage`, `onStop` y `onSynthesizeText(SynthesisRequest, SynthesisCallback)`.
- Para voces (API 21 o superior): `onGetVoices`, `onIsValidVoiceName`, `onLoadVoice` y `onGetDefaultVoiceNameFor`.
- `onSynthesizeText` se llama siempre desde un mismo hilo, distinto del principal, y es **síncrono**: hay que llamar a `callback.start(16000, ENCODING_PCM_16BIT, 1)`, luego `audioAvailable(buf, off, len)` en trozos de `getMaxBufferSize()` como máximo y al final `done()`. No se debe conservar el callback.
- Manifiesto:
  ```xml
  <service android:name=".tts.RescateTtsService" android:exported="true"
           android:directBootAware="true" android:label="@string/tts_name">
    <intent-filter>
      <action android:name="android.intent.action.TTS_SERVICE"/>
      <category android:name="android.intent.category.DEFAULT"/>
    </intent-filter>
    <meta-data android:name="android.speech.tts" android:resource="@xml/tts_engine"/>
  </service>
  ```
  `res/xml/tts_engine.xml`: `<tts-engine xmlns:android="..." android:settingsActivity="..."/>`.
- Actividades recomendadas, con los mismos `intent-filter` que el `com.svox.pico` de AOSP:
  - `android.speech.tts.engine.CHECK_TTS_DATA`: devuelve `CHECK_VOICE_DATA_PASS` y la lista de idiomas disponibles.
  - `android.speech.tts.engine.INSTALL_TTS_DATA`.
  - `android.speech.tts.engine.GET_SAMPLE_TEXT`.
  - `android.speech.tts.engine.CONFIGURE_ENGINE`.
- **Selección por defecto**: un motor de terceros solo es el motor por defecto si `tts_default_synth` lo indica. Lo escribe Ajustes, cuando la persona lo elige, o nuestra app con WSS (ver 1.4).

### 3.9 PackageInstaller: reinstalar desde la copia de `sourceDir` + `splitSourceDirs`

- Copia: `ApplicationInfo.sourceDir` (base.apk) y `splitSourceDirs` (por ABI, densidad o idioma). Los APK de `/data/app` se pueden leer desde otras apps, que es lo que hacen los extractores de APK [FUENTE SECUNDARIA y práctica habitual].
  - Desde Android 11 hay que declarar `<queries>` con los paquetes concretos (TalkBack, el TTS de Google, el TTS de Samsung…), o no se verán. Ver también 3.12.
- Instalación de varios APK:
  ```kotlin
  val pi = context.packageManager.packageInstaller
  val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
      setAppPackageName("com.google.android.marvin.talkback")
      if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
  }
  val id = pi.createSession(params)
  pi.openSession(id).use { s ->
      (listOf(base) + splits).forEachIndexed { i, f ->
          s.openWrite("part$i.apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; s.fsync(out) }
      }
      s.commit(PendingIntent.getBroadcast(context, 0, Intent(ACTION_INSTALL_RESULT).setPackage(context.packageName),
               PendingIntent.FLAG_MUTABLE).intentSender)
  }
  // En el receptor: si STATUS_PENDING_USER_ACTION, lanzar el Intent de EXTRA_INTENT (diálogo del sistema).
  ```
- Requisitos:
  - `REQUEST_INSTALL_PACKAGES` (`signature|appop`; la persona activa «Instalar apps desconocidas», o con shell `appops set <pkg> REQUEST_INSTALL_PACKAGES allow`).
  - Misma firma.
  - versionCode **igual o mayor**: no se puede bajar de versión, ver 1.5.
- **Confirmación del usuario [VERIFICADO en la documentación de `setRequireUserAction`]**: solo se evita si el instalador es el *update owner* o el *installer of record* de la app (o se actualiza a sí mismo) y la app cumple el targetSdk mínimo:
  - ≥ 29 en Android 12;
  - ≥ 30 en Android 13;
  - ≥ 31 en Android 14;
  - ≥ 33 en Android 15.

  TalkBack lo instaló Play o viene en el sistema, así que **habrá diálogo**. Nuestro servicio puede pulsar «Actualizar» o «Instalar» (ver 3.6).
- **Android 14, update ownership [VERIFICADO]**: «Once the update ownership enforcement is enabled, the other installers will need the user action to update the package even if the installers have been granted INSTALL_PACKAGES». Si el propietario de actualizaciones de TalkBack es Play, la reinstalación pedirá confirmación.
- **Por shell**: `pm install-create`, `pm install-write` y `pm install-commit` no necesitan diálogo, porque el shell tiene INSTALL_PACKAGES. Si update ownership también obliga al shell a confirmar está [SIN VERIFICAR].

### 3.10 `TileService.startActivityAndCollapse` en Android 14

- [VERIFICADO en `TileService.java`] `startActivityAndCollapse(Intent)` está `@Deprecated`: «@throws UnsupportedOperationException if called in versions UPSIDE_DOWN_CAKE and up» (cambio de compatibilidad `START_ACTIVITY_NEEDS_PENDING_INTENT`).
- Hay que usar `startActivityAndCollapse(PendingIntent)`, añadido en API 34.

### 3.11 Intents de Ajustes

| Intent | Estado [VERIFICADO en el manifiesto de Settings y en `Settings.java`] |
|---|---|
| `Settings.ACTION_ACCESSIBILITY_SETTINGS` (`android.settings.ACCESSIBILITY_SETTINGS`) | Público, exportado y sin permiso. |
| `com.android.settings.TTS_SETTINGS` | `Settings$TextToSpeechSettingsActivity`, exportada, **sin permiso**. No es constante pública del SDK, pero funciona por cadena. |
| `android.settings.ACCESSIBILITY_DETAILS_SETTINGS` | **`@SystemApi`**. La actividad exige `android.permission.OPEN_ACCESSIBILITY_DETAILS_SETTINGS`, con nivel `signature|installer`. **No se puede usar desde una app de terceros.** Alternativa: `ACTION_ACCESSIBILITY_SETTINGS` con el extra no documentado `":settings:fragment_args_key"` = componente aplanado para resaltar la fila [SIN VERIFICAR, truco de la comunidad]. |
| `android.settings.ACCESSIBILITY_SHORTCUT_SETTINGS` | Exige `MANAGE_ACCESSIBILITY` (de sistema). No se puede usar. |
| `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` + `package:` | Público. Útil para «Habilitar», «Forzar detención» y «Permitir ajustes restringidos». |
| `Settings.ACTION_LOCALE_SETTINGS`, `ACTION_BLUETOOTH_SETTINGS`, `ACTION_SOUND_SETTINGS`, `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Públicos. |

### 3.12 Claves de `Settings` (lectura por apps normales)

Desde Android 12, `Settings.java` dice: «From S+, settings keys annotated with @hide are restricted to system_server and system apps only, unless they are annotated with @Readable». Las claves públicas se pueden leer siempre [VERIFICADO].

| Clave | Tabla | ¿Pública? | ¿Lectura por apps? | Escritura |
|---|---|---|---|---|
| `enabled_accessibility_services` | Secure | Sí | Sí (`@Readable`) | WSS |
| `accessibility_enabled` | Secure | Sí | Sí | WSS (el sistema la mantiene sola) |
| `touch_exploration_enabled` | Secure | Sí | Sí | La gestiona el sistema |
| `tts_default_synth` / `tts_default_rate` / `tts_default_pitch` | Secure | Sí | Sí | WSS |
| `tts_default_locale`, `tts_enabled_plugins` | Secure | `@hide` | Sí (`@Readable`) | WSS |
| `accessibility_shortcut_target_service` | Secure | `@hide @TestApi` | Sí (`@Readable`) | WSS |
| `accessibility_button_targets` | Secure | `@hide` | Sí (`@Readable`) | WSS |
| `accessibility_qs_targets` | Secure | `@hide` | **No** (sin `@Readable`, lanza `SecurityException` si targetSdk ≥ 31) | WSS |
| `accessibility_button_mode` | Secure | `@hide` | **No** | WSS |
| `accessibility_shortcut_on_lock_screen` | Secure | `@hide` | Sí | WSS |
| `accessibility_shortcut_dialog_shown` | Secure | `@hide` | Sí | WSS |
| `accessibility_display_magnification_enabled` | Secure | `@hide @TestApi` | Sí | WSS |
| `master_mono`, `master_balance` | **System** | `@hide` | Sí | **Solo shell, root o system** (ver 1.8) |
| `adb_enabled`, `adb_wifi_enabled` | Global | `@hide`/pública | Sí | WSS |
| `adb_allowed_connection_time` | Global | `@hide` | [SIN VERIFICAR] | WSS. Valor por defecto 604 800 000 ms (7 días) |

- **Visibilidad de paquetes [VERIFICADO]**: `getInstalledAccessibilityServiceList()` filtra con `pm.filterAppAccess(...)`. Hay que declarar en `<queries>` los `intent` `android.accessibilityservice.AccessibilityService` y `android.intent.action.TTS_SERVICE`, o pedir `QUERY_ALL_PACKAGES` (sujeto a la política de Play).

### 3.13 Otros permisos que se pueden conceder con `pm grant` (nivel `development`) [VERIFICADO en `core/res/AndroidManifest.xml`]

- `WRITE_SECURE_SETTINGS` (`signature|privileged|development|role|installer`)
- `CHANGE_CONFIGURATION`
- `READ_LOGS`, `DUMP`, `PACKAGE_USAGE_STATS`, `SET_ANIMATION_SCALE`, `SYSTEM_ALERT_WINDOW`, `SET_VOLUME_KEY_LONG_PRESS_LISTENER`, `BATTERY_STATS`, `INTERACT_ACROSS_USERS`, entre otros.

---

## 4. Concederse WSS en el propio dispositivo con la depuración inalámbrica

### 4.1 libadb-android

- **Repositorio**: <https://github.com/MuntashirAkon/libadb-android> (último push en junio de 2026).
- **Versión 3.1.1**, publicada en **JitPack**, no en Maven Central [VERIFICADO]:
  - `https://repo1.maven.org/maven2/io/github/muntashirakon/libadb-android/` → 404;
  - `.../io/github/muntashirakon/libadb/` → 404;
  - search.maven.org → 0 resultados;
  - la API de JitPack da `latestOk: 3.1.1`.
- La coordenada `io.github.muntashirakon:libadb-android` **no existe en Maven Central**. Coordenada correcta: `com.github.MuntashirAkon:libadb-android:3.1.1` con el repositorio `maven { url "https://jitpack.io" }`.
- **Licencia**: doble, «GPL-3.0-or-later or Apache-2.0». Aviso del README: «this library has an LGPL dependency», que es spake2-java (LGPL-3.0).
- **Dependencias** (`libadb/build.gradle` 3.1.1):
  - `androidx.annotation:annotation:1.9.1`
  - `org.bouncycastle:bcprov-jdk15to18:1.81`
  - `com.github.MuntashirAkon.spake2-java:spake2-android:2.2.1` (JitPack, LGPL-3.0)
- **Opcionales recomendadas por el README**:
  - `org.conscrypt:conscrypt-android:2.5.3` (TLS 1.3 propio; recomendado para no depender de API ocultas);
  - `com.github.MuntashirAkon:sun-security-android:1.1` (generar el X509), o BouncyCastle `bcpkix`;
  - `org.lsposed.hiddenapibypass:hiddenapibypass:6.1`, solo si se quiere el Conscrypt del sistema.
- **API [VERIFICADO en `AbsAdbConnectionManager.java`]**: `pair(host, port, code)`, `connect(host, port)`, `connectTls(ctx, timeout)`, `autoConnect(ctx, timeout)`, `openStream("shell:...")`, `disconnect()`, `setHostAddress()` y `setApi()`.
- El README avisa: «This library has never gone through a security audit».

**Ejemplo mínimo** (Kotlin; generación de certificado con BouncyCastle `bcpkix`; guardar la clave en un almacén privado de la app):

```kotlin
class Adb(private val ctx: Context) : AbsAdbConnectionManager() {
    private val key: PrivateKey; private val cert: Certificate
    init {
        setApi(Build.VERSION.SDK_INT)
        // Cargar de disco si existe; si no, generar y persistir.
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=RescateAccesible")
        val now = Date(); val until = Date(now.time + 3650L * 86_400_000)
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now.time), now, until, name, kp.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
        key = kp.private; cert = JcaX509CertificateConverter().getCertificate(holder)
    }
    override fun getPrivateKey() = key
    override fun getCertificate() = cert
    override fun getDeviceName() = "RescateAccesible"
}

// 1) Emparejar (una sola vez): la persona abre Opciones de desarrollador > Depuración inalámbrica >
//    «Vincular dispositivo con código»; nuestra app lee el puerto por mDNS y la persona dicta el código.
suspend fun pairAndGrant(ctx: Context, pairPort: Int, code: String, connectPort: Int): String = withContext(Dispatchers.IO) {
    val adb = Adb(ctx)
    adb.pair("127.0.0.1", pairPort, code)
    adb.connect("127.0.0.1", connectPort)          // o adb.connectTls(ctx, 10_000)
    adb.openStream("shell:pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS").use { s ->
        s.openInputStream().bufferedReader().readText()   // "" si todo fue bien
    }.also { adb.disconnect() }
}
```

- La persona tiene que activar Opciones de desarrollador y Depuración inalámbrica, que necesita Wi-Fi, y estar en la pantalla de vincular **a la vez** que introduce el código en nuestra app.
  - Shizuku lo resuelve con una notificación con `RemoteInput` donde se escribe el código: <https://shizuku.rikka.app/guide/setup/>
  - Con TalkBack o con nuestro lector, la persona puede leer el código en pantalla y escribirlo en la notificación.
- **Problemas conocidos**: Shizuku se queda en «Searching for pairing service» y se arregla apagando y encendiendo la depuración inalámbrica: <https://discuss.grapheneos.org/d/36299-i-cant-pair-with-shizuku> [FUENTE SECUNDARIA].
- El permiso concedido con `pm grant` **se mantiene tras reiniciar y tras actualizar**, y se pierde al desinstalar [FUENTE SECUNDARIA, coherente con cómo funcionan los permisos `development`]. En algunos Xiaomi hace falta además «Depuración USB (Ajustes de seguridad)» [FUENTE SECUNDARIA].

### 4.2 Descubrir los puertos con mDNS

- Tipos de servicio [VERIFICADO en `libadb/.../android/AdbMdns.java`, que usa `adb-tls-pairing`, `adb-tls-connect` y `adb`, y en el comentario de `packages/modules/adb/adb_mdns.cpp` sobre `_adb-tls-connect._tcp`]:
  - **`_adb-tls-pairing._tcp`**: puerto de vinculación. Solo existe mientras está abierto el diálogo de «Vincular dispositivo con código».
  - **`_adb-tls-connect._tcp`**: puerto de conexión TLS. Cambia cada vez que se activa la depuración inalámbrica.
- Con `NsdManager`:
  ```kotlin
  val nsd = ctx.getSystemService(NsdManager::class.java)
  nsd.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
      override fun onServiceFound(si: NsdServiceInfo) {
          nsd.resolveService(si, object : NsdManager.ResolveListener {   // API 34+: registerServiceInfoCallback
              override fun onServiceResolved(r: NsdServiceInfo) { /* r.port, r.host; filtrar por IP propia */ }
              override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
          })
      }
      override fun onDiscoveryStarted(t: String) {}; override fun onDiscoveryStopped(t: String) {}
      override fun onServiceLost(s: NsdServiceInfo) {}
      override fun onStartDiscoveryFailed(t: String, e: Int) {}; override fun onStopDiscoveryFailed(t: String, e: Int) {}
  })
  ```
  libadb-android trae `AdbMdns` y los métodos `connectTls`/`autoConnect`, que hacen esto mismo.
- **Android 17 (targetSdk 37)**: la protección de red local es obligatoria y hace falta el permiso `ACCESS_LOCAL_NETWORK` (en Android 16 se usaba `NEARBY_WIFI_DEVICES`). Resolver servicios `.local` también lo necesita: <https://developer.android.com/privacy-and-security/local-network-permission> [FUENTE SECUNDARIA: copias traducidas de la página oficial]. Si conectar a `127.0.0.1` está exento [SIN VERIFICAR].
- Una vez concedido WSS, la app puede escribir `Settings.Global.adb_wifi_enabled=1` para volver a encender la depuración inalámbrica sin pasar por Ajustes (Shizuku usa WSS para esto, según su documentación) [VERIFICADO que la clave existe y es Global; que funcione en cada fabricante, SIN VERIFICAR].
  - Las autorizaciones ADB caducan si no se usan en `DEFAULT_ADB_ALLOWED_CONNECTION_TIME = 604800000` ms (7 días) [VERIFICADO], salvo que se active «Desactivar el tiempo de espera de autorizaciones adb».
  - Si esto afecta también a las claves emparejadas por la vía inalámbrica está [SIN VERIFICAR].

### 4.3 Alternativa: Shizuku

- API: `dev.rikka.shizuku:api` y `dev.rikka.shizuku:provider` **13.1.5** en Maven Central (actualizada en septiembre de 2023), licencia MIT (<https://github.com/RikkaApps/Shizuku-API>). App Shizuku v13.6.0 (25 de mayo de 2025).
- Ventaja: no hay que implementar ADB. Inconveniente: es otra app, que se para al reiniciar si no hay root y hay que volver a arrancarla desde la depuración inalámbrica.
- Recomendación: soportar las dos vías. Primero libadb integrado, para una sola concesión de WSS; Shizuku como opción. **Con WSS ya concedido no hace falta ADB en el día a día.** El shell ADB solo se necesita para lo que WSS no permite (mono/balance, `pm enable`, `pm install-existing`, `uninstall-system-updates`, `force-stop`).

---

## 5. Nombres de paquete conocidos

**Regla de diseño**: no fiarse de listas fijas. Detectar en tiempo de ejecución:

- **lectores** con `AccessibilityManager.getInstalledAccessibilityServiceList()`, filtrando por `CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION` o `FEEDBACK_SPOKEN`;
- **motores TTS** con `queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE))`.

### 5.1 Lectores de pantalla y servicios de accesibilidad

| Producto | Paquete / componente | Estado |
|---|---|---|
| Google TalkBack (Android Accessibility Suite) | `com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService` | VERIFICADO: clase en el manifiesto de google/talkback; paquete en muchas guías (por ejemplo, los scripts de devicelab de Flutter) |
| Selección para pronunciar (misma Suite) | `com.google.android.marvin.talkback/com.google.android.accessibility.selecttospeak.SelectToSpeakService` | Usado en las pruebas de Shortcut Menu |
| Samsung TalkBack (One UI 3 a 8) | Paquete `com.samsung.android.accessibility.talkback`; servicio probable `com.samsung.android.marvin.talkback.TalkBackService` | Paquete usado en las pruebas de Shortcut Menu; servicio SIN VERIFICAR. One UI 9 pasa al de Google |
| Backtalk (fork de TalkBack) | `fyi.quin.backtalk/com.google.android.marvin.talkback.TalkBackService` | Pruebas de Shortcut Menu |
| Jieshuo / Commentary (解说) | `com.nirenr.talkman` | SIN VERIFICAR (el prefijo coincide con el desarrollador «nirenr», <https://newreleases.io/project/github/nirenr/jieshuo/release/20240501>) |
| Prudence Screen Reader | `com.prudence.reader` | FUENTE SECUNDARIA (enlace de Play en AppleVis y Telegram) |
| Lectores de Huawei, Xiaomi, OPPO y vivo (China; desarrollos de BYHD «保益» o «点明» Dianming) | p. ej. `com.bjbyhd.screenreader_huawei`, `com.dianming.phoneapp` | SIN VERIFICAR: no he encontrado fuentes fiables. Hay que detectarlos dinámicamente |
| Acceso con interruptores (Google) | Dentro de `com.google.android.marvin.talkback` | Nombre de clase SIN VERIFICAR: leerlo con `settings get secure enabled_accessibility_services` |
| Shortcut Menu | `io.github.aaron_gh.shortcutmenu/.ShortcutMenuService` | VERIFICADO (copia local) |

### 5.2 Motores TTS

| Motor | Paquete | Estado |
|---|---|---|
| Google, «Speech Recognition & Synthesis» | `com.google.android.tts` | VERIFICADO (URL de Play; constante `PACKAGE_GOOGLE_TTS` en TalkBack) |
| Samsung TTS | `com.samsung.SMT` | SIN VERIFICAR (dato conocido) |
| eSpeak NG | `com.reecedunn.espeak` | VERIFICADO (F-Droid) |
| RHVoice | `com.github.olga_yakovleva.rhvoice.android` | VERIFICADO (F-Droid) |
| SherpaTTS | `org.woheller69.ttsengine` | VERIFICADO (F-Droid, versión 3.4) |
| Pico (AOSP) | `com.svox.pico` | VERIFICADO (manifiesto de AOSP) |
| Vocalizer TTS (Code Factory) | `es.codefactory.vocalizertts` | FUENTE SECUNDARIA (espejos y SimilarWeb) |
| Eloquence (Code Factory) | `es.codefactory.eloquencetts` | SIN VERIFICAR |
| Acapela TTS | `com.acapelagroup.android.tts` | SIN VERIFICAR |
| MultiTTS | `org.nobody.multitts` | SIN VERIFICAR |

### 5.3 Pantallas de inicio automático y batería de los fabricantes

Fuente: AutoStarter (MIT), `AutoStartPermissionHelper.kt` en <https://github.com/judemanutd/AutoStarter> [VERIFICADO en el código], y <https://dontkillmyapp.com/>. Las variantes cambian con cada versión de la capa del fabricante, así que **cada `startActivity` debe ir en un `try`/`catch`**.

| Marca | Componentes |
|---|---|
| Xiaomi / Redmi / POCO | `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity` |
| Huawei / Honor | `com.huawei.systemmanager/.optimize.process.ProtectActivity`; `com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity` |
| OPPO / realme (ColorOS) | `com.coloros.safecenter/.permission.startup.StartupAppListActivity`; `com.coloros.safecenter/.startupapp.StartupAppListActivity`; `com.oppo.safe/.permission.startup.StartupAppListActivity` |
| vivo / iQOO | `com.iqoo.secure/.ui.phoneoptimize.AddWhiteListActivity`; `com.iqoo.secure/.ui.phoneoptimize.BgStartUpManager`; `com.vivo.permissionmanager/.activity.BgStartUpManagerActivity` |
| Samsung | `com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity`; `.../com.samsung.android.sm.battery.ui.BatteryActivity`; `.../com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity` |
| OnePlus | `com.oneplus.security/.chainlaunch.view.ChainLaunchAppListActivity`; acción `com.android.settings.action.BACKGROUND_OPTIMIZE` |
| ASUS | `com.asus.mobilemanager/.powersaver.PowerSaverSettings`; `com.asus.mobilemanager/.autostart.AutoStartActivity` |
| Nokia (HMD) | `com.evenwell.powersaving.g3/.exception.PowerSaverExceptionActivity` |
| Letv | `com.letv.android.letvsafe/.AutobootManageActivity` |

Genérico y público: `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (con `package:`) y `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.

---

## 6. Consecuencias para la arquitectura (resumen razonado)

1. **Servicio de rescate** (`RescueService`):
   - `isAccessibilityTool=true`, `directBootAware=true`, `flagEnableAccessibilityVolume`, `flagRequestShortcutWarningDialogSpokenFeedback`, `canRetrieveWindowContent=true` (para la navegación guiada) y `canControlMagnification`.
   - **Sin** `flagRequestAccessibilityButton` (ver 3.1).
   - Es el **único** destino de las teclas de volumen. Al terminar, `disableSelf()`, para que el atajo siempre lo *encienda*.
2. **Voz propia** con Pico, mediante JNI y `AudioTrack` con `USAGE_ASSISTANCE_ACCESSIBILITY`, sin depender del TTS del sistema. Opción de forzar el altavoz.
3. **Motor TTS del sistema** (`TextToSpeechService`) sobre el mismo Pico, para que TalkBack pueda usarlo con `tts_default_synth`.
4. **Escalera de permisos**:
   - **Nivel 0**: sin permisos especiales, solo navegación guiada.
   - **Nivel 1**: WSS (claves Secure y Global).
   - **Nivel 2**: shell ADB bajo demanda (System oculto, `pm`, `am`, `cmd`).
5. **Copia de APK**: guardar TalkBack y el TTS (base + splits) cuando funcionan. Reinstalar con PackageInstaller (confirmación pulsada por nuestro servicio) o con `pm install-*` por shell.
6. **Diagnóstico al activarse**, leyendo:
   - `enabled_accessibility_services`, `tts_default_synth` y los motores instalados;
   - `STREAM_ACCESSIBILITY` y `STREAM_MUSIC`;
   - `master_mono` y `master_balance`;
   - los destinos del atajo;
   - lectores duplicados;
   - el dispositivo de salida de audio activo.

---

## 7. Fuentes principales

**Código AOSP** (`android.googlesource.com`, rama `main` salvo indicación):

- `frameworks/base/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java` (también `android10-release`)
- `.../accessibility/AccessibilityServiceConnection.java`, `.../AccessibilityInputFilter.java`
- `frameworks/base/core/java/android/accessibilityservice/AccessibilityServiceInfo.java`, `AccessibilityService.java`
- `frameworks/base/core/java/com/android/internal/accessibility/AccessibilityShortcutController.java`, `.../dialog/AccessibilityServiceWarning.java`
- `frameworks/base/core/java/android/view/View.java`, `ViewConfiguration.java`
- `frameworks/base/core/java/android/provider/Settings.java`; `packages/SettingsProvider/.../SettingsProvider.java`
- `frameworks/base/services/core/java/com/android/server/audio/AudioService.java`; `media/java/android/media/AudioAttributes.java`
- `frameworks/base/core/java/android/speech/tts/TextToSpeechService.java`, `TtsEngines.java`, `TextToSpeech.java`
- `frameworks/base/core/java/android/content/pm/PackageInstaller.java`; `services/core/java/com/android/server/pm/PackageManagerShellCommand.java`
- `frameworks/base/core/java/android/service/quicksettings/TileService.java`
- `frameworks/base/core/res/AndroidManifest.xml`; `packages/apps/Settings/AndroidManifest.xml`, `.../accessibility/AccessibilityDetailsSettingsFragment.java`
- `platform/external/svox` (`pico/lang`, `pico/lib`, `pico/tts/com_svox_picottsengine.cpp`, `pico/AndroidManifest.xml`)
- `packages/modules/adb/adb_mdns.cpp`

**Otros proyectos**:

- TalkBack: <https://github.com/google/talkback> (`AndroidManifest.xml`, `res/xml/accessibilityservice.xml`, `FailoverTextToSpeech.java`, `TextToSpeechUtils.java`)
- eSpeak NG: <https://github.com/espeak-ng/espeak-ng> (1.52.0: `ChangeLog.md`, `CMakeLists.txt`, `cmake/*.cmake`, `android/build.gradle`, `android/jni/CMakeLists.txt`, `src/libespeak-ng/synthdata.c`)
- sherpa-onnx: <https://github.com/k2-fsa/sherpa-onnx> (release v2.0.0, `tts-models`, PR #4055, `CHANGELOG.md`)
- Piper voices: <https://huggingface.co/rhasspy/piper-voices>
- libadb-android: <https://github.com/MuntashirAkon/libadb-android>; Shizuku API: <https://github.com/RikkaApps/Shizuku-API>
- AutoStarter: <https://github.com/judemanutd/AutoStarter>; <https://dontkillmyapp.com/>
- Debian: <https://sources.debian.org/src/espeak-ng/>, <https://metadata.ftp-master.debian.org/changelogs/non-free/s/svox/unstable_copyright>

**Comunidad y documentación de usuario**: todos los enlaces citados en la sección 1. Están las páginas de ayuda de Google (`support.google.com/accessibility/android/answer/6006589`, `6283655`, `7650693`, `14252950`, `6301498`), Accessible Android, el grupo Google «android_accesible», groups.io androidaccessibility, las comunidades de Samsung y Sony, el blog de fireborn y Android Authority.
