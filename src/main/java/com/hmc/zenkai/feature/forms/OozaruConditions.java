package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Condiciones de Oozaru: raza saiyan + cola + luna llena visible, más MIRAR a la luna
 * (pitch de cámara) solo para el disparo desde Base. Sin estado propio y sin efectos
 * secundarios: solo lee Level/PlayerStateFlags, así que es seguro llamarla desde cliente o
 * servidor por igual.
 *
 * DOS funciones a propósito, no una: {@link #satisfied} (raza+cola+luna) es la condición
 * AMBIENTAL, la misma tanto para decidir si Oozaru puede EMPEZAR como para decidir si debe
 * seguir activo una vez transformado — OozaruSystem la usa en los dos sentidos. Mirar hacia
 * arriba, en cambio, es SOLO el gatillo de entrada: exigirlo también para MANTENERSE
 * transformado forzaría al jugador a no apartar la vista del cielo durante todo el rato que
 * sea Oozaru, lo cual no tiene sentido de juego (en canon nadie se queda mirando la luna
 * mientras pelea transformado). {@link #lookingAtMoon} se comprueba SOLO junto a
 * {@link #satisfied} en OozaruSystem, exclusivamente cuando el formId todavía es Base.
 *
 * El chequeo de mirada es DELIBERADO como "salida de emergencia" del jugador: a diferencia de
 * cola/raza/luna (fuera de su control momento a momento), el pitch de la cámara SÍ lo controla
 * él en todo instante — un saiyan que no quiera volverse Oozaru simplemente no mira hacia
 * arriba.
 */
public final class OozaruConditions {
    private OozaruConditions() {}

    /** Pitch de Minecraft: negativo = arriba, -90 = cenit exacto. No se exige el -90 exacto
     *  (apuntar clavado sería casi imposible de sostener) sino estar DENTRO de este margen del
     *  cenit. Tunable en juego. */
    private static final float LOOK_UP_PITCH_MAX = -60f;

    /** Raza + cola + luna llena visible. Ver el javadoc de la clase: NO incluye la mirada.
     *
     * Exige Overworld explícitamente: el resto de dimensiones del mod (HTC, Namek, Otherworld)
     * usan {@code fixed_time} en su {@code dimension_type}, y {@link Level#isDay()} devuelve
     * SIEMPRE {@code false} cuando {@code hasFixedTime()} es cierto (ver
     * {@code Level.isDay()}/{@code Level.isNight()} en el propio juego) — sin este chequeo de
     * dimensión, esas tres dimensiones pasaban el chequeo "no es de día" de forma permanente
     * aunque no tenga sentido que haya luna llena ahí. Peor aún: el contador de tiempo de cada
     * nivel ({@link Level#getDayTime()}) avanza en paralelo al del Overworld tick a tick
     * independientemente de {@code fixed_time} (que solo afecta el render del cielo, no el
     * contador interno), así que {@link Level#getMoonPhase()} coincidía con la fase real del
     * Overworld en Namek/Otherworld — de ahí que el ritual se pudiera activar ahí sin que haya
     * luna propia en esas dimensiones. PENDIENTE: la técnica de luna falsa de Vegeta debe poder
     * saltarse esta condición de dimensión por completo (y el resto de {@link #satisfied}) —
     * solo debe importar que el jugador la vea; no implementado todavía. */
    public static boolean satisfied(Player p) {
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(p);
        if (stats.getRace() != Race.SAIYAN) return false;
        if (!stats.hasTail()) return false;

        Level level = p.level();
        if (level.dimension() != Level.OVERWORLD) return false;
        if (level.isDay()) return false;
        if (level.getMoonPhase() != 0) return false;
        return level.canSeeSky(p.blockPosition());
    }

    /** ¿Está el jugador mirando lo bastante hacia arriba como para "ver" la luna? Solo se usa
     *  para el disparo de entrada (ver el javadoc de la clase). */
    public static boolean lookingAtMoon(Player p) {
        return p.getXRot() <= LOOK_UP_PITCH_MAX;
    }
}
