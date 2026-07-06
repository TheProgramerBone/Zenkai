package com.hmc.zenkai.content.entity.misc;

import com.hmc.zenkai.content.entity.ZenkaiCommonAnimations;
import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import com.hmc.zenkai.content.sound.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.Set;

/**
 * Isaac: entidad GeckoLib mínima. NO tiene Model ni Renderer propios.
 * Render/modelo los aporta GenericGeoRenderer por convención de nombre "isaac".
 *
 * Click derecho con un ítem de DANCE_TRIGGER_ITEMS => alterna la animación de baile.
 * Mientras baila NO se mueve y la cabeza se congela para respetar la animación.
 */
public class IsaacEntity extends ZenkaiDefaultMob {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Ítems que activan el baile al hacer click derecho. Edita esta lista a gusto. */
    private static final Set<Item> DANCE_TRIGGER_ITEMS = Set.of(
            Items.MUSIC_DISC_13,
            Items.NOTE_BLOCK,
            Items.DIAMOND
    );

    private static final Set<Rarity> DANCE_TRIGGER_RARITIES = EnumSet.of(Rarity.RARE,Rarity.EPIC,Rarity.UNCOMMON);

    private boolean triggersDance(ItemStack stack) {
        return !stack.isEmpty()
                && (DANCE_TRIGGER_ITEMS.contains(stack.getItem())
                || DANCE_TRIGGER_RARITIES.contains(stack.getRarity()));
    }

    private static final RawAnimation DANCE = RawAnimation.begin().thenPlay("isaac.dance");

    private static final EntityDataAccessor<Boolean> DANCING =
            SynchedEntityData.defineId(IsaacEntity.class, EntityDataSerializers.BOOLEAN);

    public IsaacEntity(EntityType<? extends ZenkaiDefaultMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DANCING, false);
    }

    public boolean isDancing()            { return this.entityData.get(DANCING); }
    public void setDancing(boolean value) { this.entityData.set(DANCING, value); }

    @Override
    protected void registerGoals() {
        // El paseo NO corre mientras baila.
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 1.0) {
            @Override public boolean canUse()           { return !isDancing() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isDancing() && super.canContinueToUse(); }
        });

        // Modificados: Ya no se ejecutarán si el mob está bailando.
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override public boolean canUse()           { return !isDancing() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isDancing() && super.canContinueToUse(); }
        });

        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this) {
            @Override public boolean canUse()           { return !isDancing() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isDancing() && super.canContinueToUse(); }
        });
    }

    /** Red de seguridad: si está bailando, corta navegación, anula el movimiento horizontal y frena la rotación de cabeza por IA. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (isDancing()) {
            if (!this.level().isClientSide) {
                this.getNavigation().stop();
                this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D); // conserva gravedad
            }
            // Sincroniza la rotación del cuerpo con la cabeza para que no se tuerza de forma extraña
            this.setYRot(this.yRotO);
            this.setYHeadRot(this.yRotO);
        }
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (triggersDance(held)) {
            if (!this.level().isClientSide) {
                boolean nowDancing = !isDancing();
                setDancing(nowDancing);          // alterna baile on/off
                if (nowDancing) {
                    this.getNavigation().stop(); // frena de inmediato al empezar a bailar
                    Player nearest = this.level().getNearestPlayer(this, 16.0D);
                    if (nearest != null) {
                        nearest.playNotifySound(ModSounds.SPECIALIST.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (this.isDancing())
                return state.setAndContinue(DANCE);
            if (state.isMoving())
                return state.setAndContinue(ZenkaiCommonAnimations.WALK);
            return state.setAndContinue(ZenkaiCommonAnimations.IDLE);
        }));
    }

    @Override
    public @NotNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("dancing", isDancing());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("dancing")) setDancing(tag.getBoolean("dancing"));
    }
}