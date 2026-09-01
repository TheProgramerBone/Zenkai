#!/usr/bin/env python3
"""Genera las barras de Body/Stamina/Ki del HUD en
src/main/resources/assets/zenkai/textures/gui/bars_empty.png y bars_full.png.

REDISEÑO: el arte anterior (créditos: Spongtari) era pintado a mano con sombreado suave —
correcto en su momento, pero desentonaba con el resto de la GUI del mod, que sigue la regla de
bordes duros / sin degradado (ver gen_ki_fx.py) para cualquier textura generada por script.
Este script sustituye ese arte por un medidor de poder estilo escáner: paralelogramo inclinado
con punta de flecha a la derecha (mismo lenguaje que el original: la punta solo se rellena
cuando la barra está casi llena, porque bars_full.png se recorta por ANCHO de origen, ver
ClientZenkaiHooks.drawStatBar) + marcas de segmento diagonales + bisel plano de 2 tonos en el
borde (mismo lenguaje que btn_x.png / technique_icons.png, NO el pintado+antialiasing de
icons.png).

Este script es la ÚNICA fuente de estas dos texturas — no editar los PNG a mano.
Ejecutar con: python tools/gen_bars.py

Geometría == com.hmc.zenkai.event.ClientZenkaiHooks.java (BARS_TEX_W/H, BARS_SRC_V/H,
BAR_ROW_H, ROW_V_BODY/STAMINA/KI): un script de build no puede leer bytecode Java, así que si
esas constantes cambian ahí hay que regenerar esta textura a mano y volver a ejecutar este
script. Mantenlos en sync.

Colores: rojo/verde/cian. Stamina REUTILIZA ZenkaiPalette.BAR_STAMINA (fill) y OK_ON_PANEL (borde
oscuro) a propósito — aunque esas constantes están pensadas para las StatBar de los menús (sobre
beige), un verde es un verde: mejor compartir el tono que inventar uno nuevo que no signifique
lo mismo en dos sitios del mod. Body/Ki no tienen un equivalente tan directo en ZenkaiPalette
(sus BAR_BODY/BAR_KI están "apagados un punto para no gritar sobre el beige", ver ese archivo) y
el HUD sobre el mundo puede permitirse más saturación, así que siguen su propio tono.

CASCADA (bars + números): cada barra es más corta que la anterior (BODY_W), y el ancho real de
contenido resultante (SHEAR + BODY_W + TIP, ver ROW_CONTENT_W) es lo que
ClientZenkaiHooks.drawStatBar usa para CENTRAR el texto cur/max de esa fila — así el número seca
la misma escalera que dibuja el borde en vez de quedar centrado sobre el ancho de bloque
completo (que es igual para las 3 filas y rompía la sensación de cascada). Si BODY_W cambia
aquí, ROW_CONTENT_W (y su copia en ClientZenkaiHooks.ROW_CONTENT_W_*) hay que recalcularlos.
"""

import os

from PIL import Image, ImageDraw

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui",
)

# ── Geometría (== ClientZenkaiHooks.java) ───────────────────────────────────
TEX_W = 256
TEX_H = 64          # == BARS_SRC_H. Canvas SIN relleno muerto (el arte anterior tenía 128 con
                     # 64px de margen sin usar arriba/abajo — no hace falta, ver credit history).
ROW_H = 20           # == BAR_ROW_H
GAP = 2              # separación vertical entre barras apiladas (el arte anterior las pegaba
                     # directamente, lo que las hacía leerse fusionadas a escalas pequeñas).
ROW_V = {"body": 0, "stamina": 22, "ki": 44}  # == ROW_V_BODY/STAMINA/KI (offset ya sin BARS_SRC_V)

SHEAR = 6            # inclinación itálica: el borde izquierdo/derecho se desplaza este tanto
                     # entre arriba y abajo de la fila.
BORDER = 2           # grosor del borde plano.
TIP = 12             # longitud de la punta de flecha más allá del cuerpo del paralelogramo.

# Cuerpo (ancho del paralelogramo antes de la punta) — cada barra más corta que la anterior,
# igual que el arte original (Body > Stamina > Ki), para que las tres no se lean como una sola
# tira repetida.
BODY_W = {"body": 224, "stamina": 206, "ki": 188}

# Ancho real de contenido por fila (SHEAR + BODY_W + TIP) — == ClientZenkaiHooks.
# ROW_CONTENT_W_BODY/STAMINA/KI, usado ahí para centrar el texto cur/max sobre CADA barra en vez
# del ancho de bloque completo. Recalcular a mano si BODY_W/SHEAR/TIP cambian.
ROW_CONTENT_W = {k: SHEAR + w + TIP for k, w in BODY_W.items()}

# Espaciado de las marcas de segmento diagonales dentro del cuerpo.
TICK_STEP = 22

# ── Colores por barra: (borde claro, borde oscuro, relleno, interior vacío) ─────────────────
BARS = {
    "body":    dict(hi=0xFFFF7A63, lo=0xFF7A1408, fill=0xFFD62A1E, empty=0xFF1A0E0C),
    # fill == ZenkaiPalette.BAR_STAMINA, lo == ZenkaiPalette.OK_ON_PANEL (ver docstring).
    "stamina": dict(hi=0xFF9FE080, lo=0xFF2E6B26, fill=0xFF5EA83A, empty=0xFF10190D),
    "ki":      dict(hi=0xFF7FE0FF, lo=0xFF0E5C78, fill=0xFF1E96C8, empty=0xFF0C161A),
}

ORDER = ["body", "stamina", "ki"]


def argb(v):
    return ((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF, (v >> 24) & 0xFF)


def outer_points(width):
    """Silueta exterior (paralelogramo + punta), en coordenadas locales de la fila (0,0 =
    esquina superior izquierda del bloque de la fila, alto ROW_H)."""
    return [
        (SHEAR, 0),
        (SHEAR + width, 0),
        (SHEAR + width + TIP, ROW_H / 2),
        (SHEAR + width, ROW_H),
        (0, ROW_H),
    ]


def inner_points(width):
    """Silueta interior (recorte de BORDER px hacia dentro) donde va el relleno/interior vacío."""
    b = BORDER
    return [
        (SHEAR + b, b),
        (SHEAR + width - b, b),
        (SHEAR + width + TIP - b * 1.6, ROW_H / 2),
        (SHEAR + width - b, ROW_H - b),
        (b, ROW_H - b),
    ]


def draw_row(canvas_draw, row_y, key, filled):
    width = BODY_W[key]
    c = BARS[key]

    outer = [(x, y + row_y) for x, y in outer_points(width)]
    inner = [(x, y + row_y) for x, y in inner_points(width)]

    # Borde: base oscura + franja clara SOLO en los tramos superiores (donde "da la luz"),
    # bisel plano de 2 tonos, sin degradado.
    canvas_draw.polygon(outer, fill=argb(c["lo"]))
    canvas_draw.line([outer[0], outer[1]], fill=argb(c["hi"]), width=2)
    canvas_draw.line([outer[1], outer[2]], fill=argb(c["hi"]), width=2)

    canvas_draw.polygon(inner, fill=argb(c["fill"] if filled else c["empty"]))

    # Marcas de segmento: líneas diagonales (mismo SHEAR que el cuerpo) cada TICK_STEP px,
    # dibujadas con el tono oscuro del borde para que lean como muescas del medidor.
    x = TICK_STEP
    while x < width - BORDER:
        top = (SHEAR + x, row_y + 1)
        bot = (x, row_y + ROW_H - 1)
        canvas_draw.line([top, bot], fill=argb(c["lo"]), width=1)
        x += TICK_STEP

    # Resalte de 1px en el borde superior del interior (bisel plano, no degradado).
    hi_top = [inner[0], inner[1]]
    canvas_draw.line(hi_top, fill=argb(c["hi"] if filled else c["lo"]), width=1)


def build(filled):
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for key in ORDER:
        draw_row(d, ROW_V[key], key, filled)
    return img


def main():
    empty = build(filled=False)
    full = build(filled=True)

    empty_path = os.path.join(OUT_DIR, "bars_empty.png")
    full_path = os.path.join(OUT_DIR, "bars_full.png")
    empty.save(empty_path)
    full.save(full_path)
    print(f"Wrote {empty_path}")
    print(f"Wrote {full_path}")
    print(f"ROW_CONTENT_W (copiar a ClientZenkaiHooks si cambió): {ROW_CONTENT_W}")


if __name__ == "__main__":
    main()
