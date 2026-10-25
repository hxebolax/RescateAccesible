# Cambios del agente android-accesible

## Cómo ampliarlo

- **Nueva pauta o regla:** si es corta y no negociable, en «Reglas de oro» de `SKILL.md`; el detalle, en la referencia que corresponda (semántica, listas, diálogos, deslizadores, Material 3, baja visión), con su equivalente en Compose si lo tiene.
- **Nuevo ayudante o componente:** en `plantillas/app/src/main/java/com/ejemplo/app/accesibilidad/`, con el paquete de ejemplo, las cadenas en `strings_accesibilidad.xml` (español en `values/`, inglés en `values-en/`), una fila en la tabla de plantillas de `SKILL.md` y su explicación en la referencia. Si se puede comprobar con TalkBack, añade su recorrido a `AccesibilidadTest.kt`.
- **Plantillas de Compose:** si se añaden, van en el mismo paquete con un nombre que lo diga (por ejemplo `MenuLateralCompose.kt`) y no sustituyen a las de Views.
- **Nuevo conocimiento sobre un componente o una versión** (un componente de Material que cambia lo que anuncia, una API que queda obsoleta, una versión de TalkBack que se comporta distinto): anótalo en la referencia con la versión y cómo se comprobó.
- No crees `plantillas/tests/`: la herramienta `probar` del repositorio lanzaría pytest ahí y estas plantillas no son de Python.
- Sube `metadata.version`, anótalo aquí y ejecuta `agentes.bat validar` y `agentes.bat catalogo`.

## Cómo se comprueban las plantillas

Las plantillas salen de código que funciona en una app Android real, con pruebas instrumentadas pasadas en el emulador con TalkBack activo (el recorrido de pantallas, los deslizadores, el menú lateral y la vuelta del foco al cerrar ventanas). Para convertirlas en plantillas se han generalizado: paquete de ejemplo, textos en recursos o en parámetros, y sin nada propio de aquella app.

En este repositorio **no se compilan ni se ejecutan por sí solas**: no hay proyecto Gradle. La versión 1.0.0 sí se comprobó fuera, en una app de demostración mínima (ver la entrada 1.0.0). Al copiarlas a un proyecto:

1. Cambia el paquete `com.ejemplo.app` por el del proyecto en los `.kt` (también el import de `R`) y copia los recursos de `res/values*`. Si alguna cadena ya existe, el compilador dirá «Duplicate resources»: borra la copia de la plantilla.
2. Compila (`gradlew assembleDebug`) y corrige lo que falte (dependencias de la tabla de `SKILL.md`).
3. Rellena en `AccesibilidadTest.kt` la lista `pantallas`, los datos de muestra y los textos de `focoVuelveTrasVentanas`.
4. Pasa la prueba instrumentada con TalkBack activo en el emulador y lee el registro (ver `referencias/pruebas.md`).
5. Pide a quien use TalkBack la comprobación final de las pantallas tocadas.

## 1.0.0 - 2026-10-25

- Primera versión: agente para crear, auditar y corregir apps Android (Kotlin, Views con Material Components y equivalentes en Jetpack Compose) que cumplen Material 3 y las pautas de calidad de Google y son accesibles con TalkBack, Switch Access, Voice Access y para baja visión.
- Pautas recogidas: 12 bloqueantes (P0), 15 de alto impacto (P1), 6 de calidad (P2) y 4 de coherencia (foco que vuelve, ventana de espera, acciones según el estado y acciones personalizables por zona); pautas de calidad de Android y Material 3 (navegación, cajón, Ajustes como puerta única, borde a borde, Atrás predictivo, segundo plano, audio, privacidad, código) y la regla de que gana la accesibilidad cuando chocan.
- Plantillas: `MemoriaFoco`, `conEspera`, ayudantes de `Utilidades.kt`, `AccionesTalkBack`, `DeslizadorAjuste`, `DeslizadorPosicion`, `MenuLateral`, cadenas en español e inglés, identificadores y la prueba instrumentada `AccesibilidadTest`.
- Cambios respecto al código de origen al generalizarlo: `AccionesTalkBack` guarda en un archivo de preferencias propio y trae zonas de ejemplo; `DeslizadorAjuste` toma el formato de un registro por clave; `DeslizadorPosicion` trae un texto de posición por defecto y un paso configurable; `MenuLateral` recibe el menú, el destino actual y el icono como parámetros; `AccesibilidadTest` abre la pantalla de inicio de la app sin nombrar su clase, toma las demás de una lista y tiene un modo estricto (`estricto=true`) que hace fallar la prueba.
- Comprobado el 2026-10-25 en una app de demostración mínima (Kotlin 2.3, AGP 8.7, compileSdk 35, Material 1.12) que usa todas las plantillas: compila la app y la prueba, y `AccesibilidadTest` pasa sus 3 pruebas en el emulador con TalkBack 15 activo. El registro de TalkBack muestra que lee la barra de posición con su valor, el deslizador de ajustes pasa de «100 por ciento» a «105 por ciento», en el menú lateral solo el destino actual está seleccionado y recibe el foco al abrir, y el foco vuelve a su sitio tras el menú y tras las acciones de una fila. El modo `estricto=true` no se ha probado.
- Arreglos de esa comprobación: la prueba necesita `com.google.guava:guava` (variante `-android`) para compilar con el ATF; `ponerMenuLateral` debe llamarse después de `configurarBarra` (documentado en la plantilla); el recorrido salta las vistas que TalkBack no visita (`isImportantForAccessibility`), como la barra interna de `DeslizadorPosicion`, que salía como «sin nombre».
- Referencias: semántica para TalkBack, listas y acciones, diálogos, foco y espera, deslizadores, Material 3 y calidad, baja visión, pruebas y auditoría.
