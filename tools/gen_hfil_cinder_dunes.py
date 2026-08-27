"""Genera las dos texturas de ceniza de Cinder Dunes (Fase 4 del rework de biomas del HFIL, ver
.claude/pendiente/hfil-rework-propuesta.md secciones 3.3/5.3):

- assets/zenkai/textures/block/hfil_cinder_sand.png — capa de superficie de
  zenkai:hfil_cinder_dunes (antes hfil_dunes), sustituye a minecraft:red_sand en la
  surface_rule de otherworld_noise.json. Bloque que cae (ColoredFallingBlock), misma mecánica
  que la arena real.
- assets/zenkai/textures/block/hfil_cinder_sandstone.png — capa compactada debajo (sustituye
  a minecraft:red_sandstone), sólida, sin gravedad.

Antes de esta fase, hfil_dunes era literalmente red_sand/red_sandstone vainilla reskinneado
solo por el cielo/agua del bioma — el bioma "más prestado" de los 3 del HFIL, sin ningún
bloque propio (a diferencia de hfil_blood_shore/hfil_needle_wastes, que ya usan
HFIL_SCORCHED_STONE/HFIL_SPIKE_ROCK).

MISMO PRINCIPIO que gen_hfil_stones.py (no repetido aquí en detalle, ver ese script): recolor
HSV duotono de una textura vanilla real — se conserva el VALOR (grano/sombreado ya presente en
la textura de Mojang) y solo se fija matiz/saturación a un objetivo. Las dos parten de
red_sand/red_sandstone vainilla (ya tienen la mecánica de arena/arenisca correcta, y
red_sandstone ya tiene un patrón tallado agradable de superficie uniforme) empujadas hacia un
gris-ceniza cálido y apagado en vez del naranja saturado de arena de desierto.

Requiere que el proyecto se haya construido al menos una vez con NeoGradle (ver
gen_healing_water_bucket.py para la ruta de caché exacta).

Ejecutar: python tools/gen_hfil_cinder_dunes.py
"""

import colorsys
import glob
import io
import os
import zipfile

from PIL import Image

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURE_DIR = os.path.join(
    REPO_ROOT, "src", "main", "resources", "assets", "zenkai", "textures", "block")

CINDER_SAND_OUT = os.path.join(TEXTURE_DIR, "hfil_cinder_sand.png")
CINDER_SANDSTONE_OUT = os.path.join(TEXTURE_DIR, "hfil_cinder_sandstone.png")


def find_client_jar() -> str:
    pattern = os.path.expanduser(
        "~/.gradle/caches/neoformruntime/artifacts/minecraft_*_client.jar")
    matches = glob.glob(pattern)
    if not matches:
        raise FileNotFoundError(
            "No se encontró minecraft_*_client.jar en la caché de neoform-runtime. "
            "Ejecuta ./gradlew build (o runClient) al menos una vez antes de correr este script.")
    return matches[0]


def load_vanilla_texture(name: str) -> Image.Image:
    with zipfile.ZipFile(find_client_jar()) as jar:
        data = jar.read(f"assets/minecraft/textures/block/{name}.png")
    return Image.open(io.BytesIO(data)).convert("RGBA")


def recolor(img: Image.Image, hue: float, sat: float, val_scale: float = 1.0) -> Image.Image:
    """Recolor 'duotono': conserva el VALOR (brillo) de cada píxel — o sea el grano y el
    sombreado ya presentes en la textura original — y fija matiz/saturación a un objetivo
    uniforme. hue en grados [0, 360). sat en [0, 1]. val_scale multiplica el brillo antes
    de fijar el nuevo color (para oscurecer/aclarar sin perder el contraste relativo)."""
    out = img.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            _, _, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            v = min(1.0, max(0.0, v * val_scale))
            nr, ng, nb = colorsys.hsv_to_rgb(hue / 360.0, sat, v)
            pixels[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def main():
    os.makedirs(TEXTURE_DIR, exist_ok=True)

    red_sand = load_vanilla_texture("red_sand")
    # H≈28 (naranja-pardo muy apagado, tira a gris-ceniza cálido en vez de naranja de desierto),
    # S=0.08 (el red_sand vainilla promedia S≈0.55 — bajado mucho, casi gris), val_scale=0.85
    # (ligeramente más oscuro que el original, "ceniza" en vez de "arena limpia").
    cinder_sand = recolor(red_sand, hue=28.0, sat=0.08, val_scale=0.85)
    cinder_sand.save(CINDER_SAND_OUT)
    print(f"Escrito {CINDER_SAND_OUT}")

    red_sandstone = load_vanilla_texture("red_sandstone")
    # Mismo matiz que cinder_sand (misma familia), S=0.06 (aún más neutro — la capa compactada
    # se lee más mineral/gris que la superficie), val_scale=0.62 (bastante más oscura: ceniza
    # compactada, no arenisca limpia).
    cinder_sandstone = recolor(red_sandstone, hue=28.0, sat=0.06, val_scale=0.62)
    cinder_sandstone.save(CINDER_SANDSTONE_OUT)
    print(f"Escrito {CINDER_SANDSTONE_OUT}")


if __name__ == "__main__":
    main()
