package com.hmc.zenkai.content.entity.otherworld;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.EnumSet;

/**
 * Ángel del capullo de Freezer (Dragon Ball Super, "La resurrección de F": Freezer encerrado en un
 * capullo en el infierno, rodeado de angelitos que le cantan sin parar). PACÍFICO: no tiene
 * targetSelector, así que nunca busca pelea ni responde. Da vueltas volando alrededor de su
 * punto (el capullo, o donde se le sacó del huevo) tocando la trompeta en bucle.
 *
 * Usa la IA universal de ZenkaiDefaultMob (registerCombatGoals) como cualquier otro NPC: su ficha
 * (angel.json) trae can_fly y melee:false, así que de ahí solo le sale el vuelo estilo Vex y el
 * deambular de reserva. Lo propio es {@link CircleHomeGoal} (prioridad 5, por delante del
 * deambular) y la melodía de tick().
 *
 * Tamaño de un Vex: SCALE 0.45 sobre el rig de jugador (geo/angel.geo.json, el de los ogros más
 * una trompeta dorada en el brazo derecho que toma el color del aro de la propia skin).
 *
 * MELODÍA: Minecraft 1.21.1 no tiene instrumento de trompeta en el bloque musical (comprobado en
 * el jar: banjo, bass, bell, bit, chime, cow_bell, didgeridoo, flute, guitar, harp, iron_xylophone,
 * pling, xylophone y percusiones), así que suena la flauta con una campanita al empezar cada frase.
 * El paso de la melodía sale del gameTime y no de un contador propio: los ángeles de un mismo capullo
 * van a tempo sin coordinarse. La "voz" 1 toca una octava por debajo, para que dos ángeles no
 * suenen como uno.
 */
public class AngelEntity extends ZenkaiDefaultMob {

    private static final String HOME_TAG = "OrbitCenter";
    private static final String PHASE_TAG = "OrbitPhase";
    private static final String VOICE_TAG = "Voice";

    /** Radio y altura de la vuelta sobre el punto central (bloques). */
    public static final double ORBIT_RADIUS = 4.5;
    public static final double ORBIT_HEIGHT = 3.0;
    /** Radianes por tick: una vuelta cada ~12 s. */
    private static final double ORBIT_SPEED = Math.PI * 2 / 240.0;

    /** Ticks por paso de la melodía (4 notas por segundo). */
    private static final int NOTE_TICKS = 5;
    /** Silencio entre repeticiones, en pasos. */
    private static final int REST_STEPS = 12;
    /**
     * Semitonos del bloque musical (0 = Fa#3 .. 24 = Fa#5); -1 = silencio. Dos frases de 16 pasos
     * en Sol mayor, tipo fanfarria alegre.
     */
    private static final int[] MELODY = {
            13, 17, 20, 20, -1, 17, 20, 22, 20, -1, 17, 15, 13, -1, -1, -1,
            15, 17, 18, 20, -1, 18, 17, 15, 17, -1, 13, 15, 13, -1, -1, -1
    };

    private static final RawAnimation PLAY = RawAnimation.begin().thenLoop("angel.play");

    @Nullable private BlockPos orbitCenter;
    private double orbitPhase;
    private int voice;

    public AngelEntity(EntityType<? extends ZenkaiDefaultMob> type, Level level) {
        super(type, level);
        this.orbitPhase = this.random.nextDouble() * Math.PI * 2;
    }

    /** Fija el punto alrededor del que vuela, su posición en la vuelta y la voz (0 o 1). */
    public void orbit(BlockPos center, double phase, int voice) {
        this.orbitCenter = center;
        this.orbitPhase = phase;
        this.voice = voice;
    }

    @Override
    protected void registerGoals() {
        registerCombatGoals(0.6D);
        this.goalSelector.addGoal(5, new CircleHomeGoal(this));
        // Sin targetSelector: pacífico.
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        // El aro es el ítem del halo en la ranura de casco (lo pinta AngelHaloLayer). Se pone aquí
        // y no en el constructor para que también lo reciban los ángeles guardados antes de este
        // cambio; drop chance 0 para que no se pueda farmear el halo matándolos.
        if (getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.HALO.get()));
            setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
        // Sacado de un huevo: da vueltas donde apareció (un poco por debajo, para que la vuelta
        // quede a la altura a la que se le vio nacer).
        if (orbitCenter == null) orbitCenter = blockPosition().below((int) ORBIT_HEIGHT);
        playMelody((ServerLevel) level());
    }

    private void playMelody(ServerLevel level) {
        long t = level.getGameTime();
        if (t % NOTE_TICKS != 0) return;
        int step = (int) ((t / NOTE_TICKS) % (MELODY.length + REST_STEPS));
        if (step >= MELODY.length || MELODY[step] < 0) return;

        int note = MELODY[step] - (voice == 1 ? 12 : 0);
        float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
        // Desde la campana de la trompeta: un poco por delante de la cara.
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        double x = getX() + look.x * 0.35, y = getEyeY(), z = getZ() + look.z * 0.35;

        level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 0.6F, pitch);
        if (step % 16 == 0) {
            level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 0.35F, pitch);
        }
        // NOTE con count 0: el primer componente de velocidad es el color (nota/24), como el bloque.
        level.sendParticles(ParticleTypes.NOTE, x, y + 0.3, z, 0, note / 24.0, 0, 0, 1.0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Sin los controladores de combate de la base: su animación (angel.animation.json) solo
        // trae "angel.play", y un ángel no ataca ni se cubre.
        controllers.add(new AnimationController<>(this, "Play", 5, state -> state.setAndContinue(PLAY)));
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (orbitCenter != null) tag.put(HOME_TAG, NbtUtils.writeBlockPos(orbitCenter));
        tag.putDouble(PHASE_TAG, orbitPhase);
        tag.putInt(VOICE_TAG, voice);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, HOME_TAG).ifPresent(p -> orbitCenter = p);
        if (tag.contains(PHASE_TAG)) orbitPhase = tag.getDouble(PHASE_TAG);
        voice = tag.getInt(VOICE_TAG);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.SCALE, 0.45D);
    }

    /**
     * Vuelta continua alrededor de orbitCenter. El ángulo sale del gameTime más la fase propia
     * (igual que la melodía): dos ángeles con fases opuestas se quedan uno enfrente del otro sin
     * hablarse. Mira hacia donde vuela (VexStyleFlightControl no gira el cuerpo).
     */
    static class CircleHomeGoal extends Goal {
        private final AngelEntity angel;

        CircleHomeGoal(AngelEntity angel) {
            this.angel = angel;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() { return angel.orbitCenter != null; }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            BlockPos c = angel.orbitCenter;
            if (c == null) return;
            double a = angel.level().getGameTime() * ORBIT_SPEED + angel.orbitPhase;
            // Un vaivén suave de altura para que no parezca un tiovivo.
            double bob = Math.sin(a * 3) * 0.4;
            double tx = c.getX() + 0.5 + Math.cos(a) * ORBIT_RADIUS;
            double ty = c.getY() + ORBIT_HEIGHT + bob;
            double tz = c.getZ() + 0.5 + Math.sin(a) * ORBIT_RADIUS;
            // 0.6 y no 1.0: VexStyleFlightControl acelera hacia un punto que se mueve, y con más
            // empuje se pasa de largo y la vuelta se vuelve un zigzag.
            angel.getMoveControl().setWantedPosition(tx, ty, tz, 0.6);

            // Tangente a la vuelta: derivada de (cos a, sin a) = (-sin a, cos a). En Minecraft la
            // dirección de un yaw θ es (-sin θ, cos θ), así que el yaw es el propio ángulo.
            float yaw = Mth.wrapDegrees((float) (a * Mth.RAD_TO_DEG));
            angel.setYRot(yaw);
            angel.yBodyRot = yaw;
            angel.yHeadRot = yaw;
        }
    }
}
