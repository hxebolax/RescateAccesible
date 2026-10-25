# -*- coding: utf-8 -*-
"""
Rescate Accesible - Recorrido de una pantalla con TalkBack de verdad (SOLO en el emulador).

Mueve el foco de TalkBack como lo haría una persona, con el teclado físico del
emulador (Alt + flecha derecha: siguiente elemento), o con deslizamientos reales
en la pantalla táctil, y junta en una imagen lo que tiene el foco en cada paso
(el recuadro verde de TalkBack). Sirve para comprobar que una pantalla se recorre
entera, de principio a fin, y en qué orden.

Por qué así: `adb shell input` no pasa por TalkBack, y `uiautomator dump` mueve el
foco de accesibilidad. Aquí los eventos se escriben en los dispositivos de
entrada del kernel (`sendevent`), que TalkBack recibe como si fueran de verdad.
Necesita `adb root` (imagen del emulador con Google APIs).

Uso (con el emulador abierto, TalkBack activo y la pantalla ya abierta):
    python herramientas/recorrido_talkback.py 40                  avanza 40 veces con el teclado
    python herramientas/recorrido_talkback.py 40 dedo             con deslizamientos a la derecha
    python herramientas/recorrido_talkback.py 40 tecla salida.png imagen con el recorrido

Se para solo si el foco no se mueve en tres pasos seguidos. Nunca en un móvil real:
cambia lo que hace el lector de pantalla de quien lo usa.
"""
import io
import os
import subprocess
import sys
import time

from PIL import Image, ImageDraw

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = os.path.join(RAIZ, "entorno", "android-sdk", "platform-tools", "adb.exe")
TECLADO = "/dev/input/event1"   # «AT Translated Set 2 keyboard» del emulador
TACTIL = "/dev/input/event2"    # «virtio_input_multi_touch_1»
ANCHO, ALTO = 1080, 2400


def adb(*argumentos, binario=False):
	r = subprocess.run([ADB, *argumentos], capture_output=True)
	return r.stdout if binario else r.stdout.decode("utf-8", "replace")


def comprobar_emulador():
	serie = os.environ.get("ANDROID_SERIAL") or adb("get-serialno").strip()
	if not serie.startswith("emulator-"):
		sys.exit("[ERROR] Solo en el emulador: esto maneja TalkBack como una persona.")
	adb("root")
	adb("wait-for-device")


def tecla(codigo):
	"""Alt + tecla en el teclado físico: el atajo de TalkBack."""
	adb("shell", f"D={TECLADO}; sendevent $D 1 56 1; sendevent $D 0 0 0; sendevent $D 1 {codigo} 1; sendevent $D 0 0 0; "
		f"sendevent $D 1 {codigo} 0; sendevent $D 0 0 0; sendevent $D 1 56 0; sendevent $D 0 0 0")


def deslizar(x1, y1, x2, y2, pasos=8):
	"""Un dedo que se desliza de verdad por la pantalla táctil."""
	def punto(x, y):
		return f"sendevent $D 3 53 {x * 32767 // ANCHO}; sendevent $D 3 54 {y * 32767 // ALTO}; "
	orden = f"D={TACTIL}; sendevent $D 3 57 7; " + punto(x1, y1) + "sendevent $D 1 330 1; sendevent $D 0 0 0; "
	for i in range(1, pasos + 1):
		orden += punto(x1 + (x2 - x1) * i // pasos, y1 + (y2 - y1) * i // pasos) + "sendevent $D 0 0 0; "
	orden += "sendevent $D 3 57 4294967295; sendevent $D 1 330 0; sendevent $D 0 0 0"
	adb("shell", orden)


def siguiente(modo):
	if modo == "dedo":
		deslizar(300, 1200, 800, 1220)
	elif modo == "atras":
		tecla(105)
	else:
		tecla(106)


def foco(imagen):
	"""Rectángulo del recuadro verde de TalkBack (claro u oscuro), o None."""
	w, h = imagen.size
	peq = imagen.resize((w // 4, h // 4), Image.NEAREST)
	px = peq.load()
	xs, ys = [], []
	for y in range(peq.size[1]):
		for x in range(peq.size[0]):
			r, g, b = px[x, y]
			if g > 110 and g - r > 60 and g - b > 60:
				xs.append(x)
				ys.append(y)
	if not xs:
		return None
	return (min(xs) * 4, min(ys) * 4, max(xs) * 4 + 4, max(ys) * 4 + 4)


def main():
	pasos = int(sys.argv[1]) if len(sys.argv) > 1 else 40
	modo = sys.argv[2] if len(sys.argv) > 2 else "tecla"
	salida = sys.argv[3] if len(sys.argv) > 3 else os.path.join(RAIZ, "recorrido_talkback.png")
	comprobar_emulador()
	filas = []
	anterior, quieto = None, 0
	for i in range(1, pasos + 1):
		siguiente(modo)
		time.sleep(1.0)
		imagen = Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", binario=True))).convert("RGB")
		rect = foco(imagen)
		if rect:
			recorte = imagen.crop((0, max(0, rect[1] - 4), imagen.size[0], min(imagen.size[1], rect[3] + 4)))
			recorte = recorte.crop((0, 0, recorte.size[0], min(recorte.size[1], 300)))
		else:
			recorte = Image.new("RGB", (imagen.size[0], 60), "white")
		fila = Image.new("RGB", (imagen.size[0] + 100, recorte.size[1] + 8), "white")
		fila.paste(recorte, (100, 4))
		ImageDraw.Draw(fila).text((10, 10), str(i), fill="black", font_size=40)
		filas.append(fila)
		print(i, rect if rect else "(sin foco visible)", flush=True)
		quieto = quieto + 1 if rect == anterior else 0
		anterior = rect
		if quieto >= 3:
			print("El foco no se mueve: fin del recorrido.")
			break
	alto = sum(f.size[1] for f in filas)
	montaje = Image.new("RGB", (filas[0].size[0], alto), "white")
	y = 0
	for f in filas:
		montaje.paste(f, (0, y))
		y += f.size[1]
	montaje.resize((montaje.size[0] // 2, alto // 2)).save(salida)
	print("[OK] Recorrido en", salida)


if __name__ == "__main__":
	main()
