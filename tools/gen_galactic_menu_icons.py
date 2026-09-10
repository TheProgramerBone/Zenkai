#!/usr/bin/env python3
"""Genera los iconos de destino del menú galáctico de la SpacePod
(textures/gui/icons_galactic_menu.png, 256x256, grid de 20px) — un atlas PROPIO, separado de
icons_instant_transmision.png: este es un sistema TOTALMENTE distinto (SpacePodDestination, sin
descubrimiento ni protectorKey, ver .claude/pendiente/nave-espacial-menu-galactico.md), así que
no comparte arte ni convención de columnas con el de Instant Transmission aunque las dos
pantallas se vean parecidas a propósito.

Fila v=0, 3 celdas:
    (0,0)  = Tierra   — SpacePodDestination.EARTH
    (20,0) = Namek     — SpacePodDestination.NAMEK
    (40,0) = Yardrat   — placeholder "próximamente" (sin SpacePodDestination real detrás, ver el
             pendiente); un candado en vez de un planeta normal para que se lea "bloqueado" sin
             necesitar tooltip para entenderlo de un vistazo.

Mismo estilo que gen_instant_transmission_icons.py (planetas hermanos: esta es la MISMA Tierra
que ya existe en ese atlas en (0,0), solo que redibujada aquí para que este atlas sea
autocontenido y no dependa de abrir un archivo ajeno): bisel plano de bordes duros, sin
antialiasing ni sombreado suave. Supersample x4 + reducción NEAREST (no LANCZOS) para conservar
el filo duro en los círculos.

Este script CREA el atlas desde cero cada vez que se ejecuta (a diferencia de
gen_instant_transmission_icons.py, que abre uno ya existente y le suma filas) — este archivo es
nuevo y solo lo escribe este script, así que no hay nada previo que preservar.

Ejecutar con: python tools/gen_galactic_menu_icons.py
"""

import os
from PIL import Image, ImageDraw

ICONS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui",
    "icons_galactic_menu.png",
)

ATLAS = 256
SS = 4            # supersample (bajo a propósito: bordes duros, no antialiasing)
CELL = 20
SIZE = CELL * SS


def _canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def _down(img):
    return img.resize((CELL, CELL), Image.NEAREST)


def _circle(d, cx, cy, r, fill, outline=None, width=1):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=outline, width=width * SS)


def icon_earth():
    img = _canvas()
    d = ImageDraw.Draw(img)
    cx, cy, r = SIZE // 2, SIZE // 2, SIZE // 2 - 2 * SS
    _circle(d, cx, cy, r, (0x3A, 0x7B, 0xD9, 255), outline=(0x1E, 0x45, 0x7A, 255))
    d.polygon([(cx - r * 0.5, cy - r * 0.3), (cx - r * 0.1, cy - r * 0.6),
               (cx + r * 0.2, cy - r * 0.2), (cx - r * 0.1, cy + r * 0.1)],
              fill=(0x4C, 0xA3, 0x3A, 255))
    d.polygon([(cx + r * 0.1, cy + r * 0.2), (cx + r * 0.5, cy + r * 0.1),
               (cx + r * 0.4, cy + r * 0.55), (cx + r * 0.05, cy + r * 0.5)],
              fill=(0x4C, 0xA3, 0x3A, 255))
    return _down(img)


def icon_namek():
    img = _canvas()
    d = ImageDraw.Draw(img)
    cx, cy, r = SIZE // 2, SIZE // 2, SIZE // 2 - 2 * SS
    # Namek: mar verde en vez de azul, tierra en un verde más oscuro — mismo lenguaje que
    # icon_earth (un globo + un par de "continentes" planos), paleta invertida a propósito.
    _circle(d, cx, cy, r, (0x3E, 0x9B, 0x5C, 255), outline=(0x1C, 0x4A, 0x2C, 255))
    d.polygon([(cx - r * 0.5, cy - r * 0.3), (cx - r * 0.1, cy - r * 0.6),
               (cx + r * 0.2, cy - r * 0.2), (cx - r * 0.1, cy + r * 0.1)],
              fill=(0x22, 0x5C, 0x30, 255))
    d.polygon([(cx + r * 0.1, cy + r * 0.2), (cx + r * 0.5, cy + r * 0.1),
               (cx + r * 0.4, cy + r * 0.55), (cx + r * 0.05, cy + r * 0.5)],
              fill=(0x22, 0x5C, 0x30, 255))
    return _down(img)


def icon_yardrat_locked():
    img = _canvas()
    d = ImageDraw.Draw(img)
    cx, cy, r = SIZE // 2, SIZE // 2, SIZE // 2 - 2 * SS
    # Planeta atenuado (gris, sin continentes) + candado encima — "próximamente", igual de
    # reconocible sin tooltip que icon_unknown_dimension lo es para "sin descubrir todavía".
    _circle(d, cx, cy, r, (0x3A, 0x3E, 0x46, 255), outline=(0x20, 0x22, 0x28, 255))
    body_w, body_h = r * 0.9, r * 0.7
    bx0, by0 = cx - body_w / 2, cy - body_h * 0.15
    d.rectangle([bx0, by0, bx0 + body_w, by0 + body_h], fill=(0x8C, 0xEC, 0xFF, 255),
                outline=(0x1E, 0x45, 0x7A, 255), width=1 * SS)
    shackle_r = body_w * 0.32
    d.arc([cx - shackle_r, by0 - shackle_r * 1.5, cx + shackle_r, by0 + shackle_r * 0.5],
          start=180, end=360, fill=(0x8C, 0xEC, 0xFF, 255), width=int(1.6 * SS))
    d.ellipse([cx - 1.4 * SS, by0 + body_h * 0.32, cx + 1.4 * SS, by0 + body_h * 0.32 + 2.8 * SS],
              fill=(0x1E, 0x45, 0x7A, 255))
    return _down(img)


CELLS = [
    (0, 0, icon_earth),
    (20, 0, icon_namek),
    (40, 0, icon_yardrat_locked),
]


def main():
    atlas = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    for x, y, fn in CELLS:
        atlas.paste(fn(), (x, y))
    os.makedirs(os.path.dirname(ICONS_PATH), exist_ok=True)
    atlas.save(ICONS_PATH)
    print(f"Wrote row v=0 ({len(CELLS)} icons) into {ICONS_PATH}")


if __name__ == "__main__":
    main()
