package com.hmc.zenkai.client;

import com.hmc.zenkai.content.entity.ki_attacks.KiBlastEntity;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.combat.ZenkaiCombatStats;
import com.hmc.zenkai.core.combat.ZenkaiStats;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.OtherworldManager;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.training.TrainingHooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre e) {
        if (e.getEntity().level().isClientSide()) return;

        MinecraftServer server = e.getEntity().getServer();
        if (server == null || !ModGameRules.enableRaceBoosts(server)) return;

        float dmg = e.getNewDamage();

        // ── 1) LADO ATACANTE (jugador o entidad con stats) ────────────────────
        // Ki Blast tiene su propio cálculo -> no se recalcula aquí.
        ZenkaiCombatStats atkStats = ZenkaiStats.of(e.getSource().getEntity());
        if (atkStats != null && atkStats.isCombatActive()
                && !(e.getSource().getDirectEntity() instanceof KiBlastEntity)) {
            double strDamage = atkStats.computeMeleeFinal();

            if (e.getSource().getEntity() instanceof Player attacker) {
                // Jugador: STR (limitado por stamina) + bonus de arma, y consume stamina.
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

                dmg = (float) totalDamage;
                PlayerLifeCycle.syncIfServer(attacker);
            } else {
                // Entidad: su STR es la fuente única del daño melee (sin gate de stamina en Fase 2).
                dmg = (float) strDamage;
            }
        }

        // ── 2) LADO DEFENSOR (jugador o entidad con stats) ────────────────────
        ZenkaiCombatStats defStats = ZenkaiStats.of(e.getEntity());
        if (defStats != null && defStats.isCombatActive()) {
            if (dmg > 0f) {
                double defense = defStats.computeDefenseFinal();
                double finalDamage = (dmg <= defense)
                        ? dmg * StatsConfig.minDamagePercent()
                        : dmg - defense;
                finalDamage = Math.max(finalDamage, 0.0);
                int bodyBefore = defStats.getBody();
                defStats.addBody(-(int) Math.ceil(finalDamage));

                // Entrenamiento: TP por daño efectivo (capado por el pool restante: sin overkill).
                if (e.getSource().getEntity() instanceof ServerPlayer trainer
                        && trainer != e.getEntity()) {
                    TrainingHooks.grantFromDamage(trainer, Math.min(finalDamage, bodyBefore));
                }
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
            return;
        }

        // Víctima sin stats (mob vanilla): aplica el daño recalculado del atacante a su vida vanilla.
        e.setNewDamage(dmg);
        // Entrenamiento vs mobs vanilla: capado por la vida restante (sin overkill).
        if (dmg > 0f && e.getSource().getEntity() instanceof ServerPlayer trainer
                && trainer != e.getEntity()) {
            TrainingHooks.grantFromDamage(trainer, Math.min(dmg, e.getEntity().getHealth()));
        }
    }

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
}