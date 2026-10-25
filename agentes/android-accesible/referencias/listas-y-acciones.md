# Listas y acciones de TalkBack

Las listas son donde más se nota una app mal hecha con TalkBack: filas que se leen en cinco paradas, botones repetidos con el mismo nombre, el foco que salta al principio al refrescar y gestos sin alternativa. Esta referencia cubre las pautas P0 5 y 6, P1 14 y 15, y las de coherencia 36 y 37.

## Cada fila es una sola parada (P1 14)

- La **raíz de la fila** es la pulsable (`isClickable`, `isFocusable`) y los hijos no son enfocables. TalkBack junta sus textos en una línea.
- Lo dice todo en una línea, con el **estado primero**: «No leído, Informe de marzo, Contabilidad, hace 5 minutos». Ordena los hijos en el diseño en ese orden; si no se puede, pon el `contentDescription` de la fila con ese texto (y recuerda que entonces los hijos no se leen).
- Un icono de estado (favorito, descargado, error) no se queda sin decir: su significado entra en el texto de la fila.
- **Sin pulsables anidados con los mismos límites** (una fila pulsable que contiene una tarjeta pulsable del mismo tamaño): TalkBack se para dos veces en lo mismo.
- Los botones pequeños dentro de la fila (borrar, más opciones) son mejor **acciones de TalkBack** que paradas extra. Si siguen siendo botones visibles, con nombre único: «Más opciones de Informe de marzo».
- Doble toque en la fila hace lo principal (abrir). Si no es evidente, ponle nombre: `fila.etiquetaPulsar("Abrir informe")`.

## Que el foco no se pierda al refrescar (P1 15)

- **Ids estables** (`setHasStableIds(true)` y `getItemId` con el id del dato) o `ListAdapter` con `DiffUtil`. Con `notifyDataSetChanged()` sin ids estables, TalkBack pierde la fila en la que estaba y vuelve al principio.
- La `RecyclerView` sin foco propio (`sinFocoPropio()`): si no, con la lista vacía TalkBack se para en ella y solo dice «Lista».
- La lista vacía tiene un texto visible que lo explica («No hay elementos. Pulsa Añadir para crear uno»).
- Contenedores de páginas (`ViewPager2`) con `importantForAccessibility="no"` en el contenedor: si no, TalkBack dice «Página 1 de 5» en cada movimiento.
- Al volver de un diálogo o de otra pantalla, el foco vuelve a la fila (`MemoriaFoco`, que usa los ids estables para encontrarla aunque se haya redibujado).

## Cada gesto tiene alternativa (P0 5 y 6)

Deslizar para borrar, arrastrar para ordenar, pulsación larga, doble toque, pellizcar: con TalkBack los gestos de la app no llegan o llegan distintos. Cada uno tiene su alternativa:

| Gesto | Alternativa |
|---|---|
| Deslizar la fila para borrar o archivar | Acción de TalkBack «Borrar Informe de marzo» y opción en el menú de la fila |
| Arrastrar para ordenar | Acciones «Subir», «Bajar», «Al principio», «Al final» |
| Pulsación larga | `accionAlMantener(texto) { ... }`: TalkBack dice «Toca dos veces y mantén pulsado para ...» |
| Pellizcar para ampliar | Botones o acciones «Ampliar» y «Reducir» |

- Las acciones se ponen con `ViewCompat.addAccessibilityAction` (ayudante `accionesAccesibles(acciones)`). En una `RecyclerView` hay que **quitar las del bind anterior** en cada bind; si no, se acumulan al reciclar la fila. El ayudante lo hace solo: guarda los ids en la etiqueta `R.id.acciones_accesibles`.
- Clic y pulsación larga poco evidentes, con nombre: `ViewCompat.replaceAccessibilityAction(vista, ACTION_CLICK, "Reproducir episodio", null)` (ayudante `etiquetaPulsar`).

## Acciones según el estado (coherencia 36)

Botones y acciones de TalkBack dicen lo que harán **ahora**, según el estado del elemento:

- «Quitar de favoritos» si ya está en favoritos; «Añadir a favoritos» si no.
- «Ya está en tu biblioteca: abrirlo» en vez de «Añadir a la biblioteca» si ya está.
- «Cancelar descarga» mientras descarga, «Borrar descarga» cuando terminó, «Reintentar descarga» si falló.
- Nunca se ofrece algo que ya está hecho. Las acciones se calculan en cada bind con el estado actual.

## Acciones personalizables por zona (coherencia 37)

Una fila con quince acciones es lenta de recorrer. Por eso cada **zona** de la app (elementos, carpetas, mensajes...) tiene un catálogo de acciones, y el usuario elige en Ajustes cuáles salen en las acciones rápidas de TalkBack y en qué orden. La plantilla es `AccionesTalkBack.kt`.

- `ZonaAcciones`: una entrada por zona, con su título.
- `AccionesTalkBack.entradas(contexto, zona)`: el catálogo. `e(clave, textos...)` sale por defecto; `o(clave, textos...)` solo si el usuario la añade (útil para no cambiar lo que había al añadir acciones nuevas).
- Todas las **variantes de texto** de una acción (añadir y quitar de favoritos; subir y bajar) van en la misma entrada: se reconocen por su texto, incluidos los que llevan datos («Borrar %1$s» se reconoce por su parte fija).
- En el bind: `fila.accionesAccesibles(acciones, ZonaAcciones.ELEMENTO)`. Deja solo las elegidas, en su orden; las que no están en el catálogo (un enlace del contenido) salen siempre, al final.
- La **pulsación larga** abre siempre el menú con **todas** las acciones (`elegir(titulo, acciones)`): lo que el usuario quita de las rápidas no desaparece.
- Las claves se guardan en preferencias: no las cambies una vez publicadas.

### La pantalla de Ajustes para elegirlas

Ajustes, Acciones de TalkBack: una lista de zonas; al entrar en una, sus acciones (con `AccionesTalkBack.nombre`) en el orden actual.

- Cada acción es una fila de una parada con un interruptor: activado, sale en las acciones rápidas. TalkBack dice «Compartir, activado, interruptor».
- Ordenar sin arrastrar: cada fila tiene las acciones de TalkBack «Subir» y «Bajar» (y botones visibles para quien no usa TalkBack). Tras moverla, el foco sigue en la misma fila.
- Un botón «Restablecer» (`AccionesTalkBack.restablecer`) con confirmación.
- Se guarda al cambiar (`AccionesTalkBack.guardar`); las listas abiertas lo aplican en el siguiente bind.

## Equivalentes en Jetpack Compose

- **Ids estables:** `LazyColumn { items(lista, key = { it.id }) { ... } }`. Sin `key`, el foco se pierde al cambiar la lista.
- **Una parada por fila:** `Modifier.clickable(onClickLabel = "Abrir informe", onClick = ...)` en la fila ya fusiona sus hijos. Para cambiar el orden o el texto, `Modifier.clearAndSetSemantics { contentDescription = "No leído, Informe de marzo, ..." }` (incluye entonces las acciones dentro del mismo bloque).
- **Acciones de TalkBack:** `Modifier.semantics { customActions = listOf(CustomAccessibilityAction("Borrar Informe de marzo") { borrar(); true }) }`. Se recalculan en cada recomposición, así que reflejan el estado sin tener que quitar las anteriores.
- **Pulsación larga con nombre:** `Modifier.combinedClickable(onClickLabel = "...", onLongClickLabel = "Más opciones", onClick = ..., onLongClick = ...)`.
- **Deslizar para borrar:** `SwipeToDismissBox` más la acción propia «Borrar ...» en `customActions`.
- **Ordenar:** acciones «Subir» y «Bajar» en `customActions`.
- **Lista y posición:** `LazyColumn` ya informa de la colección; en una cuadrícula propia, `Modifier.semantics { collectionInfo = CollectionInfo(filas, columnas) }` y `collectionItemInfo` en cada celda.
- **Acciones por zona:** la misma lógica de `AccionesTalkBack.aplicar` sirve para filtrar y ordenar la lista antes de convertirla en `customActions`.
