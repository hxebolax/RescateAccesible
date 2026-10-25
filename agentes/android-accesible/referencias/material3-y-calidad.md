# Material 3 y calidad de una app Android

Una app accesible también tiene que sentirse como una app Android bien hecha: la de las pautas de calidad de Google (Core app quality), las recomendaciones de arquitectura y navegación, y Material 3. Estas pautas son compatibles con las de accesibilidad; **si chocan, gana la accesibilidad** (último apartado).

## Navegación y pantallas

1. **Atrás del sistema** (botón y gesto), sin botón Atrás propio en la pantalla. Arriba (la flecha de la barra) y Atrás coinciden dentro de la app, pero Arriba nunca la cierra.
2. **Atrás predictivo**: `android:enableOnBackInvokedCallback="true"` en el manifiesto y `OnBackPressedCallback` activado solo cuando hay algo que cerrar (`callback.isEnabled = hayAlgoAbierto`). Nunca sobrescribir `onBackPressed()` ni capturar `KEYCODE_BACK`.
3. **Conservar el estado** al girar, al volver de Recientes y tras la muerte del proceso (`ViewModel` con `SavedStateHandle`, `onSaveInstanceState`). Las vistas con estado necesitan id.
4. **Cajón de navegación** (modal en el móvil) solo para **destinos**, que son las secciones de la app. Las acciones generales (Ajustes, Ayuda, Acerca de) van separadas con un divisor. Solo el destino actual lleva el indicador activo y el estado «seleccionado»; los demás no tienen estado, y una acción nunca queda marcada. Un toque navega y cierra el cajón. Con TalkBack, al abrirlo el foco va a la sección actual. Plantilla: `MenuLateral.kt`. `NavigationView` con elementos marcables hacía que TalkBack dijera «marcado» o «no seleccionado» en todas las filas; por eso la plantilla no lo usa.
5. **Una sola puerta a Ajustes**: se abre solo desde el menú lateral. Las secciones no tienen atajos propios a Ajustes (en su menú de opciones ni en botones): al volver atrás desde un apartado abierto así, el usuario acababa en Ajustes en vez de en su sección. Cada opción nueva va en su apartado de Ajustes.
6. **Secciones coherentes**: cada sección se comporta como una pequeña app dentro de la app, con sus textos (artículo, episodio, documento según la sección) y la misma estructura que las demás (barra arriba con «Abrir menú», pestañas o barra de navegación abajo).
7. **Borde a borde** (obligatorio con targetSdk 35): `enableEdgeToEdge()` en la Activity y los insets de `systemBars`, `displayCutout` e `ime` aplicados:

   ```kotlin
   ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
       val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
       v.updatePadding(left = barras.left, top = barras.top, right = barras.right, bottom = barras.bottom)
       WindowInsetsCompat.CONSUMED
   }
   ```

   Ningún control queda bajo las barras ni en la zona de gestos. Con la fuente al 200 %, todo sigue alcanzable.
8. **Tema** `Theme.Material3.DayNight.NoActionBar` (o el del proyecto), con los colores de Material por parejas (`colorOnSurface` sobre `colorSurface`, `colorOnPrimary` sobre `colorPrimary`). El color dinámico es opcional y siempre con el contraste comprobado.
9. **Mismas funciones** en vertical, horizontal, tableta y multiventana, sin perder estado. Sin orientación fija.

## Componentes de Material 3 que ya resuelven mucho

- `MaterialToolbar` con título (el de la Activity) y `navigationContentDescription`.
- `MaterialButton`, `MaterialSwitch`, `MaterialCheckBox`, `Chip`: rol, estado y 48 dp de objetivo táctil de serie.
- `TextInputLayout` con hint, `helperText` y `error`.
- `MaterialAlertDialogBuilder`, `BottomSheetDialog`, `Snackbar`.
- `BottomNavigationView` o `NavigationBarView` con etiquetas siempre visibles (`app:labelVisibilityMode="labeled"`): el destino actual dice «seleccionado».
- `TabLayout` para pestañas, con el título de panel al cambiar.
- Tipografía con los estilos `TextAppearance.Material3.*` (en sp).

## Respuesta y trabajo en segundo plano

10. **Nada lento en el hilo principal** (sin ANR). Lo que tarde más de medio segundo muestra la ventana de espera con Cancelar (`conEspera`) y el resultado sale en la misma pantalla.
11. El arranque y el audio dan señal en uno o dos segundos. Tiempos de red cortos para lo que se espera con la app delante y errores en lenguaje claro, nunca la excepción técnica.
12. **Audio**: pedir el foco de audio al reproducir, soltarlo al parar y atender su pérdida. Con Media3: `ExoPlayer.setAudioAttributes(atributos, handleAudioFocus = true)` con `USAGE_MEDIA`. Reproducción en segundo plano con `MediaSessionService` (servicio en primer plano de tipo `mediaPlayback`, `MediaSession` y notificación con controles). Siempre se puede pausar.
13. **El audio propio no tapa a TalkBack**: TalkBack habla con su propio uso de audio (accesibilidad) y el sistema baja el volumen del contenido mientras habla si el usuario tiene activada la atenuación. No uses `USAGE_ASSISTANCE_ACCESSIBILITY` para el audio de la app, no pidas `AUDIOFOCUS_GAIN` para sonidos cortos (usa `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) y no subas el volumen por tu cuenta. Si la app tiene voz sintética propia, que se pueda pausar y que no hable a la vez que TalkBack en respuesta a un gesto.
14. **WorkManager** para el trabajo diferible (sincronizar, actualizar contenido). Respetar Doze y la batería. Servicios en primer plano solo con su tipo declarado en el manifiesto.
15. **Notificaciones** con canales, útiles y no promocionales. El permiso `POST_NOTIFICATIONS` (Android 13 o posterior) se pide en contexto, cuando el usuario activa algo que lo necesita.

## Privacidad y seguridad

16. **Permisos mínimos**, pedidos al usar la función (nunca al arrancar), con una explicación previa accesible; si se deniegan, la app sigue funcionando y lo dice en la pantalla.
17. **Almacenamiento acotado**: el selector del sistema (Storage Access Framework, Photo Picker), no permisos amplios. Datos sensibles (contraseñas, tokens) en almacenamiento interno o cifrado, nunca en el registro.
18. Componentes con `android:exported` declarado, `PendingIntent` inmutables (`FLAG_IMMUTABLE`) y solo HTTPS, salvo lo que el usuario pida expresamente.

## Código y calidad interna

19. Separar la interfaz de los datos (repositorios, `ViewModel`), corrutinas ligadas al ciclo de vida (`lifecycleScope`, `viewModelScope`) y nunca guardar el `Context` de una Activity en un objeto estático.
20. **Ningún error se pierde**: `Log.e(etiqueta, mensaje, excepcion)` con la excepción completa; ningún `catch` vacío; `CancellationException` siempre relanzada.
21. targetSdk al día, R8 en la versión de publicación, sin bibliotecas de depuración en producción. Pruebas unitarias e instrumentadas (incluida la de accesibilidad) antes de cada versión.
22. **Todas las cadenas en recursos**, en todos los idiomas de la app; idioma por app (`locales_config`). `start` y `end` en lugar de `left` y `right`.
23. **Compartir** con el Sharesheet del sistema (`Intent.createChooser`).

## Si chocan con la accesibilidad, gana la accesibilidad

- **Gestos** (deslizar el cajón, deslizar para borrar, Atrás predictivo): siempre con botón visible o acción de TalkBack.
- **Diálogos y capas**: al cerrarse, el foco vuelve a donde estaba (`MemoriaFoco`) y no se repite el título de la pantalla.
- **Snackbars con acción**: indefinidos o con el tiempo recomendado, y nunca la única vía para algo.
- **Diseño compacto**: nunca menos de 48 dp ni alturas fijas para texto.
- **Animaciones**: respetar «Quitar animaciones».
- **Indicador activo** (cajón, pestañas, barra de navegación): nunca solo color; también «seleccionado» y negrita.
- **Mensajes de estado**: región viva educada, una sola vez y sin mover el foco.
- **Material dice una cosa y TalkBack otra** (un componente que anuncia estados que no tiene): se corrige la semántica o se sustituye el componente, como el cajón.

## Equivalentes en Jetpack Compose

- **Borde a borde:** `enableEdgeToEdge()` y `Scaffold` (aplica los insets a su contenido con `innerPadding`); fuera de él, `Modifier.safeDrawingPadding()` o `windowInsetsPadding(WindowInsets.systemBars)` e `imePadding()`.
- **Atrás:** `BackHandler(enabled = ...) { }` o `PredictiveBackHandler` para animar el gesto.
- **Cajón:** `ModalNavigationDrawer` con `ModalDrawerSheet` y `NavigationDrawerItem(selected = esActual, ...)`. Comprueba con TalkBack qué anuncia cada elemento: si dice «no seleccionado» en todos o un rol que no corresponde, sustituye su semántica (`Modifier.clearAndSetSemantics { contentDescription = titulo; if (esActual) selected = true; onClick { ... } }`). Al abrir, lleva el foco al destino actual con su `FocusRequester` y compruébalo con TalkBack.
- **Tema:** `MaterialTheme(colorScheme = if (oscuro) darkColorScheme(...) else lightColorScheme(...))`, siempre con los pares `onX` sobre `X`.
- **Estado:** `rememberSaveable` y `ViewModel` con `SavedStateHandle`.
- **Barra de navegación:** `NavigationBar` con `NavigationBarItem(label = { Text(...) }, alwaysShowLabel = true)`.
