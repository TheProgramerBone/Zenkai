#!/usr/bin/env python3
"""Genera un PLACEHOLDER sintético para el evento de sonido `space_pod_launch` (ver
`SpacePodEntity.beginLaunch` y `.claude/pendiente/nave-espacial-menu-galactico.md`) — pedido
explícito del usuario tras probar el menú galáctico en juego: "estaría chévere que hubiera un
sonido para indicar el despegue".

NO es audio final — es procedimiento puro (sin muestras grabadas), mismo criterio que
`tools/gen_ki_attack_sfx.py` (ver su cabecera): quitar el silencio YA y dar algo con carácter
mientras no exista grabación real. Reemplazar este script (o su salida) en cuanto haya audio de
verdad; el nombre de archivo y el evento no cambian.

DEPENDENCIAS: numpy + soundfile (mismas que gen_ki_attack_sfx.py).

DISEÑO DE SONIDO (3.0s, 48kHz estéreo — coincide EXACTO con SpacePodEntity.LAUNCH_COUNTDOWN_TICKS,
60 ticks = 3s, así que sonando desde beginLaunch() termina justo cuando la cuenta atrás llega a
cero y se ejecuta el salto real):
  - Un "motor" grave (barrido armónico ascendente 55Hz -> 140Hz a lo largo de los 3s) que da la
    sensación de revolucionar antes de despegar.
  - Un "rugido" de ruido de banda ancha (40-1200Hz) cuya envolvente crece de 0.15 a 1.0 durante
    todo el clip — el motor ganando fuerza.
  - Un "whoosh" final de ruido de banda más aguda (300-6000Hz) SOLO en el último medio segundo —
    el golpe de despegue real, coincidiendo con el "1" de la cuenta atrás y el instante del salto.

Uso: `python3 tools/gen_space_pod_launch_sfx.py` (sin argumentos, sobrescribe
`src/main/resources/assets/zenkai/sounds/space_pod_launch.ogg`).
"""
import numpy as np
import soundfile as sf

SR = 48000
DUR = 3.0
OUT = "src/main/resources/assets/zenkai/sounds/space_pod_launch.ogg"

rng = np.random.default_rng(20260911)  # semilla fija: salida reproducible entre corridas


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


def stereo_widen(mono, spread=0.008, sr=SR):
    """Estéreo simple: copia con un desfase de unos ms entre canales (sin reverb real)."""
    delay = max(1, int(spread * sr))
    left = mono
    right = np.concatenate([np.zeros(delay), mono])[: len(mono)]
    return np.stack([left, right], axis=1)


n = int(DUR * SR)
t = np.arange(n) / SR

# Motor: barrido armónico ascendente 55Hz -> 140Hz, integrando la fase real (si no, "salta").
f0, f1 = 55.0, 140.0
k = np.log(f1 / f0) / DUR
phase = 2 * np.pi * f0 * (np.exp(k * t) - 1) / k
engine = np.sin(phase) + 0.5 * np.sin(2 * phase) + 0.25 * np.sin(3 * phase)
engine /= np.max(np.abs(engine))

# Rugido: ruido de banda ancha que crece con el tiempo (el motor ganando fuerza).
roar = band_noise(n, 40, 1200)
roar_env = np.linspace(0.15, 1.0, n) ** 1.3

# Whoosh final: SOLO el último medio segundo, ruido más agudo con su propia envolvente creciente.
burst_dur = 0.5
burst_n = int(burst_dur * SR)
burst = np.zeros(n)
burst[n - burst_n:] = band_noise(burst_n, 300, 6000) * (np.linspace(0.0, 1.0, burst_n) ** 0.7)

sig = engine * 0.5 + roar * roar_env * 0.6 + burst * 0.9

# Fade-in corto (evita un clic al empezar); SIN fade-out al final — el corte en seco es el
# propio "despegue", no hay que suavizarlo.
fade_in = int(0.05 * SR)
env = np.ones(n)
env[:fade_in] = np.linspace(0.0, 1.0, fade_in)
sig *= env

sig /= np.max(np.abs(sig)) or 1.0
sig *= 0.9

audio = stereo_widen(sig)
sf.write(OUT, audio, SR, format="OGG", subtype="VORBIS")
print(f"space_pod_launch.ogg  {DUR:.2f}s")
