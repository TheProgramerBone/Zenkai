#!/usr/bin/env python3
"""Genera el ícono "Técnicas" del hub de MasterScreen dentro de
textures/gui/icons.png (256x256, grid de 20px, mismo atlas que las pestañas — ver
ZenkaiTab/TabIconButton/AtlasIconButton).

Fila v=80 (primera fila completamente libre del atlas, confirmada por inspección directa de
píxeles antes de escribir aquí — ver CLAUDE.md, filas 0/20/40/60 ya están ocupadas):
    (0,80) = "Técnicas" — MasterScreen.ICON_TECHNIQUES_U/V, botón grande del hub que lleva a
             la lista de KiTechniqueType/PhysicalTechnique que el maestro enseña.

El botón "Skills" del mismo hub NO necesita ícono nuevo: reutiliza el ya existente de
ZenkaiTab.SKILLS (u=160, v=0) tal cual, mismo concepto visual que la pestaña de habilidades
del menú del jugador.

ESTILO: pintado con sombreado suave (aro con highlight), MISMO lenguaje que
gen_party_icons.py (fila v=60) — supersample 8x + reduce con LANCZOS, nada de esto puesto a
mano píxel a píxel. Esto es DISTINTO del bisel plano de 2 tonos de technique_icons.png (ver
gen_technique_icons.py/gen_spirit_bomb_icon.py): icons.png sigue su propia familia visual.

Ejecutar con: python tools/gen_master_icons.py
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


def _canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def _down(img):
    return img.resize((CELL, CELL), Image.LANCZOS)


def _highlight(img, cx, cy, r, alpha=80):
    """Brillo suave arriba-izquierda — mismo truco que gen_party_icons.py, para que la
    celda nueva no desentone con el resto de la fila v=60."""
    hl = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, alpha))
    hl = hl.filter(ImageFilter.GaussianBlur(r * 0.6))
    return Image.alpha_composite(img, hl)


def _badge(fill, fill_shadow, outline, glyph):
    """Círculo con sombra inferior + contorno + highlight arriba-izq, y GLYPH pintado encima.
    Copiado de gen_party_icons.py (misma familia visual, self-contained a propósito: cada
    script del atlas es independiente, ver la nota de gen_technique_icons.py)."""
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


def gen_techniques():
    """Rayo de energía sobre fondo rojo-naranja de combate: representa ki + física a la vez
    sin tener que dibujar dos glifos distintos, y se distingue a simple vista del ícono de
    Skills reutilizado (que no es de este color en ningún sitio del atlas)."""
    def glyph(d, cx, cy, r, outline):
        white = (250, 245, 235, 255)
        pts = [
            (cx + r * 0.10, cy - r * 0.55),
            (cx - r * 0.35, cy + r * 0.05),
            (cx - r * 0.05, cy + r * 0.05),
            (cx - r * 0.20, cy + r * 0.55),
            (cx + r * 0.40, cy - r * 0.10),
            (cx + r * 0.05, cy - r * 0.10),
        ]
        d.polygon(pts, fill=white, outline=outline)
    return _badge((224, 96, 48, 255), (176, 62, 28, 255), (64, 24, 10, 255), glyph)


def main():
    im = Image.open(ICONS_PATH).convert("RGBA")
    im.paste(gen_techniques(), (0, 80))
    im.save(ICONS_PATH)
    print("Updated icons.png row v=80 ->", ICONS_PATH)


if __name__ == "__main__":
    main()
