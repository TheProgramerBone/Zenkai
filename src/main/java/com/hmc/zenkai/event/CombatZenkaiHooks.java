package com.hmc.zenkai.event;

import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.combat.*;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.OtherworldManager;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import com.hmc.zenkai.feature.technique.PhysicalCombatServer;
import com.hmc.zenkai.feature.training.TrainingHooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pipeline de combate en UN SOLO handler (antes había dos handlers sobre el mismo evento y el
 * orden entre ellos no estaba definido: por eso un golpe de 15 contra una defensa de 10 a veces
 * aplicaba 15 en vez de 5). Ahora, en el mismo evento y en orden garantizado:
 *   1) Lado ATACANTE: si pega un jugador con raza (y no es Ki Blast), el golpe se recalcula con
 *      su STR (limitado por stamina) + bonus de arma, y consume stamina.
 *   2) Lado DEFENSOR: si recibe un jugador con raza, se mitiga con su DEF y el daño va al pool
 *      BODY (la vida vanilla nunca baja). Si body llega a 0 -> estado "derribado" (transición).
 * Si la víctima es un mob, recibe en su vida vanilla el daño ya recalculado del atacante.
 * onDamage solo ORQUESTA; cada paso vive en su propia forma (mismo criterio que
 * TickHandlers.onPlayerTick).
 */
public class CombatZenkaiHooks {

    /** Duración del estado derribado antes de morir de verdad (5 s). */
    public static final int DOWNED_TICKS = 100;

    /** Al salir del derribado (por curación propia o de un aliado) el jugador vuelve con este % del body máx. */
    public static final double DOWNED_REVIVE_PCT = 0.20;

    /** Hambre (puntos de comida) que gasta quien revive a un aliado con click derecho (mano vacía). */
    public static final int REVIVE_HUNGER_COST = 4;

    /** Body con el que se levanta un derribado: 20% del máximo (mínimo 1). */
    public static int downedReviveBody(PlayerStatsAttachment att) {
        return Math.max(1, (int) Math.round(att.getBodyMax() * DOWNED_REVIVE_PCT));
    }

    // =====================================================================
    // ORQUESTADOR
    // =====================================================================

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre e) {
        if (e.getEntity().level().isClientSide()) return;
        // I-frames: va lo PRIMERO, antes de tocar defensa o pools. Un dash esquiva de verdad.
        if (e.getEntity() instanceof ServerPlayer dodger
                && PhysicalCombatServer.hasIFrames(dodger.getUUID())) {
            e.setNewDamage(0.0F);
            return;
        }
        MinecraftServer server = e.getEntity().getServer();
        if (server == null || !ModGameRules.enableRaceBoosts(server)) return;

        ZenkaiCombatStats atkStats = ZenkaiStats.of(e.getSource().getEntity());
        float dmg = computeAttackDamage(e, atkStats);

        ZenkaiCombatStats defStats = ZenkaiStats.of(e.getEntity());
        if (defStats != null && defStats.isCombatActive()) {
            applyToZenkaiVictim(e, atkStats, defStats, dmg);
            return;
        }
        applyToVanillaVictim(e, dmg);
    }

    // =====================================================================
    // LADO ATACANTE
    // =====================================================================

    /**
     * Daño de salida del atacante. Los proyectiles ki traen su daño ya calculado (kiPower),
     * así que no se recalculan aquí.
     * @return el daño que entra al lado defensor.
     */
    private static float computeAttackDamage(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats) {
        float dmg = e.getNewDamage();

        if (atkStats == null || !atkStats.isCombatActive()) return dmg;
        if (e.getSource().getDirectEntity() instanceof KiProjectileEntity) return dmg;
        if (PhysicalCombatServer.isFiring()) return dmg;

        // GOLPE INDIRECTO (flecha, tridente, bola de nieve): el daño NO es el melee del
        // tirador. Sin esto caía en playerMeleeDamage y una flecha pegaba el STR completo
        // gastando estamina, con el arco actuando de "arma" del multiplicador. Lo que lleva
        // es su daño vanilla más lo que Ki Infuse le puso AL LANZARLA.
        Entity direct = e.getSource().getDirectEntity();
        if (direct != null && direct != e.getSource().getEntity()) {
            return dmg + (float) KiInfusedShot.of(direct).bonusDamage();
        }

        double strDamage = atkStats.computeMeleeFinal();

        if (e.getSource().getEntity() instanceof Player attacker) {
            return playerMeleeDamage(attacker, atkStats, strDamage, dmg);
        }
        // Entidad: su STR es la fuente única del daño melee (sin gate de stamina en Fase 2).
        return (float) strDamage;
    }

    /**
     * Golpe cuerpo a cuerpo de un JUGADOR. Compuerta de MODO COMBATE: fuera de él, el golpe
     * deja pasar el daño VANILLA puro (sin STR zenkai y sin gastar stamina).
     * EL ARMA MULTIPLICA, NO SUMA: sumando, una espada de diamante (~8) era ruido frente a
     * una STR de cientos y todas las armas valían lo mismo. Ver KiInfusion.weaponMultiplier.
     * La estamina no capa el daño: el golpe pega STR completo y lo que decide es CUÁNTOS
     * golpes aguantas. Ki Infuse suma aparte su bonus de WIL y se cobra en ki.
     */
    private static float playerMeleeDamage(Player attacker, ZenkaiCombatStats atkStats,
                                           double strDamage, float vanillaDmg) {
        boolean zenkaiMelee = attacker instanceof ServerPlayer atkSp
                && CombatModeServerState.isActive(atkSp.getUUID());
        if (!zenkaiMelee) return vanillaDmg;

        // Sin fondo de stamina: solo pega el arma vanilla, sin el STR zenkai y sin
        // multiplicador. No se puede machacar con golpes potenciados cuando estás seco,
        // pero tampoco quedas indefenso.
        if (atkStats.getStamina() <= 0) {
            return (float) KiInfusion.attackDamageOf(attacker);
        }

        // Cooldown de golpe estilo espada: misma curva que Player.attack(), 20% de daño con
        // el ticker a cero y 100% lleno. Es cuadrática a propósito — castiga el spam mucho
        // más que un lineal. Hay que aplicarla a mano porque este hook REEMPLAZA el daño
        // entrante, y vanilla ya la había aplicado sobre el valor que estamos descartando.
        float scale = consumeAttackScale(attacker);
        double chargeF = 0.2 + scale * scale * 0.8;

        double base = strDamage * KiInfusion.weaponMultiplier(attacker) * chargeF;
        // Ki Infuse: 0 si el interruptor está apagado, si va desarmado o si no le queda ki.
        // spendForMelee ya cobra: coste y daño salen del mismo número y no pueden descuadrarse.
        double bonus = KiInfusion.spendForMelee(attacker, atkStats, chargeF);

        // Coste de estamina = daño STR × carga × factor global × multiplicador de raza/estilo.
        // Sale del STR PELADO (sin arma ni infusión): el arma no cansa más, y lo que añade la
        // infusión ya se pagó en ki. Que la estamina escale igual que el daño es deliberado:
        // el spam pierde DPS pero no malgasta recurso, así el incentivo a esperar es el daño
        // y no el castigo doble.
        int staminaCost = (int) Math.ceil(strDamage * chargeF
                * CommonConfig.meleeStaminaPerHit() * atkStats.staminaCostMult());
        if (staminaCost > 0) atkStats.consumeStamina(staminaCost);

        PlayerLifeCycle.syncIfServer(attacker);
        return (float) (base + bonus);
    }

    // =====================================================================
    // LADO DEFENSOR
    // =====================================================================

    /** Víctima CON stats zenkai: mitiga con DEF y el daño va al pool body. */
    private static void applyToZenkaiVictim(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                            ZenkaiCombatStats defStats, float dmg) {
        if (dmg > 0f) {
            double finalDamage = mitigate(e, atkStats, defStats, dmg);

            int bodyBefore = defStats.getBody();
            defStats.addBody(-(int) Math.ceil(finalDamage));
            // La barra vanilla sigue al pool: sin esto la de los jefes no se movía nunca.
            // Solo para no-jugadores; el jugador mantiene su vida vanilla llena a propósito
            // (su daño vive en el pool y la GUI del mod es la que lo muestra).
            if (!(e.getEntity() instanceof Player)) {
                defStats.mirrorToVanilla(e.getEntity());
            }

            // Entrenamiento: TP por daño EFECTIVO (post-defensa y post-barrera,
            // capado por el pool restante -> sin exploit de overkill ni de derribados).
            grantTraining(e, Math.min(finalDamage, bodyBefore));
        }

        if (e.getEntity() instanceof Player victim) {
            // El jugador nunca recibe daño vanilla; el daño vive en el pool body.
            e.setNewDamage(0.0F);
            if (defStats.getBody() <= 0) onBodyDepleted(victim, PlayerStatsAttachment.get(victim));
            PlayerLifeCycle.syncIfServer(victim);
            return;
        }

        // Entidad con stats: el body es su vida real (esquiva el cap de MC).
        if (defStats.getBody() <= 0) {
            // Golpe letal: dejamos pasar daño vanilla real -> muerte con loot/XP/killer correctos.
            e.setNewDamage(Math.max(e.getEntity().getHealth(), 1.0F));
        } else {
            e.setNewDamage(0.0F); // absorbido por el pool body
        }
    }

    /** Aplica DEF, bloqueo y barrera. @return daño que llega al body. */
    private static double mitigate(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                   ZenkaiCombatStats defStats, float dmg) {
        double defense = computeDefense(e, atkStats, defStats, dmg);

        // Reducción PORCENTUAL relativa al golpe entrante, no resta.
        // Con resta, melee y defensa tenían que vivir en escalas separadas a mano: un
        // Arcosian recién creado tenía defensa 74 contra melee 75 y necesitaba 47 golpes
        // para matar a otro Arcosian. Además esto se autoescala: la misma fórmula vale con
        // STR 10 y con STR 200.000, sin recalibrar nada.
        // defensa == daño  ->  50% de reducción.
        double finalDamage = (defense <= 0.0)
                ? dmg
                : dmg * (1.0 - defense / (defense + dmg));
        finalDamage = Math.max(finalDamage, dmg * CommonConfig.minDamagePercent());

        if (e.getEntity() instanceof ServerPlayer defSp && KiCombatServer.isBlocking(defSp)) {
            finalDamage *= SkillEffects.blockDamageMultiplier(defSp);
        }

        // Barrera ki: absorbe ANTES de tocar el body (solo jugadores).
        if (e.getEntity() instanceof ServerPlayer defSp) {
            finalDamage = KiCombatServer.absorb(defSp, finalDamage);
        }
        return finalDamage;
    }

    /** DEF efectiva del defensor frente a ESTE golpe. Se mantiene SIEMPRE, en modo combate o no. */
    private static double computeDefense(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                         ZenkaiCombatStats defStats, float dmg) {
        double defense = defStats.computeDefenseFinal();

        // Proyectil ki: la DEF se escala según lo cargado que venía el disparo respecto al
        // poder de QUIEN LO LANZÓ. refPower > 0 = fue desviado (kiai), así que el dueño actual
        // ya no es quien disparó y hay que usar la referencia congelada en el proyectil.
        if (e.getSource().getDirectEntity() instanceof KiProjectileEntity proj) {
            double kiPower = proj.refPower() > 0.0
                    ? proj.refPower()
                    : (atkStats != null ? atkStats.computeKiPowerFinal() : 0.0);
            if (kiPower > 1.0e-6) defense *= (dmg / kiPower);
        } else {
            // Proyectil infusionado: misma regla que el ki. La defensa se escala según lo
            // que este disparo vale frente al poder de quien lo lanzó, congelado al salir
            // (el tirador pudo transformarse o morir mientras la flecha volaba).
            KiInfusedShot shot = KiInfusedShot.of(e.getSource().getDirectEntity());
            if (shot.isInfused() && shot.refPower() > 1.0e-6) {
                defense *= (dmg / shot.refPower());
            }
        }
        return defense;
    }

    /** Víctima SIN stats (mob vanilla): recibe en su vida el daño ya recalculado. */
    private static void applyToVanillaVictim(LivingDamageEvent.Pre e, float dmg) {
        e.setNewDamage(dmg);
        // Entrenamiento vs mobs vanilla: capado por la vida restante (sin overkill).
        if (dmg > 0f) grantTraining(e, Math.min(dmg, e.getEntity().getHealth()));
    }

    /** TP de entrenamiento al atacante, si es un jugador distinto de la víctima. */
    private static void grantTraining(LivingDamageEvent.Pre e, double amount) {
        Entity source = e.getSource().getEntity();
        if (source instanceof ServerPlayer trainer && trainer != e.getEntity()) {
            TrainingHooks.grantFromDamage(trainer, amount);
        }
    }

    // =====================================================================
    // DERRIBADO
    // =====================================================================

    /**
     * Body agotado. En vez de morir al instante:
     *  - inmortal: no cae.
     *  - ya en el otro mundo: se re-ancla ahí (sin reiniciar su temporizador de Yemma).
     *  - vivo: entra en "derribado" (acostado) 5 s. Si lo curan (senzu propio/aliado) revive;
     *    si nadie lo cura, el tick de TickHandlers lo mata de verdad y pasa al otro mundo.
     */
    private static void onBodyDepleted(Player victim, PlayerStatsAttachment att) {
        if (!(victim instanceof ServerPlayer sp)) return;

        // Inmortal: no cae NUNCA y no debe quedarse a 0. Rellenamos el body al máximo
        // (antes solo se hacía `return`, dejando body=0 y la barra bugueada en "HP 0/max").
        if (att.isImmortal()) {
            att.setBody(att.getBodyMax());
            sp.setHealth(sp.getMaxHealth());
            PlayerLifeCycle.sync(sp);
            return;
        }

        if (att.isInOtherworld()) {
            OtherworldManager.keepInOtherworld(sp);
            return;
        }

        if (att.flags().isDowned()) return; // ya está derribado

        att.flags().setDowned(true);
        att.flags().setDownedUntil(sp.serverLevel().getGameTime() + DOWNED_TICKS);
        PlayerLifeCycle.sync(sp);
    }

    /**
     * Curar a un aliado DERRIBADO con click derecho y MANO VACÍA ("darle energía"):
     *  - Solo funciona si el objetivo está derribado.
     *  - Al curador le cuesta hambre (REVIVE_HUNGER_COST); si no le queda comida, no puede.
     *  - El objetivo recupera el 20% del body; el tick de derribado (TickHandlers) lo levanta
     *    al siguiente tick al detectar body>0 (libera lock y pose).
     * Con la senzu en mano NO entra aquí (mano no vacía): esa vía la maneja SenzuBean.
     */
    @SubscribeEvent
    public static void onDownedAllyInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide()) return;
        if (e.getHand() != InteractionHand.MAIN_HAND) return;

        Player healer = e.getEntity();
        if (!healer.getMainHandItem().isEmpty()) return;               // mano vacía
        if (!(e.getTarget() instanceof ServerPlayer target)) return;

        PlayerStatsAttachment tAtt = PlayerStatsAttachment.get(target);
        if (!tAtt.flags().isDowned()) return;                          // solo si está derribado

        // Costo de hambre para el curador (creativo cura gratis).
        if (!healer.isCreative()) {
            FoodData food = healer.getFoodData();
            if (food.getFoodLevel() <= 0) {                            // sin energía que dar
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
                return;
            }
            food.setFoodLevel(Math.max(0, food.getFoodLevel() - REVIVE_HUNGER_COST));
        }

        // Levanta al aliado con el 20% del body.
        tAtt.setBody(downedReviveBody(tAtt));
        PlayerLifeCycle.sync(target);

        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    // =====================================================================
    // BLOQUEO
    // =====================================================================

    @SubscribeEvent
    public static void onAttackWhileBlocking(AttackEntityEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        if (e.getEntity() instanceof ServerPlayer sp && KiCombatServer.isBlocking(sp)) {
            e.setCanceled(true);
        }
    }

    // =====================================================================
    // COOLDOWN DE GOLPE (attack_strength de vanilla)
    // =====================================================================

    /**
     * Escalado de fuerza de golpe capturado al inicio de Player.attack().
     * Hay que guardarlo aquí porque attack() llama a resetAttackStrengthTicker() ANTES de
     * hurt(), así que cuando corre LivingDamageEvent el ticker ya volvió a cero y
     * getAttackStrengthScale devolvería ~0 para todos los golpes.
     * El valor guardado es {escala, tickCount}: el tick sirve para descartar una entrada
     * vieja y que un daño que NO venga de attack() (fuego, caída) no herede una escala baja.
     */
    private static final Map<UUID, float[]> ATTACK_SCALE = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttackCaptureScale(AttackEntityEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        Player p = e.getEntity();
        ATTACK_SCALE.put(p.getUUID(), new float[]{ p.getAttackStrengthScale(0.5f), p.tickCount });
    }

    /** Consume la escala capturada. 1.0 si no hay una fresca: el daño no vino de un golpe. */
    private static float consumeAttackScale(Player p) {
        float[] v = ATTACK_SCALE.remove(p.getUUID());
        if (v == null || p.tickCount - v[1] > 1) return 1.0f;
        return v[0];
    }

    /** Llamar al desconectar: si no, queda una entrada por jugador que haya entrado alguna vez. */
    public static void forgetAttackScale(UUID playerId) {
        ATTACK_SCALE.remove(playerId);
    }

    /** Nadie FIJA a un jugador derribado. Complemento del barrido de TickHandlers, que es
     *  quien suelta a los que ya lo tenían de objetivo antes de que cayera. */
    @SubscribeEvent
    public static void onChangeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent e) {
        if (!(e.getNewAboutToBeSetTarget() instanceof ServerPlayer target)) return;
        if (PlayerStatsAttachment.get(target).flags().isDowned()) {
            e.setCanceled(true);
        }
    }
}