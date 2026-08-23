#!/usr/bin/env python3
"""Genera la fila de íconos de PartyScreen dentro de textures/gui/icons.png (256x256, grid
de 20px, mismo atlas que las pestañas — ver ZenkaiTab/TabIconButton).

Fila v=60 (estaba libre por completo antes de esto):
    (0,60)  = fuego amigo OFF (protegido)   — FriendlyFireIconButton
    (20,60) = fuego amigo ON (peligro)      — FriendlyFireIconButton
    (40,60) = sobre de correo               — AtlasIconButton, botón "Invitar"
    (60,60) = "prohibido" (expulsar)        — dibujado a mano sobre la cabeza al pasar el
                                                ratón en PartyScreen, no un botón propio
    (80,60) = engranaje "PartyConfig"       — AtlasIconButton, abre el picker de tamaño
                                                máximo (ver PartyScreen.MaxSizePopup)

A DIFERENCIA de gen_ki_fx.py, que es degradado continuo por ser una excepción documentada,
estos SÍ llevan un poco de sombreado suave (aro con highlight) porque así es el resto del
atlas de pestañas (fuego, escudo, libro…) — pintados, no el bisel plano de dos tonos de
btn_x.png/btn_trash.png. Supersample a 8x y reduce con LANCZOS para el antialias; nada de
esto pasa por un solo píxel puesto a mano.

OJO — los dos íconos de fuego amigo son un PLACEHOLDER: el usuario dijo que los va a dibujar
él mismo en esas mismas dos celdas. Si ya los reemplazó a mano, NO vuelvas a correr este
script entero (lo pisaría) — o recorta gen_ff_icons()/su paste() de main() antes de correr.
Ejecutar con: python tools/gen_party_icons.py
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
    """Brillo suave arriba-izquierda, para que el círculo no se vea plano — mismo truco en
    los cuatro íconos, así se leen como una sola familia."""
    hl = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, alpha))
    hl = hl.filter(ImageFilter.GaussianBlur(r * 0.6))
    return Image.alpha_composite(img, hl)


def _badge(fill, fill_shadow, outline, glyph):
    """Círculo con sombra inferior + contorno + highlight arriba-izq, y GLYPH pintado encima
    (callback que recibe el ImageDraw y el centro/radio). Los tres íconos circulares
    (FF on, FF off, expulsar) comparten esta base — solo cambia color y glifo."""
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


def gen_ff_off():
    def glyph(d, cx, cy, r, outline):
        white = (250, 245, 235, 255)
        d.line([cx - r * 0.4, cy + r * 0.05, cx - r * 0.05, cy + r * 0.35], fill=white, width=int(SIZE * 0.08))
        d.line([cx - r * 0.05, cy + r * 0.35, cx + r * 0.45, cy - r * 0.3], fill=white, width=int(SIZE * 0.08))
    return _badge((118, 158, 86, 255), (86, 120, 58, 255), (30, 45, 20, 255), glyph)


def gen_ff_on():
    def glyph(d, cx, cy, r, outline):
        white = (250, 245, 235, 255)
        d.line([cx - r * 0.38, cy - r * 0.38, cx + r * 0.38, cy + r * 0.38], fill=white, width=int(SIZE * 0.09))
        d.line([cx + r * 0.38, cy - r * 0.38, cx - r * 0.38, cy + r * 0.38], fill=white, width=int(SIZE * 0.09))
    return _badge((196, 58, 42, 255), (150, 38, 26, 255), (60, 20, 14, 255), glyph)


def gen_kick():
    """"Prohibido" — círculo rojo con una sola barra horizontal. Se pinta sobre la cabeza del
    miembro al pasar el ratón (ver PartyScreen.drawMemberRow), no vive en un botón propio."""
    def glyph(d, cx, cy, r, outline):
        bw, bh = r * 1.35, SIZE * 0.13
        d.rounded_rectangle([cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2], radius=bh * 0.4,
                             fill=(250, 245, 235, 255), outline=outline, width=int(SIZE * 0.02))
    return _badge((196, 58, 42, 255), (150, 38, 26, 255), (60, 20, 14, 255), glyph)


def gen_mail():
    img = _canvas()
    d = ImageDraw.Draw(img)
    pad = SIZE * 0.14
    x0, y0, x1, y1 = pad, pad + SIZE * 0.08, SIZE - pad, SIZE - pad * 0.9
    outline = (60, 40, 18, 255)
    body = (232, 184, 90, 255)
    body_shadow = (196, 144, 58, 255)

    d.rounded_rectangle([x0, y0, x1, y1], radius=SIZE * 0.06, fill=body)
    d.rounded_rectangle([x0, y0 + (y1 - y0) * 0.55, x1, y1], radius=SIZE * 0.06, fill=body_shadow)
    d.rectangle([x0, y0, x1, y0 + (y1 - y0) * 0.45], fill=body)
    d.rounded_rectangle([x0, y0, x1, y1], radius=SIZE * 0.06, outline=outline, width=int(SIZE * 0.045))

    mx, my = (x0 + x1) / 2, y0 + (y1 - y0) * 0.62
    d.line([x0 + SIZE * 0.02, y0 + SIZE * 0.02, mx, my], fill=outline, width=int(SIZE * 0.05))
    d.line([x1 - SIZE * 0.02, y0 + SIZE * 0.02, mx, my], fill=outline, width=int(SIZE * 0.05))

    img = _highlight(img, SIZE * 0.38, SIZE * 0.32, SIZE * 0.22, alpha=70)
    return _down(img)


def gen_config():
    """Engranaje de "PartyConfig" (ajustar el tamaño máximo de la party). Mismo esqueleto
    de _badge (círculo con sombra inferior + contorno + highlight) que fuego amigo/expulsar,
    pero SIN pasar por el helper: los dientes necesitan rotarse como capas propias, algo que
    el callback `glyph` de _badge no puede hacer porque solo recibe un ImageDraw, no la
    Image completa. Color acero neutro a propósito — ni verde/rojo (fuego amigo) ni dorado
    (correo) ni rojo (expulsar), para no competir semánticamente con esos tres."""
    img = _canvas()
    cx, cy, r = SIZE / 2, SIZE / 2, SIZE * 0.42
    fill = (108, 124, 140, 255)
    fill_shadow = (78, 92, 106, 255)
    outline = (34, 40, 48, 255)
    ring_w = int(SIZE * 0.05)

    d = ImageDraw.Draw(img)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=outline, width=ring_w)

    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse([cx - r, cy - r * 0.2, cx + r, cy + r], fill=fill_shadow)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    shadow.putalpha(Image.composite(shadow.split()[3], Image.new("L", img.size, 0), mask))
    img = Image.alpha_composite(img, shadow)
    ImageDraw.Draw(img).ellipse([cx - r, cy - r, cx + r, cy + r], outline=outline, width=ring_w)

    # Dientes: UN rectángulo redondeado dibujado arriba (12 en punto) sobre una capa aparte,
    # copiado y rotado 6 veces alrededor del centro — más simple y fiable que trigonometría
    # a mano para posicionar cada diente.
    tw, th = r * 0.36, r * 0.42
    tooth_layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(tooth_layer).rounded_rectangle(
        [cx - tw / 2, cy - r - th * 0.5, cx + tw / 2, cy - r + th * 0.5],
        radius=tw * 0.3, fill=fill, outline=outline, width=max(1, int(SIZE * 0.02)))
    for i in range(6):
        img = Image.alpha_composite(
            img, tooth_layer.rotate(i * 60, center=(cx, cy), resample=Image.BICUBIC))

    # Vuelve a marcar el anillo del cuerpo por encima de los dientes que lo tapan un poco.
    ImageDraw.Draw(img).ellipse([cx - r, cy - r, cx + r, cy + r], outline=outline, width=ring_w)

    # Agujero central: fill totalmente transparente SOBRECRIBE los píxeles (ImageDraw no
    # mezcla alfa, los reemplaza), así que esto perfora un hueco real en vez de pintar negro.
    hole_r = r * 0.42
    ImageDraw.Draw(img).ellipse(
        [cx - hole_r, cy - hole_r, cx + hole_r, cy + hole_r], fill=(0, 0, 0, 0))
    ImageDraw.Draw(img).ellipse(
        [cx - hole_r, cy - hole_r, cx + hole_r, cy + hole_r],
        outline=outline, width=max(1, int(SIZE * 0.035)))

    img = _highlight(img, cx - r * 0.35, cy - r * 0.45, r * 0.5)
    return _down(img)


def main():
    im = Image.open(ICONS_PATH).convert("RGBA")
    im.paste(gen_ff_off(), (0, 60))
    im.paste(gen_ff_on(), (20, 60))
    im.paste(gen_mail(), (40, 60))
    im.paste(gen_kick(), (60, 60))
    im.paste(gen_config(), (80, 60))
    im.save(ICONS_PATH)
    print("Updated icons.png row v=60 ->", ICONS_PATH)


if __name__ == "__main__":
    main()
