package com.hmc.zenkai.event;

import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.content.item.KiWeaponItem;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.combat.*;
import com.hmc.zenkai.feature.kiweapon.KiWeaponServer;
import com.hmc.zenkai.feature.party.PartyService;
import com.hmc.zenkai.registry.ModDamageTypes;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
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

        // Daño que vainilla declara inesquivable: /kill y el vacío. Sale de aquí sin tocar
        // nada para que lo aplique vanilla tal cual.
        // Va lo PRIMERO, antes incluso que los i-frames: no tiene sentido esquivar un /kill.
        // Sin esto, /kill entraba al pool como un golpe más y dejaba al jugador derribado en
        // vez de muerto — y a un inmortal ni eso.
        // ⚠ API a verificar al compilar: DamageTypeTags.BYPASSES_INVULNERABILITY en 1.21.1.
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        // FUEGO AMIGO DE PARTY: antes que cualquier otra cosa, incluidos los i-frames de
        // abajo — un golpe bloqueado aquí no debe consumir el dash de nadie ni contar como
        // "casi me pega". Cubre lo que llega por LivingDamageEvent con el atacante
        // puesto: melee, ki (blasts, proyectiles, Kiai) y técnicas por igual, porque cada una de
        // esas rutas pasan por este mismo evento. Ver PartyService.friendlyFireBlocked.
        if (e.getSource().getEntity() instanceof ServerPlayer attacker
                && e.getEntity() instanceof ServerPlayer victim
                && PartyService.friendlyFireBlocked(attacker, victim)) {
            e.setNewDamage(0.0F);
            return;
        }

        // I-frames: antes de tocar defensa o pools. Un dash esquiva de verdad.
        if (e.getEntity() instanceof ServerPlayer dodger
                && PhysicalCombatServer.hasIFrames(dodger.getUUID())) {
            e.setNewDamage(0.0F);
            return;
        }
        MinecraftServer server = e.getEntity().getServer();
        if (server == null || !ModGameRules.enableRaceBoosts(server)) return;

        // La mitigación vanilla se lee AQUÍ, antes de que computeAttackDamage descarte el
        // daño entrante. Después ya es tarde: el ratio vive en el número que sustituimos.
        double armorMult = VanillaMitigation.armorMultiplier(e);

        ZenkaiCombatStats atkStats = ZenkaiStats.of(e.getSource().getEntity());
        float dmg = computeAttackDamage(e, atkStats, armorMult);

        // Se consume AQUÍ, inmediatamente después de computeAttackDamage (el único sitio que
        // puede haberlo marcado, vía BlackFlash.apply dentro de playerMeleeDamage) y ANTES de
        // repartir a applyToZenkaiVictim/applyToVanillaVictim — así nunca sobrevive de un golpe
        // a otro. Ver BlackFlash.consumeProc.
        boolean blackFlash = BlackFlash.consumeProc(e.getEntity().getUUID());

        ZenkaiCombatStats defStats = ZenkaiStats.of(e.getEntity());
        if (defStats != null && defStats.isCombatActive()) {
            applyToZenkaiVictim(e, atkStats, defStats, dmg, armorMult, blackFlash,
                    isEnvironmentalHazard(e.getSource()));
            return;
        }
        applyToVanillaVictim(e, dmg, armorMult);
    }

    /**
     * Lava, fuego (la etiqueta vanilla IS_FIRE cubre in_fire/on_fire/lava/hot_floor) y
     * ahogamiento: la fuente NO tiene entidad atacante. Ni STR (no hay quién pegue) ni DEF (está
     * calibrada para un rival cuyo golpe también escala con Power Level) tienen nada que hacer
     * ahí — un chorro FIJO de daño vanilla (4 de lava, 2 de ahogamiento, por medio segundo)
     * quedaba aplastado a nada contra la DEF de cualquier personaje con algo de progresión.
     * Ver mitigate() para cómo se traduce en su lugar.
     */
    private static boolean isEnvironmentalHazard(DamageSource src) {
        return src.getEntity() == null
                && (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypes.DROWN));
    }

    // =====================================================================
    // LADO ATACANTE
    // =====================================================================

    /**
     * Daño de salida del atacante. Los proyectiles ki traen su daño ya calculado (kiPower),
     * así que no se recalculan aquí.
     * @return el daño que entra al lado defensor.
     */
    private static float computeAttackDamage(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                             double armorMult) {
        float dmg = e.getNewDamage();

        if (atkStats == null || !atkStats.isCombatActive()) return dmg;
        if (e.getSource().getDirectEntity() instanceof KiProjectileEntity) return dmg;
        if (PhysicalCombatServer.isFiring()) return dmg;

        // GOLPE INDIRECTO (flecha, tridente, bola de fuego, bala de shulker): el daño NO es el
        // melee del tirador. Dos casos, y hasta ahora solo estaba resuelto el primero.
        Entity direct = e.getSource().getDirectEntity();
        if (direct != null && direct != e.getSource().getEntity()) {
            KiInfusedShot shot = KiInfusedShot.of(direct);

            // 1) INFUSIONADO: daño vanilla del proyectil + lo que Ki Infuse le puso AL LANZARLO.
            // EL ARCO MULTIPLICA EL BONUS, no se le suma: sumando, Poder V añadía ~3 puntos a un
            // bonus de miles y el encantamiento era invisible en el juego aunque estuviera en el
            // código. Mismo arreglo que weaponMultiplier hizo con las espadas.
            // El daño ORIGINAL (pre-armadura) es donde vive lo que puso Poder; dmg ya viene
            // mitigado y la armadura se cobra aparte en VanillaMitigation.
            // OJO CON EL DOBLE CONTEO: `dmg` (armadura del objetivo YA aplicada por vanilla,
            // ver LivingDamageEvent.Pre#getContainer) vuelve a pasar por mitigate(), que
            // multiplica TODO lo que sale de aquí por armorMult otra vez — necesario para las
            // ramas que sustituyen dmg por un número nuevo (STR/ki), pero aquí seguimos
            // devolviendo el propio dmg vanilla. Se cancela dividiendo por armorMult ahora, así
            // mitigate() lo deja tal y como vanilla ya lo dejó, una sola vez. El bonus de ki SÍ
            // debe recibir armorMult (tu armadura también debería frenar la parte ki de la
            // flecha), así que a él NO se le aplica esta corrección.
            if (shot.isInfused()) {
                double mult = KiInfusion.projectileMultiplier(VanillaMitigation.originalDamage(e));
                float vanillaPart = armorMult > 1.0e-6 ? (float) (dmg / armorMult) : dmg;
                return vanillaPart + (float) (shot.bonusDamage() * mult);
            }

            // 2) PROYECTIL VANILLA DE UNA ENTIDAD. Esta rama devolvía `dmg` tal cual, o sea daño
            // VANILLA sin tocar: una flecha de esqueleto hacía 3 puntos contra un pool de tres
            // cifras. Por eso esqueletos, blazes, ghasts, brujas, evokers, guardianes y
            // pillagers estaban desarmados en la práctica — y por eso cambiarles el arquetipo no
            // arreglaba nada: su share de STR sube, pero nunca pegan cuerpo a cuerpo.
            // Escala con el STR del tirador, reducido por config: un ataque a distancia debe
            // pegar menos que el puño del mismo bicho.
            // SOLO ENTIDADES: para el jugador el camino es Ki Infuse, y escalar aquí también
            // dejaría a esa habilidad sin razón de existir.
            // El guard de dmg > 0 protege lo que vanilla ya considera inofensivo (bolas de
            // nieve, huevos): sin él, un muñeco de nieve pasaría a matar.
            if (dmg > 0f && !(e.getSource().getEntity() instanceof Player)) {
                return (float) (atkStats.computeMeleeFinal() * CommonConfig.mobProjectileFactor());
            }
            // Flecha vanilla (sin infusionar) disparada por un jugador: mismo doble conteo de
            // armadura que la rama infusionada de arriba, mismo arreglo.
            return armorMult > 1.0e-6 ? (float) (dmg / armorMult) : dmg;
        }

        double strDamage = atkStats.computeMeleeFinal();

        if (e.getSource().getEntity() instanceof Player attacker) {
            return playerMeleeDamage(e.getEntity(), attacker, atkStats, strDamage, dmg,
                    VanillaMitigation.originalDamage(e));
        }

        // EXPLOSIÓN PROPIA (creeper): vanilla ya atenuó el daño por distancia y por cobertura, y
        // sustituirlo por strDamage a secas tiraba ese cálculo — los creeper hacía daño completo
        // a 1 bloque y a 6. Reusamos la proporción que vanilla calculó, mismo criterio que
        // VanillaMitigation con la armadura: no recalculamos nada suyo, leemos lo que ya decidió.
        // Solo entra aquí la explosión donde el creeper ES el proyectil (direct == entity). Una
        // bola de ghast tiene direct = LargeFireball, así que cae en la rama indirecta de arriba
        // y se escala como proyectil, que es lo correcto.
        // ⚠ API a verificar al compilar: DamageTypeTags.IS_EXPLOSION en 1.21.1.
        if (e.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            double full = CommonConfig.explosionReferenceDamage();
            double falloff = full <= 0.0
                    ? 1.0
                    : Math.min(1.0, VanillaMitigation.originalDamage(e) / full);
            return (float) (strDamage * falloff);
        }

        // Entidad: su STR es la fuente única del daño melee (sin gate de stamina en Fase 2).
        return (float) strDamage;
    }

    /**
     * Golpe cuerpo a cuerpo de un JUGADOR. Compuerta de MODO COMBATE: fuera de él, daño
     * VANILLA puro. EL ARMA MULTIPLICA (ver KiInfusion.weaponMultiplier).
     * Los recursos van por separado: la ESTAMINA la paga el STR, siempre, con habilidades o
     * sin ellas; el KI paga solo lo que Ki Fist y Ki Infuse AÑADEN. Golpear cansa igual;
     * pegar más fuerte es lo que cuesta ki.
     */
    private static float playerMeleeDamage(LivingEntity victim, Player attacker,
                                           ZenkaiCombatStats atkStats,
                                           double strDamage, float vanillaDmg,
                                           double originalVanillaDamage) {
        boolean zenkaiMelee = attacker instanceof ServerPlayer atkSp
                && CombatModeServerState.isActive(atkSp.getUUID());
        if (!zenkaiMelee) return vanillaDmg;

        // Crítico vanilla (salto+caída, x1.5 por defecto): capturado en CriticalHitEvent, que
        // dispara DENTRO de Player.attack() antes que este evento — ver onCriticalHit. Sin esto,
        // el golpe se reconstruía entero desde STR y el crítico de vanilla (partículas y sonido
        // incluidos) no cambiaba ni un punto de daño.
        float critMult = consumeCritMultiplier(attacker);

        if (atkStats.getStamina() <= 0) {
            return (float) (KiInfusion.attackDamageOf(attacker) * critMult);
        }

        float scale = consumeAttackScale(attacker);
        double chargeF = 0.2 + scale * scale * 0.8;

        // Bonus de daño de vanilla (Filo, Aspereza, el "smash" de la maza por altura de caída,
        // cualquier otro mod que toque el mismo número) como multiplicador sobre STR — ver
        // KiInfusion.enchantMultiplier. Un arma de ki NO pasa por aquí: su multiplicador ya es
        // un número de datapack (weaponMultiplier), ajeno al atributo attack_damage en el que
        // se basa este cociente.
        KiWeaponItem heldKiWeapon = KiWeaponServer.heldWeapon(attacker);
        double enchantMult = heldKiWeapon == null
                ? KiInfusion.enchantMultiplier(attacker, originalVanillaDamage, critMult)
                : 1.0;

        double base = strDamage * KiInfusion.weaponMultiplier(attacker) * enchantMult * chargeF;

        double kiWeaponExtra = KiInfusion.kiWeaponExtra(attacker, strDamage, chargeF);
        if (kiWeaponExtra > 0.0) {
            KiWeaponItem w = KiWeaponServer.heldWeapon(attacker);
            int wCost = (int) Math.ceil(KiInfusion.kiCost(atkStats, kiWeaponExtra)
                    * (w == null ? 1.0 : w.def().kiCostMult()));
            if (atkStats.getEnergy() >= wCost) {
                atkStats.consumeEnergy(wCost);
            } else {
                base = strDamage * chargeF;   // sin ki: pega como si fuera a mano limpia
            }
        }

        // Los dos bonus se calculan POR SEPARADO porque el Black Flash se engancha SOLO a
        // Ki Fist: con el interruptor puesto pero sin energía, spendForMelee cae a 0 en
        // silencio, y sin este desglose habría procs gratis con la barra vacía.
        double fistBonus   = KiFist.spendForMelee(attacker, atkStats, chargeF);
        double infuseBonus = KiInfusion.spendForMelee(attacker, atkStats, chargeF);
        double bonus = fistBonus + infuseBonus;

        int staminaCost = (int) Math.ceil(strDamage * chargeF
                * CommonConfig.meleeStaminaPerHit() * atkStats.staminaCostMult());
        if (staminaCost > 0) atkStats.consumeStamina(staminaCost);

        // El crítico multiplica el golpe COMPLETO (base de arma + bonus de ki), igual que en
        // vanilla multiplica el float final antes de hurt() — no solo la parte de STR.
        double total = (base + bonus) * critMult;

        // BLACK FLASH sobre el golpe COMPLETO y en crudo: lo que sale de aquí es lo que entra
        // a mitigate(), o sea antes de defensa, armadura y barreras.
        // Gatea en fistBonus y NO en el total: es una mecánica de puño. Ki Infuse queda fuera
        // a propósito (y además exige arma en mano por su propio hasWeapon, así que a puño
        // limpio su bonus siempre es 0).
        if (fistBonus > 0.0) {
            ServerPlayer atkSp = (ServerPlayer) attacker;
            total = BlackFlash.apply(atkSp, victim, scale, chargeF, atkStats, total);
        }

        PlayerLifeCycle.syncIfServer(attacker);
        return (float) total;
    }

    // =====================================================================
    // LADO DEFENSOR
    // =====================================================================

    /** Víctima CON stats zenkai: mitiga con DEF y el daño va al pool body. */
    private static void applyToZenkaiVictim(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                            ZenkaiCombatStats defStats, float dmg,
                                            double armorMult, boolean blackFlash,
                                            boolean environmental) {
        int bodySpent = 0;
        double finalDamage = 0.0;
        if (dmg > 0f) {
            finalDamage = mitigate(e, atkStats, defStats, dmg, armorMult, environmental);

            int bodyBefore = defStats.getBody();
            defStats.addBody(-(int) Math.ceil(finalDamage));
            bodySpent = bodyBefore - defStats.getBody();

            grantTraining(e, Math.min(finalDamage, bodyBefore));
        }

        // JUGADOR: sin cambios. Su vida vanilla la escribe PlayerLifeCycle y el daño se anula.
        if (e.getEntity() instanceof Player victim) {
            e.setNewDamage(0.0F);
            if (defStats.getBody() <= 0 && victim instanceof ServerPlayer sp) {
                // La muerte real (si llega) pasa por DownedSystem 5 s después de esto, ya sin
                // rastro del golpe que la causó — DeathCauseTracker es lo que le permite al
                // mensaje de muerte nombrar la causa en vez de caer en el genérico.
                // Black Flash es la única excepción a "usa e.getSource() tal cual": el golpe
                // base sigue siendo playerAttack de vanilla (mismo entity, atacante), pero con
                // el proc queremos que el mensaje lo diga — de ahí el DamageType propio en vez
                // del que trae el evento.
                DamageSource cause = blackFlash
                        ? sp.damageSources().source(ModDamageTypes.BLACK_FLASH, e.getSource().getEntity())
                        : e.getSource();
                DeathCauseTracker.record(sp, cause);

                PlayerStatsAttachment vAtt = PlayerStatsAttachment.get(sp);
                if (isOverkillOnImmortal(vAtt, finalDamage)) {
                    killImmortalOutright(sp, vAtt);
                } else {
                    onBodyDepleted(sp, vAtt);
                }
            }
            PlayerLifeCycle.syncIfServer(victim);
            return;
        }

        // ENTIDAD: el golpe YA NO SE ANULA. El body sigue siendo la vida real, pero lo que se
        // le ha quitado se traduce a su equivalente en vida vanilla y lo aplica VANILLA por su
        // propio camino.
        // Anularlo era lo que dejaba fuera de juego lo que escucha el pipeline: barras
        // de jefe (vanilla y de otros mods), umbrales de fase, HUD de terceros, advancements y
        // LivingDamageEvent.Post. Para el conjunto de ellos la entidad nunca había recibido un golpe.
        // El número es el MISMO que escribía el espejo, solo cambia quién lo escribe; y
        // EntityHealthSync.onDamagePost vuelve a cuadrar la vida con el pool después, así que
        // el redondeo de vanilla no puede acumular deriva.
        LivingEntity le = e.getEntity();
        if (defStats.getBody() <= 0) {
            e.setNewDamage(Math.max(le.getHealth(), 1.0F));
            return;
        }

        int max = defStats.getBodyMax();
        float equivalent = (max <= 0) ? 0.0F : le.getMaxHealth() * (bodySpent / (float) max);
        // Techo: con body > 0 la entidad NO puede morir por esta vía. Matar sigue siendo cosa
        // de la rama de arriba, que es la única que lee el pool agotado.
        float ceiling = Math.max(0.0F, le.getHealth() - 0.01F);
        e.setNewDamage(Math.min(equivalent, ceiling));
    }

    /** Aplica DEF (salvo ambiental), armadura vanilla, bloqueo, barrera de ki y absorción.
     *  @return daño que llega al body. */
    private static double mitigate(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                   ZenkaiCombatStats defStats, float dmg, double armorMult,
                                   boolean environmental) {
        double finalDamage;

        if (environmental) {
            // AMBIENTAL (lava, fuego, ahogamiento): sin atacante, así que ni STR (no hay quién
            // pegue) ni DEF (calibrada para un rival cuyo golpe TAMBIÉN escala con Power Level)
            // tienen sentido — un chorro fijo de vanilla (4 de lava, 2 de ahogamiento) quedaba
            // aplastado a nada contra la DEF de cualquier personaje con progresión. En su lugar
            // se traduce al MISMO % de vida vanilla que este golpe se habría llevado, aplicado
            // sobre el bodyMax: el % de vida perdido por un peligro del mundo es igual con PL
            // 300 o con PL 3.000.000, que es justo lo que un peligro ambiental (no un rival)
            // debería hacer.
            // `dmg` YA es post-armadura (vanilla la aplicó antes de este evento, ver
            // LivingDamageEvent.Pre#getContainer) — NO se multiplica por armorMult otra vez
            // aquí, o Protección/Resistencia se cobrarían dos veces sobre esta rama.
            float vanillaMax = e.getEntity().getMaxHealth();
            double pct = vanillaMax > 0f ? (dmg / vanillaMax) : 0.0;
            finalDamage = pct * defStats.getBodyMax();
        } else {
            double defense = computeDefense(e, atkStats, defStats, dmg);
            finalDamage = (defense <= 0.0)
                    ? dmg
                    : dmg * (1.0 - defense / (defense + dmg));

            finalDamage *= armorMult;
            finalDamage = Math.max(finalDamage, dmg * CommonConfig.minDamagePercent());
        }

        if (e.getEntity() instanceof ServerPlayer defSp && KiCombatServer.isBlocking(defSp)) {
            finalDamage *= SkillEffects.blockDamageMultiplier(defSp);
        }

        if (e.getEntity() instanceof ServerPlayer defSp) {
            // Barrera de ki (habilidad activa) ANTES que la absorción (consumible): lo que
            // gastas gestionando tú se consume antes que lo que te comiste hace diez minutos.
            finalDamage = KiCombatServer.absorb(defSp, finalDamage);
            finalDamage = VanillaMitigation.consumeAbsorption(defSp, defStats, finalDamage);
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

    /** Víctima SIN stats (mob vanilla): recibe el daño recalculado, con SU armadura contando.
     *  Sin el multiplicador aquí, setNewDamage también le tiraba la reducción a la basura y
     *  un golem de hierro encajaba lo mismo que una gallina. */
    private static void applyToVanillaVictim(LivingDamageEvent.Pre e, float dmg, double armorMult) {
        float finalDamage = (float) Math.max(dmg * armorMult,
                dmg * CommonConfig.minDamagePercent());
        e.setNewDamage(finalDamage);
        if (finalDamage > 0f) grantTraining(e, Math.min(finalDamage, e.getEntity().getHealth()));
    }

    /** TP de entrenamiento al atacante, si es un jugador distinto de la víctima. */
    private static void grantTraining(LivingDamageEvent.Pre e, double amount) {
        Entity source = e.getSource().getEntity();
        if (source instanceof ServerPlayer trainer && trainer != e.getEntity()) {
            TrainingHooks.grantFromDamage(trainer, amount, victimPowerLevel(e.getEntity()));
        }
    }

    /** PL de la víctima para el factor de diferencia de poder. Mismo fallback que
     *  EntityDeathRewardHandler: sin stats zenkai se deriva de la vida máxima. */
    private static long victimPowerLevel(LivingEntity victim) {
        ZenkaiCombatStats st = ZenkaiStats.of(victim);
        if (st != null) return PowerLevel.compute(st);
        return Math.max(1L, Math.round(victim.getMaxHealth() * CommonConfig.vanillaPowerLevelFactor()));
    }

    // =====================================================================
    // DERRIBADO
    // =====================================================================

    /**
     * ¿Este golpe, por sí solo, es "mayor de lo que el cuerpo puede absorber"? Es la promesa del
     * deseo de inmortalidad (screen.zenkai.wish.immortal.warning) tomada al pie de la letra: se
     * compara contra el body MÁXIMO (la capacidad, no lo que quedaba antes del golpe), así que
     * un rival rematando un body ya casi vacío con un golpe pequeño NO cuenta como overkill — el
     * cuerpo entero de este jugador, a plena carga, tampoco habría aguantado ese golpe. Con
     * finalDamage por debajo del umbral, la inmortalidad sigue funcionando como siempre: cae
     * derribado y ImmortalityEffect lo levanta.
     */
    private static boolean isOverkillOnImmortal(PlayerStatsAttachment att, double finalDamage) {
        return att.isImmortal()
                && finalDamage >= att.getBodyMax() * CommonConfig.immortalOverkillFraction();
    }

    /**
     * Un inmortal acaba de recibir un golpe overkill: ni el derribado ni la regeneración de
     * ImmortalityEffect le dan una oportunidad. Vivo, esto es una muerte real (mismo camino que
     * DownedSystem usa al expirar un derribado normal — allowRealDeath primero, o
     * DownedDeathGuard la cancelaría igual que cancelaría cualquier otra). En el Otro Mundo no
     * hay una segunda muerte que dar: el equivalente es el mismo reseteo que ya usa
     * OtherworldManager para cualquier golpe que lo tumbe allí.
     */
    private static void killImmortalOutright(ServerPlayer sp, PlayerStatsAttachment att) {
        if (att.isInOtherworld()) {
            OtherworldManager.keepInOtherworld(sp);
            return;
        }
        DownedDeathGuard.allowRealDeath(sp);
        sp.setHealth(0.0F);
        DamageSource cause = DeathCauseTracker.take(sp.getUUID());
        sp.die(cause != null ? cause : sp.damageSources().generic());
    }

    /**
     * Body agotado: entra en "derribado" (acostado) 5 s, EN EL OTRO MUNDO IGUAL QUE EN EL VIVO
     * — antes se saltaba el derribado allí y se reanclaba en silencio nada más tocar 0, así que
     * el combate/entrenamiento en el Otherworld no tenía ninguna consecuencia visible. Lo único
     * que cambia según dónde esté es el DESENLACE al expirar los 5 s sin curarse, y eso lo
     * decide DownedSystem.handleDowned, no aquí: vivo, muere de verdad y pasa al otro mundo; ya
     * en el otro mundo, no hay una segunda muerte que dar — se re-ancla igual que antes, solo
     * que ahora tras pasar por el mismo derribado (con su ventana para que lo curen) en vez de
     * saltárselo entero.
     * LA INMORTALIDAD NO SE MIRA AQUÍ TAMPOCO. Un inmortal cae derribado como cualquiera y se
     * levanta solo porque ImmortalityEffect le devuelve body muy rápido — salvo que el golpe que
     * lo tumbó sea "mayor de lo que su cuerpo puede absorber" (ver
     * CombatZenkaiHooks.isOverkillOnImmortal), caso que ni siquiera llega a esta clase: se
     * resuelve antes, en applyToZenkaiVictim, precisamente para que ni el derribado ni la
     * regeneración le den una oportunidad.
     */
    private static void onBodyDepleted(Player victim, PlayerStatsAttachment att) {
        if (!(victim instanceof ServerPlayer sp)) return;

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
        ZenkaiTriggers.MILESTONE.get().trigger(target, ZenkaiTriggers.Kinds.REVIVED);
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
     * getAttackStrengthScale devolvería ~0 para cualquier golpe.
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
        CRIT_MULT.remove(playerId);
    }

    // =====================================================================
    // CRÍTICO VANILLA (salto+caída)
    // =====================================================================

    /**
     * Multiplicador de crítico capturado en CriticalHitEvent — dispara DENTRO de
     * Player.attack(), antes que LivingDamageEvent.Pre, exactamente igual que ATTACK_SCALE de
     * arriba y por la misma razón: para cuando corre este evento ya no queda ninguna señal de
     * "fue crítico" que leer del lado vanilla. Guarda {multiplicador, tickCount}: el tick
     * descarta una entrada vieja para que un daño que no venga de un golpe de Player.attack()
     * (una técnica, un proyectil a distancia) no herede un crítico ajeno.
     */
    private static final Map<UUID, float[]> CRIT_MULT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        float mult = e.isCriticalHit() ? e.getDamageMultiplier() : 1.0f;
        CRIT_MULT.put(e.getEntity().getUUID(), new float[]{ mult, e.getEntity().tickCount });
    }

    /** Consume el multiplicador crítico capturado. 1.0 (neutro) si no hay uno fresco. */
    private static float consumeCritMultiplier(Player p) {
        float[] v = CRIT_MULT.remove(p.getUUID());
        if (v == null || p.tickCount - v[1] > 1) return 1.0f;
        return v[0];
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

    /**
     * Con el espejo puesto, la regeneración natural de vanilla es un problema silencioso: sube
     * la vida (que el siguiente sync vuelve a bajar) y de paso QUEMA saturación para curar algo
     * que no existe. El jugador pierde comida a cambio de nada.
     * La curación de verdad entra por el pool (RegenSystem, senzu, ImmortalityEffect) y el
     * espejo la refleja sola. Sin raza no se toca: ahí manda vanilla.
     */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!CommonConfig.mirrorHealth()) return;
        if (!PlayerStatsAttachment.get(sp).isRaceChosen()) return;
        e.setCanceled(true);
    }
}