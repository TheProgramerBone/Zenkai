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

    // DAÑO RECIBIDO POR EL JUGADOR
    //
    // Flujo:
    //   1. Si el jugador no eligió raza → combat vanilla, no tocamos nada.
    //   2. Si eligió raza → la armadura vanilla se ignora (ya cancelamos su
    //      efecto poniendo el daño de vuelta al valor pre-armadura si hace
    //      falta, o simplemente trabajamos sobre el daño final del evento).
    //   3. Comparamos el daño con la DEX (defensa) del jugador:
    //        - daño <= defensa → el jugador recibe el 5% del daño original
    //        - daño >  defensa → daño final = daño - defensa
    //   4. Aplicamos ese daño final a BODY (no a los corazones vanilla).
    //      Los corazones solo bajan si BODY llega a 0.

    @SubscribeEvent
    public static void onPlayerReceiveDamage(LivingDamageEvent.Pre e) {
        // Solo aplica si quien recibe el golpe es un jugador
        if (!(e.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(player);

        // Sin raza elegida → vanilla se encarga, no tocamos nada
        if (!att.isRaceChosen()) return;

        // El daño que llegó al evento ya pasó por armadura vanilla.
        // Como ignoramos la armadura vanilla para jugadores con raza,
        // trabajamos directamente sobre este valor.
        float incomingDamage = e.getNewDamage();
        if (incomingDamage <= 0f) return;

        double defense = att.computeDefenseFinal(); // basado en DEX

        double finalDamage;

        if (incomingDamage <= defense) {
            // El jugador absorbió el golpe → recibe solo el 5% como daño residual
            finalDamage = incomingDamage * StatsConfig.minDamagePercent(); // 0.05 por defecto
        } else {
            // El golpe superó la defensa → resta simple
            finalDamage = incomingDamage - defense;
        }

        // Nunca puede ser negativo ni cero si hay daño real
        finalDamage = Math.max(finalDamage, 0.0);

        // Aplicar a BODY (sistema interno del mod)
        int dmgInt = (int) Math.ceil(finalDamage);
        att.addBody(-dmgInt);

        if (att.getBody() <= 0) {
            // BODY agotado → muerte real
            if (!player.isDeadOrDying()) {
                player.setHealth(0.0F);
                player.die(e.getSource());
            }
        }

        // Cancelamos el daño vanilla: los corazones no bajan, solo baja BODY
        e.setNewDamage(0.0F);

        PlayerLifeCycle.syncIfServer(player);
    }

    // DAÑO DADO POR EL JUGADOR (melee)
    //
    // Flujo:
    //   1. Si el daño viene de un KiBlast → no tocamos (lo maneja KiBlastEntity).
    //   2. Si el jugador no eligió raza → vanilla se encarga.
    //   3. Daño final = STR del jugador + bonus del arma vanilla.
    //   4. El daño está limitado por la stamina disponible:
    //        - Sin stamina → no hay daño en absoluto.
    //        - Con stamina → el daño por STR se clampea a la stamina restante.
    //          El bonus del arma suma encima sin coste de stamina.
    //   5. Se consume stamina igual al daño de STR aplicado.
    @SubscribeEvent
    public static void onPlayerDealDamage(LivingDamageEvent.Pre e) {
        // Solo aplica si quien golpea es un jugador
        if (!(e.getSource().getEntity() instanceof Player player)) return;

        // Los Ki Blasts se manejan solos, no tocar aquí
        if (e.getSource().getDirectEntity() instanceof KiBlastEntity) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(player);

        // Sin raza elegida → vanilla se encarga
        if (!att.isRaceChosen()) return;

        // Daño base de la estadística STR del mod
        double strDamage = att.computeMeleeFinal();

        // Bonus del arma vanilla (espada, hacha, etc.) — no consume stamina
        double weaponBonus = 0.0;
        var attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr != null) weaponBonus = attr.getValue();

        int currentStamina = att.getStamina();

        double strApplied;   // cuánto STR se aplica realmente
        double totalDamage;  // daño total que recibe la entidad

        if (currentStamina <= 0) {
            // Sin stamina: no hay daño, ni siquiera el del arma
            strApplied  = 0.0;
            totalDamage = 0.0;
        } else {
            // El daño de STR está limitado por la stamina disponible
            strApplied  = Math.min(strDamage, currentStamina);
            totalDamage = strApplied + weaponBonus;
        }

        // Consumir stamina proporcional al STR aplicado
        int staminaCost = (int) Math.ceil(strApplied);
        if (staminaCost > 0) {
            att.consumeStamina(staminaCost);
        }

        e.setNewDamage((float) totalDamage);

        PlayerLifeCycle.syncIfServer(player);
    }
}