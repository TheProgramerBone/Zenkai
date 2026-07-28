package com.hmc.zenkai.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Config común del mod. Patrón: cada opción tiene su *_RAW (spec) y una copia volátil que se
 * refresca en onConfigLoad; el resto del código lee SOLO los getters, que son thread-safe.
 * IMPORTANTE: los valores iniciales de las copias volátiles deben coincidir con el default de
 * su *_RAW. Se usan durante el arranque, antes de que la config cargue, y si difieren el mod
 * trabaja con números equivocados en esa ventana.
 * Orden de atributos en las listas de raza/estilo: [STR, DEX, CON, WIL, SPI, MND] para las bases
 * y [STR, CON, DEX, WIL, SPI, MND] para los multiplicadores (heredado de recalcAll).
 */
public final class CommonConfig {
    private CommonConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // =====================================================================
    // SPEC — TP y topes
    // =====================================================================

    private static final ModConfigSpec.DoubleValue TP_COEFFICIENT_RAW =
            BUILDER.comment("TP cost growth: cost = points * (1 + coef * avgInvested)")
                    .defineInRange("tp.coefficient", 0.00001D, 0.0D, 100D);

    private static final ModConfigSpec.IntValue GLOBAL_ATTRIBUTE_CAP_RAW =
            BUILDER.comment("Max per attribute. 5 counted attrs x 200000 = PL cap 1,000,000")
                    .defineInRange("caps.global_attribute", 200000, 1, 1000000);

    // =====================================================================
    // SPEC — Movimiento y vuelo
    // =====================================================================

    private static final ModConfigSpec.DoubleValue SPEED_MULT_CAP_RAW =
            BUILDER.comment("Max movement multiplier (cap)")
                    .defineInRange("speed.multiplier_cap", 3.0D, 1.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue MOVE_SCALING_RAW =
            BUILDER.comment("How DEX-derived Speed translates to move % per 100 points (1.0 => +100%)")
                    .defineInRange("scaling.movement", 1.0D, 0.01D, 10.0D);

    private static final ModConfigSpec.DoubleValue RUN_STAMINA_DRAIN_RAW =
            BUILDER.comment("Stamina drained per SECOND while sprinting (before the Run skill reduction)")
                    .defineInRange("run.stamina_drain_per_second", 2.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue FLY_MULT_CAP_RAW =
            BUILDER.comment("Max fly multiplier (cap)")
                    .defineInRange("fly.multiplier_cap", 15.0D, 1.0D, 50.0D);

    private static final ModConfigSpec.DoubleValue FLY_SCALING_RAW =
            BUILDER.comment("How DEX-derived FlySpeed translates to fly % per 100 points")
                    .defineInRange("scaling.fly", 0.25D, 0.01D, 8.0D);

    private static final ModConfigSpec.DoubleValue FLY_BASE_SPEED_RAW =
            BUILDER.comment("Base flying speed before stat/skill multipliers (vanilla creative = 0.05).",
                            "Blocks per second ~= speed * 202")
                    .defineInRange("fly.base_speed", 0.05D, 0.01D, 0.5D);

    private static final ModConfigSpec.DoubleValue FLY_KI_DRAIN_RAW =
            BUILDER.comment("Ki drained per TICK while flying in turbo (before the Fly skill reduction)")
                    .defineInRange("fly.ki_drain_per_tick", 0.15D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue TURBO_DRAIN_PCT_PER_SEC_RAW =
            BUILDER.comment("Energy drained per second while in turbo, as a fraction of energyMax (0.005 = 0.5%/s)")
                    .defineInRange("aura.turbo_drain_pct_per_sec", 0.005D, 0.0D, 1.0D);

    // =====================================================================
    // SPEC — Entidades sin stats propias
    // =====================================================================

    private static final ModConfigSpec.BooleanValue VANILLA_STATS_FALLBACK_RAW =
            BUILDER.comment("Give derived Zenkai stats to mobs that have no zenkai_entities JSON.",
                            "Off = they stay vanilla-scaled and PvE becomes irrelevant past character creation")
                    .define("entity.vanilla_stats_fallback", true);

    private static final ModConfigSpec.DoubleValue VANILLA_BODY_FACTOR_RAW =
            BUILDER.comment("Fallback mobs: CON (and therefore bodyMax) = maxHealth * factor")
                    .defineInRange("entity.vanilla_body_factor", 15.0D, 0.1D, 1000.0D);


    private static final ModConfigSpec.DoubleValue VANILLA_TP_REWARD_FACTOR_RAW =
            BUILDER.comment("Fallback mobs: TP reward as a fraction of the automatic one (PL-based).",
                            "Every mob starts granting TP once the fallback is on, so mob farms can outpace training")
                    .defineInRange("entity.vanilla_tp_reward_factor", 0.25D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_PASSIVE_FACTOR_RAW =
            BUILDER.comment("Fallback mobs, passive/ambient: CON = maxHealth * factor.",
                            "1.0 keeps villagers around PL 20, canon for a human with no ki training")
                    .defineInRange("entity.factor.passive", 1.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_HOSTILE_FACTOR_RAW =
            BUILDER.comment("Fallback mobs, MobCategory.MONSTER: CON = maxHealth * factor")
                    .defineInRange("entity.factor.hostile", 12.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_BOSS_FACTOR_RAW =
            BUILDER.comment("Fallback mobs tagged zenkai:bosses: CON = maxHealth * factor")
                    .defineInRange("entity.factor.boss", 40.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_DAMAGE_RATIO_RAW =
            BUILDER.comment("Fallback mobs: how much of the category factor applies to damage.",
                            "Below 1.0 on purpose: sharing it made creepers and the warden one-shot players")
                    .defineInRange("entity.factor.damage_ratio", 0.4D, 0.0D, 10.0D);

    // =====================================================================
    // SPEC — Pools y regeneración
    // =====================================================================

    private static final ModConfigSpec.DoubleValue BODY_SCALE_RAW =
            BUILDER.comment("bodyMax = 10 + CON * scale. WARNING: multiplies time-to-kill by the same factor")
                    .defineInRange("pools.body_scale", 1.0D, 0.1D, 1000.0D);

    private static final ModConfigSpec.DoubleValue STAMINA_SCALE_RAW =
            BUILDER.comment("staminaMax = 90 + CON * scale")
                    .defineInRange("pools.stamina_scale", 1.0D, 0.1D, 1000.0D);

    private static final ModConfigSpec.DoubleValue ENERGY_SCALE_RAW =
            BUILDER.comment("energyMax = 90 + SPI * scale")
                    .defineInRange("pools.energy_scale", 1.0D, 0.1D, 1000.0D);

    private static final ModConfigSpec.DoubleValue REGEN_BODY_RAW =
            BUILDER.comment("Body regen percent per second (1.0 = 1% of max per second)")
                    .defineInRange("regen.base_per_second.body_percent", 1.5D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue REGEN_STAMINA_RAW =
            BUILDER.comment("Stamina regen percent per second (1.0 = 1% of max per second)")
                    .defineInRange("regen.base_per_second.stamina_percent", 3.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue REGEN_ENERGY_RAW =
            BUILDER.comment("Energy/Ki regen percent per second (1.0 = 1%). Meditation multiplies this, and also drives the charge rate")
                    .defineInRange("regen.base_per_second.energy_percent", 1.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue FOOD_KI_RAW =
            BUILDER.comment("Ki restored per nutrition point when finishing a food item, as % of energyMax (2.0 = 2%)")
                    .defineInRange("regen.food.ki_percent_per_nutrition", 2.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue FOOD_STAMINA_RAW =
            BUILDER.comment("Stamina restored per nutrition point when finishing a food item, as % of staminaMax (3.0 = 3%)")
                    .defineInRange("regen.food.stamina_percent_per_nutrition", 3.0D, 0.0D, 100.0D);

    // =====================================================================
    // SPEC — Combate, técnicas y detección
    // =====================================================================

    private static final ModConfigSpec.DoubleValue MIN_DAMAGE_PERCENT_RAW =
            BUILDER.comment("Damage floor as a fraction of the incoming hit, after defense reduction. 0.05 = 5%")
                    .defineInRange("combat.min_damage_percent", 0.05D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue TECHNIQUE_MAX_SLOTS_RAW =
            BUILDER.comment("Ki techniques: max technique slots per player")
                    .defineInRange("technique.max_slots", 12, 1, 24);

    private static final ModConfigSpec.IntValue SENSE_KI_RANGE_RAW =
            BUILDER.comment("Sense Ki: range in blocks")
                    .defineInRange("sense_ki.range", 64, 8, 256);

    private static final ModConfigSpec.DoubleValue SENSE_KI_SIMILAR_RAW =
            BUILDER.comment("Sense Ki: 'similar or stronger' = fraction of your PL")
                    .defineInRange("sense_ki.similar_threshold", 0.8D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue VANILLA_PL_FACTOR_RAW =
            BUILDER.comment("PL of vanilla mobs / raceless players = max_health * factor")
                    .defineInRange("power_level.vanilla_factor", 1.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.IntValue SCOUTER_RANGE_RAW =
            BUILDER.comment("Scouter: crosshair scan range in blocks")
                    .defineInRange("scouter.range", 64, 8, 256);

    private static final ModConfigSpec.DoubleValue KI_COST_PER_POWER_RAW =
            BUILDER.comment("Ki cost per point of ki power (WIL). Higher = ki drains faster, SPI matters more.")
                    .defineInRange("cost.ki_per_power", 0.70D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue MELEE_STAMINA_PER_HIT_RAW =
            BUILDER.comment("Stamina per point of melee damage (STR). Higher = fewer hits, CON matters more.")
                    .defineInRange("cost.melee_stamina_per_hit", 0.10D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue WEAPON_SCALE_RAW =
            BUILDER.comment("Weapon damage as a MULTIPLIER on melee: mult = 1 + (attack_damage - 1) * scale. 0.04 = diamond sword x1.28. Set to 0 to make weapons irrelevant again.")
                    .defineInRange("combat.weapon_scale", 0.04D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue KI_PER_BONUS_DAMAGE_RAW =
            BUILDER.comment("Ki spent per point of BONUS damage added by Ki Infuse / Ki Fist. Higher = fewer empowered hits per bar.")
                    .defineInRange("cost.ki_per_bonus_damage", 0.50D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue COMBAT_ATTACK_SPEED_RAW =
            BUILDER.comment("Attack speed while in combat mode. Vanilla base is 4.0 (5-tick recharge); anything BELOW 4.0 enables the vanilla swing cooldown and the crosshair indicator. 1.6 = sword-like, 12.5 ticks.")
                    .defineInRange("combat.attack_speed", 1.6D, 0.1D, 4.0D);

    private static final ModConfigSpec.DoubleValue OVERCHARGE_TIME_MULT_RAW =
            BUILDER.comment("Extra charge time for the 100%->200% overcharge stretch, as a multiple of the base cast time. 2.5 = the second 100% takes 2.5x as long as the first.")
                    .defineInRange("cost.overcharge_time_mult", 2.5D, 1.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue OVERCHARGE_COST_MULT_RAW =
            BUILDER.comment("Ki cost multiplier applied ONLY to the overcharged portion. 1.5 = every point past 100% costs 50% more than a normal one.")
                    .defineInRange("cost.overcharge_cost_mult", 1.5D, 1.0D, 5.0D);

    // =====================================================================
    // SPEC — Maestría y efecto Majin
    // =====================================================================

    private static final ModConfigSpec.DoubleValue FORM_MASTERY_PER_MINUTE_RAW =
            BUILDER.comment("Form mastery gained per minute while transformed (percent points, 0-100 scale)")
                    .defineInRange("mastery.form_per_minute", 0.5D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue TECH_MASTERY_PER_USE_RAW =
            BUILDER.comment("Technique mastery gained per use (percent points, 0-100 scale)")
                    .defineInRange("mastery.technique_per_use", 0.2D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue MASTERY_FORM_STAT_BONUS_RAW =
            BUILDER.comment("At 100% form mastery: bonus fraction to combat stats while transformed (0.20 = +20%)")
                    .defineInRange("mastery.form_stat_bonus", 0.20D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue MASTERY_FORM_DRAIN_RED_RAW =
            BUILDER.comment("At 100% form mastery: fraction of form ki drain removed (0.50 = -50%)")
                    .defineInRange("mastery.form_drain_reduction", 0.50D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue MASTERY_TECH_DMG_RAW =
            BUILDER.comment("At 100% technique mastery: damage bonus fraction (0.25 = +25%)")
                    .defineInRange("mastery.tech_damage_bonus", 0.25D, 0.0D, 5.0D);

    private static final ModConfigSpec.DoubleValue MASTERY_TECH_COST_RAW =
            BUILDER.comment("At 100% technique mastery: cost reduction fraction (0.30 = -30% ki/stamina)")
                    .defineInRange("mastery.tech_cost_reduction", 0.30D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue MASTERY_TECH_CAST_RAW =
            BUILDER.comment("At 100% technique mastery: charge-time reduction fraction (0.30 = charges 30% faster)")
                    .defineInRange("mastery.tech_cast_reduction", 0.30D, 0.0D, 0.95D);

    private static final ModConfigSpec.DoubleValue MAJIN_STAT_BONUS_RAW =
            BUILDER.comment("Stat bonus fraction while under the Majin effect (0.10 = +10%)")
                    .defineInRange("majin.effect_stat_bonus", 0.10D, 0.0D, 5.0D);

    // =====================================================================
    // SPEC — Entrenamiento
    // =====================================================================

    private static final ModConfigSpec.DoubleValue TRAIN_DMG_TP_RAW =
            BUILDER.comment("Training: TP per point of effective damage dealt")
                    .defineInRange("training.damage_tp_factor", 0.02D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue TRAIN_AIR_TP_RAW =
            BUILDER.comment("Training: TP per air punch = own PL * factor")
                    .defineInRange("training.air_tp_factor", 0.0001D, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue TRAIN_AIR_COST_RAW =
            BUILDER.comment("Training: air punch stamina cost as fraction of max stamina")
                    .defineInRange("training.air_stamina_cost_pct", 0.04D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue TRAIN_AIR_TICKS_RAW =
            BUILDER.comment("Training: min ticks between counted air punches")
                    .defineInRange("training.air_min_ticks", 10, 1, 200);

    private static final ModConfigSpec.DoubleValue TRAIN_HALF_LIFE_RAW =
            BUILDER.comment("Training: fatigue (session TP / own PL) at which efficiency halves")
                    .defineInRange("training.fatigue_half_life", 0.10D, 0.001D, 10.0D);

    private static final ModConfigSpec.DoubleValue TRAIN_DECAY_RAW =
            BUILDER.comment("Training: fatigue recovered per real minute of play")
                    .defineInRange("training.fatigue_decay_per_minute", 0.01D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue TRAIN_HTC_MULT_RAW =
            BUILDER.comment("Training: TP multiplier while inside the HTC")
                    .defineInRange("training.htc_multiplier", 2.0D, 1.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue TRAIN_MIN_EFF_RAW =
            BUILDER.comment("Training: efficiency floor (never drops to 0)")
                    .defineInRange("training.min_efficiency", 0.05D, 0.0D, 1.0D);


    public static final ModConfigSpec SPEC = BUILDER.build();

    // =====================================================================
    // CACHÉ VOLÁTIL — cada valor inicial DEBE igualar el default de su *_RAW
    // =====================================================================

    private static volatile double TP_COEFFICIENT = 0.00001D;
    private static volatile int    GLOBAL_ATTRIBUTE_CAP = 200000;

    private static volatile double SPEED_MULT_CAP = 3.0D;
    private static volatile double MOVE_SCALING = 1.0D;
    private static volatile double RUN_STAMINA_DRAIN = 2.0D;
    private static volatile double FLY_MULT_CAP = 15.0D;
    private static volatile double FLY_SCALING = 0.25D;
    private static volatile double FLY_BASE_SPEED = 0.05D;
    private static volatile double FLY_KI_DRAIN = 0.15D;
    private static volatile double TURBO_DRAIN_PCT_PER_SEC = 0.005D;

    private static volatile double BODY_SCALE = 1.0D, STAMINA_SCALE = 1.0D, ENERGY_SCALE = 1.0D;
    private static volatile double REGEN_BODY = 1.5, REGEN_STAMINA = 3.0, REGEN_ENERGY = 1.0;
    private static volatile double FOOD_KI_PCT = 2.0, FOOD_STAMINA_PCT = 3.0;

    private static volatile double MIN_DAMAGE_PERCENT = 0.05D;
    private static volatile int    TECHNIQUE_MAX_SLOTS = 12;
    private static volatile int    SENSE_KI_RANGE = 64;
    private static volatile double SENSE_KI_SIMILAR = 0.8D;
    private static volatile double VANILLA_PL_FACTOR = 1.0D;
    private static volatile int    SCOUTER_RANGE = 64;

    private static volatile double FORM_MASTERY_PER_MINUTE = 0.5D;
    private static volatile double TECH_MASTERY_PER_USE = 0.2D;
    private static volatile double M_FORM_STAT = 0.20D, M_FORM_DRAIN = 0.50D,
            M_TECH_DMG = 0.25D, M_TECH_COST = 0.30D, M_TECH_CAST = 0.30D;
    private static volatile double KI_COST_PER_POWER = 0.70D;
    private static volatile double MELEE_STAMINA_PER_HIT = 0.10D;
    private static volatile double WEAPON_SCALE = 0.04D;
    private static volatile double KI_PER_BONUS_DAMAGE = 0.50D;
    private static volatile double COMBAT_ATTACK_SPEED = 1.6D;
    private static volatile double OVERCHARGE_TIME_MULT = 2.5D;
    private static volatile double OVERCHARGE_COST_MULT = 1.5D;
    private static volatile double MAJIN_STAT_BONUS = 0.10D;
    private static volatile boolean VANILLA_STATS_FALLBACK = true;
    private static volatile double  VANILLA_BODY_FACTOR = 15.0D;
    private static volatile double  VANILLA_TP_REWARD_FACTOR = 0.25D;
    private static volatile double VANILLA_PASSIVE_FACTOR = 1.0D;
    private static volatile double VANILLA_HOSTILE_FACTOR = 12.0D;
    private static volatile double VANILLA_BOSS_FACTOR = 40.0D;
    private static volatile double VANILLA_DAMAGE_RATIO = 0.4D;

    private static volatile double TRAIN_DMG_TP = 0.02D, TRAIN_AIR_TP = 0.0001D,
            TRAIN_AIR_COST = 0.04D, TRAIN_HALF_LIFE = 0.10D, TRAIN_DECAY = 0.01D,
            TRAIN_HTC_MULT = 2.0D, TRAIN_MIN_EFF = 0.05D;
    private static volatile int TRAIN_AIR_TICKS = 10;

    // =====================================================================
    // CARGA
    // =====================================================================

    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) return;
        if (event.getConfig().getSpec() != SPEC) return;

        TP_COEFFICIENT       = TP_COEFFICIENT_RAW.get();
        GLOBAL_ATTRIBUTE_CAP = GLOBAL_ATTRIBUTE_CAP_RAW.get();

        SPEED_MULT_CAP    = SPEED_MULT_CAP_RAW.get();
        MOVE_SCALING      = MOVE_SCALING_RAW.get();
        RUN_STAMINA_DRAIN = RUN_STAMINA_DRAIN_RAW.get();
        FLY_MULT_CAP      = FLY_MULT_CAP_RAW.get();
        FLY_SCALING       = FLY_SCALING_RAW.get();
        FLY_BASE_SPEED    = FLY_BASE_SPEED_RAW.get();
        FLY_KI_DRAIN      = FLY_KI_DRAIN_RAW.get();
        TURBO_DRAIN_PCT_PER_SEC = TURBO_DRAIN_PCT_PER_SEC_RAW.get();

        BODY_SCALE    = BODY_SCALE_RAW.get();
        STAMINA_SCALE = STAMINA_SCALE_RAW.get();
        ENERGY_SCALE  = ENERGY_SCALE_RAW.get();
        REGEN_BODY    = REGEN_BODY_RAW.get();
        REGEN_STAMINA = REGEN_STAMINA_RAW.get();
        REGEN_ENERGY  = REGEN_ENERGY_RAW.get();
        FOOD_KI_PCT      = FOOD_KI_RAW.get();
        FOOD_STAMINA_PCT = FOOD_STAMINA_RAW.get();

        VANILLA_STATS_FALLBACK   = VANILLA_STATS_FALLBACK_RAW.get();
        VANILLA_BODY_FACTOR      = VANILLA_BODY_FACTOR_RAW.get();
        VANILLA_DAMAGE_RATIO = VANILLA_DAMAGE_RATIO_RAW.get();
        VANILLA_TP_REWARD_FACTOR = VANILLA_TP_REWARD_FACTOR_RAW.get();
        VANILLA_PASSIVE_FACTOR = VANILLA_PASSIVE_FACTOR_RAW.get();
        VANILLA_BOSS_FACTOR = VANILLA_BOSS_FACTOR_RAW.get();
        VANILLA_HOSTILE_FACTOR = VANILLA_HOSTILE_FACTOR_RAW.get();


        MIN_DAMAGE_PERCENT  = MIN_DAMAGE_PERCENT_RAW.get();
        TECHNIQUE_MAX_SLOTS = TECHNIQUE_MAX_SLOTS_RAW.get();
        SENSE_KI_RANGE      = SENSE_KI_RANGE_RAW.get();
        SENSE_KI_SIMILAR    = SENSE_KI_SIMILAR_RAW.get();
        VANILLA_PL_FACTOR   = VANILLA_PL_FACTOR_RAW.get();
        SCOUTER_RANGE       = SCOUTER_RANGE_RAW.get();

        FORM_MASTERY_PER_MINUTE = FORM_MASTERY_PER_MINUTE_RAW.get();
        TECH_MASTERY_PER_USE    = TECH_MASTERY_PER_USE_RAW.get();
        M_FORM_STAT      = MASTERY_FORM_STAT_BONUS_RAW.get();
        M_FORM_DRAIN     = MASTERY_FORM_DRAIN_RED_RAW.get();
        M_TECH_DMG       = MASTERY_TECH_DMG_RAW.get();
        M_TECH_COST      = MASTERY_TECH_COST_RAW.get();
        M_TECH_CAST      = MASTERY_TECH_CAST_RAW.get();
        MAJIN_STAT_BONUS = MAJIN_STAT_BONUS_RAW.get();

        TRAIN_DMG_TP    = TRAIN_DMG_TP_RAW.get();
        TRAIN_AIR_TP    = TRAIN_AIR_TP_RAW.get();
        TRAIN_AIR_COST  = TRAIN_AIR_COST_RAW.get();
        TRAIN_AIR_TICKS = TRAIN_AIR_TICKS_RAW.get();
        TRAIN_HALF_LIFE = TRAIN_HALF_LIFE_RAW.get();
        TRAIN_DECAY     = TRAIN_DECAY_RAW.get();
        TRAIN_HTC_MULT  = TRAIN_HTC_MULT_RAW.get();
        TRAIN_MIN_EFF   = TRAIN_MIN_EFF_RAW.get();

        KI_COST_PER_POWER     = KI_COST_PER_POWER_RAW.get();
        MELEE_STAMINA_PER_HIT = MELEE_STAMINA_PER_HIT_RAW.get();
        WEAPON_SCALE          = WEAPON_SCALE_RAW.get();
        KI_PER_BONUS_DAMAGE   = KI_PER_BONUS_DAMAGE_RAW.get();
        COMBAT_ATTACK_SPEED   = COMBAT_ATTACK_SPEED_RAW.get();
        OVERCHARGE_TIME_MULT  = OVERCHARGE_TIME_MULT_RAW.get();
        OVERCHARGE_COST_MULT  = OVERCHARGE_COST_MULT_RAW.get();
    }

    // =====================================================================
    // GETTERS (thread-safe)
    // =====================================================================

    public static double tpCoefficient()   { return TP_COEFFICIENT; }
    public static int globalAttributeCap() { return GLOBAL_ATTRIBUTE_CAP; }

    public static double speedMultiplierCap()       { return SPEED_MULT_CAP; }
    public static double movementScaling()          { return MOVE_SCALING; }
    public static double runStaminaDrainPerSecond() { return RUN_STAMINA_DRAIN; }
    public static double flyMultiplierCap()         { return FLY_MULT_CAP; }
    public static double flyScaling()               { return FLY_SCALING; }
    public static double flyBaseSpeed()             { return FLY_BASE_SPEED; }
    public static double flyKiDrainPerTick()        { return FLY_KI_DRAIN; }
    public static double turboDrainPctPerSec()      { return TURBO_DRAIN_PCT_PER_SEC; }

    public static boolean vanillaStatsFallback()   { return VANILLA_STATS_FALLBACK; }
    public static double  vanillaBodyFactor()      { return VANILLA_BODY_FACTOR; }
    public static double  vanillaDamageFactor()    { return VANILLA_DAMAGE_RATIO; }
    public static double  vanillaTpRewardFactor()  { return VANILLA_TP_REWARD_FACTOR; }
    public static double vanillaPassiveFactor() { return VANILLA_PASSIVE_FACTOR; }
    public static double vanillaHostileFactor() { return VANILLA_HOSTILE_FACTOR; }
    public static double vanillaBossFactor() { return VANILLA_BOSS_FACTOR; }
    public static double vanillaDamageRatio() { return VANILLA_DAMAGE_RATIO; }

    public static double bodyScale()      { return BODY_SCALE; }
    public static double staminaScale()   { return STAMINA_SCALE; }
    public static double energyScale()    { return ENERGY_SCALE; }
    public static double baseRegenBody()    { return REGEN_BODY; }
    public static double baseRegenStamina() { return REGEN_STAMINA; }
    public static double baseRegenEnergy()  { return REGEN_ENERGY; }
    public static double foodKiPercentPerNutrition()      { return FOOD_KI_PCT; }
    public static double foodStaminaPercentPerNutrition() { return FOOD_STAMINA_PCT; }

    public static double minDamagePercent()        { return MIN_DAMAGE_PERCENT; }
    public static int techniqueMaxSlots()          { return TECHNIQUE_MAX_SLOTS; }
    public static int senseKiRange()               { return SENSE_KI_RANGE; }
    public static double senseKiSimilarThreshold() { return SENSE_KI_SIMILAR; }
    public static double vanillaPowerLevelFactor() { return VANILLA_PL_FACTOR; }
    public static int scouterRange()               { return SCOUTER_RANGE; }

    public static double formMasteryPerMinute()      { return FORM_MASTERY_PER_MINUTE; }
    public static double techMasteryPerUse()         { return TECH_MASTERY_PER_USE; }
    public static double masteryFormStatBonus()      { return M_FORM_STAT; }
    public static double masteryFormDrainReduction() { return M_FORM_DRAIN; }
    public static double masteryTechDamageBonus()    { return M_TECH_DMG; }
    public static double masteryTechCostReduction()  { return M_TECH_COST; }
    public static double masteryTechCastReduction()  { return M_TECH_CAST; }
    public static double majinStatBonus()            { return MAJIN_STAT_BONUS; }

    public static double kiCostPerPower()    { return KI_COST_PER_POWER; }
    public static double meleeStaminaPerHit() { return MELEE_STAMINA_PER_HIT; }
    /** El arma como multiplicador del golpe, no como suma. Ver KiInfusion. */
    public static double weaponScale()       { return WEAPON_SCALE; }
    /** Ki por punto de daño EXTRA de Ki Infuse / Ki Fist. */
    public static double kiPerBonusDamage()  { return KI_PER_BONUS_DAMAGE; }

    /** attack_speed objetivo en modo combate. Por debajo de 4.0 o vanilla esconde el indicador. */
    public static double combatAttackSpeed() { return COMBAT_ATTACK_SPEED; }

    /** Cuánto más lento avanza el tramo 100%->200% respecto al primer 100%. */
    public static double overchargeTimeMult() { return OVERCHARGE_TIME_MULT; }
    /** Recargo de coste aplicado SOLO a la porción sobrecargada. */
    public static double overchargeCostMult() { return OVERCHARGE_COST_MULT; }

    public static double trainingDamageTpFactor()        { return TRAIN_DMG_TP; }
    public static double trainingAirTpFactor()           { return TRAIN_AIR_TP; }
    public static double trainingAirStaminaCostPct()     { return TRAIN_AIR_COST; }
    public static int    trainingAirMinTicks()           { return TRAIN_AIR_TICKS; }
    public static double trainingFatigueHalfLife()       { return TRAIN_HALF_LIFE; }
    public static double trainingFatigueDecayPerMinute() { return TRAIN_DECAY; }
    public static double trainingHtcMultiplier()         { return TRAIN_HTC_MULT; }
    public static double trainingMinEfficiency()         { return TRAIN_MIN_EFF; }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private static List<Integer> ints(int... vals) {
        return Arrays.stream(vals).boxed().toList();
    }

    private static List<Double> doubles(double... vals) {
        return Arrays.stream(vals).boxed().toList();
    }

    private static int[] toIntArray(List<? extends Integer> list, int[] fallback) {
        if (list == null || list.size() != 6) return fallback.clone();
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            Integer v = list.get(i);
            out[i] = (v != null) ? v : fallback[i];
        }
        return out;
    }

    private static double[] toDoubleArray(List<? extends Double> list, double[] fallback) {
        if (list == null || list.size() != 6) return fallback.clone();
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            Double v = list.get(i);
            out[i] = (v != null) ? v : fallback[i];
        }
        return out;
    }
}