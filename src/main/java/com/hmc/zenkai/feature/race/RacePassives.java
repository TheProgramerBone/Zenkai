package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;

/**
 * IDENTIDAD MECÁNICA de cada raza. Hardcodeada a propósito.
 * El datapack manda sobre los NÚMEROS (cuánto rinde un punto de STR en un majin), pero no
 * sobre QUÉ ES un majin. Un saiyan que no se hace más fuerte tras estar al borde de la muerte
 * no es un saiyan con otros valores: es otra cosa. Poner eso en JSON invitaría a datapacks que
 * cambian la naturaleza de las razas, y el mod dejaría de tener una lectura canónica.
 * Hasta ahora la diferenciación era PURAMENTE numérica: cinco razas que se distinguían por
 * tener coeficientes distintos en las mismas seis columnas. Race.baseScale() de Arcosian era
 * literalmente la única propiedad no numérica del sistema entero.
 * Las cinco pasivas, y por qué esa y no otra:
 *   SAIYAN — Zenkai. Recuperarse de un daño casi mortal te deja más fuerte. Es LA mecánica
 *     saiyan del canon y la que da nombre al mod.
 *   NAMEKIAN — Regeneración. Piccolo se rehace un brazo; el precio es ki, como en la serie.
 *   MAJIN — Cuerpo elástico. Buu encaja golpes físicos que partirían a cualquiera, pero las
 *     técnicas de energía sí le afectan: por eso reduce el daño FÍSICO y no el de ki.
 *   ARCOSIAN — Sangre fría. Freezer sobrevive en el vacío y no siente el frío, y su ki es
 *     innato: no ha entrenado un día de su vida y aun así lo controla.
 *   HUMAN — Potencial. Los humanos del canon no tienen techo biológico, tienen esfuerzo:
 *     Krilin y Ten Shin Han aguantan el ritmo entrenando más, no naciendo mejor.
 * Esta clase es SOLO consulta: constantes y funciones puras. El estado temporal (el zenkai
 * activo, sus contadores) vive en RacePassiveSystem, que es quien ticka.
 */
public final class RacePassives {
    private RacePassives() {}

    // ── SAIYAN: Zenkai ───────────────────────────────────────────────────────
    /** Fracción de body por debajo de la cual el zenkai queda armado. */
    public static final double ZENKAI_TRIGGER = 0.20;
    /** Fracción de body a la que hay que volver para que el zenkai dispare. */
    public static final double ZENKAI_RECOVER = 0.60;
    /** Bonus de stats mientras dura. Se suma al multiplicador de forma. */
    public static final double ZENKAI_BONUS = 0.12;
    /** Duración en ticks (60 s). */
    public static final int ZENKAI_DURATION = 20 * 60;
    /** Enfriamiento en ticks (10 min). Sin él, bastaría con ir tirándose a un cactus. */
    public static final int ZENKAI_COOLDOWN = 20 * 60 * 10;

    // ── NAMEKIAN: Regeneración ───────────────────────────────────────────────
    /** Multiplicador de la regeneración de body. */
    public static final double NAMEKIAN_REGEN_MULT = 3.0;
    /** Ki consumido por punto de body regenerado de más. Sin coste sería curación infinita. */
    public static final double NAMEKIAN_REGEN_KI_COST = 1.5;

    // ── MAJIN: Cuerpo elástico ───────────────────────────────────────────────
    /** Reducción del daño FÍSICO recibido. El daño de ki entra completo. */
    public static final double MAJIN_PHYSICAL_REDUCTION = 0.12;
    /** Regeneración de body extra por segundo, en fracción del máximo. Independiente de la
     *  comida: Buu no come para regenerarse. */
    public static final double MAJIN_PASSIVE_REGEN = 0.004;

    // ── ARCOSIAN: Sangre fría ────────────────────────────────────────────────
    /** Descuento sobre el coste de ki. Se multiplica con el kiCostMult del datapack. */
    public static final double ARCOSIAN_KI_DISCOUNT = 0.90;

    // ── HUMAN: Potencial ─────────────────────────────────────────────────────
    /** Multiplicador del TP ganado entrenando. */
    public static final double HUMAN_TP_GAIN = 1.15;
    /** Multiplicador de la regeneración de estamina. */
    public static final double HUMAN_STAMINA_REGEN = 1.5;

    // ── Nivel de la pasiva (skill tree, F4) ──────────────────────────────────
    //
    // QUÉ hace cada pasiva sigue aquí, hardcodeado (es la identidad de la raza). CUÁNTO rinde
    // en cada nivel sale del nodo de pasiva del árbol (data/zenkai/zenkai_skill_tree/
    // race_<raza>.json, campo "values"; ver SkillTreeData): el nivel 1 es gratis y vale lo
    // mismo que las constantes de arriba, que quedan como respaldo si el datapack no trae el
    // nodo o le falta una curva.

    /** Nivel de la pasiva de su raza (1..4); 1 si no ha mejorado nada. */
    public static int level(Player p) {
        return com.hmc.zenkai.feature.skilltree.SkillTreeNodes.passiveLevel(PlayerStatsAttachment.get(p));
    }

    /** Valor de la curva {@code key} de la pasiva de su raza en su nivel actual. */
    public static double value(Player p, String key, double fallback) {
        Race r = raceOf(p);
        if (r == null) return fallback;
        return valueAt(r, key, level(p), fallback);
    }

    /** Valor de la curva {@code key} de la pasiva de esa raza en un nivel concreto (para la
     *  pantalla de raza y el tooltip del árbol). */
    public static double valueAt(Race race, String key, int level, double fallback) {
        var d = com.hmc.zenkai.feature.skilltree.SkillTreeData.get(
                com.hmc.zenkai.feature.skilltree.SkillTreeNodes.passiveNode(race));
        return d == null ? fallback : d.value(key, level, fallback);
    }

    public static double zenkaiBonus(Player p)  { return value(p, "zenkai_bonus", ZENKAI_BONUS); }
    public static int zenkaiDuration(Player p) {
        return (int) Math.round(value(p, "zenkai_duration_s", ZENKAI_DURATION / 20.0) * 20);
    }
    public static int zenkaiCooldown(Player p) {
        return (int) Math.round(value(p, "zenkai_cooldown_s", ZENKAI_COOLDOWN / 20.0) * 20);
    }

    // ── Consulta ─────────────────────────────────────────────────────────────

    private static Race raceOf(Player p) {
        if (p == null) return null;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        return !att.isRaceChosen() ? null : att.getRace();
    }

    public static boolean is(Player p, Race race) { return raceOf(p) == race; }

    /** Multiplicador de la regeneración de body por raza. Lo consulta RegenSystem. */
    public static double bodyRegenMult(Player p) {
        return is(p, Race.NAMEKIAN) ? value(p, "regen_mult", NAMEKIAN_REGEN_MULT) : 1.0;
    }

    /** ¿La regeneración de esta raza ES su pasiva? Lo consulta CombatRegen para no anularla
     *  completamente en combate. No es lo mismo que "tiene bonus de regen": el humano recupera
     *  estamina más rápido y aun así no entra aquí, porque su identidad no es curarse. */
    public static boolean hasRegenIdentity(Player p) {
        return is(p, Race.NAMEKIAN) || is(p, Race.MAJIN);
    }

    /** Ki que cuesta la regeneración extra namekiana, dados los puntos de body regenerados. */
    public static int namekianRegenKiCost(Player p, int bodyRegenerated) {
        double mult = value(p, "regen_mult", NAMEKIAN_REGEN_MULT);
        int extra = (int) Math.round(bodyRegenerated * (1.0 - 1.0 / mult));
        return (int) Math.ceil(extra * value(p, "regen_ki_per_point", NAMEKIAN_REGEN_KI_COST));
    }

    /** Regeneración pasiva del majin por segundo, en fracción del máximo. */
    public static double majinPassiveRegen(Player p) {
        return value(p, "regen_per_second", MAJIN_PASSIVE_REGEN);
    }

    /** Multiplicador de la regeneración de estamina por raza. */
    public static double staminaRegenMult(Player p) {
        return is(p, Race.HUMAN) ? value(p, "stamina_regen", HUMAN_STAMINA_REGEN) : 1.0;
    }

    /** Multiplicador del TP ganado. Lo consulta el reparto de TP (PlayerStatsAttachment#addTrainingTP). */
    public static double tpGainMult(Player p) {
        return is(p, Race.HUMAN) ? 1.0 + value(p, "tp_gain", HUMAN_TP_GAIN - 1.0) : 1.0;
    }

    /**
     * Descuento racial sobre el coste de ki. Se MULTIPLICA con kiCostMult del datapack. Recibe
     * el attachment y no el jugador porque lo consume PlayerStatsAttachment#kiCostMult, que es el
     * punto único por el que pasa cualquier coste de ki (técnicas, Ki Infuse) y no tiene jugador.
     */
    public static double kiCostMult(PlayerStatsAttachment att) {
        if (att == null || !att.isRaceChosen() || att.getRace() != Race.ARCOSIAN) return 1.0;
        int lvl = com.hmc.zenkai.feature.skilltree.SkillTreeNodes.passiveLevel(att);
        return 1.0 - valueAt(Race.ARCOSIAN, "ki_discount", lvl, 1.0 - ARCOSIAN_KI_DISCOUNT);
    }

    /**
     * Daño ya mitigado por la raza. Solo el MAJIN mitiga, y solo lo físico.
     *
     * "Físico" se define por exclusión de los tipos de energía y de los que ignoran armadura:
     * si un daño no lleva proyectil de ki ni es ambiental puro, el cuerpo elástico lo absorbe.
     * Se deja fuera BYPASSES_INVULNERABILITY para que /kill siga funcionando.
     */
    public static float mitigate(Player p, DamageSource source, float amount) {
        if (!is(p, Race.MAJIN)) return amount;
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return amount;
        }
        if (isKiDamage(source)) return amount;   // la energía sí le entra entera
        return amount * (1.0f - (float) value(p, "phys_reduction", MAJIN_PHYSICAL_REDUCTION));
    }

    /** Tag data-driven (ver {@link ModDamageTypes#KI_DAMAGE_TAG}) en vez de namespace
     *  hardcodeado: un addon con su propio tipo de daño de energía solo necesita su propio
     *  archivo de tag para que esta mitigación lo trate como energía. */
    private static boolean isKiDamage(DamageSource source) {
        return source.is(ModDamageTypes.KI_DAMAGE_TAG);
    }

    /** ¿Esta raza ignora el daño y la ralentización por frío? */
    public static boolean immuneToCold(Player p) {
        return is(p, Race.ARCOSIAN);
    }

    /** Clave de traducción del nombre de la pasiva, para la pantalla de stats y la de razas. */
    public static String nameKey(Race race) {
        return race == null ? "" : "race.zenkai.passive." + race.name().toLowerCase();
    }

    /** Clave de traducción de la descripción. */
    public static String descKey(Race race) {
        return race == null ? "" : "race.zenkai.passive." + race.name().toLowerCase() + ".desc";
    }
}