package com.hmc.zenkai.core.network;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CombatZenkaiHooks;
import com.hmc.zenkai.content.effect.ModEffects;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.mastery.MasteryEffects;
import com.hmc.zenkai.core.network.feature.aura.TurboServerState;
import com.hmc.zenkai.core.network.feature.forms.FormDefinition;
import com.hmc.zenkai.core.network.feature.forms.FormIds;
import com.hmc.zenkai.core.network.feature.forms.FormRegistry;
import com.hmc.zenkai.core.network.feature.player.OtherworldManager;
import com.hmc.zenkai.core.network.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.skills.SkillEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TickHandlers {

    private static final ResourceLocation MOVE_MOD_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "speed_mult");

    private static final ResourceLocation TRANSFORM_LOCK_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "transform_lock");

    private static final ResourceLocation DOWNED_LOCK_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "downed_lock");

    /** Ticks seguidos cargando ki, por jugador (transitorio, solo server). */
    private static final Map<UUID, Integer> chargeTicks = new HashMap<>();

    // =====================================================================
    // ORQUESTADOR
    // =====================================================================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;

        PlayerStatsAttachment att    = p.getData(DataAttachments.PLAYER_STATS.get());
        PlayerFormAttachment   form   = p.getData(DataAttachments.PLAYER_FORM.get());
        PlayerVisualAttachment visual = p.getData(DataAttachments.PLAYER_VISUAL.get());

        tickPersistentEffects(p, att, visual);

        if (handleNoRace(p, att, form))     return;
        if (handleDowned(p, att))           return;
        if (handleBodyDepleted(p, att))     return;

        boolean turboOn = p instanceof ServerPlayer sp && TurboServerState.isOn(sp);

        tickFlight(p, att, turboOn);
        tickBoostHitbox(p, att);

        if (tickForms(p, att, form, visual)) return;

        tickKiCharge(p, att);
        tickRegen(p, att);
        tickMovement(p, att, turboOn);

        PlayerLifeCycle.syncIfServer(p);
    }

    // =====================================================================
    // ESCALONES
    // =====================================================================

    /**
     * Fracción del bonus máximo que recibe el jugador según el escalón.
     * El % de poder se aplica aparte (multiplicando), así que 0% siempre da vanilla.
     */
    private static double performanceTier(boolean control, boolean turbo) {
        if (!control) return 0.45;      // suelto: rápido pero maniobrable
        return turbo ? 1.0 : 0.7;       // el turbo exige Control pulsado
    }

    // =====================================================================
    // EFECTOS PERSISTENTES
    // =====================================================================

    private static void tickPersistentEffects(Player p, PlayerStatsAttachment att,
                                              PlayerVisualAttachment visual) {
        if (att.isImmortal()) {
            p.addEffect(new MobEffectInstance(ModEffects.IMMORTALITY,
                    MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
        } else {
            p.removeEffect(ModEffects.IMMORTALITY);
        }

        // Marca Majin: persistente como la inmortalidad. Si el flag está puesto, el efecto
        // se aplica aunque lo quiten con leche o /effect clear; solo la muerte lo borra.
        if (visual.isMajinControlled() && !p.hasEffect(ModEffects.MAJIN)) {
            p.addEffect(new MobEffectInstance(ModEffects.MAJIN,
                    MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
        }
    }

    // =====================================================================
    // CORTES DEL TICK
    // =====================================================================

    /** Sin raza no hay features. @return true si hay que cortar el tick. */
    private static boolean handleNoRace(Player p, PlayerStatsAttachment att, PlayerFormAttachment form) {
        if (att.isRaceChosen()) return false;

        att.setChargingKi(false);
        form.resetAll();

        // Sin esto, tras /zenkai reset full el jugador conservaba mayfly y la velocidad
        // de vuelo vieja (este gate retornaba antes de la sección de vuelo).
        if (!p.isCreative() && !p.isSpectator()) {
            var ab = p.getAbilities();
            if (ab.mayfly || ab.flying) {
                ab.mayfly = false;
                ab.flying = false;
                ab.setFlyingSpeed(0.05F); // default vanilla
                p.onUpdateAbilities();
            }
        }
        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveAttr != null) {
            moveAttr.removeModifier(MOVE_MOD_ID);
            moveAttr.removeModifier(TRANSFORM_LOCK_ID);
        }
        return true;
    }

    /**
     * Derribado: acostado e inmóvil 5 s. Si lo curan (body > 0) se levanta; si expira,
     * muere de verdad y LivingDeathEvent lo manda al otro mundo.
     * @return true si hay que cortar el tick.
     */
    private static boolean handleDowned(Player p, PlayerStatsAttachment att) {
        if (!att.flags().isDowned()) return false;

        applyDownedLockServer(p, true);
        // Pose horizontal, se sincroniza vía DATA_POSE; el propio jugador la fuerza en el
        // cliente (ClientZenkaiPalTick) porque LocalPlayer la recalcula cada tick.
        p.setPose(Pose.SWIMMING);
        p.setSwimming(true);

        if (att.getBody() > 0) {
            // Se levanta con el body que dejó la curación: senzu 100%, revive de aliado 20%.
            clearDowned(p, att);
            PlayerLifeCycle.syncIfServer(p);
        } else if (p.level().getGameTime() >= att.flags().getDownedUntil()) {
            clearDowned(p, att);
            if (p instanceof ServerPlayer sp) {
                // health=0 ANTES de die(): sin esto la muerte "no cuaja" (el cliente nunca ve
                // vida 0) y el respawn sincroniza -> jugador vivo con body 0 y sin viaje.
                sp.setHealth(0.0F);
                sp.die(sp.damageSources().generic());
            }
        }
        return true;
    }

    /** Body a 0: derriba, o lo mantiene en el otro mundo. @return true si hay que cortar. */
    private static boolean handleBodyDepleted(Player p, PlayerStatsAttachment att) {
        if (att.getBody() > 0 || p.isDeadOrDying() || !(p instanceof ServerPlayer sp)) return false;

        if (att.isInOtherworld()) {
            OtherworldManager.keepInOtherworld(sp);
        } else {
            att.flags().setDowned(true);
            att.flags().setDownedUntil(p.level().getGameTime() + CombatZenkaiHooks.DOWNED_TICKS);
            PlayerLifeCycle.syncIfServer(p);
        }
        return true;
    }

    // =====================================================================
    // VUELO
    // =====================================================================

    private static void tickFlight(Player p, PlayerStatsAttachment att, boolean turboOn) {
        if (p.isCreative() || p.isSpectator()) return;

        var ab = p.getAbilities();
        // La habilidad Fly HABILITA el vuelo: sin ella no se vuela aunque el toggle esté activo.
        boolean shouldFly = att.isFlyEnabled() && SkillEffects.canFly(p);
        if (ab.mayfly != shouldFly) {
            ab.mayfly = shouldFly;
            if (!shouldFly) ab.flying = false;
            p.onUpdateAbilities();
        }

        boolean control  = att.flags().isFlyBoosting();   // Ctrl+W en vuelo
        boolean flyTurbo = ab.flying && control && turboOn;

        // Máximo: DEX (flyMultiplier, con su tope) elevado por la habilidad Fly.
        // Calibrado para que DEX 1000 + Fly 10 = x2.0 = ~20 bloques/segundo en turbo.
        double max = Math.min(StatsConfig.flyMultiplierCap(), att.getFlyMultiplier())
                * SkillEffects.flySpeedFactor(p);
        // Se interpola desde 1.0 (vuelo vanilla): con 0% de poder el multiplicador cae a 1.0.
        double mult = 1.0 + (max - 1.0) * performanceTier(control, turboOn) * att.powerFraction();

        float newSpeed = (float) (StatsConfig.flyBaseSpeed() * mult);
        // Player.getFlyingSpeed() DUPLICA la velocidad al esprintar, y Control ES la tecla de
        // sprint: sin compensar, el escalón medio se llevaba un x2 gratis que rompía la
        // proporción entre escalones.
        if (p.isSprinting()) newSpeed /= 2.0F;

        // setFlyingSpeed en servidor NO llega al cliente sin onUpdateAbilities(), pero llamarlo
        // cada tick sería un paquete por tick: solo cuando la velocidad cambia de verdad.
        if (Math.abs(ab.getFlyingSpeed() - newSpeed) > 1.0e-4F) {
            ab.setFlyingSpeed(newSpeed);
            p.onUpdateAbilities();
        }

        // Coste del vuelo turbo: ki por tick reducido por Fly. El drenaje base del aura lo
        // cobra TurboServerState por su cuenta, y se auto-apaga si el ki llega a 0.
        if (flyTurbo) {
            double drain = StatsConfig.flyKiDrainPerTick() * SkillEffects.flyKiDrainFactor(p);
            if (drain > 0.0) att.addKi(-drain);
        }
        if (!ab.flying) att.flags().setFlyBoosting(false);
    }

    /** Hitbox/cámara "acostado" durante el boost de vuelo. */
    private static void tickBoostHitbox(Player p, PlayerStatsAttachment att) {
        boolean flyingNow = att.isFlyEnabled() && p.getAbilities().flying && !p.isSpectator();
        if (!flyingNow) att.flags().setFlyBoosting(false);

        boolean prone = att.flags().isFlyBoosting();
        if (prone != att.flags().isBoostSizeApplied()) {
            att.flags().setBoostSizeApplied(prone);
            p.refreshDimensions();
        }
    }

    // =====================================================================
    // TRANSFORMACIONES
    // =====================================================================

    /** Tick de forms + drenaje de ki de la forma + lock al transformar.
     *  @return true si está transformando (corta el tick). */
    private static boolean tickForms(Player p, PlayerStatsAttachment att,
                                     PlayerFormAttachment form, PlayerVisualAttachment visual) {
        if (form.serverTick(p, att, visual)) {
            PlayerLifeCycle.syncFormIfServer(p);
        }

        // Drena ki si la forma actual tiene coste por tick; sin ki, vuelve a BASE.
        ResourceLocation currentFormId = form.getFormId();
        if (currentFormId != null && !FormIds.BASE.equals(currentFormId)) {
            FormDefinition def = FormRegistry.get(currentFormId);
            if (def != null && def.allowedRaces().contains(att.getRace())) {
                double drain = def.kiDrainPerTick() * MasteryEffects.formDrainFactor(p);
                if (drain > 0.0) {
                    int before = att.getKiCurrent();
                    att.addKi(-drain);
                    if (before > 0 && att.getKiCurrent() <= 0) {
                        form.forceBase();
                        PlayerLifeCycle.syncFormIfServer(p);
                    }
                }
            }
        }

        if (form.isTransforming()) {
            applyTransformLockServer(p, true);
            AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (moveAttr != null) moveAttr.removeModifier(MOVE_MOD_ID);
            return true;
        }
        applyTransformLockServer(p, false);
        return false;
    }

    // =====================================================================
    // CARGA DE KI Y % DE PODER
    // =====================================================================

    private static void tickKiCharge(Player p, PlayerStatsAttachment att) {
        if (!att.isChargingKi()) {
            chargeTicks.remove(p.getUUID());
            return;
        }

        double perTick = att.getRegenEnergyPerTick();
        double bonusMul = 3.0; // o config
        att.addKi(perTick * bonusMul);

        // Tras 1 s cargando, el % sube de 5 en 5 cada segundo hasta el techo de Ki Control.
        chargeTicks.merge(p.getUUID(), 1, Integer::sum);
        int t = chargeTicks.get(p.getUUID());
        if (t > 20 && (t - 20) % 20 == 0) {
            if (att.setPowerPercent(att.getPowerPercent() + 5, SkillEffects.maxPowerPercent(p))
                    && p instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("messages.zenkai.power_percent",
                        att.getPowerPercent(), SkillEffects.maxPowerPercent(p)), true);
            }
        }
    }

    // =====================================================================
    // REGENERACIÓN
    // =====================================================================

    private static void tickRegen(Player p, PlayerStatsAttachment att) {
        if (p.tickCount % 20 != 0) return;

        var food = p.getFoodData();
        if (!p.isCreative() && food.getFoodLevel() <= 0) return;

        boolean didBody = false, didStamina = false;

        int bodyCur = att.getBody(), bodyMax = att.getBodyMax();
        if (bodyCur > 0 && bodyCur < bodyMax) {
            att.addBody(atLeastOne(bodyMax * (StatsConfig.baseRegenBody() / 100.0)));
            didBody = true;
        }

        // Correr en turbo drena estamina: no se regenera a la vez o se anularían entre sí.
        int stCur = att.getStamina(), stMax = att.getStaminaMax();
        if (stCur < stMax && !p.isSprinting()) {
            att.addStamina(atLeastOne(stMax * (StatsConfig.baseRegenStamina() / 100.0)));
            didStamina = true;
        }

        int kiCur = att.getEnergy(), kiMax = att.getEnergyMax();
        if (kiCur < kiMax) {
            att.addEnergy(atLeastOne(kiMax * (StatsConfig.baseRegenEnergy() / 100.0)));
        }

        if (!p.isCreative()) {
            if (didBody)    food.addExhaustion(2.4F);
            if (didStamina) food.addExhaustion(0.6F);
        }
    }

    private static int atLeastOne(double amount) {
        int v = (int) Math.round(amount);
        return v <= 0 ? 1 : v;
    }

    // =====================================================================
    // MOVIMIENTO EN TIERRA
    // =====================================================================

    private static void tickMovement(Player p, PlayerStatsAttachment att, boolean turboOn) {
        boolean control     = p.isSprinting();       // en tierra, Control = esprintar
        boolean groundTurbo = turboOn && control && !p.getAbilities().flying;

        // Solo el turbo cuesta estamina; esprintar normal es gratis.
        if (groundTurbo && p.tickCount % 20 == 0) {
            double drain = StatsConfig.runStaminaDrainPerSecond() * SkillEffects.runStaminaDrainFactor(p);
            if (drain > 0.0) {
                att.addStamina(-(int) Math.max(1, Math.round(drain)));
                if (att.getStamina() <= 0) p.setSprinting(false);
            }
        }

        // El máximo lo marca DEX (speedStat, con su tope) y lo eleva la habilidad Run.
        double speedStat = att.computeSpeedFinal();
        double max = Math.min(1.0 + (speedStat / 100.0) * StatsConfig.movementScaling(),
                StatsConfig.speedMultiplierCap()) * SkillEffects.runSpeedFactor(p);
        double moveMult = 1.0 + (max - 1.0) * performanceTier(control, turboOn) * att.powerFraction();

        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveAttr != null) {
            moveAttr.removeModifier(MOVE_MOD_ID);
            moveAttr.addTransientModifier(new AttributeModifier(
                    MOVE_MOD_ID, moveMult - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    // =====================================================================
    // LOCKS
    // =====================================================================

    /**
     * LOCK real (servidor): corta input (xxa/zza/jump), quita sprint, ancla X/Z al tick
     * anterior (xo/zo) y corta la delta horizontal.
     */
    private static void applyTransformLockServer(Player p, boolean lock) {
        applyLock(p, lock, TRANSFORM_LOCK_ID);
    }

    /**
     * LOCK de derribado: mismo anclaje que el de transformación pero con su propio id, para
     * inmovilizar al jugador mientras está acostado. El daño SÍ le llega (no es invulnerable).
     */
    private static void applyDownedLockServer(Player p, boolean lock) {
        applyLock(p, lock, DOWNED_LOCK_ID);
    }

    private static void applyLock(Player p, boolean lock, ResourceLocation id) {
        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);

        if (!lock) {
            if (moveAttr != null && moveAttr.getModifier(id) != null) {
                moveAttr.removeModifier(id);
            }
            return;
        }

        if (moveAttr != null && moveAttr.getModifier(id) == null) {
            moveAttr.addTransientModifier(new AttributeModifier(
                    id, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        p.setSprinting(false);
        p.xxa = 0.0F;
        p.zza = 0.0F;
        p.setJumping(false);
        p.setPos(p.xo, p.getY(), p.zo);   // ancla horizontal (server-only)
        var v = p.getDeltaMovement();
        p.setDeltaMovement(0.0, v.y, 0.0); // corta inercia horizontal
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
}