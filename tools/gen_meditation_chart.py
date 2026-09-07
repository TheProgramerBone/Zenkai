#!/usr/bin/env python3
"""Genera un BORRADOR de chart de Meditation (carriles A/S/D/F) a partir de un disco de música
VANILLA de Minecraft, detectando "onsets" (golpes/ataques de sonido) en el .ogg real del disco.

Parte de la decisión de diseño "híbrida" para Meditation modo Canción (ver el plan de la ronda de
pulido de Training, sección Pista F): este script cubre la mitad AUTOMÁTICA — el resultado es un
PRIMER BORRADOR, no un chart terminado. Cada canción generada aquí se debe jugar en el minijuego y
afinar a mano (mover `t`, cambiar `lane`, borrar/añadir notas sueltas) antes de darla por buena;
el JSON de salida es texto plano editable a propósito, no un formato binario.

CÓMO CONSIGUE EL AUDIO: el mod NUNCA distribuye el .ogg del disco (es asset de Mojang, protegido) —
este script solo lo LEE de la carpeta `assets/` de una instalación vanilla de Minecraft ya presente
en la máquina de quien ejecuta el script (launcher oficial), localizando el hash del objeto vía el
índice de assets (`assets/indexes/*.json`). El propio juego, en producción, reproduce el disco con
`SoundEvents.MUSIC_DISC_*` normal — este script es una herramienta de desarrollo offline, el .ogg
nunca se copia al repositorio ni a `src/main/resources`.

DEPENDENCIAS: numpy, scipy (ya en el entorno) + `soundfile` (se instaló para este propósito con
`pip install soundfile` — envuelve libsndfile, que sabe leer Ogg/Vorbis sin necesitar ffmpeg).

Algoritmo (sin librosa, deliberadamente ligero):
  1. STFT (ventana 40ms, hop 10ms) sobre el audio mono.
  2. "Spectral flux" (suma de incrementos de magnitud entre frames consecutivos, rectificado a
     positivo) como envolvente de onset.
  3. Pico local por encima de un umbral adaptativo (media + k*desviación en una ventana deslizante
     de ~300ms) con una separación mínima de 150ms entre onsets, para no disparar dos veces el
     mismo golpe.
  4. Cada onset se asigna a UN carril (0-3) según qué banda de frecuencia (grave/medio-grave/
     medio-agudo/agudo) aportó más energía a ese golpe — variedad musicalmente razonable sin
     necesitar detección de tono real. Si el carril elegido tuvo una nota hace menos de 220ms
     (ventana de acierto del juego, ver MeditationScreen.HIT_WINDOW), se prueba la siguiente banda
     en orden de energía para evitar notas imposibles de separar en el mismo carril.

Uso:
    python tools/gen_meditation_chart.py pigstep
    python tools/gen_meditation_chart.py cat --assets-dir "D:/otra/ruta/.minecraft/assets"
    python tools/gen_meditation_chart.py pigstep --ogg "C:/ya/tengo/pigstep.ogg"  (salta la búsqueda)

Salida: src/main/resources/assets/zenkai/meditation_charts/<disco>.json
"""

import argparse
import json
import os
import shutil
import sys
import tempfile

import numpy as np
from scipy.signal import stft

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHARTS_DIR = os.path.join(
    REPO_ROOT, "src", "main", "resources", "assets", "zenkai", "meditation_charts",
)

DEFAULT_ASSETS_DIR = os.path.join(
    os.environ.get("APPDATA", ""), ".minecraft", "assets",
)

LANES = 4
# Bordes de banda en Hz para el "voting" de carril por onset — grave/medio-grave/medio-agudo/agudo.
BAND_EDGES = [0, 200, 800, 3000, 20000]

MIN_ONSET_GAP_S = 0.15   # separación mínima entre dos onsets cualesquiera
MIN_SAME_LANE_GAP_S = 0.22  # ~HIT_WINDOW*TRAVEL_MS*2 de MeditationScreen; evita notas imposibles
ADAPTIVE_WINDOW_S = 0.30
ADAPTIVE_K = 1.5


def find_disc_ogg(disc_id, assets_dir):
    """Busca `minecraft/sounds/records/<disc_id>.ogg` en los índices de assets de una instalación
    vanilla y devuelve la ruta al objeto hasheado real. Prueba todos los índices presentes (hay
    uno por versión de Minecraft jugada) y se queda con el primero cuyo objeto exista en disco."""
    indexes_dir = os.path.join(assets_dir, "indexes")
    objects_dir = os.path.join(assets_dir, "objects")
    if not os.path.isdir(indexes_dir) or not os.path.isdir(objects_dir):
        raise FileNotFoundError(
            f"No se encontró una carpeta de assets vanilla válida en {assets_dir} "
            "(se esperaban las subcarpetas indexes/ y objects/). Usa --ogg para saltar la "
            "búsqueda si ya tienes el archivo en otro sitio."
        )

    key = f"minecraft/sounds/records/{disc_id}.ogg"
    for name in sorted(os.listdir(indexes_dir), reverse=True):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(indexes_dir, name), encoding="utf-8") as fh:
            index = json.load(fh)
        entry = index.get("objects", {}).get(key)
        if entry is None:
            continue
        h = entry["hash"]
        obj_path = os.path.join(objects_dir, h[:2], h)
        if os.path.isfile(obj_path):
            return obj_path
    raise FileNotFoundError(
        f"'{key}' no aparece en ningún índice de {indexes_dir} con su objeto presente en "
        f"{objects_dir}. Abre ese disco al menos una vez en el launcher vanilla (o en un perfil "
        "que lo precargue) para que el asset se descargue, o pasa --ogg directamente."
    )


def load_mono(ogg_path):
    """soundfile necesita ver la extensión .ogg para reconocer el formato — los objetos del
    launcher vanilla están guardados por hash, sin extensión, así que se copia a un temporal antes
    de leer."""
    import soundfile as sf

    with tempfile.TemporaryDirectory() as tmp:
        tmp_ogg = os.path.join(tmp, "disc.ogg")
        shutil.copyfile(ogg_path, tmp_ogg)
        data, sr = sf.read(tmp_ogg, always_2d=False)
    if data.ndim > 1:
        data = data.mean(axis=1)
    return data.astype(np.float64), sr


def detect_onsets(data, sr):
    nper = int(sr * 0.04)
    nhop = int(sr * 0.01)
    freqs, times, Z = stft(data, fs=sr, nperseg=nper, noverlap=nper - nhop)
    mag = np.abs(Z)

    diff = np.diff(mag, axis=1)
    diff[diff < 0] = 0
    flux = np.zeros(mag.shape[1])
    flux[1:] = diff.sum(axis=0)
    flux = flux / (flux.max() + 1e-9)

    win = max(1, int(ADAPTIVE_WINDOW_S / (nhop / sr)))
    padded = np.pad(flux, (win, win), mode="edge")
    thresh = np.array([
        padded[i:i + 2 * win + 1].mean() + ADAPTIVE_K * padded[i:i + 2 * win + 1].std()
        for i in range(len(flux))
    ])

    min_gap_frames = max(1, int(MIN_ONSET_GAP_S / (nhop / sr)))
    onset_frames = []
    last = -min_gap_frames
    for i in range(1, len(flux) - 1):
        if (flux[i] > thresh[i] and flux[i] >= flux[i - 1] and flux[i] >= flux[i + 1]
                and flux[i] > 0.05 and i - last >= min_gap_frames):
            onset_frames.append(i)
            last = i

    # Energía por banda en cada frame de onset, para el voting de carril.
    band_energy = np.zeros((len(BAND_EDGES) - 1, mag.shape[1]))
    for b in range(len(BAND_EDGES) - 1):
        lo, hi = BAND_EDGES[b], BAND_EDGES[b + 1]
        sel = (freqs >= lo) & (freqs < hi)
        band_energy[b] = mag[sel].sum(axis=0) if sel.any() else 0.0

    return times[onset_frames], band_energy[:, onset_frames]


def assign_lanes(onset_times, band_energy):
    """Reparte cada onset en un carril según la banda dominante; si ese carril tuvo una nota
    hace menos de MIN_SAME_LANE_GAP_S, prueba la siguiente banda por energía antes de forzar el
    carril usado hace más tiempo (evita huecos imposibles de tocar en el mismo carril)."""
    last_used = [-1e9] * LANES
    lanes = []
    for i, t in enumerate(onset_times):
        order = np.argsort(-band_energy[:, i])
        chosen = None
        for band in order:
            lane = int(band) % LANES
            if t - last_used[lane] >= MIN_SAME_LANE_GAP_S:
                chosen = lane
                break
        if chosen is None:
            chosen = int(np.argmin(last_used))
        last_used[chosen] = t
        lanes.append(chosen)
    return lanes


def difficulty_hint(notes_per_sec):
    if notes_per_sec < 2.0:
        return "easy"
    if notes_per_sec < 3.5:
        return "medium"
    return "hard"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("disc_id", help="id del disco vanilla, ej. pigstep, cat, mellohi, otherside")
    ap.add_argument("--assets-dir", default=DEFAULT_ASSETS_DIR,
                     help=f"carpeta assets/ de una instalación vanilla (default: {DEFAULT_ASSETS_DIR})")
    ap.add_argument("--ogg", default=None, help="ruta directa a un .ogg, salta la búsqueda por hash")
    args = ap.parse_args()

    ogg_path = args.ogg or find_disc_ogg(args.disc_id, args.assets_dir)
    print(f"Leyendo {ogg_path}")
    data, sr = load_mono(ogg_path)
    duration_s = len(data) / sr

    onset_times, band_energy = detect_onsets(data, sr)
    lanes = assign_lanes(onset_times, band_energy)

    notes = [{"t": int(round(t * 1000)), "lane": lane} for t, lane in zip(onset_times, lanes)]
    nps = len(notes) / duration_s if duration_s > 0 else 0.0

    chart = {
        "discId": args.disc_id,
        "durationMs": int(round(duration_s * 1000)),
        "lanes": LANES,
        "difficultyHint": difficulty_hint(nps),
        "notes": notes,
    }

    os.makedirs(CHARTS_DIR, exist_ok=True)
    out_path = os.path.join(CHARTS_DIR, f"{args.disc_id}.json")
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(chart, fh, indent=2)

    print(f"{len(notes)} notas, {duration_s:.1f}s, {nps:.2f} notas/s "
          f"(difficultyHint={chart['difficultyHint']})")
    print(f"Escrito {out_path}")
    print("BORRADOR — juégalo en el minijuego y ajusta t/lane a mano antes de darlo por bueno.")


if __name__ == "__main__":
    sys.exit(main())
