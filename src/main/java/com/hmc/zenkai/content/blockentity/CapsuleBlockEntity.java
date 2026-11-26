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
 *
 * CONTROL REMOTO ({@link com.hmc.zenkai.feature.capsule.CapsuleRemote}): el mismo ciclo a distancia, pero la cápsula
 * NO vuelve a ser ítem, para poder alternar abrir/recoger. Al recoger a distancia, la llena se queda de pie un bloque MÁS
 * ABAJO (donde estaba el suelo que se guardó); al abrir a distancia, el suelo pegado ocupa su sitio y la vacía se pone de
 * pie un bloque MÁS ARRIBA, en el centro de ese suelo. Así la zona de la siguiente captura coincide exacta con lo pegado.
 * Si no hay sitio para ponerla, hace lo de siempre (ítem en el suelo o al inventario).
 */
public class CapsuleBlockEntity extends BlockEntity {

    public enum Mode { IDLE, OUTLINE, ARMED }

    /** Cápsulas cargadas en el cliente, para el renderer del contorno/holograma (como la consola de gravedad). */
    public static final Set<CapsuleBlockEntity> CLIENT_LOADED = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private ItemStack capsule = ItemStack.EMPTY;
    private Mode mode = Mode.IDLE;
    @Nullable private UUID armedBy;
    /** Armada desde el control remoto: al capturar, la llena se queda colocada (ver la cabecera). */
    private boolean remoteArmed;
    /** Posiciones empaquetadas de lo guardado (solo llena), para el holograma. Se lee del archivo al colocarla. */
    private int[] preview = new int[0];
    private int previewRadius;
    /**
     * Giro con el que se pegará lo guardado (solo llena). Shift + clic derecho suma 90° (CapsuleRotateEvents); el holograma
     * ({@link #preview}) ya va girado. Vuelve a NONE con cada stack nuevo.
     */
    private net.minecraft.world.level.block.Rotation rotation = net.minecraft.world.level.block.Rotation.NONE;

    public CapsuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAPSULE.get(), pos, state);
    }

    // ── Estado ───────────────────────────────────────────────────────────────

    public void setCapsule(ItemStack stack) {
        this.capsule = stack;
        registerLink();
        this.mode = Mode.IDLE;
        this.preview = new int[0];
        this.rotation = net.minecraft.world.level.block.Rotation.NONE;
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
        unregisterLink();
        ItemStack s = capsule;
        capsule = ItemStack.EMPTY;
        return s;
    }

    public Mode mode() { return mode; }
    public boolean full() { return CapsuleItem.isFull(capsule); }
    public int[] preview() { return preview; }
    public int previewRadius() { return previewRadius; }

    /** Id de vínculo del control remoto, o null si nunca se vinculó. */
    public @Nullable UUID linkId() { return capsule.get(ModDataComponents.CAPSULE_LINK.get()); }

    /** Id de vínculo, creándolo si hace falta (viaja con el stack: captura, pegado y la rotura lo conservan). */
    public @Nullable UUID ensureLinkId() {
        if (capsule.isEmpty()) return null;
        UUID id = linkId();
        if (id == null) {
            id = UUID.randomUUID();
            capsule.set(ModDataComponents.CAPSULE_LINK.get(), id);
            setChanged();
        }
        registerLink();
        return id;
    }

    /** Desvincular desde el control: la cápsula pierde su id y vuelve a apilarse con las nuevas. */
    public void clearLinkId() {
        unregisterLink();
        if (!capsule.isEmpty()) capsule.remove(ModDataComponents.CAPSULE_LINK.get());
        setChanged();
    }

    /** Alta en {@link com.hmc.zenkai.feature.capsule.CapsuleLinkRegistry} (solo si está vinculada y en un mundo de servidor). */
    private void registerLink() {
        UUID id = linkId();
        if (id != null && level instanceof ServerLevel sl) {
            com.hmc.zenkai.feature.capsule.CapsuleLinkRegistry.get(sl.getServer())
                    .put(id, net.minecraft.core.GlobalPos.of(sl.dimension(), worldPosition));
        }
    }

    private void unregisterLink() {
        UUID id = linkId();
        if (id != null && level instanceof ServerLevel sl) {
            com.hmc.zenkai.feature.capsule.CapsuleLinkRegistry.get(sl.getServer())
                    .remove(id, net.minecraft.core.GlobalPos.of(sl.dimension(), worldPosition));
        }
    }

    /** Nombre puesto por el jugador (pantalla de la cápsula o yunque); "" si no tiene. */
    public String customName() {
        Component c = capsule.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (c == null) return "";
        String n = c.getString();
        return n.length() > com.hmc.zenkai.feature.capsule.CapsuleRenamePayload.MAX_LENGTH
                ? n.substring(0, com.hmc.zenkai.feature.capsule.CapsuleRenamePayload.MAX_LENGTH) : n;
    }

    /** Pone o quita (vacío) el nombre. Validado en CapsuleRenamePayload. */
    public void rename(String name) {
        if (capsule.isEmpty()) return;
        if (name.isBlank()) capsule.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        else capsule.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name.strip()));
        setChanged();
    }

    public net.minecraft.world.level.block.Rotation rotation() { return rotation; }

    /** Shift + clic derecho sobre la llena: 90° más en sentido horario, con el holograma girado al momento. */
    public void rotate(ServerPlayer player) {
        if (!full() || !(level instanceof ServerLevel)) return;
        var cw = net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
        rotation = rotation.getRotated(cw);
        int side = CapsuleZone.side(previewRadius);
        for (int i = 0; i < preview.length; i++) preview[i] = CapsuleData.rotatePacked(preview[i], side, cw);
        int degrees = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
        };
        player.displayClientMessage(Component.translatable("message.zenkai.capsule.rotation",
                com.hmc.zenkai.util.ZenkaiNumbers.screen(degrees)), true);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
        setChanged();
        sync();
    }

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
                        r.radius(), r.blocks().size(), r.mobs(), r.mobVolume(), r.skipped(), r.budget(), customName()));
            }
            case ARMED -> {
                mode = Mode.OUTLINE;
                armedBy = null;
                remoteArmed = false;
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
        remoteArmed = false;
        player.displayClientMessage(Component.translatable("message.zenkai.capsule.armed"), false);
        setChanged();
        sync();
    }

    /**
     * Clic al aire con el control remoto (ya validados dueño, dimensión, alcance y que el chunk esté cargado). Llena →
     * abre; vacía → arma (la captura espera, como siempre, a que nadie quede dentro); armada → cancela.
     */
    public void remoteToggle(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl) || capsule.isEmpty()) return;
        if (full()) {
            releaseRemote(sl, player);
            return;
        }
        if (mode == Mode.ARMED) {
            mode = Mode.OUTLINE;
            armedBy = null;
            remoteArmed = false;
            player.displayClientMessage(Component.translatable("message.zenkai.capsule.cancelled"), true);
            setChanged();
            sync();
            return;
        }
        // Sin pantalla de por medio: la misma comprobación que deja activo el botón Guardar.
        CapsuleScan.Result r = CapsuleScan.scan(sl, worldPosition, tier().radius());
        if (r.empty()) {
            player.displayClientMessage(Component.translatable("message.zenkai.capsule.capture_nothing"), true);
            return;
        }
        if (r.overBudget()) {
            player.displayClientMessage(Component.translatable("message.zenkai.capsule.capture_too_big"), true);
            return;
        }
        mode = Mode.ARMED;
        armedBy = player.getUUID();
        remoteArmed = true;
        player.displayClientMessage(Component.translatable("message.zenkai.capsule_remote.armed"), true);
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
        boolean remote = remoteArmed;
        mode = Mode.IDLE;
        armedBy = null;
        remoteArmed = false;
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
        // A distancia, la llena se queda colocada para poder abrirla otra vez con el control: un bloque más abajo (el
        // suelo guardado dejó ese hueco), o en su sitio si abajo se quedó algo; si no hay dónde, ítem como siempre.
        if (!remote || !(place(sl, pos.below(), stack) || place(sl, pos, stack))) {
            Containers.dropItemStack(sl, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        if (owner != null) {
            owner.displayClientMessage(r.skipped() > 0
                    ? Component.translatable("message.zenkai.capsule.captured_skipped", r.skipped())
                    : Component.translatable("message.zenkai.capsule.captured"), false);
        }
    }

    private void release(ServerLevel sl, ServerPlayer player) {
        CapsuleContent c = CapsuleItem.content(capsule);
        if (c == null) return;
        CapsuleRelease.Outcome o = CapsuleRelease.release(sl, worldPosition, c, player, worldPosition, rotation, null);
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

    /**
     * Abrir a distancia: como {@link #release}, pero la vacía no va al inventario sino que se queda de pie sobre el centro
     * del suelo pegado (un bloque más arriba), y eso solo se puede hacer al TERMINAR el pegado (va repartido en ticks).
     * Mientras tanto el stack viaja en el cierre del pegado; si al final no hay sitio, va al inventario de quien la abrió
     * (o cae ahí mismo si se desconectó). Un cierre inesperado del servidor a mitad del pegado pierde esa cápsula VACÍA
     * (nunca duplica nada).
     */
    private void releaseRemote(ServerLevel sl, ServerPlayer player) {
        CapsuleContent c = CapsuleItem.content(capsule);
        if (c == null) return;
        BlockPos pos = worldPosition;
        ItemStack empty = CapsuleItem.emptied(capsule);
        UUID who = player.getUUID();
        CapsuleRelease.Outcome o = CapsuleRelease.release(sl, pos, c, player, pos, rotation, () -> {
            if (place(sl, pos.above(), empty)) return;
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(who);
            if (p != null) {
                if (!p.getInventory().add(empty)) p.drop(empty, false);
            } else {
                Containers.dropItemStack(sl, pos.getX(), pos.getY() + 1, pos.getZ(), empty);
            }
        });
        if (o == CapsuleRelease.Outcome.MISSING) {
            capsule = CapsuleItem.emptied(capsule);
            sl.setBlock(pos, getBlockState().setValue(CapsuleBlock.FULL, false), 3);
            preview = new int[0];
            setChanged();
            sync();
        }
        if (o != CapsuleRelease.Outcome.OK) {
            player.displayClientMessage(Component.translatable(
                    "message.zenkai.capsule.release_" + o.name().toLowerCase(java.util.Locale.ROOT)), true);
            return;
        }
        takeCapsule();
        sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        player.displayClientMessage(Component.translatable("message.zenkai.capsule_remote.released"), true);
    }

    /**
     * Coloca la cápsula {@code stack} de pie en {@code at} si allí cabe (reemplazable) y debajo hay suelo firme, como
     * {@link CapsuleItem#useOn}. Devuelve si la colocó.
     */
    public static boolean place(ServerLevel sl, BlockPos at, ItemStack stack) {
        if (!(stack.getItem() instanceof CapsuleItem item) || sl.isOutsideBuildHeight(at)) return false;
        if (!sl.getBlockState(at).canBeReplaced() || !sl.getBlockState(at.below()).isFaceSturdy(sl, at.below(),
                net.minecraft.core.Direction.UP)) return false;
        sl.setBlock(at, com.hmc.zenkai.registry.ModBlocks.CAPSULE.get().defaultBlockState()
                .setValue(CapsuleBlock.TIER, item.tier())
                .setValue(CapsuleBlock.FULL, CapsuleItem.isFull(stack)), 3);
        if (!(sl.getBlockEntity(at) instanceof CapsuleBlockEntity be)) return false;
        be.setCapsule(stack.copyWithCount(1));
        return true;
    }

    // ── Guardado y sincronía ─────────────────────────────────────────────────

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (!capsule.isEmpty()) tag.put("Capsule", capsule.save(reg));
        tag.putString("Mode", mode.name());
        if (armedBy != null) tag.putUUID("ArmedBy", armedBy);
        if (remoteArmed) tag.putBoolean("RemoteArmed", true);
        tag.putIntArray("Preview", preview);
        tag.putInt("PreviewRadius", previewRadius);
        tag.putString("Rotation", rotation.name());
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
        remoteArmed = tag.getBoolean("RemoteArmed");
        preview = tag.getIntArray("Preview");
        previewRadius = tag.getInt("PreviewRadius");
        try {
            rotation = net.minecraft.world.level.block.Rotation.valueOf(tag.getString("Rotation"));
        } catch (IllegalArgumentException e) {
            rotation = net.minecraft.world.level.block.Rotation.NONE;
        }
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
        // Servidor: las cápsulas vinculadas antes de existir el registro (o colocadas por /setblock con NBT) se dan de alta
        // al cargarse su chunk.
        else registerLink();
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
