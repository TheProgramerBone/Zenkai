package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModDimensions;
import com.hmc.zenkai.registry.ModStructureSegments;
import com.hmc.zenkai.worldgen.StaticStructurePlacer.Segment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Colocación de estructuras únicas (no aleatorias), las TRES que se pegan a mano desde plantillas NBT:
 *  - Palacio de Yemma con la serpentina, y planeta de Kaiosama (Otherworld): al arrancar el servidor, o justo antes de
 *    mandar al primer jugador allí (ensureOtherworldPalace, llamado desde OtherworldManager).
 *  - Habitación del Tiempo: al viajar a ella por primera vez (ensureHtcStructure).
 * (Kami NO se coloca aquí: es una estructura de worldgen normal, structure_set kami_palace.)
 * El flag de "ya colocada" vive en {@link DimensionStructureData}, en los datos de la PROPIA dimensión: si se borra la
 * dimensión, se borra el flag y las estructuras vuelven a colocarse. Los mundos antiguos lo tenían en
 * {@link ZenkaiWorldData} (Overworld) y se migra la primera vez (ver {@link #isPlaced}).
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
        MinecraftServer server = otherworld.getServer();
        if (!isPlaced(server, otherworld, KEY_KAIOSAMA) && isPlaced(server, otherworld, KEY_OTHERWORLD)) {
            DimensionStructureData.get(otherworld).markPlaced(KEY_KAIOSAMA);
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
        if (isPlaced(server, level, key)) return;
        boolean ok = StaticStructurePlacer.place(level, base, segments, true);
        if (ok) {
            DimensionStructureData.get(level).markPlaced(key);
        } else {
            // Sin esto, un fallo se reintenta en CADA muerte para siempre y en silencio.
            LOGGER.error("[Zenkai] No se pudo colocar '{}' en {}. Se reintentará.", key, base);
        }
    }

    /**
     * ¿Está esta estructura marcada como colocada en {@code level}? Mira primero la marca de la dimensión. MIGRACIÓN: si no
     * está pero el mundo conserva la marca antigua (en los datos del Overworld, de antes de este cambio), se copia a la
     * dimensión y se QUITA la antigua, de modo que solo se migra una vez: si luego se borra la dimensión, la marca antigua
     * ya no existe y la estructura se recoloca. Se asume que una marca antigua significa «colocada» para no pisar nunca
     * lo que ya haya; un mundo que perdió la dimensión ANTES de este arreglo se repara con {@code /zenkai struct restore}.
     */
    public static boolean isPlaced(MinecraftServer server, ServerLevel level, String key) {
        DimensionStructureData here = DimensionStructureData.get(level);
        if (here.isPlaced(key)) return true;
        ZenkaiWorldData legacy = ZenkaiWorldData.get(server);
        if (legacy.isPlaced(key)) {
            here.markPlaced(key);
            legacy.unmarkPlaced(key);
            return true;
        }
        return false;
    }

    /** Una estructura colocada a mano: clave de la marca, nombre corto (comando), dimensión, base y segmentos. */
    private record StaticSpec(String key, String name, ResourceKey<Level> dim, BlockPos base, List<Segment> segments) {}

    private static List<StaticSpec> specs() {
        return List.of(
                new StaticSpec(KEY_OTHERWORLD, "otherworld", ModDimensions.OTHERWORLD_LEVEL,
                        ModStructureSegments.OTHERWORLD_BASE, ModStructureSegments.OTHERWORLD_PALACE),
                new StaticSpec(KEY_KAIOSAMA, "kaiosama", ModDimensions.OTHERWORLD_LEVEL,
                        ModStructureSegments.OTHERWORLD_BASE, ModStructureSegments.KAIOSAMA),
                new StaticSpec(KEY_HTC, "htc", ModDimensions.HTC_LEVEL,
                        ModStructureSegments.HTC_BASE, ModStructureSegments.HTC));
    }

    /** Líneas de /zenkai struct status: por estructura, si su dimensión está disponible y si está marcada como colocada. */
    public static List<Component> status(MinecraftServer server) {
        List<Component> out = new ArrayList<>();
        for (StaticSpec s : specs()) {
            ServerLevel level = server.getLevel(s.dim());
            out.add(Component.literal("[Zenkai] " + s.name() + ": " + (level == null ? "dimensión no disponible"
                    : isPlaced(server, level, s.key()) ? "marcada como colocada" : "NO colocada (se pondrá al arrancar o al viajar)")));
        }
        return out;
    }

    /**
     * /zenkai struct restore &lt;otherworld|kaiosama|htc|all&gt; [force]. Sin {@code force} solo coloca una estructura que NO
     * esté marcada (nunca pisa nada). Con {@code force} la recoloca ENTERA aunque esté marcada, pisando lo que hubiera
     * encima, y descarta antes los NPC de estructura vivos de la zona (los marcadores crean otros nuevos: sin esto
     * saldrían duplicados). Solo alcanza a los NPC en chunks cargados: ponte en la zona al usarlo.
     */
    public static List<Component> restore(MinecraftServer server, String which, boolean force) {
        List<Component> out = new ArrayList<>();
        boolean any = false;
        for (StaticSpec s : specs()) {
            if (!which.equals("all") && !which.equals(s.name())) continue;
            any = true;
            ServerLevel level = server.getLevel(s.dim());
            if (level == null) { out.add(Component.literal("[Zenkai] " + s.name() + ": dimensión no disponible")); continue; }
            if (!force && isPlaced(server, level, s.key())) {
                out.add(Component.literal("[Zenkai] " + s.name() + ": ya está marcada como colocada. Usa «/zenkai struct restore "
                        + s.name() + " force» para recolocarla ENTERA, pisando lo que haya encima."));
                continue;
            }
            int npcs = force ? discardStructureNpcs(level, s) : 0;
            boolean ok = StaticStructurePlacer.place(level, s.base(), s.segments(), true);
            if (ok) {
                DimensionStructureData.get(level).markPlaced(s.key());
                ProtectedZones.invalidateAll();
            }
            out.add(Component.literal("[Zenkai] " + s.name() + (ok ? ": recolocada" + (force ? " (forzado, " + npcs + " NPC descartados)" : "")
                    : ": falló (revisa el log)")));
        }
        if (!any) out.add(Component.literal("[Zenkai] Estructura desconocida: usa otherworld, kaiosama, htc o all"));
        return out;
    }

    /** Descarta los NPC de estructura (ZenkaiDefaultNPC) cargados dentro de la caja de la estructura. */
    private static int discardStructureNpcs(ServerLevel level, StaticSpec s) {
        var mgr = level.getStructureManager();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Segment seg : s.segments()) {
            var tpl = mgr.get(seg.nbt());
            if (tpl.isEmpty()) continue;
            BlockPos p = s.base().offset(seg.offset());
            var size = tpl.get().getSize();
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX() + size.getX()); maxY = Math.max(maxY, p.getY() + size.getY()); maxZ = Math.max(maxZ, p.getZ() + size.getZ());
        }
        if (minX == Double.MAX_VALUE) return 0;
        var npcs = level.getEntitiesOfClass(com.hmc.zenkai.content.entity.ZenkaiDefaultNPC.class, new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        npcs.forEach(net.minecraft.world.entity.Entity::discard);
        return npcs.size();
    }

    /** Colocación forzada (para pruebas de offsets): ignora el flag de "ya colocada" e ilumina el aire. */
    public static boolean forcePlace(ServerLevel level, String which, BlockPos base) {
        return switch (which) {
            case "kami"       -> StaticStructurePlacer.place(level, base, ModStructureSegments.KAMI, true);
            case "otherworld" -> StaticStructurePlacer.place(level, base, ModStructureSegments.OTHERWORLD_PALACE, true);
            case "kaiosama"   -> StaticStructurePlacer.place(level, base, ModStructureSegments.KAIOSAMA, true);
            case "htc"        -> StaticStructurePlacer.place(level, base, ModStructureSegments.HTC, true);
            // Arnés de prueba del motor propio de piezas modulares (ver worldgen.piecegraph) —
            // NO es namek real, son 3 piezas de relleno generadas por
            // tools/gen_piecegraph_test_pieces.py, solo para confirmar en juego que
            // enganchar/rotar/colocar funciona antes de construir el contenido de verdad.
            case "piecegraph" -> com.hmc.zenkai.worldgen.piecegraph.PieceGraphDemo.place(level, base);
            default -> false;
        };
    }
}