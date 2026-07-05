package com.hmc.zenkai.client;

import com.hmc.zenkai.content.entity.ki_attacks.KiBlastEntity;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.OtherworldManager;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

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

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre e) {
        if (e.getEntity().level().isClientSide()) return;

        MinecraftServer server = e.getEntity().getServer();
        if (server == null || !ModGameRules.enableRaceBoosts(server)) return;

        float dmg = e.getNewDamage();

        // ── 1) LADO ATACANTE ──────────────────────────────────────────────────
        if (e.getSource().getEntity() instanceof Player attacker
                && !(e.getSource().getDirectEntity() instanceof KiBlastEntity)) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(attacker);
            if (att.isRaceChosen()) {
                double strDamage = att.computeMeleeFinal();

                double weaponBonus = 0.0;
                AttributeInstance attr = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
                if (attr != null) weaponBonus = attr.getValue();

                int currentStamina = att.getStamina();
                double strApplied, totalDamage;
                if (currentStamina <= 0) {
                    strApplied = 0.0;
                    totalDamage = 0.0;
                } else {
                    strApplied = Math.min(strDamage, currentStamina);
                    totalDamage = strApplied + weaponBonus;
                }

                int staminaCost = (int) Math.ceil(strApplied);
                if (staminaCost > 0) att.consumeStamina(staminaCost);

                dmg = (float) totalDamage;
                PlayerLifeCycle.syncIfServer(attacker);
            }
        }

        // ── 2) LADO DEFENSOR ──────────────────────────────────────────────────
        if (e.getEntity() instanceof Player victim) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(victim);
            if (att.isRaceChosen()) {
                if (dmg > 0f) {
                    double defense = att.computeDefenseFinal();
                    double finalDamage = (dmg <= defense)
                            ? dmg * StatsConfig.minDamagePercent()
                            : dmg - defense;
                    finalDamage = Math.max(finalDamage, 0.0);
                    att.addBody(-(int) Math.ceil(finalDamage));
                }

                // El daño va al pool body; la vida vanilla no se toca nunca.
                e.setNewDamage(0.0F);

                if (att.getBody() <= 0) onBodyDepleted(victim, att);
                PlayerLifeCycle.syncIfServer(victim);
                return;
            }
        }

        // Víctima sin raza (mob u otro): aplica el daño recalculado del atacante a su vida vanilla.
        e.setNewDamage(dmg);
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
        if (att.isImmortal()) return;

        if (att.isInOtherworld()) {
            OtherworldManager.keepInOtherworld(sp);
            return;
        }

        if (att.flags().isDowned()) return; // ya está derribado

        att.flags().setDowned(true);
        att.flags().setDownedUntil(sp.serverLevel().getGameTime() + DOWNED_TICKS);
        PlayerLifeCycle.sync(sp);
    }
}