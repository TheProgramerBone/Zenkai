package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
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

    /** Duración de referencia (ticks) contra la que se expresan meditationSessionTpCap()/
     *  targetPracticeSessionTpCap() de ServerConfig (30s) — un minijuego con sesión más larga
     *  escala su techo proporcionalmente en vez de quedarse plano al número pensado para 30s.
     *  Compartida entre MeditationSessionPacket, TargetPracticeSessionPacket y
     *  TrainingInfoRequestPacket (el "TP potencial: hasta X" de INTRO debe usar la MISMA
     *  fórmula que el handler que de verdad concede el TP, o el número que ve el jugador antes
     *  de jugar dejaría de coincidir con lo que puede ganar de verdad). */
    public static final double SESSION_CAP_BASELINE_TICKS = 600.0; // 30s

    /**
     * TP crudo (rawTp, ANTES de fatiga/HTC/pesas) de la MEJOR sesión de Práctica Libre
     * físicamente posible con esta dificultad/duración — pedido explícito del usuario tras
     * notar que "TP potential: up to X" no cuadraba con el TP que de verdad se podía conseguir
     * jugando. Bug real encontrado revisando la fórmula: `estimatePotential()` usaba SOLO
     * `meditationSessionTpCap()` (un techo plano de ServerConfig, 500 por defecto) como techo de
     * la "mejor sesión posible", pero con `meditation.tp_per_combo` en 0.5 ni la sesión más larga
     * (120s) a la dificultad más alta llega a acercarse a 500 notas acertadas — el número
     * mostrado prometía un TP que NINGUNA sesión, por perfecta que fuera, podía llegar a dar.
     * Aquí se calcula el segundo techo real (cuántas notas caben de verdad en la duración
     * elegida, a la densidad que marca la dificultad) para que el caller haga
     * `Math.min(sessionCap, estoOtro)` — los dos techos son independientes y el más bajo manda.
     * DUPLICA a propósito las constantes de MeditationScreen.applyDifficulty()
     * (BASE_SPAWN_INTERVAL_MS=450, clamp 220..900) — esa clase es cliente-only y esto necesita
     * calcularlo en servidor; si esa fórmula cambia, hay que revisar también aquí.
     */
    public static double meditationAchievableRawTp(double difficultyFraction, int durationTicks) {
        double frac = Math.max(0.01, difficultyFraction);
        long spawnIntervalMs = (long) Math.max(220, Math.min(900, Math.round(450.0 / frac)));
        long durationMs = durationTicks * 50L;
        long maxNotes = durationMs / spawnIntervalMs;
        return maxNotes * ServerConfig.meditationTpPerCombo(); // precisión 100% (SICK) asumida
    }

    /**
     * Mismo principio que meditationAchievableRawTp(), para Ki Target Practice: cuántos orbes
     * caben de verdad en la duración/dificultad elegidas, asumiendo el mejor caso (ningún
     * spawn resulta ser una bomba — igual de optimista que asumir precisión 100% en Meditation).
     * DUPLICA las constantes de TargetPracticeScreen.applyDifficulty() (BASE_SPAWN_INTERVAL_MS=
     * 600, clamp 300..1100) por el mismo motivo (clase cliente-only).
     */
    public static double targetPracticeAchievableRawTp(double difficultyFraction, int durationTicks) {
        double frac = Math.max(0.01, difficultyFraction);
        long spawnIntervalMs = (long) Math.max(300, Math.min(1100, Math.round(600.0 / frac)));
        long durationMs = durationTicks * 50L;
        long maxOrbs = durationMs / spawnIntervalMs;
        return maxOrbs * ServerConfig.targetPracticeTpPerOrb();
    }

    /** TP por daño efectivo infligido a otra entidad. Llamar SOLO en servidor.
     *  @param victimPl PL de la víctima, para el factor de diferencia de poder. */
    public static void grantFromDamage(ServerPlayer sp, double effectiveDamage, long victimPl) {
        if (effectiveDamage <= 0) return;
        grant(sp, effectiveDamage * ServerConfig.trainingDamageTpFactor(), victimPl, TrainingCategory.COMBAT);
    }

    /** TP por matar una entidad. El reward ya viene resuelto por EntityDeathRewardHandler.
     *  Devuelve el TP ENTERO concedido (0 si no llegó a sumar nada) — EntityDeathRewardHandler
     *  lo usa para el aviso de "Train with your shadow" (cuánto TP dejó el clon).
     *  @param victimPl PL de la víctima, para el factor de diferencia de poder. */
    public static int grantFromKill(ServerPlayer sp, int reward, long victimPl) {
        if (reward <= 0) return 0;
        return grant(sp, reward, victimPl, TrainingCategory.COMBAT);
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
        // ya está limitado por estamina y por el cooldown del packet. Categoría COMBAT: es la
        // misma fatiga que sparring/matar/la sombra (ver TrainingCategory), no un cuarto pool.
        grant(sp, Math.max(1, att.getPowerLevelRaw()) * ServerConfig.trainingAirTpFactor(), 0L,
                TrainingCategory.COMBAT);
    }

    /** TP de un minijuego de Training (Meditation/Ki Target Practice). `rawTp` ya viene
     *  calculado/capado por el propio packet (ver MeditationSessionPacket/
     *  TargetPracticeSessionPacket) a partir de desempeño CRUDO reportado por el cliente —
     *  aquí solo se alimenta al núcleo compartido, igual que grantFromSwing (victimPl 0 = sin
     *  rival que comparar). Devuelve el TP entero realmente concedido (ver grant()) para que el
     *  packet se lo mande de vuelta al cliente como reward de la sesión (TrainingSessionRewardPacket). */
    public static int grantFromMeditation(ServerPlayer sp, double rawTp) {
        return grant(sp, rawTp, 0L, TrainingCategory.MEDITATION);
    }

    public static int grantFromTargetPractice(ServerPlayer sp, double rawTp) {
        return grant(sp, rawTp, 0L, TrainingCategory.TARGET_PRACTICE);
    }

    /** "TP potencial: hasta X" de la pantalla INTRO de Meditation/Ki Target Practice — una
     *  simulación de SOLO LECTURA de grant() con victimPl=0 (mismo caso que esos dos minijuegos,
     *  ver arriba) y `rawTp = rawTpCap` (el techo de sesión de ServerConfig, la mejor sesión
     *  posible). NO muta fatiga/carry/TP: es una lectura del estado ACTUAL de fatiga, así que el
     *  número baja si el jugador ya viene fatigado de entrenar, igual que le pasaría de verdad.
     *  Duplica a propósito el tramo de grant() en vez de reusarlo (grant() muta estado y no debe
     *  poder "no mutar" con un flag) — si grant() cambia su fórmula, revisar también aquí. */
    public static int estimatePotential(ServerPlayer sp, double rawTpCap, TrainingCategory cat) {
        if (rawTpCap <= 0) return 0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return 0;

        double granted = rawTpCap * currentEfficiency(sp, cat); // plFactor = 1.0 (victimPl 0, igual que grantFromMeditation/TargetPractice)
        boolean inHtc = sp.level().dimension() == ModDimensions.HTC_LEVEL;
        if (inHtc) granted *= ServerConfig.trainingHtcMultiplier();
        granted *= WeightSystem.tpFactor(att.getWeightLoad());

        if (potentialUnlockBonusApplies(sp)) {
            granted *= ServerConfig.potentialUnlockTpMult();
        }
        return (int) Math.floor(granted);
    }

    /** Núcleo: decay de fatiga, eficiencia, diferencia de poder, HTC, pesas, carry y sync.
     *  Devuelve el TP ENTERO concedido en esta llamada (0 si no llegó a sumar nada, p. ej. se
     *  quedó todo en el carry fraccional) — los llamadores de combate lo ignoran, los
     *  minijuegos de Training lo reportan al cliente.
     *  @param cat qué fatiga/carry usar — cada categoría es independiente, ver TrainingCategory. */
    private static int grant(ServerPlayer sp, double rawTp, long victimPl, TrainingCategory cat) {
        if (rawTp <= 0) return 0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return 0;
        long pl = Math.max(1, att.getPowerLevelRaw());

        TrainingData td = sp.getData(ZenkaiDataAttachments.TRAINING.get());
        long now = sp.level().getGameTime();

        // Lazy decay: minutos de juego desde el último evento, SOLO de esta categoría.
        if (td.getLastDecayTime(cat) > 0 && now > td.getLastDecayTime(cat)) {
            double minutes = (now - td.getLastDecayTime(cat)) / 1200.0;
            td.setFatigue(cat, td.getFatigue(cat) - ServerConfig.trainingFatigueDecayPerMinute() * minutes);
        }
        td.setLastDecayTime(cat, now);

        double h = ServerConfig.trainingFatigueHalfLife();
        double m = Math.max(ServerConfig.trainingMinEfficiency(), h / (h + td.getFatigue(cat)));

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
        if (potentialUnlockBonusApplies(sp)) {
            granted *= ServerConfig.potentialUnlockTpMult();
        }

        double total = granted + td.getCarry(cat);
        int whole = (int) Math.floor(total);
        td.setCarry(cat, total - whole);
        td.setFatigue(cat, td.getFatigue(cat) + base / pl); // fatiga sobre la cantidad BASE

        if (whole > 0) {
            att.addTP(whole);
            PlayerLifeCycle.syncIfServer(sp);
        }
        return whole;
    }

    /**
     * "TP potential: up to X" del selector de ShadowTrainingScreen (dificultad + forma
     * simulada) — pedido explícito del usuario tras añadir el selector "Opponent form": antes
     * solo se enseñaba un multiplicador (x_) porque Shadow no tiene techo de sesión fijo, pero
     * eso no respondía "¿cuánto TP me da esto?" al cambiar de forma. A diferencia de
     * estimatePotential() (Meditation/Target Practice, victimPl SIEMPRE 0 porque son minijuegos
     * sin rival), esto SÍ necesita el plFactor real — es la estimación de "cuánto concedería
     * matar a la sombra AHORA MISMO con esta dificultad/forma", `rawTp` ya resuelto por el
     * llamador con la MISMA fórmula "auto" que EntityStats.resolveReward() usa para el reward de
     * cualquier entidad con stats (round(victimPl * tpPerPl())) — duplicado ahí a propósito
     * porque esa clase vive en combat.entity y no debe depender de Training.
     * Duplica el tramo relevante de grant() (fatiga -> plFactor -> HTC -> pesas -> Potential
     * Unlock) por el mismo motivo que estimatePotential(): grant() MUTA estado y una consulta de
     * UI nunca debe poder mutarlo solo por mirar una pantalla. Si grant() cambia su fórmula, hay
     * que revisar los TRES (aquí, estimatePotential() y grant()) a mano.
     */
    public static int estimateCombatKillReward(ServerPlayer sp, double rawTp, long victimPl) {
        if (rawTp <= 0) return 0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return 0;
        long pl = Math.max(1, att.getPowerLevelRaw());

        double m = currentEfficiency(sp, TrainingCategory.COMBAT);
        double plFactor = 1.0;
        if (victimPl > 0) {
            double ratio = (victimPl / (double) pl) / ServerConfig.trainingPlRatioFull();
            plFactor = Math.max(ServerConfig.trainingPlRatioFloor(), Math.min(1.0, ratio));
        }

        double granted = rawTp * m * plFactor;
        boolean inHtc = sp.level().dimension() == ModDimensions.HTC_LEVEL;
        if (inHtc) granted *= ServerConfig.trainingHtcMultiplier();
        granted *= WeightSystem.tpFactor(att.getWeightLoad());

        if (potentialUnlockBonusApplies(sp)) granted *= ServerConfig.potentialUnlockTpMult();
        return (int) Math.floor(granted);
    }

    /** Multiplicador de eficiencia por fatiga ACTUAL de una categoría — lee `TrainingData.
     *  fatigue(cat)` tal cual está guardada, SIN aplicar el lazy-decay que sí corre dentro de
     *  grant() (esto es una consulta de solo lectura para UI, ej. el panel "TP Modifiers" del
     *  hub vía TrainingFatiguePacket; aplicar decay aquí mutaría estado del jugador solo por
     *  abrir una pantalla). Puede quedar un poco desactualizado hasta el próximo grant() real de
     *  esa categoría — aceptable para un indicador, no para el cálculo real de TP. Reusado por
     *  estimatePotential() de arriba. */
    public static double currentEfficiency(ServerPlayer sp, TrainingCategory cat) {
        TrainingData td = sp.getData(ZenkaiDataAttachments.TRAINING.get());
        double h = ServerConfig.trainingFatigueHalfLife();
        return Math.max(ServerConfig.trainingMinEfficiency(), h / (h + td.getFatigue(cat)));
    }

    /**
     * ¿Aplica el bono de `potentialUnlockTpMult()`? Pedido explícito del usuario (2026-09-09):
     * "que el potencial unlocked escale también con las divinas" — antes SOLO se activaba con la
     * forma literal `potential_unlock` (el "así despierta tu Kaiō-shin tu potencial" de Gohan).
     * Ahora también cuenta CUALQUIER forma `divineTier()` (human_god/namek_god/majin_god/
     * ssj_god/ssj_blue/ssj_rose — ver FormDef.divineTier(), mismo flag mecánico que ya usa
     * AuraRimRenderer para el aro de aura, no aura_type). Mismo multiplicador configurado para
     * las dos — no dos números distintos por ahora: separarlos es una decisión de balance que
     * todavía no se ha tomado, esto solo abre la puerta mecánica.
     */
    private static boolean potentialUnlockBonusApplies(ServerPlayer sp) {
        PlayerFormAttachment formAtt = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        if (FormIds.POTENTIAL_UNLOCK.equals(formAtt.getFormId())) return true;
        FormDef def = formAtt.activeDef();
        return def != null && def.divineTier();
    }
}