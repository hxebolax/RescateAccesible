# Política de privacidad y declaración de uso de la API de accesibilidad

## Política de privacidad (Rescate Accesible)

- **Qué datos trata**: ninguno personal. La app lee el estado de accesibilidad del móvil (qué servicios están activados, qué motor de voz se usa, volúmenes) para diagnosticar y reparar. Durante un rescate, el lector de rescate puede leer en voz alta el contenido de la pantalla.
- **Qué guarda**: solo su configuración, las copias de TalkBack y de la voz que el usuario pida, las grabaciones de sus propios mensajes y un registro técnico de fallos (sin texto de la pantalla). Todo en el propio móvil.
- **Qué envía**: nada. Solo se conecta a Internet para buscar actualizaciones en el servidor que figura en Ajustes (por defecto `https://xebolax.synology.me/RescateAccesible`), y para vincular la depuración inalámbrica dentro del propio móvil. No usa analíticas, publicidad ni servicios de Google.
- **Compartir el registro** de fallos lo decide el usuario con el botón «Compartir el registro de fallos».
- **Borrar todo**: desinstalar la app borra todos sus datos.

## Declaración de uso de la API de accesibilidad

Rescate Accesible es una **herramienta de accesibilidad** (`android:isAccessibilityTool="true"`) para personas ciegas y con baja visión. Usa la API de accesibilidad para:

1. **Abrir el menú de rescate con el atajo de accesibilidad** (servicio del atajo), que se apaga solo al terminar.
2. **Leer la pantalla con su propia voz** cuando el lector de pantalla del usuario no habla (modo eco) o no hay ninguno (modo propio).
3. **Recibir las teclas de volumen** solo durante un rescate o cuando no hay lector que hable, para manejar el menú y confirmar acciones.
4. **Subir el volumen de accesibilidad** si está demasiado bajo para oírse.
5. **Guiar por los Ajustes del sistema** pulsando, a petición expresa del usuario y con su confirmación, los controles necesarios para reactivar su lector, cambiar el motor de voz o reinstalar componentes de accesibilidad.
6. **Vigilar (guardián, opcional)** si el lector de pantalla o la voz del sistema dejan de funcionar, para avisar al usuario.

No recoge, guarda ni transmite el contenido de la pantalla ni las teclas; no muestra superposiciones engañosas; no cambia nada sin que el usuario lo pida.

## Permisos

| Permiso | Para qué | Cómo se quita |
|---|---|---|
| Servicio de accesibilidad | Lo de arriba | Ajustes > Accesibilidad |
| WRITE_SECURE_SETTINGS (opcional) | Activar y desactivar lectores y cambiar el motor de voz al momento | Desinstalar o `adb shell pm revoke` |
| REQUEST_INSTALL_PACKAGES | Reinstalar copias de TalkBack y de la voz, y actualizarse | Ajustes > Instalar apps desconocidas |
| POST_NOTIFICATIONS (opcional) | Avisos del guardián | Ajustes de notificaciones |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Pedir que Android no cierre la app | Ajustes de batería |
| INTERNET, estado de red, multidifusión wifi | Actualizaciones y depuración inalámbrica | No es un permiso que se pueda quitar |
| VIBRATE | Vibraciones del menú | No es un permiso que se pueda quitar |
