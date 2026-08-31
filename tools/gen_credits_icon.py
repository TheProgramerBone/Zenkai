#!/usr/bin/env python3
"""Genera el ícono de la pestaña "Credits" dentro de
textures/gui/icons.png (256x256, grid de 20px, mismo atlas que las pestañas — ver
ZenkaiTab/TabIconButton).

Fila v=80 (0/20/40/60/80/100 ya ocupadas o reservadas — ver gen_master_icons.py y
gen_appearance_icons.py; confirmado libre por muestreo directo de píxeles antes de escribir):
    (120,80) = "Credits" — ZenkaiTab.CREDITS, pestaña del menú que abre CreditsScreen
               (client/gui/screens/CreditsScreen.java).

ESTILO: pintado con sombreado suave (aro con highlight), MISMO lenguaje que
gen_master_icons.py/gen_party_icons.py — supersample 8x + reduce con LANCZOS, nada puesto a
mano píxel a píxel. Glifo de UNA sola pieza (estrella), a propósito: CLAUDE.md documenta que un
glifo compuesto de varias formas pequeñas (círculo+flecha, círculo+cruz) no se lee bien a 20px —
una estrella sola sí es una silueta única, mismo criterio que ya funcionó para el busto de
"Head" o la gota de "Body & Colors".

Ejecutar con: python tools/gen_credits_icon.py
"""

import math
import os

from PIL import Image, ImageDraw, ImageFilter

ICONS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui", "icons.png",
)

SS = 8            # supersample
CELL = 20         # tamaño de celda en el atlas (y en pantalla)
SIZE = CELL * SS


def _canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def _down(img):
    return img.resize((CELL, CELL), Image.LANCZOS)


def _highlight(img, cx, cy, r, alpha=80):
    """Brillo suave arriba-izquierda — mismo truco que gen_party_icons.py/gen_master_icons.py."""
    hl = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, alpha))
    hl = hl.filter(ImageFilter.GaussianBlur(r * 0.6))
    return Image.alpha_composite(img, hl)


def _badge(fill, fill_shadow, outline, glyph):
    """Círculo con sombra inferior + contorno + highlight arriba-izq, y GLYPH pintado encima.
    Copiado de gen_master_icons.py (misma familia visual, self-contained a propósito: cada
    script del atlas es independiente)."""
    img = _canvas()
    cx, cy, r = SIZE / 2, SIZE / 2, SIZE * 0.42
    d = ImageDraw.Draw(img)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=outline, width=int(SIZE * 0.05))

    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse([cx - r, cy - r * 0.2, cx + r, cy + r], fill=fill_shadow)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    shadow.putalpha(Image.composite(shadow.split()[3], Image.new("L", img.size, 0), mask))
    img = Image.alpha_composite(img, shadow)

    ImageDraw.Draw(img).ellipse([cx - r, cy - r, cx + r, cy + r], outline=outline, width=int(SIZE * 0.05))
    glyph(ImageDraw.Draw(img), cx, cy, r, outline)
    img = _highlight(img, cx - r * 0.35, cy - r * 0.45, r * 0.5)
    return _down(img)


def _star_points(cx, cy, r_outer, r_inner, points=5, rotation=-90):
    """Vértices de una estrella de `points` puntas, alternando radio externo/interno."""
    pts = []
    step = 360 / (points * 2)
    for i in range(points * 2):
        angle = math.radians(rotation + i * step)
        radius = r_outer if i % 2 == 0 else r_inner
        pts.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return pts


def gen_credits():
    """Estrella dorada: reconocimiento/mérito, lenguaje universal de "crédito" que no compite
    en color con Técnicas (naranja-rojo) ni Servicios (verde) de la misma fila. Dorado cálido,
    mismo tono de familia que ScreenTitle.COLOR usa para los títulos de pantalla."""
    def glyph(d, cx, cy, r, outline):
        white = (250, 245, 235, 255)
        pts = _star_points(cx, cy, r * 0.55, r * 0.24)
        d.polygon(pts, fill=white, outline=outline)
    return _badge((230, 176, 56, 255), (180, 132, 32, 255), (70, 48, 12, 255), glyph)


def main():
    im = Image.open(ICONS_PATH).convert("RGBA")
    im.paste(gen_credits(), (120, 80))
    im.save(ICONS_PATH)
    print("Updated icons.png row v=80 (u=120) ->", ICONS_PATH)


if __name__ == "__main__":
    main()
