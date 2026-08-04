package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.registry.ModDimensions;
import net.minecraft.server.level.ServerPlayer;

/**
 * Métodos de entrenamiento TP (v1.0: combate).
 * Fuentes (las TRES pasan por grant(), así que fatiga, HTC y pesas se aplican una sola vez
 * y en un solo sitio):
 *  - Daño EFECTIVO infligido (post-defensa, capado por el pool restante de la víctima ->
 *    sin exploit de overkill). Gancho: CombatZenkaiHooks.onDamage. Sparring cuenta.
 *    TP_raw = daño × training.damage_tp_factor.
 *  - Golpes al aire (mano vacía): cuesta stamina (% del máx) y tiene rate-limit servidor.
 *    TP_raw = PL LIMPIO × training.air_tp_factor.
 *  - Matar entidades: EntityDeathRewardHandler entrega su reward aquí en vez de llamar
 *    addTP() a pelo, así los kills también rinden más con pesas y menos con fatiga.
 * Rendimiento decreciente por sesión: eficiencia m = H / (H + fatiga), fatiga = TP ganado
 * entrenando normalizado por tu PL. Decae con el tiempo real de juego (lazy decay al ganar,
 * sin tick handler). Piso training.min_efficiency para que nunca sea 0 exacto.
 * DIFERENCIA DE PODER: encima de la eficiencia, lo que ganas se escala por cuánto se te
 * parece el rival. Sin esto el reward escalaba con el PL de la víctima pero no con la
 * DISTANCIA, así que a PL 50.000 seguías cobrando lo mismo por un zombi — y como la fatiga
 * se normaliza por tu PL, ser fuerte hacía que farmear chusma fatigase MENOS. El incentivo
 * estaba literalmente invertido.
 * ORDEN DE MULTIPLICADORES: base = raw × eficiencia × diferencia de poder; luego HTC y pesas
 * MULTIPLICAN encima (se acumulan: HTC ×2 con pesas ×2,5 = ×5). La fatiga acumula la cantidad
 * BASE, o sea pre-HTC y pre-pesas -> las dos mejoran el rendimiento de la SESIÓN entera, no
 * solo su velocidad instantánea.
 * El PL que se usa aquí es SIEMPRE el limpio (getPowerLevelRaw): si fuera el penalizado,
 * ponerte pesas bajaría el TP de los golpes al aire y se comería el bono que acabas de ganar.
 */
public final class TrainingHooks {
    private TrainingHooks() {}

    /** TP por daño efectivo infligido a otra entidad. Llamar SOLO en servidor.
     *  @param victimPl PL de la víctima, para el factor de diferencia de poder. */
    public static void grantFromDamage(ServerPlayer sp, double effectiveDamage, long victimPl) {
        if (effectiveDamage <= 0) return;
        grant(sp, effectiveDamage * CommonConfig.trainingDamageTpFactor(), victimPl);
    }

    /** TP por matar una entidad. El reward ya viene resuelto por EntityDeathRewardHandler.
     *  @param victimPl PL de la víctima, para el factor de diferencia de poder. */
    public static void grantFromKill(ServerPlayer sp, int reward, long victimPl) {
        if (reward <= 0) return;
        grant(sp, reward, victimPl);
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

        // victimPl 0 = sin objetivo. Golpear al aire no se castiga por diferencia de poder:
        // ya está limitado por estamina y por el cooldown del packet.
        grant(sp, Math.max(1, att.getPowerLevelRaw()) * CommonConfig.trainingAirTpFactor(), 0L);
    }

    /** Núcleo: decay de fatiga, eficiencia, diferencia de poder, HTC, pesas, carry y sync. */
    private static void grant(ServerPlayer sp, double rawTp, long victimPl) {
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

        // Diferencia de poder. El divisor NO es 1.0: el PL del jugador y el de un mob no están
        // en la misma escala (el suyo es 31-65% pool de ki, el del mob un 10%), así que un
        // combate igualado da ratio ~0.25 y no 1. Sin el divisor, un zombi calibrado para
        // matarte en 8 golpes contaba como chusma desde el minuto uno.
        double plFactor = 1.0;
        if (victimPl > 0) {
            double ratio = (victimPl / (double) pl) / CommonConfig.trainingPlRatioFull();
            plFactor = Math.max(CommonConfig.trainingPlRatioFloor(), Math.min(1.0, ratio));
        }

        double base = rawTp * m * plFactor;
        boolean inHtc = sp.level().dimension() == ModDimensions.HTC_LEVEL;
        double granted = base
                * (inHtc ? CommonConfig.trainingHtcMultiplier() : 1.0)
                * WeightSystem.tpFactor(att.getWeightLoad());

        // Potential Unlock: estás USANDO tu potencial, no entrenándolo. Se comprueba contra
        // la forma ACTIVA, nunca contra tener la habilidad: llevarla comprada no debe costar
        // nada. Se aplica sobre lo concedido y no sobre rawTp para que la fatiga siga
        // acumulándose igual — quemas lo mismo y te llevas la mitad.
        if (FormIds.POTENTIAL_UNLOCK.equals(
                sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId())) {
            granted *= CommonConfig.potentialUnlockTpMult();
        }

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