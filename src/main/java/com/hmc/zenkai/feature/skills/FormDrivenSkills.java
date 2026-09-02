package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.feature.Race;
import net.minecraft.world.entity.player.Player;

/**
 * Único punto que sabe que "levels_from_forms" tiene DOS proveedores posibles: super_forms
 * (cadena de formas normal) y god_ki (cadena de formas divine_tier, ver DivineForms). Antes de
 * god_ki, "levels_from_forms" significaba siempre "pregúntale a SuperForms" y varios sitios
 * (SkillBuyPacket, ForgetSkillPacket, PlayerStatsAttachment#respec, SkillsScreen, MasterScreen)
 * lo asumían así a pelo — con un segundo proveedor real, ese despacho tiene que vivir en un
 * solo sitio en vez de repetirse en cada uno.
 */
public final class FormDrivenSkills {
    private FormDrivenSkills() {}

    public static int maxLevel(SkillDef def, Player p) {
        if (!def.levelsFromForms()) return def.maxLevel();
        return SuperForms.SKILL.equals(def.id())
                ? Math.min(def.maxLevel(), SuperForms.maxLevel(p))
                : Math.min(def.maxLevel(), DivineForms.maxLevel(p));
    }

    public static int tpCostForLevel(SkillDef def, Player p, int level) {
        if (!def.levelsFromForms()) return def.tpCost();
        return SuperForms.SKILL.equals(def.id())
                ? SuperForms.tpCostForLevel(p, level)
                : DivineForms.tpCostForLevel(p, level);
    }

    /** Variante sin Player: la usa PlayerStatsAttachment#respec(), que solo tiene la raza a
     *  mano (el attachment no guarda referencia al jugador). */
    public static int tpCostForLevel(SkillDef def, Race race, int level) {
        if (!def.levelsFromForms()) return def.tpCost();
        return SuperForms.SKILL.equals(def.id())
                ? SuperForms.tpCostForLevel(race, level)
                : DivineForms.tpCostForLevel(race, level);
    }
}
