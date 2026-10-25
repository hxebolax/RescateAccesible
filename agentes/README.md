# Agente «Android accesible»

Rescate Accesible se ha hecho con este agente: una *skill* para [Claude Code](https://claude.com/claude-code) especializada en apps Android en Kotlin que cumplen Material 3 y las pautas de calidad de Google y son accesibles de verdad con TalkBack, Switch Access, Voice Access y para baja visión.

Se comparte por si a alguien le sirve para sus propias apps.

## Qué incluye

- `android-accesible/SKILL.md`: las reglas del agente (semántica para TalkBack, foco que vuelve, diálogos, deslizadores, Material 3, borde a borde, 48 dp, contraste, fuente al 200 %…) y su método de trabajo (auditar, implementar o diseñar desde cero).
- `android-accesible/referencias/`: guías detalladas (semántica, listas y acciones, diálogos y foco, deslizadores, Material 3 y calidad, baja visión, pruebas y auditoría).
- `android-accesible/plantillas/`: código Kotlin listo para copiar (`MemoriaFoco`, `Espera`, `Utilidades`, `AccionesTalkBack`, `DeslizadorAjuste`, `DeslizadorPosicion`, `MenuLateral`), sus cadenas en español e inglés y una prueba instrumentada que recorre la app con TalkBack y pasa el Accessibility Test Framework.
- `android-accesible/compartido/perfil-desarrollador.md`: las convenciones comunes del autor (todo en español, ningún error perdido, no inventar resultados…).

Rescate Accesible usa sus plantillas en `app/src/main/java/com/xebolax/rescate/accesibilidad/` y su prueba en `app/src/androidTest/`.

## Cómo usarlo

Copia la carpeta `android-accesible` a la carpeta de *skills* de Claude Code:

- Windows: `%USERPROFILE%\.claude\skills\android-accesible`
- macOS y Linux: `~/.claude/skills/android-accesible`

Después pídele a Claude Code, por ejemplo, «usa el agente de Android accesible para revisar esta pantalla» o «crea una pantalla de ajustes accesible con Material 3».

Las plantillas también se pueden usar sin el agente: copia los archivos de `plantillas/app/src/` a tu proyecto y cambia el paquete `com.ejemplo.app` por el tuyo.
