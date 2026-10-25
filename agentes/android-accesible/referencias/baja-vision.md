# Baja visión: contraste, tamaño, fuente y tema

Muchas personas que usan TalkBack también miran la pantalla, y muchas otras no usan lector pero necesitan letra grande, buen contraste o modo oscuro. Esta referencia cubre las pautas P1 19 a 22, 25 y las P2 28 y 29.

## Objetivos táctiles (P1 19)

- **48 × 48 dp como mínimo** para todo lo que se toca (nunca menos de 24 dp), con unos 8 dp de separación.
- Un icono de 24 dp se agranda con relleno o `minWidth` y `minHeight`, no encogiendo el área táctil. Si el diseño no deja, `TouchDelegate` en el padre.
- `MaterialButton` y los controles de Material ya garantizan 48 dp de área (aunque se dibujen más pequeños). No los rompas con alturas fijas ni quitando los insets.
- Campos de texto de 56 dp de alto (Material 3). `isSingleLine` o `setLines` quitan la altura mínima: vuelve a ponerla (ayudante `campoTexto`).

## Contraste (P1 20 y 21)

| Qué | Contraste mínimo |
|---|---|
| Texto normal | 4,5:1 |
| Texto grande (18 sp o más, o 14 sp o más en negrita) | 3:1 |
| Iconos con significado, bordes de campos, indicador de foco y estados | 3:1 |

- En **tema claro y oscuro**, incluidos los hints de los campos y el texto deshabilitado que aún deba leerse.
- Usa los **roles de color de Material por parejas**: `colorOnSurface` sobre `colorSurface`, `colorOnSurfaceVariant` sobre `colorSurfaceContainer...`, `colorOnSecondaryContainer` sobre `colorSecondaryContainer`. Un color suelto (`#888888` sobre blanco) casi nunca llega.
- El **color dinámico** (Material You) cambia los colores con el fondo de pantalla: comprueba el contraste con varios fondos o desactívalo en lo crítico.
- **El color nunca es la única señal**: errores, estados, selección y gráficos también con texto, icono, forma o negrita.

## Fuente grande y tamaño de pantalla (P1 22)

- Texto **siempre en sp**, nunca en dp ni en px.
- Con la fuente al **200 %** (Android 14 o posterior; escalado no lineal) nada se corta ni se solapa: `wrap_content`, `minHeight` en lugar de alturas fijas, `ScrollView` donde el contenido pueda crecer, y desplazamiento en un solo eje.
- Sin `ellipsize` ni `maxLines` en información esencial (títulos de elementos, mensajes de error, importes).
- Prueba también con el tamaño de pantalla (densidad) aumentado.
- En el emulador: `adb shell settings put system font_scale 2.0` (vuelve con `1.0`) y `adb shell wm density` para ver y cambiar la densidad (`adb shell wm density reset` para volver).

## Teclado y foco visible (P1 25)

- Todo operable con teclado físico (Tab, flechas, Intro), sin trampas de foco.
- El foco se ve (Material lo dibuja; si el fondo es propio, incluye el estado `state_focused`) y no queda tapado por barras ni por el teclado en pantalla.

## Modo oscuro, animaciones y destellos (P2 28)

- Tema `DayNight` que respeta el ajuste del sistema (y, si la app lo ofrece, una preferencia propia con «Según el sistema» por defecto), sin romper el contraste.
- Respetar **«Quitar animaciones»**: las animaciones del sistema ya lo hacen; las propias comprueban `ValueAnimator.areAnimatorsEnabled()` o `Settings.Global.ANIMATOR_DURATION_SCALE`.
- Nada parpadea más de **3 veces por segundo**.

## Orientación (P2 29)

Sin `screenOrientation` fija: la app funciona en vertical y horizontal (hay quien tiene el móvil fijo en un soporte).

## Ajustes del sistema que hay que respetar

- Texto en negrita del sistema (Android 12 o posterior) y texto de alto contraste: no los anules con fuentes y colores fijos.
- Ampliación: la app sigue funcionando con la pantalla ampliada; los avisos importantes no quedan fuera de la zona ampliada sin otra señal.
- Corrección e inversión de color: no dependas de un tono concreto.

## Equivalentes en Jetpack Compose

- Tamaños de texto en `.sp` (los de `MaterialTheme.typography` ya lo están).
- `Modifier.minimumInteractiveComponentSize()` en lo pulsable propio; los componentes de Material 3 ya lo aplican.
- Colores de `MaterialTheme.colorScheme` por parejas (`onSurface` sobre `surface`).
- Vista previa con `@PreviewFontScale` (varias escalas, hasta 200 %) y `@PreviewLightDark`.
- Sin `maxLines` ni `overflow = TextOverflow.Ellipsis` en información esencial; `Modifier.verticalScroll` donde el contenido pueda crecer.
- `LocalDensity.current.fontScale` solo para ajustar el diseño (pasar de fila a columna), nunca para ignorar la escala.
