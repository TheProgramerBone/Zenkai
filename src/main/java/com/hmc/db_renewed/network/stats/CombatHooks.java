package com.hmc.db_renewed.network.stats;

import com.hmc.db_renewed.util.MathUtil;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class CombatHooks {

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        PlayerStatsAttachment att = player.getData(DataAttachments.PLAYER_STATS.get());
        double defense = att.computeDefenseFinal();
        double finalDmg = Math.max(1.0, e.getAmount() - defense);
        e.setAmount((float) finalDmg);
    }

    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre e) {
        if (!(e.getSource().getEntity() instanceof Player player)) return;

        PlayerStatsAttachment att = player.getData(DataAttachments.PLAYER_STATS.get());
        double meleeBonus = att.computeMeleeFinal();
        double planned = e.getNewDamage() + Math.max(0, meleeBonus);

        int stamina = att.getStamina();
        double finalDamage = planned;
        if (stamina < finalDamage) {
            if (stamina <= 0) {
                e.setNewDamage(0f);
                return;
            } else {
                finalDamage = planned * MathUtil.safeDiv(stamina, planned);
            }
        }
        att.consumeStamina((int) Math.ceil(finalDamage));
        e.setNewDamage((float) finalDamage);

        PlayerLifeCycle.syncIfServer(player);
    }
}