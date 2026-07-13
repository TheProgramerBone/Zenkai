package com.hmc.zenkai.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * Gamerules del mod. Todas true por defecto — el mod funciona completo
 * sin tocar nada. Los admins pueden desactivar partes con /gamerule.
 */
public final class ModGameRules {

    private ModGameRules() {}

    // ── Claves ───────────────────────────────────────────────────────────────

    /** Si false, el servidor rechaza el packet de elección de raza. */
    public static final GameRules.Key<GameRules.BooleanValue> ALLOW_RACE_SELECTION =
            GameRules.register("zenkai_allowRaceSelection", GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));

    /** Si false, CombatHooks no se activa aunque el jugador tenga raza. */
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_RACE_BOOSTS =
            GameRules.register("zenkai_enableRaceBoosts", GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));

    /** Si false, los Ki Blasts no aplican daño al impactar. */
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_KI_DAMAGE =
            GameRules.register("zenkai_enableKiDamage", GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));

    /** Si false, el servidor rechaza el packet de transformación. */
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_TRANSFORMATIONS =
            GameRules.register("zenkai_enableTransformations", GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));

    /** Si false, el bloque de las 7 esferas no invoca a Shenlong. */
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_SHENLONG_SUMMON =
            GameRules.register("zenkai_enableSummon", GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));
    
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_OTHERWORLD =
            GameRules.register("zenkai_enableOtherworld", GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));

    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_STRUCTURE_PROTECTION =
            GameRules.register("zenkai_enableStructureProtection", GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));

    public static final GameRules.Key<GameRules.BooleanValue> KEEP_STRUCTURE_NPCS =
            GameRules.register("zenkai_keepStructureNpcs", GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));

    /** Si false, las técnicas explosivas no rompen bloques (solo daño/partículas). */
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_KI_GRIEFING =
            GameRules.register("zenkai_enableKiGriefing", GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));


    // ── Init ─────────────────────────────────────────────────────────────────
    public static void init() {
        // Fuerza la carga estática de las claves antes de arrancar el servidor.
    }

    // ── Helpers de lectura ────────────────────────────────────────────────────

    public static boolean allowRaceSelection(MinecraftServer server) {
        return server.getGameRules().getBoolean(ALLOW_RACE_SELECTION);
    }

    public static boolean enableRaceBoosts(MinecraftServer server) {
        return server.getGameRules().getBoolean(ENABLE_RACE_BOOSTS);
    }

    public static boolean enableKiDamage(MinecraftServer server) {
        return server.getGameRules().getBoolean(ENABLE_KI_DAMAGE);
    }

    public static boolean enableTransformations(MinecraftServer server) {
        return server.getGameRules().getBoolean(ENABLE_TRANSFORMATIONS);
    }

    public static boolean enableShenlongSummon(MinecraftServer server) {
        return server.getGameRules().getBoolean(ENABLE_SHENLONG_SUMMON);
    }

    public static boolean enableOtherworld(MinecraftServer server) {
        return server.getGameRules().getBoolean(ENABLE_OTHERWORLD);
    }

    public static boolean enableStructureProtection(MinecraftServer server) {
        return server.getGameRules().getBoolean(ENABLE_STRUCTURE_PROTECTION);
    }

    public static boolean keepStructureNpcs(MinecraftServer server) {
        return !server.getGameRules().getBoolean(KEEP_STRUCTURE_NPCS);
    }

    public static boolean enableKiGriefing(MinecraftServer server) {
        return server.getGameRules().getBoolean(ENABLE_KI_GRIEFING);
    }
}