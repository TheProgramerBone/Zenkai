package com.hmc.zenkai.client.training;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Set curado de discos vanilla para Meditation modo Canción — pedido explícito del usuario
 * ("un selector de canciones... por ejemplo Pigstep, que sea una de las difíciles", luego
 * "agregar más canciones... al menos todos los discos"). Los 19 discos de música vanilla de la
 * versión pinneada (1.21.1) están aquí, pero la CURACIÓN real (Difficulty ya jugada/decidida a
 * mano, no el hint automático) solo cubre los 3 primeros por ahora — ver el comentario sobre
 * cada grupo más abajo.
 *
 * Nombres de constante SIN dígito inicial ("13"→THIRTEEN, "11"→ELEVEN, "5"→FIVE): Java no permite
 * un identificador que empiece por número, aunque `discId` (el string real, usado para el
 * nombre de archivo del chart y la clave de traducción) sí sea literalmente "13"/"11"/"5".
 *
 * Añadir un disco nuevo a mano (1 en 1): 1) generar su chart con
 * `python tools/gen_meditation_chart.py &lt;id&gt;` (o los 19 de golpe con
 * `tools/gen_all_meditation_charts.py`), 2) jugarlo y afinar a mano el JSON resultante, 3) añadir/
 * ajustar la entrada aquí. El selector de MeditationScreen solo enseña las entradas cuyo chart YA
 * existe (ver MeditationChartLoader) — añadir aquí sin generar el JSON no rompe nada, simplemente
 * esa fila no aparece.
 */
public enum CuratedSong {
    // Curadas a mano de verdad (jugadas antes de fijar su Difficulty) — no tocar sin volver a
    // jugarlas.
    MELLOHI("mellohi", Items.MUSIC_DISC_MELLOHI, SoundEvents.MUSIC_DISC_MELLOHI, Difficulty.EASY),
    CAT("cat", Items.MUSIC_DISC_CAT, SoundEvents.MUSIC_DISC_CAT, Difficulty.MEDIUM),
    PIGSTEP("pigstep", Items.MUSIC_DISC_PIGSTEP, SoundEvents.MUSIC_DISC_PIGSTEP, Difficulty.HARD),

    // Resto de los 19 discos vanilla de 1.21.1, añadidos de golpe con
    // tools/gen_all_meditation_charts.py (pedido explícito del usuario: "agregar más canciones...
    // al menos todos los discos"). Difficulty = el `difficultyHint` AUTOMÁTICO de cada chart
    // (notas/segundo), NO curado a mano todavía — a diferencia de las 3 de arriba, ninguna de
    // estas se ha jugado ni una vez. Pendiente explícito: jugar las 16 y reclasificar/afinar
    // t/lane como ya se hizo con Pigstep (que el hint automático clasificó "medium" pese a ser la
    // más difícil de las 3 curadas a mano — el hint es solo una señal de partida, ver la nota de
    // clase).
    THIRTEEN("13", Items.MUSIC_DISC_13, SoundEvents.MUSIC_DISC_13, Difficulty.MEDIUM),
    BLOCKS("blocks", Items.MUSIC_DISC_BLOCKS, SoundEvents.MUSIC_DISC_BLOCKS, Difficulty.EASY),
    CHIRP("chirp", Items.MUSIC_DISC_CHIRP, SoundEvents.MUSIC_DISC_CHIRP, Difficulty.MEDIUM),
    FAR("far", Items.MUSIC_DISC_FAR, SoundEvents.MUSIC_DISC_FAR, Difficulty.MEDIUM),
    MALL("mall", Items.MUSIC_DISC_MALL, SoundEvents.MUSIC_DISC_MALL, Difficulty.MEDIUM),
    STAL("stal", Items.MUSIC_DISC_STAL, SoundEvents.MUSIC_DISC_STAL, Difficulty.MEDIUM),
    STRAD("strad", Items.MUSIC_DISC_STRAD, SoundEvents.MUSIC_DISC_STRAD, Difficulty.MEDIUM),
    WARD("ward", Items.MUSIC_DISC_WARD, SoundEvents.MUSIC_DISC_WARD, Difficulty.MEDIUM),
    ELEVEN("11", Items.MUSIC_DISC_11, SoundEvents.MUSIC_DISC_11, Difficulty.MEDIUM),
    WAIT("wait", Items.MUSIC_DISC_WAIT, SoundEvents.MUSIC_DISC_WAIT, Difficulty.MEDIUM),
    OTHERSIDE("otherside", Items.MUSIC_DISC_OTHERSIDE, SoundEvents.MUSIC_DISC_OTHERSIDE, Difficulty.MEDIUM),
    FIVE("5", Items.MUSIC_DISC_5, SoundEvents.MUSIC_DISC_5, Difficulty.EASY),
    RELIC("relic", Items.MUSIC_DISC_RELIC, SoundEvents.MUSIC_DISC_RELIC, Difficulty.MEDIUM),
    CREATOR("creator", Items.MUSIC_DISC_CREATOR, SoundEvents.MUSIC_DISC_CREATOR, Difficulty.MEDIUM),
    CREATOR_MUSIC_BOX("creator_music_box", Items.MUSIC_DISC_CREATOR_MUSIC_BOX,
            SoundEvents.MUSIC_DISC_CREATOR_MUSIC_BOX, Difficulty.MEDIUM),
    PRECIPICE("precipice", Items.MUSIC_DISC_PRECIPICE, SoundEvents.MUSIC_DISC_PRECIPICE, Difficulty.MEDIUM),
    ;

    /** Prefijo de la clave de traducción del nombre visible de cada disco (ver {@link #nameKey()}).
     *  Hace falta esto y no simplemente `stack.getHoverName()` porque TODOS los discos vanilla
     *  comparten el mismo nombre de ítem genérico "Music Disc" — el nombre real de la canción solo
     *  vive como línea de descripción/tooltip ("C418 - mellohi"), nunca como el nombre del ítem.
     *  Sin esto, las tres filas del selector se verían idénticas salvo por el ícono y la
     *  etiqueta de dificultad (queja explícita del usuario). */
    private static final String NAME_KEY_PREFIX = "screen.zenkai.meditation.song.";

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

    /** Clave de traducción del nombre real de la canción (ej. "Pigstep"), NO el nombre del ítem —
     *  ver el porqué en el comentario de {@link #NAME_KEY_PREFIX}. */
    public String nameKey() {
        return NAME_KEY_PREFIX + discId + ".name";
    }
}
