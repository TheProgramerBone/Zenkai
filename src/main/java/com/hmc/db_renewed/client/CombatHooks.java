package com.hmc.db_renewed.client;

import com.hmc.db_renewed.content.entity.ki_attacks.KiBlastEntity;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.stats.PlayerLifeCycle;
import com.hmc.db_renewed.core.network.feature.stats.PlayerStatsAttachment;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;


public class CombatHooks {

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        DamageSource src = e.getSource();
        PlayerStatsAttachment att = player.getData(DataAttachments.PLAYER_STATS.get());

        if (!att.isRaceChosen()) return;

        // Daño bruto recibido
        double raw = e.getAmount();
        if (raw <= 0.0) return;

        // Defensa del sistema
        double defense = att.computeDefenseFinal();
        double finalDmg = Math.max(0.0, raw - defense);
        if (finalDmg <= 0.01) {
            // Daño insignificante → cancelamos
            e.setAmount(0.0F);
            e.setCanceled(true);
            return;
        }

        int dmgInt = (int) Math.ceil(finalDmg);

        int bodyBefore = att.getBody();
        int bodyMax    = att.getBodyMax();

        // Aplicar daño a BODY
        att.addBody(-dmgInt); // clamp 0..bodyMax
        int bodyAfter = att.getBody();

        // Si todavía tenemos BODY > 0
        if (bodyAfter > 0) {
            // Dejamos un daño vanilla pequeño (por ejemplo 1f) SOLO para animación/knockback.
            // NO cancelamos el evento.
            e.setAmount(1.0F);
        } else {
            // BODY llegó a 0 → muerte real
            if (!player.isDeadOrDying()) {
                player.setHealth(0.0F);
                player.die(src);
            }
            // Cancelamos este daño para que no vuelva a procesarse
            e.setAmount(0.0F);
            e.setCanceled(true);
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