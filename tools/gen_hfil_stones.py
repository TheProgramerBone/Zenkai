"""Genera las dos texturas de piedra nuevas del rediseño del HFIL (ver
.claude/pendiente/hfil-infierno-rediseno.md, fase 1):

- assets/zenkai/textures/block/hfil_scorched_stone.png — roca base CÁLIDA (negruzca/
  rojiza), nuevo default_block del Otherworld (sustituye a minecraft:stone en
  worldgen/noise_settings/otherworld_noise.json). Arregla de paso la "mancha de piedra"
  documentada en CLAUDE.md: si algo se escapa del gate above_preliminary_surface, lo que
  se ve deja de ser piedra gris vainilla fuera de lugar.
- assets/zenkai/textures/block/hfil_spike_rock.png — roca FRÍA (azul-grisácea/violeta),
  usada solo por las formaciones de pinchos (HfilSpikeFeature) — el contraste frío/cálido
  es justo lo que se ve en las 4 imágenes de referencia de la sesión de diseño (siluetas
  de pinchos azul-violeta contra cielo rojo/rosa).

POR QUÉ PARTIR DE TEXTURAS VANILLA EN VEZ DE DIBUJAR A MANO (mismo principio que
gen_healing_water_bucket.py): la fuente de vainilla ya trae el grano/sombreado de roca
correcto a nivel de pixel art de Mojang. Se recolorea por HSV: el VALOR (brillo, o sea el
grano/sombreado) de cada píxel se conserva y solo se reemplazan matiz y saturación por un
objetivo fijo — un recolor "duotono" que no inventa sombreado nuevo.

- hfil_scorched_stone parte de minecraft:netherrack (roca cálida, moteada, rojiza — ya es
  literalmente "piedra del inframundo" en vainilla) oscurecida para leerse como calcinada,
  no como carne cruda.
- hfil_spike_rock parte de minecraft:basalt_side (grano vertical de columna, ya pensado
  para pilares/pinchos en vainilla — basalt deltas) con la saturación EMPUJADA hacia un
  azul-violeta perceptible (el basalto vainilla es casi gris neutro, S≈0.07 de media —
  demasiado bajo para leerse como "frío" a simple vista).

Requiere que el proyecto se haya construido al menos una vez con NeoGradle (ver
gen_healing_water_bucket.py para la ruta de caché exacta).

Ejecutar: python tools/gen_hfil_stones.py
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

SCORCHED_STONE_OUT = os.path.join(TEXTURE_DIR, "hfil_scorched_stone.png")
SPIKE_ROCK_OUT = os.path.join(TEXTURE_DIR, "hfil_spike_rock.png")


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

    netherrack = load_vanilla_texture("netherrack")
    # H≈355 (rojo, ligerísimo hacia el magenta para no ser idéntico al netherrack real),
    # S=0.5 (netherrack vainilla promedia S≈0.61 — se baja un poco: más ceniza, menos
    # carne cruda), val_scale=0.72 (más oscuro que el netherrack original, "calcinado").
    scorched = recolor(netherrack, hue=355.0, sat=0.5, val_scale=0.72)
    scorched.save(SCORCHED_STONE_OUT)
    print(f"Escrito {SCORCHED_STONE_OUT}")

    basalt = load_vanilla_texture("basalt_side")
    # H≈252 (azul-violeta), S=0.20 (el basalto vainilla promedia S≈0.07 — casi gris neutro,
    # se empuja bastante para que el tinte frío se lea a simple vista), val_scale=0.9
    # (ligeramente más oscuro que el basalto original).
    spike = recolor(basalt, hue=252.0, sat=0.20, val_scale=0.9)
    spike.save(SPIKE_ROCK_OUT)
    print(f"Escrito {SPIKE_ROCK_OUT}")


if __name__ == "__main__":
    main()
