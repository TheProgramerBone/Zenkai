package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * «Contención Rota», forma Legendaria del arcosiano (decidida con el usuario, 2026-10-25): una SEGUNDA transformación que
 * se activa desde una forma arcosiana ya puesta (la gente usa el Arcosiano normal y, en un aprieto, sube de nivel). Salto
 * fijo y grande (LegendaryForms.ceiling), SIN drenaje de ki continuo; en su lugar un temporizador de TENSIÓN: al agotarse
 * hay colapso (vuelve a Base con ki y estamina a cero) y un enfriamiento largo. Salir antes por voluntad también deja
 * enfriamiento, proporcional a lo usado, para que entrar y salir no reinicie la tensión gratis. COMPARTE la maestría con la
 * forma de la que parte (PlayerFormAttachment#legendOrigin) y no tiene nodo propio en el árbol.
 * Solo servidor; el estado (tensionUntil, cooldown) viaja en el sync de la forma para la HUD.
 */
public final class ArcosianContainment {
    private ArcosianContainment() {}

    public static boolean wearing(PlayerFormAttachment form) {
        return FormIds.ARCOSIAN_LEGENDARY.equals(form.getFormId());
    }

    /** ¿Puede empezar la transformación ahora? Hay que llevar una forma arcosiana (no Base) y no estar en enfriamiento. */
    public static boolean canEnter(Player p, PlayerFormAttachment form) {
        if (FormIds.BASE.equals(form.getFormId()) || wearing(form)) return false;
        return p.level().getGameTime() >= form.getLegendCooldownUntil();
    }

    /** Un tick de servidor (desde FormSystem): arranca la tensión al entrar, la agota con colapso o cobra la salida voluntaria. */
    public static void tick(Player p, PlayerStatsAttachment att, PlayerFormAttachment form) {
        if (!(p instanceof ServerPlayer sp)) return;
        long now = p.level().getGameTime();

        if (wearing(form)) {
            if (form.getTensionUntil() <= 0) {
                // Entrada: la maestría compartida alarga la tensión (de ×1 a ×(1 + bonus) al 100 %).
                double mult = 1.0 + ServerConfig.arcosianLegendTensionMasteryBonus() * form.activeMastery() / 100.0;
                long total = Math.round(ServerConfig.arcosianLegendTensionSeconds() * 20.0 * mult);
                form.setTension(now + total, total);
                PlayerLifeCycle.syncFormIfServer(p);
            } else if (now >= form.getTensionUntil()) {
                collapse(sp, att, form, now);
            }
            return;
        }
        if (form.getTensionUntil() > 0) {
            // Salió antes (tap, rueda, ki...): enfriamiento proporcional a la tensión que gastó.
            double used = 1.0 - Math.max(0L, form.getTensionUntil() - now) / (double) Math.max(1L, form.getTensionTotal());
            form.setLegendCooldownUntil(now + Math.round(cooldownTicks() * Math.max(0.0, Math.min(1.0, used))));
            form.setTension(0L, 0L);
            PlayerLifeCycle.syncFormIfServer(p);
        }
    }

    private static void collapse(ServerPlayer sp, PlayerStatsAttachment att, PlayerFormAttachment form, long now) {
        form.setTension(0L, 0L);
        form.forceBase();
        // Colapso: ki y estamina a cero. El enfriamiento se pone DESPUÉS de forceBase por si este reinicia estado.
        att.consumeEnergy(att.getEnergy());
        att.addStamina(-att.getStamina());
        form.setLegendCooldownUntil(now + cooldownTicks());
        att.setArcosianPp(0.0);   // el precio de romper la contención: el tanque de Power Points se vacía
        sp.displayClientMessage(Component.translatable("messages.zenkai.arcosian_legendary.collapse"), true);
        PlayerLifeCycle.sync(sp);
    }

    private static long cooldownTicks() {
        return Math.round(ServerConfig.arcosianLegendCooldownSeconds() * 20.0);
    }
}
