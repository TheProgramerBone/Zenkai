package com.hmc.zenkai.core.mastery;

import com.hmc.zenkai.content.effect.MajinEffect;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.forms.FormIds;
import com.hmc.zenkai.core.network.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * Efectos de la maestría (curvas LINEALES; el "at 100%" viene de StatsConfig):
 *  - Forma:   stats x (1 + statBonus * m/100)  ·  drenaje x (1 - drainRed * m/100)
 *  - Técnica: daño  x (1 + dmgBonus  * m/100)  ·  costo   x (1 - costRed  * m/100)
 *             cast  x (1 - castRed   * m/100)  (solo ki: reduce los ticks de carga requeridos)
 * Todos los factores devuelven 1.0 en base/maestría 0, así que aplicarlos siempre es seguro.
 */
public final class MasteryEffects {
    private MasteryEffects() {}

    /**
     * Multiplicador GLOBAL de stats de combate: maestría de la forma actual (1.0 en base)
     * x boost de la marca Majin (si el jugador está bajo el efecto). Todos los puntos de
     * combate lo aplican, así que ambos efectos entran por aquí.
     */
    public static double formStatFactor(Player p) {
        PlayerFormAttachment form = p.getData(DataAttachments.PLAYER_FORM.get());
        double f = 1.0;
        if (!FormIds.BASE.equals(form.getFormId())) {
            double m = form.getFormMastery(form.getFormId()) / 100.0;
            f *= 1.0 + StatsConfig.masteryFormStatBonus() * m;
        }
        if (MajinEffect.isActive(p)) {
            f *= 1.0 + StatsConfig.majinStatBonus();
        }
        return f;
    }

    /** Multiplicador del drenaje de ki de la forma actual (1.0 sin maestría). */
    public static double formDrainFactor(Player p) {
        PlayerFormAttachment form = p.getData(DataAttachments.PLAYER_FORM.get());
        double m = form.getFormMastery(form.getFormId()) / 100.0;
        return Math.max(0.0, 1.0 - StatsConfig.masteryFormDrainReduction() * m);
    }

    /** Multiplicador de daño de una técnica (clave = nombre del tipo). */
    public static double techDamageFactor(PlayerStatsAttachment att, String key) {
        double m = att.getTechniqueMastery(key) / 100.0;
        return 1.0 + StatsConfig.masteryTechDamageBonus() * m;
    }

    /** Multiplicador del costo (ki o stamina) de una técnica. */
    public static double techCostFactor(PlayerStatsAttachment att, String key) {
        double m = att.getTechniqueMastery(key) / 100.0;
        return Math.max(0.0, 1.0 - StatsConfig.masteryTechCostReduction() * m);
    }

    /** Multiplicador de los ticks de carga requeridos (solo ki; <1 = carga más rápido). */
    public static double techCastFactor(PlayerStatsAttachment att, String key) {
        double m = att.getTechniqueMastery(key) / 100.0;
        return Math.max(0.05, 1.0 - StatsConfig.masteryTechCastReduction() * m);
    }
}