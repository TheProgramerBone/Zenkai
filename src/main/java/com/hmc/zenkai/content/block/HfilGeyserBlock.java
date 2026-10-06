package com.hmc.zenkai.content.block;

import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Géiser del HFIL ("Tatsumaki Jigoku" de Beppu, un géiser real que entra en erupción cada
 * ~30 minutos). Cada IDLE_MIN-IDLE_MAX ticks suelta un chorro de vapor que LANZA hacia arriba
 * cualquier entidad que esté encima: decoración y movilidad a la vez (sube a quien aún no vuela).
 *
 * POR QUÉ ticks programados y una propiedad STAGE en vez de un BlockEntity: el ciclo entero
 * cabe en "reposo → 15 pasos de erupción → reposo", y la propiedad hace de contador. Sin
 * BlockEntity no hay registro de tipo ni datos que guardar. La propiedad cambia solo durante
 * la erupción (~2 s cada 5-10 s), un coste de red despreciable para un bloque que es raro.
 *
 * El ciclo arranca en {@link #onPlace} (colocado por un jugador) o lo programa la feature de
 * worldgen (HfilGeyserFeature, donde onPlace no corre). {@link #randomTick} es la red de
 * seguridad: si por lo que sea no hay tick programado (chunk antiguo, comando /setblock con
 * flags raros), lo vuelve a programar.
 */
public class HfilGeyserBlock extends Block {

    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 15);

    /**
     * De qué suelo está hecho (texturas por variante, ver ModBlockStateProvider y
     * tools/gen_hfil_springs.py). Regla del usuario (2026-10-02): una estructura usa SIEMPRE los
     * bloques del bioma donde aparece, aunque sea el mismo concepto en los tres — un géiser de
     * otro color sobre el suelo se lee como "esto no debería estar aquí". La feature elige la
     * variante leyendo el bloque de suelo que sustituye ({@link #groundFor}).
     */
    public enum Ground implements StringRepresentable {
        SCORCHED("scorched"), PALE("pale"), CINDER("cinder");

        private final String name;

        Ground(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Ground> GROUND = EnumProperty.create("ground", Ground.class);

    /** Variante que pega con un bloque de suelo dado; la piedra calcinada (el relleno del
     *  Otherworld) es el valor por defecto para cualquier otro bloque. */
    public static Ground groundFor(BlockState ground) {
        if (ground.is(ModBlocks.HFIL_PALE_STONE.get())) return Ground.PALE;
        if (ground.is(ModBlocks.HFIL_CINDER_SAND.get()) || ground.is(ModBlocks.HFIL_CINDER_SANDSTONE.get())) {
            return Ground.CINDER;
        }
        return Ground.SCORCHED;
    }

    private static final int IDLE_MIN = 100;
    private static final int IDLE_MAX = 200;
    /** Ticks entre pasos de erupción: 15 pasos × 3 = ~2.25 s de chorro. */
    private static final int ERUPT_STEP_TICKS = 3;
    /** Alto de la columna que empuja, en bloques sobre el géiser. */
    private static final int COLUMN_HEIGHT = 8;
    /** Velocidad vertical impuesta (bloques/tick): ~6-7 bloques de subida. */
    private static final double LAUNCH_SPEED = 1.1;

    public HfilGeyserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0).setValue(GROUND, Ground.SCORCHED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, GROUND);
    }

    /** Colocado a mano por un jugador: toma la variante del bloque sobre el que se apoya. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(GROUND,
                groundFor(ctx.getLevel().getBlockState(ctx.getClickedPos().below())));
    }

    /** Programa el siguiente arranque. Público para que la feature de worldgen lo use. */
    public static void scheduleIdle(LevelAccessor level, BlockPos pos, Block block, RandomSource random) {
        level.scheduleTick(pos, block, IDLE_MIN + random.nextInt(IDLE_MAX - IDLE_MIN + 1));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            scheduleIdle(level, pos, this, level.getRandom());
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            scheduleIdle(level, pos, this, random);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);
        if (stage == 0) {
            // Arranque: siseo de vapor, el aviso para quien esté cerca.
            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS,
                    1.4F, 0.5F + random.nextFloat() * 0.2F);
            level.setBlock(pos, state.setValue(STAGE, 1), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, ERUPT_STEP_TICKS);
            return;
        }
        erupt(level, pos, random);
        if (stage >= 15) {
            level.setBlock(pos, state.setValue(STAGE, 0), Block.UPDATE_CLIENTS);
            scheduleIdle(level, pos, this, random);
        } else {
            level.setBlock(pos, state.setValue(STAGE, stage + 1), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, ERUPT_STEP_TICKS);
        }
    }

    private static void erupt(ServerLevel level, BlockPos pos, RandomSource random) {
        AABB column = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(),
                pos.getX() + 1, pos.getY() + 1 + COLUMN_HEIGHT, pos.getZ() + 1);
        for (Entity e : level.getEntities((Entity) null, column, ent -> !ent.isSpectator())) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x, Math.max(v.y, LAUNCH_SPEED), v.z);
            e.resetFallDistance();
            // Sin esto el servidor cambia la velocidad pero el cliente del jugador no se entera
            // (el movimiento del jugador es autoridad del cliente): hurtMarked fuerza el envío.
            e.hurtMarked = true;
        }
        // Chorro: count = 0 hace que (dx, dy, dz) sea la VELOCIDAD de la partícula en vez de un
        // área de dispersión — así sube recto en columna en vez de abrirse en una nube.
        for (int i = 0; i < 4; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            level.sendParticles(ParticleTypes.CLOUD, x, pos.getY() + 1.05, z, 0,
                    (random.nextDouble() - 0.5) * 0.1, 1.0, (random.nextDouble() - 0.5) * 0.1,
                    0.55 + random.nextDouble() * 0.25);
        }
    }

    /** En reposo, una voluta de vapor de vez en cuando: el géiser se adivina antes de que salte. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(STAGE) != 0 || random.nextInt(3) != 0) return;
        if (!level.getBlockState(pos.above()).isAir()) return;
        level.addParticle(ParticleTypes.CLOUD,
                pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.03, 0.0);
    }
}
