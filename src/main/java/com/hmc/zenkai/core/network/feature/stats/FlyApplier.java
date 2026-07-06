package com.hmc.zenkai.core.network.feature.stats;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Vuelo dinámico (capa 1: física/velocidad). Sobre el mayfly:
 *  - Crucero LENTO por defecto.
 *  - Al mantener CONTROL (tecla de sprint) mientras vuelas, una rampa de aceleración sube la
 *    velocidad hasta un MÁXIMO que escala con DEXTERITY (getFlyMultiplier). Al soltar, frena.
 * Solo actúa sobre el jugador local (cliente); el movimiento de vuelo es client-authoritative.
 */
public class FlyApplier {

    /** Rampa 0..1 del jugador local: 0 = crucero, 1 = velocidad máxima con Control. */
    private static float ramp = 0f;

    // ── Diales tuneables ─────────────────────────────────────────────────────
    private static final float FLY_CRUISE = 0.02f; // velocidad base (sin Control), lenta
    private static final float FLY_BOOST  = 0.03f; // base del boost; se multiplica por el mult de dexterity
    private static final float RAMP_UP    = 0.04f; // subida de la rampa por tick al mantener Control (~25 ticks a tope)
    private static final float RAMP_DOWN  = 0.08f; // bajada por tick al soltar (frena más rápido)

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var player = mc.player;

        // Creativo/espectador: no tocamos su vuelo.
        if (player.isCreative() || player.isSpectator()) { ramp = 0f; return; }

        var att = player.getData(DataAttachments.PLAYER_STATS.get());
        if (!att.isFlyEnabled() || !player.getAbilities().mayfly) { ramp = 0f; return; }

        // Acelera solo mientras vuelas de verdad y mantienes Control (sprint).
        boolean boosting = player.getAbilities().flying && mc.options.keySprint.isDown();
        ramp = Mth.clamp(ramp + (boosting ? RAMP_UP : -RAMP_DOWN), 0f, 1f);

        double dexMult = att.getFlyMultiplier();                 // >=1, escala con DEXTERITY (cap en config)
        double max     = FLY_BOOST * dexMult;                    // velocidad máxima con Control, según dex
        double current = FLY_CRUISE + (max - FLY_CRUISE) * ramp; // interpola crucero -> máx según la rampa

        player.getAbilities().setFlyingSpeed((float) current);
    }
}