package com.hmc.zenkai.core.training;

import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.worldgen.ModDimensions;
import net.minecraft.server.level.ServerPlayer;

/**
 * Métodos de entrenamiento TP (v1.0: combate).
 *
 * Fuentes:
 *  - Daño EFECTIVO infligido (post-defensa, capado por el pool restante de la víctima ->
 *    sin exploit de overkill). Gancho: CombatZenkaiHooks.onDamage. Sparring cuenta: no
 *    hace falta matar. TP_raw = daño × training.damage_tp_factor.
 *  - Golpes al aire (mano vacía): cuesta stamina (% del máx) y tiene rate-limit servidor.
 *    TP_raw = PL propio × training.air_tp_factor. El grind seguro del novato.
 *
 * Rendimiento decreciente por sesión: eficiencia m = H / (H + fatiga), fatiga = TP ganado
 * entrenando normalizado por tu PL. Decae con el tiempo real de juego (lazy decay al ganar,
 * sin tick handler): recuperas training.fatigue_decay_per_minute por minuto. Piso
 * training.min_efficiency para que nunca sea 0 exacto.
 *
 * HTC: entrenar DENTRO de la Habitación del Tiempo multiplica el TP otorgado por
 * training.htc_multiplier, pero la fatiga acumula la cantidad BASE (pre-HTC) -> la Habitación
 * duplica el rendimiento de la sesión completa, no solo la velocidad.
 *
 * Balance simulado (defaults, PL 10K, sparring vs brawler igual a 40 golpes/min):
 *  10 min ≈ 40% del PL en TP (kills de iguales ≈ 67%: matar sigue siendo lo más rápido);
 *  con HTC ≈ 80%. Golpes al aire ≈ 18% del PL en 30 min.
 */
public final class TrainingHooks {
    private TrainingHooks() {}

    /** TP por daño efectivo infligido a otra entidad. Llamar SOLO en servidor. */
    public static void grantFromDamage(ServerPlayer sp, double effectiveDamage) {
        if (effectiveDamage <= 0) return;
        grant(sp, effectiveDamage * StatsConfig.trainingDamageTpFactor());
    }

    /** Golpe al aire con mano vacía (TrainingSwingPacket). Valida cooldown + stamina. */
    public static void grantFromSwing(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return;

        TrainingData td = sp.getData(DataAttachments.TRAINING.get());
        long now = sp.level().getGameTime();
        if (now - td.getLastSwingTime() < StatsConfig.trainingAirMinTicks()) return;

        int cost = (int) Math.ceil(att.getStaminaMax() * StatsConfig.trainingAirStaminaCostPct());
        if (att.getStamina() < cost) return;
        att.consumeStamina(cost);
        td.setLastSwingTime(now);

        grant(sp, Math.max(1, att.getPowerLevel()) * StatsConfig.trainingAirTpFactor());
    }

    /** Núcleo: aplica decay de fatiga, eficiencia, multiplicador HTC, carry fraccional y sync. */
    private static void grant(ServerPlayer sp, double rawTp) {
        if (rawTp <= 0) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return;
        long pl = Math.max(1, att.getPowerLevel());

        TrainingData td = sp.getData(DataAttachments.TRAINING.get());
        long now = sp.level().getGameTime();

        // Lazy decay: minutos de juego desde el último evento.
        if (td.getLastDecayTime() > 0 && now > td.getLastDecayTime()) {
            double minutes = (now - td.getLastDecayTime()) / 1200.0;
            td.setFatigue(td.getFatigue() - StatsConfig.trainingFatigueDecayPerMinute() * minutes);
        }
        td.setLastDecayTime(now);

        double h = StatsConfig.trainingFatigueHalfLife();
        double m = Math.max(StatsConfig.trainingMinEfficiency(), h / (h + td.getFatigue()));

        double base = rawTp * m;
        boolean inHtc = sp.level().dimension() == ModDimensions.HTC_LEVEL;
        double granted = base * (inHtc ? StatsConfig.trainingHtcMultiplier() : 1.0);

        double total = granted + td.getCarry();
        int whole = (int) Math.floor(total);
        td.setCarry(total - whole);
        td.setFatigue(td.getFatigue() + base / pl); // fatiga sobre la cantidad BASE (pre-HTC)

        if (whole > 0) {
            att.addTP(whole);
            PlayerLifeCycle.syncIfServer(sp);
        }
    }
}