# Auditoría de accesibilidad y calidad: método y ejemplo

## Método

1. **Alcance.** Qué pantallas y tareas se revisan, y si se puede cambiar código (en una auditoría, normalmente no).
2. **Fuentes activas.** El código que se compila: `app/src/main` (y los módulos de la app), los diseños XML, los menús, los temas y las cadenas. Ignora `build/`, copias, dependencias y material de referencia.
3. **Inventario.** Activities y fragments (o pantallas de Compose), con su título; listas y sus adaptadores; diálogos; vistas propias; deslizadores; navegación (cajón, pestañas, barra inferior); trabajo lento y cómo se espera; audio.
4. **Recorrido por tareas.** Para cada tarea principal (abrir, buscar, elegir, actuar, configurar, volver): cómo se llega deslizando con TalkBack, qué se oye, qué alternativa tiene cada gesto y adónde vuelve el foco.
5. **Revisión estática** con las búsquedas de abajo, leyendo cada coincidencia en su contexto (una coincidencia no es un fallo hasta comprobarlo).
6. **Pruebas automáticas** existentes y, si hay emulador, la prueba instrumentada con TalkBack (ver [pruebas.md](pruebas.md)).
7. **Comprobación con TalkBack real**, si la hace el usuario; si no, queda como pendiente.

## Qué buscar en el código

### Views y XML

- `ImageButton`, `ImageView` con `onClick` o `clickable`, `FloatingActionButton` y `MaterialButton` con solo `app:icon` **sin** `contentDescription` (P0 1).
- `contentDescription` con «botón», «icono», «imagen», «pulsa» o con el aspecto («lápiz») (P0 2).
- `contentDescription` en un `EditText` o `TextInputEditText`; campos sin `TextInputLayout` ni `labelFor` (P0 3).
- `roleDescription` junto a `setAccessibilityHeading` o `accessibilityHeading` (P0 4).
- `setOnTouchListener` u `onTouchEvent` sin `performClick()`; vistas propias con zonas sin `ExploreByTouchHelper` (P0 7).
- `ItemTouchHelper` (deslizar o arrastrar) sin acciones de TalkBack equivalentes; `addAccessibilityAction` en un `onBindViewHolder` sin quitar las anteriores (P0 5).
- Activities sin `title` o con títulos repetidos; fragments o pestañas sin `setAccessibilityPaneTitle` (P0 8).
- Capas propias superpuestas sin `noHideDescendants` en el fondo (P0 9).
- `announceForAccessibility` (obsoleto en API 36), `accessibilityLiveRegion="assertive"`, regiones vivas en textos que cambian cada segundo (P0 10).
- `accessibilityTraversalBefore` o `After` (posibles ciclos), `left` y `right` en lugar de `start` y `end` (P0 11).
- `alpha="0"` o `INVISIBLE` en vistas con contenido, cajones que se leen cerrados (P0 12).
- Dos botones con el mismo texto o descripción en una pantalla (P1 13).
- Filas con varios hijos `clickable` o `focusable`; `notifyDataSetChanged()` sin `setHasStableIds(true)`; `RecyclerView` enfocable; `ViewPager2` sin `importantForAccessibility="no"` (P1 14 y 15).
- `textSize` en `dp` o `px`; `layout_height` fija en vistas con texto; `ellipsize` o `maxLines` en títulos esenciales (P1 22).
- Objetivos menores de 48 dp: `layout_width` o `layout_height` de 24 a 40 dp en vistas pulsables (P1 19).
- Colores fijos (`#...`, `@android:color/...`) en texto o iconos en lugar de los roles de Material (P1 20).
- `Toast` con información importante o como única confirmación de algo (P1 24).
- `screenOrientation` fija (P2 29); cadenas literales en el código o en XML (P2 30).
- `NavigationView` con `checkableBehavior`, acciones marcadas en el cajón o atajos a Ajustes fuera del menú lateral.
- `onBackPressed()` sobrescrito, `KEYCODE_BACK`, `OnBackPressedCallback(true)` siempre activo.
- `GlobalScope`, trabajo de red o de disco en el hilo principal, `catch (e: Exception)` que se traga `CancellationException`, `catch` vacíos o sin `Log`.
- `AudioManager.requestAudioFocus` ausente en un reproductor; `USAGE_ASSISTANCE_ACCESSIBILITY` en el audio de la app.

### Compose

- `Icon(..., contentDescription = null)` dentro de algo pulsable sin otro texto.
- `Modifier.clickable` sin `onClickLabel` cuando la acción no es evidente; `pointerInput` o `detectDragGestures` sin `customActions` equivalentes.
- `Modifier.size(...)` menor de 48 dp en lo pulsable, sin `minimumInteractiveComponentSize`.
- `LazyColumn` o `LazyRow` con `items(...)` sin `key`.
- `clearAndSetSemantics` que deja fuera información o acciones.
- `fontSize` en `dp`, `maxLines = 1` en títulos esenciales.
- `NavigationDrawerItem` sin comprobar qué anuncia; `BackHandler` siempre activo.

## Prioridades

- **P0, bloqueo:** impide la tarea o deja algo sin poder usarse con TalkBack (pautas P0 1 a 12, y la pérdida de foco que obliga a empezar de nuevo).
- **P1, grave:** se puede hacer, pero con pérdida de información, mucho esfuerzo o confusión (pautas P1 13 a 27 y las de coherencia).
- **P2, mejora:** calidad, comodidad y casos menos frecuentes (pautas P2 28 a 33 y calidad interna).

## Formato de cada hallazgo

- **Identificador y título** en una frase.
- **Prioridad:** P0, P1 o P2, y la pauta que incumple.
- **Tarea afectada.**
- **Evidencia:** `archivo:línea` y símbolo; si es un fallo demostrado por el código, una hipótesis o una observación real (registro de TalkBack, prueba instrumentada, el usuario).
- **Reproducción:** pasos con TalkBack (deslizar, doble toque, menú de acciones).
- **Resultado esperado:** lo que debería decir o hacer.
- **Propuesta concreta** y qué plantilla o ayudante del agente la resuelve.
- **Validación:** qué prueba automática y qué comprobación con TalkBack confirmarán la corrección.

No inventes hallazgos para llenar la plantilla ni cambies ajustes del usuario para que una prueba pase.

## Ejemplo de hallazgos

### A01. El botón de borrar de cada fila no tiene nombre

- **Prioridad:** P0 (pauta 1).
- **Tarea:** borrar un elemento de la lista.
- **Evidencia (código):** `app/src/main/res/layout/fila_elemento.xml:31` define un `ImageButton` con `@drawable/ic_borrar` sin `contentDescription`.
- **Reproducción:** deslizar por la lista; tras cada fila, TalkBack dice «Botón sin etiqueta».
- **Esperado:** sin parada extra; en el menú de acciones de la fila, «Borrar Informe de marzo».
- **Propuesta:** quitar el botón como parada (`importantForAccessibility="no"`, sigue visible para quien lo toca) y añadir la acción con `fila.accionesAccesibles(acciones, ZonaAcciones.ELEMENTO)` y la cadena `accion_borrar_x`.
- **Validación:** `AccesibilidadTest` sin «¡SIN NOMBRE!» en esa pantalla; comprobación con TalkBack del menú de acciones.

### A02. Al cerrar el diálogo de confirmación el foco vuelve al principio

- **Prioridad:** P1 (coherencia 34). Hipótesis del código, sin observar con TalkBack.
- **Evidencia:** `app/src/main/java/.../ListaActivity.kt:88` refresca la lista con `notifyDataSetChanged()` y el adaptador no tiene ids estables; no hay nada que devuelva el foco.
- **Esperado:** el foco en la fila siguiente a la borrada o en la misma fila si no se borró.
- **Propuesta:** `setHasStableIds(true)` con el id del dato, o `ListAdapter` con `DiffUtil`; registrar `MemoriaFoco` en la `Application`.
- **Validación:** `focoVuelveTrasVentanas` con la vuelta de las acciones de una fila; comprobación con TalkBack.

### A03. El deslizador de velocidad dice un porcentaje del recorrido

- **Prioridad:** P1 (pauta 26).
- **Evidencia:** `app/src/main/res/xml/ajustes.xml:12` usa `SeekBarPreference` de 50 a 300; TalkBack dirá el porcentaje del recorrido, no el valor.
- **Esperado:** «100 por ciento», y de 5 en 5.
- **Propuesta:** `DeslizadorAjuste` con `app:seekBarIncrement="5"` y `DeslizadorAjuste.formatos["velocidad"] = DeslizadorAjuste.porCiento`.
- **Validación:** línea «DESLIZADOR antes → después» de `AccesibilidadTest`; comprobación con TalkBack.

## Cierre de la auditoría

- Resumen por prioridad (cuántos P0, P1 y P2).
- Qué se revisó y qué no.
- Qué se ejecutó (pruebas, prueba instrumentada, registro de TalkBack) y con qué resultado real.
- Qué queda pendiente de comprobar con TalkBack, Switch Access o Voice Access.
- Fecha y versión (o commit) revisada.
