package com.hmc.zenkai.feature.gravity;

import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Daño del ki de un dron de entrenamiento agresivo (Fase 4 de la 0.7.0). Lo llama {@code CombatZenkaiHooks}
 * tras CANCELAR el golpe: no pasa por la tubería de combate (ni DEF, ni derribado, ni muerte permanente).
 * Quita el {@code shotBodyPercent} de la dificultad del dron (del body máximo), escalado por el daño que llegó (1 = impacto directo;
 * menos con el área de la explosión), escrito directo en el pool y con suelo en 1: igual que el drenaje de
 * la cámara, el entrenamiento nunca mata. Creativo y espectador no reciben nada.
 * Si el impacto te deja en 1 (o ya estabas en 1), devuelve true: la ronda agresiva termina ahí con el TP de los
 * drones ya destruidos (pedido del usuario, 2026-11-22; ver GravityDroneRound#defeat).
 */
public final class DroneShotDamage {
    private DroneShotDamage() {}

    /** @return true si el jugador quedó en 1 de body (fuera de combate para la ronda) */
    public static boolean apply(ServerPlayer sp, float amount, double bodyPercent) {
        if (sp.isCreative() || sp.isSpectator()) return false;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (att.getBody() <= 1) return true;
        double scale = Math.max(0.0, Math.min(1.0, amount));
        int loss = (int) Math.ceil(att.getBodyMax() * bodyPercent / 100.0 * scale);
        if (loss <= 0) return false;
        att.setBody(Math.max(1, att.getBody() - loss));
        PlayerLifeCycle.sync(sp);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6f, 1.2f);
        return att.getBody() <= 1;
    }
}
