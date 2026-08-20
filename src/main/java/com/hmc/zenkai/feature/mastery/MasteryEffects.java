package com.hmc.zenkai.feature.mastery;

import com.hmc.zenkai.content.effect.MajinEffect;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * Efectos de la maestría (curvas LINEALES; el "at 100%" viene de StatsConfig):
 *  - Forma:   drenaje x (1 - drainRed * m/100). El bonus de stats de la forma NO escala con
 *    maestría: vive en el datapack (zenkai_forms, totalStatPercent()) y es fijo por forma.
 *  - Técnica: daño  x (1 + dmgBonus  * m/100)  ·  costo   x (1 - costRed  * m/100)
 *             cast  x (1 - castRed   * m/100)  (solo ki: reduce los ticks de carga requeridos)
 * Todos los factores devuelven 1.0 en base/maestría 0, así que aplicarlos siempre es seguro.
 */
public final class MasteryEffects {
    private MasteryEffects() {}

    /** Multiplicador del drenaje de ki de la forma actual (1.0 sin maestría). */
    public static double formDrainFactor(Player p) {
        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        double m = form.getFormMastery(form.getFormId()) / 100.0;
        return Math.max(0.0, 1.0 - CommonConfig.masteryFormDrainReduction() * m);
    }

    /** Multiplicador de daño de una técnica (clave = nombre del tipo). */
    public static double techDamageFactor(PlayerStatsAttachment att, String key) {
        double m = att.getTechniqueMastery(key) / 100.0;
        return 1.0 + CommonConfig.masteryTechDamageBonus() * m;
    }

    /** Multiplicador del costo (ki o stamina) de una técnica. */
    public static double techCostFactor(PlayerStatsAttachment att, String key) {
        double m = att.getTechniqueMastery(key) / 100.0;
        return Math.max(0.0, 1.0 - CommonConfig.masteryTechCostReduction() * m);
    }

    /** Multiplicador de los ticks de carga requeridos (solo ki; <1 = carga más rápido). */
    public static double techCastFactor(PlayerStatsAttachment att, String key) {
        double m = att.getTechniqueMastery(key) / 100.0;
        return Math.max(0.05, 1.0 - CommonConfig.masteryTechCastReduction() * m);
    }

    /**
     * Multiplicador total de stats. ADITIVO entre capas: 1 + %forma + %kaioken, con el
     * bonus de Majin multiplicando encima.
     * YA NO se llama desde los sitios de combate: lo consume TickHandlers.tickForms, que lo
     * guarda en PlayerStatsAttachment.setStatMultiplier(). Así el PL, la pantalla de stats se actualiza sola.
     */
    public static double formStatFactor(Player p) {
        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        double f = 1.0 + form.totalStatPercent();
        if (MajinEffect.isActive(p)) {
            f *= 1.0 + CommonConfig.majinStatBonus();
        }
        // Fatiga por agotar el kaioken. Va aquí porque FormSystem vuelca este valor en
        // setStatMultiplier cada tick: así el castigo se propaga solo al PL, al daño y a la GUI.
        if (form.isStrained(p.level().getGameTime())) f *= 0.9;
        return f;
    }
}