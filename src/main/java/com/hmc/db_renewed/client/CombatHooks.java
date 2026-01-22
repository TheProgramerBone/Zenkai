package com.hmc.db_renewed.client;

import com.hmc.db_renewed.content.entity.ki_attacks.KiBlastEntity;
import com.hmc.db_renewed.core.config.StatsConfig;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.player.PlayerLifeCycle;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public class CombatHooks {

    @SubscribeEvent
    public static void onFinalDamage(LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        DamageSource src = e.getSource();
        PlayerStatsAttachment att = player.getData(DataAttachments.PLAYER_STATS.get());
        if (!att.isRaceChosen()) return;

        // ESTE ya es el daño FINAL (armadura/pociones/absorción ya aplicaron)
        float vanillaFinal = e.getNewDamage();
        if (vanillaFinal <= 0f) return;

        double defense = att.computeDefenseFinal();

        // Aplicar defensa del mod SOBRE el daño ya mitigado por vanilla
        double afterDefense = vanillaFinal - defense;

        // Si la defensa supera el daño, aplica mínimo % configurable (evita inmortalidad)
        if (afterDefense <= 0.0) {
            double minPct = StatsConfig.minDamagePercent();
            afterDefense = vanillaFinal * Math.max(0.0, minPct);
        }

        // Convertir a daño entero para BODY
        int dmgInt = (int) Math.ceil(afterDefense);
        if (dmgInt <= 0) {
            // si quedara 0 por redondeos/config, no tocamos
            e.setNewDamage(0f);
            return;
        }

        // Aplicar a BODY
        att.addBody(-dmgInt);
        int bodyAfter = att.getBody();

        if (bodyAfter > 0) {
            // No queremos que baje vida vanilla; el “tanqueo” es BODY
            e.setNewDamage(0.0F);
        } else {
            // BODY = 0 -> muerte real
            if (!player.isDeadOrDying()) {
                player.setHealth(0.0F);
                player.die(src);
            }
            e.setNewDamage(0.0F);
        }

        PlayerLifeCycle.syncIfServer(player);
    }

    /**
     * Aplica melee + consumo de stamina SOLO para golpes cuerpo a cuerpo del jugador.
     * No afecta a KiBlastEntity (para que no gaste stamina al impactar).
     */
    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre e) {
        if (!(e.getSource().getEntity() instanceof Player player)) return;

        // Si el daño viene de un KiBlast, no tocamos stamina ni daño aquí
        if (e.getSource().getDirectEntity() instanceof KiBlastEntity) {
            return;
        }

        PlayerStatsAttachment att = player.getData(DataAttachments.PLAYER_STATS.get());

        if (!att.isRaceChosen()) return;

        // 1) Stat de melee del MOD: este es el daño "propio" del jugador
        double meleeStat = att.computeMeleeFinal(); // p.ej. 500

        // 2) Bonus del arma (vanilla): NO afecta al costo de stamina
        double weaponBonus = 0.0;
        var attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr != null) {
            weaponBonus = attr.getValue(); // puño ~1, espada ~7–10, etc.
        }

        int stamina = att.getStamina();
        double meleeDamage;   // daño que viene del stat
        double finalDamage;   // daño total = melee + arma

        if (stamina <= 0) {
            // Sin stamina no hay daño (ni de arma)
            meleeDamage = 0.0;
            finalDamage = 0.0;
        } else {
            // El daño por stat está limitado por la stamina disponible
            meleeDamage = Math.min(meleeStat, stamina);
            // El arma suma GRATIS en términos de stamina
            finalDamage = meleeDamage + weaponBonus;
        }

        // 3) Consumir stamina SOLO por el daño de melee (no por el arma)
        int staminaUsed = (int) Math.ceil(meleeDamage);
        if (staminaUsed > 0) {
            att.consumeStamina(staminaUsed);
        }

        // 4) Aplicar el daño calculado al evento
        e.setNewDamage((float) finalDamage);

        PlayerLifeCycle.syncIfServer(player);
    }
}