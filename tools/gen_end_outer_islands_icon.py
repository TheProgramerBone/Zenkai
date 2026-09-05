#!/usr/bin/env python3
"""Genera SOLO el icono de "Islas Exteriores" (submenú del End en Instant Transmission) dentro
de textures/gui/icons_instant_transmision.png — fila v=1 (y=20), columna 0 (x=0): la PRIMERA
celda de una fila nueva, vacía hasta ahora.

Por qué una fila nueva en vez de una celda libre de la fila v=0: la columna 10 de la fila v=0
("NetherPortal") está reservada a propósito para una futura estructura del PROPIO Nether (pedido
explícito del usuario — "el del nether portal por favor déjalo para el portal del nether de la
dimensión del nether, tengo pensado que haya una estructura futura"). Un intento anterior de esta
sesión reusó esa columna para Islas Exteriores por error; se revirtió y esta es la corrección:
un hueco NUEVO en la fila siguiente en vez de robarle su columna al Nether.

Mismo patrón que gen_korin_tower_icon.py (que documenta por qué vive separado de
gen_instant_transmission_icons.py): ABRE el atlas existente y pega SOLO en su propia celda, nunca
regenera las demás — 9 de las 12 celdas de la fila v=0 ya están retocadas a mano y no coinciden
con lo que el script hermano produce.

Diseño: un solo remolino/vórtice (el brillo de un End Gateway) sobre fondo casi negro — un solo
glifo dominante, mismo criterio que icon_nether/icon_end del script hermano. Paleta cian/turquesa
(el color real del haz de un End Gateway en el juego), deliberadamente distinta del morado de la
columna 10 (NetherPortal) para que las dos no se confundan.

Ejecutar con: python tools/gen_end_outer_islands_icon.py
"""

import os
from PIL import Image, ImageDraw

ICONS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui",
    "icons_instant_transmision.png",
)

SS = 4  # supersample (bajo a propósito: bordes duros, sin antialiasing, mismo criterio que el resto)
CELL = 20
SIZE = CELL * SS
COL, ROW = 0, 1  # x=0, y=20 — primera celda de una fila nueva, vacía


def icon_outer_islands():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy, r = SIZE // 2, SIZE // 2, SIZE // 2 - 2 * SS

    # Fondo: casi negro, mismo tono que el vacío del End (icon_end ya usa este tono de base).
    d.ellipse([cx - r, cy - r, cx + r, cy + r],
              fill=(0x0C, 0x0A, 0x14, 255), outline=(0x28, 0x22, 0x38, 255), width=1 * SS)

    # Remolino: brazos concéntricos decrecientes, un solo glifo (espiral) en vez de piezas
    # sueltas — mismo criterio que el resto del atlas (ver la lección de Gender en
    # AppearanceScreen sobre glifos compuestos a 20px).
    turns = 2.2
    steps = 40
    import math
    pts = []
    for i in range(steps + 1):
        t = i / steps
        ang = t * turns * 2 * math.pi
        rad = r * (0.92 - 0.78 * t)
        pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))
    d.line(pts, fill=(0x4A, 0xE8, 0xD8, 255), width=int(2.4 * SS), joint="curve")
    # Núcleo brillante en el centro del remolino.
    core_r = r * 0.16
    d.ellipse([cx - core_r, cy - core_r, cx + core_r, cy + core_r], fill=(0xC8, 0xFF, 0xF2, 255))

    return img.resize((CELL, CELL), Image.NEAREST)


def main():
    atlas = Image.open(ICONS_PATH).convert("RGBA")  # ABRE el atlas existente, no lo recrea
    atlas.paste(icon_outer_islands(), (COL * CELL, ROW * CELL))
    atlas.save(ICONS_PATH)
    print(f"Wrote cell ({COL},{ROW}) [Outer Islands] into {ICONS_PATH} — no other cell touched")


if __name__ == "__main__":
    main()
