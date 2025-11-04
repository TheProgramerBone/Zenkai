package com.hmc.db_renewed.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class StatsConfig {
    private StatsConfig() {}

    // === BUILDER/SPEC ===
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue TP_COEFFICIENT_RAW =
            BUILDER.comment("TP cost coeff: cost = 1 + invested * coeff")
                    .defineInRange("tp.coefficient", 1.5D, 0.1D, 100D);

    private static final ModConfigSpec.IntValue GLOBAL_ATTRIBUTE_CAP_RAW =
            BUILDER.comment("Global cap per attribute")
                    .defineInRange("caps.global_attribute", 200, 1, 10000);

    private static final ModConfigSpec.DoubleValue SPEED_MULT_CAP_RAW =
            BUILDER.comment("Max movement multiplier (cap)")
                    .defineInRange("speed.multiplier_cap", 2.0D, 1.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue FLY_MULT_CAP_RAW =
            BUILDER.comment("Max fly multiplier (cap)")
                    .defineInRange("fly.multiplier_cap", 2.0D, 1.0D, 10.0D);

    private static final ModConfigSpec.IntValue REGEN_BODY_RAW =
            BUILDER.comment("Base regen per tick for Body")
                    .defineInRange("regen.base_per_tick.body", 1, 0, 1000);

    private static final ModConfigSpec.IntValue REGEN_STAMINA_RAW =
            BUILDER.comment("Base regen per tick for Stamina")
                    .defineInRange("regen.base_per_tick.stamina", 1, 0, 1000);

    private static final ModConfigSpec.IntValue REGEN_ENERGY_RAW =
            BUILDER.comment("Base regen per tick for Energy/Ki")
                    .defineInRange("regen.base_per_tick.energy", 1, 0, 1000);

    private static final ModConfigSpec.DoubleValue MOVE_SCALING_RAW =
            BUILDER.comment("How DEX-derived Speed translates to move % per 100 points (1.0 => +100%)")
                    .defineInRange("scaling.movement", 1.0D, 0.01D, 10.0D);

    private static final ModConfigSpec.DoubleValue FLY_SCALING_RAW =
            BUILDER.comment("How DEX-derived FlySpeed translates to fly % per 100 points")
                    .defineInRange("scaling.fly", 1.0D, 0.01D, 10.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // === Caché segura (sólo lectura) ===
    private static volatile double TP_COEFFICIENT = 1.5D;
    private static volatile int GLOBAL_ATTRIBUTE_CAP = 200;
    private static volatile double SPEED_MULT_CAP = 2.0D;
    private static volatile double FLY_MULT_CAP = 2.0D;
    private static volatile int REGEN_BODY = 1, REGEN_STAMINA = 1, REGEN_ENERGY = 1;
    private static volatile double MOVE_SCALING = 1.0D, FLY_SCALING = 1.0D;

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
}