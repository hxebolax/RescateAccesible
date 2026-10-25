# Informe de pruebas y simulaciones de averías

Rescate Accesible 1.0.0-pruebas. Simulación del 2026-10-25 13:08 en Google sdk_gphone64_x86_64, Android 15 (API 35), TalkBack 15.0.0.639625893.

- Escenarios superados: **15 de 15**.
- Comprobaciones correctas: **61 de 61**.

## S01. Ningún motor de voz del sistema funciona

Resultado: **Superado** (31 s).

Avería simulada: Se deshabilita la app Servicios de voz de Google (el motor predeterminado) y se avería el motor Pico de la app. TalkBack sigue activo pero no tiene ninguna voz: el usuario no oye nada.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Con un lector activo: el menú es una lista de botones.
3. (La avería simulada de Pico se retira aquí: solo quería demostrar que el rescate habla aunque fallen todas las demás voces.)
4. Pulsa volumen abajo 5 veces hasta oír «Reparar o reinstalar TalkBack y la voz» y mantiene pulsado volumen abajo para elegirla.
5. Pulsa volumen abajo 2 veces hasta oír «Reconocimiento y síntesis de voz de Google, versión googletts.google-speech-apk_20240319.00_p0.617183227, deshabilitada: no funciona hasta habilitarla en su información, sin copia» y mantiene pulsado volumen abajo para elegirla.
6. Pulsa volumen abajo 1 vez hasta oír «Habilitar Reconocimiento y síntesis de voz de Google» y mantiene pulsado volumen abajo para elegirla.
7. La guía abre la información de la app en Ajustes, dice lo que encuentra y pulsa «Habilitar» (habilitar no es irreversible: no pide confirmación) y vuelve al menú de rescate.
8. Pulsa Atrás 3 veces para volver al menú principal (Atrás vuelve a la página anterior).
9. Pulsa volumen abajo 3 veces hasta oír «Voz del sistema» y mantiene pulsado volumen abajo para elegirla.
10. Pulsa volumen abajo 1 vez hasta oír «Probar la voz del sistema: Reconocimiento y síntesis de voz de Google» y mantiene pulsado volumen abajo para elegirla.
11. Oye el resultado de la prueba y una frase dicha con la voz de Google.

Comprobaciones:

- Bien: Avería real: la voz del sistema no habla
- Bien: El menú de rescate se abre con el atajo
- Bien: El rescate habla con su segunda voz incluida (eSpeak NG), sin el motor del sistema ni Pico
- Bien: La app Servicios de voz de Google vuelve a estar habilitada
- Bien: El motor predeterminado sigue siendo la voz de Google (leído del ajuste del sistema)
- Bien: La voz del sistema vuelve a hablar (prueba real de síntesis en inglés (Estados Unidos))

## S02. Se eligió un motor de voz que falla y TalkBack se calla

Resultado: **Superado** (22 s).

Avería simulada: Se elige como motor predeterminado la voz de Google y después se deshabilita (como un motor estropeado o desinstalado a medias). TalkBack activo.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Con un lector activo: el menú es una lista de botones.
3. Pulsa volumen abajo 3 veces hasta oír «Voz del sistema» y mantiene pulsado volumen abajo para elegirla.
4. Pulsa volumen abajo 3 veces hasta oír «Usar la voz de rescate como voz del sistema» y mantiene pulsado volumen abajo para elegirla.

Comprobaciones:

- Bien: El menú de rescate se abre con el atajo
- Bien: El motor predeterminado es ahora la voz de rescate de la app (leído del ajuste del sistema)
- Bien: TalkBack vuelve a hablar: el sistema pidió 6 frases al motor de rescate

## S03. TalkBack desactivado (sin ningún lector)

Resultado: **Superado** (12 s).

Avería simulada: Se desactiva TalkBack. El móvil no tiene ningún lector de pantalla: no habla nada.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Sin lector activo: el menú habla solo con la voz de rescate.
3. Pone un dedo en la primera opción, lo desliza hacia abajo oyendo cada opción al pasar y lo levanta al oír «Lectores de pantalla».
4. Pone un dedo en la primera opción, lo desliza hacia abajo oyendo cada opción al pasar y lo levanta al oír «TalkBack, desactivado».
5. Pone un dedo en la primera opción, lo desliza hacia abajo oyendo cada opción al pasar y lo levanta al oír «Activar TalkBack».

Comprobaciones:

- Bien: El menú de rescate se abre con el atajo
- Bien: Sin lector, el menú habla solo y se maneja con el dedo
- Bien: TalkBack activado y en marcha
- Bien: El rescate se cierra solo para que hable TalkBack

## S04. TalkBack desactivado y sin permiso avanzado

Resultado: **Superado** (27 s).

Avería simulada: Como S03, pero la app no tiene el permiso avanzado: no puede activar TalkBack directamente y tiene que guiar por los Ajustes del sistema.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Sin lector activo: el menú habla solo con la voz de rescate.
3. Pulsa volumen abajo 2 veces hasta oír «Lectores de pantalla» y mantiene pulsado volumen abajo para elegirla.
4. Pulsa volumen abajo 1 vez hasta oír «TalkBack, desactivado» y mantiene pulsado volumen abajo para elegirla.
5. Pulsa volumen abajo 1 vez hasta oír «Activar TalkBack» y mantiene pulsado volumen abajo para elegirla.
6. La guía abre Ajustes de accesibilidad, lee lo que encuentra con la voz de rescate, entra en TalkBack y busca su interruptor.
7. Oye la pregunta de la guía (volumen arriba sí, volumen abajo no) y pulsa volumen arriba.

Comprobaciones:

- Bien: El menú de rescate se abre con el atajo
- Bien: TalkBack activado desde los Ajustes del sistema (la guía pulsó el interruptor y «Permitir»)
- Bien: TalkBack en marcha

## S05. Volumen a cero

Resultado: **Superado** (16 s).

Avería simulada: Se bajan a cero el volumen multimedia y el de accesibilidad: aunque todo funcione, no se oye nada.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Con un lector activo: el menú es una lista de botones.
3. Al abrirse, el rescate comprueba el volumen y lo sube solo hasta el mínimo elegido (60 %), y lo dice.
4. Pulsa volumen abajo 4 veces hasta oír «Sonido y volumen» y mantiene pulsado volumen abajo para elegirla.
5. Pulsa volumen abajo 3 veces hasta oír «Subir el volumen» y mantiene pulsado volumen abajo para elegirla.
6. Oye «Volumen al … por ciento».

Comprobaciones:

- Bien: Avería real: volumen multimedia al 0 %
- Bien: El menú de rescate se abre con el atajo
- Bien: Volumen multimedia subido (60 %)
- Bien: Volumen de accesibilidad subido (66 %)

## S06. Voz demasiado rápida y aguda

Resultado: **Superado** (14 s).

Avería simulada: Se pone la velocidad de la voz del sistema al 600 % y el tono al 50 %: TalkBack habla de forma ininteligible.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Con un lector activo: el menú es una lista de botones.
3. Pulsa volumen abajo 3 veces hasta oír «Voz del sistema» y mantiene pulsado volumen abajo para elegirla.
4. Pulsa volumen abajo 4 veces hasta oír «Poner la velocidad y el tono normales» y mantiene pulsado volumen abajo para elegirla.

Comprobaciones:

- Bien: El menú de rescate se abre con el atajo
- Bien: Velocidad de la voz del sistema al 100 %
- Bien: Tono de la voz del sistema al 100 %

## S07. Modo seguro: apagar el lector mudo y recuperarlo solo

Resultado: **Superado** (27 s).

Avería simulada: TalkBack está activo pero se supone mudo. El usuario necesita que el menú hable con su propia voz sin TalkBack, y que TalkBack vuelva solo si no hace nada.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Con un lector activo: el menú es una lista de botones.
3. Pulsa tres veces rápido volumen arriba (modo seguro de Shortcut Menu).
4. No toca nada durante 15 segundos (la espera del modo seguro).

Comprobaciones:

- Bien: El menú de rescate se abre con el atajo
- Bien: Modo seguro: TalkBack apagado
- Bien: El menú pasa a hablar solo con la voz de rescate
- Bien: Pasada la espera, TalkBack vuelve a activarse solo
- Bien: TalkBack en marcha y el rescate cerrado

## S08. Modo eco: TalkBack activo pero mudo, sin permiso avanzado

Resultado: **Superado** (6 s).

Avería simulada: Sin el permiso avanzado no se puede apagar TalkBack. El usuario quiere oír lo que TalkBack enfoca con la voz de rescate.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Con un lector activo: el menú es una lista de botones.
3. Pulsa tres veces rápido volumen arriba.

Comprobaciones:

- Bien: El menú de rescate se abre con el atajo
- Bien: Modo eco: la voz de rescate lee lo que enfoca TalkBack, sin tocar TalkBack
- Bien: TalkBack sigue activado
- Bien: Al salir del rescate se apaga el modo eco

## S09. Otro servicio de accesibilidad roba el control

Resultado: **Superado** (17 s).

Avería simulada: Se activa «Seleccionar para pronunciar» junto a TalkBack, un servicio que el usuario no quiere y que interfiere.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Con un lector activo: el menú es una lista de botones.
3. Pulsa volumen abajo 2 veces hasta oír «Lectores de pantalla» y mantiene pulsado volumen abajo para elegirla.
4. Pulsa volumen abajo 3 veces hasta oír «Otro servicio de accesibilidad activado» y mantiene pulsado volumen abajo para elegirla.
5. Pulsa volumen abajo 1 vez hasta oír «Desactivar Enunciar selección» y mantiene pulsado volumen abajo para elegirla.

Comprobaciones:

- Bien: El menú de rescate se abre con el atajo
- Bien: El servicio que molestaba queda desactivado
- Bien: TalkBack sigue activado

## S10. TalkBack cerrado a la fuerza (limpiador o ahorro de batería)

Resultado: **Superado** (21 s).

Avería simulada: Se fuerza la detención de TalkBack, como hacen algunos limpiadores y el ahorro de batería de muchos fabricantes.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Sin lector activo: el menú habla solo con la voz de rescate.
3. Pulsa volumen abajo 2 veces hasta oír «Lectores de pantalla» y mantiene pulsado volumen abajo para elegirla.
4. Pulsa volumen abajo 1 vez hasta oír «TalkBack, desactivado» y mantiene pulsado volumen abajo para elegirla.
5. Pulsa volumen abajo 1 vez hasta oír «Activar TalkBack» y mantiene pulsado volumen abajo para elegirla.

Comprobaciones:

- Bien: Avería real: TalkBack no está en marcha (Android además lo quita de los servicios activados)
- Bien: El menú de rescate se abre con el atajo
- Bien: TalkBack activado y en marcha otra vez

## S11. Restaurar la configuración buena guardada

Resultado: **Superado** (18 s).

Avería simulada: Con todo funcionando se guarda la configuración. Después se rompen varias cosas a la vez: TalkBack apagado, motor de voz cambiado y velocidad al 300 %.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Sin lector activo: el menú habla solo con la voz de rescate.
3. Pulsa volumen abajo 7 veces hasta oír «Restaurar mi configuración» y mantiene pulsado volumen abajo para elegirla.
4. Pulsa volumen abajo 2 veces hasta oír «Restaurar la configuración guardada» y mantiene pulsado volumen abajo para elegirla.
5. Pulsa volumen abajo 1 vez hasta oír «Restaurar la configuración guardada» y mantiene pulsado volumen abajo para elegirla.
6. Confirma «Restaurar la configuración guardada» en la página de confirmación.

Comprobaciones:

- Bien: Configuración buena guardada
- Bien: El menú de rescate se abre con el atajo
- Bien: TalkBack activado de nuevo
- Bien: Motor de voz de Google de nuevo
- Bien: Velocidad al 100 % de nuevo

## S12. Fallan las voces incluidas: grabaciones y pitidos

Resultado: **Superado** (11 s).

Avería simulada: Se averían a la vez Pico y eSpeak NG dentro de la app (bibliotecas dañadas o sin memoria).

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Con un lector activo: el menú es una lista de botones.
3. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
4. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Con un lector activo: el menú es una lista de botones.
5. Pulsa volumen abajo 2 veces hasta oír «Lectores de pantalla» y mantiene pulsado volumen abajo para elegirla.
6. Cada opción suena con su grabación y, además, vibra tantas veces como su número.

Comprobaciones:

- Bien: El menú de rescate se abre con el atajo
- Bien: Con Pico averiado el rescate pasa a eSpeak NG
- Bien: El menú de rescate se abre con el atajo
- Bien: Con las dos voces averiadas el rescate usa las grabaciones de respaldo
- Bien: Hay grabación de «Lectores de pantalla» en español (Pico o eSpeak) para decirla sin síntesis
- Bien: Hay grabación de «Con las teclas de volumen: vol…»
- Bien: El menú sigue funcionando sin ninguna síntesis

## S13. El guardián detecta que la voz del sistema se ha roto

Resultado: **Superado** (16 s).

Avería simulada: El guardián está activado. Se deshabilita la voz de Google y se avería el motor Pico: TalkBack queda mudo sin que el usuario haya hecho nada.

Lo que hizo el usuario y la app:

1. El usuario enciende la pantalla. El guardián comprueba en silencio la voz del sistema.
2. Oye el aviso del guardián y pulsa volumen arriba (o toca «Abrir el rescate» en la mitad de abajo de la pantalla).
3. Pantalla de bloqueo no activa al abrirse el rescate.
4. Pulsa volumen abajo 3 veces hasta oír «Voz del sistema» y mantiene pulsado volumen abajo para elegirla.
5. Pulsa volumen abajo 3 veces hasta oír «Usar la voz de rescate como voz del sistema» y mantiene pulsado volumen abajo para elegirla.

Comprobaciones:

- Bien: El guardián detecta la voz rota y activa el modo eco (la voz de rescate lee lo que enfoca TalkBack)
- Bien: El guardián muestra su aviso con «Abrir el rescate»
- Bien: Se abre el rescate (incluso sobre la pantalla de bloqueo)
- Bien: La voz de rescate es el motor del sistema

## S14. El guardián avisa de que TalkBack se ha apagado

Resultado: **Superado** (25 s).

Avería simulada: Con el guardián activado, TalkBack se apaga (por error, por un fallo o por otra app).

Lo que hizo el usuario y la app:

1. TalkBack se apaga. El guardián lo dice con la voz de rescate: «Atención: tu lector de pantalla se ha apagado…».
2. Oye el aviso del guardián y pulsa volumen arriba (o toca «Abrir el rescate» en la mitad de abajo de la pantalla).
3. Pulsa volumen abajo 2 veces hasta oír «Lectores de pantalla» y mantiene pulsado volumen abajo para elegirla.
4. Pulsa volumen abajo 1 vez hasta oír «TalkBack, desactivado» y mantiene pulsado volumen abajo para elegirla.
5. Pulsa volumen abajo 1 vez hasta oír «Activar TalkBack» y mantiene pulsado volumen abajo para elegirla.

Comprobaciones:

- Bien: Se abre el rescate desde el guardián
- Bien: TalkBack activado y en marcha
- Bien: El guardián sigue activado vigilando

## S15. Diagnóstico automático

Resultado: **Superado** (9 s).

Avería simulada: Se rompen varias cosas a la vez (velocidad extrema, volumen a cero, servicio extra activado) y se pide el diagnóstico.

Lo que hizo el usuario y la app:

1. Mantiene pulsadas a la vez las dos teclas de volumen unos 3 segundos (atajo de accesibilidad). [Simulado: se activa el servicio del atajo, que es lo que hace Android al detectarlo.]
2. Se abre el menú de rescate («Rescate Accesible», 12 opciones). Con un lector activo: el menú es una lista de botones.
3. Pulsa volumen abajo 1 vez hasta oír «Diagnosticar y reparar» y mantiene pulsado volumen abajo para elegirla.
4. Oye los hallazgos: Grave: Reconocimiento y síntesis de voz de Google no habla | Aviso: La velocidad o el tono de la voz están en valores extremos | Aviso: El atajo de volumen no abre Rescate Accesible | Aviso: Hay 2 cambios desde la configuración guardada | Información: Otros servicios activos: Enunciar selección | Información: El guardián está desactivado | Información: Optimización de batería activada | Información: No hay copia de Suite de Accesibilidad Android | Información: No hay copia de Reconocimiento y síntesis de voz de Google | Lo que funciona bien: una comprobación | Volver

Comprobaciones:

- Bien: El menú de rescate se abre con el atajo
- Bien: El diagnóstico termina y presenta sus hallazgos
- Bien: Detecta la velocidad de voz extrema
- Bien: Detecta el servicio que interfiere

