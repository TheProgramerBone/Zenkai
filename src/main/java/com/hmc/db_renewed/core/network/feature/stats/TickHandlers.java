package com.hmc.db_renewed.core.network.feature.stats;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.config.StatsConfig;
import com.hmc.db_renewed.content.effect.ModEffects;
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

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        PlayerStatsAttachment att = p.getData(DataAttachments.PLAYER_STATS.get());

        if (!p.level().isClientSide()) {
            if (att.isImmortal()) {
                p.addEffect(new MobEffectInstance(
                        ModEffects.IMMORTALITY,
                        MobEffectInstance.INFINITE_DURATION,0,true,false,false));
            } else {
                p.removeEffect(ModEffects.IMMORTALITY);
            }
        }
        if (att.isRaceChosen()) return;



        if (!p.level().isClientSide()) {
            if (att.getBody() > 0 && !p.isDeadOrDying()) {
                float max = p.getMaxHealth();
                if (p.getHealth() < max) {
                    p.setHealth(max);
                }
            }
        }

        // ================================
        //  HABILIDAD DE VOLAR (server)
        // ================================
        if (!p.isCreative() && !p.isSpectator()) {
            var ab = p.getAbilities();
            boolean shouldFly = att.isFlyEnabled();
            if (ab.mayfly != shouldFly) {
                ab.mayfly = shouldFly;
                if (!shouldFly) ab.flying = false;
                p.onUpdateAbilities(); // sync abilities al cliente
            }

            float baseFly = 0.02f;
            float flyMult = (float)Math.min(2.0, att.getFlyMultiplier()); // 1.0..2.0
            ab.setFlyingSpeed(baseFly * flyMult);
        }

        // ================================
        //  SOLO LÓGICA DE SERVIDOR
        // ================================
        if (p.level().isClientSide()) {
            return;
        }

        // ----------------------------
        // Carga de KI (mantener tecla)
        // ----------------------------
        if (att.isChargingKi()) {
            double perTick = att.getRegenEnergyPerTick(); // lo puedes ajustar luego a %/s si quieres
            double bonusMul = 3.0; // o desde config
            double gain = perTick * bonusMul;
            att.addKi(gain); // clamp interno
        }

        // ----------------------------
        // REGENERACIÓN CADA SEGUNDO
        // ----------------------------
        // 1 "segundo" de juego ~ 20 ticks
        if (p.tickCount % 20 == 0) {
            var food = p.getFoodData();

            // ¿Permitimos regenerar este segundo?
            boolean canRegen = true;

            if (!p.isCreative()) {
                // En survival: solo regenera si tiene algo de comida
                canRegen = food.getFoodLevel() > 0;
            }

            if (canRegen) {
                boolean didBody    = false;
                boolean didStamina = false;
                // (Ki no cuenta para hambre, así que no necesitamos bandera para él)

                // --- BODY (vida del mod) ---
                int bodyCur = att.getBody();
                int bodyMax = att.getBodyMax();
                if (bodyCur > 0 && bodyCur < bodyMax) {
                    double pct = StatsConfig.baseRegenBody() / 100.0;
                    int regen = (int) Math.round(bodyMax * pct);
                    if (regen <= 0) regen = 1;
                    att.addBody(regen);
                    didBody = true;
                }

                // --- STAMINA ---
                int stCur = att.getStamina();
                int stMax = att.getStaminaMax();
                if (stCur < stMax) {
                    double pct = StatsConfig.baseRegenStamina() / 100.0;
                    int regen = (int) Math.round(stMax * pct);
                    if (regen <= 0) regen = 1;
                    att.addStamina(regen);
                    didStamina = true;
                }

                // --- ENERGY / KI (NO consume comida) ---
                int kiCur = att.getEnergy();
                int kiMax = att.getEnergyMax();
                if (kiCur < kiMax) {
                    double pct = StatsConfig.baseRegenEnergy() / 100.0;
                    int regen = (int) Math.round(kiMax * pct);
                    if (regen <= 0) regen = 1;
                    att.addEnergy(regen);
                }

                // --- COSTE DE HAMBRE (solo survival y según qué regeneró) ---
                if (!p.isCreative()) {
                    // Body gasta MÁS comida
                    if (didBody) {
                        food.addExhaustion(2.4F);
                    }
                    // Stamina gasta POCA comida
                    if (didStamina) {
                        food.addExhaustion(0.6F);
                    }
                }
            }
        }

        // ----------------------------
        // Movimiento: 1.0 + Speed/100 * scaling
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
                    moveMult - 1.0, // multiplicador sobre base
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        }

        // Sync solo en servidor
        PlayerLifeCycle.syncIfServer(p);
    }
}