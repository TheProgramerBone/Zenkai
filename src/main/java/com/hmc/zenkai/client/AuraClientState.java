package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.core.network.feature.aura.TurboPacket;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

/**
 * Estado cliente del aura: turbo local (doble-tap R, optimista; el server confirma/fuerza
 * apagado por energía vía TurboSyncPacket) + set de remotos en turbo.
 * El aura se ve: local = turbo O cargando ki (la carga es local-only por ahora);
 * remoto = turbo sincronizado.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraClientState {
    private AuraClientState() {}

    /** Ventana del doble-tap en ticks. */
    private static final int DOUBLE_TAP_TICKS = 7;

    private static boolean localTurbo = false;
    private static final Set<Integer> REMOTE_TURBO = new HashSet<>();

    private static boolean prevDown = false;
    private static long lastPressTick = Long.MIN_VALUE;
    private static long tick = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        tick++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            localTurbo = false;
            REMOTE_TURBO.clear();
            prevDown = false;
            return;
        }

        boolean down = KeyBindings.FIRE_KI.isDown();
        // Doble-tap de R SIN click derecho (para no confundir con el combo de carga).
        if (down && !prevDown && mc.screen == null && !mc.options.keyUse.isDown()) {
            if (tick - lastPressTick <= DOUBLE_TAP_TICKS) {
                lastPressTick = Long.MIN_VALUE;
                if (PlayerStatsAttachment.get(mc.player).isRaceChosen()) {
                    localTurbo = !localTurbo; // optimista; el server confirma
                    PacketDistributor.sendToServer(new TurboPacket(localTurbo));
                }
            } else {
                lastPressTick = tick;
            }
        }
        prevDown = down;
    }

    /** Confirmación/forzado desde el server (energía agotada, respawn...). */
    public static void applySync(int entityId, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == entityId) {
            localTurbo = on;
        } else if (on) {
            REMOTE_TURBO.add(entityId);
        } else {
            REMOTE_TURBO.remove(Integer.valueOf(entityId));
        }
    }

    public static boolean isAuraActive(AbstractClientPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player) {
            return localTurbo || CombatModeClientState.isCharging();
        }
        return REMOTE_TURBO.contains(p.getId());
    }

    /** Color del aura. Punto único para que las transformaciones lo pisen después
     *  (auraStyleId ya existe en PlayerVisualAttachment). */
    public static int resolveColor(AbstractClientPlayer p) {
        return p.getData(com.hmc.zenkai.core.network.feature.stats.DataAttachments.PLAYER_VISUAL.get())
                .getAuraColorRgb();
    }
}