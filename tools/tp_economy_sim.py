#!/usr/bin/env python3
"""
Simulacion de la economia de TP de Zenkai (.claude/pendiente/economia-tp.md).

Herramienta de calibracion, NO codigo del mod -- no se compila ni se ejecuta como parte del
build (`./gradlew build`). Se corre a mano con `python3 tools/tp_economy_sim.py`.

Objetivo: estimar horas reales necesarias para llegar a los 5,000,000 TP de
referencia de endgame (CommonConfig.auraReferenceTp), usando la MISMA formula
que TrainingHooks.grant() para fatiga/eficiencia/diferencia de poder, y
probando distintos "loops de sesion" (granjeo continuo vs rafagas+descanso,
con/sin HTC, con/sin pesas a full carga).

Las constantes de mas abajo son una foto de los defaults de CommonConfig y
EntityArchetype -- CALIBRADOS el 2026-08-20 tras esta misma simulacion (ver el
pendiente): training.damage_tp_factor 0.02->0.10, entity.tp_per_pl 0.05->0.25,
training.fatigue_decay_per_minute 0.01->0.20. Si vuelven a cambiar en el
config, hay que actualizar aqui a mano (no hay nada que las mantenga en sync).

HALLAZGO CLAVE que justifica el modelo (ver conversacion): en granjeo
SOSTENIDO, TP_por_kill y tiempo_por_kill escalan los dos con el PL de la
presa, asi que el PL de la presa elegida (siempre que cumpla pl_ratio_full)
NO afecta el TP/hora resultante. Por eso el modelo no necesita recorrer el
bestiario real -- basta fijar la presa exactamente en el umbral de ratio
completo (victimPL = pl_ratio_full * propioPL) y el resultado generaliza.

SEGUNDO HALLAZGO, mas fuerte: con segundos-por-kill constante durante toda la
progresion, el TP/hora sostenido tampoco depende del ritmo de combate (2s y 40s
por kill dan el mismo resultado) -- se cancela con la propia fatiga que genera.
En regimen sostenido, TP/hora = 60 x fatigue_decay_per_minute x tu_PL. Por eso
esa es la palanca dominante del sistema, no damage_tp_factor/tp_per_pl (que
solo importan para que un kill individual se sienta bien recompensado, o para
forzar la eficiencia por debajo del suelo a base de fuerza bruta).

Parametro libre que SI hace falta declarar a mano: cuantos segundos tarda un
kill "de nivel" (contra una presa que da ratio de poder completo). Mirar
CombatZenkaiHooks para derivarlo con precision requeriria modelar multiplicador
de arma, chargeF, Ki Fist/Ki Infuse y mitigacion por defensa -- una cadena
demasiado larga y sensible a build/gear para tratarla como un numero
calculable sin jugarlo. En vez de eso se trata como el PARAMETRO DE RITMO DE
DISEnO (cuanto debe durar una pelea de entrenamiento normal), constante a lo
largo de toda la progresion -- que es ademas la hipotesis mas razonable: un
jugador que invierte en combate deberia matar cosas de su nivel a un ritmo
mas o menos parejo en todo momento, no cada vez mas lento ni mas rapido.
Se prueban varios valores para acotar el rango.
"""

import math

# ── Constantes (espejo de CommonConfig, defaults CALIBRADOS 2026-08-20) ─────
PL_RATIO_FLOOR   = 0.05
PL_RATIO_FULL    = 0.25
FATIGUE_HALFLIFE = 0.10
FATIGUE_DECAY_PER_MIN = 0.20   # training.fatigue_decay_per_minute (0.01 -> 0.20)
MIN_EFFICIENCY   = 0.05
DAMAGE_TP_FACTOR  = 0.10       # training.damage_tp_factor (0.02 -> 0.10)
AIR_TP_FACTOR     = 0.0001
TP_PER_PL         = 0.25       # entity.tp_per_pl (0.05 -> 0.25; antes hardcodeado y duplicado)
HTC_MULTIPLIER    = 2.0
WEIGHT_TP_BONUS   = 1.5        # granted *= (1 + WEIGHT_TP_BONUS) a carga completa
BODY_SCALE        = 1.0

TP_COEFFICIENT      = 1.0      # stats.tp_coefficient
ATTRIBUTE_BASE_COST = 1.0      # stats.attribute_base_cost
GLOBAL_ATTR_CAP     = 200_000

# PowerLevel.java -- pesos del "medidor" (W_*), MIND fuera
W_STR, W_CON, W_DEX, W_WIL, W_SPI = 1.0, 0.6, 1.0, 1.0, 0.25
NUM_COUNTED_ATTRS = 5  # STR, CON, DEX, WIL, SPI (reparto uniforme asumido)

# EntityArchetype.java -- shape (STR,CON,DEX,WIL,SPI) y bodyMult (espejo)
ARCHETYPES = {
    "brawler":   dict(shape=(30, 28, 17, 15, 10), body_mult=1.15),
    "ki_user":   dict(shape=(10, 15, 17, 30, 28), body_mult=1.0),
    "balanced":  dict(shape=(20, 20, 20, 20, 20), body_mult=1.0),
    "speedster": dict(shape=(20, 12, 38, 18, 12), body_mult=0.85),
    "tank":      dict(shape=(15, 38, 25, 12, 10), body_mult=1.4),
    "boss":      dict(shape=(24, 24, 18, 20, 14), body_mult=1.25),
}

TARGET_TP = 5_000_000
MAX_HOURS_CUTOFF = 600  # si no llega antes de esto, se reporta como "no alcanzado"


# ── Coste de atributos: cost(n 0-indexado) = base + coef*n -> cumulative(N) = N(N+1)/2 ──
def points_from_invested_tp(tp_invested: float) -> float:
    """Inversa de TP_invertido(N) = N*(N+1)/2 (continua, no entera -- suficiente para la curva)."""
    if tp_invested <= 0:
        return 0.0
    n = (-1 + math.sqrt(1 + 8 * tp_invested)) / 2.0
    return min(n, GLOBAL_ATTR_CAP)


def player_pl_from_tp(tp_total_invested: float) -> float:
    """Reparto uniforme entre los 5 atributos contados (supuesto declarado en la conversacion)."""
    per_attr_tp = tp_total_invested / NUM_COUNTED_ATTRS
    n = points_from_invested_tp(per_attr_tp)
    return n * (W_STR + W_CON + W_DEX + W_WIL + W_SPI)


def victim_body_max(victim_pl: float, archetype: str) -> float:
    shape = ARCHETYPES[archetype]["shape"]
    body_mult = ARCHETYPES[archetype]["body_mult"]
    shape_sum = sum(shape)  # = 100 para los 6 arquetipos actuales
    con_shape_frac = shape[1] / shape_sum
    con = victim_pl * con_shape_frac  # B_CON=1.0, denom=shape_sum (PowerLevel.solveAttributes)
    return max(1.0, 10 + con * body_mult * BODY_SCALE)


def simulate(archetype: str, kill_seconds: float, burst_minutes: float, rest_minutes: float,
             use_htc: bool, weight_mult: float, max_hours: float = MAX_HOURS_CUTOFF):
    """
    Devuelve (horas_hasta_target o None, lista de checkpoints (horas, tp_total)).
    Ciclo: mata en rafaga durante burst_minutes (si es 0 => granjeo continuo sin pausas),
    luego descansa rest_minutes (fatiga decae, nada de TP), repite.
    La presa siempre se elige en el umbral exacto de ratio completo (ver docstring del modulo).
    kill_seconds es CONSTANTE durante toda la progresion (parametro de ritmo de diseno,
    ver docstring del modulo) -- no depende del PL actual del jugador.
    """
    tp_total = 0.0
    fatigue = 0.0
    elapsed_min = 0.0
    checkpoints = []
    next_checkpoint = 100_000

    burst_elapsed = 0.0
    in_rest = False

    while elapsed_min / 60.0 < max_hours:
        pl = max(1.0, player_pl_from_tp(tp_total))

        if in_rest:
            # Salto directo al final del descanso: nada de TP, solo decae fatiga.
            fatigue = max(0.0, fatigue - FATIGUE_DECAY_PER_MIN * rest_minutes)
            elapsed_min += rest_minutes
            in_rest = False
            burst_elapsed = 0.0
            continue

        victim_pl = PL_RATIO_FULL * pl  # umbral exacto: plFactor = 1.0
        body_max = victim_body_max(victim_pl, archetype)
        raw_tp = body_max * DAMAGE_TP_FACTOR + victim_pl * TP_PER_PL
        kill_minutes = kill_seconds / 60.0

        # Decay desde el ultimo evento (lazy decay, igual que TrainingHooks.grant()).
        fatigue = max(0.0, fatigue - FATIGUE_DECAY_PER_MIN * kill_minutes)
        m = max(MIN_EFFICIENCY, FATIGUE_HALFLIFE / (FATIGUE_HALFLIFE + fatigue))

        base = raw_tp * m  # plFactor = 1.0 por construccion
        granted = base * (HTC_MULTIPLIER if use_htc else 1.0) * weight_mult

        tp_total += granted
        fatigue += base / pl
        elapsed_min += kill_minutes
        burst_elapsed += kill_minutes

        if tp_total >= next_checkpoint:
            checkpoints.append((elapsed_min / 60.0, tp_total))
            next_checkpoint += 100_000 if next_checkpoint < 1_000_000 else 500_000

        if tp_total >= TARGET_TP:
            return elapsed_min / 60.0, checkpoints

        if burst_minutes > 0 and burst_elapsed >= burst_minutes:
            in_rest = True

    return None, checkpoints


def fmt_hours(h):
    return f"{h:6.1f} h" if h is not None else "  >600 h"


if __name__ == "__main__":
    print(f"Objetivo: {TARGET_TP:,} TP  (PL implicado ~= {player_pl_from_tp(TARGET_TP):.0f})\n")

    print("=== Sensibilidad a segundos-por-kill de nivel (parametro de ritmo de diseno) ===")
    print("Arquetipo=balanced, granjeo CONTINUO (sin rafagas), sin HTC ni pesas:\n")
    for ks in (2, 5, 10, 20, 40):
        h, _ = simulate("balanced", ks, burst_minutes=0, rest_minutes=0,
                         use_htc=False, weight_mult=1.0)
        print(f"  {ks:3d}s/kill  ->  {fmt_hours(h)}")

    print("\n=== Efecto del arquetipo de la presa (deberia influir poco, por la invariancia) ===")
    print("10s/kill, granjeo continuo, sin HTC ni pesas:\n")
    for arch in ARCHETYPES:
        h, _ = simulate(arch, 10, burst_minutes=0, rest_minutes=0,
                         use_htc=False, weight_mult=1.0)
        print(f"  {arch:10s} -> {fmt_hours(h)}")

    print("\n=== Matriz de loops de sesion (10s/kill, arquetipo balanced) ===")
    print(f"{'cadencia (burst/descanso min)':32s} {'HTC':>5s} {'pesas x2.5':>11s} {'horas a 5M':>11s}")
    scenarios = [
        (0, 0, False, 1.0),      # baseline: grind continuo sin nada
        (0, 0, True, 1.0),       # continuo + HTC
        (0, 0, True, 2.5),       # continuo + HTC + pesas
        (5, 5, True, 2.5),       # rafagas cortas 1:1
        (10, 20, True, 2.5),     # rafaga corta, descanso largo
        (15, 45, True, 2.5),     # rafaga corta, descanso muy largo
        (30, 10, True, 2.5),     # rafaga larga, descanso corto
    ]
    for burst, rest, htc, wmult in scenarios:
        h, _ = simulate("balanced", 10, burst_minutes=burst, rest_minutes=rest,
                         use_htc=htc, weight_mult=wmult)
        cadence = "continuo" if burst == 0 else f"{burst:g}/{rest:g}"
        print(f"{cadence:32s} {str(htc):>5s} {wmult:>11.1f} {fmt_hours(h):>11s}")

    print("\n=== Curva completa (checkpoints) del mejor y peor caso, 10s/kill ===")
    for label, (burst, rest, htc, wmult) in [
        ("peor caso (continuo, sin buffs)", (0, 0, False, 1.0)),
        ("mejor caso (continuo, HTC+pesas)", (0, 0, True, 2.5)),
    ]:
        print(f"\n-- {label} --")
        h, cps = simulate("balanced", 10, burst_minutes=burst, rest_minutes=rest,
                           use_htc=htc, weight_mult=wmult, max_hours=1000)
        for hrs, tp in cps:
            print(f"  {hrs:7.1f} h  ->  {tp:,.0f} TP")
        print(f"  Horas hasta {TARGET_TP:,}: {fmt_hours(h)}")

    print("\n=== Tabla final: segundos/kill necesarios para 20-40h (continuo, HTC+pesas) ===")
    for target_h in (20, 30, 40):
        lo, hi = 0.1, 120.0
        for _ in range(40):
            mid = (lo + hi) / 2
            h, _ = simulate("balanced", mid, burst_minutes=0, rest_minutes=0,
                             use_htc=True, weight_mult=2.5, max_hours=2000)
            if h is None or h > target_h:
                hi = mid
            else:
                lo = mid
        print(f"  Para llegar en {target_h}h hacen falta ~{lo:.1f}s por kill de nivel (o mas rapido)")
