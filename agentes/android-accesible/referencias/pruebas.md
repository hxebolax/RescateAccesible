# Pruebas de accesibilidad en Android

Ninguna prueba automática equivale a usar la app con TalkBack. Las pruebas encuentran lo que se puede medir (nombres que faltan, objetivos pequeños, contraste, foco que no vuelve) y dejan constancia; la última palabra la tiene una persona con TalkBack. Al entregar, separa siempre **lo verificado**, **lo que se deduce del código** y **lo pendiente**.

## Niveles

| Nivel | Qué comprueba | Herramienta |
|---|---|---|
| Unitarias | Textos que oirá el usuario (tiempos hablados, etiquetas compuestas, acciones según el estado) | JUnit en `src/test` |
| Comprobaciones automáticas en pruebas de interfaz | Nombres, 48 dp, contraste, duplicados, pulsables anidados | Espresso con `AccessibilityChecks`; Compose con sus comprobaciones de accesibilidad |
| Recorrido con TalkBack activo | Qué lee TalkBack en cada elemento, deslizadores, foco al cerrar ventanas | `AccesibilidadTest.kt` (prueba instrumentada) en el emulador |
| Revisión en el dispositivo | Lo mismo que el ATF, sobre la app real | Accessibility Scanner |
| Uso real | Que las tareas se pueden hacer y se entienden | TalkBack, Switch Access, Voice Access, fuente al 200 % |

## Espresso con AccessibilityChecks

Con la dependencia `androidx.test.espresso:espresso-accessibility`, una línea hace que cada acción de Espresso (un `click()`, un `typeText()`) pase el Accessibility Test Framework sobre toda la pantalla y falle si hay errores:

```kotlin
companion object {
    @BeforeClass @JvmStatic
    fun activarComprobaciones() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
    }
}
```

Los falsos positivos conocidos se silencian con `setSuppressingResultMatcher(...)`, uno a uno y con un comentario que diga por qué. Nunca se silencia una categoría entera para que la prueba pase.

## Pruebas de Compose

- Afirma la semántica, no el aspecto: `onNodeWithContentDescription("Reproducir").assertHasClickAction()`, `onNodeWithText("Velocidad").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100 por ciento"))`, `assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))`.
- `onRoot().printToLog("ARBOL")` muestra el árbol semántico: es lo más parecido a lo que recibe TalkBack.
- Las versiones recientes de Compose (1.8 o posterior) traen comprobaciones del ATF para las pruebas de Compose (artefacto `ui-test-junit4-accessibility` y `enableAccessibilityChecks()` en la regla de la prueba). Comprueba que la versión del proyecto lo tiene antes de proponerlo.

## La prueba instrumentada con TalkBack (`AccesibilidadTest.kt`)

La plantilla recorre las pantallas **con TalkBack encendido**:

- Obtiene `UiAutomation` con `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`. Sin esa bandera, al empezar la prueba Android suspende TalkBack y no se oye nada.
- En cada pantalla recoge los elementos en los que TalkBack se pararía al deslizar, les pone el **foco de accesibilidad** uno a uno (TalkBack los lee de verdad) y registra su nombre y estado. Marca «¡SIN NOMBRE!» los que no tienen.
- En los deslizadores, desliza arriba como TalkBack, registra el valor antes y después, y vuelve.
- Pasa el **Accessibility Test Framework** (las mismas comprobaciones que Accessibility Scanner) con una captura para el contraste.
- `menuLateral`: registra qué expone cada elemento del cajón (debe haber un solo «seleccionado» y ningún elemento marcable).
- `focoVuelveTrasVentanas`: pone el foco en un elemento, abre algo encima (menú, acciones de una fila, espera y resultado), lo cierra y comprueba si el foco volvió; registra los eventos que llegan a TalkBack al cerrar (un cambio de ventana de la pantalla de debajo repetiría su título).
- Por defecto no falla: es un informe. Con `-Pandroid.testInstrumentationRunnerArguments.estricto=true` falla si hay elementos sin nombre, errores del ATF o foco que no vuelve.

Al copiarla: cambia el paquete, rellena la lista `pantallas` con el Intent de cada pantalla, crea datos de muestra en `preparar()` (una lista vacía no prueba nada) y adapta los textos de `focoVuelveTrasVentanas`.

### Preparar el emulador

1. Un AVD con imagen **Google Play** (o Google APIs) y la app «Android Accessibility Suite» (TalkBack) instalada o actualizada desde Play Store. No instales APK de TalkBack de fuentes no oficiales.
2. Guarda el valor actual: `adb -s emulator-5554 shell settings get secure enabled_accessibility_services`.
3. Activa TalkBack:

   ```
   adb -s emulator-5554 shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
   adb -s emulator-5554 shell settings put secure accessibility_enabled 1
   ```

4. Para ver lo que dice: Ajustes de TalkBack, Ajustes avanzados, Ajustes para desarrolladores, «Nivel de salida del registro» en **Detallado** (VERBOSE). Basta con hacerlo una vez en ese AVD.
5. Al terminar, vuelve a poner el valor guardado; si estaba vacío: `adb -s emulator-5554 shell settings delete secure enabled_accessibility_services`.

**Solo en el emulador.** Nunca actives, desactives ni cambies servicios de accesibilidad en un dispositivo físico del usuario sin pedírselo: si usa TalkBack para manejar el teléfono, podría quedarse sin acceso a él.

### Ejecutar y leer el resultado

```
gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ejemplo.app.AccesibilidadTest
adb -s emulator-5554 logcat -d -s Recorrido ATF Foco Eventos Menu
adb -s emulator-5554 logcat -d | findstr /C:"Speaking fragment"
```

(En una consola POSIX, `grep "Speaking fragment"` en lugar de `findstr`.)

- Las líneas `Speaking fragment text="..."` son **lo que TalkBack dijo de verdad**, con el registro detallado activado. Es la prueba más fiel que se puede automatizar.
- `Recorrido`: cada elemento con su número, clase y nombre; «DESLIZADOR antes → después».
- `ATF`: tipo (ERROR, WARNING), comprobación, elemento y mensaje.
- `Foco`: BIEN o MAL, con el nombre antes y después.
- `Eventos`: los eventos que llegan a TalkBack al cerrar una ventana.

### Falsos avisos conocidos del ATF

- Contraste del **recuadro de foco** de TalkBack (se captura la pantalla con el recuadro dibujado).
- Contraste bajo el **oscurecido** de detrás de un cajón o un diálogo abierto.

Cualquier otro aviso se investiga; no se descarta por costumbre.

## Lo que no sirve

- **`adb shell input tap`, `swipe`, `keyevent` o `keycombination` no pasan por TalkBack**: se inyectan saltándose el filtro de accesibilidad. Sirven para manejar la app, no para simular gestos ni atajos de TalkBack, ni para saber qué diría.
- Una **captura de pantalla** o un volcado de `uiautomator dump` muestran el árbol o el aspecto, no lo que se oye.
- Que el ATF no dé errores no significa que la app se pueda usar: no sabe si el orden tiene sentido, si un nombre es comprensible o si una tarea se puede terminar.

## Accessibility Scanner

App de Google para el dispositivo o el emulador: analiza la pantalla visible o graba un recorrido y propone correcciones (etiquetas, objetivos táctiles, contraste, texto que no escala). Útil para revisar pantallas a las que la prueba instrumentada no llega.

## Protocolo con TalkBack real

Lo hace una persona; el agente prepara la lista concreta de qué comprobar y en qué pantalla, sin inventar el resultado.

1. Recorrer cada pantalla deslizando a la derecha: todo se lee, en orden, sin paradas vacías ni duplicadas, con nombres que se entienden.
2. Doble toque en cada control: hace lo que dice.
3. Menú de acciones de TalkBack en las filas: están las acciones, con nombres según el estado, y en el orden elegido en Ajustes.
4. Navegar por encabezados: cada sección tiene el suyo.
5. Abrir y cerrar diálogos, menús y el cajón: el foco vuelve al elemento de partida y no se repite el título.
6. Acciones lentas: aparece la espera con Cancelar, Cancelar funciona y el resultado sale en la misma pantalla.
7. Deslizadores: dicen el valor con unidad y avanzan un paso exacto.
8. Fuente al 200 %, modo oscuro, horizontal: nada cortado ni inalcanzable.
9. Teclado físico, Switch Access y Voice Access («Pulsa Guardar») en los flujos principales.

Nunca escribas «probado con TalkBack» por haber pasado `AccesibilidadTest`, el ATF o Accessibility Scanner. Puedes decir «el registro de TalkBack en el emulador muestra que dijo ...» cuando lo hayas leído en el registro.
