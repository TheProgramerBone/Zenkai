package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.EnergyTransportBlockEntity;
import com.hmc.zenkai.content.blockentity.FluidTransportBlockEntity;
import com.hmc.zenkai.content.blockentity.TransportBlockEntity;
import com.hmc.zenkai.feature.transport.SideMode;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Los cuatro bloques de transporte: cable de energía, tubería de fluidos y sus dos tanques. Es UNA
 * clase con dos parámetros (qué transporta y si es cable o tanque).
 *
 * UN SOLO ESTADO, como Mekanism: el bloque no tiene propiedades de blockstate. Qué brazo o placa se
 * pinta en cada cara (el «aspecto», {@link com.hmc.zenkai.feature.transport.Look}) vive en el block
 * entity, se sincroniza al cliente y llega al modelo como ModelData (client/model/TransportBakedModel).
 * La forma de colisión y selección también sale del block entity, con una caché por combinación
 * ({@link TransportGeometry#cableShape}). Una propiedad por cara habría sido 4096 estados por bloque.
 *
 * CLIC: agachado + clic derecho con las manos vacías cambia el modo de la cara (también con el
 * Configurator de Mekanism o cualquier llave: ver feature/transport/TransportWrench). Qué cara es lo decide
 * el PUNTO golpeado ({@link TransportBlockEntity#sideAt}): sobre un brazo, ese brazo; sobre el núcleo
 * de un cable o sobre un tanque, la cara tocada. Clic derecho normal enseña el contenido. El tanque
 * de fluidos además acepta cubos y cualquier ítem con fluido.
 *
 * AL ROMPER un tanque conserva contenido y modos en el ítem (data components, como Mekanism): ver
 * TransportBlockEntity#collectImplicitComponents y la tabla de botín de datagen.
 */
public class TransportBlock extends BaseEntityBlock {

    public enum Kind implements StringRepresentable {
        ENERGY, FLUID;
        @Override public @NotNull String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static final MapCodec<TransportBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            propertiesCodec(),
            StringRepresentable.fromEnum(Kind::values).fieldOf("kind").forGetter(b -> b.kind),
            Codec.BOOL.fieldOf("cable").forGetter(b -> b.cable)
    ).apply(i, TransportBlock::new));

    private final Kind kind;
    private final boolean cable;

    public TransportBlock(Properties properties, Kind kind, boolean cable) {
        super(properties);
        this.kind = kind;
        this.cable = cable;
    }

    public Kind kind() { return kind; }
    public boolean isCable() { return cable; }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.MODEL; }

    // ── Forma (sale del block entity) ────────────────────────────────────────

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        if (!cable) return Shapes.block();
        // Sin block entity (generación de mundo, cálculos con EmptyBlockGetter): solo el núcleo.
        int bits = level.getBlockEntity(pos) instanceof TransportBlockEntity be ? be.lookBits() : 0;
        return TransportGeometry.cableShape(bits);
    }

    // ── Block entity ─────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return kind == Kind.ENERGY ? new EnergyTransportBlockEntity(pos, state) : new FluidTransportBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return kind == Kind.ENERGY
                ? createTickerHelper(type, ModBlockEntities.ENERGY_TRANSPORT.get(), EnergyTransportBlockEntity::serverTick)
                : createTickerHelper(type, ModBlockEntities.FLUID_TRANSPORT.get(), FluidTransportBlockEntity::serverTick);
    }

    /** Un vecino cambió: puede haber ganado o perdido su capability, así que se repasan los brazos. */
    @Override
    protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                   @NotNull Block block, @NotNull BlockPos fromPos, boolean moved) {
        super.neighborChanged(state, level, pos, block, fromPos, moved);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TransportBlockEntity be) be.recomputeLook();
    }

    // ── Interacción ──────────────────────────────────────────────────────────

    /** Cubos y demás ítems con fluido contra el tanque de fluidos (la tubería no es un depósito). */
    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state,
                                                       @NotNull Level level, @NotNull BlockPos pos,
                                                       @NotNull Player player, @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hit) {
        if (kind != Kind.FLUID || cable || stack.getCapability(Capabilities.FluidHandler.ITEM) == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
        if (level.getBlockEntity(pos) instanceof FluidTransportBlockEntity be
                && be.fluidAt(null) != null && FluidUtil.interactWithFluidHandler(player, hand, be.fluidAt(null))) {
            return ItemInteractionResult.sidedSuccess(false);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TransportBlockEntity be)) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            configureSide(be, hit, player);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(be.info(), true);
        return InteractionResult.CONSUME;
    }

    /**
     * Pasa al siguiente modo la cara apuntada y lo avisa en la action bar. Lo comparten el clic con la
     * mano vacía y las llaves ({@link com.hmc.zenkai.feature.transport.TransportWrench}). Solo servidor.
     */
    public static void configureSide(TransportBlockEntity be, BlockHitResult hit, Player player) {
        Direction face = be.sideAt(hit);
        SideMode mode = be.cycleMode(face);
        Level level = be.getLevel();
        if (level != null) {
            level.playSound(null, be.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.4f);
        }
        player.displayClientMessage(Component.translatable("messages.zenkai.transport.mode",
                Component.translatable("messages.zenkai.transport.side." + face.getName()),
                Component.translatable(mode.langKey())), true);
    }
}
