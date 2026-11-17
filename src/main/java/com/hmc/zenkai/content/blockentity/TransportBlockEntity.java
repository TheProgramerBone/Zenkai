package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.content.block.TransportBlock;
import com.hmc.zenkai.content.block.TransportGeometry;
import com.hmc.zenkai.feature.transport.Look;
import com.hmc.zenkai.feature.transport.SideMode;
import com.hmc.zenkai.feature.transport.TankFormation;
import com.hmc.zenkai.feature.transport.TransportNetwork;
import com.hmc.zenkai.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Base de los transportes (cables, tuberías, tanques): el modo de cada una de las seis caras, su
 * ASPECTO sincronizado al cliente y la lógica común. Qué se mueve (FE o fluido) lo decide cada subclase.
 *
 * RED COMPARTIDA (como Mekanism, desde 2026-11-17): los cables conectados forman una
 * {@link TransportNetwork} con UN búfer cuya capacidad es la suma de la de cada cable; la red entrega y
 * tira de lo que la rodea según el modo de cada cara. Cada cable guarda en NBT solo su cuota. Hasta esa
 * fecha cada bloque tenía su búfer y equilibraba con sus vecinos: una línea larga tardaba en llenarse.
 * Los TANQUES son extremos de la red de cables, pero entre ellos forman GRUPOS multibloque al estilo
 * Create ({@link TankFormation}: base cuadrada hasta 3x3, cualquier altura) con la misma maquinaria de
 * búfer y cuotas. Cada tanque manda al cliente qué bordes de cada cara son borde del grupo
 * ({@link #CT_PROPERTY}) para la textura conectada.
 *
 * ASPECTO (como los transmisores de Mekanism): el servidor deduce de los modos y de los vecinos un
 * {@link Look} por cara, lo guarda empaquetado en 18 bits y lo manda al cliente por el paquete de
 * actualización del block entity. El cliente lo entrega al modelo como ModelData
 * ({@link #LOOK_PROPERTY}) y lo usa para la forma. El blockstate no lleva nada.
 */
public abstract class TransportBlockEntity extends BlockEntity {

    /** Aspecto empaquetado de las seis caras, para el modelo. */
    public static final ModelProperty<Integer> LOOK_PROPERTY = new ModelProperty<>();

    /**
     * Textura conectada de un tanque: 4 bits por cara (izquierda, derecha, arriba, abajo, vistos desde
     * fuera) que dicen si ese lado de la cara es BORDE del grupo; 6 caras en el orden de
     * get3DDataValue(). Como un grupo es un prisma, no hay esquinas interiores: 16 variantes por cara.
     */
    public static final ModelProperty<Integer> CT_PROPERTY = new ModelProperty<>();
    public static final int CT_ALONE = 0xFFFFFF;
    public static final int CT_LEFT = 1, CT_RIGHT = 2, CT_TOP = 4, CT_BOTTOM = 8;

    /**
     * Qué dirección es la izquierda, derecha, arriba y abajo de cada cara VISTA DESDE FUERA, con la
     * orientación de UV por defecto de Minecraft (la de los modelos de cubo): en la cara norte la
     * u crece hacia el oeste, así que su izquierda es el este; arriba en la cara de abajo es el sur.
     */
    private static final Direction[][] FACE_AXES = {
            {Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH},   // DOWN
            {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH},   // UP
            {Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN},       // NORTH
            {Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN},       // SOUTH
            {Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN},     // WEST
            {Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN},     // EAST
    };

    private final SideMode[] modes = new SideMode[6];
    /** Red a la que pertenece (solo cables y solo en el servidor). Transitoria: no se guarda. */
    private @Nullable TransportNetwork network;
    private final Look[] look = new Look[6];
    private int ctBits = CT_ALONE;
    private int ticks;

    protected TransportBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        Arrays.fill(modes, SideMode.NORMAL);
        Arrays.fill(look, Look.OFF);
    }

    public abstract TransportBlock.Kind kind();

    /** Texto para la action bar al hacer clic derecho. */
    public abstract Component info();

    protected TransportBlock block() { return (TransportBlock) getBlockState().getBlock(); }
    protected boolean isCable() { return block().isCable(); }
    public boolean isCableBlock() { return isCable(); }

    /** Capacidad de ESTE bloque (para la red, la suma de las de sus cables). */
    public abstract long capacityLong();

    // ── Red ──────────────────────────────────────────────────────────────────

    /** Acumulador de cuotas del tipo de red de este bloque. */
    public abstract TransportNetwork.Accumulator newAccumulator();

    /** Red del cable o grupo del tanque, construyéndolo si hace falta. null en el cliente o si ya se quitó. */
    protected @Nullable TransportNetwork network() {
        if (level == null || level.isClientSide || isRemoved()) return null;
        if (network == null || !network.isValid()) {
            if (isCable()) TransportNetwork.build(this);
            else TankFormation.build(this);
        }
        return network;
    }

    public int ctBits() { return ctBits; }

    /** La red actual si es válida, SIN construir ninguna. */
    public @Nullable TransportNetwork currentNetwork() {
        return network != null && network.isValid() ? network : null;
    }

    public void attachNetwork(TransportNetwork net) {
        network = net;
        if (!isCable()) updateConnectedTexture(net);
    }

    /** Recalcula los bordes de cada cara frente al grupo y, si cambiaron, los manda al cliente. */
    private void updateConnectedTexture(TransportNetwork group) {
        int bits = 0;
        for (Direction face : Direction.values()) {
            Direction[] axes = FACE_AXES[face.get3DDataValue()];
            int e = 0;
            for (int k = 0; k < 4; k++) {
                if (!group.contains(worldPosition.relative(axes[k]))) e |= 1 << k;
            }
            bits |= e << (4 * face.get3DDataValue());
        }
        if (bits == ctBits) return;
        ctBits = bits;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void detachNetwork(TransportNetwork net) { if (network == net) network = null; }

    /** Disuelve la red actual (sus cuotas quedan escritas en cada miembro). */
    public void dissolveNetwork() {
        if (network != null) network.invalidate();
        network = null;
    }

    /** Pone al día la cuota de este cable antes de leerla o guardarla. */
    protected void syncShare() {
        TransportNetwork n = currentNetwork();
        if (n != null) n.syncShares();
    }

    /** ¿Este cable y el cable vecino {@code o} (en la dirección {@code d}) se unen por sus modos? */
    public boolean joinsByModes(TransportBlockEntity o, Direction d) {
        return isCable() && o.isCable() && o.kind() == kind()
                && mode(d) != SideMode.NONE && o.mode(d.getOpposite()) != SideMode.NONE;
    }

    /** ¿Sus contenidos dejan unirlos? Las tuberías no mezclan fluidos. */
    protected boolean compatibleContents(TransportBlockEntity o) { return true; }

    @Override
    public void setRemoved() {
        super.setRemoved();
        dissolveNetwork();   // también al descargar el chunk: lo que queda cargado se rehace sin este cable
    }

    public SideMode mode(Direction d) { return modes[d.get3DDataValue()]; }
    public Look look(Direction d) { return look[d.get3DDataValue()]; }
    public int lookBits() { return Look.pack(look); }

    // ── Modo de cara ─────────────────────────────────────────────────────────

    /** Pasa la cara al siguiente modo y refresca aspecto y capabilities. Devuelve el nuevo modo. */
    public SideMode cycleMode(Direction d) {
        SideMode next = mode(d).next();
        modes[d.get3DDataValue()] = next;
        setChanged();
        dissolveNetwork();   // una cara Desconectada parte la red; una que deja de estarlo la une
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            recomputeLook();
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());   // un cable vecino depende de MI modo
        }
        return next;
    }

    /**
     * La cara hacia la que apunta un clic, por el PUNTO golpeado (la lógica de Mekanism): sobre un
     * brazo es ese brazo, aunque se haya pulsado su lado de arriba; sobre el núcleo o sobre un tanque,
     * la cara que se tocó. Las cajas son las mismas que dibuja el modelo (TransportGeometry).
     */
    public Direction sideAt(BlockHitResult hit) {
        Direction face = hit.getDirection();
        if (!isCable()) return face;
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(worldPosition));
        for (Direction d : Direction.values()) {
            Look l = look(d);
            if (l != Look.NORMAL && l != Look.PUSH && l != Look.PULL) continue;
            if (TransportGeometry.armShape(d, l).bounds().inflate(1.0E-3).contains(local)) return d;
        }
        return face;
    }

    // ── Aspecto ──────────────────────────────────────────────────────────────

    /**
     * Recalcula el aspecto a partir de los modos y de los vecinos, y si cambió lo manda al cliente.
     * Cables y tuberías: Empujar y Tirar son una configuración explícita y se ven siempre (así el
     * cambio de modo se nota aunque no haya nada delante); Normal solo se ve con algo conectado; una
     * cara Desconectada no tiene brazo. Tanques: el aspecto ES el modo.
     */
    public void recomputeLook() {
        if (level == null || level.isClientSide) return;
        boolean cable = isCable();
        boolean changed = false;
        for (Direction d : Direction.values()) {
            Look now = cable ? cableLook(d) : Look.of(mode(d));
            int i = d.get3DDataValue();
            if (look[i] != now) { look[i] = now; changed = true; }
        }
        if (changed) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private Look cableLook(Direction d) {
        SideMode mode = mode(d);
        if (mode == SideMode.NONE) return Look.OFF;
        if (mode == SideMode.PUSH) return Look.PUSH;
        if (mode == SideMode.PULL) return Look.PULL;
        BlockPos np = worldPosition.relative(d);
        Direction opp = d.getOpposite();
        BlockEntity nb = level.getBlockEntity(np);
        boolean connected;
        if (nb instanceof TransportBlockEntity o && o.kind() == kind()) {
            connected = o.isCable() ? joinsByModes(o, d) && compatibleContents(o) : o.mode(opp) != SideMode.NONE;
        } else {
            connected = kind() == TransportBlock.Kind.ENERGY
                    ? level.getCapability(Capabilities.EnergyStorage.BLOCK, np, opp) != null
                    : level.getCapability(Capabilities.FluidHandler.BLOCK, np, opp) != null;
        }
        return connected ? Look.NORMAL : Look.OFF;
    }

    /**
     * Llamar al principio del tick del servidor: repasa el aspecto en el primer tick (un tanque
     * colocado desde su ítem trae los modos de los componentes DESPUÉS de crearse) y después cada
     * 2 s (un vecino pudo ganar su capability sin avisar).
     */
    protected void housekeeping() {
        if (level != null && (++ticks == 1 || ticks % 40 == 0)) {
            recomputeLook();
            if (isCable()) mergeCheck();
        }
    }

    /**
     * Si un cable vecino unido está en OTRA red, se disuelve la propia para que el relleno las junte.
     * Cubre lo que no avisa: una tubería que se vacía y ya puede unirse a la de otro fluido, un chunk
     * vecino que se carga.
     */
    private void mergeCheck() {
        TransportNetwork mine = currentNetwork();
        if (mine == null) return;
        for (Direction d : Direction.values()) {
            BlockPos np = worldPosition.relative(d);
            if (!level.isLoaded(np)) continue;
            if (level.getBlockEntity(np) instanceof TransportBlockEntity o && joinsByModes(o, d)
                    && compatibleContents(o) && o.currentNetwork() != null && o.currentNetwork() != mine) {
                mine.invalidate();
                return;
            }
        }
    }

    // ── Sincronización con el cliente ────────────────────────────────────────

    @Override
    public void onLoad() {
        super.onLoad();
        requestModelDataUpdate();   // no hace nada en el servidor
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Look", lookBits());
        tag.putInt("Ct", ctBits);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull ModelData getModelData() {
        return ModelData.builder().with(LOOK_PROPERTY, lookBits()).with(CT_PROPERTY, ctBits).build();
    }

    private void readLook(int bits) {
        for (int i = 0; i < 6; i++) look[i] = Look.unpack(bits, i);
    }

    // ── Componentes del ítem (solo tanques: un cable no «lleva» nada al romperse) ──

    @Override
    protected void collectImplicitComponents(@NotNull DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (isCable()) return;
        List<Integer> raw = new ArrayList<>(6);
        boolean custom = false;
        for (SideMode m : modes) {
            raw.add(m.ordinal());
            custom |= m != SideMode.NORMAL;
        }
        if (custom) builder.set(ModDataComponents.TRANSPORT_MODES.get(), raw);
    }

    @Override
    protected void applyImplicitComponents(@NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        if (isCable()) return;
        List<Integer> raw = input.get(ModDataComponents.TRANSPORT_MODES.get());
        if (raw == null) return;
        SideMode[] all = SideMode.values();
        for (int i = 0; i < 6 && i < raw.size(); i++) {
            int o = raw.get(i);
            modes[i] = o >= 0 && o < all.length ? all[o] : SideMode.NORMAL;
        }
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        int[] raw = new int[6];
        for (int i = 0; i < 6; i++) raw[i] = modes[i].ordinal();
        tag.putIntArray("Modes", raw);
        tag.putInt("Look", lookBits());
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        int[] raw = tag.getIntArray("Modes");
        SideMode[] all = SideMode.values();
        for (int i = 0; i < 6 && i < raw.length; i++) {
            modes[i] = raw[i] >= 0 && raw[i] < all.length ? all[raw[i]] : SideMode.NORMAL;
        }
        if (tag.contains("Look")) {
            int before = lookBits();
            int ctBefore = ctBits;
            readLook(tag.getInt("Look"));
            if (tag.contains("Ct")) ctBits = tag.getInt("Ct");
            // En el cliente llega aquí desde el paquete de actualización. requestModelDataUpdate() solo
            // refresca el DATO; NO vuelve a dibujar el trozo de mapa. Sin el aviso de abajo el modelo se
            // queda con el aspecto antiguo aunque la forma (que lee el block entity directamente) ya
            // haya cambiado: la caja de selección crecía y el brazo no aparecía (capturas del 2026-11-16).
            requestModelDataUpdate();
            if (level != null && level.isClientSide && (before != lookBits() || ctBefore != ctBits)) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
            }
        }
    }
}
