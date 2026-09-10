#!/usr/bin/env python3
"""Genera charts BORRADOR de Meditation para TODOS los discos de música vanilla de una sola vez,
en vez de invocar `gen_meditation_chart.py <id>` uno por uno — pedido explícito del usuario
("agregar más canciones... o generar un código en python para que los haga automáticamente").

NO duplica el algoritmo de detección de onsets: importa las funciones de gen_meditation_chart.py
(find_disc_ogg/load_mono/detect_onsets/assign_lanes/difficulty_hint/CHARTS_DIR/LANES/
DEFAULT_ASSETS_DIR) y las llama una vez por disco — un solo sitio donde vive el algoritmo.

Sigue siendo la mitad AUTOMÁTICA del proceso "híbrido" documentado en gen_meditation_chart.py:
el resultado de cada disco es un PRIMER BORRADOR, no un chart terminado. Añadir un disco nuevo al
selector de Meditation (MeditationScreen) requiere ADEMÁS una entrada en
client/training/CuratedSong.java — este script solo escribe los JSON de chart, nunca toca Java.

Uso:
    python tools/gen_all_meditation_charts.py
    python tools/gen_all_meditation_charts.py --assets-dir "D:/otra/ruta/.minecraft/assets"
    python tools/gen_all_meditation_charts.py --only pigstep otherside   (solo esos IDs)
"""

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_meditation_chart as gmc  # noqa: E402  (import tras el sys.path.insert, a propósito)

# IDs de TODOS los discos de música vanilla presentes en la versión pinneada del mod
# (gradle.properties: minecraft_version=1.21.1) — 19 discos, de "13" (1.9) a "precipice" (1.21).
# Si el mod sube de versión de Minecraft y Mojang añade discos nuevos, esta lista es el único
# sitio que hay que ampliar para que este script los recoja.
ALL_DISC_IDS = [
    "13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward",
    "11", "wait", "pigstep", "otherside", "5", "relic", "creator", "creator_music_box", "precipice",
]


def generate_one(disc_id, assets_dir):
    """Mismo cuerpo que gen_meditation_chart.main(), sin el argparse/print de un disco suelto."""
    ogg_path = gmc.find_disc_ogg(disc_id, assets_dir)
    data, sr = gmc.load_mono(ogg_path)
    duration_s = len(data) / sr

    onset_times, band_energy = gmc.detect_onsets(data, sr)
    lanes = gmc.assign_lanes(onset_times, band_energy)

    notes = [{"t": int(round(t * 1000)), "lane": lane} for t, lane in zip(onset_times, lanes)]
    nps = len(notes) / duration_s if duration_s > 0 else 0.0

    chart = {
        "discId": disc_id,
        "durationMs": int(round(duration_s * 1000)),
        "lanes": gmc.LANES,
        "difficultyHint": gmc.difficulty_hint(nps),
        "notes": notes,
    }

    os.makedirs(gmc.CHARTS_DIR, exist_ok=True)
    out_path = os.path.join(gmc.CHARTS_DIR, f"{disc_id}.json")
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(chart, fh, indent=2)

    return len(notes), duration_s, nps, chart["difficultyHint"]


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--assets-dir", default=gmc.DEFAULT_ASSETS_DIR,
                     help=f"carpeta assets/ de una instalación vanilla (default: {gmc.DEFAULT_ASSETS_DIR})")
    ap.add_argument("--only", nargs="*", default=None,
                     help="generar solo estos IDs en vez de la lista completa de 19 discos")
    args = ap.parse_args()

    ids = args.only if args.only else ALL_DISC_IDS
    ok, failed = [], []
    for disc_id in ids:
        try:
            n, dur, nps, hint = generate_one(disc_id, args.assets_dir)
            print(f"OK   {disc_id:20s} {n:4d} notas, {dur:6.1f}s, {nps:.2f} notas/s (hint={hint})")
            ok.append(disc_id)
        except Exception as ex:
            print(f"FAIL {disc_id:20s} {ex}")
            failed.append(disc_id)

    print()
    print(f"{len(ok)}/{len(ids)} generados.")
    if failed:
        print("Fallidos (disco no encontrado en la instalación vanilla local — abrirlo una vez "
              "en el launcher, o pasar --assets-dir):", ", ".join(failed))
    print("BORRADORES — cada uno sigue necesitando jugarse y afinar t/lane a mano antes de darlo "
          "por bueno (ver el javadoc de gen_meditation_chart.py). Añadir la entrada "
          "correspondiente en CuratedSong.java para que aparezca en el selector del juego.")


if __name__ == "__main__":
    sys.exit(main())
