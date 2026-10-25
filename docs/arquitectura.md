# Arquitectura y flujos de rescate

## Visión general

Rescate Accesible es una sola app Android (Kotlin, minSdk 24, targetSdk 35) con código nativo para las voces. Se organiza en paquetes con responsabilidades separadas:

| Paquete | Qué hace |
|---|---|
| `voz` | Voces de rescate y salida de audio: Pico, eSpeak NG, grabaciones, pitidos, motores del sistema y la cadena `VozEmergencia` |
| `servicio` | Servicios de accesibilidad: el del atajo, el guardián y el lector de rescate |
| `rescate` | El menú hablado, sus páginas, la sesión, el mosaico y los avisos |
| `diagnostico` | Diagnóstico, reglas puras, prueba de motores TTS, estado del sonido |
| `guia` | Guía automática por los Ajustes del sistema |
| `reparar` | Copias de APK, reinstalación, tiendas |
| `sistema` | Servicios de accesibilidad y ajustes seguros (de Shortcut Menu), configuración buena |
| `permiso` | Concesión del permiso avanzado |
| `tts` | La voz de rescate como motor TTS del sistema |
| `actualizaciones` | Actualizador desde el servidor propio |
| `ui` | Pantallas de preparación, ajustes, manual, créditos, ayudante |
| `nucleo` | Preferencias, idioma, registro técnico, almacenamiento protegido |

## Componentes de Android

- `ServicioAtajo` (AccessibilityService): asignado al atajo de las teclas de volumen. El atajo lo enciende y abre el menú; al terminar se apaga con `disableSelf()`. Sin `flagRequestAccessibilityButton`, porque Android quita del atajo a un servicio con ese indicador cada vez que se apaga (AOSP `removeShortcutTargetForUnboundServiceLocked`). Es también el lector de rescate y el que guía por los Ajustes.
- `ServicioGuardian` (AccessibilityService, opcional): queda activado; vigila los servicios activados, la exploración táctil y la pantalla encendida.
- `ActividadRescate`: el menú, sobre la pantalla de bloqueo y con arranque directo.
- `ServicioTtsPropio` (TextToSpeechService): Pico como motor del sistema. TalkBack escucha `tts_default_synth` y cambia de motor al momento.
- `MosaicoRescate` (TileService), acceso directo dinámico, notificación del guardián.
- `ActividadPrincipal`, `ActividadAsistente`, `ActividadDiagnostico`, `ActividadAjustes`, `ActividadCopias`, `ActividadPermiso`, `ActividadAyudante`, `ActividadManual`, `ActividadCreditos`.

## La voz de rescate (`VozEmergencia`)

Orden de respaldo, bajando de escalón cuando uno falla:

1. Motor del sistema elegido como voz de rescate (opcional; se da por malo si no empieza a hablar en 5 s o da error).
2. Pico (es-ES, en-US, en-GB), 16 kHz.
3. eSpeak NG (es, en, fr, de, it, pt), 22 050 Hz.
4. Grabaciones de los textos fijos: primero las hechas en el móvil, luego las incluidas en el APK; Pico y eSpeak, español e inglés; µ-law 16 kHz.
5. Pitidos generados y vibraciones (la posición de una opción se cuenta con vibraciones).

Una única `ColaAudio` en su propio hilo serializa todo el sonido: cada frase en su pista; un contador de «generación» corta lo que suena sin carreras. La salida usa el uso de audio de accesibilidad, pide foco transitorio, sube el volumen al mínimo elegido y puede forzar el altavoz si hay Bluetooth.

## El lector de rescate

- Apagado: solo habla el menú.
- Eco: hay otro lector activo y mudo; se dicen los eventos de foco de accesibilidad, ventanas, anuncios y clics de casillas. Sin exploración táctil propia.
- Propio: pide la exploración táctil y responde a gestos (siguiente, anterior, desplazar, atrás, inicio, notificaciones, recientes, leer todo).

Protecciones: no repite el mismo texto en 700 ms, máximo 6 anuncios por segundo, corta al tocar, nunca guarda ni registra el texto.

## Flujos de rescate

### TalkBack mudo por un motor de voz roto
Atajo → menú (con TalkBack activo: lista; con el lector mudo se usa con las teclas) → Voz del sistema → «Usar la voz de rescate como voz del sistema» → con permiso avanzado se escribe `tts_default_synth` y se comprueba leyéndolo; sin él, la guía abre Ajustes de TTS y elige el motor → TalkBack vuelve a hablar.

### Sin lector
Atajo → el menú habla solo (arrastrar y levantar) → Lectores → TalkBack → Activar → con permiso, se escribe la lista de servicios y se cierra el rescate; sin permiso, la guía abre Accesibilidad, entra en TalkBack, confirma con volumen arriba, pulsa el interruptor y «Permitir».

### Modo seguro
Tres veces volumen arriba → se comprueba que Pico habla → se apagan los lectores elegidos → el menú habla solo → si se sale sin lector o pasan los segundos de espera sin tocar nada, se devuelven los lectores apagados.

### Guardián
Pantalla encendida → prueba silenciosa del motor del sistema (como mucho cada 30 min) → si falla: aviso con la voz de rescate, modo eco y escucha de las teclas → tres veces volumen arriba abre el rescate. Si el lector se apaga o se cae: aviso y escucha de las teclas.

### Reinstalación
Copia (base y divididos con SHA-256) → PackageInstaller con todos los APK → Android pide confirmar → la guía lee el diálogo y pulsa Instalar tras confirmar con volumen arriba → se dice el resultado.

## Almacenamiento y privacidad

- Preferencias, voces copiadas, grabaciones y registro en el almacenamiento protegido del dispositivo (arranque directo).
- Copias de APK en el almacenamiento interno sin copia de seguridad.
- Nada leído de otras apps se guarda ni se registra.

## Compilación

Gradle 8.11.1 y AGP 8.7.3, Kotlin 2.3.21, NDK 27.2, CMake 3.22.1. CMake propio sin descargas: `rescatepico` (Pico + JNI), `rescateespeak` (eSpeak NG + ucd-tools + speech-player como biblioteca aparte). Páginas de 16 KB. Las cuatro arquitecturas.
