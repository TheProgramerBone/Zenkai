"""Genera assets/zenkai/textures/item/healing_water_bucket.png a partir del
water_bucket.png vanilla, recoloreando SOLO los píxeles del líquido (deja intacto
el metal gris/blanco del cubo).

POR QUÉ PARTIR DEL VANILLA EN VEZ DE DIBUJAR A MANO: el bucket de vainilla ya
tiene la silueta y el sombreado (5 tonos de la cara superior/lateral del
líquido) correctos a nivel de pixel art de Mojang — reinventar esa silueta a
mano solo introduciría inconsistencias de estilo. La única fuente de verdad de
CUÁLES son los píxeles de líquido es la propia textura: son los únicos con
R, G y B distintos entre sí (el metal del cubo es siempre gris neutro o
blanco, R==G==B). Recolorear por HSV conservando S y V y fijando solo H
preserva el sombreado ya existente (mismo principio que el resto de
tools/gen_*.py: nunca introducir un degradado nuevo donde el original no
tenía uno).

Requiere que el proyecto se haya construido al menos una vez con NeoGradle:
lee el jar de cliente sin ofuscar desde la caché de neoform-runtime
(~/.gradle/caches/neoformruntime/artifacts/minecraft_<version>_client.jar,
sin hash en el nombre — a diferencia de modules-2, esta ruta es estable).

Ejecutar: python tools/gen_healing_water_bucket.py
"""

import colorsys
import glob
import io
import os
import zipfile

from PIL import Image

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_PATH = os.path.join(
    REPO_ROOT, "src", "main", "resources", "assets", "zenkai",
    "textures", "item", "healing_water_bucket.png")

# Tinte curativo verde-cian, mismo objetivo de color que el tinte de fluido en
# HealingWaterFluidClientExtensions (0xFF3FE0B0) para que cubo e icono combinen.
TARGET_HUE = 150.0 / 360.0


def find_client_jar() -> str:
    pattern = os.path.expanduser(
        "~/.gradle/caches/neoformruntime/artifacts/minecraft_*_client.jar")
    matches = glob.glob(pattern)
    if not matches:
        raise FileNotFoundError(
            "No se encontró minecraft_*_client.jar en la caché de neoform-runtime. "
            "Ejecuta ./gradlew build (o runClient) al menos una vez antes de correr este script.")
    return matches[0]


def load_vanilla_water_bucket() -> Image.Image:
    with zipfile.ZipFile(find_client_jar()) as jar:
        data = jar.read("assets/minecraft/textures/item/water_bucket.png")
    return Image.open(io.BytesIO(data)).convert("RGBA")


def recolor_liquid(img: Image.Image) -> Image.Image:
    out = img.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            if r == g == b:
                continue  # metal gris/blanco del cubo: intacto
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            nr, ng, nb = colorsys.hsv_to_rgb(TARGET_HUE, s, v)
            pixels[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def main():
    vanilla = load_vanilla_water_bucket()
    healing = recolor_liquid(vanilla)
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    healing.save(OUTPUT_PATH)
    print(f"Escrito {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
