package com.hmc.zenkai.content.entity.otherworld;

import com.hmc.zenkai.content.entity.ZenkaiDefaultNPC;
import com.hmc.zenkai.core.network.feature.player.OtherworldManager;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class YemmaEntity extends ZenkaiDefaultNPC {

    public YemmaEntity(EntityType<? extends ZenkaiDefaultNPC> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    /** Hacia dónde mira Emma en reposo (sin jugador cerca). */
    private static final float REST_YAW = 180.0f;
    /** Rango en el que "mira al jugador"; fuera de esto, vuelve a REST_YAW. */
    private static final float LOOK_RANGE = 5.0f;

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, LOOK_RANGE));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
            PlayerStatsAttachment stats = PlayerStatsAttachment.get(sp);

            if (!stats.isInOtherworld()) {
                sp.displayClientMessage(Component.translatable("messages.zenkai.yemma_not_dead")
                        .withStyle(ChatFormatting.GRAY), false);
            } else {
                long remaining = OtherworldManager.REVIVE_DELAY_TICKS
                        - (this.level().getGameTime() - stats.getOtherworldSince());

                if (remaining > 0) {
                    sp.displayClientMessage(Component.translatable("messages.zenkai.yemma_wait",
                            formatTime(remaining)).withStyle(ChatFormatting.YELLOW), false);
                } else {
                    sp.displayClientMessage(Component.translatable("messages.zenkai.yemma_revive")
                            .withStyle(ChatFormatting.GREEN), false);
                    OtherworldManager.revive(sp);
                }
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** Ticks restantes -> "M:SS". */
    private static String formatTime(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / 20L;
        return String.format("%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

        public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0f)
                .add(Attributes.MOVEMENT_SPEED, 0.0f);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return;

        Player nearest = this.level().getNearestPlayer(this, LOOK_RANGE);
        if (nearest == null) {
            this.setYRot(REST_YAW);
            this.yRotO = REST_YAW;
            this.setYBodyRot(REST_YAW);
            this.setYHeadRot(REST_YAW);
            this.yHeadRotO = REST_YAW;
            this.getNavigation().stop();
        }
    }
}