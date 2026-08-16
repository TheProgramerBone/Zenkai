package com.hmc.zenkai.client.sound;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.registry.ModSounds;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Enciende y apaga los bucles de carga de ki (C) y de turbo (R) de TODOS los jugadores
 * visibles, no solo del propio. Si solo sonara el local, un jugador cargando delante de ti
 * sería mudo y el aura no tendría sonido.
energy_generator * MISMO PATRÓN QUE ScouterBenchSounds y por el mismo motivo: el mapa por jugador impide
 * apilar bucles. Sin él, cada tick crearía una instancia nueva encima de la anterior y a los
 * cinco segundos habría cien copias sonando a la vez.
energy_generator * Se recorre la lista de jugadores del nivel y no un evento por jugador porque el estado
 * puede apagarse SIN evento — alguien que sale del rango de tracking, o que se desconecta
 * cargando, deja su bucle huérfano. Barrer aquí lo cubre con una regla sola.
energy_generator * Los dos estados que consulta ya viajan solos: isChargingKi está en el attachment (que se
 * sincroniza a los trackers) y el turbo lo mantiene AuraClientState con TurboSyncPacket. No
 * hace falta ningún packet nuevo para el sonido.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiPlayerSounds {
    private ZenkaiPlayerSounds() {}

    private static final Map<Integer, PlayerLoopSound> KI_CHARGE = new HashMap<>();
    private static final Map<Integer, PlayerLoopSound> TURBO     = new HashMap<>();

    private static final float KI_CHARGE_VOLUME = 0.55f;
    private static final float TURBO_VOLUME     = 0.45f;

    public static boolean isChargingKi(AbstractClientPlayer p) {
        return p.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).isChargingKi();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stopAll(KI_CHARGE);
            stopAll(TURBO);
            return;
        }

        prune(KI_CHARGE);
        prune(TURBO);

        for (var entity : mc.level.players()) {
            if (!(entity instanceof AbstractClientPlayer p)) continue;
            sync(KI_CHARGE, p, isChargingKi(p),
                    () -> new PlayerLoopSound(ModSounds.KI_CHARGE.get(), p,
                            ZenkaiPlayerSounds::isChargingKi, KI_CHARGE_VOLUME));
            sync(TURBO, p, AuraClientState.isTurbo(p),
                    () -> new PlayerLoopSound(ModSounds.TURBO_LOOP.get(), p,
                            AuraClientState::isTurbo, TURBO_VOLUME));
        }
    }

    /** Arranca el bucle si toca y no lo hay; el apagado lo decide el propio sonido en su
     *  tick(), que es quien conoce la condición y no depende de que este barrido llegue. */
    private static void sync(Map<Integer, PlayerLoopSound> active, AbstractClientPlayer p,
                             boolean shouldPlay, java.util.function.Supplier<PlayerLoopSound> factory) {
        int id = p.getId();
        PlayerLoopSound current = active.get(id);
        if (current != null && !current.isStopped()) return;
        if (!shouldPlay) { active.remove(id); return; }

        PlayerLoopSound sound = factory.get();
        active.put(id, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    /** Quita del mapa los que el motor ya paró. Sin esto el mapa crece con cada jugador que
     *  haya cargado ki alguna vez en la sesión. */
    private static void prune(Map<Integer, PlayerLoopSound> active) {
        Iterator<Map.Entry<Integer, PlayerLoopSound>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isStopped()) it.remove();
        }
    }

    private static void stopAll(Map<Integer, PlayerLoopSound> active) {
        for (PlayerLoopSound s : active.values()) {
            Minecraft.getInstance().getSoundManager().stop(s);
        }
        active.clear();
    }
}