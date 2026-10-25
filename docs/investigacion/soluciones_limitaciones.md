# Cómo superar o reducir las limitaciones de Rescate Accesible

Investigación sobre las limitaciones recogidas en `docs/limitaciones.md`, cruzada con la
arquitectura de `docs/arquitectura.md`. Para cada tema se indica qué se puede hacer con API
pública, con `WRITE_SECURE_SETTINGS`, con el shell ADB integrado (depuración inalámbrica, uid
`shell` = 2000), con root, y qué queda imposible.

**Leyenda de fiabilidad**
- **[VERIFICADO]**: comprobado en una fuente citada (código AOSP, código del proyecto real,
  documentación oficial).
- **[SUPUESTO]**: deducción o testimonio de foro no confirmado en fuente primaria.

**Advertencia transversal [VERIFICADO]**: que un permiso esté declarado en el manifest del shell
no garantiza que la operación funcione en todo dispositivo. SELinux, AppOps, flags de framework y
las capas de fabricante pueden bloquearla. Fuente: https://technastic.com/adb-shell-permissions-android/

**Contexto de permisos del shell ADB [VERIFICADO]**: el uid `shell` (AID_SHELL = 2000,
`system/core/libcutils/include/private/android_filesystem_config.h`) corre como el paquete de
sistema `com.android.shell` (sharedUserId `android.uid.shell`), que en su `AndroidManifest.xml`
declara, entre otros: `WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`, `CHANGE_CONFIGURATION`,
`INSTALL_PACKAGES`, `INSTALL_PACKAGE_UPDATES`, `DELETE_PACKAGES`,
`CHANGE_COMPONENT_ENABLED_STATE`, `MANAGE_ACCESSIBILITY`, `FORCE_STOP_PACKAGES`,
`RETRIEVE_WINDOW_CONTENT`. Por tanto el shell ADB tiene de facto más poder que la app con solo
`WRITE_SECURE_SETTINGS`.
Fuentes: https://android.googlesource.com/platform/system/core/+/master/libcutils/include/private/android_filesystem_config.h
· https://technastic.com/adb-shell-permissions-android/ · https://sysapps.bayton.org/packages/com.android.shell

**`WRITE_SECURE_SETTINGS` — alcance [VERIFICADO]**: escribe `Settings.Secure` y `Settings.Global`,
NO `Settings.System` (eso necesita `WRITE_SETTINGS`). Nivel de protección
`signature|privileged|development|role|installer`, comentario AOSP "Not for use by third-party
applications". Al tener el flag `development`, se concede por adb con
`pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` y sobrevive a reinicios.
Fuentes: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml
· https://juejin.cn/post/7371745917495525402 · https://github.com/MuntashirAkon/SetEdit
· restricción nueva en Android 16 sobre `WRITE_DEVICE_CONFIG` (no afecta a Secure/Global estándar): https://discuss.grapheneos.org/d/1554/1

---

## 1. Activar servicios de accesibilidad apagados, cambiar el motor TTS, audio mono/balance, desinstalar actualizaciones, habilitar apps, forzar detención, idioma del sistema

Resumen de la limitación: muchas acciones "solo se pueden leer" o "necesitan el shell" o "solo
desde la pantalla de info de la app".

### 1.1 Activar un servicio de accesibilidad (incluida la propia app o TalkBack)
- Clave: `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` (lista separada por `:` de
  `<pkg>/<service>`). A veces conviene además `accessibility_enabled=1`. **[VERIFICADO]** es la
  forma estándar por shell/WRITE_SECURE_SETTINGS.
  Fuentes: https://docs.appetize.io/guides-and-samples/test-with-the-talkback-screen-reader
  · https://discussion.datalogic.com/t/grant-accessibility-permission-to-a-third-party-app/3115
- **(a) API pública**: NO (una app no puede autoactivarse como servicio de accesibilidad).
- **(b) WRITE_SECURE_SETTINGS**: SÍ — escribir la lista conservando los demás componentes.
- **(c) shell ADB**: SÍ — `settings put secure enabled_accessibility_services <lista>`.
- **(d) root**: SÍ.
- `cmd accessibility` NO tiene subcomando `enable/disable` de servicios. **[VERIFICADO]** sus
  subcomandos reales son `get/set-bind-instant-service-allowed`, `call-system-action`,
  `start-trace`, `stop-trace`, `check-hidraw`; todos exigen `MANAGE_ACCESSIBILITY` (que el shell
  tiene), y `call-system-action` limita a ROOT/SYSTEM/SHELL_UID.
  Fuente: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/AccessibilityShellCommand.java
  - `call-system-action <id>` **[VERIFICADO]** permite al shell lanzar acciones globales del
    sistema (back, home, recents, notificaciones, etc.) sin servicio de accesibilidad: útil como
    respaldo del lector de rescate desde el shell.

### 1.2 Cambiar el motor TTS predeterminado
- Clave: `Settings.Secure` `tts_default_synth` (ya lo usa la app con permiso avanzado, según la
  arquitectura). **[VERIFICADO]** es Secure. (b) WRITE_SECURE_SETTINGS: SÍ; (c) shell: SÍ;
  (a) API pública: NO. TalkBack escucha esa clave y cambia de motor al momento.
- Velocidad/tono globales del motor: `tts_default_rate`, `tts_default_pitch` (Secure, 100 = 1x).
  **[VERIFICADO]** https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/Settings.java

### 1.3 Audio mono y balance
- `master_mono` y `master_balance` están en **`Settings.System`** (NO Secure). **[VERIFICADO]**
  (AudioService registra observers en Settings.System).
  Fuente: https://gerrit.omnirom.org/plugins/gitiles/android_frameworks_base/+/1eb5088a8033469607e87c940b5cac82ce8154cd%5E%21
- **(a) API pública**: NO. **(b) solo WRITE_SECURE_SETTINGS**: **NO** (System necesita
  WRITE_SETTINGS). **(c) shell ADB**: **SÍ** (`com.android.shell` tiene WRITE_SETTINGS):
  `settings put system master_mono 0` / `settings put system master_balance 0.0`. **(d) root**: SÍ.
- Conclusión importante: mono/balance es de los pocos casos donde `WRITE_SECURE_SETTINGS` **no
  basta** y el shell ADB sí resuelve.

### 1.4 Desinstalar actualizaciones, habilitar/inhabilitar apps, forzar detención
- `pm enable <pkg>` / `pm disable-user <pkg>`: el shell tiene `CHANGE_COMPONENT_ENABLED_STATE`.
  **[VERIFICADO]** commit AOSP "Allow Shell to change component enabled state"
  https://android.googlesource.com/platform/frameworks/base/+/bf12565%5E%21/
- `cmd package uninstall-system-updates <pkg>` (revertir una app de sistema a fábrica): ejecutable
  por shell. **[VERIFICADO]** https://sysapps.bayton.org/packages/com.oem.systemupdate
  (`pm uninstall-system-updates` suele delegar en `cmd package`; **[SUPUESTO]** ambas formas valen).
- `cmd package install-existing <pkg>` / `pm install-existing`: reinstala para el usuario actual
  una app desinstalada con `pm uninstall --user 0`. **[VERIFICADO (shell)]**
  https://community.e.foundation/t/howto-uninstall-default-apps/77024/1
- `am force-stop <pkg>`: shell tiene `FORCE_STOP_PACKAGES`. **[SUPUESTO/estándar]** (uso clásico de
  adb). CUIDADO: force-stop de un servicio de accesibilidad lo quita de
  `enabled_accessibility_services` (ver §4 y §5).
- `pm clear --cache-only <pkg>`: borra solo caché. **[VERIFICADO parcialmente]** funciona en
  Android 13 y anteriores; **[SUPUESTO]** `--cache-only` añadido ~API 33.
  Fuente: https://technastic.com/?p=27999 · lógica installd https://android.googlesource.com/platform/frameworks/native/+/1f6a7f1%5E%21/
- **(a) API pública** para todo lo anterior sobre OTROS paquetes: NO. **(c) shell**: SÍ a todo.
  **(d) root**: SÍ. **(b) WRITE_SECURE_SETTINGS no cubre pm/am** (son otros permisos).
- Automatizar la pantalla de info de la app con accesibilidad sigue siendo la vía sin shell; ver
  §8 para los nombres de recursos de los botones.

### 1.5 Idioma del sistema
- No hay `settings put` estándar para el locale del sistema. **[VERIFICADO/mixto]**
  - Per-app: `cmd locale set-app-locales <pkg> --locales <tags>` (LocaleManager, API 33+); una app
    puede cambiar SU propio locale con `LocaleManager`/`AppCompatDelegate` (API pública).
    Fuente: https://github.com/VegaBobo/Language-Selector
  - Sistema por shell: `am update-config --locale <tag>` (requiere `CHANGE_CONFIGURATION`, que el
    shell tiene; puede estar restringido en stock) o, en ramas recientes (Android 16+),
    `cmd locale set-device-locale`. **[SUPUESTO]** disponibilidad exacta.
    Fuentes: https://android.googlesource.com/platform/development/+show/refs/heads/temp-android16-plus/apps/CustomLocale/README.md
    · patrón MoreLocale2/Appium con `pm grant CHANGE_CONFIGURATION`: https://discuss.appium.io/t/how-to-change-the-language-locale-on-android-emulator/9860
  - **(a)** solo el locale de la propia app; **(b)** WRITE_SECURE_SETTINGS NO basta (hace falta
    `CHANGE_CONFIGURATION`); **(c) shell**: SÍ (`am update-config` / `cmd locale set-device-locale`);
    **(d) root**: SÍ.
  - `settings put system system_locales ...` sugerido en foros: **[SUPUESTO / no fiable]**.

---

## 2. Mantener viva o reconectar la depuración inalámbrica sin volver a emparejar

**Hallazgo clave [VERIFICADO]**: Shizuku oficial (13.6.0+) ya hace exactamente esto SIN root en
Android 13+. La receta es: (1) escribir `Settings.Global adb_wifi_enabled=1` con
`WRITE_SECURE_SETTINGS`; (2) descubrir el puerto por NSD (`_adb-tls-connect._tcp`); (3) conectar a
`127.0.0.1:<puerto>` con la MISMA clave RSA del emparejamiento.
Fuentes: https://github.com/RikkaApps/Shizuku (BootCompleteReceiver.kt, AdbMdns.kt)
· https://github.com/RikkaApps/Shizuku/releases (nota 13.6.0: "auto start without root on Android13+ when connected to a trusted WLAN")

### 2.1 Persistencia de las claves emparejadas
- **[VERIFICADO]** adbd solo lee `/data/misc/adb/adb_keys`. Al emparejar con éxito,
  `AdbDebuggingManager` ejecuta `setLastConnectionTime` + `persistKeyStore`, que reescribe
  `adb_keys`; la clave emparejada por Wi-Fi persiste entre reinicios. `adb_temp_keys.xml` guarda la
  última conexión y los BSSID de confianza.
  Fuentes: https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/adb_wifi.md
  · https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/adb/AdbDebuggingManager.java
- **[VERIFICADO]** adbd valida la conexión TLS solo comparando la CLAVE PÚBLICA del cliente
  (`adbd_tls_verify_cert` → `EVP_PKEY_cmp`), no la caducidad del certificado X.509. Lo crítico es
  reutilizar exactamente el mismo par de claves.
  Fuente: https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/daemon/auth.cpp
- **Caducidad de 7 días [VERIFICADO]**: `DEFAULT_ADB_ALLOWED_CONNECTION_TIME = 604800000` (7 días);
  `filterOutOldKeys()` borra de `adb_keys` las claves no usadas en ese plazo. Con
  `adb_allowed_connection_time=0` no caducan. **[VERIFICADO en código / SUPUESTO en consecuencia]**
  una conexión Wi-Fi (`MSG_WIFI_DEVICE_CONNECTED`) NO llama a `setLastConnectionTime`, así que una
  clave Wi-Fi podría caducar ~7 días tras el emparejamiento aunque se use. Shizuku pone
  `adb_allowed_connection_time=0` en cada arranque; conviene hacer lo mismo con
  `WRITE_SECURE_SETTINGS`.
- **[VERIFICADO]** El dispositivo sigue emparejado hasta que el usuario pulse "Revoke adb debugging
  authorizations" u "Olvidar". https://developer.android.com/tools/adb

### 2.2 Puerto dinámico y descubrimiento local
- **[VERIFICADO]** El puerto de conexión cambia en cada activación (`new TlsServer(0)`, puerto
  efímero) y puede cambiar si adbd reinicia. adbd registra mDNS `_adb-tls-connect._tcp`.
  Fuente: https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/daemon/adb_wifi.cpp
- **[VERIFICADO]** adbd escucha en todas las interfaces (bind a `in6addr_any`), así que
  `127.0.0.1` funciona. libadb-android usa `127.0.0.1` por defecto.
  Fuentes: https://android.googlesource.com/platform/system/core/+/refs/heads/main/libcutils/socket_inaddr_any_server_unix.cpp
  · https://github.com/MuntashirAkon/libadb-android
- **[VERIFICADO]** Descubrimiento en el propio dispositivo con `NsdManager.discoverServices("_adb-tls-connect._tcp")`,
  filtrando por IP local / probando el puerto en 127.0.0.1 (como Shizuku `AdbMdns.kt` y LADB
  `DnsDiscover.kt`). Alternativa: barrido de puertos 32768–60999 (adb-auto-enable).
  Fuentes: https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/java/moe/shizuku/manager/adb/AdbMdns.kt
  · https://github.com/mouldybread/adb-auto-enable
- **[VERIFICADO]** NO es viable leer `service.adb.tls.port` desde la app: la propiedad tiene
  contexto SELinux `adbd_prop` (system_internal_prop) y `appdomain` no puede leerla. La API oculta
  `IAdbManager.getAdbWirelessPort()` exige `MANAGE_DEBUGGING` (signature|privileged).
  Fuentes: https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/property_contexts
  · https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/adb/AdbService.java

### 2.3 Activar la depuración inalámbrica con WRITE_SECURE_SETTINGS
- **[VERIFICADO]** `Settings.Global.ADB_WIFI_ENABLED = "adb_wifi_enabled"` existe desde Android 11
  (comprobado: no está en android10-release, sí en 11–15 y main). `AdbService` registra un
  ContentObserver: al cambiar a 1 pone `persist.adb.tls_server.enable=1`, toma MulticastLock,
  arranca adbd y llama a `setAdbEnabled(...,WIFI)`. La app Ajustes usa el mismo mecanismo.
  Fuentes: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/adb/AdbService.java
  · https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/development/WirelessDebuggingEnabler.java
- **[VERIFICADO]** USB (`adb_enabled`) NO es necesario según el código (transportes independientes),
  pero Shizuku/LADB ponen también `ADB_ENABLED=1` por precaución. Recomendado imitarlo.
- **Condiciones duras que impiden activarlo en silencio [VERIFICADO]** (en AdbDebuggingManager):
  1. **Wi-Fi conectado**. Sin red (`networkId == -1`) o sin BSSID, reescribe `adb_wifi_enabled=0`.
  2. **BSSID de confianza**. Si el BSSID no está en `mTrustedNetworks`, muestra diálogo al usuario
     y vuelve a poner 0. La red se añade solo si el usuario marca "Permitir siempre en esta red".
  3. Un BroadcastReceiver pone 0 si se apaga el Wi-Fi, se pierde la conexión o cambia el BSSID.
  4. `DISALLOW_DEBUGGING_FEATURES` bloquea cualquier valor ≠ 0.
  5. Desactivar Opciones de desarrollador pone `adb_wifi_enabled=0`.
- **Diferencias por versión [VERIFICADO]**: la clave y el observer existen desde Android 11, pero
  Shizuku solo habilita el autoarranque por ADB con `SDK_INT >= 33` (Android 13), citando
  `r.android.com/2128832`. **[SUPUESTO]** en Android 11–12 el autoarranque puede no ser fiable.
  Android 17 ("ADB Wi-Fi 2.0") apaga/enciende solo según redes de confianza.
  Fuente: https://android-developers.googleblog.com/2026/09/wireless-debugging-adb-wifi-2.html

### 2.4 Conectar al propio adbd sin intervención del usuario — veredicto
- **SÍ es posible [VERIFICADO vía Shizuku]**: con la clave emparejada guardada + WRITE_SECURE_SETTINGS
  la app escribe `adb_wifi_enabled=1`, descubre el puerto por NSD y conecta a 127.0.0.1 con su clave,
  obteniendo shell sin que el usuario toque nada. **Condiciones:** Wi-Fi conectado a una red cuyo
  BSSID el usuario marcó como "siempre permitida", y la clave no caducada (`adb_allowed_connection_time=0`).
- **Mantener viva la conexión [SUPUESTO, bien apoyado]**: hace falta un servicio en primer plano que
  vigile `adb_wifi_enabled` (ContentObserver) y la red, y repita activar→descubrir→conectar. La
  conexión se corta (`kick_all_tcp_tls_transports`) al cambiar/perder red. **Mejor práctica
  [VERIFICADO]**: lanzar un proceso propio con `app_process` (uid shell) y cerrar ADB; ese proceso
  vive hasta el siguiente reinicio (patrón Shizuku/libadb).
- Proyectos de referencia: Shizuku, LADB (https://github.com/tytydraco/LADB), adb-auto-enable,
  KeepADB (https://f-droid.org/packages/de.hohnepeople.keepadb/), libadb-android.

**Implicación estratégica**: si la app logra que el usuario, UNA vez, (a) empareje por ADB
inalámbrico, (b) conceda `WRITE_SECURE_SETTINGS` por ese shell, y (c) marque su Wi-Fi de casa como
"siempre permitida", entonces en cada rescate puede re-obtener shell sola (con Wi-Fi) y ejecutar
todo lo de §1 sin pantallas de Ajustes. Es la palanca más potente del proyecto.

---

## 3. Asignar el atajo de volumen a la app sin que el usuario lo haga

- Clave: `Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE` = `<pkg>/<service>` (atajo por
  hardware = teclas de volumen). **[VERIFICADO]**
  Fuentes: https://code.yawk.at/android/android-9.0.0_r35/com/android/internal/accessibility/AccessibilityShortcutController.java
  · fix de valor vacío https://android.googlesource.com/platform/frameworks/base/+/72c52dfb48cc
  - **(b) WRITE_SECURE_SETTINGS**: SÍ. **(c) shell**: SÍ (`settings put secure accessibility_shortcut_target_service <comp>`).
    **(a) API pública**: NO. **(d) root**: SÍ.
  - Escribir cadena vacía o inválida rompe el atajo (hay fix AOSP que mete valor por defecto).
- Claves relacionadas (Settings.Secure, escribibles por shell/WRITE_SECURE_SETTINGS):
  - `accessibility_shortcut_on_lock_screen` (1 = disponible en pantalla de bloqueo). **[VERIFICADO]**
    `AccessibilityShortcutController.isAccessibilityShortcutAvailable(phoneLocked)` usa
    `mEnabledOnLockScreen`, que por defecto vale lo que valga `DIALOG_SHOWN`.
    Fuente: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/accessibility/AccessibilityShortcutController.java
  - `accessibility_shortcut_dialog_shown` (1 = ya se mostró el diálogo de aviso → el atajo queda
    operativo y por defecto habilitado en bloqueo). **[VERIFICADO funcionalmente]** en el mismo
    controlador. Poniéndolo a 1 se evita el diálogo inicial.
  - `accessibility_button_targets` / `accessibility_button_long_press_targets` (botón software,
    lista `:`), en Android 11+ tras el rediseño. **[VERIFICADO]**
- **Android 8–10 vs 11+ [VERIFICADO/mixto]**: el atajo por volumen llegó en Android 8.0; la clave del
  servicio objetivo ya era `accessibility_shortcut_target_service` (visible en AOSP 9), con
  `accessibility_shortcut_enabled` para activar/desactivar el atajo. En Android 11 (rediseño) se
  generalizaron atajos (botón/gesto/hardware) y listas multi-objetivo. Habilitar el servicio en sí
  siempre va por `enabled_accessibility_services`; `..._target_service` solo define QUÉ lanza el
  atajo, no lo habilita.
- **Qué hace Android cuando el servicio del atajo se apaga [VERIFICADO]**: la arquitectura ya evita
  `flagRequestAccessibilityButton` porque AOSP quita del atajo a un servicio con ese flag al
  desvincularse (`removeShortcutTargetForUnboundServiceLocked`). Con `disableSelf()` el
  `ServicioAtajo` deja de estar vinculado; el valor de `accessibility_shortcut_target_service`
  permanece (es solo una referencia a componente), así que el atajo vuelve a arrancarlo la próxima
  vez. Confirmar que no se limpia en tu versión objetivo.
- **Alternativa Android 11+ sin AccessibilityService [VERIFICADO, muy recomendable]**: el atajo puede
  apuntar a una **Activity** mediante `Intent.CATEGORY_ACCESSIBILITY_SHORTCUT_TARGET` + meta-data
  `android.accessibilityshortcut.target` (XML `<accessibility-shortcut-target>` con `description` y
  `summary`). El usuario mantiene las dos teclas de volumen y se abre `ActividadRescate`
  directamente, sin los requisitos de política de Play de un AccessibilityService.
  Fuente: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/accessibilityservice/AccessibilityShortcutInfo.java

---

## 4. Detectar y deshacer «TalkBack suspendido» y «TalkBack activado pero caído»

### 4.1 «TalkBack suspendido / pausado»
- **[VERIFICADO]** El "suspend" clásico (`suspendTalkBack`, pref `pref_suspended`, broadcast
  `RESUME_FEEDBACK`) **se eliminó en TalkBack 13.0** (commit bdead86e, 2022). Además, desde Android 8
  no hacía nada en teléfono/tableta (`hasAccessibilityShortcut()` lo cortocircuita).
  Fuente: https://github.com/google/talkback/commit/bdead86e21beae508fa1fd7a24a06608485e1c29
  - **Solo afecta a Android 7.x con TalkBack ≤ 12** (tu minSdk es 24). Ahí: detectar "TalkBack
    vinculado + `isTouchExplorationEnabled()`== false" como firma del suspend, o la notificación
    `R.id.notification_suspended` vía `NotificationListenerService`. Reanudar: disparar el
    PendingIntent de esa notificación (`sbn.notification.contentIntent.send()`), o apagar/encender
    pantalla (reanuda con `SCREEN_ON`/`KEYGUARD`). El broadcast `RESUME_FEEDBACK` exige permiso de
    firma: ni la app ni el shell pueden enviarlo; con root sí (`am broadcast -a ... -p <pkg>`).
    Reactivar el servicio NO sirve (se vuelve a suspender por `pref_suspended`); habría que borrar
    esa pref con root antes.
- **Equivalente moderno (TalkBack 13–16.2) «activado pero mudo» [VERIFICADO]**: la acción
  `TOGGLE_VOICE_FEEDBACK` ("Turn speech on or off", gesto por defecto: 2 dedos triple toque y
  mantener; teclado `KEYCODE_VOLUME_MUTE` + modificador). Pone `isMuteSpeech` **solo en memoria**,
  no en SharedPreferences, y no hay API externa para detectarlo.
  Fuente: https://github.com/google/talkback/blob/229212fdf5842191d0a93fc95d9ca1423b346866/utils/src/main/java/com/google/android/accessibility/utils/output/SpeechControllerImpl.java
  - **Deshacerlo [VERIFICADO+SUPUESTO]**: reiniciar el servicio crea un `SpeechControllerImpl` nuevo
    con `isMuteSpeech=false` → vuelve a hablar. Ver §4.2 para reiniciarlo sin apagarlo.
  - "Pause or resume speech" (2 dedos, 1 toque) solo pausa la frase en curso, no el servicio.

### 4.2 «TalkBack activado pero caído»
- **[VERIFICADO]** AOSP mantiene estados off/bound/binding/**crashed** por servicio.
  `getEnabledAccessibilityServiceList()` devuelve los **vinculados** (`mBoundServices`), no la lista
  de Settings. Algoritmo de detección:
  1. `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` contiene TalkBack (está "encendido").
  2. TalkBack NO aparece en `getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)`.
  3. Si 1 y 2 persisten unos segundos → está en binding o crashed.
  - Señales extra: `isTouchExplorationEnabled()==false`; por shell `pidof com.google.android.marvin.talkback`
    o `dumpsys accessibility` (secciones Bound/Enabled/Crashed, requiere DUMP → shell/root).
  Fuente: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java
- **Reiniciar TalkBack sin desactivarlo [VERIFICADO]**: reescribir `enabled_accessibility_services`
  en DOS pasos (quitar TalkBack, esperar a que desaparezca de la lista de vinculados, volver a
  ponerlo) fuerza `unbindLocked()`+`bindLocked()`. Escribir el mismo valor no hace nada.
  - **(b) WRITE_SECURE_SETTINGS**: SÍ. **(c) shell**: SÍ. **(d) root**: SÍ.
  - **NO usar `am force-stop com.google.android.marvin.talkback` a secas [VERIFICADO]**: lo quita de
    `enabled_accessibility_services` y deja al ciego sin lector. Si se usa, hay que volver a añadirlo.
  - `accessibility_enabled` NO sirve para reiniciar (AMS solo lo escribe, no lo observa). **[VERIFICADO]**
  - Alternativa con root: `am crash <pkg>` o `kill -9` matan el proceso sin tocar la lista; el
    sistema relanza por `BIND_AUTO_CREATE`. **[SUPUESTO]**
- **Paquete/componente [VERIFICADO]**: Play = `com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`
  (sin cambios). Build F-Droid = `com.android.talkback/...`. Samsung lleva su propio TalkBack
  **[SUPUESTO]** `com.samsung.android.accessibility.talkback`. Recomendación: no hardcodear; buscar
  en `getInstalledAccessibilityServiceList()` por paquete que contenga "talkback" o clase que acabe
  en `.TalkBackService`.

---

## 5. Evitar que los «limpiadores»/ahorro de batería maten el guardián

### 5.1 ¿Es inmune un servicio de accesibilidad activo? NO
- **[VERIFICADO]** Dos casos distintos:
  - **Kill del proceso** (LMK, crash): el sistema lo **relanza** solo, porque la conexión usa
    `BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE_WHILE_AWAKE | BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`.
    Fuente: https://android.googlesource.com/platform/superproject/main/+/main:frameworks/base/services/accessibility/java/com/android/server/accessibility/AccessibilityServiceConnection.java
  - **Force-stop**: `onPackagesForceStoppedLocked` **desactiva** el servicio (lo borra de
    `ENABLED_ACCESSIBILITY_SERVICES` y lo persiste) → el usuario tiene que reactivarlo a mano.
    Fuente: https://android.googlesource.com/platform/superproject/main/+/main:frameworks/base/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java
  - `BIND_FOREGROUND_SERVICE_WHILE_AWAKE` solo da prioridad con pantalla encendida; con pantalla
    apagada los limpiadores atacan.
- **[VERIFICADO (testimonios/soporte)]** Varias capas hacen force-stop real (que desactiva la
  accesibilidad): OPPO/ColorOS mata servicios de accesibilidad al apagar pantalla
  (https://dontkillmyapp.com/oppo), Samsung "App power monitor" los apaga al optimizar
  (https://zzz.buzz/2017/05/04/samsung-app-power-monitor-and-android-accessibility-services),
  Bitdefender documenta pérdida de permiso de accesibilidad por ahorro de energía en Xiaomi, Huawei,
  OPPO, vivo, Samsung, OnePlus (https://www.bitdefender.com/consumer/support/answer/32406/),
  testimonios de "malfunctioning" en OnePlus/Huawei/Xiaomi/POCO/realme
  (https://github.com/bitwarden/mobile/issues/596).
- **Consecuencia de diseño**: el guardián debe (1) detectar que lo han desactivado (leer
  `ENABLED_ACCESSIBILITY_SERVICES` desde otro componente) y avisar con voz/vibración; (2) guiar al
  usuario por autoarranque + sin restricción de batería + bloqueo en recientes.

### 5.2 Intents de autoarranque por fabricante (AutoStarter 1.1.0, `Build.BRAND`) [VERIFICADO del fuente]
Fuente: https://github.com/judemanutd/AutoStarter/blob/master/autostarter/src/main/java/com/judemanutd/autostarter/AutoStartPermissionHelper.kt

| Marca | Paquete | Clase (en orden de intento) |
|---|---|---|
| xiaomi/poco/redmi | `com.miui.securitycenter` | `com.miui.permcenter.autostart.AutoStartManagementActivity` |
| letv | `com.letv.android.letvsafe` | `com.letv.android.letvsafe.AutobootManageActivity` |
| asus | `com.asus.mobilemanager` | `...powersaver.PowerSaverSettings` / `...autostart.AutoStartActivity` |
| honor | `com.huawei.systemmanager` | `...optimize.process.ProtectActivity` |
| huawei | `com.huawei.systemmanager` | `...startupmgr.ui.StartupNormalAppListActivity` / `...optimize.process.ProtectActivity` |
| oppo | `com.coloros.safecenter` / `com.oppo.safe` | `...permission.startup.StartupAppListActivity` / `...startupapp.StartupAppListActivity` |
| vivo | `com.iqoo.secure` / `com.vivo.permissionmanager` | `...ui.phoneoptimize.AddWhiteListActivity` / `...activity.BgStartUpManagerActivity` |
| nokia | `com.evenwell.powersaving.g3` | `...exception.PowerSaverExceptionActivity` |
| samsung | `com.samsung.android.lool` | `com.samsung.android.sm.ui.battery.BatteryActivity` (y variantes) |
| oneplus | `com.oneplus.security` | `...chainlaunch.view.ChainLaunchAppListActivity` (o acción `com.android.settings.action.BACKGROUND_OPTIMIZE`) |

- **[VERIFICADO]** Comprobar antes `getInstalledApplications` y envolver en try/catch (Huawei EMUI 10
  lanza `SecurityException` sobre `StartupNormalAppListActivity`). El `<queries>` de la librería
  tiene erratas para Nokia/Samsung/OnePlus; **declara tú los paquetes exactos** en `<queries>` con
  targetSdk 30+. Issues: #76, #84, #86, #87. Librería sin mantenimiento desde 2021.
- Xiaomi detección de estado del autoarranque: https://github.com/XomaDev/MIUI-autostart

### 5.3 Pasos por fabricante (dontkillmyapp.com) [VERIFICADO]
Ranking de agresividad (5 = peor): Huawei 5, Xiaomi 5, OnePlus 5, Samsung 5, Asus 4, Oppo 4, Vivo 3,
Nokia 0, AOSP 0. https://dontkillmyapp.com/
- **Xiaomi**: bloquear en recientes; Autostart (MIUI 14: Apps → app → Permisos → "Background
  autostart"); batería "No restrictions"; desactivar "MIUI optimizations". https://dontkillmyapp.com/xiaomi
- **Huawei**: App launch "Manage manually" (todo ON); desactivar Smart tune-up; en EMUI 9+,
  PowerGenie mata lo no listado → `pm uninstall -k --user 0 com.huawei.powergenie` (por ADB).
  https://dontkillmyapp.com/huawei
- **OPPO/ColorOS**: bloquear en recientes; permitir arranque (`com.coloros.safecenter` /
  `com.coloros.safecenter.permission.Permission`); sin optimización; servicio en primer plano.
  https://dontkillmyapp.com/oppo
- **vivo**: Más ajustes → Aplicaciones → Autoarranque; batería sin restricción; bloquear en
  recientes. https://dontkillmyapp.com/vivo
- **Samsung**: quitar de "Sleeping/Deep sleeping", añadir a "Never sleeping"; desactivar "Put unused
  apps to sleep", Adaptive battery y Auto-optimize; bloquear en recientes. One UI 6+ promete no
  optimizar servicios en primer plano de apps con targetSdk 34. https://dontkillmyapp.com/samsung
- **OnePlus**: batería "Don't optimize"; desactivar App Auto-Launch; en OnePlus 6+ Optimización
  avanzada → desactivar Deep optimization y Sleep standby optimization (los reactiva a veces).
  https://dontkillmyapp.com/oneplus

### 5.4 Ignorar optimización de batería
- **[VERIFICADO]** `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (permiso
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, URI `package:`) exime SOLO de Doze/App Standby de AOSP
  (red, wakelocks parciales); no toca las capas de fabricante (Autostart, PowerGenie, Sleeping apps,
  Deep optimization). Sin permiso: `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (abre la lista).
  Fuente: https://developer.android.com/training/monitoring-device-state/doze-standby
- **[VERIFICADO]** AOSP NO exime a los servicios de accesibilidad por ser tales
  (`AppStandbyController` no los menciona).
- **Política de Play [VERIFICADO]**: prohíbe pedir la exención salvo casos aceptables; la tabla
  oficial incluye **"Safety app"** como aceptable. La accesibilidad no es categoría propia; una app
  de rescate encaja en "Safety app". Fuera de Play no hay restricción.

---

## 6. Rescate disponible antes de desbloquear (arranque directo / FBE)

- **[VERIFICADO]** Dos almacenes: CE (tras desbloquear) y DE (antes y después). La guía oficial cita
  explícitamente "Apps that provide accessibility services, like Talkback" como caso de Direct Boot.
  Fuente: https://developer.android.com/privacy-and-security/direct-boot
- **[VERIFICADO]** Marcar `android:directBootAware="true"` en los componentes; usar
  `ACTION_LOCKED_BOOT_COMPLETED` (ya con DE disponible) y `createDeviceProtectedStorageContext()`.
  `ACTION_USER_UNLOCKED` / `UserManager.isUserUnlocked()` para lo que use CE (la arquitectura ya
  guarda prefs/voces/registro en almacenamiento protegido = DE; mantenerlo así).
- **[VERIFICADO]** Un AccessibilityService activo con `directBootAware="true"` **se vincula antes del
  desbloqueo**: `AccessibilityManagerService.updateServicesLocked()` salta los servicios no
  directBootAware mientras el usuario está bloqueado. TalkBack declara `directBootAware="true"`.
  Fuentes: AccessibilityManagerService.java (main) · https://github.com/google/talkback/blob/master/talkback/src/main/AndroidManifest.xml
- **[VERIFICADO]** El atajo de volumen funciona en la pantalla de bloqueo si
  `accessibility_shortcut_on_lock_screen==1` (por defecto, tras mostrarse el diálogo). Un destino
  directBootAware puede arrancar antes del desbloqueo. `Settings.Secure` se lee antes de desbloquear.
- **Pantallas de Ajustes antes de desbloquear [VERIFICADO/mixto]**: la app Ajustes de AOSP es
  `directBootAware` + `defaultToDeviceProtectedStorage`, pero **ninguna** de sus Activities declara
  `showWhenLocked` (ni la de accesibilidad). **[SUPUESTO]** abrir Ajustes desde el bloqueo lo deja
  detrás del keyguard hasta que el usuario introduce la credencial (que es el propio desbloqueo). No
  hay intents públicos de Ajustes que funcionen sobre el bloqueo en AOSP.
- **Mostrar la propia `ActividadRescate` sobre el bloqueo [VERIFICADO]**: `setShowWhenLocked(true)` /
  `android:showWhenLocked` (API 27; en 24–26 usar `FLAG_SHOW_WHEN_LOCKED`), `setTurnScreenOn`,
  `KeyguardManager.requestDismissKeyguard`. La Activity debe ser directBootAware para verse antes del
  primer desbloqueo. El servicio puede lanzarla (tiene `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`).
- **Resumen**: el menú de rescate y su voz SÍ pueden estar disponibles antes de desbloquear
  (Activity + servicio directBootAware, voces/prefs en DE). Lo que NO funciona bien antes de
  desbloquear es automatizar pantallas de Ajustes del sistema.

---

## 7. Diálogos del instalador y `pm install`

- **[VERIFICADO]** `pm install` / `cmd package install` como uid shell instalan en **silencio** (el
  shell tiene `INSTALL_PACKAGES`); NO aparece el diálogo del PackageInstaller. Una app normal con
  `PackageInstaller` siempre muestra diálogo. (a) API pública: NO silencioso; (c) shell: SÍ; (d)
  root: SÍ. Fuente: https://technastic.com/adb-shell-permissions-android/
- **Downgrade `pm install -r -d` [VERIFICADO]**: `-d` ("allow version downgrade") según el help de
  adb está restringido a paquetes **debuggable**; para apps release da
  `INSTALL_FAILED_VERSION_DOWNGRADE` (hay que desinstalar e instalar la antigua, perdiendo datos).
  Fuentes: https://gitlab.e.foundation/e/os/android_system_core/-/commit/85ed97518c0c1ea312bb3b4941cd03de90f873fa
  · https://android.googlesource.com/platform/frameworks/base/+/921dd75%5E%21/
  - Reinstalar la MISMA versión: `pm install -r` sí (no es downgrade). Instalar versión inferior de
    app release: solo root (o borrando datos).
- `--force-queryable` (marcar el paquete consultable por todas las apps, visibilidad Android 11+):
  ejecutable por shell. **[SUPUESTO]** verificar con `pm install --help`.
- **Pulsar el diálogo con accesibilidad [VERIFICADO relacionado]**: si no se usa shell, el diálogo
  del instalador se automatiza con el servicio de accesibilidad; cuidado con
  `accessibilityDataSensitive` (ver §8.5) y con los toques "tapados" si hay superposición.

---

## 8. Diferencias de Ajustes por fabricante

### 8.1 Intents públicos de `android.provider.Settings` [VERIFICADO-AOSP]
Fuente: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/Settings.java

| Constante | Valor |
|---|---|
| `ACTION_ACCESSIBILITY_SETTINGS` | `android.settings.ACCESSIBILITY_SETTINGS` |
| `ACTION_APPLICATION_DETAILS_SETTINGS` | `android.settings.APPLICATION_DETAILS_SETTINGS` (URI `package:<pkg>`) |
| `ACTION_LOCALE_SETTINGS` | `android.settings.LOCALE_SETTINGS` |
| `ACTION_APP_LOCALE_SETTINGS` | `android.settings.APP_LOCALE_SETTINGS` (API 33+) |
| `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` | lista de optimización |
| `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | diálogo, URI `package:` |
| `ACTION_MANAGE_APPLICATIONS_SETTINGS` / `ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS` | lista de apps |

- **TTS [VERIFICADO]**: NO hay constante pública. La acción real es la cadena literal
  `"com.android.settings.TTS_SETTINGS"` (Activity `Settings$TextToSpeechSettingsActivity`,
  exported). **[SUPUESTO]** no todos los fabricantes la resuelven → usar `resolveActivity()` y
  respaldo (Ajustes de accesibilidad/idioma).
- **`ACTION_ACCESSIBILITY_DETAILS_SETTINGS` NO sirve a apps de terceros [VERIFICADO]**: `@hide
  @SystemApi`, exige permiso `OPEN_ACCESSIBILITY_DETAILS_SETTINGS`. Hay que abrir la lista general y
  navegar.

### 8.2 Nombres de recursos de cadenas en `com.android.settings` [VERIFICADO-AOSP]
(en / es España / es-rUS pueden diferir; si el móvil está en es-US se usa es-rUS)

| Recurso | Inglés | es |
|---|---|---|
| `force_stop` | Force stop | Forzar detención |
| `force_stop_dlg_title` | Force stop? | ¿Forzar detención? |
| `dlg_ok` / `dlg_cancel` | OK / Cancel | Aceptar / Cancelar |
| `app_factory_reset` | Uninstall updates | Desinstalar actualizaciones |
| `enable_text` / `disable_text` | Enable / Disable | Habilitar / Inhabilitar |
| `install_text` / `uninstall_text` | Install / Uninstall | Instalar / Desinstalar |
| `app_disable_dlg_positive` | Disable app | Inhabilitar aplicación |
| `allow` / `deny` | Allow / Deny | Permitir / Denegar |
| `user_installed_services_category_title` | Downloaded apps (A11–; "Downloaded services" en A9–10) | Aplicaciones descargadas / Servicios descargados |
| `accessibility_service_primary_switch_title` | Use %1$s (A11: `..._master_switch_title`) | Usar %1$s |

- NO existe `uninstall_all_updates` (es `app_factory_reset`). **[VERIFICADO]**
- TTS en SettingsLib (mismo paquete): `tts_settings_title` ("Salida de conversión de texto a
  voz"/"Salida de texto a voz"), `tts_default_rate_title`, `tts_default_pitch_title`.
- **Técnica recomendada [VERIFICADO-3º]**: NO hardcodear textos; leerlos del paquete de Ajustes con
  `packageManager.getResourcesForApplication(pkg).getIdentifier(name,"string",pkg)` (como SD Maid
  SE). Declarar `<queries><package android:name="com.android.settings"/></queries>`.
  Fuente: https://github.com/d4rken-org/sdmaid-se/tree/main/app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/core/automation/specs

### 8.3 Pantalla Info de la app [VERIFICADO-AOSP]
- Botones directos en fila `ActionButtonsPreference`: button1 Abrir, button2 Desinstalar (→
  Inhabilitar/Habilitar en apps de sistema), button3 Forzar detención (se habilita si la app corre).
  IDs `com.android.settings:id/button1..4`. "Desinstalar actualizaciones" y "Permitir ajustes
  restringidos" van en el menú ⋮ (overflow).
- **MIUI/HyperOS [VERIFICADO-3º]**: la Info de la app NO está en `com.android.settings` sino en
  `com.miui.securitycenter` (`com.miui.appmanager.ApplicationsDetailsActivity`); la clave del botón
  es `menu_item_force_stop`. ColorOS/OxygenOS siguen en `com.android.settings` pero el botón de
  confirmación repite el verbo ("Forzar detención" en vez de "Aceptar"); en Android 15+ el nodo
  clicable es un hermano del icono/etiqueta. Samsung One UI usa `com.android.settings` con claves
  AOSP. (Comentarios de SD Maid `HyperOsSpecs/MIUISpecs/ColorOSSpecs/OxygenOSSpecs/OneUISpecs`.)

### 8.4 Lista de servicios de accesibilidad instalados por fabricante
- AOSP: Accesibilidad → categoría `user_installed_services_category` ("Aplicaciones descargadas" en
  A11+, "Servicios descargados" en A9–10). **[VERIFICADO-AOSP]**
- Samsung One UI: Accesibilidad → "Installed apps"/"Installed services" (ES: "Aplicaciones
  instaladas"; "Servicios instalados" **[SUPUESTO]**). Buscar ambas variantes. En One UI ≤6 la
  pantalla la sirve `com.samsung.accessibility` (`AccessibilityHomepageActivity`); en One UI 7
  `com.android.settings`. **Incluir `com.samsung.accessibility` al filtrar eventos por paquete.**
  Fuentes: https://www.samsung.com/ph/support/mobile-devices/how-to-customize-accessibility-settings-on-your-galaxy-phone/
  · https://github.com/joshdeutc/custos/blob/main/docs/APPWATCHER_SAMSUNG_COMPAT.md
- OnePlus OxygenOS 11 (OnePlus 6): Sistema → Accesibilidad; etiqueta esperada AOSP de A11
  ("Aplicaciones descargadas"). **[SUPUESTO]**
- Xiaomi MIUI/HyperOS: Ajustes adicionales → Accesibilidad → "Downloaded apps" (en MIUI 14
  Accesibilidad subió al nivel principal). **[VERIFICADO-3º]**
- ColorOS (OPPO): Ajustes adicionales → Accesibilidad → "Downloaded apps". vivo "Downloaded
  services", Huawei "Installed services", Honor "Downloaded Services". **[VERIFICADO-3º]**
  Fuente: https://j.codekk.com/developer-tools/open-accessibility-service

### 8.5 Paquetes de Ajustes y diálogo de "control total" / "Ajustes restringidos"
- `com.android.settings` existe en todos (personalizado por el OEM). Autostart/Info en Xiaomi =
  `com.miui.securitycenter`; OPPO safecenter = `com.coloros.safecenter`. **[VERIFICADO-3º]**
- **Diálogo "control total" (activar servicio) [VERIFICADO-AOSP]**: en Android 14+ lo crea el
  framework; cadenas con prefijo `android:`: `accessibility_enable_service_title` ("¿Permitir que
  %1$s pueda controlar totalmente...?"), botones `accessibility_dialog_button_allow` ("Permitir"),
  `accessibility_dialog_button_deny`. IDs `accessibility_permission_enable_allow_button` etc. El
  layout tiene `android:accessibilityDataSensitive="yes"` → **tu servicio debe declarar
  `android:isAccessibilityTool="true"`** para ver/pulsar "Permitir"; además el botón ignora toques
  "tapados" (superposiciones). En A11–13 estaban en Settings (`enable_service_title`).
- **Ajustes restringidos (Android 13+, apps sideloaded) [VERIFICADO-AOSP]**: al intentar activar
  accesibilidad aparece "Ajuste restringido" (`blocked_by_restricted_settings_title`). Desbloqueo:
  Info de la app → ⋮ → "Permitir ajustes restringidos" (`app_restricted_settings_lockscreen_title`).
  **Orden obligatorio [VERIFICADO]**: la opción del menú solo aparece DESPUÉS de que se haya mostrado
  el diálogo "Ajuste restringido". Requiere que el usuario introduzca PIN/huella
  (`BiometricPrompt`): la app no debe introducirlo. En Android 15+ se gestiona con
  `EnhancedConfirmationManager`. Fuente: https://support.google.com/android/answer/12623953

---

## 9. Voz de alta calidad offline (sherpa-onnx + Piper español)

**Hallazgo que cambia el diseño [VERIFICADO]**: sherpa-onnx v2.0.0 (23-oct-2026) **eliminó
espeak-ng y piper-phonemize** por incompatibilidad GPL-3 con su licencia Apache-2.0. Las voces
Piper (VITS con fonemas espeak) ya no funcionan "tal cual" en v2: hay que (a) aportar un
`lexicon.txt` (solo hay en/zh, no español) o (b) fonemizar fuera con `piper-phonemize` (GPL-3,
AAR ~12,7 MB) y pasar `phonemeCodepoints`.
Fuentes: https://github.com/k2-fsa/sherpa-onnx/pull/4055 · https://github.com/k2-fsa/sherpa-onnx/issues/3731

- **sherpa-onnx [VERIFICADO]**: Apache-2.0; AAR oficial para las 4 arquitecturas (arm64-v8a,
  armeabi-v7a, x86, x86_64), minSdk 21. Tamaño nativo por arquitectura (solo `-jni` + onnxruntime):
  ~22–37 MB sin comprimir, ~11–13 MB comprimido (arm64 ≈ 31 MB / 11,8 MB). Con App Bundle, Play
  entrega solo la del usuario.
  Fuentes: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.0 · https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
- **Voces Piper español (modelo int8 empaquetado para sherpa) [VERIFICADO]**:
  - `es_ES-davefx-medium` — 21,2 MB int8 — licencia **CC0** (dominio público). Recomendada.
  - `es_MX-ald-medium` — 21,3 MB int8 — **Unlicense** (dominio público). Recomendada (es-MX).
  - `es_MX-claude-high` — 21,2 MB int8 — Apache-2.0 según model card (procedencia de voz no
    documentada → **[SUPUESTO]** riesgo).
  - `es_ES-sharvard-medium` — 23,5 MB int8 — CC BY 3.0 (exige atribución).
  - `es_ES-carlfm-x_low` — 13,4 MB int8 — dominio público.
  - `es_AR-daniela-high` — 35,1 MB int8 — CC BY-SA 4.0.
  - Evitar `es_ES-miro-high` (CC BY-NC-ND, **no comercial**) y `es_ES-glados-medium` (imita personaje
    de Valve).
  Fuentes de licencias (MODEL_CARD por voz) en https://huggingface.co/rhasspy/piper-voices/tree/main/es
- **Alternativa SIN GPL [VERIFICADO]**: sherpa-onnx v2 + **Coqui VITS es/css10**
  (`vits-coqui-es-css10`, 67 MB, BSD-3) NO necesita léxico ni piper-phonemize (basado en
  caracteres). Una sola voz masculina. O davefx/ald + reglas propias letra→fonema (español muy
  regular). Fuente: comentario en `TtsEngine.kt` de SherpaOnnxTtsEngine.
- **Exponer como motor del sistema [VERIFICADO]**: el ejemplo oficial `SherpaOnnxTtsEngine`
  implementa `TextToSpeechService` (`TtsService : TextToSpeechService()`), con `TTS_SERVICE` e
  `res/xml/tts_engine.xml`. Encaja con el `ServicioTtsPropio` de la arquitectura. También puede
  reproducirse con `AudioTrack` dentro de la app sin pasar por el TTS del sistema.
  Fuente: https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxTtsEngine
- **Implicación de licencia [SUPUESTO, no es consejo legal]**: usar piper-phonemize (o sherpa v1.x
  con espeak dentro) enlaza código GPL-3 → probablemente obliga a GPL-3 la app. Salidas: Coqui CSS10
  o reglas propias. Recomendación: descargar la voz bajo demanda (con conexión, nunca en emergencia),
  no en el APK. davefx (es_ES) y ald (es_MX) en int8 ≈ 21 MB por voz.

---

## 10. Atajo por triple pulsación del botón de encendido u otras teclas

- **AOSP: no hay API pública para que una app registre doble/triple pulsación de encendido
  [VERIFICADO]**. Los ajustes (`power_button_double_press`, `power_button_triple_press`,
  `double_tap_power_button_gesture`, etc.) son `@hide` en Settings.Global/Secure; `PhoneWindowManager`
  los lee. La triple pulsación solo admite 0/nada, modo teatro o brillo — **no abrir una app**. La
  doble puede abrir `config_doublePressOnPowerTargetActivity`, recurso del FABRICANTE (vacío por
  defecto). Reasignables con WRITE_SECURE_SETTINGS **[SUPUESTO por el nivel development]**, pero
  ningún valor abre una app de terceros y los OEM suelen ignorarlos.
  Fuentes: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/Settings.java
  · config.xml / PhoneWindowManager.java
- **Vía legítima: ser el asistente digital [VERIFICADO]**: rol `android.app.role.ASSISTANT`
  (`requestable="false"`, lo elige el usuario en Apps predeterminadas → Asistente). Con un
  `VoiceInteractionService` o actividad `ACTION_ASSIST`, en dispositivos donde la pulsación larga de
  encendido abre el asistente (valor 5), eso lanzaría la app. Inconveniente: sustituye a
  Gemini/Assistant.
- **Por fabricante [VERIFICADO/mixto]**: Samsung permite "doble pulsación del botón lateral → Abrir
  app" (Funciones avanzadas → Botón lateral), y Good Lock/RegiStar asignar la pulsación larga a una
  app; Galaxy XCover programa la tecla XCover (con opción "usar con pantalla bloqueada", útil para
  ciegos). Pixel: Quick Tap (doble toque atrás) → Abrir app (solo desbloqueado). Xiaomi: toque
  trasero doble/triple en algunos modelos. OnePlus/Huawei: sin asignar app al encendido de forma
  verificable. **Ninguno se activa desde la app**; como mucho, guiar al usuario.
  Fuentes: https://www.samsung.com/latin_en/support/mobile-devices/how-to-customize-the-side-button-with-new-features-on-your-galaxy-phone-and-tablet/
  · https://support.google.com/pixelphone/answer/7443425
- **Teclas de volumen en el AccessibilityService [VERIFICADO]**: con
  `android:canRequestFilterKeyEvents="true"` + `FLAG_REQUEST_FILTER_KEY_EVENTS`, `onKeyEvent` recibe
  las teclas antes que el sistema. PERO con **pantalla apagada NO llega ninguna tecla** (sin
  `FLAG_PASS_TO_USER`), y el **botón de encendido nunca** llega a `onKeyEvent`. El atajo de volumen
  (mantener ambas 3 s, 1 s tras confirmar) existe desde Android 8.0.
  Fuentes: AccessibilityService.java / AccessibilityInputFilter.java / ViewConfiguration.java (main)
  · https://developer.android.com/guide/topics/ui/accessibility/views/service

---

## 11. Restaurar ajustes propios de TalkBack

- **[VERIFICADO]** La velocidad propia de TalkBack es DISTINTA de `tts_default_rate`:
  **velocidad efectiva = `pref_rate_volume` × `tts_default_rate`/100**. `pref_rate_volume` es un
  SharedPreference (String de un float, por defecto "1.0"); tono = `pref_pitch_volume`; volumen de
  voz = `pref_speech_volume`; verbosidad = `pref_verbosity_preset_key`
  (`..._value_high/custom/low`). **No hay clave Settings.Secure para la velocidad propia de
  TalkBack.** Fuentes: FailoverTextToSpeech.java, SpeechRateAndPitchActor.java, VerbosityPreferences.java
  (https://github.com/google/talkback).
- **[VERIFICADO]** TalkBack guarda sus prefs en almacenamiento DE:
  `/data/user_de/0/com.google.android.marvin.talkback/shared_prefs/com.google.android.marvin.talkback_preferences.xml`
  (ruta **[SUPUESTO]**, confirmar con `su -c ls`). **NO expone ContentProvider ni intents para
  preferencias** (el único provider exportado es `LabelProvider`, solo etiquetas). `run-as` no
  funciona en build de Play (no debuggable).
- **Qué se puede restaurar**:
  - **(a) API pública**: solo automatizar la UI de `TalkBackPreferencesActivity` (exported) con el
    servicio de accesibilidad. Existe un atajo de teclado "Reset speech rate and pitch" en 16.2
    (**[SUPUESTO]** inyectarlo con `input keyevent`).
  - **(b) WRITE_SECURE_SETTINGS / (c) shell**: solo `tts_default_rate` y `tts_default_pitch`
    (globales del motor), no las prefs propias de TalkBack.
  - **(d) root**: leer/escribir el XML de prefs, PERO hay que parar TalkBack antes (SharedPreferences
    no soporta multiproceso; reescribiría el fichero). Procedimiento: force-stop → editar XML →
    conservar owner/permterm/SELinux (`chown`/`chmod 660`/`restorecon`) → volver a añadir a
    `enabled_accessibility_services`. **[VERIFICADO+SUPUESTO]**
  - **Imposible sin root**: leer/restaurar las prefs internas de TalkBack de forma fiable.
- Backup: `TalkBackBackupAgent` está vacío en el código abierto; en Play probablemente usa Google
  Backup (no controlable por la app). **[VERIFICADO/SUPUESTO]**

---

## Fuentes principales consultadas
- AOSP (cs.android.com / android.googlesource.com): `frameworks/base` (Settings.java, AdbService,
  AdbDebuggingManager, AccessibilityManagerService, AccessibilityServiceConnection,
  AccessibilityShortcutController, AudioService, PhoneWindowManager, AccessibilityShellCommand),
  `packages/modules/adb` (adb_wifi.cpp, auth.cpp, adb_wifi.md), `packages/apps/Settings`,
  `system/sepolicy`, `system/core`.
- Código real: TalkBack (github.com/google/talkback), Shizuku, LADB, libadb-android, adb-auto-enable,
  KeepADB, SD Maid SE, AutoStarter, sherpa-onnx, rhasspy/piper.
- Documentación: developer.android.com (direct-boot, adb, accessibility, doze-standby),
  support.google.com, dontkillmyapp.com, technastic.com, Hugging Face (rhasspy/piper-voices).
