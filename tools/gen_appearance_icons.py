#!/usr/bin/env python3
"""Genera los íconos del hub de AppearanceScreen ("Head"/"Body & Colors") dentro de
textures/gui/icons.png (256x256, grid de 20px, mismo atlas que las pestañas — ver
ZenkaiTab/TabIconButton/AtlasIconButton).

Fila v=80 (ya tenía (0,80)="Técnicas" y (20,80)="Servicios" de gen_master_icons.py; 40/60/80
seguían libres, confirmado por inspección directa de píxeles antes de escribir aquí — mismo
criterio que exige CLAUDE.md, no asumir libre solo porque el comentario de otro script diga
"primera fila completamente libre" de cuando se escribió):
    (40,80)  = "Head"          — AppearanceScreen.ICON_HEAD_U/V
    (60,80)  = "Body & Colors" — AppearanceScreen.ICON_BODY_U/V
    (80,80)  = RESERVADA para "Gender: Male" — AppearanceScreen.ICON_GENDER_MALE_U/V
    (100,80) = RESERVADA para "Gender: Female" — AppearanceScreen.ICON_GENDER_FEMALE_U/V

    (80,80)/(100,80) NO se generan aquí a propósito — el usuario las dibuja a mano. Pasaron por
    dos rondas de intentos generados (un badge pintado, luego un glifo ♂/♀ hecho con
    GuiGraphics.fill() en Java) y ninguno convenció; en vez de una tercera ronda, main() solo
    las deja limpias/transparentes y listas para que se pinten a mano. NO añadir código
    generador para estas dos celdas sin que el usuario lo pida — sustituiría lo que haya
    pintado ahí.

Fila v=100, celda (0,100) — confirmada íntegramente libre por muestreo de píxeles: RESERVADA
para "Reset View" (lupa) de HeadAppearanceScreen.ICON_RESET_VIEW_U/V,
BodyColorsScreen.ICON_RESET_VIEW_U/V y AppearanceScreen.ICON_RESET_VIEW_U/V (botón que reinicia
el zoom con rueda del preview a su valor por defecto, en las 3 pantallas con preview del hub de
apariencia). Mismo criterio que Gender arriba: una lupa es aro+mango, un glifo compuesto de 2
piezas — se deja SIN generar aquí, pintada a mano por el usuario (YA PINTADA). NO añadir código
generador para esta celda: sustituiría el arte hecho a mano.

ESTILO: MISMA familia que gen_party_icons.py/gen_master_icons.py (_badge: círculo con sombra
inferior + contorno + highlight sutil arriba-izq), NO el bisel plano de technique_icons.png —
icons.png sigue su propia familia visual pintada, ver CLAUDE.md.

Ajuste pedido explícitamente por el usuario al encargar este script: los glifos van en UN SOLO
color plano (blanco), sin degradados propios ni relleno con gradiente — el único difuminado de
todo el ícono es el highlight pequeño y sutil (alpha=80, blur=r*0.6) que YA usan el resto de
íconos pintados de esta fila/atlas, sin aumentarlo. Siluetas gruesas y simples (nada de detalle
fino que se pierda al reducir con LANCZOS) para que se lean nítidas a 20px en vez de "borrosas".

Ejecutar con: python tools/gen_appearance_icons.py
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

WHITE = (250, 245, 235, 255)


def _canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def _down(img):
    return img.resize((CELL, CELL), Image.LANCZOS)


def _highlight(img, cx, cy, r, alpha=80):
    """Brillo suave arriba-izquierda — mismo truco y misma intensidad que gen_party_icons.py/
    gen_master_icons.py, para que esta fila no desentone. NO subir alpha/radio: es justo el
    punto donde un highlight deja de leerse como "volumen" y empieza a leerse como "borroso"."""
    hl = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, alpha))
    hl = hl.filter(ImageFilter.GaussianBlur(r * 0.6))
    return Image.alpha_composite(img, hl)


def _badge(fill, fill_shadow, outline, glyph):
    """Círculo con sombra inferior + contorno + highlight arriba-izq, y GLYPH pintado encima
    (callback que recibe el ImageDraw y el centro/radio). Copiado de gen_party_icons.py/
    gen_master_icons.py a propósito (self-contained, ver la nota de gen_technique_icons.py:
    cada script del atlas es independiente)."""
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


def gen_head():
    """Busto de perfil (cabeza + hombros), el mismo glifo de "persona/perfil" que se usa en
    cualquier lado — se probó primero una cara con ojos+boca y a 20px se disolvía en un borrón
    sin forma reconocible (dos manchas y una raya). Dos formas grandes y separadas (círculo +
    arco de hombros) en vez de detalle fino: se lee de un vistazo incluso reducida. Ámbar
    cálido: evoca piel/rostro sin competir con los otros dos tonos de esta fila."""
    def glyph(d, cx, cy, r, outline):
        head_r = r * 0.40
        head_cy = cy - r * 0.28
        d.ellipse([cx - head_r, head_cy - head_r, cx + head_r, head_cy + head_r],
                   fill=WHITE, outline=outline, width=int(SIZE * 0.03))
        # Hombros: un solo trapecio ancho, no un contorno de ropa — misma idea que la gota de
        # Body/el rectángulo de Services, una silueta plana y grande en vez de detalle fino.
        top_w, bot_w = r * 0.40, r * 0.72
        top_y, bot_y = cy + r * 0.08, cy + r * 0.60
        d.polygon([
            (cx - top_w, top_y), (cx + top_w, top_y),
            (cx + bot_w, bot_y), (cx - bot_w, bot_y),
        ], fill=WHITE, outline=outline)
    return _badge((230, 176, 120, 255), (188, 132, 78, 255), (64, 40, 16, 255), glyph)


def gen_body():
    """Gota de pintura: silueta gruesa de un solo color plano — representa el tinte/color de
    piel y detalles sin necesitar un glifo de "cuerpo" (que a 20px se vería como un borrón sin
    forma clara). Teal, distinto de los otros dos badges de la fila."""
    def glyph(d, cx, cy, r, outline):
        top = (cx, cy - r * 0.55)
        d.polygon([top, (cx - r * 0.36, cy + r * 0.05), (cx + r * 0.36, cy + r * 0.05)],
                   fill=WHITE, outline=outline)
        d.ellipse([cx - r * 0.40, cy - r * 0.05, cx + r * 0.40, cy + r * 0.65],
                   fill=WHITE, outline=outline, width=int(SIZE * 0.03))
    return _badge((64, 158, 176, 255), (40, 116, 132, 255), (14, 48, 56, 255), glyph)


def main():
    im = Image.open(ICONS_PATH).convert("RGBA")
    im.paste(gen_head(), (40, 80))
    im.paste(gen_body(), (60, 80))
    # (80,80) y (100,80) (Gender Male/Female) NO se tocan aquí — quedaron limpias/transparentes
    # de una pasada anterior y el usuario las pinta a mano desde ahora. Mismo cuidado que
    # gen_party_icons.py ya documenta para sus 2 íconos de fuego amigo: NO volver a pastear
    # nada en estas 2 celdas una vez que tengan arte a mano, o lo pisaría.
    im.save(ICONS_PATH)
    print("Updated icons.png row v=80 (u=40/60 = Head/Body; u=80/100 untouched, reserved) ->", ICONS_PATH)


if __name__ == "__main__":
    main()
