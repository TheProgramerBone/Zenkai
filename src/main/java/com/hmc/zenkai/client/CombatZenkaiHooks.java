package com.hmc.zenkai.client;

import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.combat.ZenkaiCombatStats;
import com.hmc.zenkai.core.combat.ZenkaiStats;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.mastery.MasteryEffects;
import com.hmc.zenkai.core.network.feature.combat.CombatModeServerState;
import com.hmc.zenkai.core.network.feature.player.OtherworldManager;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.skills.SkillEffects;
import com.hmc.zenkai.core.technique.KiCombatServer;
import com.hmc.zenkai.core.technique.PhysicalCombatServer;
import com.hmc.zenkai.core.training.TrainingHooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Pipeline de combate en UN SOLO handler (antes había dos handlers sobre el mismo evento y el
 * orden entre ellos no estaba definido: por eso un golpe de 15 contra una defensa de 10 a veces
 * aplicaba 15 en vez de 5). Ahora, en el mismo evento y en orden garantizado:
 *   1) Lado ATACANTE: si pega un jugador con raza (y no es Ki Blast), el golpe se recalcula con
 *      su STR (limitado por stamina) + bonus de arma, y consume stamina.
 *   2) Lado DEFENSOR: si recibe un jugador con raza, se mitiga con su DEF y el daño va al pool
 *      BODY (la vida vanilla nunca baja). Si body llega a 0 -> estado "derribado" (transición).
 * Si la víctima es un mob, recibe en su vida vanilla el daño ya recalculado del atacante.
 * onDamage solo ORQUESTA; cada paso vive en su propia forma (mismo criterio que
 * TickHandlers.onPlayerTick).
 */
public class CombatZenkaiHooks {

    /** Duración del estado derribado antes de morir de verdad (5 s). */
    public static final int DOWNED_TICKS = 100;

    /** Al salir del derribado (por curación propia o de un aliado) el jugador vuelve con este % del body máx. */
    public static final double DOWNED_REVIVE_PCT = 0.20;

    /** Hambre (puntos de comida) que gasta quien revive a un aliado con click derecho (mano vacía). */
    public static final int REVIVE_HUNGER_COST = 4;

    /** Body con el que se levanta un derribado: 20% del máximo (mínimo 1). */
    public static int downedReviveBody(PlayerStatsAttachment att) {
        return Math.max(1, (int) Math.round(att.getBodyMax() * DOWNED_REVIVE_PCT));
    }

    // =====================================================================
    // ORQUESTADOR
    // =====================================================================

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre e) {
        if (e.getEntity().level().isClientSide()) return;
        // I-frames: va lo PRIMERO, antes de tocar defensa o pools. Un dash esquiva de verdad.
        if (e.getEntity() instanceof ServerPlayer dodger
                && PhysicalCombatServer.hasIFrames(dodger.getUUID())) {
            e.setNewDamage(0.0F);
            return;
        }
        MinecraftServer server = e.getEntity().getServer();
        if (server == null || !ModGameRules.enableRaceBoosts(server)) return;

        ZenkaiCombatStats atkStats = ZenkaiStats.of(e.getSource().getEntity());
        float dmg = computeAttackDamage(e, atkStats);

        ZenkaiCombatStats defStats = ZenkaiStats.of(e.getEntity());
        if (defStats != null && defStats.isCombatActive()) {
            applyToZenkaiVictim(e, atkStats, defStats, dmg);
            return;
        }
        applyToVanillaVictim(e, dmg);
    }

    // =====================================================================
    // LADO ATACANTE
    // =====================================================================

    /**
     * Daño de salida del atacante. Los proyectiles ki traen su daño ya calculado (kiPower),
     * así que no se recalculan aquí.
     * @return el daño que entra al lado defensor.
     */
    private static float computeAttackDamage(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats) {
        float dmg = e.getNewDamage();

        if (atkStats == null || !atkStats.isCombatActive()) return dmg;
        if (e.getSource().getDirectEntity() instanceof KiProjectileEntity) return dmg;
        if (PhysicalCombatServer.isFiring()) return dmg;

        double strDamage = atkStats.computeMeleeFinal();
        if (e.getSource().getEntity() instanceof Player atkP) {
            strDamage *= MasteryEffects.formStatFactor(atkP);
        }

        if (e.getSource().getEntity() instanceof Player attacker) {
            return playerMeleeDamage(attacker, atkStats, strDamage, dmg);
        }
        // Entidad: su STR es la fuente única del daño melee (sin gate de stamina en Fase 2).
        return (float) strDamage;
    }

    /**
     * Golpe cuerpo a cuerpo de un JUGADOR. Compuerta de MODO COMBATE: fuera de él, el golpe
     * deja pasar el daño VANILLA puro (sin STR zenkai y sin gastar stamina). Contra pools
     * zenkai es simbólico -> golpes amistosos sin vaporizar a nadie; contra mobs, normal.
     */
    private static float playerMeleeDamage(Player attacker, ZenkaiCombatStats atkStats,
                                           double strDamage, float vanillaDmg) {
        boolean zenkaiMelee = attacker instanceof ServerPlayer atkSp
                && CombatModeServerState.isActive(atkSp.getUUID());
        if (!zenkaiMelee) return vanillaDmg;

        // STR (limitado por stamina) + bonus de arma, y consume stamina.
        double weaponBonus = 0.0;
        AttributeInstance attr = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr != null) weaponBonus = attr.getValue();

        int currentStamina = atkStats.getStamina();
        double strApplied, totalDamage;
        if (currentStamina <= 0) {
            strApplied = 0.0;
            totalDamage = 0.0;
        } else {
            strApplied = Math.min(strDamage, currentStamina);
            totalDamage = strApplied + weaponBonus;
        }

        int staminaCost = (int) Math.ceil(strApplied);
        if (staminaCost > 0) atkStats.consumeStamina(staminaCost);

        PlayerLifeCycle.syncIfServer(attacker);
        return (float) totalDamage;
    }

    // =====================================================================
    // LADO DEFENSOR
    // =====================================================================

    /** Víctima CON stats zenkai: mitiga con DEF y el daño va al pool body. */
    private static void applyToZenkaiVictim(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                            ZenkaiCombatStats defStats, float dmg) {
        if (dmg > 0f) {
            double finalDamage = mitigate(e, atkStats, defStats, dmg);

            int bodyBefore = defStats.getBody();
            defStats.addBody(-(int) Math.ceil(finalDamage));

            // Entrenamiento: TP por daño EFECTIVO (post-defensa y post-barrera,
            // capado por el pool restante -> sin exploit de overkill ni de derribados).
            grantTraining(e, Math.min(finalDamage, bodyBefore));
        }

        if (e.getEntity() instanceof Player victim) {
            // El jugador nunca recibe daño vanilla; el daño vive en el pool body.
            e.setNewDamage(0.0F);
            if (defStats.getBody() <= 0) onBodyDepleted(victim, PlayerStatsAttachment.get(victim));
            PlayerLifeCycle.syncIfServer(victim);
            return;
        }

        // Entidad con stats: el body es su vida real (esquiva el cap de MC).
        if (defStats.getBody() <= 0) {
            // Golpe letal: dejamos pasar daño vanilla real -> muerte con loot/XP/killer correctos.
            e.setNewDamage(Math.max(e.getEntity().getHealth(), 1.0F));
        } else {
            e.setNewDamage(0.0F); // absorbido por el pool body
        }
    }

    /** Aplica DEF, bloqueo y barrera. @return daño que llega al body. */
    private static double mitigate(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                   ZenkaiCombatStats defStats, float dmg) {
        double defense = computeDefense(e, atkStats, defStats, dmg);

        double finalDamage = (dmg <= defense)
                ? dmg * StatsConfig.minDamagePercent()
                : dmg - defense;
        finalDamage = Math.max(finalDamage, 0.0);

        if (e.getEntity() instanceof ServerPlayer defSp && KiCombatServer.isBlocking(defSp)) {
            finalDamage *= SkillEffects.blockDamageMultiplier(defSp);
        }

        // Barrera ki: absorbe ANTES de tocar el body (solo jugadores).
        if (e.getEntity() instanceof ServerPlayer defSp) {
            finalDamage = KiCombatServer.absorb(defSp, finalDamage);
        }
        return finalDamage;
    }

    /** DEF efectiva del defensor frente a ESTE golpe. Se mantiene SIEMPRE, en modo combate o no. */
    private static double computeDefense(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                         ZenkaiCombatStats defStats, float dmg) {
        double defense = defStats.computeDefenseFinal();

        if (e.getEntity() instanceof Player defP) {
            defense *= MasteryEffects.formStatFactor(defP);
        }
        if (PhysicalCombatServer.isFiring()) {
            defense *= PhysicalCombatServer.currentDefenseScale();
        }

        // Proyectil ki: la DEF se escala según lo cargado que venía el disparo respecto al
        // poder de QUIEN LO LANZÓ. refPower > 0 = fue desviado (kiai), así que el dueño actual
        // ya no es quien disparó y hay que usar la referencia congelada en el proyectil.
        if (e.getSource().getDirectEntity() instanceof KiProjectileEntity proj) {
            double kiPower = proj.refPower() > 0.0
                    ? proj.refPower()
                    : (atkStats != null ? atkStats.computeKiPowerFinal() : 0.0);
            if (kiPower > 1.0e-6) defense *= (dmg / kiPower);
        }
        return defense;
    }

    /** Víctima SIN stats (mob vanilla): recibe en su vida el daño ya recalculado. */
    private static void applyToVanillaVictim(LivingDamageEvent.Pre e, float dmg) {
        e.setNewDamage(dmg);
        // Entrenamiento vs mobs vanilla: capado por la vida restante (sin overkill).
        if (dmg > 0f) grantTraining(e, Math.min(dmg, e.getEntity().getHealth()));
    }

    /** TP de entrenamiento al atacante, si es un jugador distinto de la víctima. */
    private static void grantTraining(LivingDamageEvent.Pre e, double amount) {
        Entity source = e.getSource().getEntity();
        if (source instanceof ServerPlayer trainer && trainer != e.getEntity()) {
            TrainingHooks.grantFromDamage(trainer, amount);
        }
    }

    // =====================================================================
    // DERRIBADO
    // =====================================================================

    /**
     * Body agotado. En vez de morir al instante:
     *  - inmortal: no cae.
     *  - ya en el otro mundo: se re-ancla ahí (sin reiniciar su temporizador de Yemma).
     *  - vivo: entra en "derribado" (acostado) 5 s. Si lo curan (senzu propio/aliado) revive;
     *    si nadie lo cura, el tick de TickHandlers lo mata de verdad y pasa al otro mundo.
     */
    private static void onBodyDepleted(Player victim, PlayerStatsAttachment att) {
        if (!(victim instanceof ServerPlayer sp)) return;

        // Inmortal: no cae NUNCA y no debe quedarse a 0. Rellenamos el body al máximo
        // (antes solo se hacía `return`, dejando body=0 y la barra bugueada en "HP 0/max").
        if (att.isImmortal()) {
            att.setBody(att.getBodyMax());
            sp.setHealth(sp.getMaxHealth());
            PlayerLifeCycle.sync(sp);
            return;
        }

        if (att.isInOtherworld()) {
            OtherworldManager.keepInOtherworld(sp);
            return;
        }

        if (att.flags().isDowned()) return; // ya está derribado

        att.flags().setDowned(true);
        att.flags().setDownedUntil(sp.serverLevel().getGameTime() + DOWNED_TICKS);
        PlayerLifeCycle.sync(sp);
    }

    /**
     * Curar a un aliado DERRIBADO con click derecho y MANO VACÍA ("darle energía"):
     *  - Solo funciona si el objetivo está derribado.
     *  - Al curador le cuesta hambre (REVIVE_HUNGER_COST); si no le queda comida, no puede.
     *  - El objetivo recupera el 20% del body; el tick de derribado (TickHandlers) lo levanta
     *    al siguiente tick al detectar body>0 (libera lock y pose).
     * Con la senzu en mano NO entra aquí (mano no vacía): esa vía la maneja SenzuBean.
     */
    @SubscribeEvent
    public static void onDownedAllyInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide()) return;
        if (e.getHand() != InteractionHand.MAIN_HAND) return;

        Player healer = e.getEntity();
        if (!healer.getMainHandItem().isEmpty()) return;               // mano vacía
        if (!(e.getTarget() instanceof ServerPlayer target)) return;

        PlayerStatsAttachment tAtt = PlayerStatsAttachment.get(target);
        if (!tAtt.flags().isDowned()) return;                          // solo si está derribado

        // Costo de hambre para el curador (creativo cura gratis).
        if (!healer.isCreative()) {
            FoodData food = healer.getFoodData();
            if (food.getFoodLevel() <= 0) {                            // sin energía que dar
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
                return;
            }
            food.setFoodLevel(Math.max(0, food.getFoodLevel() - REVIVE_HUNGER_COST));
        }

        // Levanta al aliado con el 20% del body.
        tAtt.setBody(downedReviveBody(tAtt));
        PlayerLifeCycle.sync(target);

        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    // =====================================================================
    // BLOQUEO
    // =====================================================================

    @SubscribeEvent
    public static void onAttackWhileBlocking(net.neoforged.neoforge.event.entity.player.AttackEntityEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        if (e.getEntity() instanceof ServerPlayer sp && KiCombatServer.isBlocking(sp)) {
            e.setCanceled(true);
        }
    }

    /** Nadie FIJA a un jugador derribado. Complemento del barrido de TickHandlers, que es
     *  quien suelta a los que ya lo tenían de objetivo antes de que cayera. */
    @SubscribeEvent
    public static void onChangeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent e) {
        if (!(e.getNewAboutToBeSetTarget() instanceof ServerPlayer target)) return;
        if (PlayerStatsAttachment.get(target).flags().isDowned()) {
            e.setCanceled(true);
        }
    }
}