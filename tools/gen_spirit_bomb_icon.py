#!/usr/bin/env python3
"""Genera el icono de SPIRIT_BOMB (celda 9, la primera fuera del atlas 180x40 original) dentro
de textures/gui/technique_icons.png — que este script AMPLÍA de 180x40 a 200x40 (9 -> 10
celdas de 20px) para tener sitio.

Atlas existente, layout == com.hmc.zenkai.client.TechniqueIcons.java:
    fila 0 (y=0):  silueta gris por tipo -> TEÑIDA en cliente con el color de la técnica.
    fila 1 (y=20): detalle blanco puro, SIN teñir, dibujado encima.
    celda = ordinal() de KiTechniqueType (ver ese enum: "ordinal() = celda del ícono en
    technique_icons.png"). SPIRIT_BOMB se añadió AL FINAL del enum a propósito (ordinal 9,
    ver su comentario) para no reordenar las 9 celdas ya existentes.

Identidad visual == KiTechniqueType.SPIRIT_BOMB ("Genki Dama: técnica firma de Kaio") y su
propio TechniqueDef (charge_ticks largo, tamaño/radio por encima de BIG_BLAST): una esfera de
energía reunida, no un rayo. Fila 0 es una esfera con un núcleo más claro descentrado (mismo
lenguaje que EXPLOSION: "núcleo redondo más claro" sobre una silueta oscura); fila 1 son
marcas CONVERGIENDO hacia el centro (energía siendo absorbida desde fuera), lo opuesto de la
estela DIVERGENTE de EXPLOSION (que irradia hacia fuera) — mismo vocabulario visual, sentido
contrario, para que las dos celdas no se confundan a tamaño de icono.

ESTILO: bisel plano de 2 tonos, bordes DUROS, sin antialiasing — MISMA paleta ya usada en este
atlas (LIGHT/MID de gen_technique_icons.py, BLAST/BIG_BLAST = (223,223,223)/(188,188,188)).
PIL ImageDraw sin supersample: sus primitivas no antialiasan por defecto, que es justo lo que
hace falta aquí. NO usar supersample+LANCZOS (esa es la familia de icons.png, ver
gen_master_icons.py) — desentonaría con el resto de la fila.

Ejecutar con: python tools/gen_spirit_bomb_icon.py
"""

import os

from PIL import Image, ImageDraw

ATLAS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui", "technique_icons.png",
)

CELL = 20
OLD_W = 180   # 9 celdas (WAVE..EXPLOSION)
NEW_W = 200   # 10 celdas (+ SPIRIT_BOMB)
ATLAS_H = 40
SPIRIT_BOMB_CELL = 9  # == KiTechniqueType.SPIRIT_BOMB.ordinal() (NO reordenar sin tocar el enum)

# Paleta == la ya usada en este atlas (BLAST/BIG_BLAST), para que la celda nueva no desentone.
LIGHT = (223, 223, 223, 255)
MID = (188, 188, 188, 255)
WHITE = (255, 255, 255, 255)


def _draw_row0(draw):
    """Esfera de energía reunida: cuerpo MID con un núcleo LIGHT descentrado hacia arriba-
    izquierda (mismo vocabulario que el núcleo claro de EXPLOSION, aquí sin las puntas de
    mecha porque esto no es una detonación inminente, es una bola ya formada)."""
    cx, cy = 10.0, 10.0
    draw.ellipse((cx - 8, cy - 8, cx + 8, cy + 8), fill=MID)
    draw.ellipse((cx - 4.5, cy - 5.5, cx + 2.5, cy + 1.5), fill=LIGHT)


def _draw_row1(draw):
    """Cuatro marcas CONVERGIENDO hacia el centro (energía siendo absorbida desde fuera) —
    lo opuesto de la estela divergente de EXPLOSION, mismo lenguaje en sentido contrario."""
    cx, cy = 10, 10
    for ang_len in ((-8, -8, -4, -4), (8, -8, 4, -4), (-8, 8, -4, 4), (8, 8, 4, 4)):
        x0, y0, x1, y1 = ang_len
        draw.line((cx + x0, cy + y0, cx + x1, cy + y1), fill=WHITE, width=1)


def main():
    old = Image.open(ATLAS_PATH).convert("RGBA")  # ABRE el atlas existente, no lo recrea
    if old.width < NEW_W:
        atlas = Image.new("RGBA", (NEW_W, ATLAS_H), (0, 0, 0, 0))
        atlas.paste(old, (0, 0))
    else:
        atlas = old  # ya se había ampliado antes; no lo vuelve a encoger

    row0 = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    _draw_row0(ImageDraw.Draw(row0))
    atlas.paste(row0, (SPIRIT_BOMB_CELL * CELL, 0), row0)

    row1 = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    _draw_row1(ImageDraw.Draw(row1))
    atlas.paste(row1, (SPIRIT_BOMB_CELL * CELL, CELL), row1)

    atlas.save(ATLAS_PATH)
    print(f"Wrote SPIRIT_BOMB icon (cell {SPIRIT_BOMB_CELL}) into {ATLAS_PATH}, atlas now {atlas.width}x{atlas.height}")


if __name__ == "__main__":
    main()
