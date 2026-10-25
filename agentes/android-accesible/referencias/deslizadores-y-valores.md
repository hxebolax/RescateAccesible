# Deslizadores y controles ajustables

Un `SeekBar` sin más hace que TalkBack diga «37 por ciento» de un ajuste que va de 50 a 300, y que cada deslizamiento mueva un trozo del recorrido que no coincide con ningún valor redondo. Esta referencia cubre la pauta P1 26.

## Lo que tiene que pasar

- TalkBack dice el **valor con su unidad**: «10 segundos», «100 por ciento», «18 puntos», «12 minutos de 45 minutos, 26 por ciento, quedan 33 minutos».
- Cada deslizamiento arriba o abajo (o las teclas de volumen, según la configuración de TalkBack) cambia **exactamente un paso coherente**, y siempre a múltiplos del paso: de 100 a 105 o a 95, nunca de 100 a 103.
- Tras moverlo, TalkBack dice el valor nuevo.
- Es **una sola parada**: no se leen aparte la fila, el título y un número sin unidad.
- El nombre dice qué se ajusta («Velocidad de reproducción»); la descripción del ajuste, como pista.

## Cómo se consigue en Views

- **Estado:** `info.stateDescription = "10 segundos"` en el delegado de accesibilidad. Es lo que TalkBack lee en lugar del porcentaje.
- **Rango:** `info.rangeInfo = RangeInfoCompat.obtain(tipo, min, max, actual)`, para que TalkBack sepa que es ajustable.
- **Paso:** se intercepta `ACTION_SCROLL_FORWARD` y `ACTION_SCROLL_BACKWARD` en `performAccessibilityAction` y se calcula el siguiente múltiplo del paso.
- **Valor exacto:** `ACTION_SET_PROGRESS` (API 24 o superior), que usan Voice Access y algunos servicios.
- **Rol:** `className = SeekBar::class.java.name` para que se anuncie como control deslizante.

Plantillas:

- `DeslizadorAjuste.kt`: un `SeekBarPreference` para pantallas de ajustes (androidx.preference). El paso es `app:seekBarIncrement`; el formato del valor se registra por clave al arrancar (`DeslizadorAjuste.formatos["velocidad"] = DeslizadorAjuste.porCiento`).
- `DeslizadorPosicion.kt`: la barra de posición de un reproductor. Un contenedor con la accesibilidad y, dentro, un `Slider` de Material solo visual (con `noHideDescendants` y sin su ayudante de accesibilidad: si no, TalkBack diría «Valor, 0»). Paso del 5 % de la duración, nunca menos de 5 segundos (configurable con `pasoFijoMs`).

## Decir el valor nuevo y API 36

Las dos plantillas dicen el valor nuevo con `announceForAccessibility`, que es lo que se probó con TalkBack y funciona. Está **obsoleto desde API 36** (sigue existiendo, pero puede dejar de tener efecto en el futuro). Alternativas a valorar, **sin dar por hecho que funcionan igual hasta probarlas con TalkBack**:

- Cambiar el estado con `ViewCompat.setStateDescription(vista, texto)`: el sistema envía el evento de cambio de estado y TalkBack lo lee si la vista tiene el foco.
- En un delegado que calcula el estado al vuelo, enviar `TYPE_WINDOW_CONTENT_CHANGED` con `CONTENT_CHANGE_TYPE_STATE_DESCRIPTION` tras el cambio.

Si se sustituye, se verifica con la prueba instrumentada (línea «DESLIZADOR antes y después» del registro) y con el registro de voz de TalkBack.

## Otros controles ajustables

- `NumberPicker` y ruedas de selección: mejor un campo numérico, un `elegirUno` con los valores o botones «Aumentar» y «Disminuir» con el valor en un texto con región viva educada.
- `Slider` de Material: fija `stepSize` para que el paso sea exacto y comprueba con TalkBack qué dice; si lee un número sin unidad, envuélvelo como en `DeslizadorPosicion` o usa su delegado para poner `stateDescription`.
- `RatingBar`: el estado como texto («3 de 5 estrellas»).

## Equivalentes en Jetpack Compose

- `Slider(value, onValueChange, valueRange = 50f..300f, steps = ...)`: `steps` es el número de puntos intermedios, así que el paso queda exacto (de 50 a 300 de 5 en 5 son 49 puntos intermedios).
- El valor con unidad: `Modifier.semantics { stateDescription = "${valor.roundToInt()} por ciento" }`.
- Un control propio: `Modifier.progressSemantics(valor, rango, steps)` o, a mano, `semantics { progressBarRangeInfo = ProgressBarRangeInfo(valor, rango, steps); setProgress { nuevo -> ...; true }; stateDescription = ... }`.
- Comprueba en una prueba de Compose que el nodo tiene el `stateDescription` esperado y, con TalkBack real, que el paso es el correcto.
