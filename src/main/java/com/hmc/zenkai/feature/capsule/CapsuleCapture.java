package com.hmc.zenkai.feature.capsule;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Guarda la zona de una cápsula vacía en un archivo y la quita del mundo (servidor). Todo en UN tick: quitar bloques por
 * lotes dejaría una ventana en la que el jugador rompe a mano un bloque ya guardado y lo duplica.
 *
 * ORDEN (cada paso puede cancelar sin haber tocado el mundo):
 *   1. Escaneo ({@link CapsuleScan}): vacía → nada; más volumen que el rango → no cabe.
 *   2. Claims: un {@code BreakEvent} por bloque en nombre de quien la armó (P10 / §3.5). Si un mod de protección cancela
 *      UNO, se cancela la captura entera (no se deja la casa agujereada).
 *   3. Se copia y se escribe el archivo. Si el disco falla, se cancela.
 *   4. Se quitan entidades y bloques. Los inventarios se VACÍAN y su block entity se retira ANTES de poner aire: así
 *      ningún {@code onRemove} (de vanilla o de otro mod) encuentra el inventario y lo suelta al suelo, que sería duplicar.
 *      Se pone aire sin avisar a los vecinos ni recalcular formas (flags 2|16|32), para que una antorcha o una puerta de la
 *      zona no salte como objeto antes de que le toque; al final solo se avisa al borde de la zona.
 */
public final class CapsuleCapture {
    private CapsuleCapture() {}

    public enum Outcome { OK, NOTHING, TOO_BIG, PROTECTED, ERROR }

    public record Result(Outcome outcome, CapsuleContent content, int skipped) {
        static Result fail(Outcome o) { return new Result(o, null, 0); }
    }

    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    public static Result capture(ServerLevel level, BlockPos capsule, int radius, Player actor) {
        CapsuleScan.Result scan = CapsuleScan.scan(level, capsule, radius);
        if (scan.empty()) return Result.fail(Outcome.NOTHING);
        if (scan.overBudget()) return Result.fail(Outcome.TOO_BIG);

        for (BlockPos p : scan.blocks()) {
            var ev = NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, p, level.getBlockState(p), actor));
            if (ev.isCanceled()) return Result.fail(Outcome.PROTECTED);
        }

        BlockPos min = scan.min();
        CapsuleData data = new CapsuleData(radius);
        Map<BlockState, Integer> index = new HashMap<>();
        int n = scan.blocks().size();
        data.pos = new int[n];
        data.state = new int[n];
        for (int i = 0; i < n; i++) {
            BlockPos p = scan.blocks().get(i);
            BlockState st = level.getBlockState(p);
            int packed = CapsuleZone.pack(p.getX() - min.getX(), p.getY() - min.getY(), p.getZ() - min.getZ());
            data.pos[i] = packed;
            data.state[i] = index.computeIfAbsent(st, s -> { data.palette.add(s); return data.palette.size() - 1; });
            BlockEntity be = level.getBlockEntity(p);
            if (be != null) data.blockEntities.put(packed, be.saveWithoutMetadata(level.registryAccess()));
        }
        int saved = 0;
        for (Entity e : scan.entities()) {
            CompoundTag tag = new CompoundTag();
            if (!e.saveAsPassenger(tag)) continue;
            Vec3 rel = e.position().subtract(min.getX(), min.getY(), min.getZ());
            data.entities.add(new CapsuleData.StoredEntity(rel, tag));
            saved++;
        }

        UUID id = UUID.randomUUID();
        if (!CapsuleStorage.write(level.getServer(), id, data.save())) return Result.fail(Outcome.ERROR);

        // Entidades: la montura y sus pasajeros, sin soltar botín (DISCARDED no es una muerte).
        for (Entity e : scan.entities()) {
            for (Entity part : e.getIndirectPassengers()) part.discard();
            e.discard();
        }

        Set<BlockPos> removed = new HashSet<>(scan.blocks());
        for (BlockPos p : scan.blocks()) {
            BlockEntity be = level.getBlockEntity(p);
            if (be != null) {
                Clearable.tryClear(be);
                level.removeBlockEntity(p);
            }
            level.setBlock(p, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        }
        notifyBorder(level, removed);
        effects(level, min, radius);

        int mobs = scan.mobs();
        return new Result(Outcome.OK, new CapsuleContent(id, n, mobs, radius), scan.skipped());
    }

    /** Avisa a los bloques de FUERA que tocan lo quitado (agua que fluye, antorchas pegadas a una pared que se fue…). */
    static void notifyBorder(ServerLevel level, Set<BlockPos> changed) {
        for (BlockPos p : changed) {
            boolean border = false;
            for (Direction d : Direction.values()) {
                if (!changed.contains(p.relative(d))) { border = true; break; }
            }
            if (!border) continue;
            BlockState st = level.getBlockState(p);
            st.updateNeighbourShapes(level, p, Block.UPDATE_ALL);
            level.updateNeighborsAt(p, st.getBlock());
        }
    }

    /** El «¡puf!» de la cápsula: nube y sonido, repartidos por la zona sin pasar de unas decenas de partículas. */
    static void effects(ServerLevel level, BlockPos min, int radius) {
        int s = CapsuleZone.side(radius);
        Vec3 c = new Vec3(min.getX() + s / 2.0, min.getY() + 1.0, min.getZ() + s / 2.0);
        level.sendParticles(ParticleTypes.POOF, c.x, c.y + s / 4.0, c.z, Math.min(80, 10 + s * 4),
                s / 3.0, s / 4.0, s / 3.0, 0.05);
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, Math.min(40, 6 + s * 2),
                s / 3.0, 0.5, s / 3.0, 0.02);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.6F);
    }
}
