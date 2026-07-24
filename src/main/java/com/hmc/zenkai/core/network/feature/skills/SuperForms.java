package com.hmc.zenkai.core.skills;

import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.forms.FormDef;
import com.hmc.zenkai.core.network.feature.forms.FormIds;
import com.hmc.zenkai.core.network.feature.forms.FormRegistry;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Habilidad "super_forms": desbloquea las transformaciones de TU raza, una por nivel.
 *
 * Nada de esto está en el JSON de la skill, y es a propósito: el nivel máximo y el coste de
 * cada nivel se DERIVAN de la cadena de formas del datapack. Consecuencias:
 *  - Un saiyan ve 5 niveles (1 + 4 formas) y un arcosiano 6 (1 + 5). Nadie compra un nivel
 *    que no le da nada, que era justo el problema de tener un max_level fijo.
 *  - El coste de cada nivel es el tp_cost de la forma que desbloquea: los números viven en
 *    UN sitio (el JSON de la forma) y no pueden desincronizarse.
 *  - Añadir una forma al datapack alarga la habilidad sola, sin tocar Java ni la skill.
 *
 * Mapa de niveles:
 *   nivel 1 -> BASE. REGALADO (0 TP): se otorga al elegir raza y sobrevive al respec, así que
 *              el jugador SIEMPRE tiene la habilidad. No desbloquea ninguna transformación.
 *   nivel 2 -> 1ª forma · nivel 3 -> 2ª forma · ... · nivel N -> forma N-1.
 *
 * La API base va por Race (no por Player) porque el respec la necesita desde el attachment,
 * que no tiene referencia al jugador.
 */
public final class SuperForms {
    private SuperForms() {}

    public static final String SKILL = "super_forms";

    // ── API por raza ─────────────────────────────────────────────────────────

    /** Cadena de formas de esa raza, en orden. Vacía si la raza no transforma. */
    public static List<ResourceLocation> chain(Race race) {
        return FormRegistry.chainFor(race);
    }

    /** Niveles que la habilidad puede llegar a tener para esa raza (1 + nº de formas). */
    public static int maxLevel(Race race) {
        return 1 + chain(race).size();
    }

    /**
     * Coste en TP de comprar ese nivel. El nivel 1 es gratis (es la base); del 2 en adelante,
     * el tp_cost de la forma que desbloquea. MAX_VALUE si ese nivel no existe para esta raza,
     * de modo que nunca sea comprable ni reembolsable.
     */
    public static int tpCostForLevel(Race race, int level) {
        if (level <= 1) return 0;
        List<ResourceLocation> c = chain(race);
        int i = level - 2;
        if (i < 0 || i >= c.size()) return Integer.MAX_VALUE;
        FormDef fd = FormDef.get(c.get(i));
        return fd == null ? Integer.MAX_VALUE : fd.tpCost();
    }

    /** Posición (1-based) de una forma en la cadena de esa raza. 0 si no pertenece. */
    public static int depthOf(Race race, ResourceLocation form) {
        if (race == null || form == null || FormIds.BASE.equals(form)) return 0;
        List<ResourceLocation> c = chain(race);
        for (int i = 0; i < c.size(); i++) {
            if (c.get(i).equals(form)) return i + 1;
        }
        return 0;
    }

    /** Nivel de habilidad necesario para usar esa forma (profundidad + 1, por el nivel base). */
    public static int requiredLevel(Race race, ResourceLocation form) {
        int d = depthOf(race, form);
        return d <= 0 ? Integer.MAX_VALUE : d + 1;
    }

    // ── API por jugador ──────────────────────────────────────────────────────

    /** Nivel actual. 0 solo si aún no se le ha otorgado el nivel base. */
    public static int level(Player p) {
        return SkillEffects.level(p, SKILL);
    }

    public static Race raceOf(Player p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        return att == null ? null : att.getRace();
    }

    public static int maxLevel(Player p) {
        return maxLevel(raceOf(p));
    }

    public static int tpCostForLevel(Player p, int level) {
        return tpCostForLevel(raceOf(p), level);
    }

    /** ¿Este jugador tiene desbloqueada esa forma? BASE siempre sí. */
    public static boolean unlocked(Player p, ResourceLocation form) {
        if (form == null || FormIds.BASE.equals(form)) return true;
        return level(p) >= requiredLevel(raceOf(p), form);
    }
}