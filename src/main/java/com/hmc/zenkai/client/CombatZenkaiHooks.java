package com.hmc.zenkai.client;

import com.hmc.zenkai.content.entity.ki_attacks.KiBlastEntity;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public class CombatZenkaiHooks {

    @SubscribeEvent
    public static void onPlayerReceiveDamage(LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        MinecraftServer server = player.getServer();
        if (server == null || !ModGameRules.enableRaceBoosts(server)) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(player);
        if (!att.isRaceChosen()) return;

        float incomingDamage = e.getNewDamage();
        if (incomingDamage <= 0f) return;

        double defense = att.computeDefenseFinal();

        double finalDamage;
        if (incomingDamage <= defense) {
            finalDamage = incomingDamage * StatsConfig.minDamagePercent();
        } else {
            finalDamage = incomingDamage - defense;
        }
        finalDamage = Math.max(finalDamage, 0.0);

        int dmgInt = (int) Math.ceil(finalDamage);
        att.addBody(-dmgInt);

        if (att.getBody() <= 0) {
            if (!player.isDeadOrDying()) {
                player.setHealth(0.0F);
                player.die(e.getSource());
            }
        }

        e.setNewDamage(0.0F);
        PlayerLifeCycle.syncIfServer(player);
    }

    @SubscribeEvent
    public static void onPlayerDealDamage(LivingDamageEvent.Pre e) {
        if (!(e.getSource().getEntity() instanceof Player player)) return;
        if (e.getSource().getDirectEntity() instanceof KiBlastEntity) return;

        MinecraftServer server = player.getServer();
        if (server == null || !ModGameRules.enableRaceBoosts(server)) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(player);
        if (!att.isRaceChosen()) return;

        double strDamage = att.computeMeleeFinal();

        double weaponBonus = 0.0;
        var attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr != null) weaponBonus = attr.getValue();

        int currentStamina = att.getStamina();

        double strApplied;
        double totalDamage;

        if (currentStamina <= 0) {
            strApplied  = 0.0;
            totalDamage = 0.0;
        } else {
            strApplied  = Math.min(strDamage, currentStamina);
            totalDamage = strApplied + weaponBonus;
        }

        int staminaCost = (int) Math.ceil(strApplied);
        if (staminaCost > 0) att.consumeStamina(staminaCost);

        e.setNewDamage((float) totalDamage);
        PlayerLifeCycle.syncIfServer(player);
    }
}