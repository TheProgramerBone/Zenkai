#!/usr/bin/env python3
"""Genera el fondo del diálogo de un maestro (MasterScreen) en
src/main/resources/assets/zenkai/textures/gui/master_screen.png.

REDISEÑO: antes esto era un relleno de un solo tono (DIALOG_BG) — deuda visual, no una
decisión (ver el historial de este archivo). Ahora comparte el mismo lenguaje ESTRUCTURAL que
common_screen.png (inspeccionado a nivel de píxel antes de escribir esto): marco de tres
anillos + una banda ligeramente más clara hacia el centro del área de contenido + una marca de
agua muy sutil, todo en bloques de un tono plano sobre otro — SIN degradado suave, coherente
con la regla de bordes duros del mod (ver gen_ki_fx.py). La diferencia es la PALETA:
VIOLETA/CIRUELA (ZenkaiPalette.MASTER_*) en vez del naranja/dorado de common_screen/BORDER_* —
un diálogo con un NPC se lee distinto de un panel propio del jugador de un vistazo, sin
depender del retrato 3D para notarlo.

Un primer intento usó azul/cian y resultó ser casi el mismo tono que ZenkaiTechPalette (CYAN/
SCREEN_BG): el diálogo de un maestro se leía como un panel de máquina (scouter bench), no como
un personaje sabio. El violeta/ciruela de aquí abajo reutiliza el tono que ZenkaiPalette ya
asocia a "forma y maestría" (SPECIAL_ON_PANEL/SECTION_FORM) en vez de chocar con la temática
tecnológica — ver el comentario completo en ZenkaiPalette.java.

Pixel art de bordes DUROS a propósito (sin degradados): es la regla del mod, ver gen_ki_fx.py.
El brillo de esquina (MASTER_BORDER_HI) es un bloque sólido, no un glow radial.

Este script es la ÚNICA fuente de esta textura — no editar el PNG a mano.
Ejecutar con: python tools/gen_master_screen.py

Los colores y medidas están DUPLICADOS a propósito desde ZenkaiPalette.java y
MasterScreen.java (BG_W/BG_H/PORTRAIT_W/PADDING): un script de build no puede leer bytecode
Java, así que si cambian esas constantes hay que regenerar la textura a mano y volver a
ejecutar este script. Mantenlos en sync.
"""

import os

from PIL import Image, ImageDraw

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

# ── Colores (== ZenkaiPalette.java, bloque "Paleta fría del diálogo de maestro") ─────────────
BORDER_IN = 0xFF3A1B4A
BORDER_MID = 0xFF6B3A78   # == ZenkaiPalette.SPECIAL_ON_PANEL, mismo "ciruela"
BORDER_OUT = 0xFFAF7FC4
BORDER_HI = 0xFFE8D0F0
DIALOG_BG = 0xFF160F1A
DIALOG_PANEL = 0xFF1F1723

# Marca de agua: un paso más clara que DIALOG_PANEL, MISMA magnitud de contraste que el
# "悟" de common_screen contra su BEIGE (~10-15 por canal). Solo se usa aquí — nada en Java
# dibuja esta marca, así que no hace falta un token en ZenkaiPalette para ella.
WATERMARK = 0xFF2B2130


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

    # Banda del área de contenido (donde viven el hub/las listas): un paso más clara que el
    # fondo, con bordes duros — mismo principio que el BEIGE_DARK de common_screen, aplicado
    # aquí como una banda rectangular en vez de un sombreado difuso. Deja el margen de los
    # anillos intacto (BORDER_W) y no toca el panel del retrato.
    cx0 = PORTRAIT_W + BORDER_W
    cy0 = BORDER_W
    cw = BG_W - cx0 - BORDER_W
    ch = BG_H - BORDER_W * 2
    fill_rect(px, cx0, cy0, cw, ch, DIALOG_PANEL)

    # Marca de agua: un rombo doble muy sutil, centrado en la banda de contenido — mismo
    # espíritu que el "悟" de common_screen (una identidad discreta bajo el texto, nunca
    # compitiendo con él). Trazo de 2px, PIL sin antialiasing (bordes duros por defecto).
    draw = ImageDraw.Draw(img)
    ccx, ccy = cx0 + cw / 2, cy0 + ch / 2
    for scale in (0.62, 0.40):
        w, h = cw * scale * 0.5, ch * scale * 0.5
        pts = [(ccx, ccy - h), (ccx + w, ccy), (ccx, ccy + h), (ccx - w, ccy)]
        draw.line(pts + [pts[0]], fill=argb(WATERMARK), width=2)

    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    out_path = os.path.join(OUT_DIR, "master_screen.png")
    gen().save(out_path)
    print(f"Escrito {out_path}")


if __name__ == "__main__":
    main()
