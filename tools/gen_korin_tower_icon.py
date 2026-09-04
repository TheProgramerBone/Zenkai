#!/usr/bin/env python3
"""Genera SOLO el icono de Korin's Tower dentro de textures/gui/icons_instant_transmision.png
(fila v=0, columna 9 — x=180 — el mismo hueco que el docstring de
gen_instant_transmission_icons.py ya marcaba como "SIN USO... libre para un futuro ícono nuevo").

Por qué este script vive SEPARADO de gen_instant_transmission_icons.py en vez de añadir una
función más a su lista CELLS: esa lista regenera las 12 celdas CADA VEZ que se ejecuta, y 9 de
esas 12 ya no coinciden con lo que el script produce — el usuario las retocó a mano directamente
en el PNG (ver el aviso al principio de ese archivo). Volver a ejecutar ese script para añadir
UNA celda nueva se llevaría por delante todo ese arte retocado. Este script en cambio ABRE el
atlas existente y pega SOLO en su propia celda (columna 9), exactamente igual de seguro de
re-ejecutar que el resto de celdas hand-maintained — nunca toca ninguna otra.

Diseño: un poste delgado (el tronco de bambú de la torre de Korin) + un disco plano arriba (la
plataforma donde vive Korin) — un solo glifo dominante con una segunda pieza mínima pegada
encima, mismo criterio que ya usa icon_kami_palace (base+cuerpo+remate en un polígono) en el
script hermano. Paleta verde/caña, deliberadamente distinta del blanco/dorado de Kami's Palace
para que las dos torres no se confundan en la lista de destinos del Overworld.

Ejecutar con: python tools/gen_korin_tower_icon.py
"""

import os
from PIL import Image, ImageDraw

ICONS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui",
    "icons_instant_transmision.png",
)

SS = 4  # supersample (bajo a propósito: bordes duros, sin antialiasing, mismo criterio que el hermano)
CELL = 20
SIZE = CELL * SS
COL, ROW = 9, 0  # x=180, y=0 — ver docstring


def icon_korin_tower():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    m = 2 * SS
    pole_w = SIZE * 0.16
    pole_x0 = SIZE / 2 - pole_w / 2
    pole_x1 = SIZE / 2 + pole_w / 2
    pole_top = SIZE * 0.22
    pole_bottom = SIZE - m
    # Poste único, mismo tono de contorno oscuro que el resto de la fila.
    d.rectangle([pole_x0, pole_top, pole_x1, pole_bottom],
                fill=(0x6E, 0x8A, 0x3A, 255), outline=(0x38, 0x4A, 0x18, 255), width=1 * SS)
    # Disco/plataforma en la punta — segunda pieza mínima, misma paleta.
    disc_w = SIZE * 0.62
    disc_h = SIZE * 0.16
    disc_y = pole_top - disc_h * 0.35
    d.ellipse([SIZE / 2 - disc_w / 2, disc_y - disc_h / 2, SIZE / 2 + disc_w / 2, disc_y + disc_h / 2],
              fill=(0x8A, 0xB4, 0x4A, 255), outline=(0x38, 0x4A, 0x18, 255), width=1 * SS)
    return img.resize((CELL, CELL), Image.NEAREST)


def main():
    atlas = Image.open(ICONS_PATH).convert("RGBA")  # ABRE el atlas existente, no lo recrea
    atlas.paste(icon_korin_tower(), (COL * CELL, ROW * CELL))
    atlas.save(ICONS_PATH)
    print(f"Wrote cell ({COL},{ROW}) [Korin's Tower] into {ICONS_PATH} — no other cell touched")


if __name__ == "__main__":
    main()
