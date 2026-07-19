package com.hmc.zenkai.core.network;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CombatZenkaiHooks;
import com.hmc.zenkai.content.effect.ModEffects;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.mastery.MasteryEffects;
import com.hmc.zenkai.core.network.feature.forms.FormDefinition;
import com.hmc.zenkai.core.network.feature.forms.FormIds;
import com.hmc.zenkai.core.network.feature.forms.FormRegistry;
import com.hmc.zenkai.core.network.feature.player.OtherworldManager;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class TickHandlers {

    private static final ResourceLocation MOVE_MOD_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "speed_mult");

    private static final ResourceLocation TRANSFORM_LOCK_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "transform_lock");

    private static final ResourceLocation DOWNED_LOCK_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "downed_lock");

    /**
     * LOCK real (servidor):
     * - Corta input (xxa/zza/jump)
     * - Quita sprint
     * - Ancla X/Z al tick anterior (xo/zo)
     * - Corta delta horizontal
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

    /**
     * LOCK de derribado (servidor): mismo anclaje que el de transformación pero con su propio id,
     * para inmovilizar al jugador mientras está acostado. El daño SÍ le llega (no es invulnerable).
     */
    private static void applyDownedLockServer(Player p, boolean lock) {
        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);

        if (!lock) {
            if (moveAttr != null && moveAttr.getModifier(DOWNED_LOCK_ID) != null) {
                moveAttr.removeModifier(DOWNED_LOCK_ID);
            }
            return;
        }

        if (moveAttr != null && moveAttr.getModifier(DOWNED_LOCK_ID) == null) {
            moveAttr.addTransientModifier(new AttributeModifier(
                    DOWNED_LOCK_ID,
                    -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }

        p.setSprinting(false);
        p.xxa = 0.0F;
        p.zza = 0.0F;
        p.setJumping(false);
        p.setPos(p.xo, p.getY(), p.zo);
        var v = p.getDeltaMovement();
        p.setDeltaMovement(0.0, v.y, 0.0);
        p.hurtMarked = true;
    }

    /** Sale del estado derribado: limpia flags, libera el lock y restaura la pose. */
    private static void clearDowned(Player p, PlayerStatsAttachment att) {
        att.flags().setDowned(false);
        att.flags().setDownedUntil(0L);
        applyDownedLockServer(p, false);
        p.setSwimming(false);
        p.setPose(Pose.STANDING); // updatePlayerPose recalculará la correcta al siguiente tick
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();

        // ================================
        // SOLO SERVIDOR
        // ================================
        if (p.level().isClientSide()) return;

        PlayerStatsAttachment att = p.getData(DataAttachments.PLAYER_STATS.get());
        var stats  = p.getData(DataAttachments.PLAYER_STATS.get());
        var form   = p.getData(DataAttachments.PLAYER_FORM.get());
        var visual = p.getData(DataAttachments.PLAYER_VISUAL.get());

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

        // Marca Majin: persistente como la inmortalidad. Si el flag está puesto, el efecto
        // se aplica aunque lo quiten con leche o /effect clear; solo la muerte lo borra.
        if (visual.isMajinControlled() && !p.hasEffect(ModEffects.MAJIN)) {
            p.addEffect(new MobEffectInstance(
                    ModEffects.MAJIN,
                    MobEffectInstance.INFINITE_DURATION, 0, true, false, false
            ));
        }

        // ================================
        // Gate: si no eligió raza, corta features
        // ================================
        if (!att.isRaceChosen()) {
            att.setChargingKi(false);
            stats.setChargingKi(false);
            form.resetAll();

            AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
            // Sin raza no hay vuelo. Sin esto, tras /zenkai reset full el jugador conservaba
            // mayfly y la velocidad de vuelo vieja (este gate retornaba antes de la sección
            // "Volar (server)" que gestiona las abilities).
            if (!p.isCreative() && !p.isSpectator()) {
                var ab = p.getAbilities();
                if (ab.mayfly || ab.flying) {
                    ab.mayfly = false;
                    ab.flying = false;
                    ab.setFlyingSpeed(0.05F); // default vanilla
                    p.onUpdateAbilities();
                }
            }
            if (moveAttr != null) {
                moveAttr.removeModifier(MOVE_MOD_ID);
                moveAttr.removeModifier(TRANSFORM_LOCK_ID);
            }
            return;
        }

        // ================================
        // DERRIBADO (server)
        // ================================
        // Acostado e inmóvil durante 5 s. Si lo curan (senzu propio/aliado -> body>0) se levanta.
        // Si nadie lo cura y expira el tiempo, muere de verdad: LivingDeathEvent -> OtherworldHandler
        // lo manda al otro mundo (o muerte real si enableOtherworld está desactivado).
        if (att.flags().isDowned()) {
            applyDownedLockServer(p, true);
            // Pose de gateo/nado: cuerpo horizontal (como al meterse en un hueco de 1 bloque).
            // Se sincroniza a los demás jugadores vía DATA_POSE; el propio jugador la fuerza en
            // el cliente (ClientZenkaiPalTick), porque su pose la recalcula LocalPlayer cada tick.
            p.setPose(Pose.SWIMMING);
            p.setSwimming(true);

            if (att.getBody() > 0) {
                // Se levanta con el body que la curación haya dejado: la senzu deja 100%,
                // el revive de aliado (mano vacía) ya fija su 20% en CombatZenkaiHooks.
                clearDowned(p, att);
                PlayerLifeCycle.syncIfServer(p);
            } else if (p.level().getGameTime() >= att.flags().getDownedUntil()) {
                clearDowned(p, att);
                if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                    // health=0 ANTES de die(): sin esto la muerte "no cuaja" (el cliente
                    // nunca ve vida 0) y el respawn sincroniza en multiplayer ->
                    // jugador vivo con body 0 y sin viaje al otro mundo.
                    sp.setHealth(0.0F);
                    sp.die(sp.damageSources().generic());
                }
            }
            return; // mientras está derribado no corre volar/regen/transformación/movimiento
        }

        // ================================
        // Curar vida vanilla si body > 0
        // ================================
        // Mantener la vida vanilla llena mientras el BODY sea la vida real.
        // ================================
        if (att.getBody() <= 0 && !p.isDeadOrDying()
                && p instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (att.isInOtherworld()) {
                OtherworldManager.keepInOtherworld(sp);
            } else {
                att.flags().setDowned(true);
                att.flags().setDownedUntil(p.level().getGameTime() + CombatZenkaiHooks.DOWNED_TICKS);
                PlayerLifeCycle.syncIfServer(p);
            }
            return;
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
            if (!ab.flying) att.flags().setFlyBoosting(false);
        }
        {
            boolean flyingNow = att.isFlyEnabled() && p.getAbilities().flying && !p.isSpectator();
            if (!flyingNow) att.flags().setFlyBoosting(false);
            boolean prone = att.flags().isFlyBoosting();
            if (prone != att.flags().isBoostSizeApplied()) {
                att.flags().setBoostSizeApplied(prone);
                p.refreshDimensions();
            }
        }

        // ================================
        // Transformación (SERVER)
        // ================================

        // 1) Tick de lógica de forms (hold / completar / etc.)
        boolean formDirty = form.serverTick(p, stats, visual);

        // 1.1) Si cambió algo importante (completó / canceló), sync inmediato
        if (formDirty) {
            PlayerLifeCycle.syncFormIfServer(p);
        }

        // ================================
        // KI DRAIN (SERVER) - NUEVO
        // ================================
        // Drena Ki si la forma actual tiene costo por tick.
        // Si Ki llega a 0 -> vuelve a BASE automáticamente.
        {
            ResourceLocation currentFormId = form.getFormId();
            if (currentFormId != null && !FormIds.BASE.equals(currentFormId)) {
                FormDefinition def = FormRegistry.get(currentFormId);

                // (por seguridad) solo si la raza actual está permitida
                if (def != null && def.allowedRaces().contains(att.getRace())) {
                    double drain = def.kiDrainPerTick()* MasteryEffects.formDrainFactor(p);
                    if (drain > 0.0) {
                        int before = att.getKiCurrent();
                        att.addKi(-drain);

                        // Si se quedó sin Ki -> forzar base + sync
                        if (before > 0 && att.getKiCurrent() <= 0) {
                            form.forceBase();
                            PlayerLifeCycle.syncFormIfServer(p);
                        }
                    }
                }
            }
        }

        // 2) Si está en proceso (cargando transformación), lock + NO speed_mult
        if (form.isTransforming()) {
            applyTransformLockServer(p, true);

            AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (moveAttr != null) moveAttr.removeModifier(MOVE_MOD_ID);

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