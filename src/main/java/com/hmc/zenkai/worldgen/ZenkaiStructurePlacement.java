package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModDimensions;
import com.hmc.zenkai.registry.ModStructureSegments;
import com.hmc.zenkai.worldgen.StaticStructurePlacer.Segment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Colocación de estructuras únicas (no aleatorias):
 *  - Kami: una vez en el overworld al arrancar el servidor.
 *  - Otherworld: una vez, justo antes de mandar al primer jugador allí
 *    (ensureOtherworldPalace, llamado desde OtherworldManager).
 * El flag de "ya colocada" vive en ZenkaiWorldData (una vez por mundo).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ZenkaiStructurePlacement {
    private ZenkaiStructurePlacement() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Structures");

    public static final String KEY_KAMI       = "kami_palace";
    public static final String KEY_OTHERWORLD = "otherworld_palace";
    public static final String KEY_KAIOSAMA   = "kaiosama_planet";
    public static final String KEY_HTC        = "htc_structure";

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        NoHostileSpawnZones.clear();
        ServerLevel ow = event.getServer().getLevel(ModDimensions.OTHERWORLD_LEVEL);
        if (ow != null) {
            // ORDEN IMPORTA: ensureKaiosama() detecta "mundo de antes del split" mirando si
            // KEY_OTHERWORLD YA estaba marcado — eso solo distingue un mundo viejo de uno
            // recién creado si se comprueba ANTES de que ensureOtherworldPalace() lo marque
            // en ESTA misma llamada. Invertir el orden haría que todo mundo NUEVO se
            // confundiera con uno migrado y Kaiosama nunca se colocara de verdad.
            ensureKaiosama(ow);
            ensureOtherworldPalace(ow);
        }
        NoHostileSpawnZones.addFromBase(ModDimensions.OTHERWORLD_LEVEL,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_MIN,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SX,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SY,
                ModStructureSegments.OTHERWORLD_NO_SPAWN_SZ,
                "protector.zenkai.yemma");
        // Zona propia para el planeta de Kaiosama, al final de la serpentina: antes quedaba
        // dentro de la caja de arriba y ese tramo se anunciaba protegido por Yemma.
        NoHostileSpawnZones.addFromBase(ModDimensions.OTHERWORLD_LEVEL,
                ModStructureSegments.KAIO_NO_SPAWN_MIN,
                ModStructureSegments.KAIO_NO_SPAWN_SX,
                ModStructureSegments.KAIO_NO_SPAWN_SY,
                ModStructureSegments.KAIO_NO_SPAWN_SZ,
                "protector.zenkai.kaiosama");
        NoHostileSpawnZones.addFromBase(ModDimensions.HTC_LEVEL,
                ModStructureSegments.HTC_NO_SPAWN_MIN,
                ModStructureSegments.HTC_NO_SPAWN_SX,
                ModStructureSegments.HTC_NO_SPAWN_SY,
                ModStructureSegments.HTC_NO_SPAWN_SZ,
                "protector.zenkai.htc");

    }


    /** Garantiza que el palacio del otro mundo exista antes de teletransportar. */
    public static void ensureOtherworldPalace(ServerLevel otherworld) {
        placeOnce(otherworld.getServer(), otherworld, KEY_OTHERWORLD,
                ModStructureSegments.OTHERWORLD_BASE, ModStructureSegments.OTHERWORLD_PALACE);
    }

    /**
     * Garantiza que el planeta de Kaiosama exista. Separado de `ensureOtherworldPalace` en la
     * Fase 2 de Instant Transmission (el sistema de "estructura descubierta" necesita una clave
     * propia por estructura) — antes las dos compartían el mismo `placeOnce` bajo
     * {@code KEY_OTHERWORLD}. MIGRACIÓN: un mundo generado ANTES de este split ya tiene a
     * Kaiosama físicamente puesto (compartía la colocación del palacio), así que si
     * {@code KEY_OTHERWORLD} ya estaba marcado y {@code KEY_KAIOSAMA} todavía no, se marca
     * directamente como colocado sin volver a escribir bloques encima — de lo contrario
     * `StaticStructurePlacer` repetiría el snapshot NBT sobre cualquier cambio que el jugador
     * ya hubiera hecho ahí.
     * ORDEN: debe llamarse ANTES que `ensureOtherworldPalace` en el mismo arranque (ver
     * onServerStarted) — la comprobación de arriba solo distingue "mundo viejo" de "mundo
     * nuevo" si `KEY_OTHERWORLD` todavía refleja el estado guardado en disco, no uno que esta
     * misma llamada acabe de marcar.
     */
    public static void ensureKaiosama(ServerLevel otherworld) {
        ZenkaiWorldData data = ZenkaiWorldData.get(otherworld.getServer());
        if (!data.isPlaced(KEY_KAIOSAMA) && data.isPlaced(KEY_OTHERWORLD)) {
            data.markPlaced(KEY_KAIOSAMA);
        }
        placeOnce(otherworld.getServer(), otherworld, KEY_KAIOSAMA,
                ModStructureSegments.OTHERWORLD_BASE, ModStructureSegments.KAIOSAMA);
    }

    /** Garantiza que la estructura de la Habitación del Tiempo exista antes de teletransportar allí. */
    public static void ensureHtcStructure(ServerLevel htc) {
        placeOnce(htc.getServer(), htc, KEY_HTC,
                ModStructureSegments.HTC_BASE, ModStructureSegments.HTC);
    }

    private static void placeOnce(MinecraftServer server, ServerLevel level,
                                  String key, BlockPos base, List<Segment> segments) {
        ZenkaiWorldData data = ZenkaiWorldData.get(server);
        if (data.isPlaced(key)) return;
        boolean ok = StaticStructurePlacer.place(level, base, segments, true);
        if (ok) {
            data.markPlaced(key);
        } else {
            // Sin esto, un fallo se reintenta en CADA muerte para siempre y en silencio.
            LOGGER.error("[Zenkai] No se pudo colocar '{}' en {}. Se reintentará.", key, base);
        }
    }

    /** Colocación forzada (para pruebas de offsets): ignora el flag de "ya colocada" e ilumina el aire. */
    public static boolean forcePlace(ServerLevel level, String which, BlockPos base) {
        return switch (which) {
            case "kami"       -> StaticStructurePlacer.place(level, base, ModStructureSegments.KAMI, true);
            case "otherworld" -> StaticStructurePlacer.place(level, base, ModStructureSegments.OTHERWORLD_PALACE, true);
            case "kaiosama"   -> StaticStructurePlacer.place(level, base, ModStructureSegments.KAIOSAMA, true);
            case "htc"        -> StaticStructurePlacer.place(level, base, ModStructureSegments.HTC, true);
            default -> false;
        };
    }
}