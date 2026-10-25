# Perfil del desarrollador y convenciones comunes

Este archivo lo comparten **todos** los agentes. Tómalo como contexto obligatorio antes de actuar con cualquiera de ellos. Aplica estas convenciones salvo que el proyecto en el que trabajas diga otra cosa de forma explícita (en su `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `pyproject.toml`, etc.).

Cada agente es **autónomo**: todo lo que necesita está en su carpeta y en este archivo. No hace falta consultar ningún otro proyecto.

## Quién es el usuario

- Programa en **Python** y **wxPython** (Phoenix) para **Windows**.
- **Versión de Python: no hay una fija.** Usa la que declare el proyecto (`requires-python` en `pyproject.toml`, `.python-version`, el Python del entorno virtual existente). Si no declara ninguna, pregunta, o propone la más reciente que tenga ruedas publicadas de wxPython y cx_Freeze. A veces el usuario necesitará una más nueva y otras veces una más antigua; no des por hecho ninguna.
- Sus aplicaciones son **accesibles con lectores de pantalla** (NVDA, JAWS, Narrador). La accesibilidad es un requisito de cada cambio, no un extra.
- Trabaja en **español**. La interfaz, los mensajes, los registros (logs), los comentarios, las docstrings y los informes van en español de España. Solo se mantienen en inglés los nombres de API o los identificadores técnicos que ya lo estén.
- Compila **siempre con cx_Freeze** e incluye los runtimes de Visual C++ (`include_msvcr`).

## Reglas de oro comunes

1. **Todo vive dentro de la carpeta de la aplicación.** La aplicación es portable. Lo que genera o necesita va en `data/`, junto a `main.py` (desde código) o junto al `.exe` (compilada): configuración, base de datos, registros, caché, temporales, sesiones, descargas por defecto, binarios auxiliares, sonidos, traducciones y documentación de usuario. Nunca va en `%APPDATA%`, `%LOCALAPPDATA%`, `%TEMP%` ni en el registro de Windows, salvo que el usuario lo pida expresamente.
2. **La compilación distribuible sale limpia.** A `build/<App>/data` solo viaja lo imprescindible para arrancar: binarios, sonidos por defecto, traducciones, manual, iconos y plugins sin `__pycache__`. Nunca configuración personal, registros, bases de datos, sesiones, copias de seguridad, temporales ni claves.
3. **Rutas siempre calculadas.** Desde código, la raíz es la carpeta de `main.py`. En la versión compilada, es `Path(sys.executable).parent`. Un único módulo de rutas (`app/core/paths.py` o equivalente) las resuelve todas.
4. **Ningún error se pierde.** Todo fallo queda registrado con archivo, línea, función, hilo y traza. Está prohibido un `except: pass` silencioso sin, al menos, un `log.debug(..., exc_info=True)` justificado.
5. **La interfaz nunca se bloquea.** El trabajo pesado va en hilos, y los controles solo se modifican desde el hilo principal (`wx.CallAfter`). Si el hilo de la interfaz se congela, el lector de pantalla se queda mudo.
6. **Botones y diálogos en español.** Se usa `wx.Locale(wx.LANGUAGE_SPANISH)` al arrancar y etiquetas explícitas con acelerador (`&Aceptar`, `&Cancelar`, `&Sí`, `&No`).
7. **No inventes resultados.** No afirmes «probado con NVDA», «compila» o «funciona» sin haberlo ejecutado. Separa lo verificado, lo que se deduce del código y lo que queda pendiente.

## Estilo de código

- Respeta el estilo del proyecto: indentación (espacios o tabulaciones), nombres y organización. En un proyecto nuevo, usa **tabulaciones** y `ruff` con `builtins = ["_"]`. Con tabulaciones, cada nivel de sangría se oye como una sola tabulación en el lector de pantalla.
- La función de traducción `_` suele instalarla gettext en `builtins`. No la redefinas dentro de una función: provoca `UnboundLocalError`.
- Las docstrings y los comentarios explican el **porqué**, en español.
- Usa la sintaxis de tipos que admita la versión mínima del proyecto. Con `from __future__ import annotations` puedes escribir `list[str]` y `X | None` en anotaciones desde Python 3.7.
- Las pruebas van con `pytest` en `tests/` y nunca tocan el `data/` real: usa una variable de entorno de redirección (`APP_DATA_DIR`) o `tmp_path`.

## Estructura típica de un proyecto

```
Proyecto/
├── main.py                 # Punto de entrada; configura registro, locale y lanza wx
├── app/
│   ├── core/               # paths.py, version.py, registro, configuración, voz...
│   ├── services/           # lógica sin wx
│   └── ui/                 # ventanas, diálogos, controles accesibles
├── data/                   # TODO lo que la app necesita o genera (portable)
│   ├── bin/  sounds/  locales/  docs/  icons/  plugins/      ← se distribuyen
│   └── config/  logs/  db/  cache/  temp/  sessions/  backups/ ← NO se distribuyen
├── tools/                  # empaquetar.py, verificar_build.py, generadores...
├── tests/
├── entorno.bat             # menú: crear venv, instalar, ejecutar, pruebas, compilar...
├── setup_cx.py             # cx_Freeze
├── requirements.txt / requirements-dev.txt
└── pyproject.toml          # versión, requires-python, ruff, pytest
```

## Cómo entregar el trabajo

- Explica qué cambiaste y por qué, con rutas `archivo:línea`.
- Indica qué ejecutaste (pruebas, compilación) y cuál fue el resultado real.
- Enumera lo que queda pendiente de verificar a mano: lector de pantalla real, compilación en otra máquina, etc.
- Escribe pensando en una persona que usa lector de pantalla: estructura clara con encabezados y listas, sin tablas enormes ni arte ASCII.
