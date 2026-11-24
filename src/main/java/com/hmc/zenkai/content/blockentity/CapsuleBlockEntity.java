package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.content.block.CapsuleBlock;
import com.hmc.zenkai.content.item.CapsuleItem;
import com.hmc.zenkai.feature.capsule.CapsuleCapture;
import com.hmc.zenkai.feature.capsule.CapsuleContent;
import com.hmc.zenkai.feature.capsule.CapsuleData;
import com.hmc.zenkai.feature.capsule.CapsuleRelease;
import com.hmc.zenkai.feature.capsule.CapsuleScan;
import com.hmc.zenkai.feature.capsule.CapsuleStorage;
import com.hmc.zenkai.feature.capsule.CapsuleTier;
import com.hmc.zenkai.feature.capsule.CapsuleZone;
import com.hmc.zenkai.feature.capsule.OpenCapsuleScreenPayload;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Estado de una cápsula colocada (Fase 6). Guarda el STACK de la cápsula (rango y, si está llena, su contenido) y el modo:
 *
 *   IDLE     vacía, recién colocada. Clic → OUTLINE.
 *   OUTLINE  vacía, se ve el contorno de la zona en el cliente. Clic → la pantalla con el resumen (lo calcula el servidor).
 *   ARMED    se pulsó Guardar: espera a que no quede NINGÚN jugador dentro de la zona (los jugadores nunca se guardan) y
 *            entonces captura. Clic → cancela y vuelve a OUTLINE.
 *   (llena)  enseña el holograma de lo que pegaría (cubos cian; rojos donde algo estorba, lo calcula el cliente). Clic →
 *            suelta lo guardado; la cápsula vacía cae junto al jugador.
 *
 * Tras guardar, la cápsula se cierra con un «¡puf!» y queda en el suelo como ítem LLENO.
 */
public class CapsuleBlockEntity extends BlockEntity {

    public enum Mode { IDLE, OUTLINE, ARMED }

    /** Cápsulas cargadas en el cliente, para el renderer del contorno/holograma (como la consola de gravedad). */
    public static final Set<CapsuleBlockEntity> CLIENT_LOADED = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private ItemStack capsule = ItemStack.EMPTY;
    private Mode mode = Mode.IDLE;
    @Nullable private UUID armedBy;
    /** Posiciones empaquetadas de lo guardado (solo llena), para el holograma. Se lee del archivo al colocarla. */
    private int[] preview = new int[0];
    private int previewRadius;

    public CapsuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAPSULE.get(), pos, state);
    }

    // ── Estado ───────────────────────────────────────────────────────────────

    public void setCapsule(ItemStack stack) {
        this.capsule = stack;
        this.mode = Mode.IDLE;
        this.preview = new int[0];
        CapsuleContent c = CapsuleItem.content(stack);
        if (c != null && level instanceof ServerLevel sl) {
            CompoundTag tag = CapsuleStorage.read(sl.getServer(), c.id());
            if (tag != null) {
                this.preview = CapsuleData.load(tag, sl).previewPositions();
            }
            this.previewRadius = c.radius();
        }
        setChanged();
        sync();
    }

    /** Saca el stack (al romperse o al soltar); el bloque queda sin nada que soltar. */
    public ItemStack takeCapsule() {
        ItemStack s = capsule;
        capsule = ItemStack.EMPTY;
        return s;
    }

    public Mode mode() { return mode; }
    public boolean full() { return CapsuleItem.isFull(capsule); }
    public int[] preview() { return preview; }
    public int previewRadius() { return previewRadius; }

    public CapsuleTier tier() {
        return getBlockState().hasProperty(CapsuleBlock.TIER) ? getBlockState().getValue(CapsuleBlock.TIER) : CapsuleTier.COPPER;
    }

    // ── Interacción ──────────────────────────────────────────────────────────

    public void onUse(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl) || capsule.isEmpty()) return;
        if (full()) {
            release(sl, player);
            return;
        }
        switch (mode) {
            case IDLE -> {
                mode = Mode.OUTLINE;
                player.displayClientMessage(Component.translatable("message.zenkai.capsule.outline"), true);
                setChanged();
                sync();
            }
            case OUTLINE -> {
                CapsuleScan.Result r = CapsuleScan.scan(sl, worldPosition, tier().radius());
                PacketDistributor.sendToPlayer(player, new OpenCapsuleScreenPayload(worldPosition, tier().ordinal(),
                        r.radius(), r.blocks().size(), r.mobs(), r.mobVolume(), r.skipped(), r.budget()));
            }
            case ARMED -> {
                mode = Mode.OUTLINE;
                armedBy = null;
                player.displayClientMessage(Component.translatable("message.zenkai.capsule.cancelled"), true);
                setChanged();
                sync();
            }
        }
    }

    /** Botón Guardar de la pantalla (CapsuleArmPayload). */
    public void arm(ServerPlayer player) {
        if (full() || capsule.isEmpty() || mode == Mode.ARMED) return;
        mode = Mode.ARMED;
        armedBy = player.getUUID();
        player.displayClientMessage(Component.translatable("message.zenkai.capsule.armed"), false);
        setChanged();
        sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CapsuleBlockEntity be) {
        if (be.mode != Mode.ARMED || !(level instanceof ServerLevel sl) || level.getGameTime() % 10 != 0) return;
        var box = CapsuleZone.box(CapsuleZone.captureMin(pos, be.tier().radius()), be.tier().radius());
        boolean occupied = !sl.getEntitiesOfClass(Player.class, box, p -> !p.isSpectator()).isEmpty();
        if (!occupied) be.capture(sl);
    }

    private void capture(ServerLevel sl) {
        ServerPlayer owner = armedBy == null ? null : sl.getServer().getPlayerList().getPlayer(armedBy);
        // Sin el jugador conectado, los claims se consultan con un jugador falso: un mod de protección lo tratará como
        // un extraño y cancelará la captura en una zona protegida, que es lo seguro.
        Player actor = owner != null ? owner : FakePlayerFactory.getMinecraft(sl);
        BlockPos pos = worldPosition;
        // El stack sale ANTES de capturar: al quitar el suelo de debajo la cápsula se cae (updateShape) y, si aún lo
        // tuviera, soltaría una cápsula vacía.
        ItemStack stack = takeCapsule();
        mode = Mode.IDLE;
        armedBy = null;
        CapsuleCapture.Result r = CapsuleCapture.capture(sl, pos, tier().radius(), actor);
        if (r.outcome() != CapsuleCapture.Outcome.OK) {
            if (sl.getBlockEntity(pos) == this) {
                capsule = stack;
                mode = Mode.OUTLINE;
                setChanged();
                sync();
            } else {
                Containers.dropItemStack(sl, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
            if (owner != null) owner.displayClientMessage(Component.translatable(
                    "message.zenkai.capsule.capture_" + r.outcome().name().toLowerCase(java.util.Locale.ROOT)), false);
            return;
        }
        stack.set(ModDataComponents.CAPSULE_CONTENT.get(), r.content());
        if (sl.getBlockState(pos).getBlock() instanceof CapsuleBlock) sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        Containers.dropItemStack(sl, pos.getX(), pos.getY(), pos.getZ(), stack);
        if (owner != null) {
            owner.displayClientMessage(r.skipped() > 0
                    ? Component.translatable("message.zenkai.capsule.captured_skipped", r.skipped())
                    : Component.translatable("message.zenkai.capsule.captured"), false);
        }
    }

    private void release(ServerLevel sl, ServerPlayer player) {
        CapsuleContent c = CapsuleItem.content(capsule);
        if (c == null) return;
        CapsuleRelease.Outcome o = CapsuleRelease.release(sl, worldPosition, c, player, worldPosition);
        if (o == CapsuleRelease.Outcome.MISSING) {
            // Copia de una cápsula ya soltada (o archivo borrado): se queda vacía, que es la verdad.
            capsule = CapsuleItem.emptied(capsule);
            sl.setBlock(worldPosition, getBlockState().setValue(CapsuleBlock.FULL, false), 3);
            preview = new int[0];
            setChanged();
            sync();
        }
        if (o != CapsuleRelease.Outcome.OK) {
            player.displayClientMessage(Component.translatable(
                    "message.zenkai.capsule.release_" + o.name().toLowerCase(java.util.Locale.ROOT)), false);
            return;
        }
        ItemStack empty = CapsuleItem.emptied(takeCapsule());
        sl.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);
        if (!player.getInventory().add(empty)) player.drop(empty, false);
    }

    // ── Guardado y sincronía ─────────────────────────────────────────────────

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (!capsule.isEmpty()) tag.put("Capsule", capsule.save(reg));
        tag.putString("Mode", mode.name());
        if (armedBy != null) tag.putUUID("ArmedBy", armedBy);
        tag.putIntArray("Preview", preview);
        tag.putInt("PreviewRadius", previewRadius);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        capsule = tag.contains("Capsule", Tag.TAG_COMPOUND)
                ? ItemStack.parse(reg, tag.getCompound("Capsule")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        try {
            mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.IDLE;
        }
        armedBy = tag.hasUUID("ArmedBy") ? tag.getUUID("ArmedBy") : null;
        preview = tag.getIntArray("Preview");
        previewRadius = tag.getInt("PreviewRadius");
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider reg) {
        // Al cliente solo le hace falta el modo y el holograma, no el stack.
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", mode.name());
        tag.putIntArray("Preview", preview);
        tag.putInt("PreviewRadius", previewRadius);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) CLIENT_LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        CLIENT_LOADED.remove(this);
    }
}
