package com.hmc.zenkai.feature.mastery;

import com.hmc.zenkai.content.effect.MajinEffect;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.event.tick.ArcosianPowerSystem;
import com.hmc.zenkai.event.tick.OverlayMetersSystem;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.OverlayStateSystem;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * Efectos de la maestría (curvas LINEALES; el "at 100%" viene de StatsConfig):
 *  - Forma:   el bonus de stats y el drenaje interpolan POR FORMA entre sus valores "untrained" y
 *    "mastered" del datapack (FormDef.statPercent/kiDrainPerTick, vía PlayerFormAttachment).
 *    formDrainFactor (config mastery.form_drain_reduction) NO lo llama nadie: el drenaje real ya
 *    sale de FormDef.effectiveKiDrainPerTick. Ultra Instinct/Ultra Ego suman además efectos
 *    propios de maestría (esquive, calor, perforación) en OverlayMetersSystem.
 *  - Técnica: daño  x (1 + dmgBonus  * m/100)  ·  costo   x (1 - costRed  * m/100)
 *             cast  x (1 - castRed   * m/100)  (solo ki: reduce los ticks de carga requeridos)
 * Cada factor devuelve 1.0 en base/maestría 0, así que aplicarlos siempre es seguro.
 */
public final class MasteryEffects {
    private MasteryEffects() {}

    /** Multiplicador del drenaje de ki de la forma actual (1.0 sin maestría). */
    public static double formDrainFactor(Player p) {
        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        double m = form.getFormMastery(form.getFormId()) / 100.0;
        return Math.max(0.0, 1.0 - ServerConfig.masteryFormDrainReduction() * m);
    }

    /** ¿Lleva Beast puesta Y está forzando (powerPercent > 100)? Lo consulta también el cliente
     *  (powerPercent y formId ya viajan sincronizados) para la animación idle agresiva. */
    public static boolean isBeastOverdrive(Player p) {
        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        return com.hmc.zenkai.feature.forms.FormIds.BEAST.equals(form.getFormId())
                && PlayerStatsAttachment.get(p).getPowerPercent() > 100;
    }

    private static double beastOverdriveBonus(Player p, PlayerFormAttachment form) {
        return isBeastOverdrive(p) ? ServerConfig.beastOverdriveStatBonus() : 0.0;
    }

    /** Multiplicador de daño de una técnica (clave = nombre del tipo). */
    public static double techDamageFactor(PlayerStatsAttachment att, String key) {
        double m = att.getTechniqueMastery(key) / 100.0;
        return 1.0 + ServerConfig.masteryTechDamageBonus() * m;
    }

    /** Multiplicador del costo (ki o stamina) de una técnica. */
    public static double techCostFactor(PlayerStatsAttachment att, String key) {
        double m = att.getTechniqueMastery(key) / 100.0;
        return Math.max(0.0, 1.0 - ServerConfig.masteryTechCostReduction() * m);
    }

    /** Multiplicador de los ticks de carga requeridos (solo ki; <1 = carga más rápido). */
    public static double techCastFactor(PlayerStatsAttachment att, String key) {
        double m = att.getTechniqueMastery(key) / 100.0;
        return Math.max(0.05, 1.0 - ServerConfig.masteryTechCastReduction() * m);
    }

    /**
     * Multiplicador total de stats. ADITIVO entre capas: 1 + %forma + %kaioken + %overlays
     * genéricos (cualquier estado del mismo tipo, ver {@link OverlayStateSystem} — término
     * independiente, hoy siempre 0.0 en cualquier partida real) + PP arcosiano + overdrive de
     * Beast + barra de daño de Ultra Ego, con el bonus de Majin multiplicando encima.
     * YA NO se llama desde los sitios de combate: lo consume TickHandlers.tickForms, que lo
     * guarda en PlayerStatsAttachment.setStatMultiplier(). Así el PL, la pantalla de stats se actualiza sola.
     */
    public static double formStatFactor(Player p) {
        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        double f = 1.0 + form.totalStatPercent() + OverlayStateSystem.statPercent(p)
                + ArcosianPowerSystem.statBonus(p) + beastOverdriveBonus(p, form)
                + OverlayMetersSystem.ueStatBonus(p);
        if (MajinEffect.isActive(p)) {
            f *= 1.0 + ServerConfig.majinStatBonus();
        }
        // Fatiga por agotar el kaioken. Va aquí porque FormSystem vuelca este valor en
        // setStatMultiplier cada tick: así el castigo se propaga solo al PL, al daño y a la GUI.
        if (form.isStrained(p.level().getGameTime())) f *= 0.9;
        return f;
    }
}