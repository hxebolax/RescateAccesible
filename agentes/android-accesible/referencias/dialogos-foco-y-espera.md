# Diálogos, foco y espera

Con TalkBack, el foco es la posición del usuario en la pantalla. Perderlo al cerrar un diálogo, oír otra vez el título de la pantalla o quedarse sin saber si la app está trabajando obliga a recorrer todo de nuevo. Esta referencia cubre las pautas P0 9, P1 23 y 24, y las de coherencia 34 y 35.

## Diálogos (P0 9)

- Usa `MaterialAlertDialogBuilder`: es una ventana aparte, así que **aísla el foco** (TalkBack no se escapa a la pantalla de detrás) y Atrás lo cierra.
- Título que dice qué se decide. Botones que dicen la **acción**: «Borrar», «Salir sin guardar», «Cancelar»; nunca «Sí» y «No» si se puede evitar (ayudante `confirmar(mensaje, textoSi)`).
- Mensajes largos con `setMessage`: TalkBack los recorre y se pueden copiar (ayudante `mensaje`).
- Listas de opciones con `setItems` o `setSingleChoiceItems` (ayudantes `elegir` y `elegirUno`): TalkBack dice «marcado» solo en la opción actual.
- Un campo dentro de un diálogo, con `TextInputLayout` y su hint; el foco al campo al mostrarse; Intro acepta (ayudante `pedirTexto`).
- **Capas propias** que no son una ventana (un panel que se superpone, una hoja inferior hecha a mano): el fondo con `importantForAccessibility = noHideDescendants`, `setAccessibilityPaneTitle` en la capa, mover el foco a ella al abrir y devolverlo al cerrar. Si puedes, usa `BottomSheetDialog` o un diálogo y te ahorras todo esto.

## El foco vuelve a donde estaba (coherencia 34)

Al cerrar cualquier diálogo, menú o pantalla que se abrió encima, el foco de TalkBack vuelve al elemento desde el que se abrió. La plantilla `MemoriaFoco.kt` lo hace para **toda la app** sin código en cada sitio:

1. Se registra una vez en `Application.onCreate()`: `registerActivityLifecycleCallbacks(MemoriaFoco)`.
2. Cuando la ventana de una pantalla pierde el foco (se abre algo encima), apunta qué vista tenía el foco de accesibilidad y, si estaba en una lista, qué fila (posición e id estable).
3. Cuando lo recupera, al cabo de unos 450 ms (TalkBack mueve el foco al volver y hay que corregirlo después), lo pone en la misma vista o, si la lista se redibujó, en la fila con el mismo id.
4. Sirve para diálogos encadenados (espera y después resultado): si al perder el foco no hay ninguna vista enfocada, conserva lo anterior.
5. Solo actúa con TalkBack activo y descarta recuerdos de más de 10 minutos.

### Por qué se descarta el título repetido

Al cerrarse un diálogo o un menú, Android envía un cambio de ventana (`TYPE_WINDOW_STATE_CHANGED`) de la pantalla de debajo. TalkBack lo lee como coletilla («Ajustes») y además **reinicia su control de lectura**: deja de moverse por las acciones y pasa a deletrear. Como la pantalla no ha cambiado, `MemoriaFoco` descarta ese evento durante el primer segundo tras la vuelta, y solo cuando la pantalla no se fue al fondo (si se abrió otra Activity encima y se vuelve, el título sí se anuncia, porque es un cambio de pantalla real).

### Requisitos y límites

- Las listas necesitan ids estables para encontrar la fila tras redibujarse.
- Si la fila ya no existe (se borró), no se fuerza nada: TalkBack pone el foco donde decida.
- En Compose no sirve tal cual: no hay vistas que recordar. Ver el apartado de Compose.

## Mover el foco a propósito

- Solo cuando el usuario lo espera: al abrir el cajón (a la sección actual), al abrir una capa propia (a su primer elemento), al volver (a donde estaba).
- Con `vista.performAccessibilityAction(ACTION_ACCESSIBILITY_FOCUS, null)` dentro de un `post { }`, para que la vista ya esté dibujada.
- Nunca robar el foco por un cambio que el usuario no ha pedido (llega un dato nuevo, termina una descarga): eso va en una región viva educada.

## Ventana de espera para lo lento (coherencia 35)

Toda acción que puede tardar (red, importar, reconocer texto, consultar un servicio de IA, sincronizar) usa `conEspera(titulo, mensaje) { ... }` (plantilla `Espera.kt`):

- Si termina en menos de medio segundo, no se muestra nada.
- Si tarda más, aparece un diálogo con el título, el mensaje y **Cancelar**. TalkBack lo lee al aparecer. La rueda de progreso no se lee: el mensaje ya dice qué pasa.
- Cancelar (o Atrás) para el trabajo en el acto y avisa «Cancelado».
- El **resultado se muestra en la misma pantalla**, nunca después en otra, cuando el usuario ya está haciendo otra cosa.
- Al cerrarse, el foco vuelve al botón que lo lanzó (`MemoriaFoco`).
- Quien la llama **relanza `CancellationException`** antes de cualquier `catch (e: Exception)`; si no, la cancelación se trata como un error y se muestra un mensaje falso.
- Los errores se muestran en lenguaje claro («No hay conexión. Comprueba la red y vuelve a intentarlo»), nunca la excepción técnica, y se registran con `Log.e(etiqueta, mensaje, excepcion)`.
- Tiempos de red cortos para lo que el usuario espera con la app delante (unos segundos para conectar), no los de por defecto de la biblioteca.

## Tiempos y avisos (P1 23 y 24)

- **Nada se oculta solo con TalkBack activo** (barras de reproducción, controles superpuestos). Si algo debe desaparecer, el tiempo sale de `AccessibilityManager.getRecommendedTimeoutMillis(...)`, que respeta el ajuste «Tiempo para actuar».
- Nada avanza solo (carruseles, presentaciones). Si avanza, con pausa y botones anterior y siguiente.
- **Toast** solo para confirmaciones pasajeras. Ninguna información crítica ni ninguna acción (Deshacer) va solo en un Toast.
- **Snackbar con acción**: indefinido o con el tiempo recomendado (ayudante `conDeshacer`), y nunca como única forma de hacer algo.

## Atrás

- Atrás cierra lo que esté encima: diálogo, cajón, capa propia, modo de selección. En pantallas propias, con un `OnBackPressedCallback` que solo está activo mientras hay algo que cerrar (ver [material3-y-calidad.md](material3-y-calidad.md)).

## Equivalentes en Jetpack Compose

- **Diálogos:** `AlertDialog` de Material 3 es una ventana aparte y aísla el foco. `ModalBottomSheet` también. Botones con la acción.
- **Capas propias:** `Modifier.semantics { paneTitle = "..." }` en la capa y el contenido de detrás fuera del árbol mientras está abierta.
- **Foco:** `FocusRequester` y `Modifier.focusRequester(...)` mueven el foco de entrada (teclado); TalkBack suele seguirlo en elementos enfocables, pero Compose no tiene una API pública para mover el foco de accesibilidad. Compruébalo con TalkBack real y no lo des por hecho.
- **Volver al elemento:** guarda el id del elemento que abrió el diálogo y, al cerrarse, pide el foco a su `FocusRequester` (o desplaza la lista hasta él). Verifica con TalkBack que el foco de accesibilidad llega.
- **Espera:** el mismo patrón con estado: `var esperando by remember { mutableStateOf(false) }`, un `AlertDialog` con Cancelar que cancela el `Job` y el resultado en la misma pantalla. Relanza también `CancellationException`.
- **Atrás:** `BackHandler(enabled = hayAlgoAbierto) { cerrar() }`.
- **Avisos:** `SnackbarHostState.showSnackbar(mensaje, actionLabel = "Deshacer", duration = SnackbarDuration.Indefinite)` cuando hay acción.
