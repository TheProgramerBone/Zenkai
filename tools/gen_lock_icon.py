#!/usr/bin/env python3
"""Genera btn_lock.png y btn_lock_highlight.png (16x16) en textures/gui/.

PLACEHOLDER A PROPOSITO. Es el candado del boton de desbloqueo del editor de tecnicas de ki
(TechniqueEditScreen / LockIconButton), donde antes habia un PanelButton ancho con el texto
"Unlock (6999 TP - 89 MND)" que se salia de su propio marco. El arte de aqui esta pensado para
que el usuario lo repinte a mano cuando quiera: si eso pasa, ESTE SCRIPT DEJA DE SER LA FUENTE
DE VERDAD y hay que anotarlo en este mismo docstring antes de volver a ejecutarlo (ver la nota
de CLAUDE.md sobre gen_instant_transmission_icons.py, que sobreescribio 9 iconos hechos a mano
justo por saltarse esta comprobacion).

Familia visual: BISEL PLANO DE DOS TONOS -- btn_x.png, btn_trash.png, btn_pencil.png -- NO la
de textures/gui/icons.png (pintada con supersample+LANCZOS). Reglas, las mismas que ya
documenta gen_pencil_icon.py y que salieron de muestrear btn_x.png:

  - alfa BINARIO (0 o 255), bordes duros, cero antialiasing.
  - 5 colores planos, literalmente los de btn_x.png.
  - la variante _highlight sube el contorno a dorado y aclara el cuerpo; no es un tinte.

La luz viene de ARRIBA-IZQUIERDA en la familia entera: el filo dorado va en el borde izquierdo
del arco y del cuerpo, y la sombra en el derecho.

Los dos estados NO son el mismo dibujo con otra rampa: el normal es un candado CERRADO y el de
hover uno ABIERTO (el arco se levanta y gira a la derecha). El boton dice "esto se desbloquea",
asi que el hover ensena el resultado de pulsarlo. El estado inactivo no es un tercer PNG: lo
pinta LockIconButton oscureciendo el normal, igual que PlusIconButton.

Ejecutar con: python tools/gen_lock_icon.py
"""

import os

from PIL import Image

GUI_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui",
)

SIZE = 16

# O = contorno, H = filo dorado, h = filo palido, B = cara iluminada, b = cara en sombra.
# Cuerpo comun a los dos estados (filas 7..14): cajon con ojo de cerradura de 2x2 y espiga.
BODY = [
    ".OOOOOOOOOOOOOO.",
    ".OHhBBBBBBBBbbO.",
    ".OHhBBBBBBBBbbO.",
    ".OHhBBBOOBBBbbO.",
    ".OHhBBBOOBBBbbO.",
    ".OHhBBBBOBBBbbO.",
    ".OHhBBBBBBBBbbO.",
    ".OOOOOOOOOOOOOO.",
]

# Arco CERRADO: las dos patas bajan hasta el cuerpo. Pared de 2 px y hueco de 4: con la
# pared a 3 y el hueco a 2 (primer intento) el arco se leia como un bloque macizo pegado al
# cuerpo, no como un aro -- a 16 px el hueco tiene que ser MAS ancho que sus paredes.
SHACKLE_CLOSED = [
    "................",
    ".....OOOOOO.....",
    "....OH....bO....",
    "....OH....bO....",
    "....OH....bO....",
    "....OH....bO....",
    "....OH....bO....",
]

# Arco ABIERTO: la pata izquierda se ha soltado (termina en la fila 4) y el arco entero se
# desplaza una columna a la derecha. La pata derecha sigue clavada en el cuerpo.
SHACKLE_OPEN = [
    "................",
    "......OOOOOO....",
    ".....OH....bO...",
    ".....OH....bO...",
    ".....OO....bO...",
    "...........bO...",
    "...........bO...",
]

NORMAL = {
    "O": (204, 67, 4, 255),
    "H": (250, 221, 7, 255),
    "h": (252, 236, 97, 255),
    "B": (223, 197, 164, 255),
    "b": (208, 156, 119, 255),
}

HOVER = {
    "O": (250, 221, 7, 255),
    "H": (252, 236, 97, 255),
    "h": (252, 236, 97, 255),
    "B": (255, 255, 255, 255),
    "b": (223, 197, 164, 255),
}


def build(shackle, palette):
    art = shackle + BODY + ["................"]
    if len(art) != SIZE:
        raise ValueError("el dibujo tiene %d filas, deberia tener %d" % (len(art), SIZE))
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(art):
        if len(row) != SIZE:
            raise ValueError("fila %d mide %d, deberia medir %d" % (y, len(row), SIZE))
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = palette[ch]
    return img


def main():
    for name, shackle, palette in (
        ("btn_lock.png", SHACKLE_CLOSED, NORMAL),
        ("btn_lock_highlight.png", SHACKLE_OPEN, HOVER),
    ):
        path = os.path.join(GUI_DIR, name)
        build(shackle, palette).save(path)
        print("escrito %s" % path)


if __name__ == "__main__":
    main()
