package com.hmc.zenkai.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config común del mod: ModConfig.Type.COMMON, un archivo POR INSTALACIÓN — cliente y servidor
 * leen cada uno su PROPIA copia local, nunca se sincroniza. Reservada para opciones que
 * genuinamente no son gameplay (herramientas de desarrollo per-instalación) — NO para balance.
 * Hasta 2026-09-04 esta clase concentraba ~65 números de balance de gameplay (coste de TP,
 * escalado de stats, fórmulas de combate, economía de entrenamiento, maestría...). Casi todos se
 * migraron a {@link ServerConfig} (ModConfig.Type.SERVER: por-mundo, sincronizado
 * automáticamente al cliente al conectarse) porque son justo el tipo de dato que Type.COMMON
 * sirve peor: cualquier lectura de cliente para predicción/preview/HUD veía su PROPIA copia
 * local en vez del valor real que el servidor iba a aplicar. Esto no era hipotético — ver el
 * histórico de git de {@code feature/stats/TpCurve.java} antes de esa fecha para el bug real que
 * causó (un packet a medida, {@code TpCurveSyncPacket}, tuvo que parchear a mano justo esta
 * laguna para dos de estos números; ya no hace falta, `Type.SERVER` lo cubre solo). Antes de
 * añadir una opción nueva aquí, confirmar que de verdad no es un número de balance
 * server-authoritative — si lo es, va en {@link ServerConfig}.
 */
public final class CommonConfig {
    private CommonConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<String> TECH_DUMP_DIR_RAW =
            BUILDER.comment("DEV ONLY. Extra folder where /zenkai tech dump also writes the technique JSONs,",
                            "on top of the world datapack. Point it at src/main/resources/data/zenkai/zenkai_techniques",
                            "to keep in-game tuning. Empty = disabled.")
                    .define("dev.technique_dump_dir", "");

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile String TECH_DUMP_DIR = "";

    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) return;
        if (event.getConfig().getSpec() != SPEC) return;

        TECH_DUMP_DIR = TECH_DUMP_DIR_RAW.get();
    }

    public static String techniqueDumpDir() { return TECH_DUMP_DIR; }
}
