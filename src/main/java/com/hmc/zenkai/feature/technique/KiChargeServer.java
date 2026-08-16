package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
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

    /** Arranca la carga. NO VALIDA: lo hizo ActionResolver. Solo mecánica y difusión. */
    public static void begin(ServerPlayer sp, KiTechnique tech) {
        ACTIVE.put(sp.getUUID(), new Charging(-1, sp.level().getGameTime()));
        broadcast(sp, tech, true);

        SoundEvent snd = TechniqueAssets.soundOf(tech.chargeSound());
        if (snd != null) {
            sp.level().playSound(null, sp.getX(), sp.getEyeY(), sp.getZ(),
                    snd, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    /** Apaga la bola. Idempotente: lo llaman el resolver, la muerte y la desconexión. */
    public static void end(ServerPlayer sp) {
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