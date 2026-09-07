package com.hmc.zenkai.client.training;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Set curado de discos vanilla para Meditation modo Canción — pedido explícito del usuario
 * ("un selector de canciones... por ejemplo Pigstep, que sea una de las difíciles"). La
 * dificultad es una etiqueta CURADA a mano (decisión de producto), no directamente el
 * `difficultyHint` que calcula tools/gen_meditation_chart.py por densidad de notas — ese hint es
 * solo una señal de partida, esto es lo que de verdad se le enseña al jugador tras jugarlo.
 *
 * Añadir un disco nuevo: 1) generar su chart con
 * `python tools/gen_meditation_chart.py &lt;id&gt;`, 2) jugarlo y afinar a mano el JSON
 * resultante, 3) añadir una entrada aquí. El selector de MeditationScreen solo enseña las
 * entradas cuyo chart YA existe (ver MeditationChartLoader) — añadir aquí sin generar el JSON no
 * rompe nada, simplemente esa fila no aparece.
 */
public enum CuratedSong {
    MELLOHI("mellohi", Items.MUSIC_DISC_MELLOHI, SoundEvents.MUSIC_DISC_MELLOHI, Difficulty.EASY),
    CAT("cat", Items.MUSIC_DISC_CAT, SoundEvents.MUSIC_DISC_CAT, Difficulty.MEDIUM),
    PIGSTEP("pigstep", Items.MUSIC_DISC_PIGSTEP, SoundEvents.MUSIC_DISC_PIGSTEP, Difficulty.HARD),
    ;

    public enum Difficulty {
        EASY("screen.zenkai.meditation.song.difficulty.easy"),
        MEDIUM("screen.zenkai.meditation.song.difficulty.medium"),
        HARD("screen.zenkai.meditation.song.difficulty.hard");

        public final String translationKey;
        Difficulty(String key) { this.translationKey = key; }
    }

    public final String discId;
    public final Item item;
    public final Holder<SoundEvent> sound;
    public final Difficulty difficulty;

    CuratedSong(String discId, Item item, Holder<SoundEvent> sound, Difficulty difficulty) {
        this.discId = discId;
        this.item = item;
        this.sound = sound;
        this.difficulty = difficulty;
    }
}
