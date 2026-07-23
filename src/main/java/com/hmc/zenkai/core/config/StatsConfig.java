package com.hmc.zenkai.core.config;

import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.Style;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

/**
 * Config común del mod. Patrón: cada opción tiene su *_RAW (spec) y una copia volátil que se
 * refresca en onConfigLoad; el resto del código lee SOLO los getters, que son thread-safe.
 *
 * IMPORTANTE: los valores iniciales de las copias volátiles deben coincidir con el default de
 * su *_RAW. Se usan durante el arranque, antes de que la config cargue, y si difieren el mod
 * trabaja con números equivocados en esa ventana.
 *
 * Orden de atributos en las listas de raza/estilo: [STR, DEX, CON, WIL, SPI, MND] para las bases
 * y [STR, CON, DEX, WIL, SPI, MND] para los multiplicadores (heredado de recalcAll).
 */
public final class StatsConfig {
    private StatsConfig() {}

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
            BUILDER.comment("If (vanillaFinalDamage - modDefense) <= 0, deal at least this % of vanilla final damage. 0.01 = 1%")
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

    // =====================================================================
    // SPEC — Bases y multiplicadores por raza / estilo
    // =====================================================================

    // Bases: [STR, DEX, CON, WIL, SPI, MND]
    private static final int[] HUMAN_BASE_DEFAULT    = {10, 10, 10, 10, 10, 10};
    private static final int[] SAIYAN_BASE_DEFAULT   = {14, 12, 10,  8,  6, 10};
    private static final int[] NAMEKIAN_BASE_DEFAULT = { 8, 10,  8, 11, 13, 10};
    private static final int[] ARCOSIAN_BASE_DEFAULT = { 8, 10,  8, 12, 12, 10};
    private static final int[] MAJIN_BASE_DEFAULT    = {10, 10,  8,  8, 10, 10};

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> HUMAN_BASE_RAW =
            BUILDER.comment("Base attributes for HUMAN: [STR, DEX, CON, WIL, SPI, MND]")
                    .defineList("race.human.base", ints(HUMAN_BASE_DEFAULT), o -> o instanceof Integer);

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> SAIYAN_BASE_RAW =
            BUILDER.comment("Base attributes for SAIYAN: [STR, DEX, CON, WIL, SPI, MND]")
                    .defineList("race.saiyan.base", ints(SAIYAN_BASE_DEFAULT), o -> o instanceof Integer);

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> NAMEKIAN_BASE_RAW =
            BUILDER.comment("Base attributes for NAMEKIAN: [STR, DEX, CON, WIL, SPI, MND]")
                    .defineList("race.namekian.base", ints(NAMEKIAN_BASE_DEFAULT), o -> o instanceof Integer);

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> ARCOSIAN_BASE_RAW =
            BUILDER.comment("Base attributes for ARCOSIAN: [STR, DEX, CON, WIL, SPI, MND]")
                    .defineList("race.arcosian.base", ints(ARCOSIAN_BASE_DEFAULT), o -> o instanceof Integer);

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> MAJIN_BASE_RAW =
            BUILDER.comment("Base attributes for MAJIN: [STR, DEX, CON, WIL, SPI, MND]")
                    .defineList("race.majin.base", ints(MAJIN_BASE_DEFAULT), o -> o instanceof Integer);

    // Multiplicadores: [STR, CON, DEX, WIL, SPI, MND]
    private static final double[] HUMAN_MULT_DEFAULT    = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
    private static final double[] SAIYAN_MULT_DEFAULT   = {1.3, 1.0, 1.2, 0.8, 0.7, 1.0};
    private static final double[] NAMEKIAN_MULT_DEFAULT = {0.8, 0.9, 0.9, 1.1, 1.3, 1.0};
    private static final double[] ARCOSIAN_MULT_DEFAULT = {0.9, 0.9, 1.0, 1.2, 1.1, 1.0};
    private static final double[] MAJIN_MULT_DEFAULT    = {0.9, 1.3, 0.9, 1.1, 0.8, 1.0};

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> HUMAN_MULT_RAW =
            BUILDER.comment("Race multipliers for HUMAN: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("race.human.mult", doubles(HUMAN_MULT_DEFAULT), o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> SAIYAN_MULT_RAW =
            BUILDER.comment("Race multipliers for SAIYAN: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("race.saiyan.mult", doubles(SAIYAN_MULT_DEFAULT), o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> NAMEKIAN_MULT_RAW =
            BUILDER.comment("Race multipliers for NAMEKIAN: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("race.namekian.mult", doubles(NAMEKIAN_MULT_DEFAULT), o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> ARCOSIAN_MULT_RAW =
            BUILDER.comment("Race multipliers for ARCOSIAN: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("race.arcosian.mult", doubles(ARCOSIAN_MULT_DEFAULT), o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> MAJIN_MULT_RAW =
            BUILDER.comment("Race multipliers for MAJIN: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("race.majin.mult", doubles(MAJIN_MULT_DEFAULT), o -> o instanceof Number);

    private static final double[] WARRIOR_MULT_DEFAULT      = {1.2, 1.1, 1.3, 0.8, 0.8, 1.0};
    private static final double[] MARTIAL_MULT_DEFAULT      = {1.1, 1.0, 1.0, 1.1, 1.0, 1.0};
    private static final double[] SPIRITUALIST_MULT_DEFAULT = {0.9, 0.9, 0.9, 1.3, 1.2, 1.0};

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> WARRIOR_MULT_RAW =
            BUILDER.comment("Style multipliers for WARRIOR: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("style.warrior.mult", doubles(WARRIOR_MULT_DEFAULT), o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> MARTIAL_MULT_RAW =
            BUILDER.comment("Style multipliers for MARTIAL_ARTIST: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("style.martial_artist.mult", doubles(MARTIAL_MULT_DEFAULT), o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> SPIRITUALIST_MULT_RAW =
            BUILDER.comment("Style multipliers for SPIRITUALIST: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("style.spiritualist.mult", doubles(SPIRITUALIST_MULT_DEFAULT), o -> o instanceof Number);

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
    private static volatile double MAJIN_STAT_BONUS = 0.10D;

    private static volatile double TRAIN_DMG_TP = 0.02D, TRAIN_AIR_TP = 0.0001D,
            TRAIN_AIR_COST = 0.04D, TRAIN_HALF_LIFE = 0.10D, TRAIN_DECAY = 0.01D,
            TRAIN_HTC_MULT = 2.0D, TRAIN_MIN_EFF = 0.05D;
    private static volatile int TRAIN_AIR_TICKS = 10;

    private static final EnumMap<Race, int[]>     RACE_BASES  = new EnumMap<>(Race.class);
    private static final EnumMap<Race, double[]>  RACE_MULTS  = new EnumMap<>(Race.class);
    private static final EnumMap<Style, double[]> STYLE_MULTS = new EnumMap<>(Style.class);

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

        RACE_BASES.clear();
        RACE_BASES.put(Race.HUMAN,    toIntArray(HUMAN_BASE_RAW.get(),    HUMAN_BASE_DEFAULT));
        RACE_BASES.put(Race.SAIYAN,   toIntArray(SAIYAN_BASE_RAW.get(),   SAIYAN_BASE_DEFAULT));
        RACE_BASES.put(Race.NAMEKIAN, toIntArray(NAMEKIAN_BASE_RAW.get(), NAMEKIAN_BASE_DEFAULT));
        RACE_BASES.put(Race.ARCOSIAN, toIntArray(ARCOSIAN_BASE_RAW.get(), ARCOSIAN_BASE_DEFAULT));
        RACE_BASES.put(Race.MAJIN,    toIntArray(MAJIN_BASE_RAW.get(),    MAJIN_BASE_DEFAULT));

        RACE_MULTS.clear();
        RACE_MULTS.put(Race.HUMAN,    toDoubleArray(HUMAN_MULT_RAW.get(),    HUMAN_MULT_DEFAULT));
        RACE_MULTS.put(Race.SAIYAN,   toDoubleArray(SAIYAN_MULT_RAW.get(),   SAIYAN_MULT_DEFAULT));
        RACE_MULTS.put(Race.NAMEKIAN, toDoubleArray(NAMEKIAN_MULT_RAW.get(), NAMEKIAN_MULT_DEFAULT));
        RACE_MULTS.put(Race.ARCOSIAN, toDoubleArray(ARCOSIAN_MULT_RAW.get(), ARCOSIAN_MULT_DEFAULT));
        RACE_MULTS.put(Race.MAJIN,    toDoubleArray(MAJIN_MULT_RAW.get(),    MAJIN_MULT_DEFAULT));

        STYLE_MULTS.clear();
        STYLE_MULTS.put(Style.WARRIOR,        toDoubleArray(WARRIOR_MULT_RAW.get(),      WARRIOR_MULT_DEFAULT));
        STYLE_MULTS.put(Style.MARTIAL_ARTIST, toDoubleArray(MARTIAL_MULT_RAW.get(),      MARTIAL_MULT_DEFAULT));
        STYLE_MULTS.put(Style.SPIRITUALIST,   toDoubleArray(SPIRITUALIST_MULT_RAW.get(), SPIRITUALIST_MULT_DEFAULT));
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

    public static double trainingDamageTpFactor()        { return TRAIN_DMG_TP; }
    public static double trainingAirTpFactor()           { return TRAIN_AIR_TP; }
    public static double trainingAirStaminaCostPct()     { return TRAIN_AIR_COST; }
    public static int    trainingAirMinTicks()           { return TRAIN_AIR_TICKS; }
    public static double trainingFatigueHalfLife()       { return TRAIN_HALF_LIFE; }
    public static double trainingFatigueDecayPerMinute() { return TRAIN_DECAY; }
    public static double trainingHtcMultiplier()         { return TRAIN_HTC_MULT; }
    public static double trainingMinEfficiency()         { return TRAIN_MIN_EFF; }

    /** Copia de los stats base de la raza: [STR, DEX, CON, WIL, SPI, MND]. */
    public static int[] raceBaseAttributes(Race race) {
        int[] def = switch (race) {
            case HUMAN    -> HUMAN_BASE_DEFAULT;
            case SAIYAN   -> SAIYAN_BASE_DEFAULT;
            case NAMEKIAN -> NAMEKIAN_BASE_DEFAULT;
            case ARCOSIAN -> ARCOSIAN_BASE_DEFAULT;
            case MAJIN    -> MAJIN_BASE_DEFAULT;
        };
        return RACE_BASES.getOrDefault(race, def).clone();
    }

    /** Copia de [mSTR, mCON, mDEX, mWIL, mSPI, mMND] de la raza. */
    public static double[] raceMultipliers(Race race) {
        double[] def = switch (race) {
            case HUMAN    -> HUMAN_MULT_DEFAULT;
            case SAIYAN   -> SAIYAN_MULT_DEFAULT;
            case NAMEKIAN -> NAMEKIAN_MULT_DEFAULT;
            case ARCOSIAN -> ARCOSIAN_MULT_DEFAULT;
            case MAJIN    -> MAJIN_MULT_DEFAULT;
        };
        return RACE_MULTS.getOrDefault(race, def).clone();
    }

    /** Copia de [sSTR, sCON, sDEX, sWIL, sSPI, sMND] del estilo. */
    public static double[] styleMultipliers(Style style) {
        double[] def = switch (style) {
            case WARRIOR        -> WARRIOR_MULT_DEFAULT;
            case MARTIAL_ARTIST -> MARTIAL_MULT_DEFAULT;
            case SPIRITUALIST   -> SPIRITUALIST_MULT_DEFAULT;
        };
        return STYLE_MULTS.getOrDefault(style, def).clone();
    }

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