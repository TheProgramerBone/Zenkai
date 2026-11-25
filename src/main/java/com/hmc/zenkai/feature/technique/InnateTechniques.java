package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Técnicas INNATAS de raza ({@code innate_race} en el JSON de la técnica, ver {@link TechniqueDef}): la raza dueña las
 * tiene desbloqueadas desde el principio, sin pagar TP, y las demás ni las ven ni pueden comprarlas. Hoy: la
 * absorción del Majin ({@code physical/absorb.json}).
 *
 * {@link #ensure} corre una vez por segundo para cada jugador: lo desbloquea si falta (también en mundos y personajes
 * anteriores a la técnica, sin migración). NO la enlaza a ninguna casilla: eso lo decide el jugador en Técnicas.
 */
public final class InnateTechniques {
    private InnateTechniques() {}

    /** ¿Puede esta raza ver/usar la técnica? Sin raza dueña ni requerida, cualquiera (la requerida solo limita, no regala). */
    public static boolean availableTo(PhysicalTechnique t, Race race) {
        String owner = t.innateRace();
        String required = t.requiredRace();
        String mine = race == null ? "" : race.name().toLowerCase(Locale.ROOT);
        return (owner.isEmpty() || mine.equals(owner)) && (required.isEmpty() || mine.equals(required));
    }

    public static void ensure(ServerPlayer sp, PlayerStatsAttachment att) {
        if (!att.isRaceChosen()) return;
        boolean changed = false;
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            if (t.innateRace().isEmpty() || !t.enabled()) continue;
            if (availableTo(t, att.getRace()) && !att.techniques().isUnlocked(t)) {
                att.techniques().unlock(t);
                changed = true;
            }
        }
        if (changed) PlayerLifeCycle.sync(sp);
    }
}
