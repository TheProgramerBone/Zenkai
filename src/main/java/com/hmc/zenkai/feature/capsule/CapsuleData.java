package com.hmc.zenkai.feature.capsule;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenido de una cápsula llena: lo que {@link CapsuleStorage} escribe en disco. Posiciones RELATIVAS a la esquina mínima
 * de la zona de captura (0..2r por eje, empaquetadas con {@link CapsuleZone#pack}).
 *
 * Formato (NBT comprimido): {@code Radius}, {@code DataVersion}, {@code Palette} (estados), {@code Pos}/{@code State}
 * (int[] paralelos: posición y su índice en la paleta), {@code BlockEntities} (lista {@code {Pos, Data}}) y
 * {@code Entities} (lista {@code {X, Y, Z, Data}} con la posición relativa en dobles y el NBT de {@code saveAsPassenger},
 * que incluye a los pasajeros). {@code DataVersion} se guarda por si un día hace falta pasar un DataFixer al actualizar
 * Minecraft; hoy no se usa.
 */
public final class CapsuleData {

    public final int radius;
    public final List<BlockState> palette = new ArrayList<>();
    /** Posición empaquetada de cada bloque, y su índice de paleta en {@link #state} (mismo orden). */
    public int[] pos = new int[0];
    public int[] state = new int[0];
    public final Map<Integer, CompoundTag> blockEntities = new HashMap<>();
    public final List<StoredEntity> entities = new ArrayList<>();

    public record StoredEntity(Vec3 rel, CompoundTag data) {}

    public CapsuleData(int radius) {
        this.radius = radius;
    }

    public int blockCount() { return pos.length; }

    public BlockState stateAt(int i) { return palette.get(state[i]); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Radius", radius);
        tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        ListTag pal = new ListTag();
        for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
        tag.put("Palette", pal);
        tag.putIntArray("Pos", pos);
        tag.putIntArray("State", state);
        ListTag bes = new ListTag();
        for (var e : blockEntities.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putInt("Pos", e.getKey());
            c.put("Data", e.getValue());
            bes.add(c);
        }
        tag.put("BlockEntities", bes);
        ListTag ents = new ListTag();
        for (StoredEntity se : entities) {
            CompoundTag c = new CompoundTag();
            c.putDouble("X", se.rel.x);
            c.putDouble("Y", se.rel.y);
            c.putDouble("Z", se.rel.z);
            c.put("Data", se.data);
            ents.add(c);
        }
        tag.put("Entities", ents);
        return tag;
    }

    public static CapsuleData load(CompoundTag tag, ServerLevel level) {
        CapsuleData d = new CapsuleData(tag.getInt("Radius"));
        HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
        for (Tag t : tag.getList("Palette", Tag.TAG_COMPOUND)) {
            d.palette.add(NbtUtils.readBlockState(blocks, (CompoundTag) t));
        }
        d.pos = tag.getIntArray("Pos");
        d.state = tag.getIntArray("State");
        if (d.state.length != d.pos.length) {
            // Archivo dañado: se queda con lo que cuadra en vez de lanzar a mitad del pegado.
            int n = Math.min(d.pos.length, d.state.length);
            d.pos = java.util.Arrays.copyOf(d.pos, n);
            d.state = java.util.Arrays.copyOf(d.state, n);
        }
        for (int i = 0; i < d.state.length; i++) {
            if (d.state[i] < 0 || d.state[i] >= d.palette.size()) d.state[i] = 0;
        }
        for (Tag t : tag.getList("BlockEntities", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.blockEntities.put(c.getInt("Pos"), c.getCompound("Data"));
        }
        for (Tag t : tag.getList("Entities", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.entities.add(new StoredEntity(new Vec3(c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z")),
                    c.getCompound("Data")));
        }
        return d;
    }

    /** Posiciones empaquetadas, para el holograma del cliente (sin estados: solo se dibujan cubos). */
    public int[] previewPositions() { return pos.clone(); }

    public static BlockPos at(BlockPos min, int packed) {
        return min.offset(CapsuleZone.unpackX(packed), CapsuleZone.unpackY(packed), CapsuleZone.unpackZ(packed));
    }

    /** Lista de dobles para reescribir {@code Pos} de una entidad. */
    static ListTag doubles(double x, double y, double z) {
        ListTag l = new ListTag();
        l.add(DoubleTag.valueOf(x));
        l.add(DoubleTag.valueOf(y));
        l.add(DoubleTag.valueOf(z));
        return l;
    }
}
