package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Kaioken: quema VIDA mientras esté activo. Es lo que impide que sea gratis, y por eso
 * drena body y no ki. Se apaga solo al caer al mínimo o al perder la habilidad.
 */
public final class KaiokenSystem {
    private KaiokenSystem() {}

    public static void tick(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        var form = c.form();
        KaiokenTier tier = form.getKaioken();
        if (!tier.isOn()) return;

        // Perdió la habilidad (respec) o el nivel ya no alcanza este escalón: se apaga.
        if (!tier.allowedAt(SkillEffects.kaiokenLevel(p))) {
            form.setKaioken(KaiokenTier.OFF);
            if (p instanceof ServerPlayer sp) PlayerLifeCycle.sync(sp);
            return;
        }

        double[] carry = PlayerTickState.carry(p.getUUID());
        double perTick = att.getBodyMax() * (tier.drainPctPerSecond() / 100.0) / 20.0
                * SkillEffects.kaiokenDrainFactor(p);

        // Reusa el acumulador de body [0] A PROPÓSITO: RegenSystem suma ahí una vez por
        // segundo, así que una buena constitución compensa la quema y el jugador aguanta
        // el kaioken. Ver PlayerTickState#carry.
        carry[0] -= perTick;
        int whole = (int) Math.floor(-carry[0]);
        if (whole > 0) {
            carry[0] += whole;
            att.addBody(-whole);
        }

        // No mata: al llegar a 1 se corta solo. Morir por kaioken sería un castigo doble
        // (ya te deja al borde y sin recursos).
        if (att.getBody() <= 1) {
            att.setBody(1);
            form.setKaioken(KaiokenTier.OFF);
            if (p instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("messages.zenkai.kaioken.exhausted"), true);
                PlayerLifeCycle.sync(sp);
            }
        }
    }
}