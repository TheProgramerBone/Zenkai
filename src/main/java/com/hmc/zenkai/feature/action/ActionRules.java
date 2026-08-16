package com.hmc.zenkai.feature.action;

/**
 * ÚNICA fuente de verdad de qué se puede hacer y cuándo. Sin estado, sin dependencias de
 * lado: la compilan cliente y servidor y ambos leen las mismas reglas.
 *
 * MATRIZ A (acciones exclusivas) — canInterrupt():
 *
 *   Estado ↓ / Request →   BLOCK    PHYS     KI       TRANSFORM
 *   NONE                   ✓        ✓        ✓        ✓
 *   BLOCK                  —        ✗        ✗        ✗
 *   PHYSICAL/ACTIVE        corta    ✗        ✗        ✗
 *   PHYSICAL/INSTANT       ✓        ✓        ✓        ✓
 *   KI_TECHNIQUE/CHARGING  cancela  cancela  cancela+nueva desde 0   ✗
 *   KI_TECHNIQUE/RELEASING ✗        ✗        ✗        ✗
 *   TRANSFORM              ✗        ✗        ✗        —
 *   DOWNED                 ✗        ✗        ✗        ✗
 *
 * MATRIZ B (estados sostenidos) — cancelsChargingKi() / cancelsTurbo():
 *
 *   Request        chargingKi   turbo     flying
 *   BLOCK          cancela      mantiene  mantiene
 *   PHYS           cancela      mantiene  mantiene
 *   KI técnica     cancela      mantiene  mantiene
 *   TRANSFORM      cancela      cancela   mantiene
 *   DOWNED         cancela      cancela   cancela
 */
public final class ActionRules {

    private ActionRules() {}

    /**
     * Decisión de balance: TURBO sobrevive a bloquear, pegar y cargar técnicas. Es un modo
     * sostenido de gasto de ki, no una acción; apagarlo al usar una técnica castigaría justo
     * al que lo tiene encendido para eso. Poner a true para invertirlo.
     */
    private static final boolean TURBO_CANCELLED_BY_ACTIONS = false;

    // ── Matriz A ──────────────────────────────────────────────────────────────

    /** ¿Puede `incoming` arrancar estando en `current`/`phase`? */
    public static boolean canInterrupt(ActionType current, ActionPhase phase, ActionType incoming) {
        if (current == null || current == ActionType.NONE) return true;
        return switch (current) {
            // Defender no se "interrumpe": se sale soltando el botón.
            case BLOCK -> false;
            // Los movimientos con duración (dash, barrage) solo los corta defender.
            // Los instantáneos (heavy blow, kiai) no dejan estado: no bloquean nada.
            case PHYSICAL -> phase == ActionPhase.INSTANT || incoming == ActionType.BLOCK;
            // Cargando: cualquier cosa cancela, salvo transformar. Otra técnica de ki también
            // cancela — y la nueva empieza desde 0, sin heredar progreso.
            case KI_TECHNIQUE -> phase == ActionPhase.CHARGING && incoming != ActionType.TRANSFORM;
            case TRANSFORM -> false;
            default -> true;
        };
    }

    // ── Matriz B ──────────────────────────────────────────────────────────────

    /** Cargar ki se CANCELA (no se pausa) con cualquier acción exclusiva. */
    public static boolean cancelsChargingKi(ActionType incoming) {
        return incoming != null && incoming != ActionType.NONE;
    }

    /** Solo transformar apaga el turbo (más el derribo, que va aparte). */
    public static boolean cancelsTurbo(ActionType incoming) {
        if (incoming == ActionType.TRANSFORM) return true;
        return TURBO_CANCELLED_BY_ACTIONS && incoming != null && incoming != ActionType.NONE;
    }

    // ── Guardas comunes ───────────────────────────────────────────────────────

    /** Guardas que comparten TODAS las acciones de combate. */
    private static ActionReject common(ActionContext ctx) {
        if (!ctx.raceChosen()) return ActionReject.NO_RACE;
        if (ctx.downed())      return ActionReject.DOWNED;
        if (!ctx.combatMode()) return ActionReject.NOT_COMBAT_MODE;
        if (!ctx.handsFree())  return ActionReject.HANDS_BUSY;
        return ActionReject.OK;
    }

    // ── Técnicas físicas ──────────────────────────────────────────────────────

    public static ActionReject canFirePhysical(ActionContext ctx, boolean enabled, boolean unlocked,
                                               boolean cooldownReady, int cost, double stamina) {
        ActionReject c = common(ctx);
        if (!c.ok()) return c;
        if (ctx.blocking())    return ActionReject.BLOCKING;
        if (ctx.physBusy())    return ActionReject.BUSY;   // un movimiento a la vez
        if (!enabled)          return ActionReject.DISABLED;
        if (!unlocked)         return ActionReject.LOCKED;
        if (!cooldownReady)    return ActionReject.ON_COOLDOWN;
        if (stamina < cost)    return ActionReject.NO_RESOURCE;
        return ActionReject.OK;
    }

    // ── Técnicas de ki ────────────────────────────────────────────────────────

    /**
     * Empezar a cargar. SÍ comprueba cooldown: si la técnica está enfriando, no se deja ni
     * arrancar la carga.
     * La alternativa —permitir cargar durante el cooldown para que ambos tiempos se solapen—
     * se probó y se descartó: el jugador ve la barra llenarse, suelta, y el disparo se
     * rechaza. Feedback que miente. Bloquear antes de empezar es honesto: la técnica no
     * responde y el overlay muestra el motivo.
     * Consecuencia asumida: carga y enfriamiento se SUMAN. Se ajusta desde cooldown_ticks en
     * el datapack, no desde aquí.
     * No cuesta energía: el coste se cobra al release aceptado.
     */
    public static ActionReject canStartKiCharge(ActionContext ctx, boolean enabled, boolean unlocked,
                                                boolean cooldownReady) {
        ActionReject c = common(ctx);
        if (!c.ok()) return c;
        if (ctx.blocking())   return ActionReject.BLOCKING;
        if (ctx.physBusy())   return ActionReject.BUSY;
        if (!enabled)         return ActionReject.DISABLED;
        if (!unlocked)        return ActionReject.LOCKED;
        if (!cooldownReady)   return ActionReject.ON_COOLDOWN;
        return ActionReject.OK;
    }

    /**
     * Disparar. `slot` es el que pide el cliente; se exige que coincida con el que de verdad
     * está cargando. `ratio` lo calcula el llamante A PARTIR de authoritativeChargeTicks(),
     * nunca del valor que mandó el cliente.
     */
    public static ActionReject canReleaseKi(ActionContext ctx, int slot, boolean enabled,
                                            boolean unlocked, boolean cooldownReady,
                                            double ratio, double minRatio,
                                            double energy, int cost) {
        ActionReject c = common(ctx);
        if (!c.ok()) return c;
        if (ctx.blocking())               return ActionReject.BLOCKING;
        if (!ctx.chargingTechnique())     return ActionReject.NO_CHARGE;
        if (ctx.chargingSlot() != slot)   return ActionReject.WRONG_SLOT;
        if (!enabled)                     return ActionReject.DISABLED;
        if (!unlocked)                    return ActionReject.LOCKED;
        if (!cooldownReady)               return ActionReject.ON_COOLDOWN;
        if (ratio < minRatio)             return ActionReject.UNDERCHARGED;
        if (energy < cost)                return ActionReject.NO_RESOURCE;
        return ActionReject.OK;
    }

    /**
     * CARGA AUTORITATIVA. El cliente NO decide cuánto cargó: se deriva del tiempo real
     * transcurrido desde que el servidor registró el inicio.
     * Antes, KiFirePacket aceptaba el chargeTicks del paquete y solo lo clampaba al máximo,
     * así que un cliente modificado disparaba cualquier técnica al 200% de sobrecarga de
     * forma instantánea. KiChargeServer ya guardaba startTick precisamente para esto.
     */
    public static int authoritativeChargeTicks(long elapsedTicks, int maxTicks) {
        if (elapsedTicks <= 0) return 0;
        return (int) Math.min(elapsedTicks, Math.max(0, maxTicks));
    }
}