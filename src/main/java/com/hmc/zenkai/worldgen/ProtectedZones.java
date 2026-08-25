package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ZenkaiStructureTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fachada única de "¿esta posición está en zona protegida y por quién?".
 * Consulta dos backends, en este orden:
 *  1) Zonas estáticas ({@link NoHostileSpawnZones}) — para estructuras colocadas por
 *     código en dimensión propia (Otherworld, HTC), que no tienen referencias de worldgen.
 *  2) Estructuras generadas por worldgen que estén en el tag zenkai:protected.
 *
 * El backend 2 se cachea por chunk porque ExplosionEvent.Detonate puede pedir cientos de
 * posiciones en un tick y ProtectedZoneMessageHandler consulta por jugador y por tick.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ProtectedZones {
    private ProtectedZones() {}

    private record Entry(BoundingBox box, String protector) {}
    private record CacheKey(ResourceKey<Level> dim, long chunk) {}

    private static final Map<CacheKey, List<Entry>> CACHE = new HashMap<>();

    /** Clave de traducción del protector, o null si la posición no está protegida. */
    public static String protectorAt(ServerLevel level, double x, double y, double z) {
        String staticZone = NoHostileSpawnZones.getProtector(level.dimension(), x, y, z);
        if (staticZone != null) return staticZone;
        return structureAt(level, BlockPos.containing(x, y, z));
    }

    public static String protectorAt(ServerLevel level, BlockPos pos) {
        String staticZone = NoHostileSpawnZones.getProtector(
                level.dimension(), pos.getX(), pos.getY(), pos.getZ());
        if (staticZone != null) return staticZone;
        return structureAt(level, pos);
    }

    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return protectorAt(level, pos) != null;
    }

    private static String structureAt(ServerLevel level, BlockPos pos) {
        CacheKey key = new CacheKey(level.dimension(), ChunkPos.asLong(pos));
        List<Entry> entries = CACHE.computeIfAbsent(key, k -> build(level, pos));
        for (Entry e : entries) {
            if (e.box().isInside(pos)) return e.protector();
        }
        return null;
    }

    /** Recolecta una vez por chunk las cajas de cada una de las estructuras protegidas que lo tocan. */
    private static List<Entry> build(ServerLevel level, BlockPos pos) {
        List<Entry> out = new ArrayList<>();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var tag = registry.getTag(ZenkaiStructureTags.PROTECTED);
        if (tag.isEmpty()) return out;

        SectionPos section = SectionPos.of(pos);
        for (Holder<Structure> holder : tag.get()) {
            Structure structure = holder.value();
            ResourceKey<Structure> id = registry.getResourceKey(structure).orElse(null);
            if (id == null) continue;
            Margin margin = MARGINS.getOrDefault(id, Margin.DEFAULT);
            String protector = "protector.zenkai." + id.location().getPath();

            for (StructureStart start : level.structureManager().startsForStructure(section, structure)) {
                out.add(new Entry(margin.apply(start.getBoundingBox()), protector));
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        CACHE.remove(new CacheKey(level.dimension(), event.getChunk().getPos().toLong()));
    }

    /** Para /zenkai struct place y similares: invalida el caché tras colocar algo a mano. */
    public static void invalidateAll() { CACHE.clear(); }

    /** Margen por cara (bloques) que se añade a la caja de la estructura. */
    private record Margin(int x, int down, int up, int z) {
        static final Margin DEFAULT = new Margin(16, 8, 16, 16);

        BoundingBox apply(BoundingBox b) {
            return new BoundingBox(
                    b.minX() - x, b.minY() - down, b.minZ() - z,
                    b.maxX() + x, b.maxY() + up,   b.maxZ() + z);
        }
    }

    private static final Map<ResourceKey<Structure>, Margin> MARGINS = Map.of(
            key("kami_palace"), new Margin(32, 0, 24, 32)
    );

    private static ResourceKey<Structure> key(String path) {
        return ResourceKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path));
    }
}