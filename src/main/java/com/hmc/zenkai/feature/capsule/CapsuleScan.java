package com.hmc.zenkai.feature.capsule;

import com.hmc.zenkai.content.block.CapsuleBlock;
import com.hmc.zenkai.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Qué se llevaría una cápsula de la zona (SERVIDOR: mira el NBT de los inventarios, que el cliente no tiene). Lo usan la
 * pantalla (resumen) y la captura (que vuelve a escanear: entre abrir la pantalla y salir de la zona el mundo cambia).
 *
 * REGLAS (§3.5 y P10 de pendientes-0.7.0.md, decisión del usuario 2026-11-24 «lo prohibido se queda»):
 *   - El aire no cuenta. Los bloques que son SOLO fluido (agua, lava, sus corrientes) tampoco: no se guardan ni se quitan,
 *     para que una casa junto a un río no se lleve el río (un bloque anegado sí viaja con su agua).
 *   - PROHIBIDOS (se quedan en el mundo y se cuentan aparte): el tag {@code zenkai:capsule_blacklist_blocks} (esferas, bloque
 *     de Namek, máquinas con estado ligado al sitio, bedrock, spawners, portales…), cualquier bloque irrompible (dureza
 *     negativa: cubre la bedrock de otros mods), otra cápsula colocada y un bloque cuyo inventario lleve una cápsula dentro.
 *   - Entidades: mobs (cualquier {@link LivingEntity} salvo jugadores), decoraciones colgadas (cuadros, marcos) y vehículos
 *     (barcas, vagonetas). NO objetos sueltos, orbes ni proyectiles. Prohibidas: el tag {@code zenkai:capsule_blacklist}
 *     (jefes, maestros, Shenlong, cuerpos de jugador), las que lleven a un jugador montado y las que carguen una cápsula.
 *   - Un mob ocupa su caja redondeada hacia arriba: {@code ceil(ancho)² × ceil(alto)} (vaca, aldeano y Saibaman = 2).
 *     Los vehículos y decoraciones no ocupan volumen.
 *   - Cápsulas dentro de cápsulas: bloqueadas también vacías (más simple que distinguir, y una vacía guardada no aporta nada).
 */
public final class CapsuleScan {
    private CapsuleScan() {}

    public record Result(BlockPos min, int radius, List<BlockPos> blocks, List<Entity> entities,
                         int mobs, int mobVolume, int skipped) {
        public int used() { return blocks.size() + mobVolume; }
        public int budget() { int s = CapsuleZone.side(radius); return s * s * s; }
        public boolean empty() { return blocks.isEmpty() && entities.isEmpty(); }
        public boolean overBudget() { return used() > budget(); }
    }

    /** Escanea la zona de una cápsula vacía colocada en {@code capsule}. */
    public static Result scan(ServerLevel level, BlockPos capsule, int radius) {
        BlockPos min = CapsuleZone.captureMin(capsule, radius);
        int s = CapsuleZone.side(radius);
        List<BlockPos> blocks = new ArrayList<>();
        int skipped = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                for (int z = 0; z < s; z++) {
                    m.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    if (m.equals(capsule) || level.isOutsideBuildHeight(m)) continue;
                    BlockState st = level.getBlockState(m);
                    if (st.isAir() || st.getBlock() instanceof LiquidBlock) continue;
                    if (isProhibited(level, m, st)) { skipped++; continue; }
                    blocks.add(m.immutable());
                }
            }
        }

        List<Entity> entities = new ArrayList<>();
        int mobs = 0, volume = 0;
        // Cuentan las entidades con los PIES dentro de la zona (no basta con rozarla), y solo las raíces: los pasajeros
        // viajan con su montura.
        var box = CapsuleZone.box(min, radius);
        for (Entity e : level.getEntities((Entity) null, box, e -> e.getVehicle() == null && box.contains(e.position()))) {
            if (!isCapturableKind(e)) continue;
            if (isProhibitedEntity(level, e)) { skipped++; continue; }
            entities.add(e);
            for (Entity part : e.getSelfAndPassengers().toList()) {
                if (part instanceof LivingEntity) {
                    mobs++;
                    volume += mobVolume(part);
                }
            }
        }
        return new Result(min, radius, blocks, entities, mobs, volume, skipped);
    }

    public static int mobVolume(Entity e) {
        int w = Math.max(1, Mth.ceil(e.getBbWidth()));
        int h = Math.max(1, Mth.ceil(e.getBbHeight()));
        return w * w * h;
    }

    private static boolean isCapturableKind(Entity e) {
        if (e instanceof Player || !e.isAlive()) return false;
        return e instanceof LivingEntity || e instanceof HangingEntity || e instanceof VehicleEntity;
    }

    static boolean isProhibited(ServerLevel level, BlockPos pos, BlockState st) {
        if (st.is(ModTags.Blocks.CAPSULE_BLACKLIST_BLOCKS)) return true;
        if (st.getBlock() instanceof CapsuleBlock) return true;
        if (st.getDestroySpeed(level, pos) < 0) return true;
        if (st.hasBlockEntity()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && containsCapsule(be.saveWithoutMetadata(level.registryAccess()))) return true;
        }
        return false;
    }

    private static boolean isProhibitedEntity(ServerLevel level, Entity root) {
        for (Entity e : root.getSelfAndPassengers().toList()) {
            if (e instanceof Player) return true;
            if (e.getType().is(ModTags.EntityTypes.CAPSULE_BLACKLIST)) return true;
            CompoundTag tag = new CompoundTag();
            e.saveWithoutId(tag);
            if (containsCapsule(tag)) return true;
        }
        return false;
    }

    /**
     * ¿Hay una cápsula en algún punto de este NBT? Busca su id como texto en cualquier profundidad: así vale para cofres,
     * shulkers dentro de cofres, mochilas de otros mods, burros con alforjas o un zorro con la cápsula en la boca, sin
     * conocer el formato de cada uno.
     */
    public static boolean containsCapsule(Tag tag) {
        if (tag instanceof StringTag s) return CapsuleItemIds.isCapsuleId(s.getAsString());
        if (tag instanceof CompoundTag c) {
            for (String k : c.getAllKeys()) {
                Tag child = c.get(k);
                if (child != null && containsCapsule(child)) return true;
            }
            return false;
        }
        if (tag instanceof CollectionTag<?> list) {
            for (Tag child : list) if (containsCapsule(child)) return true;
        }
        return false;
    }
}
