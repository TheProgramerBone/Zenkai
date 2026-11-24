package com.hmc.zenkai.feature.capsule;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Suelta el contenido de una cápsula llena (servidor): comprobación previa COMPLETA y pegado repartido en ticks (P11).
 *
 * COMPROBACIÓN (antes de tocar nada; si falla, la cápsula no se gasta y el jugador recibe un aviso):
 *   - el archivo existe (si no, la cápsula era una copia de otra ya soltada: queda vacía, ver {@link CapsuleContent});
 *   - cada posición destino es aire o reemplazable (hierba, nieve, agua…), y está dentro de la altura del mundo;
 *   - ningún pegado en curso ocupa ya esa zona;
 *   - claims: un {@code EntityPlaceEvent} por bloque en nombre de quien la suelta. Se comprueba TODO de antemano y no por
 *     lote, porque cancelar a medio pegar dejaría media casa en el mundo y media en la cápsula.
 *
 * PEGADO ({@code capsules.paste_blocks_per_tick} por tick, 512 por defecto): primero bloques y sus block entities (sin
 * avisar a vecinos, como hace vanilla con las estructuras: nada se cae ni salta antes de tiempo), luego las
 * actualizaciones, y al final las entidades. Si algo ocupó un destino entre la comprobación y su turno (un jugador puso un
 * bloque), ese bloque cae como objeto en vez de pisar lo nuevo. Al apagar el servidor los pegados pendientes se terminan
 * de golpe; un CIERRE INESPERADO a mitad deja la parte pegada en el mundo y el resto en el archivo, sin duplicar nada (la
 * cápsula ya está vacía).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class CapsuleRelease {
    private CapsuleRelease() {}

    public enum Outcome { OK, MISSING, OBSTRUCTED, PROTECTED, BUSY }

    private static final List<PasteJob> JOBS = new ArrayList<>();

    /**
     * Comprueba y, si todo cuadra, arranca el pegado con la capa de abajo al nivel de {@code anchor}.
     * {@code ignore} es una posición que cuenta como libre (la propia cápsula colocada, que se quita justo después).
     */
    public static Outcome release(ServerLevel level, BlockPos anchor, CapsuleContent content, Player actor,
                                  @Nullable BlockPos ignore) {
        CompoundTag tag = CapsuleStorage.read(level.getServer(), content.id());
        if (tag == null) return Outcome.MISSING;
        CapsuleData data = CapsuleData.load(tag, level);
        BlockPos min = CapsuleZone.pasteMin(anchor, data.radius);
        AABB box = CapsuleZone.box(min, data.radius);
        for (PasteJob j : JOBS) {
            if (j.level == level && j.box.intersects(box)) return Outcome.BUSY;
        }
        for (int i = 0; i < data.blockCount(); i++) {
            BlockPos t = CapsuleData.at(min, data.pos[i]);
            if (t.equals(ignore)) continue;
            if (level.isOutsideBuildHeight(t) || !isFree(level, t)) return Outcome.OBSTRUCTED;
        }
        for (int i = 0; i < data.blockCount(); i++) {
            BlockPos t = CapsuleData.at(min, data.pos[i]);
            var snap = BlockSnapshot.create(level.dimension(), level, t);
            var ev = NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snap, level.getBlockState(t.below()), actor));
            if (ev.isCanceled()) return Outcome.PROTECTED;
        }
        JOBS.add(new PasteJob(level, data, min, box, content.id()));
        CapsuleCapture.effects(level, min, data.radius);
        return Outcome.OK;
    }

    static boolean isFree(ServerLevel level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        return st.isAir() || st.canBeReplaced();
    }

    /** ¿Hay un pegado en curso que toque esta posición? (el holograma de cliente no lo sabe; lo usa la captura). */
    public static boolean busyAt(ServerLevel level, BlockPos p) {
        for (PasteJob j : JOBS) if (j.level == level && j.box.contains(p.getCenter())) return true;
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) return;
        int perTick = ServerConfig.capsulePasteBlocksPerTick();
        Iterator<PasteJob> it = JOBS.iterator();
        while (it.hasNext()) {
            PasteJob j = it.next();
            if (j.step(perTick)) it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        finishAll();
    }

    /** Termina cada pegado pendiente en este mismo tick (apagado del servidor y gametests). */
    public static void finishAll() {
        for (PasteJob j : JOBS) {
            try {
                while (!j.step(Integer.MAX_VALUE)) { /* hasta el final */ }
            } catch (RuntimeException e) {
                Zenkai.LOGGER.error("Capsule: paste {} failed while finishing", j.id, e);
            }
        }
        JOBS.clear();
    }

    private static final class PasteJob {
        private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

        final ServerLevel level;
        final CapsuleData data;
        final BlockPos min;
        final AABB box;
        final UUID id;
        final Set<BlockPos> placed = new HashSet<>();
        /** Lo mismo que {@link #placed}, en orden, para recorrerlo por lotes en la fase de actualizaciones. */
        final List<BlockPos> placedList = new ArrayList<>();
        int phase;   // 0 bloques, 1 actualizaciones, 2 entidades
        int cursor;

        PasteJob(ServerLevel level, CapsuleData data, BlockPos min, AABB box, UUID id) {
            this.level = level;
            this.data = data;
            this.min = min;
            this.box = box;
            this.id = id;
        }

        /** Avanza hasta {@code budget} bloques. true = terminado. */
        boolean step(int budget) {
            if (phase == 0) {
                int end = (int) Math.min((long) cursor + budget, data.blockCount());
                for (; cursor < end; cursor++) placeOne(cursor);
                if (cursor >= data.blockCount()) { phase = 1; cursor = 0; }
                return false;
            }
            if (phase == 1) {
                List<BlockPos> list = placedList;
                int end = (int) Math.min((long) cursor + budget * 2L, list.size());
                for (; cursor < end; cursor++) {
                    BlockPos p = list.get(cursor);
                    level.blockUpdated(p, level.getBlockState(p).getBlock());
                }
                if (cursor >= list.size()) {
                    CapsuleCapture.notifyBorder(level, placed);
                    phase = 2;
                }
                return false;
            }
            spawnEntities();
            CapsuleStorage.delete(level.getServer(), id);
            return true;
        }

        private void placeOne(int i) {
            BlockPos t = CapsuleData.at(min, data.pos[i]);
            BlockState st = data.stateAt(i);
            if (level.isOutsideBuildHeight(t)) return;
            if (!isFree(level, t)) {
                // Alguien ocupó el sitio después de la comprobación: el bloque guardado cae como objeto.
                ItemStack drop = new ItemStack(st.getBlock().asItem());
                if (!drop.isEmpty()) Block.popResource(level, t, drop);
                return;
            }
            level.setBlock(t, st, PLACE_FLAGS);
            CompoundTag beTag = data.blockEntities.get(data.pos[i]);
            if (beTag != null) {
                BlockEntity be = level.getBlockEntity(t);
                if (be != null) {
                    be.loadWithComponents(beTag, level.registryAccess());
                    be.setChanged();
                }
            }
            placed.add(t);
            placedList.add(t);
        }

        private void spawnEntities() {
            for (CapsuleData.StoredEntity se : data.entities) {
                CompoundTag tag = se.data().copy();
                double nx = min.getX() + se.rel().x, ny = min.getY() + se.rel().y, nz = min.getZ() + se.rel().z;
                shiftHangingAnchor(tag, nx, ny, nz);
                tag.put("Pos", CapsuleData.doubles(nx, ny, nz));
                Entity e = EntityType.loadEntityRecursive(tag, level, ent -> ent);
                if (e == null) continue;
                e.moveTo(nx, ny, nz, e.getYRot(), e.getXRot());
                if (!level.tryAddFreshEntityWithPassengers(e)) {
                    // El UUID ya existe en el mundo (no debería: el original se descartó al guardar): uno nuevo para
                    // la montura y cada pasajero.
                    for (Entity part : e.getSelfAndPassengers().toList()) part.setUUID(UUID.randomUUID());
                    level.tryAddFreshEntityWithPassengers(e);
                }
            }
        }

        /**
         * Cuadros, marcos y nudos de correa guardan el bloque al que se cuelgan en {@code TileX/Y/Z}, aparte de {@code Pos}:
         * se desplazan lo mismo que la entidad, o aparecerían colgados de su sitio viejo y se romperían.
         */
        private static void shiftHangingAnchor(CompoundTag tag, double nx, double ny, double nz) {
            if (!tag.contains("TileX") || !tag.contains("Pos", Tag.TAG_LIST)) return;
            ListTag old = tag.getList("Pos", Tag.TAG_DOUBLE);
            if (old.size() < 3) return;
            int dx = (int) Math.round(nx - old.getDouble(0));
            int dy = (int) Math.round(ny - old.getDouble(1));
            int dz = (int) Math.round(nz - old.getDouble(2));
            tag.putInt("TileX", tag.getInt("TileX") + dx);
            tag.putInt("TileY", tag.getInt("TileY") + dy);
            tag.putInt("TileZ", tag.getInt("TileZ") + dz);
        }
    }
}
