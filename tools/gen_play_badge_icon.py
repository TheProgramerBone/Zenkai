#!/usr/bin/env python3
"""Añade el icono de "reproducir sonido" (círculo teal + triángulo blanco, pedido explícito del
usuario con una imagen de referencia) a textures/gui/icons.png (256x256, grid de 20px), celda
(60,120) — la primera libre a continuación de las 3 de gen_training_hub_icons.py en esa misma
fila (confirmada libre por inspección directa de píxeles antes de escribir aquí, mismo criterio
que usan los demás scripts del atlas).

Sustituye al intento anterior (`tools/gen_play_icon.py`, `btn_play.png`/`btn_play_highlight.png`
en la familia de bisel plano de btn_x/btn_pencil) — el usuario pidió explícitamente un badge
redondo con sombreado suave en su lugar, estilo icons.png, no la familia de iconos de fila.
Ese script y esos dos PNG se borraron; `PlayIconButton` ahora blitea esta celda escalada a 12px
de destino (ver TechniqueIcons.blit para el mismo truco de blit con tamaño de destino != tamaño
de origen — el badge se dibuja a resolución nativa de 20px y se reduce al vuelo en pantalla, sin
perder nitidez porque el downscale del propio juego ya hace ese trabajo).

ESTILO: mismo `_badge` (círculo con sombra inferior + contorno + highlight arriba-izquierda) que
gen_training_hub_icons.py/gen_master_icons.py/gen_party_icons.py — supersample 8x + LANCZOS,
nada puesto a mano píxel a píxel. Helper duplicado a propósito (ver la nota de
gen_technique_icons.py sobre por qué cada script del atlas es independiente).

Ejecutar con: python tools/gen_play_badge_icon.py
"""

import os

from PIL import Image, ImageDraw, ImageFilter

ICONS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui", "icons.png",
)

SS = 8            # supersample
CELL = 20         # tamaño de celda en el atlas (y en pantalla)
SIZE = CELL * SS

U, V = 60, 120    # celda libre elegida para este icono


def _canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def _down(img):
    return img.resize((CELL, CELL), Image.LANCZOS)


def _highlight(img, cx, cy, r, alpha=80):
    hl = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, alpha))
    hl = hl.filter(ImageFilter.GaussianBlur(r * 0.6))
    return Image.alpha_composite(img, hl)


def _badge(fill, fill_shadow, outline, glyph):
    """Círculo con sombra inferior + contorno + highlight arriba-izq, y GLYPH pintado encima."""
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


def gen_play():
    """Triángulo "play" blanco, centrado ligeramente a la derecha (compensación óptica clásica
    del glifo: un triángulo geométricamente centrado se lee "corrido a la izquierda")."""
    def glyph(d, cx, cy, r, outline):
        white = (255, 255, 255, 255)
        d.polygon([
            (cx - r * 0.30, cy - r * 0.44),
            (cx - r * 0.30, cy + r * 0.44),
            (cx + r * 0.42, cy),
        ], fill=white)
    return _badge((44, 191, 164, 255), (30, 140, 120, 255), (13, 58, 50, 255), glyph)


def main():
    im = Image.open(ICONS_PATH).convert("RGBA")
    im.paste(gen_play(), (U, V))
    im.save(ICONS_PATH)
    print(f"Updated icons.png cell ({U},{V}) ->", ICONS_PATH)


if __name__ == "__main__":
    main()
