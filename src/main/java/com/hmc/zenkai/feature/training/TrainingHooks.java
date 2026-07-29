package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.registry.ModDimensions;
import net.minecraft.server.level.ServerPlayer;

/**
 * Métodos de entrenamiento TP (v1.0: combate).
 *
 * Fuentes (las TRES pasan por grant(), así que fatiga, HTC y pesas se aplican una sola vez
 * y en un solo sitio):
 *  - Daño EFECTIVO infligido (post-defensa, capado por el pool restante de la víctima ->
 *    sin exploit de overkill). Gancho: CombatZenkaiHooks.onDamage. Sparring cuenta.
 *    TP_raw = daño × training.damage_tp_factor.
 *  - Golpes al aire (mano vacía): cuesta stamina (% del máx) y tiene rate-limit servidor.
 *    TP_raw = PL LIMPIO × training.air_tp_factor.
 *  - Matar entidades: EntityDeathRewardHandler entrega su reward aquí en vez de llamar
 *    addTP() a pelo, así los kills también rinden más con pesas y menos con fatiga.
 *
 * Rendimiento decreciente por sesión: eficiencia m = H / (H + fatiga), fatiga = TP ganado
 * entrenando normalizado por tu PL. Decae con el tiempo real de juego (lazy decay al ganar,
 * sin tick handler). Piso training.min_efficiency para que nunca sea 0 exacto.
 *
 * ORDEN DE MULTIPLICADORES: base = raw × eficiencia; luego HTC y pesas MULTIPLICAN encima
 * (se acumulan: HTC ×2 con pesas ×2,5 = ×5). La fatiga acumula la cantidad BASE, o sea
 * pre-HTC y pre-pesas -> las dos mejoran el rendimiento de la SESIÓN entera, no solo su
 * velocidad instantánea.
 *
 * El PL que se usa aquí es SIEMPRE el limpio (getPowerLevelRaw): si fuera el penalizado,
 * ponerte pesas bajaría el TP de los golpes al aire y se comería el bono que acabas de ganar.
 */
public final class TrainingHooks {
    private TrainingHooks() {}

    /** TP por daño efectivo infligido a otra entidad. Llamar SOLO en servidor. */
    public static void grantFromDamage(ServerPlayer sp, double effectiveDamage) {
        if (effectiveDamage <= 0) return;
        grant(sp, effectiveDamage * CommonConfig.trainingDamageTpFactor());
    }

    /** TP por matar una entidad. El reward ya viene resuelto por EntityDeathRewardHandler. */
    public static void grantFromKill(ServerPlayer sp, int reward) {
        if (reward <= 0) return;
        grant(sp, reward);
    }

    /** Golpe al aire con mano vacía (TrainingSwingPacket). Valida cooldown + stamina. */
    public static void grantFromSwing(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return;

        TrainingData td = sp.getData(ZenkaiDataAttachments.TRAINING.get());
        long now = sp.level().getGameTime();
        if (now - td.getLastSwingTime() < CommonConfig.trainingAirMinTicks()) return;

        int cost = (int) Math.ceil(att.getStaminaMax() * CommonConfig.trainingAirStaminaCostPct());
        if (att.getStamina() < cost) return;
        att.consumeStamina(cost);
        td.setLastSwingTime(now);

        grant(sp, Math.max(1, att.getPowerLevelRaw()) * CommonConfig.trainingAirTpFactor());
    }

    /** Núcleo: decay de fatiga, eficiencia, HTC, pesas, carry fraccional y sync. */
    private static void grant(ServerPlayer sp, double rawTp) {
        if (rawTp <= 0) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return;
        long pl = Math.max(1, att.getPowerLevelRaw());

        TrainingData td = sp.getData(ZenkaiDataAttachments.TRAINING.get());
        long now = sp.level().getGameTime();

        // Lazy decay: minutos de juego desde el último evento.
        if (td.getLastDecayTime() > 0 && now > td.getLastDecayTime()) {
            double minutes = (now - td.getLastDecayTime()) / 1200.0;
            td.setFatigue(td.getFatigue() - CommonConfig.trainingFatigueDecayPerMinute() * minutes);
        }
        td.setLastDecayTime(now);

        double h = CommonConfig.trainingFatigueHalfLife();
        double m = Math.max(CommonConfig.trainingMinEfficiency(), h / (h + td.getFatigue()));

        double base = rawTp * m;
        boolean inHtc = sp.level().dimension() == ModDimensions.HTC_LEVEL;
        double granted = base
                * (inHtc ? CommonConfig.trainingHtcMultiplier() : 1.0)
                * WeightSystem.tpFactor(att.getWeightLoad());

        double total = granted + td.getCarry();
        int whole = (int) Math.floor(total);
        td.setCarry(total - whole);
        td.setFatigue(td.getFatigue() + base / pl); // fatiga sobre la cantidad BASE

        if (whole > 0) {
            att.addTP(whole);
            PlayerLifeCycle.syncIfServer(sp);
        }
    }
}