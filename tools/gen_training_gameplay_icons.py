#!/usr/bin/env python3
"""Genera 3 sprites de GAMEPLAY (no de botón/hub) para los minijuegos de Training dentro de
textures/gui/icons.png (256x256, grid de 20px, mismo atlas que las pestañas — ver
ZenkaiTab/TabIconButton/AtlasIconButton).

Hasta ahora, en la fase PLAYING de Ki Target Practice y Meditation, orbes/bombas/notas eran
simples `GuiGraphics.fill()` de cuadrados de color plano (Pista B2/B3 del plan de pulido de
Training) — esto los reemplaza por sprites de verdad, pedido explícito del usuario.

A DIFERENCIA de gen_training_hub_icons.py/gen_master_icons.py, estos NO llevan el bisel de
"badge" circular (círculo+sombra+aro): son sprites sueltos que se dibujan flotando sobre el
mundo/las notas cayendo, no botones de menú — un aro de botón alrededor se vería como un ícono
de UI perdido en medio de la pantalla de juego. Cada uno deja el fondo transparente y usa su
propio halo/sombra suaves en vez del bisel compartido.

Fila v=120 (completamente libre en el momento de escribir esto — confirmado por inspección
directa de píxeles; NO es la misma fila que usaron los íconos del hub de Training, que ahora
viven en v=80 u=140/160/180, editados a mano por el usuario tras la Ronda 1 de este plan):
    (0,120)  = orbe de ki (TargetPracticeScreen, orbe bueno)
    (20,120) = calavera/bomba (TargetPracticeScreen, orbe malo)
    (40,120) = nota musical (MeditationScreen, nota que cae)

Ejecutar con: python tools/gen_training_gameplay_icons.py
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


def gen_orb():
    """Orbe de ki: núcleo brillante + halo suave hacia fuera, sin aro duro — tiene que leerse
    como "energía", no como un botón. Ámbar/dorado, mismo tono que ZenkaiPalette.VALUE que ya
    usaba el fill() plano que reemplaza, para que el color no cambie de golpe para el jugador."""
    img = _canvas()
    cx, cy = SIZE / 2, SIZE / 2
    core = (255, 214, 102, 255)
    mid = (255, 190, 60, 160)

    halo = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse(
        [cx - SIZE * 0.46, cy - SIZE * 0.46, cx + SIZE * 0.46, cy + SIZE * 0.46], fill=mid)
    halo = halo.filter(ImageFilter.GaussianBlur(SIZE * 0.06))
    img = Image.alpha_composite(img, halo)

    ImageDraw.Draw(img).ellipse(
        [cx - SIZE * 0.30, cy - SIZE * 0.30, cx + SIZE * 0.30, cy + SIZE * 0.30], fill=core)

    hl = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse(
        [cx - SIZE * 0.14 - SIZE * 0.10, cy - SIZE * 0.14 - SIZE * 0.12,
         cx - SIZE * 0.10 + SIZE * 0.10, cy - SIZE * 0.10 + SIZE * 0.12],
        fill=(255, 255, 255, 190))
    hl = hl.filter(ImageFilter.GaussianBlur(SIZE * 0.02))
    img = Image.alpha_composite(img, hl)
    return _down(img)


def gen_bomb():
    """Calavera simplificada: cráneo redondeado + 2 cuencas + nariz, en rojo oscuro (mismo tono
    que ZenkaiPalette.ERROR) — "peligro", no "botón". Las cuencas se recortan con fill totalmente
    transparente sobre el cráneo ya pintado, no se pintan negras (se verían como ojos, no como
    agujeros, sobre un fondo oscuro variable en juego)."""
    img = _canvas()
    cx, cy = SIZE / 2, SIZE / 2
    bone = (224, 96, 88, 255)
    outline = (64, 16, 12, 255)

    d = ImageDraw.Draw(img)
    d.ellipse([cx - SIZE * 0.34, cy - SIZE * 0.38, cx + SIZE * 0.34, cy + SIZE * 0.16],
              fill=bone, outline=outline, width=int(SIZE * 0.025))
    d.rectangle([cx - SIZE * 0.20, cy - SIZE * 0.02, cx + SIZE * 0.20, cy + SIZE * 0.22],
                fill=bone)
    d.line([(cx - SIZE * 0.20, cy + SIZE * 0.22), (cx + SIZE * 0.20, cy + SIZE * 0.22)],
           fill=outline, width=int(SIZE * 0.025))

    eye_r = SIZE * 0.09
    for ex in (cx - SIZE * 0.15, cx + SIZE * 0.15):
        d.ellipse([ex - eye_r, cy - SIZE * 0.14 - eye_r, ex + eye_r, cy - SIZE * 0.14 + eye_r],
                  fill=(0, 0, 0, 0))
        d.ellipse([ex - eye_r, cy - SIZE * 0.14 - eye_r, ex + eye_r, cy - SIZE * 0.14 + eye_r],
                  outline=outline, width=int(SIZE * 0.02))

    nose_r = SIZE * 0.045
    d.polygon([
        (cx, cy - SIZE * 0.02),
        (cx - nose_r, cy + SIZE * 0.06),
        (cx + nose_r, cy + SIZE * 0.06),
    ], fill=(0, 0, 0, 0), outline=outline)
    return _down(img)


def gen_note():
    """Corchea (nota musical): cabeza ovalada + plica + banderín, ámbar (mismo tono que
    ZenkaiPalette.VALUE, el que usaban las notas de Meditation como fill() plano). Pensada para
    dibujarse ANCHA/BAJA (blitteada a un rectángulo tipo 36x14, no cuadrada) — el contenido real
    ocupa una franja horizontal central de la celda con margen arriba/abajo a propósito."""
    img = _canvas()
    cx, cy = SIZE / 2, SIZE / 2
    fill = (255, 214, 102, 255)
    outline = (140, 96, 20, 255)

    head_rx, head_ry = SIZE * 0.16, SIZE * 0.12
    head_cx, head_cy = cx - SIZE * 0.12, cy + SIZE * 0.14
    d = ImageDraw.Draw(img)
    d.ellipse([head_cx - head_rx, head_cy - head_ry, head_cx + head_rx, head_cy + head_ry],
              fill=fill, outline=outline, width=int(SIZE * 0.025))

    stem_x = head_cx + head_rx * 0.85
    d.rectangle([stem_x, cy - SIZE * 0.32, stem_x + SIZE * 0.045, head_cy], fill=fill)

    d.polygon([
        (stem_x + SIZE * 0.045, cy - SIZE * 0.32),
        (stem_x + SIZE * 0.32, cy - SIZE * 0.20),
        (stem_x + SIZE * 0.30, cy - SIZE * 0.06),
        (stem_x + SIZE * 0.045, cy - SIZE * 0.14),
    ], fill=fill, outline=outline)
    return _down(img)


def main():
    im = Image.open(ICONS_PATH).convert("RGBA")
    im.paste(gen_orb(), (0, 120))
    im.paste(gen_bomb(), (20, 120))
    im.paste(gen_note(), (40, 120))
    im.save(ICONS_PATH)
    print("Updated icons.png row v=120 (gameplay sprites) ->", ICONS_PATH)


if __name__ == "__main__":
    main()
