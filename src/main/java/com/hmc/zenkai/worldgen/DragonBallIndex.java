package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Índice en memoria de esferas del dragón físicamente colocadas en chunks actualmente
 * cargados (jugador o worldgen, igual que el escaneo que sustituye). Antes, DragonRadarItem
 * recorría chunk/sección/bloque desde cero CADA 20 ticks POR CADA jugador con el radar
 * activo (ver git history) — con varios radares encendidos a la vez ese coste se
 * multiplicaba por jugador aunque estuvieran mirando la misma zona. Aquí cada chunk se
 * escanea UNA SOLA VEZ (a demanda, la primera vez que algo lo consulta) y de ahí en
 * adelante el radar solo itera el puñado de posiciones ya indexadas — el escaneo caro por
 * paleta/bloque desaparece del camino caliente.
 *
 * No es la fuente de verdad: si un bloque cambia por una vía que no dispara BreakEvent/
 * EntityPlaceEvent (explosión, /setblock, otro mod), la entrada queda obsoleta hasta que
 * {@link #nearest} la valida contra el mundo real y la descarta sola. Un chunk nunca
 * indexado (nadie lo ha consultado aún) simplemente no aporta candidatos — se indexa en el
 * momento en que haga falta, igual que el escaneo anterior solo miraba chunks ya cargados.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class DragonBallIndex {
    private DragonBallIndex() {}

    private record ChunkKey(ResourceKey<Level> dim, long chunk) {}

    private static final Map<ChunkKey, Set<Long>> INDEX = new HashMap<>();
    private static final Predicate<BlockState> IS_BALL = s -> s.is(ModTags.Blocks.DRAGON_BALLS_BLOCK);

    /** Esfera más cercana a {@code origin} entre los chunks cargados dentro de {@code radius}. */
    public static BlockPos nearest(ServerLevel level, BlockPos origin, int radius) {
        int minCx = (origin.getX() - radius) >> 4, maxCx = (origin.getX() + radius) >> 4;
        int minCz = (origin.getZ() - radius) >> 4, maxCz = (origin.getZ() + radius) >> 4;

        BlockPos best = null;
        double bestSqr = (double) radius * radius;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz); // nunca fuerza carga
                if (chunk == null) continue;

                Iterator<Long> it = forChunk(level, chunk).iterator();
                while (it.hasNext()) {
                    BlockPos p = BlockPos.of(it.next());
                    // Autolimpieza: si dejó de ser una esfera por una vía que no pasó por
                    // los eventos de abajo, se descarta aquí en vez de devolver un objetivo
                    // fantasma.
                    if (!level.getBlockState(p).is(ModTags.Blocks.DRAGON_BALLS_BLOCK)) {
                        it.remove();
                        continue;
                    }
                    double d = origin.distSqr(p);
                    if (d < bestSqr) { bestSqr = d; best = p; }
                }
            }
        }
        return best;
    }

    /** Posiciones indexadas de un chunk, escaneándolo la primera vez que se pide. */
    private static Set<Long> forChunk(ServerLevel level, LevelChunk chunk) {
        ChunkKey key = new ChunkKey(level.dimension(), chunk.getPos().toLong());
        return INDEX.computeIfAbsent(key, k -> scan(level, chunk));
    }

    /** Mismo filtro por paleta que el escaneo original: por sección, se descarta gratis con
     *  {@code maybeHas} antes de mirar bloque a bloque. Solo corre una vez por chunk. */
    private static Set<Long> scan(ServerLevel level, LevelChunk chunk) {
        Set<Long> found = new HashSet<>();
        int baseX = chunk.getPos().x << 4, baseZ = chunk.getPos().z << 4;
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection sec = sections[i];
            if (sec.hasOnlyAir() || !sec.maybeHas(IS_BALL)) continue;

            int baseY = level.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (sec.getBlockState(x, y, z).is(ModTags.Blocks.DRAGON_BALLS_BLOCK)) {
                            found.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Nueva esfera colocada: solo se anota si el chunk YA está indexado (si no, se indexará
     *  completo -incluyendo esta- la próxima vez que haga falta). */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getPlacedBlock().is(ModTags.Blocks.DRAGON_BALLS_BLOCK)) return;
        Set<Long> set = INDEX.get(new ChunkKey(level.dimension(), ChunkPos.asLong(event.getPos())));
        if (set != null) set.add(event.getPos().asLong());
    }

    /** Esfera rota: se quita del índice si estaba. {@link DragonBallLootHandler} ya marca la
     *  misma posición como saqueada en el mismo evento; aquí solo se mantiene el índice. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getState().is(ModTags.Blocks.DRAGON_BALLS_BLOCK)) return;
        Set<Long> set = INDEX.get(new ChunkKey(level.dimension(), ChunkPos.asLong(event.getPos())));
        if (set != null) set.remove(event.getPos().asLong());
    }

    /** El chunk se descarga: su entrada se olvida entera, se reconstruye sola si vuelve a
     *  cargarse y a hacer falta. */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        INDEX.remove(new ChunkKey(level.dimension(), event.getChunk().getPos().toLong()));
    }
}
