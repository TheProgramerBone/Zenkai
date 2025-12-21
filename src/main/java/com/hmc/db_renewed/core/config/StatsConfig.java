package com.hmc.db_renewed.core.config;

import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.Style;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

public final class StatsConfig {
    private StatsConfig() {}

    // === BUILDER/SPEC ===
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // -------------------------------------------------
    // TP / CAPS / MOVEMENT (YA LOS TENÍAS)
    // -------------------------------------------------
    private static final ModConfigSpec.DoubleValue TP_COEFFICIENT_RAW =
            BUILDER.comment("TP cost coeff: cost = 1 + invested * coeff")
                    .defineInRange("tp.coefficient", 1.5D, 0.1D, 100D);

    private static final ModConfigSpec.IntValue GLOBAL_ATTRIBUTE_CAP_RAW =
            BUILDER.comment("Global cap per attribute")
                    .defineInRange("caps.global_attribute", 1000, 1, 10000);

    private static final ModConfigSpec.DoubleValue SPEED_MULT_CAP_RAW =
            BUILDER.comment("Max movement multiplier (cap)")
                    .defineInRange("speed.multiplier_cap", 3.0D, 1.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue FLY_MULT_CAP_RAW =
            BUILDER.comment("Max fly multiplier (cap)")
                    .defineInRange("fly.multiplier_cap", 5.0D, 1.0D, 10.0D);

    private static final ModConfigSpec.IntValue REGEN_BODY_RAW =
            BUILDER.comment("Body regen percent per second (1 = 1% of max per second)")
                    .defineInRange("regen.base_per_second.body_percent", 5, 0, 100);

    private static final ModConfigSpec.IntValue REGEN_STAMINA_RAW =
            BUILDER.comment("Stamina regen percent per second (1 = 1% of max per second)")
                    .defineInRange("regen.base_per_second.stamina_percent", 10, 0, 100);

    private static final ModConfigSpec.IntValue REGEN_ENERGY_RAW =
            BUILDER.comment("Energy/Ki regen percent per second (1 = 1% of max per second)")
                    .defineInRange("regen.base_per_second.energy_percent", 5, 0, 100);

    private static final ModConfigSpec.DoubleValue MOVE_SCALING_RAW =
            BUILDER.comment("How DEX-derived Speed translates to move % per 100 points (1.0 => +100%)")
                    .defineInRange("scaling.movement", 1.0D, 0.01D, 10.0D);

    private static final ModConfigSpec.DoubleValue FLY_SCALING_RAW =
            BUILDER.comment("How DEX-derived FlySpeed translates to fly % per 100 points")
                    .defineInRange("scaling.fly", 1.0D, 0.01D, 10.0D);

    // -------------------------------------------------
    // === NEW === BASE STATS POR RAZA (STR, DEX, CON, WIL, SPI, MND)
    // -------------------------------------------------
    // IMPORTANTE: el orden es SIEMPRE:
    // [0]=STR, [1]=DEX, [2]=CON, [3]=WIL, [4]=SPI, [5]=MND

    private static final int[] HUMAN_BASE_DEFAULT     = {10, 10, 10, 10, 10, 10};
    private static final int[] SAIYAN_BASE_DEFAULT    = {14, 12, 10,  8,  6, 10};
    private static final int[] NAMEKIAN_BASE_DEFAULT  = { 8, 10,  8, 11, 13, 10};
    private static final int[] ARCOSIAN_BASE_DEFAULT  = { 8, 10,  8, 12, 12, 10};
    private static final int[] MAJIN_BASE_DEFAULT     = {10, 10,  8,  8, 10, 10};

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> HUMAN_BASE_RAW =
            BUILDER.comment("Base attributes for HUMAN: [STR, DEX, CON, WIL, SPI, MND]")
                    .defineList("race.human.base",
                            ints(HUMAN_BASE_DEFAULT),
                            o -> o instanceof Integer);

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> SAIYAN_BASE_RAW =
            BUILDER.comment("Base attributes for SAIYAN: [STR, DEX, CON, WIL, SPI, MND]")
                    .defineList("race.saiyan.base",
                            ints(SAIYAN_BASE_DEFAULT),
                            o -> o instanceof Integer);

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> NAMEKIAN_BASE_RAW =
            BUILDER.comment("Base attributes for NAMEKIAN: [STR, DEX, CON, WIL, SPI, MND]")
                    .defineList("race.namekian.base",
                            ints(NAMEKIAN_BASE_DEFAULT),
                            o -> o instanceof Integer);

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> ARCOSIAN_BASE_RAW =
            BUILDER.comment("Base attributes for ARCOSIAN: [STR, DEX, CON, WIL, SPI, MND]")
                    .defineList("race.arcosian.base",
                            ints(ARCOSIAN_BASE_DEFAULT),
                            o -> o instanceof Integer);

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> MAJIN_BASE_RAW =
            BUILDER.comment("Base attributes for MAJIN: [STR, DEX, CON, WIL, SPI, MND]")
                    .defineList("race.majin.base",
                            ints(MAJIN_BASE_DEFAULT),
                            o -> o instanceof Integer);


    // -------------------------------------------------
    // === NEW === MULTIPLICADORES POR RAZA
    // -------------------------------------------------
    // Orden: [STR, CON, DEX, WIL, SPI, MND]
    // (copiados de tus switches de recalcAll)

    private static final double[] HUMAN_MULT_DEFAULT    = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
    private static final double[] SAIYAN_MULT_DEFAULT   = {1.3, 1.0, 1.2, 0.8, 0.7, 1.0};
    private static final double[] NAMEKIAN_MULT_DEFAULT = {0.8, 0.9, 0.9, 1.1, 1.3, 1.0};
    private static final double[] ARCOSIAN_MULT_DEFAULT = {0.9, 0.9, 1.0, 1.2, 1.1, 1.0};
    private static final double[] MAJIN_MULT_DEFAULT    = {0.9, 1.3, 0.9, 1.1, 0.8, 1.0};

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> HUMAN_MULT_RAW =
            BUILDER.comment("Race multipliers for HUMAN: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("race.human.mult",
                            doubles(HUMAN_MULT_DEFAULT),
                            o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> SAIYAN_MULT_RAW =
            BUILDER.comment("Race multipliers for SAIYAN: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("race.saiyan.mult",
                            doubles(SAIYAN_MULT_DEFAULT),
                            o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> NAMEKIAN_MULT_RAW =
            BUILDER.comment("Race multipliers for NAMEKIAN: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("race.namekian.mult",
                            doubles(NAMEKIAN_MULT_DEFAULT),
                            o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> ARCOSIAN_MULT_RAW =
            BUILDER.comment("Race multipliers for ARCOSIAN: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("race.arcosian.mult",
                            doubles(ARCOSIAN_MULT_DEFAULT),
                            o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> MAJIN_MULT_RAW =
            BUILDER.comment("Race multipliers for MAJIN: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("race.majin.mult",
                            doubles(MAJIN_MULT_DEFAULT),
                            o -> o instanceof Number);

    // -------------------------------------------------
    // === NEW === MULTIPLICADORES POR ESTILO
    // -------------------------------------------------
    // Orden: [STR, CON, DEX, WIL, SPI, MND]
    // Copiados de tus sSTR, sCON, etc.

    private static final double[] WARRIOR_MULT_DEFAULT =      {1.2, 1.1, 1.3, 0.8, 0.8, 1.0};
    private static final double[] MARTIAL_MULT_DEFAULT =      {1.1, 1.0, 1.0, 1.1, 1.0, 1.0};
    private static final double[] SPIRITUALIST_MULT_DEFAULT = {0.9, 0.9, 0.9, 1.3, 1.2, 1.0};

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> WARRIOR_MULT_RAW =
            BUILDER.comment("Style multipliers for WARRIOR: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("style.warrior.mult",
                            doubles(WARRIOR_MULT_DEFAULT),
                            o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> MARTIAL_MULT_RAW =
            BUILDER.comment("Style multipliers for MARTIAL_ARTIST: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("style.martial_artist.mult",
                            doubles(MARTIAL_MULT_DEFAULT),
                            o -> o instanceof Number);

    private static final ModConfigSpec.ConfigValue<List<? extends Double>> SPIRITUALIST_MULT_RAW =
            BUILDER.comment("Style multipliers for SPIRITUALIST: [STR, CON, DEX, WIL, SPI, MND]")
                    .defineList("style.spiritualist.mult",
                            doubles(SPIRITUALIST_MULT_DEFAULT),
                            o -> o instanceof Number);

    // -------------------------------------------------
    // BUILD
    // -------------------------------------------------
    public static final ModConfigSpec SPEC = BUILDER.build();

    // === Caché segura (sólo lectura) ===
    private static volatile double TP_COEFFICIENT = 1.5D;
    private static volatile int GLOBAL_ATTRIBUTE_CAP = 200;
    private static volatile double SPEED_MULT_CAP = 2.0D;
    private static volatile double FLY_MULT_CAP = 2.0D;
    private static volatile int REGEN_BODY = 1, REGEN_STAMINA = 1, REGEN_ENERGY = 1;
    private static volatile double MOVE_SCALING = 1.0D, FLY_SCALING = 1.0D;

    // === NEW: cachés para race/style ===
    private static final EnumMap<Race, int[]> RACE_BASES = new EnumMap<>(Race.class);
    private static final EnumMap<Race, double[]> RACE_MULTS = new EnumMap<>(Race.class);
    private static final EnumMap<Style, double[]> STYLE_MULTS = new EnumMap<>(Style.class);

    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        TP_COEFFICIENT       = TP_COEFFICIENT_RAW.get();
        GLOBAL_ATTRIBUTE_CAP = GLOBAL_ATTRIBUTE_CAP_RAW.get();
        SPEED_MULT_CAP       = SPEED_MULT_CAP_RAW.get();
        FLY_MULT_CAP         = FLY_MULT_CAP_RAW.get();
        REGEN_BODY           = REGEN_BODY_RAW.get();
        REGEN_STAMINA        = REGEN_STAMINA_RAW.get();
        REGEN_ENERGY         = REGEN_ENERGY_RAW.get();
        MOVE_SCALING         = MOVE_SCALING_RAW.get();
        FLY_SCALING          = FLY_SCALING_RAW.get();

        // === NEW: cargar race bases ===
        RACE_BASES.clear();
        RACE_BASES.put(Race.HUMAN,    toIntArray(HUMAN_BASE_RAW.get(),    HUMAN_BASE_DEFAULT));
        RACE_BASES.put(Race.SAIYAN,   toIntArray(SAIYAN_BASE_RAW.get(),   SAIYAN_BASE_DEFAULT));
        RACE_BASES.put(Race.NAMEKIAN, toIntArray(NAMEKIAN_BASE_RAW.get(), NAMEKIAN_BASE_DEFAULT));
        RACE_BASES.put(Race.ARCOSIAN, toIntArray(ARCOSIAN_BASE_RAW.get(), ARCOSIAN_BASE_DEFAULT));
        RACE_BASES.put(Race.MAJIN,    toIntArray(MAJIN_BASE_RAW.get(),    MAJIN_BASE_DEFAULT));

        // === NEW: cargar race multipliers ===
        RACE_MULTS.clear();
        RACE_MULTS.put(Race.HUMAN,    toDoubleArray(HUMAN_MULT_RAW.get(),    HUMAN_MULT_DEFAULT));
        RACE_MULTS.put(Race.SAIYAN,   toDoubleArray(SAIYAN_MULT_RAW.get(),   SAIYAN_MULT_DEFAULT));
        RACE_MULTS.put(Race.NAMEKIAN, toDoubleArray(NAMEKIAN_MULT_RAW.get(), NAMEKIAN_MULT_DEFAULT));
        RACE_MULTS.put(Race.ARCOSIAN, toDoubleArray(ARCOSIAN_MULT_RAW.get(), ARCOSIAN_MULT_DEFAULT));
        RACE_MULTS.put(Race.MAJIN,    toDoubleArray(MAJIN_MULT_RAW.get(),    MAJIN_MULT_DEFAULT));

        // === NEW: cargar style multipliers ===
        STYLE_MULTS.clear();
        STYLE_MULTS.put(Style.WARRIOR,        toDoubleArray(WARRIOR_MULT_RAW.get(),        WARRIOR_MULT_DEFAULT));
        STYLE_MULTS.put(Style.MARTIAL_ARTIST, toDoubleArray(MARTIAL_MULT_RAW.get(),        MARTIAL_MULT_DEFAULT));
        STYLE_MULTS.put(Style.SPIRITUALIST,   toDoubleArray(SPIRITUALIST_MULT_RAW.get(),   SPIRITUALIST_MULT_DEFAULT));
    }

    // === Getters públicos (thread-safe) ===
    public static double tpCoefficient()      { return TP_COEFFICIENT; }
    public static int globalAttributeCap()    { return GLOBAL_ATTRIBUTE_CAP; }
    public static double speedMultiplierCap() { return SPEED_MULT_CAP; }
    public static double flyMultiplierCap()   { return FLY_MULT_CAP; }

    public static int baseRegenBody()         { return REGEN_BODY; }
    public static int baseRegenStamina()      { return REGEN_STAMINA; }
    public static int baseRegenEnergy()       { return REGEN_ENERGY; }

    public static double movementScaling()    { return MOVE_SCALING; }
    public static double flyScaling()         { return FLY_SCALING; }

    // === NEW: getters para bases y multiplicadores ===

    /** Devuelve una copia de los stats base de la raza: [STR, DEX, CON, WIL, SPI, MND]. */
    public static int[] raceBaseAttributes(Race race) {
        int[] def = switch (race) {
            case HUMAN    -> HUMAN_BASE_DEFAULT;
            case SAIYAN   -> SAIYAN_BASE_DEFAULT;
            case NAMEKIAN -> NAMEKIAN_BASE_DEFAULT;
            case ARCOSIAN -> ARCOSIAN_BASE_DEFAULT;
            case MAJIN    -> MAJIN_BASE_DEFAULT;
        };
        int[] v = RACE_BASES.getOrDefault(race, def);
        return v.clone();
    }

    /** Devuelve [mSTR, mCON, mDEX, mWIL, mSPI, mMND] de la raza. */
    public static double[] raceMultipliers(Race race) {
        double[] def = switch (race) {
            case HUMAN    -> HUMAN_MULT_DEFAULT;
            case SAIYAN   -> SAIYAN_MULT_DEFAULT;
            case NAMEKIAN -> NAMEKIAN_MULT_DEFAULT;
            case ARCOSIAN -> ARCOSIAN_MULT_DEFAULT;
            case MAJIN    -> MAJIN_MULT_DEFAULT;
        };
        double[] v = RACE_MULTS.getOrDefault(race, def);
        return v.clone();
    }

    /** Devuelve [sSTR, sCON, sDEX, sWIL, sSPI, sMND] del estilo. */
    public static double[] styleMultipliers(Style style) {
        double[] def = switch (style) {
            case WARRIOR        -> WARRIOR_MULT_DEFAULT;
            case MARTIAL_ARTIST -> MARTIAL_MULT_DEFAULT;
            case SPIRITUALIST   -> SPIRITUALIST_MULT_DEFAULT;
        };
        double[] v = STYLE_MULTS.getOrDefault(style, def);
        return v.clone();
    }

    // === Helpers internos ===

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
