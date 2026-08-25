#!/usr/bin/env python3
"""Genera el icono de EXPLOSION (celda 8, la última) dentro de
textures/gui/technique_icons.png (180x40, rejilla de 20px, 2 filas).

Atlas existente, layout == com.hmc.zenkai.client.TechniqueIcons.java:
    fila 0 (y=0):  silueta gris por tipo -> TEÑIDA en cliente con el color de la técnica.
    fila 1 (y=20): detalle blanco puro, SIN teñir, dibujado encima.
    celda = ordinal() de KiTechniqueType (ver ese enum: "ordinal() = celda del ícono en
    technique_icons.png"). EXPLOSION es ordinal 8, la última columna (u=160..180) — hasta
    ahora vacía en las dos filas. Identidad visual == com.hmc.zenkai.feature.technique.
    KiTechniqueType ("no viaja; es la mecha antes de detonar... inestabilidad inminente") y
    com.hmc.zenkai.client.render_and_model_entities.ki.KiVisual (EXPLOSION: hervor alto,
    envolvente ancha) — de ahí la estela irregular en vez de un anillo limpio.

SOLO TOCA LA CELDA 8. Las otras ocho (WAVE..DISK) ya existen en el PNG y este script las deja
intactas: abre el atlas existente y hace paste() únicamente en su columna, igual que
gen_party_icons.py hace con icons.png.

ESTILO: bisel plano de 2 tonos, bordes DUROS, sin antialiasing — comprobado por muestreo
directo de los píxeles ya existentes en este atlas (alpha binario 0/255; 2-4 grises planos
por icono, p.ej. BLAST/BIG_BLAST = exactamente (223,223,223)/(188,188,188)). Esto es DISTINTO
del atlas textures/gui/icons.png (pintado con sombreado suave + antialiasing a 8x supersample,
ver .claude/skills/add-gui-texture-generator/references/icon-atlas.md) — technique_icons.png
sigue la regla de bordes duros por defecto del resto del mod, no la excepción de icons.png. No
usar supersample+LANCZOS aquí: reintroduce alfa parcial y desentona con el resto de la fila.

Ejecutar con: python tools/gen_technique_icons.py
"""

import math
import os

from PIL import Image, ImageDraw

ATLAS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui", "technique_icons.png",
)

CELL = 20
EXPLOSION_CELL = 8  # == KiTechniqueType.EXPLOSION.ordinal() (NO reordenar sin tocar el enum)

# Paleta == la ya usada en este atlas (BLAST/BIG_BLAST), para que la celda nueva no desentone.
LIGHT = (223, 223, 223, 255)
MID = (188, 188, 188, 255)
WHITE = (255, 255, 255, 255)


def _draw_row0(draw):
    """Estela de 8 puntas irregulares (no una estrella perfecta: la mitad de las puntas es un
    punto más corta que la otra, para leerse como inestable en vez de decorativa) con un
    núcleo redondo más claro encima — el mismo lenguaje de "borde oscuro / centro claro" que
    BARRIER, pero aquí el borde ES la silueta entera en vez de un anillo."""
    cx, cy = 10.0, 10.0
    spikes = 8
    pts = []
    for i in range(spikes * 2):
        ang = math.pi * i / spikes
        if i % 2 == 0:
            r = 9.0 if (i // 2) % 2 == 0 else 7.5
        else:
            r = 4.0
        pts.append((cx + r * math.sin(ang), cy - r * math.cos(ang)))
    draw.polygon(pts, fill=MID)
    draw.ellipse((cx - 4, cy - 4, cx + 4, cy + 4), fill=LIGHT)


def _draw_row1(draw):
    """Chispazo central de 4 puntas: mismo lenguaje que el brillo suelto de BARRIER (un trazo
    blanco sin teñir), aquí en forma de destello para leerse como "a punto de detonar"."""
    cx, cy = 10, 10
    draw.line((cx - 3, cy, cx + 3, cy), fill=WHITE, width=1)
    draw.line((cx, cy - 3, cx, cy + 3), fill=WHITE, width=1)
    for dx, dy in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        draw.point((cx + dx, cy + dy), fill=WHITE)


def main():
    atlas = Image.open(ATLAS_PATH).convert("RGBA")  # ABRE el atlas existente, no lo recrea

    row0 = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    _draw_row0(ImageDraw.Draw(row0))
    atlas.paste(row0, (EXPLOSION_CELL * CELL, 0), row0)

    row1 = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    _draw_row1(ImageDraw.Draw(row1))
    atlas.paste(row1, (EXPLOSION_CELL * CELL, CELL), row1)

    atlas.save(ATLAS_PATH)
    print(f"Wrote EXPLOSION icon (cell {EXPLOSION_CELL}) into {ATLAS_PATH}")


if __name__ == "__main__":
    main()
