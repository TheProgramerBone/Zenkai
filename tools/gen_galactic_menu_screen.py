#!/usr/bin/env python3
"""Genera el fondo del diálogo del menú galáctico de la SpacePod (GalacticMenuScreen) en
src/main/resources/assets/zenkai/textures/gui/galactic_menu.png.

Pedido explícito del usuario: "generala y hazla similar a la de instant transmission pero hazla
más tecnológica" — MISMA estructura de marco que instant_transmission_menu.png (anillos duros +
brillo de esquina + banda de contenido semitransparente, ver tools/gen_instant_transmission_menu.py,
que a su vez sigue el patrón de tools/gen_master_screen.py) y el MISMO tamaño de diálogo (210x180),
pero con la paleta e identidad TECNOLÓGICA del banco de scouter (ZenkaiTechPalette) en vez de la
cósmica índigo/violeta de Instant Transmission — es la consola de una nave, no un fenómeno de ki.
En vez de nebulosa+campo de estrellas (motivo orgánico/espacial), lleva un motivo de HUD/consola:
una rejilla de líneas finas tipo plano técnico, dos anillos de "radar" concéntricos, y marcas en
ángulo en las cuatro esquinas del área de contenido (viewfinder) — todo de bordes duros, SIN
degradados: a diferencia de la nebulosa de Instant Transmission (única excepción documentada a la
regla del mod, ver gen_ki_fx.py), aquí ni una sola línea necesita blur para leerse como
"tecnológico", así que esta textura NO pide excepción a la regla de bordes duros por defecto.

Este script es la ÚNICA fuente de esta textura — no editar el PNG a mano.
Ejecutar con: python tools/gen_galactic_menu_screen.py

Los colores y medidas están DUPLICADOS a propósito desde ZenkaiTechPalette.java y
GalacticMenuScreen.java (BG_W/BG_H/PADDING): un script de build no puede leer bytecode Java, así
que si cambian esas constantes hay que regenerar la textura a mano y volver a ejecutar este
script. Mantenlos en sync.
"""

import os

from PIL import Image, ImageDraw

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui",
)

# ── Geometría (== GalacticMenuScreen.java) ────────────────────────────────────
BG_W = 210
BG_H = 180
PADDING = 10
BORDER_W = 3  # 1px BORDER_IN + 1px BORDER_MID + 1px BORDER_OUT, de fuera hacia dentro

# ── Colores (== ZenkaiTechPalette.java) ───────────────────────────────────────
BORDER_IN = 0xFF0A0C10    # SCREEN_EDGE: anillo interior, casi negro
BORDER_MID = 0xFF536174   # STEEL_DARK: anillo medio, acero
BORDER_OUT = 0xFF56B0C8   # CYAN: anillo exterior, el acento de la familia tecnológica
BORDER_HI = 0xFF8CECFF    # CYAN_HI: brillo de esquina
DIALOG_BG = 0xFF12161E    # SCREEN_BG: pantalla hundida, base del diálogo entero
DIALOG_PANEL = 0xFF1A202C  # SCREEN_LINE: banda de contenido, un paso más clara

# Rejilla/radar: un único tono cian a baja alfa, nunca un segundo color — "instrumento", no "arte".
GRID_LINE = (0x56, 0xB0, 0xC8, 22)
RADAR_RING = (0x56, 0xB0, 0xC8, 40)
BRACKET = (0x8C, 0xEC, 0xFF, 200)

GRID_STEP = 14
BRACKET_LEN = 10
BRACKET_INSET = BORDER_W + 4


def argb(v):
    a = (v >> 24) & 0xFF
    r = (v >> 16) & 0xFF
    g = (v >> 8) & 0xFF
    b = v & 0xFF
    return (r, g, b, a)


def fill_rect(px, x0, y0, w, h, color):
    c = argb(color)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px[x, y] = c


def draw_ring(px, inset, color):
    """Marco de 1px a `inset` píxeles del borde de BG_W x BG_H."""
    c = argb(color)
    x0, y0 = inset, inset
    x1, y1 = BG_W - 1 - inset, BG_H - 1 - inset
    for x in range(x0, x1 + 1):
        px[x, y0] = c
        px[x, y1] = c
    for y in range(y0, y1 + 1):
        px[x0, y] = c
        px[x1, y] = c


def add_grid(img):
    """Rejilla de líneas finas tipo plano técnico — líneas de 1px, sin blur (ya son duras por
    definición). Se pinta en una capa aparte y se compone con alpha_composite, mismo motivo que
    add_stars de gen_instant_transmission_menu.py: dibujar directo sobre `img` con alfa parcial
    dejaría huecos de transparencia real en un fondo que tiene que quedar opaco."""
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    for x in range(BORDER_W, BG_W - BORDER_W, GRID_STEP):
        d.line([(x, BORDER_W), (x, BG_H - BORDER_W)], fill=GRID_LINE)
    for y in range(BORDER_W, BG_H - BORDER_W, GRID_STEP):
        d.line([(BORDER_W, y), (BG_W - BORDER_W, y)], fill=GRID_LINE)
    return Image.alpha_composite(img, overlay)


def add_radar_rings(img):
    """Dos anillos concéntricos huecos, centrados en el diálogo — motivo de "radar" de consola,
    bordes duros (contorno de 1px, sin relleno)."""
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    cx, cy = BG_W // 2, BG_H // 2 + 6
    for r in (46, 74):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=RADAR_RING, width=1)
    return Image.alpha_composite(img, overlay)


def add_viewfinder_brackets(img):
    """Cuatro marcas en L, una por esquina del área de contenido — lenguaje de mira/HUD, distinto
    del bloque sólido de esquina (que sigue existiendo, ver draw_ring/fill_rect más abajo)."""
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    i = BRACKET_INSET
    n = BRACKET_LEN
    corners = [
        ((i, i), (1, 1)),
        ((BG_W - 1 - i, i), (-1, 1)),
        ((i, BG_H - 1 - i), (1, -1)),
        ((BG_W - 1 - i, BG_H - 1 - i), (-1, -1)),
    ]
    for (x, y), (sx, sy) in corners:
        d.line([(x, y), (x + n * sx, y)], fill=BRACKET, width=1)
        d.line([(x, y), (x, y + n * sy)], fill=BRACKET, width=1)
    return Image.alpha_composite(img, overlay)


def gen():
    img = Image.new("RGBA", (BG_W, BG_H), argb(DIALOG_BG))
    img = add_grid(img)
    img = add_radar_rings(img)
    img = add_viewfinder_brackets(img)

    px = img.load()
    for inset, color in enumerate((BORDER_IN, BORDER_MID, BORDER_OUT)):
        draw_ring(px, inset, color)

    # Brillo de esquina: bloque sólido, no un degradado — mismo criterio que master_screen.png /
    # instant_transmission_menu.png.
    for cx, cy in ((0, 0), (BG_W - BORDER_W, 0),
                   (0, BG_H - BORDER_W), (BG_W - BORDER_W, BG_H - BORDER_W)):
        fill_rect(px, cx, cy, BORDER_W, BORDER_W, BORDER_HI)

    # Banda de contenido: un paso más clara que el fondo, bordes duros, deja el margen de los
    # anillos intacto — mismo principio que la banda de instant_transmission_menu.png.
    cx0 = cy0 = BORDER_W
    cw = BG_W - BORDER_W * 2
    ch = BG_H - BORDER_W * 2
    band = Image.new("RGBA", (cw, ch), argb(DIALOG_PANEL))
    band.putalpha(70)  # semitransparente: deja la rejilla/radar asomando debajo
    img.alpha_composite(band, (cx0, cy0))

    return img.convert("RGBA")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    out_path = os.path.join(OUT_DIR, "galactic_menu.png")
    gen().save(out_path)
    print(f"Escrito {out_path}")


if __name__ == "__main__":
    main()
