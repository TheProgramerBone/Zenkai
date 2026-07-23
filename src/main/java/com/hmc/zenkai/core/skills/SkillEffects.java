package com.hmc.zenkai.core.skills;

import com.hmc.zenkai.core.network.feature.forms.KaiokenTier;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.world.entity.player.Player;

/**
 * Consulta centralizada de los efectos de habilidad (el equivalente a MasteryEffects).
 * Todos los números salen de las curvas del datapack vía SkillDef#value, así que
 * balancear es editar el JSON y hacer /reload, nunca tocar esta clase.
 */
public final class SkillEffects {
    private SkillEffects() {}

    public static final String FLY = "fly";
    public static final String RUN = "run";
    public static final String KI_CONTROL = "ki_control";
    public static final String MEDITATION = "meditation";
    public static final String KI_BLOCK = "ki_block";
    public static final String KI_SENSE = "ki_sense";
    public static final String KAIOKEN = "kaioken";

    public static int level(Player p, String skillId) {
        if (p == null) return 0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        return att == null ? 0 : att.skills().level(skillId);
    }

    /** Valor de una curva en el nivel actual; devuelve fallback si no tiene la habilidad. */
    private static double curve(Player p, String skillId, String key, double fallback) {
        int lvl = level(p, skillId);
        if (lvl <= 0) return fallback;
        SkillDef def = SkillDef.get(skillId);
        return def == null ? fallback : def.value(key, lvl, fallback);
    }

    // ── Fly ──────────────────────────────────────────────────────────────────
    /** Sin la habilidad no se vuela: el nivel 1 lo enseña Kami. */
    public static boolean canFly(Player p) { return level(p, FLY) > 0; }

    /** Multiplicador del drenaje de ki al volar (1.0 = coste completo). */
    public static double flyKiDrainFactor(Player p) { return curve(p, FLY, "ki_cost_mult", 1.0); }

    /** Multiplicador de velocidad de vuelo por nivel. */
    public static double flySpeedFactor(Player p) { return curve(p, FLY, "speed_mult", 1.0); }

    // ── Run ──────────────────────────────────────────────────────────────────
    public static double runStaminaDrainFactor(Player p) { return curve(p, RUN, "stamina_cost_mult", 1.0); }

    public static double runSpeedFactor(Player p) { return curve(p, RUN, "speed_mult", 1.0); }

    /** Techo del % de poder: 50 bases + 5 por nivel de Ki Control (nivel 10 = 100). */
    public static int maxPowerPercent(Player p) {
        return 50 + 5 * level(p, KI_CONTROL);
    }

    // ── Meditación ───────────────────────────────────────────────────────────
    /** EL GATE: sin Meditación NO se recupera ki cargando con C. El % de poder sí sube
     *  igual: concentrarse siempre funciona, canalizar el ki es lo que hay que aprender. */
    public static boolean canChargeKi(Player p) { return level(p, MEDITATION) > 0; }

    /** Multiplicador del regen PASIVO de ki. 1.0 sin la habilidad (mínimo vital). */
    public static double kiRegenFactor(Player p) { return curve(p, MEDITATION, "regen_mult", 1.0); }

    /** Multiplicador de la carga con C sobre el regen base por segundo. 0 = no puede cargar. */
    public static double kiChargeFactor(Player p) { return curve(p, MEDITATION, "charge_mult", 0.0); }

    // ── Ki Block ─────────────────────────────────────────────────────────────
    /** Fracción del daño que se CORTA al bloquear: 0.20 sin la habilidad, 0.50 a nivel 5.
     *  Bloquear siempre sirve de algo; la habilidad es lo que lo hace fiable. */
    public static double blockReduction(Player p) {
        return curve(p, KI_BLOCK, "block_reduction", 0.20);
    }

    /** Multiplicador de daño RECIBIDO al bloquear. Menos es mejor. */
    public static double blockDamageMultiplier(Player p) {
        return Math.max(0.0, 1.0 - blockReduction(p));
    }

    // ── Ki Sense ─────────────────────────────────────────────────────────────
    public static int senseLevel(Player p) { return level(p, KI_SENSE); }

    /** Multiplicador del rango de sentido. 1.0 sin la habilidad. */
    public static double senseRangeFactor(Player p) { return curve(p, KI_SENSE, "range_mult", 1.0); }

    /** Fijar objetivo se desbloquea con el nivel 1. */
    public static boolean canLockOn(Player p) { return level(p, KI_SENSE) <= 0; }

    // Lo que se PERCIBE por nivel. Son umbrales de estructura, no números a balancear,
    // así que viven aquí y no como listas de 0/1 en el datapack (fáciles de descuadrar).
    public static boolean senseShowsHealth(Player p)    { return level(p, KI_SENSE) >= 2; }
    public static boolean senseShowsAlignment(Player p) { return level(p, KI_SENSE) >= 3; }
    public static boolean senseShowsKi(Player p)        { return level(p, KI_SENSE) >= 4; }
    public static boolean senseShowsStamina(Player p)   { return level(p, KI_SENSE) >= 5; }

    // ── Kaioken ──────────────────────────────────────────────────────────────
    public static int kaiokenLevel(Player p) { return level(p, KAIOKEN); }

    /** Multiplicador del drenaje de vida. 1.0 sin maestría, 0.35 a nivel 10. */
    public static double kaiokenDrainFactor(Player p) {
        return curve(p, KAIOKEN, "drain_factor", 1.0);
    }

    /** Escalón más alto que este jugador puede usar. */
    public static KaiokenTier maxKaioken(Player p) {
        return KaiokenTier.highestFor(level(p, KAIOKEN));
    }
}