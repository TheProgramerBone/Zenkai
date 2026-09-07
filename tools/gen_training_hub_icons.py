#!/usr/bin/env python3
"""Genera los 3 íconos propios de las filas del hub de Training (TrainingHubScreen) dentro de
textures/gui/icons.png (256x256, grid de 20px, mismo atlas que las pestañas — ver
ZenkaiTab/TabIconButton/AtlasIconButton).

Hasta ahora esas 3 filas reusaban íconos de OTRO concepto visual (ki-charge/turbo/kaioken del
HUD de estado, filas (0,40)/(20,40)/(40,40) — ver icon_hud_ki_charge/turbo/kaioken en
ZenkaiUiCredits) como placeholder, y Shadow y Meditation acabaron compartiendo literalmente el
mismo glifo (ver el comentario de TrainingHubScreen.java antes de este script). Se descartó
regenerar los 9 íconos de pestaña "antiguos" del atlas (decisión explícita del usuario, quedan
como están) — esto SOLO añade 3 celdas nuevas, no toca ninguna existente.

Fila v=120 (primera fila COMPLETAMENTE libre del atlas, confirmada por inspección directa de
píxeles antes de escribir aquí, igual que hicieron gen_master_icons.py/gen_appearance_icons.py
para v=80 en su momento):
    (0,120)  = "Train with your shadow" — TrainingHubScreen.ICON_SHADOW_U/V. Puño con un
               "afterimage" traslúcido detrás (combatir contra tu propio doble).
    (20,120) = "Meditation" — TrainingHubScreen.ICON_MEDITATION_U/V. Silueta sentada en
               posición de loto con líneas de calma/enfoque radiando.
    (40,120) = "Ki Target Practice" — TrainingHubScreen.ICON_TARGET_PRACTICE_U/V. Retícula de
               diana con un punto de ki en el centro (reflejos, no combate).

ESTILO: pintado con sombreado suave (aro con highlight), MISMO lenguaje que gen_master_icons.py/
gen_party_icons.py: supersample 8x + reduce con LANCZOS, nada puesto a mano píxel a píxel. Cada
script del atlas es independiente a propósito (ver la nota de gen_technique_icons.py) — el
helper `_badge` está duplicado de gen_master_icons.py, no importado.

Ejecutar con: python tools/gen_training_hub_icons.py
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


def _fighter(d, cx, cy, r, color):
    """Silueta en postura de combate (cabeza + torso inclinado + brazo/puño adelantado) — misma
    fórmula de "cabeza + cuerpo con polígono" que gen_meditation() (probada legible a 20px),
    inclinada hacia delante en vez de sentada."""
    d.ellipse([cx - r * 0.15, cy - r * 0.58, cx + r * 0.15, cy - r * 0.28], fill=color)
    d.polygon([
        (cx - r * 0.08, cy - r * 0.26),
        (cx - r * 0.32, cy + r * 0.50),
        (cx + r * 0.28, cy + r * 0.50),
        (cx + r * 0.20, cy - r * 0.08),
    ], fill=color)
    d.polygon([
        (cx + r * 0.10, cy - r * 0.14),
        (cx + r * 0.58, cy - r * 0.22),
        (cx + r * 0.58, cy - r * 0.02),
        (cx + r * 0.16, cy + r * 0.06),
    ], fill=color)


def gen_shadow():
    """Silueta en guardia con un afterimage traslúcido offset detrás — combate contra tu propio
    doble, NO el naranja de combate normal (Técnicas ya usa ese tono) para que se lea como "algo
    tuyo, duplicado" en vez de "otro tipo de ataque"."""
    def glyph(d, cx, cy, r, outline):
        _fighter(d, cx - r * 0.16, cy + r * 0.10, r, (255, 255, 255, 65))  # afterimage, detrás
        _fighter(d, cx, cy, r, (245, 240, 250, 255))                       # real, encima
    return _badge((72, 64, 96, 255), (48, 42, 68, 255), (20, 16, 28, 255), glyph)


def gen_meditation():
    """Silueta sentada en loto + líneas de calma radiando — enfoque/quietud, tono azul-verde
    frío para distinguirse tanto del violeta de Shadow como del rojo de Target Practice."""
    def glyph(d, cx, cy, r, outline):
        white = (250, 245, 235, 255)
        # Cabeza.
        d.ellipse([cx - r * 0.16, cy - r * 0.55, cx + r * 0.16, cy - r * 0.23], fill=white)
        # Torso/piernas cruzadas (loto): un triángulo ancho en la base.
        d.polygon([
            (cx, cy - r * 0.20),
            (cx - r * 0.48, cy + r * 0.42),
            (cx + r * 0.48, cy + r * 0.42),
        ], fill=white)
        # Líneas de calma radiando desde la cabeza.
        for ang in (-50, 0, 50):
            rad = math.radians(ang - 90)
            x0 = cx + math.cos(rad) * r * 0.32
            y0 = cy - r * 0.55 + math.sin(rad) * r * 0.32
            x1 = cx + math.cos(rad) * r * 0.58
            y1 = cy - r * 0.55 + math.sin(rad) * r * 0.58
            d.line([(x0, y0), (x1, y1)], fill=white, width=max(1, int(r * 0.06)))
    return _badge((64, 128, 152, 255), (40, 96, 116, 255), (16, 44, 54, 255), glyph)


def gen_target_practice():
    """Retícula de diana con un punto de ki en el centro — reflejos/puntería, mismo rojo que
    ERROR/las bombas del propio minijuego para que la fila ya adelante el tono del contenido."""
    def glyph(d, cx, cy, r, outline):
        white = (250, 245, 235, 255)
        d.ellipse([cx - r * 0.50, cy - r * 0.50, cx + r * 0.50, cy + r * 0.50],
                  outline=white, width=max(1, int(r * 0.09)))
        d.ellipse([cx - r * 0.24, cy - r * 0.24, cx + r * 0.24, cy + r * 0.24],
                  outline=white, width=max(1, int(r * 0.07)))
        d.ellipse([cx - r * 0.08, cy - r * 0.08, cx + r * 0.08, cy + r * 0.08], fill=white)
        # Marcas de mira (arriba/abajo/izq/der), fuera del aro grande.
        for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            x0 = cx + dx * r * 0.58
            y0 = cy + dy * r * 0.58
            x1 = cx + dx * r * 0.72
            y1 = cy + dy * r * 0.72
            d.line([(x0, y0), (x1, y1)], fill=white, width=max(1, int(r * 0.08)))
    return _badge((176, 64, 48, 255), (128, 40, 28, 255), (48, 16, 10, 255), glyph)


def main():
    im = Image.open(ICONS_PATH).convert("RGBA")
    im.paste(gen_shadow(), (0, 120))
    im.paste(gen_meditation(), (20, 120))
    im.paste(gen_target_practice(), (40, 120))
    im.save(ICONS_PATH)
    print("Updated icons.png row v=120 ->", ICONS_PATH)


if __name__ == "__main__":
    main()
