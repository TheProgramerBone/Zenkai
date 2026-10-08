package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CombatModeClientState;
import com.hmc.zenkai.client.action.ActionStateClient;
import com.hmc.zenkai.feature.action.ActionPhase;
import com.hmc.zenkai.feature.action.ActionState;
import com.hmc.zenkai.feature.action.ActionType;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.aura.TurboPacket;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
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
        if (p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).isTransforming()) return true;

        // Cargando ki: idem (lo escribe KiChargePacket en el servidor).
        if (p.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).isChargingKi()) return true;

        // Cargando una TÉCNICA de ki: el aura solo sale al pasar del 100 % (sobrecarga).
        if (isTechniqueOvercharging(p)) return true;

        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player) return localTurbo;
        return REMOTE_TURBO.contains(p.getId());
    }

    /**
     * ¿Está CARGANDO ki o transformándose? Es {@link #isAuraActive} sin el turbo: el turbo
     * mantiene el aura encendida pero no remueve el suelo. Lo usan los efectos de suelo
     * (AuraChargeGroundFx, AuraFlameGroundRenderer). Mismas fuentes que isAuraActive: flags del
     * servidor ya sincronizados a los trackers, más la carga optimista del jugador local.
     */
    public static boolean isChargingOrTransforming(AbstractClientPlayer p) {
        if (p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).isTransforming()) return true;
        if (p.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).isChargingKi()) return true;
        return isTechniqueOvercharging(p);
    }

    /**
     * ¿Está SOBRECARGANDO una técnica de ki (más del 100 % de carga)? Hasta ahí cargar una técnica no
     * enciende el aura (pedido del usuario, 2026-10-07): solo la sobrecarga. Vale para locales y
     * remotos: la fase OVERCHARGING la decide el servidor (ActionStateServer, depende de la maestría
     * y no se puede derivar en el cliente de un observador); el jugador local además mira su
     * predicción para no esperar el round-trip.
     */
    public static boolean isTechniqueOvercharging(AbstractClientPlayer p) {
        ActionState st = ActionStateClient.of(p.getId());
        if (st.type() == ActionType.KI_TECHNIQUE && st.phase() == ActionPhase.OVERCHARGING) return true;
        Minecraft mc = Minecraft.getInstance();
        return p == mc.player && CombatModeClientState.chargeRatio(mc) > 1.0;
    }

    /** ¿Este jugador está en turbo AHORA? El local incluye el estado optimista para
     *  no esperar el round-trip; los remotos van por el sync del servidor. */
    public static boolean isTurbo(AbstractClientPlayer p) {
        if (p == Minecraft.getInstance().player) {
            return localTurbo || REMOTE_TURBO.contains(p.getId());
        }
        return REMOTE_TURBO.contains(p.getId());
    }

    /** Color del aura. Delega en AuraColors (core) para que servidor y cliente no se
     *  separen: las partículas de impacto resuelven el tinte. */
    public static int resolveColor(AbstractClientPlayer p) {
        return AuraColors.resolve(p);
    }

}