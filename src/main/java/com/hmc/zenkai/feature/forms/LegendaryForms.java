package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skilltree.SkillTree;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Legendario de las demás razas (diseño en .claude/pendiente/skill-tree.md §1): el MISMO deseo y el mismo flag
 * {@code isLegendary} que el del Saiyan, pero cada raza recibe UNA forma propia. Esta clase reúne lo común:
 * qué forma es de qué raza, el porcentaje de stats acoplado al TP y la «resonancia divina».
 *
 * Escalado (decidido con el usuario, 2026-10-25): el Legendario crece LENTO con el TP gastado en atributos,
 * hasta un techo algo por debajo de la forma divina de su raza; con God Ki, la «resonancia» multiplica todo
 * el porcentaje y lo deja POR ENCIMA del techo divino (es un modificador de la MISMA forma, no una forma
 * nueva: el Broly canon supera a los dioses y no hay cimas post-divinas). El % no sale del JSON sino de
 * aquí, como Potential Unlock: el servidor refresca {@link #ceiling} cada tick y viaja en el sync de la forma.
 * El Saiyan (ikari / ssj_green / ssj_green_max) NO usa esto: sus números están en su JSON.
 */
public final class LegendaryForms {
    private LegendaryForms() {}

    /** Fracción del techo con maestría 0 (con maestría 100 es el techo entero). */
    public static final double MASTERY_FLOOR_FACTOR = 0.55;
    /** TP gastado de referencia de {@code legendary.growth_percent_at_5000_tp}. */
    private static final double REF_TP = 5000.0;

    /** Raza dueña de una forma de la línea Legendaria, o null si no pertenece a ella. */
    @Nullable
    public static Race ownerOf(@Nullable ResourceLocation form) {
        if (form == null) return null;
        if (FormIds.IKARI.equals(form) || FormIds.SSJ_GREEN.equals(form) || FormIds.SSJ_GREEN_MAX.equals(form)) {
            return Race.SAIYAN;
        }
        if (FormIds.HUMAN_LEGENDARY.equals(form)) return Race.HUMAN;
        if (FormIds.NAMEK_LEGENDARY.equals(form)) return Race.NAMEKIAN;
        if (FormIds.ARCOSIAN_LEGENDARY.equals(form)) return Race.ARCOSIAN;
        if (FormIds.MAJIN_LEGENDARY.equals(form)) return Race.MAJIN;
        return null;
    }

    /** ¿Es una de las cuatro formas Legendarias de las razas NO saiyan (las del % acoplado al TP)? */
    public static boolean isScaled(@Nullable ResourceLocation form) {
        Race r = ownerOf(form);
        return r != null && r != Race.SAIYAN;
    }

    /** Forma Legendaria de una raza que no es Saiyan (la del Saiyan es una cadena), o null. */
    @Nullable
    public static ResourceLocation formOf(@Nullable Race race) {
        if (race == null) return null;
        return switch (race) {
            case HUMAN -> FormIds.HUMAN_LEGENDARY;
            case NAMEKIAN -> FormIds.NAMEK_LEGENDARY;
            case ARCOSIAN -> FormIds.ARCOSIAN_LEGENDARY;
            case MAJIN -> FormIds.MAJIN_LEGENDARY;
            default -> null;
        };
    }

    /** Formas Legendarias sin nodo propio en el árbol (la Contención Rota del Arcosiano es una 2ª transformación). */
    public static boolean hiddenNode(@Nullable ResourceLocation form) {
        return FormIds.ARCOSIAN_LEGENDARY.equals(form);
    }

    /** Porcentaje a maestría 100 para un TP gastado y si hay resonancia divina (la fórmula, sin Player). */
    public static double ceilingFor(double tpSpent, boolean godKi) {
        double growth = ServerConfig.legendaryGrowthPercent() * Math.sqrt(Math.max(0.0, tpSpent) / REF_TP);
        double pct = Math.min(growth, ServerConfig.legendaryCeilingPercent());
        return godKi ? pct * ServerConfig.legendaryResonanceMult() : pct;
    }

    /** Techo actual de este jugador (lo refresca el servidor cada tick). */
    public static double ceiling(@Nullable Player p) {
        if (p == null) return 0.0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        // Contención Rota: salto FIJO y grande (no crece con el TP); solo la resonancia divina lo multiplica.
        if (att.getRace() == Race.ARCOSIAN) {
            double fixed = ServerConfig.arcosianLegendPercent();
            return SkillTree.hasGodKi(p) ? fixed * ServerConfig.legendaryResonanceMult() : fixed;
        }
        return ceilingFor(att.raceStats().getTpSpent(), SkillTree.hasGodKi(p));
    }

    /** Fracción que suma la forma, a partir del techo ya resuelto y de SU maestría (0-100). */
    public static double statPercent(double ceiling, float mastery0to100) {
        double t = Math.max(0f, Math.min(100f, mastery0to100)) / 100.0;
        return ceiling * (MASTERY_FLOOR_FACTOR + (1.0 - MASTERY_FLOOR_FACTOR) * t);
    }
}
