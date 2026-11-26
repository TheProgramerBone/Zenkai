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

    /**
     * Copia girada en horizontal alrededor del centro de la zona (rotación de la cápsula colocada, pedida 2026-11-26). Mismo
     * convenio que vanilla ({@code StructureTemplate}): CLOCKWISE_90 lleva (x, z) a (−z, x), así norte pasa a este y
     * {@link BlockState#rotate} gira escaleras, puertas, cofres o raíles a juego. La zona es un cubo, así que ocupa lo mismo.
     *
     * Entidades: posición girada, yaw + 90° por paso y la orientación de cuadros y marcos. Su {@code Pos} y {@code TileX/Z}
     * quedan RELATIVOS a la esquina (girados), y el pegado los desplaza a la vez con la diferencia entera hasta la posición
     * nueva (CapsuleRelease.shiftHangingAnchor): así el bloque del que cuelga un cuadro gira con él.
     */
    public CapsuleData rotated(net.minecraft.world.level.block.Rotation rot) {
        if (rot == net.minecraft.world.level.block.Rotation.NONE) return this;
        int side = CapsuleZone.side(radius);
        CapsuleData d = new CapsuleData(radius);
        for (BlockState st : palette) d.palette.add(st.rotate(rot));
        d.state = state.clone();
        d.pos = new int[pos.length];
        for (int i = 0; i < pos.length; i++) d.pos[i] = rotatePacked(pos[i], side, rot);
        for (var e : blockEntities.entrySet()) d.blockEntities.put(rotatePacked(e.getKey(), side, rot), e.getValue());
        for (StoredEntity se : entities) {
            double[] xz = rotatePoint(se.rel.x, se.rel.z, side, rot);
            CompoundTag tag = se.data.copy();
            if (tag.contains("Pos", Tag.TAG_LIST)) {
                // Pos relativo y girado, igual que la entidad: el pegado lo lleva a su sitio junto con TileX/Z.
                if (tag.contains("TileX")) {
                    ListTag old = tag.getList("Pos", Tag.TAG_DOUBLE);
                    double ox = old.getDouble(0) - se.rel.x, oz = old.getDouble(2) - se.rel.z;   // esquina de la captura
                    int tx = (int) Math.round(tag.getInt("TileX") - ox), tz = (int) Math.round(tag.getInt("TileZ") - oz);
                    int[] t = rotateCell(tx, tz, side, rot);
                    tag.putInt("TileX", t[0]);
                    tag.putInt("TileZ", t[1]);
                    tag.putInt("TileY", (int) Math.round(tag.getInt("TileY") - (old.getDouble(1) - se.rel.y)));
                }
                tag.put("Pos", doubles(xz[0], se.rel.y, xz[1]));
            }
            if (tag.contains("Rotation", Tag.TAG_LIST)) {
                ListTag r = tag.getList("Rotation", Tag.TAG_FLOAT);
                if (r.size() >= 2) {
                    float yaw = r.getFloat(0) + 90f * steps(rot), pitch = r.getFloat(1);
                    ListTag nr = new ListTag();
                    nr.add(net.minecraft.nbt.FloatTag.valueOf(net.minecraft.util.Mth.wrapDegrees(yaw)));
                    nr.add(net.minecraft.nbt.FloatTag.valueOf(pitch));
                    tag.put("Rotation", nr);
                }
            }
            // Marcos: "Facing" 3D; cuadros: "facing" 2D (horizontal).
            if (tag.contains("Facing", Tag.TAG_BYTE)) {
                net.minecraft.core.Direction f = net.minecraft.core.Direction.from3DDataValue(tag.getByte("Facing"));
                if (f.getAxis().isHorizontal()) tag.putByte("Facing", (byte) rot.rotate(f).get3DDataValue());
            }
            if (tag.contains("facing", Tag.TAG_BYTE)) {
                net.minecraft.core.Direction f = net.minecraft.core.Direction.from2DDataValue(tag.getByte("facing"));
                tag.putByte("facing", (byte) rot.rotate(f).get2DDataValue());
            }
            d.entities.add(new StoredEntity(new Vec3(xz[0], se.rel.y, xz[1]), tag));
        }
        return d;
    }

    private static int steps(net.minecraft.world.level.block.Rotation rot) {
        return switch (rot) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
    }

    /** Celda (x, z) en 0..side−1 girada alrededor del centro de la zona. */
    static int[] rotateCell(int x, int z, int side, net.minecraft.world.level.block.Rotation rot) {
        int m = side - 1;
        return switch (rot) {
            case NONE -> new int[]{x, z};
            case CLOCKWISE_90 -> new int[]{m - z, x};
            case CLOCKWISE_180 -> new int[]{m - x, m - z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, m - x};
        };
    }

    /** Punto continuo (x, z) en 0..side (entidades), con el mismo giro que {@link #rotateCell}. */
    static double[] rotatePoint(double x, double z, int side, net.minecraft.world.level.block.Rotation rot) {
        return switch (rot) {
            case NONE -> new double[]{x, z};
            case CLOCKWISE_90 -> new double[]{side - z, x};
            case CLOCKWISE_180 -> new double[]{side - x, side - z};
            case COUNTERCLOCKWISE_90 -> new double[]{z, side - x};
        };
    }

    /** Posición empaquetada ({@link CapsuleZone#pack}) girada; lo usa también el holograma de la cápsula colocada. */
    public static int rotatePacked(int p, int side, net.minecraft.world.level.block.Rotation rot) {
        int[] xz = rotateCell(CapsuleZone.unpackX(p), CapsuleZone.unpackZ(p), side, rot);
        return CapsuleZone.pack(xz[0], CapsuleZone.unpackY(p), xz[1]);
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
