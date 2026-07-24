package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.core.network.feature.aura.AuraColors;
import com.hmc.zenkai.core.network.feature.aura.TurboPacket;
import com.hmc.zenkai.core.network.feature.forms.FormDef;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
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

    public static boolean localTurbo = false;
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

        boolean down = KeyBindings.TURBO.isDown();
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
            REMOTE_TURBO.remove(entityId);
        }
    }

    public static boolean isAuraActive(AbstractClientPlayer p) {
        // Transformándose: flag autoritativo del servidor, sincronizado a trackers ->
        // vale igual para el jugador local y para los remotos, sin packet nuevo.
        if (p.getData(DataAttachments.PLAYER_FORM.get()).isTransforming()) return true;

        // Cargando ki: idem (lo escribe KiChargePacket en el servidor).
        if (p.getData(DataAttachments.PLAYER_STATS.get()).isChargingKi()) return true;

        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player) {
            // Local: además el estado optimista, para no esperar el round-trip.
            return localTurbo || CombatModeClientState.isCharging();
        }
        return REMOTE_TURBO.contains(p.getId());
    }

    /** Color del aura. Delega en AuraColors (core) para que servidor y cliente no se
     *  separen: las partículas de impacto resuelven el tinte. */
    public static int resolveColor(AbstractClientPlayer p) {
        return AuraColors.resolve(p);
    }

    /** Clave de tipo de aura de la forma activa ("default", "ssj", "divine", "dark").
     *  El kaioken NO enmascara el tipo: es capa de color (AuraColors.Layers); la
     *  FORMA decide la silueta. Sin forma → "default". */
    public static String resolveAuraType(AbstractClientPlayer p) {
        FormDef def = p.getData(DataAttachments.PLAYER_FORM.get()).activeDef();
        return def == null ? "default" : def.auraType();
    }
}