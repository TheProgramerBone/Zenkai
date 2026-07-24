package com.hmc.zenkai.core.technique;

import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.technique.KiChargeStatePacket;
import com.hmc.zenkai.core.network.feature.technique.TechniqueAssets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado servidor de "cargando técnica". ÚNICO sitio donde se enciende y se apaga, para que
 * el sonido, el aviso a los clientes y la limpieza no puedan desincronizarse.
 * Guarda el tick de inicio, no el progreso: los clientes derivan cuánto lleva cargada la
 * bola restando, y así no hay que sincronizar un contador cada tick.
 */
public final class KiChargeServer {
    private KiChargeServer() {}

    public record Charging(int slot, long startTick) {}

    private static final Map<UUID, Charging> ACTIVE = new ConcurrentHashMap<>();

    public static Charging of(ServerPlayer sp) { return ACTIVE.get(sp.getUUID()); }

    public static boolean isCharging(ServerPlayer sp) { return ACTIVE.containsKey(sp.getUUID()); }

    /** Empieza a cargar: valida el slot, avisa a los que le ven y lanza el sonido. */
    public static void start(ServerPlayer sp, int slot) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (att == null) return;

        KiTechnique tech = att.techniques().slot(slot);
        if (tech == null || !tech.type().enabled()) return;
        if (!att.techniques().isUnlocked(tech.type())) return;

        ACTIVE.put(sp.getUUID(), new Charging(slot, sp.level().getGameTime()));
        broadcast(sp, tech, true);

        // Una vez al empezar, no en bucle: un sonido repetido necesita instancia persistente
        // en cliente y aquí lo que importa es que TODOS lo oigan arrancar.
        SoundEvent snd = TechniqueAssets.soundOf(tech.chargeSound());
        if (snd != null) {
            sp.level().playSound(null, sp.getX(), sp.getEyeY(), sp.getZ(),
                    snd, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    /**
     * Deja de cargar. Idempotente a propósito: lo llaman el packet, el disparo, la muerte y
     * la desconexión, y ninguno debería tener que preguntar antes.
     */
    public static void stop(ServerPlayer sp) {
        if (ACTIVE.remove(sp.getUUID()) == null) return;
        broadcastStop(sp);
    }

    private static void broadcast(ServerPlayer sp, KiTechnique tech, boolean charging) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new KiChargeStatePacket(
                sp.getId(), charging, tech.rgb(), tech.size(),
                tech.type().ordinal(), tech.position().ordinal()));
    }

    private static void broadcastStop(ServerPlayer sp) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new KiChargeStatePacket(sp.getId(), false, 0, 1, 0, 0));
    }
}