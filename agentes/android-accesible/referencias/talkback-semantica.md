# Semántica para TalkBack: nombres, roles, estados, encabezados, regiones vivas y orden

TalkBack no ve la pantalla: lee el árbol de accesibilidad. De cada elemento dice su **nombre**, su **estado**, su **rol** y, si la tiene, una **pista** de uso. Si el árbol está bien, TalkBack, Switch Access y Voice Access funcionan sin código extra. Esta referencia cubre las pautas P0 1 a 4, 7, 8, 10 a 12 y las P1 13, 16 a 18.

## Nombres accesibles (P0 1 y 2, P1 13 y 18)

Todo control interactivo tiene un nombre localizado. TalkBack nunca debe decir «sin etiqueta» ni «botón» a secas.

| Elemento (Views) | De dónde sale el nombre |
|---|---|
| `ImageButton`, `ImageView` pulsable, `MaterialButton` solo con icono, FAB | `android:contentDescription` |
| Botón Arriba o de menú de la `MaterialToolbar` | `app:navigationContentDescription` o `setNavigationContentDescription()` |
| Elemento de menú de la barra | `android:title` (si solo se ve el icono, el título sigue siendo el nombre) |
| `EditText` dentro de `TextInputLayout` | `android:hint` del `TextInputLayout` (etiqueta flotante) |
| Icono final o inicial de un `TextInputLayout` | `app:endIconContentDescription`, `app:startIconContentDescription` |
| `EditText` suelto con una etiqueta visible | `android:labelFor` en el `TextView` de la etiqueta |
| `Button`, `Chip`, `CheckBox`, `Switch` | Su texto visible |

Reglas:

- La etiqueta dice la **acción o el propósito** («Editar nota», «Reproducir»), no el aspecto («lápiz», «triángulo»).
- Sin «botón», «marcado», «seleccionado» ni «pulsa dos veces» en el nombre: el rol, el estado y la forma de uso los añade el sistema.
- Los `EditText` **no** llevan `contentDescription`: taparía lo escrito. Los errores van con `TextInputLayout.setError()` (o `error = ...`) y explican cómo corregir («Escribe una dirección que empiece por https://»).
- **Nombres únicos** en una misma pantalla: no dos «Añadir» (uno en la barra y otro en la lista). En listas, el nombre incluye el elemento: «Borrar Informe de marzo».
- El **texto visible** de un botón aparece al principio y en el mismo orden en su nombre accesible. Voice Access activa los controles por lo que se ve («Pulsa Guardar»): un botón que muestra «Guardar» y se llama «Confirmar cambios» no responde.
- Todo en recursos `strings.xml`, traducido. Nunca un nombre en inglés en la versión española.

## Roles y estados (P0 4)

- Usa los **widgets estándar**: `MaterialSwitch`, `MaterialCheckBox`, `MaterialRadioButton`, `Slider`, `MaterialButton`, `TabLayout`. Traen rol y estado de serie.
- Una vista propia toma el rol de una clase del SDK: `info.className = Button::class.java.name` (ayudante `comoBoton()`), `SeekBar`, `CheckBox`...
- El estado va en `stateDescription` (`ViewCompat.setStateDescription` o en el delegado: `info.stateDescription = ...`), y en `isCheckable` e `isChecked` si es marcable.
- **Seleccionado** y **marcado** no son lo mismo: `isSelected` es «el actual» (la pestaña o la sección en la que estás); `isChecked` es una opción que se activa y desactiva. Una acción nunca queda ni seleccionada ni marcada.
- `roleDescription` solo si no hay ninguna clase equivalente. **Nunca** en un encabezado que ya tiene `setAccessibilityHeading`: TalkBack diría «encabezado» dos veces. El nivel del encabezado, si importa, va en `stateDescription`.

## Vistas propias pulsables (P0 7)

- Si sobrescribes `onTouchEvent`, llama a `performClick()` al reconocer el toque: es lo que activa el doble toque de TalkBack.
- Una vista con varias zonas pulsables dibujadas (un teclado, un gráfico, un calendario propio) usa `ExploreByTouchHelper`: cada zona es un nodo virtual con nombre, rol y acciones.
- `KEYCODE_DPAD_CENTER` y `KEYCODE_ENTER` hacen lo mismo que el toque (teclado físico, Switch Access).
- Agrupar texto bajo un padre: el padre `focusable` (o `ViewCompat.setScreenReaderFocusable(padre, true)`) y los hijos sin foco propio. TalkBack junta los textos de los hijos en una sola parada.

## Cada pantalla se anuncia (P0 8)

- El **título de la Activity** (`title = ...`) es lo que TalkBack dice al entrar. Debe ser único y decir dónde estás («Ajustes de reproducción», no «Ajustes» en tres sitios).
- Fragments, pestañas y paneles que cambian sin cambiar de Activity: `ViewCompat.setAccessibilityPaneTitle(vista, "...")`.
- El cajón de navegación: `DrawerLayout.setDrawerTitle(GravityCompat.START, ...)`.

## Encabezados (P1 16)

- `ViewCompat.setAccessibilityHeading(vista, true)` (ayudante `encabezado()`) o `android:accessibilityHeading="true"` en los títulos de sección. TalkBack permite saltar de encabezado en encabezado.
- Ni ninguno ni todo encabezado: solo lo que divide la pantalla en partes.
- En contenido HTML (WebView, documentos), encabezados reales `h1` a `h6`.

## Mensajes de estado y regiones vivas (P0 10)

- Un mensaje que aparece sin mover el foco (resultado de una búsqueda, «Guardado», un error general) va en una vista con `android:accessibilityLiveRegion="polite"` (`ViewCompat.setAccessibilityLiveRegion`). TalkBack lo lee cuando cambia, sin quitarle el foco al usuario.
- `assertive` solo para lo urgente: interrumpe lo que esté diciendo TalkBack.
- Nunca una región viva que cambie cada segundo (un contador, la posición de reproducción): TalkBack no callaría.
- Errores de un campo: `setError` en su `TextInputLayout`. Avisos con acción: Snackbar (ver [dialogos-foco-y-espera.md](dialogos-foco-y-espera.md)).
- `announceForAccessibility` está obsoleto en API 36. Prefiere región viva, `stateDescription` o el título del panel; si queda algún uso, que sea para confirmaciones pasajeras y documentado.

## Orden de lectura (P0 11)

- El orden en que TalkBack recorre la pantalla es el del árbol de vistas. **Reestructura el diseño** para que coincida con el orden visual antes de recurrir a `accessibilityTraversalBefore` o `accessibilityTraversalAfter`; si los usas, sin ciclos.
- `start` y `end` en lugar de `left` y `right`, para que el orden también sea correcto en idiomas de derecha a izquierda.
- **Nunca `isFocusable = false` en un `ScrollView` o `NestedScrollView`** (ni `android:focusable="false"`). TalkBack solo desplaza solo un contenedor enfocable: si no lo es, al deslizar a la derecha desde el último elemento visible vuelve al principio y el resto de la pantalla solo se alcanza desplazando con dos dedos. Déjalo enfocable, como viene de serie (no es una parada propia: TalkBack se para en lo de dentro), y quita el resaltado de foco de teclado con `defaultFocusHighlightEnabled = false` (API 26 y posteriores). Comprobado con TalkBack 15 en el emulador.

## Nada oculto que se lea, nada visible que no se lea (P0 12)

- Lo que no se ve no se lee: un cajón cerrado, una vista con `alpha = 0`, un panel fuera de pantalla. Usa `visibility = GONE` o `importantForAccessibility = noHideDescendants`.
- Lo decorativo (iconos junto a un texto que ya lo dice, divisores, ilustraciones) con `importantForAccessibility="no"`.
- Lo que se ve y aporta información (un icono de estado sin texto) tiene nombre o forma parte del texto de su fila.

## Enlaces (P1 17)

- Enlaces dentro de un texto con `URLSpan` o `Linkify`, activables desde el menú de TalkBack (enlaces).
- El texto del enlace se entiende solo: «Política de privacidad», nunca «aquí» ni «pulsa este enlace».

## No adaptar la interfaz al lector (P2 32)

Detectar TalkBack (`lectorActivo()`) solo para ajustar tiempos y no ocultar cosas solas. Nunca para cambiar la interfaz entera: quien usa TalkBack a veces mira la pantalla, y quien no lo usa también necesita una interfaz clara.

## Equivalentes en Jetpack Compose

En Compose el árbol de accesibilidad es el **árbol semántico**. Los componentes de Material 3 ya ponen rol y estado; lo propio se declara con `Modifier.semantics`.

- **Nombre:** `Icon(..., contentDescription = stringResource(R.string.editar_nota))`; en lo demás, `Modifier.semantics { contentDescription = "..." }`. Un `Icon` decorativo, `contentDescription = null`.
- **Campo de texto:** `OutlinedTextField(label = { Text(...) })`, sin `contentDescription`; error con `isError = true` y `supportingText`, o `Modifier.semantics { error("...") }`.
- **Rol:** `Modifier.clickable(role = Role.Button, onClickLabel = "...")`, `Modifier.semantics { role = Role.Switch }`. `Role` tiene Button, Checkbox, Switch, RadioButton, Tab, Image, DropdownList y otros.
- **Estado:** `Modifier.semantics { stateDescription = "..." }`; seleccionado con `Modifier.selectable(selected = ..., role = Role.Tab)` o `semantics { selected = true }`; marcable con `Modifier.toggleable(value = ..., role = Role.Switch)`.
- **Encabezado:** `Modifier.semantics { heading() }`.
- **Título de panel:** `Modifier.semantics { paneTitle = "..." }`.
- **Región viva:** `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` (o `Assertive`).
- **Agrupar:** `Modifier.semantics(mergeDescendants = true) {}`; `clickable` ya fusiona a sus hijos. Para sustituir todo lo que dicen los hijos, `Modifier.clearAndSetSemantics { contentDescription = "..." }` (los hijos dejan de existir para TalkBack: inclúyelo todo).
- **Ocultar:** `Modifier.semantics { hideFromAccessibility() }` en las versiones recientes de Compose (en las anteriores, `invisibleToUser()`); lo decorativo, sin semántica.
- **Orden:** el de composición. Si no basta, `Modifier.semantics { isTraversalGroup = true }` en el contenedor y `traversalIndex = 1f` (menor se lee antes) en los hijos.
- **Tamaño mínimo:** `Modifier.minimumInteractiveComponentSize()` (48 dp); los componentes de Material 3 ya lo aplican.
- **Acciones propias:** `customActions` (ver [listas-y-acciones.md](listas-y-acciones.md)).
- **Comprobar:** en las pruebas, `composeTestRule.onRoot().printToLog("ARBOL")` muestra el árbol semántico tal como lo recibe TalkBack.
