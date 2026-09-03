#!/usr/bin/env python3
"""Genera las texturas PROPIAS de los emisores de partículas del aura que no reutilizan
la hoja compartida de faldones (aura_flame_*.png): el ascua de fuego de SSJG
(AuraModifier.fireEmbers, AuraEmberRenderer) y el segmento de rayo de SSJ Rose
(AuraModifier.electricSparks, AuraSparkRenderer.drawJagged).

Escribe directamente en src/main/resources/assets/zenkai/textures/entity/.

BLANCAS con toda la información en el canal alfa, igual que aura_flame_*.png y las tres
texturas de gen_ki_fx.py: el tinte lo pone el color por vértice, así una sola textura
sirve para cualquier aura_type que active el flag. Degradados continuos a propósito —
misma excepción a la regla pixel-art del mod que ya documenta gen_ki_fx.py.

Reemplazan (2026-09-02) dos intentos previos que no funcionaron para SSJG: un pase
aditivo sobre el propio cono del aura (lavaba al jugador a blanco) y partículas vanilla
ParticleTypes.FLAME (se veían fuera de lugar contra el estilo del aura). Y, para Rose, el
rayo dejó de reutilizar el cuadrante de "penacho" de la hoja de llamas — geometría de
llama, no de rayo, aunque sirviera de placeholder aceptable.

Ejecutar con: python tools/gen_aura_particles.py
"""

import math
import os

from PIL import Image

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "entity",
)

EMBER_SIZE = 64
RAYO_W, RAYO_H = 32, 128


def smoothstep(edge0, edge1, x):
    t = min(1.0, max(0.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)


def gen_ember():
    """Ascua de fuego: gota alargada, más ancha por debajo y afilada hacia arriba (como
    una lengua de llama pequeña despegándose), degradado radial suave sin borde duro.
    AuraEmberRenderer la dibuja subiendo con leve deriva lateral."""
    img = Image.new("RGBA", (EMBER_SIZE, EMBER_SIZE))
    px = img.load()
    cx = (EMBER_SIZE - 1) / 2.0
    cy = EMBER_SIZE * 0.60  # centro del bulbo, algo por debajo de la mitad
    for y in range(EMBER_SIZE):
        v = y / (EMBER_SIZE - 1)
        # 1.0 en la base (v alto = abajo en textura, que es "y" chico en el quad al
        # revés de cómo se ve en pantalla no importa: solo es la forma), se afina
        # según sube.
        taper = 1.0 - smoothstep(0.15, 0.95, v) * 0.60
        for x in range(EMBER_SIZE):
            dx = (x - cx) / (EMBER_SIZE * 0.5 * max(0.12, taper))
            dy = (y - cy) / (EMBER_SIZE * 0.62)
            r = math.hypot(dx, dy)
            alpha = max(0.0, 1.0 - r) ** 2.0
            a = round(min(1.0, alpha) * 255)
            px[x, y] = (255, 255, 255, a)
    return img


def gen_rayo():
    """Segmento de rayo: núcleo brillante estrecho que se afina en las dos puntas, con
    brillo lateral suave. AuraSparkRenderer.drawJagged encadena 3 copias de ESTE mismo
    frame con quiebro de rotación/offset alternado — el quiebro lo da la geometría, no
    la textura, así que un solo segmento recto basta."""
    img = Image.new("RGBA", (RAYO_W, RAYO_H))
    px = img.load()
    cx = (RAYO_W - 1) / 2.0
    for y in range(RAYO_H):
        v = y / (RAYO_H - 1)
        taper = math.sin(v * math.pi) ** 0.55  # 0 en las puntas, 1 en el centro
        for x in range(RAYO_W):
            dx = abs(x - cx) / cx
            core = max(0.0, 1.0 - dx) ** 1.7
            alpha = core * taper
            a = round(min(1.0, alpha) * 255)
            px[x, y] = (255, 255, 255, a)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    gen_ember().save(os.path.join(OUT_DIR, "aura_ember.png"))
    gen_rayo().save(os.path.join(OUT_DIR, "aura_rayo.png"))
    print(f"Escritas aura_ember.png ({EMBER_SIZE}x{EMBER_SIZE}) y "
          f"aura_rayo.png ({RAYO_W}x{RAYO_H}) en {OUT_DIR}")


if __name__ == "__main__":
    main()
