package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.feature.Race;
import net.minecraft.world.entity.player.Player;

/**
 * Único punto que sabe cuál es el techo y el precio de una skill PARA UNA RAZA (SkillBuyPacket, ForgetSkillPacket,
 * SkillsTreeScreen, MasterScreen, SkillCommands, el logro de «todas las habilidades» pasan por aquí). Hubo skills cuyo techo
 * salía de la cadena de formas de la raza ({@code levels_from_forms}: super_forms y god_ki); con la 0.7.0 todas las formas son
 * nodos propios del árbol y esa mecánica desapareció: hoy solo queda la excepción de las skills exclusivas de una raza.
 */
public final class FormDrivenSkills {
    private FormDrivenSkills() {}

    public static int maxLevel(SkillDef def, Player p) {
        return maxLevel(def, com.hmc.zenkai.feature.player.PlayerStatsAttachment.get(p).getRace());
    }

    /** Variante por raza: la usan el logro de "todas las habilidades" y cualquiera sin Player. */
    public static int maxLevel(SkillDef def, Race race) {
        // El Gigante es exclusivo del Namekiano pero su JSON no lleva raza: sin esto /zenkai skill give
        // (y give all) se lo daban a cualquiera, y SkillBuyPacket también lo habría vendido. Techo 0 = "N/A para su raza".
        if (SkillEffects.NAMEK_GIANT.equals(def.id()) && race != Race.NAMEKIAN) return 0;
        return def.maxLevel();
    }

    public static int tpCostForLevel(SkillDef def, Player p, int level) {
        return def.tpCost();
    }

    public static int tpCostForLevel(SkillDef def, Race race, int level) {
        return def.tpCost();
    }

    /** ¿Tiene sentido esta skill para esa raza? Techo 0 = no existe para ella. */
    public static boolean appliesTo(SkillDef def, Race race) {
        return maxLevel(def, race) > 0;
    }
}
