#!/usr/bin/env python3
"""Genera PLACEHOLDERS sintéticos para los 8 eventos de sonido `ki_attack_charge_1..4` /
`ki_attack_release_1..4` (ver `.claude/pendiente/audio-faltante.md`) — hoy registrados en
`ModSounds`/`sounds.json` y ya seleccionables por el jugador en `TechniqueEditScreen`
(`TechniqueAssets.chargeSounds()`/`releaseSounds()`, descubiertos por prefijo), pero sin ningún
`.ogg` real detrás: cargar/soltar un ki blast suena a silencio hoy.

NO son audio final — son procedimiento puro (sin muestras grabadas ni samples externos), pensados
para quitar el silencio YA y dar algo con carácter mientras no exista grabación real. Reemplazar
este script (o su salida) en cuanto haya audio de verdad; el nombre de archivo y el evento no
cambian, así que no hace falta tocar `sounds.json`/`ModSounds` cuando eso pase.

DEPENDENCIAS: numpy + `soundfile` (ya en el entorno, envuelve libsndfile — escribe Ogg/Vorbis
real sin necesitar ffmpeg; el mismo paquete que ya usa `tools/gen_meditation_chart.py` para leer).

DISEÑO DE SONIDO (referencia: `ki_charge_start.ogg`/`detransform.ogg` existentes, 48kHz estéreo,
~1.5-1.6s los de un solo disparo — aquí más cortos a propósito, son el "chasquido" de cargar/
soltar, no el bucle sostenido que ya cubre `ki_charge_loop.ogg`):

  - `ki_attack_charge_N` (~0.55-0.75s): barrido ascendente (sube de tono) + un lecho de ruido
    filtrado tipo "whoosh" que crece con la envolvente — la sensación de energía acumulándose.
    Termina con un golpe de armónico agudo breve (el "listo para disparar").
  - `ki_attack_release_N` (~0.35-0.50s): un transitorio de ataque instantáneo (clic/crack de
    ruido de banda ancha) seguido de un barrido DESCENDENTE rápido con caída exponencial — la
    sensación de soltar toda la energía de golpe.

Las 4 variantes de cada uno cambian tono base, cantidad de ruido/aspereza y textura armónica
(seno puro vs. diente de sierra suavizado) para que se sientan como opciones DISTINTAS en el
selector del editor, no el mismo sonido con volumen distinto — 1 es la más limpia/aguda, 4 la
más grave/áspera, con una progresión de intensidad entre medias.

Uso: `python3 tools/gen_ki_attack_sfx.py` (sin argumentos, sobrescribe los 8 archivos en
`src/main/resources/assets/zenkai/sounds/`).
"""
import numpy as np
import soundfile as sf

SR = 48000
OUT_DIR = "src/main/resources/assets/zenkai/sounds"

rng = np.random.default_rng(20260909)  # semilla fija: salida reproducible entre corridas


def envelope_attack_sustain_release(n, attack, release):
    """Fade-in lineal corto, meseta a 1.0, fade-out exponencial al final."""
    env = np.ones(n)
    a = max(1, int(attack * n))
    r = max(1, int(release * n))
    env[:a] = np.linspace(0.0, 1.0, a)
    tail = np.linspace(0.0, 1.0, r)
    env[n - r:] *= np.exp(-4.0 * (1.0 - tail))
    return env


def band_noise(n, lo_hz, hi_hz, sr=SR):
    """Ruido blanco filtrado a una banda [lo_hz, hi_hz] vía FFT (sin dependencias de filtros)."""
    white = rng.standard_normal(n)
    spec = np.fft.rfft(white)
    freqs = np.fft.rfftfreq(n, d=1.0 / sr)
    mask = (freqs >= lo_hz) & (freqs <= hi_hz)
    spec[~mask] = 0.0
    out = np.fft.irfft(spec, n)
    peak = np.max(np.abs(out)) or 1.0
    return out / peak


def charge_tone(n, f0, f1, harmonic_mix, sr=SR):
    """Barrido ascendente f0->f1 (exponencial, se siente más natural que lineal), con un
    segundo armónico mezclado (harmonic_mix = 0 puro seno, 1 casi diente de sierra)."""
    t = np.arange(n) / sr
    dur = n / sr
    # Frecuencia instantánea exponencial: integrar para la fase real (si no, el barrido "salta").
    k = np.log(f1 / f0) / dur
    freq_t = f0 * np.exp(k * t)
    phase = 2 * np.pi * f0 * (np.exp(k * t) - 1) / k
    fundamental = np.sin(phase)
    second = np.sin(2 * phase) * 0.5
    third = np.sin(3 * phase) * 0.25
    tone = fundamental + harmonic_mix * (second + third)
    return tone / np.max(np.abs(tone))


def release_tone(n, f0, f1, harmonic_mix, sr=SR):
    """Barrido DESCENDENTE rápido f0->f1 (f1 < f0), misma construcción que charge_tone."""
    return charge_tone(n, f0, f1, harmonic_mix, sr=sr)


def stereo_widen(mono, spread=0.006, sr=SR):
    """Estéreo simple: copia con un desfase de unos ms entre canales (sin reverb real)."""
    delay = max(1, int(spread * sr))
    left = mono
    right = np.concatenate([np.zeros(delay), mono])[: len(mono)]
    return np.stack([left, right], axis=1)


def make_charge(n_id, f0, f1, harmonic_mix, noise_amt, dur):
    n = int(dur * SR)
    tone = charge_tone(n, f0, f1, harmonic_mix)
    noise = band_noise(n, f0 * 0.6, f1 * 1.3) * noise_amt
    # El ruido crece con el tiempo (whoosh que se arma), el tono domina al final (golpe agudo).
    noise_env = np.linspace(0.2, 1.0, n) ** 1.5
    sig = tone * 0.85 + noise * noise_env
    env = envelope_attack_sustain_release(n, attack=0.08, release=0.22)
    sig *= env
    sig /= np.max(np.abs(sig)) or 1.0
    sig *= 0.85
    return stereo_widen(sig)


def make_release(n_id, f0, f1, harmonic_mix, noise_amt, dur):
    n = int(dur * SR)
    tone = release_tone(n, f0, f1, harmonic_mix)
    # Transitorio: una ráfaga de ruido de banda ancha SOLO en los primeros ~12ms (el "crack").
    crack_n = max(1, int(0.012 * SR))
    crack = np.zeros(n)
    crack[:crack_n] = band_noise(crack_n, 800, 12000) * np.linspace(1.0, 0.0, crack_n)
    sustain_noise = band_noise(n, f1 * 0.5, f0 * 1.1) * noise_amt
    sig = tone * 0.9 + crack * 1.1 + sustain_noise * np.linspace(1.0, 0.1, n)
    env = envelope_attack_sustain_release(n, attack=0.01, release=0.55)
    sig *= env
    sig /= np.max(np.abs(sig)) or 1.0
    sig *= 0.9
    return stereo_widen(sig)


# (f0, f1, harmonic_mix, noise_amt, duración) por variante 1..4 — progresión limpia -> grave/áspera.
CHARGE_PARAMS = [
    (420, 1450, 0.10, 0.18, 0.58),  # 1: limpio, agudo, casi sin ruido
    (360, 1150, 0.30, 0.28, 0.64),  # 2: un poco más grueso
    (300,  950, 0.55, 0.40, 0.70),  # 3: áspero, más ruido de fondo
    (230,  760, 0.80, 0.55, 0.75),  # 4: grave y sucio, el más "pesado"
]
RELEASE_PARAMS = [
    (1900, 500, 0.10, 0.15, 0.36),  # 1: pew limpio y agudo
    (1550, 420, 0.30, 0.22, 0.40),
    (1250, 340, 0.55, 0.32, 0.45),
    (1000, 260, 0.80, 0.42, 0.50),  # 4: grave, denso
]

for i, (f0, f1, hm, na, dur) in enumerate(CHARGE_PARAMS, start=1):
    audio = make_charge(i, f0, f1, hm, na, dur)
    sf.write(f"{OUT_DIR}/ki_attack_charge_{i}.ogg", audio, SR, format="OGG", subtype="VORBIS")
    print(f"ki_attack_charge_{i}.ogg  {dur:.2f}s")

for i, (f0, f1, hm, na, dur) in enumerate(RELEASE_PARAMS, start=1):
    audio = make_release(i, f0, f1, hm, na, dur)
    sf.write(f"{OUT_DIR}/ki_attack_release_{i}.ogg", audio, SR, format="OGG", subtype="VORBIS")
    print(f"ki_attack_release_{i}.ogg  {dur:.2f}s")

print("Listo — 8 placeholders escritos. Reemplazar por audio real cuando exista.")
