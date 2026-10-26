package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.feature.Race;
import net.minecraft.world.entity.player.Player;

/**
 * Único punto que sabe de dónde salen el techo y el precio de una skill "levels_from_forms"
 * (SkillBuyPacket, ForgetSkillPacket, PlayerStatsAttachment#respec, SkillsScreen, MasterScreen,
 * ModCommands pasan por aquí). Hubo DOS proveedores, super_forms y god_ki; desde F5 del skill
 * tree god_ki es de nivel 1 sin levels_from_forms y las formas divinas son nodos propios, así que
 * hoy el único proveedor es SuperForms. El despacho se queda en un solo sitio por si vuelve a
 * haber otro.
 */
public final class FormDrivenSkills {
    private FormDrivenSkills() {}

    public static int maxLevel(SkillDef def, Player p) {
        return maxLevel(def, SuperForms.raceOf(p));
    }

    /** Variante por raza: la usan el logro de "todas las habilidades" y cualquiera sin Player. */
    public static int maxLevel(SkillDef def, Race race) {
        // El Gigante es exclusivo del Namekiano pero su JSON no lleva raza: sin esto /zenkai skill give
        // (y give all) se lo daban a cualquiera, y SkillBuyPacket también lo habría vendido. Techo 0 = "N/A para su raza".
        if (SkillEffects.NAMEK_GIANT.equals(def.id()) && race != Race.NAMEKIAN) return 0;
        if (!def.levelsFromForms()) return def.maxLevel();
        return Math.min(def.maxLevel(), SuperForms.maxLevel(race));
    }

    public static int tpCostForLevel(SkillDef def, Player p, int level) {
        return tpCostForLevel(def, SuperForms.raceOf(p), level);
    }

    /** Variante sin Player: la usa PlayerStatsAttachment#respec(), que solo tiene la raza a
     *  mano (el attachment no guarda referencia al jugador). */
    public static int tpCostForLevel(SkillDef def, Race race, int level) {
        if (!def.levelsFromForms()) return def.tpCost();
        return SuperForms.tpCostForLevel(race, level);
    }

    /** ¿Tiene sentido esta skill para esa raza? Una skill derivada de formas sin ningún nivel
     *  posible no existe para ella. */
    public static boolean appliesTo(SkillDef def, Race race) {
        if (!def.levelsFromForms()) return true;
        return maxLevel(def, race) > 0;
    }
}
