package com.hmc.zenkai.client.training;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * El disco de verdad sonando durante Meditation modo Canción. Categoría RECORDS (la de un
 * jukebox, con su propio slider "Music Discs" en Opciones), NO MUSIC — así MeditationScreen
 * puede silenciar la música ambiente vanilla (categoría MUSIC) sin silenciarse a sí mismo (ver
 * MeditationScreen.startSongSession/stopSongSound, pedido explícito del usuario: "se escuchan
 * sobreescuchadas las 2"). `relative=true` + `Attenuation.NONE`: no es un jukebox físico en el
 * mundo, debe oírse igual de fuerte se mueva el jugador donde se mueva mientras el minijuego
 * está abierto.
 *
 * DUCK EN FALLO: en vez de superponer un sonido de "nota" sobre la canción real (que es
 * exactamente la queja del usuario — "que con las notas que uno toca es que suene pigstep... no
 * que suene la música como tal [aparte]"), un fallo baja el volumen brevemente en vez de sumar
 * un sonido ajeno: el propio disco "se apaga un poco" como castigo, sin clashear con su melodía.
 * getVolume() en AbstractSoundInstance lee `this.volume` en cada llamada (no lo cachea), así que
 * mutarlo en tick() basta para que el motor de sonido lo aplique en caliente.
 */
public class MeditationDiscSound extends AbstractTickableSoundInstance {

    private static final float DUCK_VOLUME_MULT = 0.35f;

    private final float baseVolume;
    private long duckUntilMs = 0L;

    public MeditationDiscSound(SoundEvent event, float baseVolume) {
        super(event, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.baseVolume = baseVolume;
        this.volume = baseVolume;
        this.pitch = 1.0f;
        this.looping = false;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
    }

    /** Baja el volumen durante `durationMs` — llamar en cada nota fallada (ver
     *  MeditationScreen.tick()). Sucesivas llamadas solapadas simplemente extienden el duck,
     *  no lo apilan (nunca queda más silencioso que DUCK_VOLUME_MULT). */
    public void duck(long durationMs) {
        duckUntilMs = Math.max(duckUntilMs, System.currentTimeMillis() + durationMs);
    }

    @Override
    public void tick() {
        this.volume = System.currentTimeMillis() < duckUntilMs ? baseVolume * DUCK_VOLUME_MULT : baseVolume;
    }
}
