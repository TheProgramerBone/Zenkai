#!/usr/bin/env python3
"""Genera las texturas del pipeline visual de ki (halo y estela) directamente en
src/main/resources/assets/zenkai/textures/entity/.

Ambas son BLANCAS con toda la información en el canal alfa: el tinte lo pone el color por
vértice (ver KiProjectileRenderer), así una sola textura sirve para las nueve técnicas y para
cualquier color que elija el jugador. Son degradados continuos a propósito — excepción a la
regla pixel-art del mod, igual que la hoja del aura.

Este script es la ÚNICA fuente de estas dos texturas. Antes hubo dos PNG distintos con el mismo
nombre y se perdió una ronda entera por eso; que solo exista este generador evita que se repita.
Ejecutar con: python tools/gen_ki_fx.py
"""

import math
import os

from PIL import Image

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "zenkai", "textures", "entity",
)

HALO_SIZE = 128
TRAIL_W = 64
TRAIL_H = 64


def smoothstep(edge0, edge1, x):
    t = min(1.0, max(0.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)


def gen_halo():
    """Degradado radial. alpha = pow(1 - r, 3.0), cero exacto en r >= 1.
    SIN realce en el centro: una versión anterior lo tenía y se leía como un disco duro en vez
    de como un desvanecido."""
    img = Image.new("RGBA", (HALO_SIZE, HALO_SIZE))
    cx = cy = (HALO_SIZE - 1) / 2.0
    max_r = HALO_SIZE / 2.0
    px = img.load()
    for y in range(HALO_SIZE):
        for x in range(HALO_SIZE):
            r = math.hypot(x - cx, y - cy) / max_r
            if r >= 1.0:
                alpha = 0.0
            else:
                alpha = (1.0 - r) ** 3.0
            a = round(alpha * 255)
            px[x, y] = (255, 255, 255, a)
    return img


def gen_trail_soft():
    """Campana en U a lo largo de U (a lo ancho de la cinta) y estriado suave en V (a lo largo,
    para que el desplazamiento de UV se lea como flujo).
    bell = pow(1 - smoothstep(0,1,|2u-1|), 1.35)
    stripe = 1 + 0.13*sin(2*pi*3*v) + 0.07*sin(2*pi*7*v)   (normalizado a promedio 1)
    Con más amplitud de la indicada aquí, la estela parpadea en vez de fluir."""
    img = Image.new("RGBA", (TRAIL_W, TRAIL_H))
    px = img.load()
    for yy in range(TRAIL_H):
        v = yy / (TRAIL_H - 1)
        stripe = 1.0 + 0.13 * math.sin(2 * math.pi * 3 * v) + 0.07 * math.sin(2 * math.pi * 7 * v)
        stripe = max(0.0, stripe)
        for xx in range(TRAIL_W):
            u = xx / (TRAIL_W - 1)
            bell = (1.0 - smoothstep(0.0, 1.0, abs(2 * u - 1))) ** 1.35
            alpha = max(0.0, min(1.0, bell * stripe))
            a = round(alpha * 255)
            px[xx, yy] = (255, 255, 255, a)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    halo_path = os.path.join(OUT_DIR, "ki_halo.png")
    gen_halo().save(halo_path)
    print(f"Escrito {halo_path} ({HALO_SIZE}x{HALO_SIZE})")

    trail_path = os.path.join(OUT_DIR, "ki_trail_soft.png")
    gen_trail_soft().save(trail_path)
    print(f"Escrito {trail_path} ({TRAIL_W}x{TRAIL_H})")


if __name__ == "__main__":
    main()
