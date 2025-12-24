package com.hmc.db_renewed.core.network;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.config.StatsConfig;
import com.hmc.db_renewed.content.effect.ModEffects;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.player.PlayerLifeCycle;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class TickHandlers {

    private static final ResourceLocation MOVE_MOD_ID =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "speed_mult");

    private static final ResourceLocation TRANSFORM_LOCK_ID =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "transform_lock");

    /**
     * LOCK real (servidor):
     * - Corta input (xxa/zza/jump)
     * - Quita sprint
     * - Ancla X/Z al tick anterior (xo/zo)
     * - Corta delta horizontal
     *
     * En cliente NO se debe llamar (evita zoom/jitter). El cliente bloquea input en ClientPalTick.
     */
    private static void applyTransformLockServer(Player p, boolean lock) {
        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);

        if (!lock) {
            if (moveAttr != null && moveAttr.getModifier(TRANSFORM_LOCK_ID) != null) {
                moveAttr.removeModifier(TRANSFORM_LOCK_ID);
            }
            return;
        }

        if (moveAttr != null && moveAttr.getModifier(TRANSFORM_LOCK_ID) == null) {
            moveAttr.addTransientModifier(new AttributeModifier(
                    TRANSFORM_LOCK_ID,
                    -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }

        p.setSprinting(false);

        p.xxa = 0.0F;
        p.zza = 0.0F;
        p.setJumping(false);

        // ancla horizontal (server-only)
        p.setPos(p.xo, p.getY(), p.zo);

        // corta inercia horizontal
        var v = p.getDeltaMovement();
        p.setDeltaMovement(0.0, v.y, 0.0);

        p.hurtMarked = true;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        PlayerStatsAttachment att = p.getData(DataAttachments.PLAYER_STATS.get());

        // ================================
        // SOLO SERVIDOR desde aquí
        // ================================
        if (p.level().isClientSide()) {
            return;
        }

        // ================================
        // Inmortalidad (server)
        // ================================
        if (att.isImmortal()) {
            p.addEffect(new MobEffectInstance(
                    ModEffects.IMMORTALITY,
                    MobEffectInstance.INFINITE_DURATION, 0, true, false, false
            ));
        } else {
            p.removeEffect(ModEffects.IMMORTALITY);
        }

        // ================================
        // Gate: si no eligió raza, corta features
        // ================================
        if (!att.isRaceChosen()) {
            att.setChargingKi(false);
            att.setTransforming(false);

            // Limpia modifiers por si quedaron de antes
            AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (moveAttr != null) {
                moveAttr.removeModifier(MOVE_MOD_ID);
                moveAttr.removeModifier(TRANSFORM_LOCK_ID);
            }
            return;
        }

        // ================================
        // Curar vida vanilla si body > 0
        // ================================
        if (att.getBody() > 0 && !p.isDeadOrDying()) {
            float max = p.getMaxHealth();
            if (p.getHealth() < max) {
                p.setHealth(max);
            }
        }

        // ================================
        // Volar (server)
        // ================================
        if (!p.isCreative() && !p.isSpectator()) {
            var ab = p.getAbilities();
            boolean shouldFly = att.isFlyEnabled();

            if (ab.mayfly != shouldFly) {
                ab.mayfly = shouldFly;
                if (!shouldFly) ab.flying = false;
                p.onUpdateAbilities();
            }

            float baseFly = 0.02f;
            float flyMult = (float) Math.min(2.0, att.getFlyMultiplier());
            ab.setFlyingSpeed(baseFly * flyMult);
        }

        // ================================
        // Transformación: lock + NO tocar speed_mult
        // (evita pulso de FOV/zoom)
        // ================================
        if (att.isTransforming()) {
            applyTransformLockServer(p, true);

            AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (moveAttr != null) {
                moveAttr.removeModifier(MOVE_MOD_ID);
            }

            PlayerLifeCycle.syncIfServer(p);
            return;
        } else {
            applyTransformLockServer(p, false);
        }

        // ----------------------------
        // Carga de KI (mantener tecla)
        // ----------------------------
        if (att.isChargingKi()) {
            double perTick = att.getRegenEnergyPerTick();
            double bonusMul = 3.0; // o config
            double gain = perTick * bonusMul;
            att.addKi(gain);
        }

        // ----------------------------
        // REGENERACIÓN CADA SEGUNDO
        // ----------------------------
        if (p.tickCount % 20 == 0) {
            var food = p.getFoodData();

            boolean canRegen = true;
            if (!p.isCreative()) {
                canRegen = food.getFoodLevel() > 0;
            }

            if (canRegen) {
                boolean didBody = false;
                boolean didStamina = false;

                int bodyCur = att.getBody();
                int bodyMax = att.getBodyMax();
                if (bodyCur > 0 && bodyCur < bodyMax) {
                    double pct = StatsConfig.baseRegenBody() / 100.0;
                    int regen = (int) Math.round(bodyMax * pct);
                    if (regen <= 0) regen = 1;
                    att.addBody(regen);
                    didBody = true;
                }

                int stCur = att.getStamina();
                int stMax = att.getStaminaMax();
                if (stCur < stMax) {
                    double pct = StatsConfig.baseRegenStamina() / 100.0;
                    int regen = (int) Math.round(stMax * pct);
                    if (regen <= 0) regen = 1;
                    att.addStamina(regen);
                    didStamina = true;
                }

                int kiCur = att.getEnergy();
                int kiMax = att.getEnergyMax();
                if (kiCur < kiMax) {
                    double pct = StatsConfig.baseRegenEnergy() / 100.0;
                    int regen = (int) Math.round(kiMax * pct);
                    if (regen <= 0) regen = 1;
                    att.addEnergy(regen);
                }

                if (!p.isCreative()) {
                    if (didBody) food.addExhaustion(2.4F);
                    if (didStamina) food.addExhaustion(0.6F);
                }
            }
        }

        // ----------------------------
        // Movimiento por stats (solo cuando NO transforma)
        // ----------------------------
        double speedStat = att.computeSpeedFinal();
        double moveMult = Math.min(
                1.0 + (speedStat / 100.0) * StatsConfig.movementScaling(),
                StatsConfig.speedMultiplierCap()
        );

        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveAttr != null) {
            moveAttr.removeModifier(MOVE_MOD_ID);
            moveAttr.addTransientModifier(new AttributeModifier(
                    MOVE_MOD_ID,
                    moveMult - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        }

        PlayerLifeCycle.syncIfServer(p);
    }
}
