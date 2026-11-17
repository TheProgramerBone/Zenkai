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
 * Power Points (PP) del Arcosiano: fuerza bruta contenida que se libera de golpe. Rediseñado el 2026-10-26 tras una auditoría
 * (el tamaño del tanque no daba poder) siguiendo a Dragon Block C (JRMCore: ArcRsrvGrowth / ArcRsrvPowCst / ArcosianPPDamMulti):
 * - CARGA en las formas BAJAS con el poder por debajo del 50 %, y más rápido cuanto más baja: Base 1x, Segunda 0,5x y Tercera
 *   0,25x de {@code arcosian.pp_gain_per_second}. Los niveles 1-4 de la skill "arcosian_power" agrandan el tanque.
 * - GASTO en PP ABSOLUTOS por segundo ({@code arcosian.pp_cost_per_second}) con el poder en 100 % O MÁS (el overdrive
 *   también cuenta) y desde la Forma Final: Final x1, Golden/Black x1,5, 5th x2, Golden/Black 5th x2,75. Como el gasto es
 *   absoluto, un tanque grande DURA MÁS (antes era un % del tanque y subir de nivel solo alargaba la carga).
 * - BONO de stats proporcional a la fracción llena del tanque ({@link #statBonus}), con un multiplicador por forma
 *   (Golden/Black x1,25, 5th x1,5, 5th Golden/Black x1,75, Arcosian God x{@code arcosian.god.pp_stat_mult}).
 * Mecánicas propias de las formas (lo que la propuesta B de la auditoría dejó en manos de cada forma):
 *  - Segunda y Tercera forma (contención): GANAN PP también peleando, por golpe dado y por golpe recibido, mientras el poder
 *    está por debajo del 100 % ({@link #onCombatHit}).
 *  - Arcosian God («Tanque divino»): tanque x{@code arcosian.god.pp_capacity_mult} una vez desbloqueada, gasto x
 *    {@code arcosian.god.pp_drain_mult}, bono x{@code arcosian.god.pp_stat_mult} y el tanque se REPONE con cada golpe dado.
 *  - Contención Rota (Legendario): no usa PP; al colapsar vacía el tanque (ArcosianContainment.collapse).
 * Los números de balance viven en ServerConfig (arcosian.* / arcosian.god.*) y en la curva pp_max de la skill.
 */
public final class ArcosianPowerSystem {
    private ArcosianPowerSystem() {}

    public static final String SKILL = "arcosian_power";

    private static final Set<ResourceLocation> FINAL_OR_HIGHER = Set.of(
            FormIds.FINAL_FORM, FormIds.FIFTH_FORM, FormIds.GOLDEN_FORM, FormIds.BLACK_FORM,
            FormIds.GOLDEN_FIFTH_FORM, FormIds.BLACK_FIFTH_FORM, FormIds.ARCOSIAN_GOD);

    /** Capacidad del tanque según el nivel de la skill (0 sin la skill). */
    public static double ppMax(Player p) {
        int lvl = SkillEffects.level(p, SKILL);
        if (lvl <= 0) return 0.0;
        SkillDef def = SkillDef.get(SKILL);
        double max = def == null ? 0.0 : def.value("pp_max", lvl, 0.0);
        // Arcosian God, «Tanque divino»: tener la forma DESBLOQUEADA agranda el tanque (se carga en formas bajas, así que no
        // basta con llevarla puesta).
        if (max > 0.0 && com.hmc.zenkai.feature.skills.SuperForms.unlocked(p, FormIds.ARCOSIAN_GOD)) {
            max *= ServerConfig.arcosianGodPpCapacityMult();
        }
        return max;
    }

    private static boolean wearingGod(ResourceLocation formId) {
        return FormIds.ARCOSIAN_GOD.equals(formId);
    }

    /** Poder liberado del 100 % en adelante (el overdrive, más del 100 %, también cuenta) con una forma Final o superior. */
    private static boolean applying(PlayerStatsAttachment att, ResourceLocation formId) {
        return att.getPowerPercent() >= 100 && FINAL_OR_HIGHER.contains(formId);
    }

    /** ¿Se está GASTANDO el tanque ahora mismo? Lo lee la HUD para avisar de poco tanque. */
    public static boolean draining(PlayerStatsAttachment att, ResourceLocation formId) {
        return applying(att, formId);
    }

    /** ¿Se está CARGANDO el tanque ahora mismo? (forma baja con el poder bajo el 50 %). La HUD apaga el rayo si no carga ni gasta. */
    public static boolean charging(PlayerStatsAttachment att, ResourceLocation formId) {
        return att.getPowerPercent() < 50 && chargePerSecond(formId) > 0.0;
    }

    /** PP por segundo que se CARGAN en esta forma con el poder bajo el 50 % (0 en las formas altas). */
    private static double chargePerSecond(ResourceLocation formId) {
        double base = ServerConfig.arcPpGain();
        if (FormIds.BASE.equals(formId)) return base;
        if (FormIds.SECOND_FORM.equals(formId)) return base * 0.5;
        if (FormIds.THIRD_FORM.equals(formId)) return base * 0.25;
        return 0.0;
    }

    /** Multiplicador del GASTO de cada forma sobre {@code arcosian.pp_cost_per_second} (el God, además, x pp_drain_mult). */
    private static double costMult(ResourceLocation formId) {
        if (FormIds.GOLDEN_FORM.equals(formId) || FormIds.BLACK_FORM.equals(formId)) return 1.5;
        if (FormIds.FIFTH_FORM.equals(formId)) return 2.0;
        if (FormIds.GOLDEN_FIFTH_FORM.equals(formId) || FormIds.BLACK_FIFTH_FORM.equals(formId)) return 2.75;
        if (wearingGod(formId)) return ServerConfig.arcosianGodPpDrainMult();
        return 1.0;
    }

    /** Multiplicador del BONO de stats de cada forma (las formas que más gastan, más rinden). */
    private static double bonusMult(ResourceLocation formId) {
        if (FormIds.GOLDEN_FORM.equals(formId) || FormIds.BLACK_FORM.equals(formId)) return 1.25;
        if (FormIds.FIFTH_FORM.equals(formId)) return 1.5;
        if (FormIds.GOLDEN_FIFTH_FORM.equals(formId) || FormIds.BLACK_FIFTH_FORM.equals(formId)) return 1.75;
        if (wearingGod(formId)) return ServerConfig.arcosianGodPpStatMult();
        return 1.0;
    }

    /** Bono ADITIVO de stats por PP, sumado en MasteryEffects.formStatFactor. 0 si no aplica. */
    public static double statBonus(Player p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (att.getRace() != Race.ARCOSIAN) return 0.0;
        double max = ppMax(p);
        if (max <= 0.0 || att.getArcosianPp() <= 0.0) return 0.0;
        ResourceLocation form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();
        if (!applying(att, form)) return 0.0;
        return ServerConfig.arcPpStat() * bonusMult(form) * Math.min(1.0, att.getArcosianPp() / max);
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
        if (applying(att, form)) {
            if (pp > 0.0) pp -= ServerConfig.arcPpDrain() * costMult(form) / 20.0;
        } else if (att.getPowerPercent() < 50) {
            pp += chargePerSecond(form) / 20.0;
        }
        pp = Math.max(0.0, Math.min(max, pp));
        if (pp != att.getArcosianPp()) att.setArcosianPp(pp);
    }

    /**
     * Un golpe en combate (servidor), llamado desde CombatZenkaiHooks cuando de verdad quita cuerpo. Segunda y Tercera forma
     * ganan PP por golpe dado y por golpe recibido con el poder bajo el 100 %; el Arcosian God repone PP con cada golpe dado,
     * a cualquier poder.
     */
    public static void onCombatHit(net.minecraft.server.level.ServerPlayer p, boolean dealt) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (att.getRace() != Race.ARCOSIAN) return;
        double max = ppMax(p);
        if (max <= 0.0) return;
        ResourceLocation form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();
        double gain = 0.0;
        if ((FormIds.SECOND_FORM.equals(form) || FormIds.THIRD_FORM.equals(form)) && att.getPowerPercent() < 100) {
            gain = dealt ? ServerConfig.arcPpCombatDealt() : ServerConfig.arcPpCombatTaken();
        } else if (wearingGod(form) && dealt) {
            gain = ServerConfig.arcosianGodPpRefillPerHit();
        }
        if (gain <= 0.0) return;
        att.setArcosianPp(Math.min(max, att.getArcosianPp() + gain));
    }
}
