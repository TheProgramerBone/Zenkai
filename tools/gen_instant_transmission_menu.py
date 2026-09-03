#!/usr/bin/env python3
"""Genera el fondo del diálogo del menú de planetas de Transmisión Instantánea
(InstantTransmissionMenuScreen) en
src/main/resources/assets/zenkai/textures/gui/instant_transmission_menu.png.

REDISEÑO "aires de espacio", pedido explícito del usuario — antes este diálogo usaba el marco
genérico DIALOG_BG/BORDER_IN/BORDER_MID a mano (relleno plano, sin identidad propia), mismo
punto de partida que master_screen.png tenía antes de tener textura propia (ver ese script).
Sigue la MISMA estructura que master_screen.png (marco de anillos duros + brillo de esquina +
banda de contenido) pero con paleta y contenido propios:

- Paleta AZUL-ÍNDIGO/VIOLETA (ZenkaiPalette.TRANSMISSION_*), deliberadamente DISTINTA tanto del
  naranja/dorado de los paneles del jugador (BORDER_*/DIALOG_*) como del ciruela/violeta cálido
  de un diálogo con maestro (MASTER_*) como del cian/teal PLANO de la temática tecnológica
  (ZenkaiTechPalette.CYAN/SCREEN_BG, scouter bench/energy generator) — MasterScreen ya advierte
  en su propio script sobre chocar justo con esa última familia ("se leía como un panel de
  máquina, no como un personaje sabio"); aquí el riesgo es el mismo si se usara un cian plano,
  así que el acento brillante usa el mismo tono "destello" que la propia Transmisión Instantánea
  ya lleva en otras partes del mod (0x7FE0FF, ver el git history de TransmissionGaugeOverlay/
  InstantTransmissionCrosshairOverlay) en vez de inventar un cian nuevo o pedir prestado el de
  scouter — es MÁS BRILLANTE/HELADO que ZenkaiTechPalette.CYAN (0xFF56B0C8) y vive sobre una base
  ÍNDIGO/VIOLETA oscura (no gris-azulado plano de máquina), así que se lee como "energía cósmica"
  y no como "panel tecnológico".

EXCEPCIÓN DOCUMENTADA a la regla de bordes duros del mod (ver gen_ki_fx.py, la única excepción
previa): la nebulosa de fondo SÍ usa un degradado suave (blobs con GaussianBlur) a propósito —
un campo de estrellas sin ninguna variación de tono se lee plano, no "espacial". El resto —
anillos del marco, brillo de esquina, y el campo de estrellas en sí (puntos de 1-2px, alguno en
cruz para las "más brillantes") — sigue la regla POR DEFECTO: bordes duros, sin antialiasing.

Este script es la ÚNICA fuente de esta textura — no editar el PNG a mano.
Ejecutar con: python tools/gen_instant_transmission_menu.py

Los colores y medidas están DUPLICADOS a propósito desde ZenkaiPalette.java y
InstantTransmissionMenuScreen.java (BG_W/BG_H/PADDING): un script de build no puede leer
bytecode Java, así que si cambian esas constantes hay que regenerar la textura a mano y volver a
ejecutar este script. Mantenlos en sync.
"""

import os
import random

from PIL import Image, ImageDraw, ImageFilter

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui",
)

# ── Geometría (== InstantTransmissionMenuScreen.java) ─────────────────────────
BG_W = 210
BG_H = 180
PADDING = 10
BORDER_W = 3  # 1px BORDER_IN + 1px BORDER_MID + 1px BORDER_OUT, de fuera hacia dentro

# ── Colores (== ZenkaiPalette.java, bloque "Paleta cósmica del menú de Instant Transmission") ──
BORDER_IN = 0xFF120C38    # anillo interior: índigo casi negro
BORDER_MID = 0xFF3D2E86   # anillo medio: violeta-azul medio
BORDER_OUT = 0xFF7FE0FF   # anillo exterior: el "destello" propio de Instant Transmission
BORDER_HI = 0xFFEAF7FF    # brillo de esquina: casi blanco, tinte helado
DIALOG_BG = 0xFF0A0E1E    # fondo base: espacio profundo
DIALOG_PANEL = 0xFF141230  # banda de contenido: un paso más clara, sigue siendo oscura

# Nebulosa: dos tonos de blob, baja alfa, se superponen y difuminan (única zona con degradado).
NEBULA_VIOLET = (0x6A, 0x3F, 0xB0, 60)
NEBULA_CYAN = (0x3F, 0xA0, 0xC4, 45)

# Estrellas: mayoría tenues blanco-azuladas, unas pocas "destacadas" en el cian propio.
STAR_DIM = (0xC8, 0xD8, 0xF0, 160)
STAR_BRIGHT = (0xFF, 0xFF, 0xFF, 230)
STAR_ACCENT = (0x9F, 0xEC, 0xFF, 230)

SEED = 20260903  # fecha de esta ronda — determinista, no cambia entre ejecuciones


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


def add_nebula(img):
    """Dos manchas de color muy suaves, borrosas — la ÚNICA parte de este atlas con degradado
    (ver el docstring de módulo). Se pintan ANTES del campo de estrellas y del marco, para que
    ambos queden encima sin que el blur se filtre por fuera de los anillos (el marco se dibuja
    después, sobre el propio borde de la imagen)."""
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    d.ellipse([BG_W * 0.05, BG_H * 0.35, BG_W * 0.55, BG_H * 1.05], fill=NEBULA_VIOLET)
    d.ellipse([BG_W * 0.45, -BG_H * 0.15, BG_W * 1.05, BG_H * 0.55], fill=NEBULA_CYAN)
    overlay = overlay.filter(ImageFilter.GaussianBlur(BG_W * 0.12))
    return Image.alpha_composite(img, overlay)


def add_stars(img, rng, count, region):
    """Campo de estrellas de bordes duros (sin blur) dentro de `region` = (x0,y0,x1,y1). La
    mayoría son un solo píxel tenue; unas pocas son un píxel brillante blanco o acento cian;
    menos aún llevan una cruz de 3px (la "más brillante" del racimo).
    Se pintan en una capa APARTE y se componen con alpha_composite (nunca ImageDraw directo
    sobre `img`): dibujar directo con un fill translúcido REEMPLAZA el píxel en vez de
    mezclarlo, dejando agujeros de transparencia real en un panel que tiene que quedar opaco
    de principio a fin (ver DIALOG_BG en ZenkaiPalette — el mundo detrás no puede colarse).
    Componer sobre un fondo ya opaco siempre da alfa final 255, sea cual sea el alfa de la
    estrella — mismo truco que ya usa add_nebula."""
    x0, y0, x1, y1 = region
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    for _ in range(count):
        x = rng.randint(x0, x1)
        y = rng.randint(y0, y1)
        roll = rng.random()
        if roll < 0.75:
            d.point((x, y), fill=STAR_DIM)
        elif roll < 0.93:
            d.point((x, y), fill=STAR_BRIGHT)
        else:
            color = STAR_ACCENT if rng.random() < 0.5 else STAR_BRIGHT
            d.point((x, y), fill=color)
            d.point((x - 1, y), fill=color)
            d.point((x + 1, y), fill=color)
            d.point((x, y - 1), fill=color)
            d.point((x, y + 1), fill=color)
    return Image.alpha_composite(img, overlay)


def gen():
    img = Image.new("RGBA", (BG_W, BG_H), argb(DIALOG_BG))
    img = add_nebula(img)

    rng = random.Random(SEED)
    img = add_stars(img, rng, 90, (BORDER_W, BORDER_W, BG_W - 1 - BORDER_W, BG_H - 1 - BORDER_W))

    px = img.load()
    for inset, color in enumerate((BORDER_IN, BORDER_MID, BORDER_OUT)):
        draw_ring(px, inset, color)

    # Brillo de esquina: bloque sólido, no un degradado — mismo criterio que master_screen.png.
    for cx, cy in ((0, 0), (BG_W - BORDER_W, 0),
                   (0, BG_H - BORDER_W), (BG_W - BORDER_W, BG_H - BORDER_W)):
        fill_rect(px, cx, cy, BORDER_W, BORDER_W, BORDER_HI)

    # Banda de contenido: un paso más clara que el fondo, bordes duros, deja el margen de los
    # anillos intacto — mismo principio que la banda de master_screen.png.
    cx0 = cy0 = BORDER_W
    cw = BG_W - BORDER_W * 2
    ch = BG_H - BORDER_W * 2
    band = Image.new("RGBA", (cw, ch), argb(DIALOG_PANEL))
    band.putalpha(70)  # semitransparente: dejar la nebulosa/estrellas asomando debajo
    img.alpha_composite(band, (cx0, cy0))

    return img.convert("RGBA")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    out_path = os.path.join(OUT_DIR, "instant_transmission_menu.png")
    gen().save(out_path)
    print(f"Escrito {out_path}")


if __name__ == "__main__":
    main()
