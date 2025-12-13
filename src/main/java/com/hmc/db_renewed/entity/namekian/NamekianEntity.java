package com.hmc.db_renewed.entity.namekian;

import com.hmc.db_renewed.entity.CommonAnimations;
import com.hmc.db_renewed.entity.ModVillagerTrades;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class NamekianEntity extends AbstractVillager implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public NamekianEntity(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PanicGoal(this, 1D));
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && !this.isTrading() && !this.isBaby()) {
            this.setTradingPlayer(player);
            this.openTradingScreen(player, this.getDisplayName(), 1);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected void rewardTradeXp(MerchantOffer merchantOffer) {

    }

    @Override
    protected void updateTrades() {
        VillagerTrades.ItemListing[] listings = ModVillagerTrades.NAMEKIAN_TRADES;
        MerchantOffers offers = this.getOffers();
        this.addOffersFromItemListings(offers, listings, 5);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractVillager.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel serverLevel, @NotNull AgeableMob ageableMob) {
        return null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(CommonAnimations.genericWalkIdleController(this));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return super.hurt(source, amount);
    }
}
