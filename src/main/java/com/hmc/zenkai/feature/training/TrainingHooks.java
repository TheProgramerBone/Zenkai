package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.registry.ModDimensions;
import net.minecraft.server.level.ServerPlayer;

/**
 * Funciones de entrenamiento TP (v1.0: combate).
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
        grant(sp, effectiveDamage * ServerConfig.trainingDamageTpFactor(), victimPl);
    }

    /** TP por matar una entidad. El reward ya viene resuelto por EntityDeathRewardHandler.
     *  Devuelve el TP ENTERO concedido (0 si no llegó a sumar nada) — EntityDeathRewardHandler
     *  lo usa para el aviso de "Train with your shadow" (cuánto TP dejó el clon).
     *  @param victimPl PL de la víctima, para el factor de diferencia de poder. */
    public static int grantFromKill(ServerPlayer sp, int reward, long victimPl) {
        if (reward <= 0) return 0;
        return grant(sp, reward, victimPl);
    }

    /** Golpe al aire con mano vacía (TrainingSwingPacket). Valida cooldown + stamina. */
    public static void grantFromSwing(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return;

        TrainingData td = sp.getData(ZenkaiDataAttachments.TRAINING.get());
        long now = sp.level().getGameTime();
        if (now - td.getLastSwingTime() < ServerConfig.trainingAirMinTicks()) return;

        int cost = (int) Math.ceil(att.getStaminaMax() * ServerConfig.trainingAirStaminaCostPct());
        if (att.getStamina() < cost) return;
        att.consumeStamina(cost);
        td.setLastSwingTime(now);

        // victimPl 0 = sin objetivo. Golpear al aire no se castiga por diferencia de poder:
        // ya está limitado por estamina y por el cooldown del packet.
        grant(sp, Math.max(1, att.getPowerLevelRaw()) * ServerConfig.trainingAirTpFactor(), 0L);
    }

    /** TP de un minijuego de Training (Meditation/Ki Target Practice). `rawTp` ya viene
     *  calculado/capado por el propio packet (ver MeditationSessionPacket/
     *  TargetPracticeSessionPacket) a partir de desempeño CRUDO reportado por el cliente —
     *  aquí solo se alimenta al núcleo compartido, igual que grantFromSwing (victimPl 0 = sin
     *  rival que comparar). Devuelve el TP entero realmente concedido (ver grant()) para que el
     *  packet se lo mande de vuelta al cliente como reward de la sesión (TrainingSessionRewardPacket). */
    public static int grantFromMeditation(ServerPlayer sp, double rawTp) {
        return grant(sp, rawTp, 0L);
    }

    public static int grantFromTargetPractice(ServerPlayer sp, double rawTp) {
        return grant(sp, rawTp, 0L);
    }

    /** "TP potencial: hasta X" de la pantalla INTRO de Meditation/Ki Target Practice — una
     *  simulación de SOLO LECTURA de grant() con victimPl=0 (mismo caso que esos dos minijuegos,
     *  ver arriba) y `rawTp = rawTpCap` (el techo de sesión de ServerConfig, la mejor sesión
     *  posible). NO muta fatiga/carry/TP: es una lectura del estado ACTUAL de fatiga, así que el
     *  número baja si el jugador ya viene fatigado de entrenar, igual que le pasaría de verdad.
     *  Duplica a propósito el tramo de grant() en vez de reusarlo (grant() muta estado y no debe
     *  poder "no mutar" con un flag) — si grant() cambia su fórmula, revisar también aquí. */
    public static int estimatePotential(ServerPlayer sp, double rawTpCap) {
        if (rawTpCap <= 0) return 0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return 0;

        double granted = rawTpCap * currentEfficiency(sp); // plFactor = 1.0 (victimPl 0, igual que grantFromMeditation/TargetPractice)
        boolean inHtc = sp.level().dimension() == ModDimensions.HTC_LEVEL;
        if (inHtc) granted *= ServerConfig.trainingHtcMultiplier();
        granted *= WeightSystem.tpFactor(att.getWeightLoad());

        if (FormIds.POTENTIAL_UNLOCK.equals(
                sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId())) {
            granted *= ServerConfig.potentialUnlockTpMult();
        }
        return (int) Math.floor(granted);
    }

    /** Núcleo: decay de fatiga, eficiencia, diferencia de poder, HTC, pesas, carry y sync.
     *  Devuelve el TP ENTERO concedido en esta llamada (0 si no llegó a sumar nada, p. ej. se
     *  quedó todo en el carry fraccional) — los llamadores de combate lo ignoran, los
     *  minijuegos de Training lo reportan al cliente. */
    private static int grant(ServerPlayer sp, double rawTp, long victimPl) {
        if (rawTp <= 0) return 0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return 0;
        long pl = Math.max(1, att.getPowerLevelRaw());

        TrainingData td = sp.getData(ZenkaiDataAttachments.TRAINING.get());
        long now = sp.level().getGameTime();

        // Lazy decay: minutos de juego desde el último evento.
        if (td.getLastDecayTime() > 0 && now > td.getLastDecayTime()) {
            double minutes = (now - td.getLastDecayTime()) / 1200.0;
            td.setFatigue(td.getFatigue() - ServerConfig.trainingFatigueDecayPerMinute() * minutes);
        }
        td.setLastDecayTime(now);

        double h = ServerConfig.trainingFatigueHalfLife();
        double m = Math.max(ServerConfig.trainingMinEfficiency(), h / (h + td.getFatigue()));

        // Diferencia de poder. El divisor NO es 1.0: el PL del jugador y el de un mob no están
        // en la misma escala (el suyo es 31-65% pool de ki, el del mob un 10%), así que un
        // combate igualado da ratio ~0.25 y no 1. Sin el divisor, un zombi calibrado para
        // matarte en 8 golpes contaba como chusma desde el minuto uno.
        double plFactor = 1.0;
        if (victimPl > 0) {
            double ratio = (victimPl / (double) pl) / ServerConfig.trainingPlRatioFull();
            plFactor = Math.max(ServerConfig.trainingPlRatioFloor(), Math.min(1.0, ratio));
        }

        double base = rawTp * m * plFactor;
        boolean inHtc = sp.level().dimension() == ModDimensions.HTC_LEVEL;
        double granted = base
                * (inHtc ? ServerConfig.trainingHtcMultiplier() : 1.0)
                * WeightSystem.tpFactor(att.getWeightLoad());

        // Potential Unlock: estás USANDO tu potencial, no entrenándolo. Se comprueba contra
        // la forma ACTIVA, nunca contra tener la habilidad: llevarla comprada no debe costar
        // nada. Se aplica sobre lo concedido y no sobre rawTp para que la fatiga siga
        // acumulándose igual — quemas lo mismo y te llevas la mitad.
        if (FormIds.POTENTIAL_UNLOCK.equals(
                sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId())) {
            granted *= ServerConfig.potentialUnlockTpMult();
        }

        double total = granted + td.getCarry();
        int whole = (int) Math.floor(total);
        td.setCarry(total - whole);
        td.setFatigue(td.getFatigue() + base / pl); // fatiga sobre la cantidad BASE

        if (whole > 0) {
            att.addTP(whole);
            PlayerLifeCycle.syncIfServer(sp);
        }
        return whole;
    }

    /** Multiplicador de eficiencia por fatiga ACTUAL — lee `TrainingData.fatigue` tal cual está
     *  guardada, SIN aplicar el lazy-decay que sí corre dentro de grant() (esto es una consulta
     *  de solo lectura para UI, ej. el panel "TP Modifiers" del hub vía TrainingFatiguePacket;
     *  aplicar decay aquí mutaría estado del jugador solo por abrir una pantalla). Puede quedar
     *  un poco desactualizado hasta el próximo grant() real — aceptable para un indicador, no
     *  para el cálculo real de TP. Reusado por estimatePotential() de arriba. */
    public static double currentEfficiency(ServerPlayer sp) {
        TrainingData td = sp.getData(ZenkaiDataAttachments.TRAINING.get());
        double h = ServerConfig.trainingFatigueHalfLife();
        return Math.max(ServerConfig.trainingMinEfficiency(), h / (h + td.getFatigue()));
    }
}