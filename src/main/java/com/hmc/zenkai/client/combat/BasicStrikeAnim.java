package com.hmc.zenkai.client.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CombatModeClientState;
import com.hmc.zenkai.client.action.ActionStateClient;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.event.ZenkaiPalAnimations;
import com.hmc.zenkai.feature.action.ActionType;
import com.hmc.zenkai.feature.combat.BasicStrikePacket;
import com.hmc.zenkai.feature.combat.GuardHands;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Animación de los golpes básicos de mano vacía, al estilo de DragonMineZ: cada clic izquierdo en modo combate
 * lanza un clip de puño o patada que alterna con un contador (puño de la mano principal, puño de la otra, puño,
 * patada de remate) y que reinicia tras un rato sin golpear.
 *
 * SOLO presentación: no toca el daño, la estamina ni el enfriamiento (eso lo siguen decidiendo el cooldown de
 * ataque de vanilla y el servidor), y no bloquea ni cancela nada. Se omite si el jugador está haciendo otra cosa
 * (técnica, bloqueo, carga, aturdido, agarrado) para no pisar su pose. El clip se anima aquí por predicción y se
 * redifunde a los demás con BasicStrikePacket / BasicStrikeAnimPacket.
 *
 * El 4.º golpe coincide con el remate del combo (combo.finisher_every) cuando la cadena va completa; si se falla algún
 * golpe el contador del cliente y la cadena real del servidor pueden desfasarse un golpe: es solo estética.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class BasicStrikeAnim {
    private BasicStrikeAnim() {}

    private static int counter;
    private static long lastSwing = -1000;

    @SubscribeEvent
    public static void onAttackClick(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack() || !CombatModeClientState.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        if (!GuardHands.handsFree(mc.player)) return;
        // Un golpe flojo (pulsar antes de que el cooldown cargue) no cuenta como golpe: ni anima ni avanza el contador.
        if (mc.player.getAttackStrengthScale(0.5f) < 0.5f) return;
        if (StunClientState.localStunned() || GrabClientState.isHeld()) return;
        var action = ActionStateClient.local();
        if (action.type() != ActionType.NONE || action.physBusy()) return;

        long now = mc.level.getGameTime();
        if (now - lastSwing > ServerConfig.comboWindowTicks()) counter = 0;
        lastSwing = now;
        int every = ServerConfig.comboFinisherEvery();
        int variant = every > 0 && counter % every == every - 1 ? 3 : (counter % 3 == 1 ? 1 : 0);
        counter = every > 0 ? (counter + 1) % every : counter + 1;

        ZenkaiPalAnimations.playBasicStrike(mc.player, variant);
        if (mc.getConnection() != null) PacketDistributor.sendToServer(new BasicStrikePacket(variant));
    }
}
