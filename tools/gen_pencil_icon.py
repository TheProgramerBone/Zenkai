#!/usr/bin/env python3
"""Genera btn_pencil.png y btn_pencil_highlight.png (12x12) en textures/gui/.

Es el icono de "editar" de la fila de tecnicas (TechniquesScreen): abre TechniqueEditScreen
para esa instancia de ki. Vive en la familia de BISEL PLANO DE DOS TONOS -- btn_x.png,
btn_trash.png, btn_arrow_left.png y companiia -- NO en la de textures/gui/icons.png (pintada
con sombreado suave y antialiasing a 8x supersample). Reglas de la familia, comprobadas por
muestreo directo de los pixeles de btn_x.png y btn_x_highlight.png antes de escribir esto:

  - alfa BINARIO (0 o 255), bordes duros, cero antialiasing. Nada de supersample+LANCZOS.
  - 5 colores planos como mucho, y son literalmente los mismos que ya usa btn_x:
        (204, 67,  4)  contorno
        (250, 221, 7)  filo dorado del lado iluminado (arriba-izquierda)
        (252, 236, 97) filo dorado palido
        (223, 197,164) cara iluminada
        (208, 156,119) cara en sombra
  - la variante _highlight sube el contorno a dorado y aclara el cuerpo, igual que
    btn_x_highlight hace con btn_x: no es un tinte uniforme, es otra rampa.

La luz viene de ARRIBA-IZQUIERDA en la familia entera, asi que el lapiz esta dibujado en
diagonal con el filo claro en su borde superior-izquierdo y la sombra en el inferior-derecho,
y la punta converge hacia abajo-izquierda.

Este script es la FUENTE DE VERDAD de los dos PNG que escribe: para cambiar el icono se edita
el mapa ART de aqui abajo y se vuelve a ejecutar, nunca se retoca el PNG a mano (ver la nota
de CLAUDE.md sobre gen_instant_transmission_icons.py y lo que pasa cuando esas dos cosas se
desincronizan).

Ejecutar con: python tools/gen_pencil_icon.py
"""

import os

from PIL import Image

GUI_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui",
)

SIZE = 12

# O = contorno, H = filo dorado, h = filo palido, B = cara iluminada, b = cara en sombra.
# El extremo plano (la goma) arriba a la derecha; la punta converge abajo a la izquierda.
# Las dos ultimas filas van MACIZAS de contorno a proposito: es el grafito. Sin ellas la
# silueta se lee como una barra diagonal cualquiera y no como un lapiz -- a 12 px la punta
# oscura es lo unico que distingue una cosa de la otra.
ART = [
    ".........OOO",
    "........OHHO",
    ".......OHhBO",
    "......OHhBbO",
    ".....OHhBbO.",
    "....OHhBbO..",
    "...OHhBbO...",
    "..OHhBbO....",
    ".OHhBbO.....",
    ".OHBbO......",
    ".OOOO.......",
    ".OOO........",
]

# Rampa normal: la misma que btn_x.png, color por color.
NORMAL = {
    "O": (204, 67, 4, 255),
    "H": (250, 221, 7, 255),
    "h": (252, 236, 97, 255),
    "B": (223, 197, 164, 255),
    "b": (208, 156, 119, 255),
}

# Rampa de hover: contorno dorado y cuerpo aclarado, mismo salto que btn_x -> btn_x_highlight
# (alli el contorno (204,67,4) pasa a (250,221,7) y la cara en sombra desaparece).
HOVER = {
    "O": (250, 221, 7, 255),
    "H": (252, 236, 97, 255),
    "h": (252, 236, 97, 255),
    "B": (255, 255, 255, 255),
    "b": (223, 197, 164, 255),
}


def build(palette):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(ART):
        if len(row) != SIZE:
            raise ValueError("fila %d de ART mide %d, deberia medir %d" % (y, len(row), SIZE))
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = palette[ch]
    return img


def main():
    if len(ART) != SIZE:
        raise ValueError("ART tiene %d filas, deberia tener %d" % (len(ART), SIZE))
    for name, palette in (("btn_pencil.png", NORMAL), ("btn_pencil_highlight.png", HOVER)):
        path = os.path.join(GUI_DIR, name)
        build(palette).save(path)
        print("escrito %s" % path)


if __name__ == "__main__":
    main()
