#!/usr/bin/env python3
"""Genera el fondo del diálogo de un maestro (MasterScreen) en
src/main/resources/assets/zenkai/textures/gui/master_screen.png.

Reemplaza el marco de tres anillos + rellenos que MasterScreen dibujaba a mano con g.fill():
UN solo master_screen.png, compartido por TODOS los maestros (Kami, Kaio, Korin y los que
añada el datapack) — lo que distingue a cada uno sigue siendo su retrato 3D animado, no el
fondo. Mismo lenguaje visual que el popup lateral de la ficha (StatsScreen, 3 anillos IN/
MID/OUT) pero opaco, porque este diálogo flota solo sobre el mundo sin ningún panel opaco
al lado — ver el comentario "PALETA UNIFICADA" en MasterScreen.java.

Pixel art de bordes DUROS a propósito (sin degradados): es la regla del mod, ver
gen_ki_fx.py. El brillo de esquina (BORDER_HI) es un bloque sólido, no un glow radial.

Este script es la ÚNICA fuente de esta textura — no editar el PNG a mano.
Ejecutar con: python tools/gen_master_screen.py

Los colores y medidas están DUPLICADOS a propósito desde ZenkaiPalette.java y
MasterScreen.java (BG_W/BG_H/PORTRAIT_W/PADDING): un script de build no puede leer bytecode
Java, así que si cambian esas constantes hay que regenerar la textura a mano y volver a
ejecutar este script. Mantenlos en sync.
"""

import os

from PIL import Image

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui",
)

# ── Geometría (== MasterScreen.java) ──────────────────────────────────────────
BG_W = 360
BG_H = 210
PORTRAIT_W = 116
PADDING = 10
BORDER_W = 3  # 1px BORDER_IN + 1px BORDER_MID + 1px BORDER_OUT, de fuera hacia dentro

# ── Colores (== ZenkaiPalette.java) ───────────────────────────────────────────
BORDER_IN = 0xFFAC421B
BORDER_MID = 0xFFF06500
BORDER_OUT = 0xFFF1D839
BORDER_HI = 0xFFFDF099
DIALOG_BG = 0xFF1E1410
DIALOG_PANEL = 0xFF241A12


def argb(v):
    """0xAARRGGBB -> (r, g, b, a) para PIL."""
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


def draw_outline(px, x0, y0, w, h, color):
    c = argb(color)
    x1, y1 = x0 + w - 1, y0 + h - 1
    for x in range(x0, x1 + 1):
        px[x, y0] = c
        px[x, y1] = c
    for y in range(y0, y1 + 1):
        px[x0, y] = c
        px[x1, y] = c


def gen():
    img = Image.new("RGBA", (BG_W, BG_H))
    px = img.load()

    fill_rect(px, 0, 0, BG_W, BG_H, DIALOG_BG)

    for inset, color in enumerate((BORDER_IN, BORDER_MID, BORDER_OUT)):
        draw_ring(px, inset, color)

    # Brillo de esquina: bloque sólido de BORDER_W x BORDER_W, no un degradado.
    for cx, cy in ((0, 0), (BG_W - BORDER_W, 0),
                   (0, BG_H - BORDER_W), (BG_W - BORDER_W, BG_H - BORDER_W)):
        fill_rect(px, cx, cy, BORDER_W, BORDER_W, BORDER_HI)

    # Panel recesado del retrato (izquierda), con su propio contorno 1px para leerse
    # como "hundido" respecto al fondo del diálogo — igual que un slot de inventario.
    px0, py0 = PADDING // 2, PADDING // 2
    pw, ph = PORTRAIT_W - px0, BG_H - PADDING
    fill_rect(px, px0, py0, pw, ph, DIALOG_PANEL)
    draw_outline(px, px0, py0, pw, ph, BORDER_IN)

    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    out_path = os.path.join(OUT_DIR, "master_screen.png")
    gen().save(out_path)
    print(f"Escrito {out_path}")


if __name__ == "__main__":
    main()
