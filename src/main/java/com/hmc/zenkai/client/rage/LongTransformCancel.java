package com.hmc.zenkai.client.rage;

import com.hmc.zenkai.client.aura.compose.AuraComposer;
import com.hmc.zenkai.client.aura.compose.TransformCameraFx;
import com.hmc.zenkai.feature.rage.LongTransformState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.UUID;

/** Cancela en el cliente la secuencia larga de un jugador cuya carga se cortó antes de completarse (ver LongTransformCancelPacket). */
public final class LongTransformCancel {
    private LongTransformCancel() {}

    public static void apply(UUID player) {
        LongTransformState.consumeInfo(player);
        ClientFormDelay.cancel(player);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getPlayerByUUID(player) instanceof AbstractClientPlayer p)) return;
        AwakeningScene.cancel(p);
        AuraComposer.cancelTransform(p.getId());
        if (p == mc.player) TransformCameraFx.cancel();
    }
}
