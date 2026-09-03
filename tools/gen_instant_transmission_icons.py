#!/usr/bin/env python3
"""Genera los iconos de planetas/destinos del menú de Instant Transmission (Fase 2) dentro de
textures/gui/icons_instant_transmision.png (256x256, grid de 20px) — un atlas PROPIO, separado
de icons.png, creado a propósito para que mods de dimensiones externas puedan sumar sus propios
iconos ahí sin tocar el atlas principal del HUD (decisión del usuario, ver
.claude/pendiente/instant-transmission-pendiente.md).

Fila v=0. Mapa de columnas — REVISADO al generalizar el sistema a cualquier dimensión modeada
(ver .claude/pendiente/instant-transmission-pendiente.md, ronda "dimensiones universales"): los
PÍXELES de este atlas NO cambiaron con esa ronda, solo el código que los interpreta — columnas
0/3 siguen siendo Overworld/Otherworld (TeleportRealm, ahora solo esos dos valores, con
`iconColumn()` explícito en vez de `ordinal()`); columnas 1/2 (antes TeleportRealm.NETHER/END)
pasaron a ser el ícono de esas DOS dimensiones dentro del mapa genérico
`InstantTransmissionMenuScreen.KNOWN_DIM_ICON_COLUMN`, resuelto por su ResourceLocation en vez de
un valor de enum; columna 4 (antes el placeholder fijo "Dimensión Desconocida" /
TeleportRealm.THIRD_PARTY, un signo de interrogación) pasó a ser el ícono de RESERVA para
cualquier dimensión de un mod de terceros sin entrada propia en ese mapa
(`DEFAULT_DIM_ICON_COLUMN`) — el mismo dibujo, reaprovechado como fallback de verdad en vez de
una fila fija "sin implementar todavía". Columnas de destino: 5 + ordinal de
TeleportDestination (ahora solo 4 valores, ver ese enum — NETHER_PORTAL/END_SPAWN se retiraron,
fusionados en el mecanismo genérico de arriba):
    (0,0)   = Overworld              — TeleportRealm.OVERWORLD (iconColumn() = 0)
    (20,0)  = Nether                 — dimensión GENÉRICA minecraft:the_nether
    (40,0)  = El Fin                 — dimensión GENÉRICA minecraft:the_end
    (60,0)  = Otherworld             — TeleportRealm.OTHERWORLD (iconColumn() = 3)
    (80,0)  = Ícono de RESERVA ("?") — cualquier dimensión sin entrada propia en el mapa
    (100,0) = Home                   — TeleportDestination.HOME
    (120,0) = Kami's Palace          — TeleportDestination.KAMI_PALACE
    (140,0) = Yemma's Palace         — TeleportDestination.YEMMA_PALACE
    (160,0) = Planeta de Kaiosama    — TeleportDestination.KAIOSAMA_PLANET
    (220,0) = Party (TP a compañeros de party, nivel 8+) — sin ordinal propio, no es ni un
              TeleportRealm ni un TeleportDestination (ver InstantTransmissionMenuScreen.
              ICON_PARTY) — columna fija, sin relación con las de destino.
    (180,0), (200,0) = SIN USO desde esta ronda (antes Portal del Nether/Plataforma del Fin,
              enum retirado) — quedan transparentes, libres para un futuro ícono nuevo.

Estilo: bisel plano de bordes duros, SIN antialiasing ni sombreado suave (a diferencia de
icons.png/technique_icons.png, que ya tienen su propio precedente pintado — este atlas es nuevo
y no hereda ninguno, así que sigue la regla POR DEFECTO del mod: bordes duros, sin degradados).
Supersample x4 + reducción NEAREST (no LANCZOS) para conservar el filo duro en los círculos en
vez de suavizarlo.

Colores: literales, sin acoplamiento a ZenkaiPalette (esta pantalla usa su propia paleta de
diálogo genérica — DIALOG_BG/BORDER_IN — para el marco, no para el contenido de estos iconos).

Ejecutar con: python tools/gen_instant_transmission_icons.py
"""

import os
from PIL import Image, ImageDraw

ICONS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "gui",
    "icons_instant_transmision.png",
)

SS = 4            # supersample (bajo a propósito: bordes duros, no antialiasing)
CELL = 20
SIZE = CELL * SS


def _canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def _down(img):
    return img.resize((CELL, CELL), Image.NEAREST)


def _circle(d, cx, cy, r, fill, outline=None, width=1):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=outline, width=width * SS)


def icon_overworld():
    img = _canvas()
    d = ImageDraw.Draw(img)
    cx, cy, r = SIZE // 2, SIZE // 2, SIZE // 2 - 2 * SS
    _circle(d, cx, cy, r, (0x3A, 0x7B, 0xD9, 255), outline=(0x1E, 0x45, 0x7A, 255))
    # Un par de "continentes" simples, mismo verde plano sin degradado.
    d.polygon([(cx - r * 0.5, cy - r * 0.3), (cx - r * 0.1, cy - r * 0.6),
               (cx + r * 0.2, cy - r * 0.2), (cx - r * 0.1, cy + r * 0.1)],
              fill=(0x4C, 0xA3, 0x3A, 255))
    d.polygon([(cx + r * 0.1, cy + r * 0.2), (cx + r * 0.5, cy + r * 0.1),
               (cx + r * 0.4, cy + r * 0.55), (cx + r * 0.05, cy + r * 0.5)],
              fill=(0x4C, 0xA3, 0x3A, 255))
    return _down(img)


def icon_nether():
    img = _canvas()
    d = ImageDraw.Draw(img)
    cx, cy, r = SIZE // 2, SIZE // 2, SIZE // 2 - 2 * SS
    _circle(d, cx, cy, r, (0x8A, 0x1E, 0x12, 255), outline=(0x3A, 0x0A, 0x06, 255))
    # Grieta de lava: una sola forma dentada, mismo criterio que el resto (un glifo dominante).
    d.polygon([(cx - r * 0.5, cy - r * 0.5), (cx - r * 0.1, cy - r * 0.1),
               (cx + r * 0.3, cy - r * 0.4), (cx + r * 0.1, cy + r * 0.05),
               (cx + r * 0.45, cy + r * 0.5), (cx - r * 0.15, cy + r * 0.15),
               (cx - r * 0.4, cy + r * 0.55)],
              fill=(0xF2, 0x8A, 0x1E, 255))
    return _down(img)


def icon_end():
    img = _canvas()
    d = ImageDraw.Draw(img)
    cx, cy, r = SIZE // 2, SIZE // 2, SIZE // 2 - 2 * SS
    _circle(d, cx, cy, r, (0x1A, 0x14, 0x24, 255), outline=(0x3A, 0x2E, 0x4A, 255))
    # Ojo de Ender: un solo iris almendrado descentrado, la pieza mínima que lo hace reconocible
    # sin caer en un glifo de varias piezas sueltas (ver la lección de Gender en AppearanceScreen).
    eye_w, eye_h = r * 1.1, r * 0.55
    ex, ey = cx - r * 0.05, cy + r * 0.05
    d.ellipse([ex - eye_w / 2, ey - eye_h / 2, ex + eye_w / 2, ey + eye_h / 2],
              fill=(0x7A, 0xC8, 0x5A, 255))
    d.ellipse([ex - eye_h * 0.28, ey - eye_h * 0.28, ex + eye_h * 0.28, ey + eye_h * 0.28],
              fill=(0x1A, 0x14, 0x24, 255))
    return _down(img)


def icon_otherworld():
    img = _canvas()
    d = ImageDraw.Draw(img)
    cx, cy, r = SIZE // 2, SIZE // 2, SIZE // 2 - 2 * SS
    _circle(d, cx, cy, r, (0x4A, 0x3A, 0x5E, 255), outline=(0x2A, 0x1E, 0x38, 255))
    # Media luna más clara (fase), un solo tono plano sin degradado.
    d.pieslice([cx - r, cy - r, cx + r, cy + r], -30, 150, fill=(0x6E, 0x5A, 0x86, 255))
    _circle(d, cx - int(r * 0.15), cy, int(r * 0.92), (0x4A, 0x3A, 0x5E, 255))
    return _down(img)


def icon_unknown_dimension():
    img = _canvas()
    d = ImageDraw.Draw(img)
    cx, cy, r = SIZE // 2, SIZE // 2, SIZE // 2 - 2 * SS
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(0x99, 0x99, 0x99, 255), width=2 * SS)
    # Interrogante simple, trazo grueso — un solo glifo dominante en vez de piezas sueltas.
    d.arc([cx - r * 0.4, cy - r * 0.55, cx + r * 0.4, cy + r * 0.05],
          start=200, end=430, fill=(0x99, 0x99, 0x99, 255), width=int(2.2 * SS))
    d.ellipse([cx - 1.4 * SS, cy + r * 0.35, cx + 1.4 * SS, cy + r * 0.35 + 2.8 * SS],
              fill=(0x99, 0x99, 0x99, 255))
    return _down(img)


def icon_home():
    img = _canvas()
    d = ImageDraw.Draw(img)
    m = 2 * SS
    w, h = SIZE - 2 * m, SIZE - 2 * m
    x0, y0 = m, m + int(h * 0.18)
    body_h = h - int(h * 0.18)
    # Silueta ÚNICA (un solo polígono: cuerpo + techo a dos aguas), 2-tono plano.
    d.polygon([
        (x0, y0 + body_h), (x0, y0 + int(body_h * 0.45)),
        (x0 + w // 2, y0 - int(h * 0.18)), (x0 + w, y0 + int(body_h * 0.45)),
        (x0 + w, y0 + body_h),
    ], fill=(0xD9, 0xA2, 0x3A, 255), outline=(0x6E, 0x4A, 0x18, 255), width=1 * SS)
    # Puerta, mismo tono oscuro del contorno.
    door_w, door_h = w * 0.28, body_h * 0.5
    d.rectangle([x0 + w / 2 - door_w / 2, y0 + body_h - door_h, x0 + w / 2 + door_w / 2, y0 + body_h],
                fill=(0x6E, 0x4A, 0x18, 255))
    return _down(img)


def icon_kami_palace():
    img = _canvas()
    d = ImageDraw.Draw(img)
    m = 2 * SS
    w = SIZE - 2 * m
    base_y = SIZE - m
    # Torre única: base ancha + cuerpo + remate en punta, un solo polígono, blanco/dorado.
    d.polygon([
        (m, base_y), (m, base_y - w * 0.55),
        (m + w * 0.5, base_y - w * 1.05),
        (m + w, base_y - w * 0.55), (m + w, base_y),
    ], fill=(0xF2, 0xE6, 0xC8, 255), outline=(0x8A, 0x6A, 0x3A, 255), width=1 * SS)
    # Franja base (escalón del pilar), mismo tono de contorno.
    d.rectangle([m - 1 * SS, base_y - 2 * SS, m + w + 1 * SS, base_y], fill=(0x8A, 0x6A, 0x3A, 255))
    return _down(img)


def icon_yemma_palace():
    img = _canvas()
    d = ImageDraw.Draw(img)
    m = 2 * SS
    w = SIZE - 2 * m
    top, bottom = m, SIZE - m
    # Arco/puerta única: rectángulo con la parte superior redondeada (una sola silueta).
    d.rounded_rectangle([m, top, m + w, bottom], radius=int(w * 0.45),
                         fill=(0xB4, 0x4A, 0x2E, 255), outline=(0x5E, 0x1E, 0x0E, 255), width=1 * SS)
    # Vano interior (hueco), mismo tono oscuro del contorno — sugiere puerta sin ser una 2ª pieza.
    inner_m = w * 0.28
    d.rounded_rectangle([m + inner_m, top + w * 0.55, m + w - inner_m, bottom],
                         radius=int(w * 0.2), fill=(0x5E, 0x1E, 0x0E, 255))
    return _down(img)


def icon_kaiosama_planet():
    img = _canvas()
    d = ImageDraw.Draw(img)
    cx, cy, r = SIZE // 2, SIZE // 2, int(SIZE * 0.32)
    _circle(d, cx, cy, r, (0xD9, 0x8A, 0x2E, 255), outline=(0x6E, 0x42, 0x12, 255))
    # Anillo: una elipse fina cruzando el planeta, mismo motivo que Saturno — una sola pieza
    # adicional reconocible, no un glifo de varias piezas sueltas.
    ring_w, ring_h = int(r * 2.6), int(r * 0.7)
    ring = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    rd = ImageDraw.Draw(ring)
    rd.ellipse([cx - ring_w // 2, cy - ring_h // 2, cx + ring_w // 2, cy + ring_h // 2],
               outline=(0xF2, 0xC9, 0x7A, 255), width=int(1.6 * SS))
    img.alpha_composite(ring)
    return _down(img)


def icon_nether_portal():
    img = _canvas()
    d = ImageDraw.Draw(img)
    m = 2 * SS
    w, h = SIZE - 2 * m, SIZE - 2 * m
    # Marco de obsidiana (silueta única) con el hueco del portal relleno de magenta plano —
    # mismo lenguaje de "silueta + hueco" que ya usa icon_yemma_palace, solo que aquí el hueco
    # SÍ lleva su propio color (la identidad del icono es precisamente ese color).
    d.rectangle([m, m, m + w, m + h], fill=(0x14, 0x0A, 0x1E, 255), outline=(0x0A, 0x05, 0x12, 255), width=1 * SS)
    frame = w * 0.22
    d.rectangle([m + frame, m + frame * 0.6, m + w - frame, m + h - frame * 0.6],
                fill=(0xB8, 0x3A, 0xE8, 255))
    return _down(img)


def icon_end_spawn():
    img = _canvas()
    d = ImageDraw.Draw(img)
    m = 2 * SS
    w = SIZE - 2 * m
    # Plataforma de obsidiana: una sola barra gruesa, mismo criterio "un solo glifo dominante"
    # que Home/Kami — nada de patas ni pilares sueltos, sería demasiado detalle a 20px.
    plat_y0, plat_y1 = SIZE - m - w * 0.22, SIZE - m
    d.rectangle([m, plat_y0, m + w, plat_y1], fill=(0x1E, 0x18, 0x28, 255), outline=(0x0A, 0x08, 0x12, 255), width=1 * SS)
    # Ojo de Ender clavado en la plataforma, mismo tono que icon_end — sugiere "portal de salida"
    # sin repetir la forma de icon_nether_portal.
    eye_w, eye_h = w * 0.55, w * 0.3
    ex, ey = m + w / 2, plat_y0 - eye_h * 0.35
    d.ellipse([ex - eye_w / 2, ey - eye_h / 2, ex + eye_w / 2, ey + eye_h / 2], fill=(0x7A, 0xC8, 0x5A, 255))
    d.ellipse([ex - eye_h * 0.28, ey - eye_h * 0.28, ex + eye_h * 0.28, ey + eye_h * 0.28], fill=(0x1E, 0x18, 0x28, 255))
    return _down(img)


def icon_party():
    img = _canvas()
    d = ImageDraw.Draw(img)
    # Dos círculos idénticos solapados (mismo tono, mismo contorno) — el motivo estándar de
    # "grupo/compañeros" con una sola pieza repetida, no un glifo compuesto de piezas distintas
    # (ver la lección de Gender en AppearanceScreen sobre por qué eso falla a 20px).
    r = int(SIZE * 0.30)
    cy = SIZE // 2 + int(SIZE * 0.04)
    cx1 = SIZE // 2 - int(r * 0.55)
    cx2 = SIZE // 2 + int(r * 0.55)
    fill = (0xD9, 0xA2, 0x3A, 255)
    outline = (0x6E, 0x4A, 0x18, 255)
    _circle(d, cx1, cy, r, fill, outline=outline, width=1)
    _circle(d, cx2, cy, r, fill, outline=outline, width=1)
    return _down(img)


CELLS = [
    (0, 0, icon_overworld),
    (20, 0, icon_nether),
    (40, 0, icon_end),
    (60, 0, icon_otherworld),
    (80, 0, icon_unknown_dimension),
    (100, 0, icon_home),
    (120, 0, icon_kami_palace),
    (140, 0, icon_yemma_palace),
    (160, 0, icon_kaiosama_planet),
    (180, 0, icon_nether_portal),
    (200, 0, icon_end_spawn),
    (220, 0, icon_party),
]


def main():
    atlas = Image.open(ICONS_PATH).convert("RGBA")  # ABRE el atlas existente, no lo recrea
    for x, y, fn in CELLS:
        atlas.paste(fn(), (x, y))
    atlas.save(ICONS_PATH)
    print(f"Wrote row v=0 ({len(CELLS)} icons) into {ICONS_PATH}")


if __name__ == "__main__":
    main()
