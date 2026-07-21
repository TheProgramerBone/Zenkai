package com.hmc.zenkai.core.skills;

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

    /** Techo del % de poder: 50 base + 5 por nivel de Ki Control (nivel 10 = 100). */
    public static int maxPowerPercent(Player p) {
        return 50 + 5 * level(p, KI_CONTROL);
    }
}