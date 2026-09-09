#!/usr/bin/env python3
"""
Simulacion de la economia de TP de Zenkai (.claude/pendiente/economia-tp.md).

Herramienta de calibracion, NO codigo del mod -- no se compila ni se ejecuta como parte del
build (`./gradlew build`). Se corre a mano con `python3 tools/tp_economy_sim.py`.

Objetivo: estimar horas reales necesarias para llegar a los 5,000,000 TP de
referencia de endgame (ServerConfig.auraReferenceTp), usando la MISMA formula
que TrainingHooks.grant() para fatiga/eficiencia/diferencia de poder, y
probando distintos "loops de sesion" (granjeo continuo vs rafagas+descanso,
con/sin HTC, con/sin pesas a full carga).

Las constantes de mas abajo son una foto de los defaults de ServerConfig y
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

Parametro libre declarado a mano en la primera version de este script:
cuantos segundos tarda un kill "de nivel" (contra una presa que da ratio de
poder completo). Se probaban varios valores (2-40s) para acotar el rango
porque derivarlo con precision requeria modelar multiplicador de arma,
chargeF, Ki Fist/Ki Infuse y mitigacion por defensa.

ACTUALIZADO 2026-09-09: esa cadena YA esta modelada (ver kill_seconds_real()
mas abajo, espejo linea a linea de CombatZenkaiHooks.playerMeleeDamage +
.mitigate()) -- el numero de segundos por kill ya NO hace falta declararlo a
mano, se CALCULA a partir de las mismas formulas reales de combate, en el
mismo punto de progresion (TP invertido) que el resto de la simulacion.
Simplificaciones deliberadas del modelo de combate (documentadas donde se
usan, no ocultas):
  - A PUNO LIMPIO o con una espada de hierro SIN encantar (ATTACK_MODES) --
    Ki Fist/Ki Infuse y encantamientos vanilla se dejan en 0 a proposito:
    modelarlos añadiria variables de build (cuanto ki gasta, que encantamiento
    lleva) en vez de dar un numero de referencia limpio. Los dos modos dan
    una cota INFERIOR de DPS real, no un techo -- cualquier inversion en ki
    solo puede acelerar el kill, nunca ralentizarlo.
  - chargeF = 1.0 (carga completa) siempre: se asume que el jugador espera el
    cooldown de vainilla entre golpes en vez de hacer spam -- mismo criterio
    que "ritmo de diseño constante" de la version anterior, pero ahora
    aplicado a CADA golpe individual, no a la pelea entera.
  - Sin critico vainilla (salto+caida) ni armadura del mob: ambos solo
    ACORTAN el kill si se dieran, asi que de nuevo el numero es una cota
    inferior de velocidad real, nunca una sobreestimacion.
El HALLAZGO CLAVE de mas arriba (TP/hora sostenido NO depende del ritmo de
combate) sigue siendo cierto y sigue siendo la razon real de por que
damage_tp_factor/fatigue_decay_per_minute son las palancas que importan --
esto no lo cambia. Lo que aporta kill_seconds_real() es poder CONFIRMAR que
el ritmo de combate resultante se sigue sintiendo bien (ni instantaneo ni
eterno) en vez de suponerlo, y reemplazar el rango adivinado 2-40s por un
numero trazable hasta el código real.
"""

import math

# ── Constantes (espejo de ServerConfig, defaults CALIBRADOS 2026-08-20) ─────
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

# ── Combate real (CombatZenkaiHooks) -- espejo, 2026-09-09 ──────────────────
MIN_DAMAGE_PERCENT = 0.05  # ServerConfig.minDamagePercent(): suelo de mitigate()
WEAPON_SCALE        = 0.04  # ServerConfig.weaponScale(): KiInfusion.weaponMultiplier()
MELEE_FROM_WIL       = 0.15  # StatSynergy.MELEE_FROM_WIL: melee = (STR + WIL*esto) * coef

# Coeficiente de melee del JUGADOR (RaceStatTable, data/zenkai/zenkai_race_stats/*.json):
# humano "warrior" -- la combinacion mas generalista/de pelea de las 15 (5 razas x 3
# estilos), elegida como referencia neutra en vez de barrer las 15 (el objetivo es un
# numero de referencia trazable, no un barrido de builds). El coeficiente de DEFENSA de la
# VICTIMA no hace falta declararlo: EntityStats.computeDefenseFinal() = DEX directo, SIN
# coeficiente de raza -- ese multiplicador solo existe para el lado JUGADOR (StatSynergy).
PLAYER_MELEE_COEF = 9.8  # human.json -> "warrior" -> "melee"

# base_attributes de human.json (misma raza que PLAYER_MELEE_COEF) -- un personaje recién
# creado, con 0 TP invertido, YA tiene esto en cada atributo antes de comprar ni un punto.
# Sin sumarlo, kill_seconds_real(0, ...) da strDamage=0 -> hit=0 -> "inf" segundos por kill
# (division por cero encubierta): un personaje de verdad nunca está en cero absoluto.
BASE_ATTR = 5

# Attributes.ATTACK_SPEED de vainilla: base 4.0 a puño limpio (cooldown = 20/4.0 = 5 ticks =
# 0.25s por golpe a carga completa); una espada de hierro resta 2.4 (deja 1.6, 0.625s/golpe)
# y aporta weaponMultiplier vía KiInfusion.weaponMultiplier -- attackDamageOf con espada de
# hierro sin encantar = 4.0 (1.0 base + 3.0 del arma), extra = 3.0, *WEAPON_SCALE.
ATTACK_MODES = {
    "puño limpio":     dict(attack_speed=4.0, weapon_mult=1.0),
    "espada de hierro": dict(attack_speed=1.6,
                              weapon_mult=1.0 + max(0.0, 4.0 - 1.0) * WEAPON_SCALE),
}


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


# ── Combate real: CombatZenkaiHooks.playerMeleeDamage + .mitigate(), espejo ─────────────
def player_str_wil(tp_total_invested: float):
    """STR y WIL del jugador: BASE_ATTR (base de raza, ver arriba) + puntos INVERTIDOS bajo
    el mismo reparto uniforme que player_pl_from_tp (las 5 stats contadas reciben el mismo
    numero de puntos) -- asi que STR == WIL == BASE_ATTR + n."""
    per_attr_tp = tp_total_invested / NUM_COUNTED_ATTRS
    n = points_from_invested_tp(per_attr_tp) + BASE_ATTR
    return n, n


def player_melee_hit(tp_total_invested: float, weapon_mult: float = 1.0) -> float:
    """Un golpe a carga completa (chargeF=1.0), sin encantamientos vainilla ni bonus de Ki
    Fist/Ki Infuse (0 en los dos, ver docstring del modulo) -- CombatZenkaiHooks.
    playerMeleeDamage: base = strDamage * weaponMultiplier * enchantMult(=1) * chargeF(=1)."""
    str_, wil = player_str_wil(tp_total_invested)
    str_damage = (str_ + wil * MELEE_FROM_WIL) * PLAYER_MELEE_COEF
    return str_damage * weapon_mult


def mitigated_hit(raw_hit: float, defense: float) -> float:
    """CombatZenkaiHooks.mitigate(), rama no-ambiental: sin armadura del mob (armorMult=1.0,
    la mayoria de mobs vainilla no llevan), sin bloqueo/barrera/absorcion (la presa de
    granjeo no bloquea). finalDamage = dmg*(1 - def/(def+dmg)), suelo en MIN_DAMAGE_PERCENT."""
    if defense <= 0.0:
        final = raw_hit
    else:
        final = raw_hit * (1.0 - defense / (defense + raw_hit))
    return max(final, raw_hit * MIN_DAMAGE_PERCENT)


def victim_defense(victim_pl: float, archetype: str) -> float:
    """EntityStats.computeDefenseFinal() = DEX directo (SIN coeficiente de raza) -- y DEX =
    victim_pl * shape[DEX]/shape_sum, exactamente el mismo reparto que PowerLevel.
    solveAttributes usa para CON en victim_body_max (ver su docstring)."""
    shape = ARCHETYPES[archetype]["shape"]
    dex_frac = shape[2] / sum(shape)  # shape = (STR, CON, DEX, WIL, SPI)
    return victim_pl * dex_frac


def kill_seconds_real(tp_total_invested: float, archetype: str, mode: str) -> float:
    """Segundos para matar a la presa exacta del umbral de ratio completo (PL_RATIO_FULL),
    derivados de la formula REAL de combate en el punto de progresion dado -- sustituye al
    parametro de ritmo de diseño declarado a mano de la version anterior de este script."""
    pl = max(1.0, player_pl_from_tp(tp_total_invested))
    victim_pl = PL_RATIO_FULL * pl
    body_max = victim_body_max(victim_pl, archetype)
    defense = victim_defense(victim_pl, archetype)

    m = ATTACK_MODES[mode]
    raw_hit = player_melee_hit(tp_total_invested, weapon_mult=m["weapon_mult"])
    hit = mitigated_hit(raw_hit, defense)
    if hit <= 0.0:
        return float("inf")
    hits_needed = math.ceil(body_max / hit)
    return hits_needed / m["attack_speed"]


def simulate(archetype: str, kill_seconds: float, burst_minutes: float, rest_minutes: float,
             use_htc: bool, weight_mult: float, max_hours: float = MAX_HOURS_CUTOFF,
             combat_mode: str = None):
    """
    Devuelve (horas_hasta_target o None, lista de checkpoints (horas, tp_total)).
    Ciclo: mata en rafaga durante burst_minutes (si es 0 => granjeo continuo sin pausas),
    luego descansa rest_minutes (fatiga decae, nada de TP), repite.
    La presa siempre se elige en el umbral exacto de ratio completo (ver docstring del modulo).

    kill_seconds: si combat_mode es None (comportamiento ORIGINAL), es una CONSTANTE durante
    toda la progresion -- el parametro de ritmo de diseño declarado a mano. Si combat_mode es
    "puño limpio"/"espada de hierro" (ATTACK_MODES), kill_seconds se IGNORA y se recalcula en
    cada iteracion con kill_seconds_real(tp_total, archetype, combat_mode) -- el ritmo real de
    combate en el punto de progresion actual, no un numero fijo.
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
        ks = kill_seconds_real(tp_total, archetype, combat_mode) if combat_mode else kill_seconds
        kill_minutes = ks / 60.0

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

    print("=== NUEVO 2026-09-09: segundos-por-kill REALES, derivados de CombatZenkaiHooks ===")
    print("(sustituye al rango adivinado de la seccion siguiente -- ver docstring del modulo)\n")
    print(f"{'TP invertido':>14s} {'PL jugador':>11s} {'puño limpio':>13s} {'espada hierro':>14s}")
    for tp_point in (0, 1_000, 100_000, 1_000_000, TARGET_TP):
        pl_here = player_pl_from_tp(tp_point)
        fist = kill_seconds_real(tp_point, "balanced", "puño limpio")
        sword = kill_seconds_real(tp_point, "balanced", "espada de hierro")
        print(f"{tp_point:>14,} {pl_here:>11,.0f} {fist:>11.1f}s {sword:>12.1f}s")
    print("(arquetipo=balanced; cota INFERIOR de DPS real -- sin Ki Fist/Infuse, sin "
          "encantamientos, sin critico -- ver docstring de kill_seconds_real)")

    print("\n=== Sensibilidad a segundos-por-kill de nivel (parametro de ritmo de diseno) ===")
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

    print("\n=== NUEVO 2026-09-09: horas a 5M con combate REAL (no un s/kill adivinado) ===")
    print("Mismos escenarios de la matriz de arriba, pero kill_seconds se recalcula cada golpe")
    print("con kill_seconds_real() -- confirma si el rango adivinado (arriba) fue optimista o")
    print("pesimista frente a lo que el arquetipo balanced realmente puede hacer a puño limpio:\n")
    print(f"{'cadencia (burst/descanso min)':32s} {'HTC':>5s} {'pesas x2.5':>11s} {'horas a 5M':>11s}")
    for burst, rest, htc, wmult in scenarios:
        h, _ = simulate("balanced", 0, burst_minutes=burst, rest_minutes=rest,
                         use_htc=htc, weight_mult=wmult, combat_mode="puño limpio")
        cadence = "continuo" if burst == 0 else f"{burst:g}/{rest:g}"
        print(f"{cadence:32s} {str(htc):>5s} {wmult:>11.1f} {fmt_hours(h):>11s}")
    print("(kill_seconds ignorado -- el 0 es un placeholder, combat_mode manda)")
