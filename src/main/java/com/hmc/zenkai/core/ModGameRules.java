package com.hmc.zenkai.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * Gamerules del mod. Todas true por defecto — el mod funciona completo
 * sin tocar nada. Los admins pueden desactivar partes con /gamerule.
 *
 * Comandos disponibles:
 *   /gamerule dbr_allowRaceSelection false   → bloquea la selección de raza
 *   /gamerule dbr_enableRaceBoosts false     → combat 100% vanilla
 *   /gamerule dbr_enableKiDamage false       → Ki Blasts no hacen daño
 *   /gamerule dbr_enableTransformations false → bloquea transformaciones
 *
 * Uso en código:
 *   ModGameRules.allowRaceSelection(server)    → boolean
 *   ModGameRules.enableRaceBoosts(server)      → boolean
 *   ModGameRules.enableKiDamage(server)        → boolean
 *   ModGameRules.enableTransformations(server) → boolean
 */
public final class ModGameRules {

    private ModGameRules() {}

    // ── Claves ───────────────────────────────────────────────────────────────

    /** Si false, el servidor rechaza el packet de elección de raza. */
    public static final GameRules.Key<GameRules.BooleanValue> ALLOW_RACE_SELECTION =
            GameRules.register(
                    "dbr_allowRaceSelection",
                    GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(true)
            );

    /**
     * Si false, CombatHooks no se activa aunque el jugador tenga raza.
     * El combat vuelve a ser 100% vanilla.
     */
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_RACE_BOOSTS =
            GameRules.register(
                    "dbr_enableRaceBoosts",
                    GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(true)
            );

    /**
     * Si false, los Ki Blasts no aplican daño al impactar.
     * Se pueden usar visualmente sin afectar el combate.
     */
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_KI_DAMAGE =
            GameRules.register(
                    "dbr_enableKiDamage",
                    GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(true)
            );

    /**
     * Si false, el servidor rechaza el packet de transformación.
     * El jugador permanece en su forma base aunque tenga el Ki necesario.
     */
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_TRANSFORMATIONS =
            GameRules.register(
                    "dbr_enableTransformations",
                    GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(true)
            );

    // ── Init ─────────────────────────────────────────────────────────────────

    /**
     * Llamar desde el constructor de DragonBlockRenewed para forzar
     * la inicialización estática de las claves antes de que el servidor arranque.
     */
    public static void init() {
        // Las constantes ya se registran al cargar la clase.
        // Este método existe solo para provocar esa carga de forma explícita
        // y documentada, evitando que el JVM la retrase.
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
}