package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cámara de contención de ki: bloque de DOS alturas (como una puerta) con modelo GeckoLib.
 * La mitad inferior lleva el block entity y dibuja el modelo entero; la superior existe para
 * ocupar su celda, dar la colisión de arriba y prestar sus caras a tuberías y cables (delega en
 * la inferior, ver Zenkai.registerCapabilities).
 *
 * ACTIVE vive en el blockstate por lo mismo que WORKING en el banco: el cliente se entera por la
 * sincronización normal de bloques y el controlador de animación lo lee sin paquete propio.
 *
 * PUERTA (OPEN, en las dos mitades): clic derecho la abre o la cierra; se cierra sola cuando
 * alguien entra (lo detecta el block entity). Agachado + clic derecho abre la pantalla.
 *
 * COLISIÓN. Cerrada: bloque macizo para quien está fuera y HUECO para quien ya está dentro de su
 * columna (al ocupante lo sujeta el block entity, no las paredes: las formas con escala no caben
 * en un hueco de paredes finas y el empuje de «estás dentro de un bloque» las expulsaría).
 * Abierta: solo los dos costados y la espalda, sin suelo ni techo, para que un jugador de 1,8 o
 * una vaca de 0,9 entren andando a ras del suelo.
 */
public class KiContainmentChamberBlock extends BaseEntityBlock {

    public static final MapCodec<KiContainmentChamberBlock> CODEC = simpleCodec(KiContainmentChamberBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    /** Paredes con la puerta abierta, por orientación del frente. Costados de medio píxel: una vaca mide 0,9. */
    private static final java.util.Map<Direction, VoxelShape> OPEN_SHAPES = new java.util.EnumMap<>(Direction.class);
    static {
        VoxelShape west = Block.box(0, 0, 0, 0.5, 16, 16), east = Block.box(15.5, 0, 0, 16, 16, 16);
        VoxelShape north = Block.box(0, 0, 0, 16, 16, 0.5), south = Block.box(0, 0, 15.5, 16, 16, 16);
        OPEN_SHAPES.put(Direction.NORTH, Shapes.or(west, east, Block.box(0, 0, 15, 16, 16, 16)));
        OPEN_SHAPES.put(Direction.SOUTH, Shapes.or(west, east, Block.box(0, 0, 0, 16, 16, 1)));
        OPEN_SHAPES.put(Direction.WEST, Shapes.or(north, south, Block.box(15, 0, 0, 16, 16, 16)));
        OPEN_SHAPES.put(Direction.EAST, Shapes.or(north, south, Block.box(0, 0, 0, 1, 16, 16)));
    }

    public KiContainmentChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(ACTIVE, false)
                .setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HALF, ACTIVE, OPEN);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    /** Posición de la mitad inferior (la del block entity) a partir de cualquiera de las dos. */
    public static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    // ── Colocación y rotura de las dos mitades (patrón de DoorBlock) ─────────

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null;
        }
        // getOpposite: el frente (la puerta de cristal) mira AL jugador que la coloca.
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER).setValue(ACTIVE, false), 3);
    }

    /** Si desaparece una mitad, la otra se va con ella. */
    @Override
    protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir,
                                              @NotNull BlockState neighbor, @NotNull LevelAccessor level,
                                              @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOther = dir.getAxis() == Direction.Axis.Y
                && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP);
        if (towardsOther && !(neighbor.is(this) && neighbor.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * Rompiendo la mitad SUPERIOR en creativo, la inferior se quita sin soltar el ítem (lo que
     * hace vanilla con puertas y plantas altas). En supervivencia la tabla de botín solo suelta
     * por la mitad inferior, que cae sola por updateShape.
     */
    @Override
    public @NotNull BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos,
                                                 @NotNull BlockState state, @NotNull Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Al quitar el bloque se cierra la sesión: el jugador de dentro deja de estar «en la cámara». */
    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                            @NotNull BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof KiContainmentChamberBlockEntity be) {
            be.endSession();
            be.dropContents();   // mejoras y batería no se evaporan con el bloque
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        Direction facing = state.getValue(FACING);
        return state.setValue(FACING, mirror.getRotation(facing).rotate(facing));
    }

    // ── Forma ────────────────────────────────────────────────────────────────

    @Override
    protected @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                    @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        if (state.getValue(OPEN)) return OPEN_SHAPES.get(state.getValue(FACING));
        if (ctx instanceof EntityCollisionContext ecc && ecc.getEntity() != null
                && isInsideColumn(ecc.getEntity(), lowerPos(state, pos))) {
            return Shapes.empty();
        }
        return Shapes.block();
    }

    /** ¿El centro de la entidad está dentro de la columna de dos bloques de la cámara? */
    private static boolean isInsideColumn(Entity e, BlockPos lower) {
        AABB column = new AABB(lower.getX(), lower.getY(), lower.getZ(),
                lower.getX() + 1.0, lower.getY() + 2.0, lower.getZ() + 1.0);
        return column.contains(e.getX(), e.getY() + 0.01, e.getZ());
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** El cristal deja pasar la luz: sin esto el jugador de dentro se vería a oscuras. */
    @Override
    protected boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level,
                                             @NotNull BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level,
                                       @NotNull BlockPos pos) {
        return 1.0f;
    }

    // ── Block entity ─────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? new KiContainmentChamberBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        return createTickerHelper(type, ModBlockEntities.KI_CONTAINMENT_CHAMBER.get(),
                KiContainmentChamberBlockEntity::serverTick);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(lowerPos(state, pos)) instanceof KiContainmentChamberBlockEntity be
                && player instanceof ServerPlayer sp) {
            // Dos gestos, sin tecla propia: clic derecho = puerta (abre, cierra, o libera a quien
            // esté dentro); agachado = pantalla. El rayo de quien está DENTRO sí da en este bloque
            // (VoxelShape.clip devuelve impacto cuando el origen ya está en la forma), y como el
            // ocupante no puede agacharse (está sujeto), su clic siempre es «abrir la puerta».
            if (sp.isShiftKeyDown()) be.openMenuFor(sp);
            else be.toggleDoor();
        }
        return InteractionResult.CONSUME;
    }
}
