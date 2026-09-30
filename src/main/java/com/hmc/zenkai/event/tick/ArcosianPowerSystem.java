package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * Power Points (PP) del Arcosiano: fuerza bruta contenida que se libera de golpe.
 * - Se ACUMULAN solo en Base y con el poder liberado por debajo del 50% (reposo/contención),
 *   hasta el tope del tanque, que sube con los niveles 1-4 de la skill "arcosian_power".
 * - Se APLICAN (bono de stats proporcional al tanque, ver {@link #statBonus}) y se consumen solo
 *   con el poder al 100% o más (overdrive) Y en Final Form o superior.
 * Los números de balance viven en ServerConfig (arcosian.*) y en la curva pp_max de la skill.
 */
public final class ArcosianPowerSystem {
    private ArcosianPowerSystem() {}

    public static final String SKILL = "arcosian_power";

    private static final Set<ResourceLocation> FINAL_OR_HIGHER = Set.of(
            FormIds.FINAL_FORM, FormIds.FIFTH_FORM, FormIds.GOLDEN_FORM, FormIds.BLACK_FORM,
            FormIds.GOLDEN_FIFTH_FORM, FormIds.BLACK_FIFTH_FORM);

    /** Capacidad del tanque según el nivel de la skill (0 sin la skill). */
    public static double ppMax(Player p) {
        int lvl = SkillEffects.level(p, SKILL);
        if (lvl <= 0) return 0.0;
        SkillDef def = SkillDef.get(SKILL);
        return def == null ? 0.0 : def.value("pp_max", lvl, 0.0);
    }

    private static boolean applying(PlayerStatsAttachment att, ResourceLocation formId) {
        return att.getPowerPercent() >= 100 && FINAL_OR_HIGHER.contains(formId);
    }

    /** Bono ADITIVO de stats por PP, sumado en MasteryEffects.formStatFactor. 0 si no aplica. */
    public static double statBonus(Player p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (att.getRace() != Race.ARCOSIAN) return 0.0;
        double max = ppMax(p);
        if (max <= 0.0 || att.getArcosianPp() <= 0.0) return 0.0;
        ResourceLocation form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();
        if (!applying(att, form)) return 0.0;
        return ServerConfig.arcPpStat() * Math.min(1.0, att.getArcosianPp() / max);
    }

    public static void tick(TickCtx c) {
        PlayerStatsAttachment att = c.att();
        if (att.getRace() != Race.ARCOSIAN) return;
        double max = ppMax(c.p());
        double pp = att.getArcosianPp();
        if (max <= 0.0) {
            if (pp != 0.0) att.setArcosianPp(0.0);
            return;
        }
        ResourceLocation form = c.form().getFormId();
        if (FormIds.BASE.equals(form) && att.getPowerPercent() < 50) {
            pp += ServerConfig.arcPpGain() / 20.0;
        } else if (applying(att, form) && pp > 0.0) {
            pp -= max * (ServerConfig.arcPpDrain() / 100.0) / 20.0;
        }
        pp = Math.max(0.0, Math.min(max, pp));
        if (pp != att.getArcosianPp()) att.setArcosianPp(pp);
    }
}
