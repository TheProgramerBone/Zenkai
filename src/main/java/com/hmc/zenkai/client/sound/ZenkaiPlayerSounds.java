package com.hmc.zenkai.client.sound;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.registry.ModSounds;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Enciende y apaga los sonidos de "cargar ki" del conjunto de jugadores visibles, no solo del
 * propio. Si solo sonara el local, un jugador cargando delante de ti sería mudo.
 * MISMO PATRÓN QUE ScouterBenchSounds y por el mismo motivo: un mapa por jugador impide
 * apilar bucles. Sin él, cada tick crearía una instancia nueva encima de la anterior y a los
 * cinco segundos habría cien copias sonando a la vez.
 * Se recorre la lista de jugadores del nivel y no un evento por jugador porque el estado
 * puede apagarse SIN evento — alguien que sale del rango de tracking, o que se desconecta
 * cargando, deja su sonido huérfano. Barrer aquí lo cubre con una regla sola.
 * Los estados que consulta ya viajan solos: isChargingKi está en el attachment (que se
 * sincroniza a los trackers), el turbo lo mantiene AuraClientState con TurboSyncPacket, y
 * isTransforming/isBase vienen de PlayerFormAttachment (sync vía SyncPlayerFormPacket, ver
 * CLAUDE.md). No hace falta ningún packet nuevo para el sonido.
 *
 * MÁQUINA DE ESTADOS (por jugador, IDLE -> INTRO -> LOOP): cargar ki (C) y empezar a
 * transformar (H sostenido) comparten el MISMO par de sonidos — KI_CHARGE_START suena una
 * vez y, cuando acaba SOLO, el bucle KI_CHARGE toma el relevo mientras la condición siga
 * activa. Turbo (R) es la excepción: salta DIRECTO al bucle (TURBO_LOOP, mismo archivo de
 * audio que KI_CHARGE pero volumen/subtítulo propios) sin pasar por el arranque. Los tres
 * estados son mutuamente excluyentes (ActionRules: TRANSFORM cancela tanto chargingKi como
 * turbo), así que nunca hace falta desempatar cuál "manda" una vez el bucle ya está sonando —
 * isLoopWorthy() vale igual de bien para decidir cuándo se apaga sea cual sea el que lo
 * encendió. DETRANSFORM es un disparo aparte, en el flanco de isBase() (no de isTransforming):
 * dropear solo el kaioken y quedarse en la forma NO cuenta como destransformación.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiPlayerSounds {
    private ZenkaiPlayerSounds() {}

    /** Bucle ya en marcha (cargar ki, transformando o turbo). */
    private static final Map<Integer, PlayerLoopSound> LOOP = new HashMap<>();
    /** Arranque de una sola vez todavía sonando. SimpleSoundInstance NO es un
     *  TickableSoundInstance — nunca se marca "stopped" sola — así que su fin se vigila con
     *  SoundManager.isActive() (¿sigue teniendo canal en el motor?), no con isStopped(). */
    private static final Map<Integer, SoundInstance> INTRO = new HashMap<>();
    /** Flanco de isBase() por jugador, para el disparo único de destransformación. */
    private static final Map<Integer, Boolean> BASE_PREV = new HashMap<>();

    private static final float KI_CHARGE_VOLUME = 0.55f;
    private static final float TURBO_VOLUME     = 0.45f;
    private static final float ONE_SHOT_VOLUME  = 0.8f;

    public static boolean isChargingKi(AbstractClientPlayer p) {
        return p.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).isChargingKi();
    }

    private static boolean isTransformHold(AbstractClientPlayer p) {
        return p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).isTransforming();
    }

    private static boolean isBaseForm(AbstractClientPlayer p) {
        return p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).isBase();
    }

    /** true si cualquiera de los tres estados que comparten el bucle está activo. */
    private static boolean isLoopWorthy(AbstractClientPlayer p) {
        return isChargingKi(p) || isTransformHold(p) || AuraClientState.isTurbo(p);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stopAllLoops();
            stopAllIntros();
            BASE_PREV.clear();
            return;
        }

        pruneLoops();

        Set<Integer> present = new HashSet<>();
        for (var entity : mc.level.players()) {
            if (!(entity instanceof AbstractClientPlayer p)) continue;
            present.add(p.getId());
            tickAuraSound(p);
            fireEdge(BASE_PREV, p, isBaseForm(p), ModSounds.DETRANSFORM.get());
        }
        INTRO.keySet().retainAll(present);
        BASE_PREV.keySet().retainAll(present);
    }

    /**
     * Un jugador está en LOOP si tiene un PlayerLoopSound vivo en el mapa, en INTRO si tiene
     * un arranque registrado, o IDLE si no tiene ninguno de los dos — leído del contenido de
     * los mapas en vez de un enum aparte, para no poder desincronizar "el estado" de "lo que
     * realmente está sonando".
     */
    private static void tickAuraSound(AbstractClientPlayer p) {
        int id = p.getId();
        PlayerLoopSound loop = LOOP.get(id);
        if (loop != null && !loop.isStopped()) return; // ya en bucle: se apaga solo (stillActive)

        Minecraft mc = Minecraft.getInstance();
        SoundInstance intro = INTRO.get(id);
        boolean turbo = AuraClientState.isTurbo(p);
        boolean active = turbo || isChargingKi(p) || isTransformHold(p);

        if (intro != null) {
            if (!active) {
                // La condición se soltó antes de que el arranque acabara solo (toque corto de
                // C, o transformación abortada): se corta a mano, no se deja terminar de sonar.
                mc.getSoundManager().stop(intro);
                INTRO.remove(id);
            } else if (!mc.getSoundManager().isActive(intro)) {
                // Acabó solo y la condición sigue viva: el bucle toma el relevo.
                INTRO.remove(id);
                startLoop(p, false);
            }
            return;
        }

        if (!active) return;
        if (turbo) {
            startLoop(p, true); // turbo: directo al bucle, sin arranque
        } else {
            SimpleSoundInstance s = new SimpleSoundInstance(ModSounds.KI_CHARGE_START.get(),
                    SoundSource.PLAYERS, ONE_SHOT_VOLUME, 1.0f,
                    SoundInstance.createUnseededRandom(), p.getX(), p.getY(), p.getZ());
            INTRO.put(id, s);
            mc.getSoundManager().play(s);
        }
    }

    private static void startLoop(AbstractClientPlayer p, boolean turbo) {
        SoundEvent event = turbo ? ModSounds.TURBO_LOOP.get() : ModSounds.KI_CHARGE.get();
        float volume = turbo ? TURBO_VOLUME : KI_CHARGE_VOLUME;
        PlayerLoopSound sound = new PlayerLoopSound(event, p, ZenkaiPlayerSounds::isLoopWorthy, volume);
        LOOP.put(p.getId(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    /** Quita del mapa los que el motor ya paró. Sin esto el mapa crece con cada jugador que
     *  haya cargado ki alguna vez en la sesión. */
    private static void pruneLoops() {
        Iterator<Map.Entry<Integer, PlayerLoopSound>> it = LOOP.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isStopped()) it.remove();
        }
    }

    private static void stopAllLoops() {
        for (PlayerLoopSound s : LOOP.values()) Minecraft.getInstance().getSoundManager().stop(s);
        LOOP.clear();
    }

    private static void stopAllIntros() {
        for (SoundInstance s : INTRO.values()) Minecraft.getInstance().getSoundManager().stop(s);
        INTRO.clear();
    }

    /**
     * Dispara un sonido de UNA sola vez cuando `state` pasa de false a true para ese jugador.
     * `getOrDefault(id, state)` es la clave: la primera vez que se ve a un jugador, "antes" se
     * inicializa al valor ACTUAL, así que nunca dispara en falso solo por acabar de entrar en
     * rango (p. ej. alguien que ya estaba en base antes de que este cliente lo cargara).
     * Posicional (SimpleSoundInstance, no PlayerLoopSound): un disparo único no necesita
     * seguir al jugador tick a tick, solo sonar donde estaba en el instante del flanco.
     */
    private static void fireEdge(Map<Integer, Boolean> prev, AbstractClientPlayer p,
                                 boolean state, SoundEvent oneShot) {
        int id = p.getId();
        boolean was = prev.getOrDefault(id, state);
        if (!was && state) {
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
                    oneShot, SoundSource.PLAYERS, ONE_SHOT_VOLUME, 1.0f,
                    SoundInstance.createUnseededRandom(), p.getX(), p.getY(), p.getZ()));
        }
        prev.put(id, state);
    }
}
