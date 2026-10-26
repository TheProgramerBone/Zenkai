package com.hmc.zenkai.event;

import com.hmc.zenkai.feature.combat.DashServer;
import com.hmc.zenkai.feature.sense.DamageNumberPacket;

import com.hmc.zenkai.event.tick.OverlayMetersSystem;
import com.hmc.zenkai.content.entity.ai.BlockingMob;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.kiweapon.KiWeaponDef;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.combat.*;
import com.hmc.zenkai.feature.kiweapon.KiWeaponServer;
import com.hmc.zenkai.feature.party.PartyService;
import com.hmc.zenkai.registry.ModDamageTypes;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.registry.ModSounds;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.OtherworldManager;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import com.hmc.zenkai.feature.technique.PhysicalCombatServer;
import com.hmc.zenkai.feature.training.TrainingHooks;
import com.hmc.zenkai.event.tick.DownedSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
 * Un jugador SIN raza no queda fuera: entra con el perfil del Humano base (UnracedBaseline),
 * tanto al pegar como al recibir.
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

    /**
     * Esquive automático de Ultra Instinct. Vive en LivingIncomingDamageEvent y CANCELA el
     * golpe, en vez de poner el daño a 0 en LivingDamageEvent.Pre: ahí vanilla ya decidió
     * reproducir la animación de daño (parpadeo rojo, sacudida de cámara, sonido de golpe),
     * así que un esquive con daño 0 seguía "doliendo" en pantalla. Cancelar aquí hace que
     * hurt() devuelva false antes de nada de eso. Se repiten las exclusiones que onDamage
     * aplica primero (daño inesquivable, fuego amigo, i-frames) para no gastar ki en ellas.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        // NPC con kiai en su ficha: puede parar el golpe cuerpo a cuerpo (ver ParryServer.tryMobParry).
        if (e.getEntity() instanceof com.hmc.zenkai.content.entity.ZenkaiDefaultMob mob) {
            if (!e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    && ParryServer.tryMobParry(mob, e.getSource())) {
                e.setCanceled(true);
            }
            return;
        }
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.level().isClientSide()) return;
        // Sangre fría del Arcosiano: el frío (nieve en polvo) no le hace daño.
        if (e.getSource().is(DamageTypeTags.IS_FREEZING)
                && com.hmc.zenkai.feature.race.RacePassives.immuneToCold(sp)) {
            e.setCanceled(true);
            return;
        }
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (e.getSource().getEntity() instanceof ServerPlayer attacker
                && PartyService.friendlyFireBlocked(attacker, sp)) return;
        if (PhysicalCombatServer.hasIFrames(sp.getUUID()) || DashServer.hasIFrames(sp)) return;
        // PARRY: antes que el esquive de UI. Cancelar aquí (y no a 0 en Pre) evita la animación de
        // daño: el golpe parreado no «duele». Los proyectiles de ki se desvían en
        // KiProjectileEntity.onHitEntity, antes de llegar a hacer daño.
        if (ParryServer.tryParry(sp, e.getSource())) { e.setCanceled(true); return; }
        if (OverlayMetersSystem.tryDodge(sp)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre e) {
        if (e.getEntity().level().isClientSide()) return;

        // Daño que vainilla declara inesquivable: /kill y el vacío. Sale de aquí sin tocar
        // nada para que lo aplique vanilla tal cual.
        // Va lo PRIMERO, antes incluso que los i-frames: no tiene sentido esquivar un /kill.
        // Sin esto, /kill entraba al pool como un golpe más y dejaba al jugador derribado en
        // vez de muerto — y a un inmortal ni eso.
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
                && (PhysicalCombatServer.hasIFrames(dodger.getUUID()) || DashServer.hasIFrames(dodger))) {
            e.setNewDamage(0.0F);
            return;
        }
        // (El esquive automático de Ultra Instinct ya se resolvió en onIncomingDamage, antes de
        // que vanilla aplique la animación de daño: un golpe esquivado nunca llega hasta aquí.)
        MinecraftServer server = e.getEntity().getServer();
        if (server == null) return;
        if (!ModGameRules.enableRaceBoosts(server)) {
            scaleKiToVanilla(e);
            return;
        }

        // La mitigación vanilla se lee AQUÍ, antes de que computeAttackDamage descarte el
        // daño entrante. Después ya es tarde: el ratio vive en el número que sustituimos.
        double armorMult = VanillaMitigation.armorMultiplier(e);

        // combatStatsOf y no ZenkaiStats.of: un jugador SIN raza entra con el perfil del Humano
        // base (UnracedBaseline) en vez de quedar fuera del pipeline como un mob vanilla.
        ZenkaiCombatStats atkStats = UnracedBaseline.combatStatsOf(e.getSource().getEntity());
        float dmg = computeAttackDamage(e, atkStats, armorMult);
        // Contraataque tras un parry: el primer golpe saliente del defensor sale reforzado.
        if (dmg > 0f && e.getSource().getEntity() != e.getEntity()) {
            dmg *= ParryServer.consumeCounterMult(e.getSource().getEntity());
        }

        // Se consume AQUÍ, inmediatamente después de computeAttackDamage (el único sitio que
        // puede haberlo marcado, vía BlackFlash.apply dentro de playerMeleeDamage) y ANTES de
        // repartir a applyToZenkaiVictim/applyToVanillaVictim — así nunca sobrevive de un golpe
        // a otro. Ver BlackFlash.consumeProc.
        boolean blackFlash = BlackFlash.consumeProc(e.getEntity().getUUID());

        ZenkaiCombatStats defStats = UnracedBaseline.combatStatsOf(e.getEntity());
        if (UnracedBaseline.is(defStats)) {
            applyToUnracedVictim(e, atkStats, defStats, dmg, armorMult);
            return;
        }
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
                return (float) (atkStats.computeMeleeFinal() * ServerConfig.mobProjectileFactor());
            }
            // Flecha vanilla (sin infusionar) disparada por un jugador: mismo doble conteo de
            // armadura que la rama infusionada de arriba, mismo arreglo.
            return armorMult > 1.0e-6 ? (float) (dmg / armorMult) : dmg;
        }

        double strDamage = atkStats.computeMeleeFinal();

        if (UnracedBaseline.is(atkStats) && e.getSource().getEntity() instanceof Player unraced) {
            return unracedMeleeDamage(unraced, strDamage, VanillaMitigation.originalDamage(e));
        }
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
        if (e.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            double full = ServerConfig.explosionReferenceDamage();
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
        KiWeaponDef heldKiWeapon = KiWeaponServer.heldWeapon(attacker);
        double enchantMult = heldKiWeapon == null
                ? KiInfusion.enchantMultiplier(attacker, originalVanillaDamage, critMult)
                : 1.0;

        double base = strDamage * KiInfusion.weaponMultiplier(attacker) * enchantMult * chargeF;

        double kiWeaponExtra = KiInfusion.kiWeaponExtra(attacker, strDamage, chargeF);
        if (kiWeaponExtra > 0.0) {
            KiWeaponDef w = KiWeaponServer.heldWeapon(attacker);
            int wCost = (int) Math.ceil(KiInfusion.kiCost(atkStats, kiWeaponExtra)
                    * (w == null ? 1.0 : w.kiCostMult()));
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
                * ServerConfig.meleeStaminaPerHit() * atkStats.staminaCostMult());
        if (staminaCost > 0) atkStats.consumeStamina(staminaCost);

        // El crítico multiplica el golpe COMPLETO (base de arma + bonus de ki), igual que en
        // vanilla multiplica el float final antes de hurt() — no solo la parte de STR.
        MELEE_REF.put(attacker.getUUID(), strDamage * chargeF);
        double total = (base + bonus) * critMult;
        // Combo ANTES del Black Flash (orden fijado en ComboTracker). Un golpe sin cargar (scale bajo) no
        // cuenta: abanicar el aire no debe alimentar la cadena.
        if (attacker instanceof ServerPlayer comboSp && scale >= 0.5f) {
            total *= com.hmc.zenkai.feature.combat.ComboTracker.registerHit(comboSp);
            // Remate (cada Nº golpe seguido a mano vacía): aleja al rival.
            if (comboSp.getMainHandItem().isEmpty()
                    && com.hmc.zenkai.feature.combat.ComboTracker.isFinisher(comboSp)) {
                com.hmc.zenkai.feature.combat.ComboTracker.queueFinisher(comboSp, victim);
            }
        }

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

    /**
     * Golpe cuerpo a cuerpo de un jugador SIN raza (perfil UnracedBaseline). Igual que el de
     * playerMeleeDamage pero sin compuerta de modo combate (no puede activarlo), sin estamina,
     * sin ki y sin Black Flash: STR del Humano base x arma x encantamientos x carga x crítico.
     * Antes salía su daño vanilla (1-7), que contra la DEF y el pool de un rival con raza (o de
     * un mob con stats) no hacía nada.
     */
    private static float unracedMeleeDamage(Player attacker, double strDamage,
                                            double originalVanillaDamage) {
        float critMult = consumeCritMultiplier(attacker);
        float scale = consumeAttackScale(attacker);
        double chargeF = 0.2 + scale * scale * 0.8;
        double enchantMult = KiInfusion.enchantMultiplier(attacker, originalVanillaDamage, critMult);
        MELEE_REF.put(attacker.getUUID(), strDamage * chargeF);
        return (float) (strDamage * KiInfusion.weaponMultiplier(attacker) * enchantMult
                * chargeF * critMult);
    }

    // =====================================================================
    // LADO DEFENSOR
    // =====================================================================

    /**
     * Víctima: jugador SIN raza (perfil UnracedBaseline). Su vida vanilla es la de verdad; el
     * golpe se mitiga con la DEF del Humano base y se aplica el MISMO % de vida vanilla que se
     * llevaría de un body base. Así un golpe que a un Humano recién creado le quita un tercio, a
     * él también — ni lo mata de uno (antes: STR entera contra 20 de vida) ni lo deja intocable.
     * Sin atacante con stats (lava, caída, /damage, un mob sin stats) no se toca nada: eso es
     * daño vanilla contra vida vanilla, ya cuadra. Sin derribado: muere como en vanilla.
     */
    private static void applyToUnracedVictim(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                             ZenkaiCombatStats defStats, float dmg,
                                             double armorMult) {
        if (atkStats == null || !atkStats.isCombatActive()) return;
        if (dmg <= 0f) {
            e.setNewDamage(0.0F);
            return;
        }
        double finalDamage = mitigate(e, atkStats, defStats, dmg, armorMult, false, false);
        float vanillaMax = e.getEntity().getMaxHealth();
        float equivalent = (float) (finalDamage / defStats.getBodyMax() * vanillaMax);
        e.setNewDamage(Math.max(0.0F, equivalent));
        if (equivalent > 0f) {
            showDamageNumber(e, finalDamage, false);
            grantTraining(e, Math.min(finalDamage, defStats.getBody()));
        }
    }

    /**
     * Número de daño flotante para el atacante (solo jugadores con Ki Sense, ver DamageNumberPacket).
     * Se llama con el daño REAL tras la mitigación, no con el entrante, para que el número sea lo que
     * de verdad se le quitó a la víctima. Los flags solo cambian el color en el cliente.
     */
    private static void showDamageNumber(LivingDamageEvent.Pre e, double amount, boolean blackFlash) {
        int flags = 0;
        if (blackFlash) flags |= DamageNumberPacket.FLAG_BLACK_FLASH;
        if (isBlockingNow(e.getEntity())) flags |= DamageNumberPacket.FLAG_BLOCKED;
        if (e.getSource().is(ModDamageTypes.KI_DAMAGE_TAG)) flags |= DamageNumberPacket.FLAG_KI;
        DamageNumberPacket.sendToAttacker(e.getSource().getEntity(), e.getEntity(), amount, flags);
    }

    /**
     * Gamerule zenkai_enableRaceBoosts en false: el combate va por vida vanilla, pero los
     * proyectiles y zonas de ki siguen saliendo con su daño de escala Zenkai (kiPower, cientos o
     * miles) y mataban de un toque. Se traduce a escala vanilla con la misma regla de % que el
     * resto: la fracción del body del LANZADOR que vale el golpe, sobre la vida máxima vanilla
     * del lanzador. Un ki blast de un personaje recién creado queda en unos pocos corazones y
     * escala con su progreso igual que antes, solo que en la unidad que ahora se usa.
     */
    private static void scaleKiToVanilla(LivingDamageEvent.Pre e) {
        if (!e.getSource().is(ModDamageTypes.KI_DAMAGE_TAG)) return;
        if (!(e.getSource().getEntity() instanceof LivingEntity caster)) return;
        ZenkaiCombatStats st = ZenkaiStats.of(caster);
        if (st == null || !st.isCombatActive() || st.getBodyMax() <= 0) return;
        e.setNewDamage((float) (e.getNewDamage() / st.getBodyMax() * caster.getMaxHealth()));
    }

    /** Víctima CON stats zenkai: mitiga con DEF y el daño va al pool body. */
    private static void applyToZenkaiVictim(LivingDamageEvent.Pre e, ZenkaiCombatStats atkStats,
                                            ZenkaiCombatStats defStats, float dmg,
                                            double armorMult, boolean blackFlash,
                                            boolean environmental) {
        int bodySpent = 0;
        double finalDamage = 0.0;
        if (dmg > 0f) {
            // Black Flash: atraviesa la guardia del rival (no su defensa), ver mitigate.
            finalDamage = mitigate(e, atkStats, defStats, dmg, armorMult, environmental, blackFlash);
            // Cuerpo elástico del Majin (RacePassives.mitigate): solo lo físico, por nivel de pasiva.
            if (e.getEntity() instanceof ServerPlayer majin) {
                finalDamage = com.hmc.zenkai.feature.race.RacePassives.mitigate(majin, e.getSource(), (float) finalDamage);
            }

            // «Temerario» (forma Legendaria del humano): su poder se paga con defensa, recibe más daño.
            if (e.getEntity() instanceof ServerPlayer reckless
                    && com.hmc.zenkai.feature.race.RacePassives.wearingHumanLegendary(reckless)) {
                finalDamage *= 1.0 + com.hmc.zenkai.config.ServerConfig.humanLegendDamageTakenExtra();
            }

            int bodyBefore = defStats.getBody();
            defStats.addBody(-(int) Math.ceil(finalDamage));
            bodySpent = bodyBefore - defStats.getBody();

            // Power Points del arcosiano: la contención (Segunda/Tercera) carga peleando y el Arcosian God se repone al pegar.
            if (bodySpent > 0) {
                if (e.getSource().getEntity() instanceof ServerPlayer hitter && hitter != e.getEntity()) {
                    com.hmc.zenkai.event.tick.ArcosianPowerSystem.onCombatHit(hitter, true);
                }
                if (e.getEntity() instanceof ServerPlayer struck) {
                    com.hmc.zenkai.event.tick.ArcosianPowerSystem.onCombatHit(struck, false);
                }
            }

            // «Ancestros» (forma Legendaria del namekiano): una parte del daño que hace le cura el cuerpo.
            if (bodySpent > 0 && e.getSource().getEntity() instanceof ServerPlayer ancestor
                    && ancestor != e.getEntity()
                    && com.hmc.zenkai.feature.race.RacePassives.wearingNamekLegendary(ancestor)) {
                int heal = (int) Math.round(bodySpent * com.hmc.zenkai.config.ServerConfig.namekLegendLifesteal());
                if (heal > 0) {
                    PlayerStatsAttachment.get(ancestor).addBody(heal);
                    PlayerLifeCycle.syncIfServer(ancestor);
                }
            }

            showDamageNumber(e, bodySpent, blackFlash);
            grantTraining(e, Math.min(finalDamage, bodyBefore));
            if (e.getEntity() instanceof ServerPlayer ueVictim) {
                OverlayMetersSystem.onDamaged(ueVictim, bodySpent, defStats.getBodyMax());
            }
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
                    markImmortalOutrightKill(sp);
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
                                   boolean environmental, boolean ignoreGuard) {
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
            finalDamage = Math.max(finalDamage, dmg * ServerConfig.minDamagePercent());

            // PvP: entre jugadores el daño se escala (combat.pvp_damage_scale, 0,5). Sin esto dos iguales se
            // mataban en 1-2 s (simulación 2026-10-15, docs/combate-y-muerte.md). Solo jugador contra jugador:
            // el PvE no cambia.
            if (e.getSource().getEntity() instanceof Player && e.getEntity() instanceof Player
                    && e.getSource().getEntity() != e.getEntity()) {
                finalDamage *= ServerConfig.pvpDamageScale();
            }
        }

        // Black Flash (ignoreGuard): el golpe perfecto atraviesa la guardia. Se eligió la guardia y no la defensa
        // porque entre iguales perforar defensa apenas cambia nada (+2 %) y contra rivales muy superiores lo
        // volvía un mata-gigantes, justo lo que BlackFlash se diseñó para no ser.
        if (!ignoreGuard && isBlockingNow(e.getEntity())) {
            double blockMult = blockDamageMultiplierOf(e.getEntity());
            // Arma que rompe guardias (el hacha): ignora parte de lo que la guardia le quitaría.
            if (e.getSource().getEntity() instanceof Player axeman) {
                KiWeaponDef w = KiWeaponServer.heldWeapon(axeman);
                if (w != null && w.guardBreak() > 0.0) blockMult += (1.0 - blockMult) * w.guardBreak();
            }
            finalDamage *= blockMult;
            // Aguante de guardia: lo que pasó la guardia lo gasta. Va DESPUÉS de consumeBlockMultiplier
            // (que ya cobró durabilidad y ki del escudo) y solo si la guardia sigue en pie (no Black Flash).
            GuardStamina.onBlockedHit(e.getEntity(), finalDamage, defStats.getBodyMax());
        }

        // Jugador sin raza: no tiene barrera de ki y su absorción ya la cobró vanilla sobre su
        // propia vida antes de este evento.
        if (e.getEntity() instanceof ServerPlayer defSp && !UnracedBaseline.is(defStats)) {
            // Barrera de ki (habilidad activa) ANTES que la absorción (consumible): lo que
            // gastas gestionando tú se consume antes que lo que te comiste hace diez minutos.
            finalDamage = KiCombatServer.absorb(defSp, finalDamage);
            finalDamage = VanillaMitigation.consumeAbsorption(defSp, defStats, finalDamage);
        }
        return finalDamage;
    }

    /** ¿Está bloqueando ESTE defensor, sea jugador (ActionState, vía KiCombatServer) o un mob
     *  (BlockingMob, ver ShadowCloneEntity/BlockHabitGoal)? Generaliza lo que antes solo miraba
     *  ServerPlayer — sin esto, un mob nunca podía beneficiarse de bloquear. Público: lo
     *  reutiliza CombatAiUtil.isBlocking para que KiAttackGoal/PhysicalAttackGoal sepan si el
     *  OBJETIVO está en guardia antes de elegir ataque, sin duplicar este mismo chequeo. */
    public static boolean isBlockingNow(LivingEntity defender) {
        if (defender instanceof ServerPlayer sp) return KiCombatServer.isBlocking(sp);
        if (defender instanceof BlockingMob bm) return bm.isBlockingNow();
        return false;
    }

    /** Espejo de isBlockingNow para el multiplicador de daño a aplicar mientras bloquea. Los
     *  jugadores lo sacan de su skill ki_block (0.80..0.50 según nivel); un mob no tiene esa
     *  skill, así que trae su propio número fijo (BlockingMob.blockDamageMultiplier()). */
    private static double blockDamageMultiplierOf(LivingEntity defender) {
        // Una vez por golpe bloqueado: con escudo gasta durabilidad y, imbuido, ki (GuardHands).
        if (defender instanceof ServerPlayer sp) return GuardHands.consumeBlockMultiplier(sp);
        if (defender instanceof BlockingMob bm) return bm.blockDamageMultiplier();
        return 1.0;
    }

    /** Golpe BASE (fuerza x carga, sin arma ni Ki Fist ni crítico) del último golpe cuerpo a cuerpo de cada jugador,
     *  la K de la fórmula de defensa única. Lo escribe playerMeleeDamage/unracedMeleeDamage y lo consume
     *  computeDefense en el mismo evento. */
    private static final java.util.Map<java.util.UUID, Double> MELEE_REF = new java.util.concurrent.ConcurrentHashMap<>();

    /** Tope de perforación de defensa sumando cualquier fuente. */
    private static final double PIERCE_CAP = 0.40;

    /** Perforación total: las fuentes se MULTIPLICAN (dos de 30 % = 51 %), nunca se suman, y no pasan del 40 %. */
    private static double totalPierce(double... sources) {
        double keep = 1.0;
        for (double p : sources) keep *= 1.0 - Math.max(0.0, Math.min(1.0, p));
        return Math.min(PIERCE_CAP, 1.0 - keep);
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
            // La energía se para peor que un puño (y se puede esquivar): solo cuenta parte de la defensa.
            defense *= ServerConfig.kiDefenseFactor();
            // Ultra Ego IMBUYENDO la técnica de ki (aura morada): perfora la MITAD que una técnica física. Con la
            // perforación entera, la técnica de ki ganaba ~4 veces más que una física (la defensa le quita al ki
            // una parte fija y a un golpe grande casi nada); además cuesta un 20 % más de ki (KiCombatServer.ueImbueCostMult).
            defense *= 1.0 - totalPierce(OverlayMetersSystem.uePenetration(e.getSource().getEntity()) * 0.5);
        } else {
            // Proyectil infusionado: misma regla que el ki. La defensa se escala según lo
            // que este disparo vale frente al poder de quien lo lanzó, congelado al salir
            // (el tirador pudo transformarse o morir mientras la flecha volaba).
            KiInfusedShot shot = KiInfusedShot.of(e.getSource().getDirectEntity());
            if (shot.isInfused() && shot.refPower() > 1.0e-6) {
                defense *= (dmg / shot.refPower());
            }
        }
        // FÓRMULA DE DEFENSA ÚNICA (2026-10-15). El ki ya resolvía su defensa como golpe × K / (K + defensa), con
        // K = el poder de quien lo lanza: escalar la defensa por golpe/K convierte golpe²/(golpe+defensa) en eso.
        // Ahora el cuerpo a cuerpo hace lo mismo con K = su golpe BASE sin multiplicadores (fuerza x carga del
        // jugador, o la fuerza de una entidad). Efecto: el puñetazo normal queda EXACTAMENTE igual (golpe = K),
        // incluido el castigo a golpear sin cargar; los multiplicadores (armas, Ki Fist, críticos, técnicas) pasan
        // a ser lineales. Antes rendían más que lineal: el Hammer x2,6 mataba de un golpe a un Saiyan guerrero
        // igual. Solo golpes DIRECTOS: las flechas y proyectiles de mobs siguen con la fórmula de siempre.
        Entity src = e.getSource().getEntity();
        boolean direct = src != null && (e.getSource().getDirectEntity() == src || PhysicalCombatServer.isFiring());
        if (direct && atkStats != null && dmg > 0f && !(e.getSource().getDirectEntity() instanceof KiProjectileEntity)
                && !KiInfusedShot.of(e.getSource().getDirectEntity()).isInfused()) {
            Double ref = PhysicalCombatServer.isFiring() ? null : MELEE_REF.remove(src.getUUID());
            double k = ref != null ? ref : atkStats.computeMeleeFinal();
            if (k > 1.0e-6) defense *= (dmg / k);
        }
        // Ultra Ego: perforación de defensa entera de las técnicas físicas (PhysicalCombatServer marca
        // el puente mientras reparte el daño de la técnica; un puñetazo normal no pasa por aquí).
        if (PhysicalCombatServer.isFiring()) {
            defense *= 1.0 - totalPierce(OverlayMetersSystem.uePenetration(src));
        }
        return defense;
    }

    /** Víctima SIN stats (mob vanilla): recibe el daño recalculado, con SU armadura contando.
     *  Sin el multiplicador aquí, setNewDamage también le tiraba la reducción a la basura y
     *  un golem de hierro encajaba lo mismo que una gallina. */
    private static void applyToVanillaVictim(LivingDamageEvent.Pre e, float dmg, double armorMult) {
        float finalDamage = (float) Math.max(dmg * armorMult,
                dmg * ServerConfig.minDamagePercent());
        e.setNewDamage(finalDamage);
        if (finalDamage > 0f) {
            showDamageNumber(e, Math.min(finalDamage, e.getEntity().getHealth()), false);
            grantTraining(e, Math.min(finalDamage, e.getEntity().getHealth()));
        }
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
        return Math.max(1L, Math.round(victim.getMaxHealth() * ServerConfig.vanillaPowerLevelFactor()));
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
     *
     * CONTRATO: `finalDamage` tiene que ser SIEMPRE la salida de mitigate(), es decir el daño
     * ya pasado por defensa, armadura, suelo de minDamagePercent, BLOQUEO, barrera de ki y
     * absorción — nunca el `dmg` crudo del evento. Es lo que hace que bloquear valga contra un
     * one-shot: mitigate() aplica blockDamageMultiplier() (corta como mínimo un 20%, hasta un
     * 50% con Ki Block al 5) DESPUÉS del suelo de minDamagePercent, así que un golpe que sin
     * bloquear pasaría el umbral puede quedarse por debajo y resolverse como derribado normal
     * — del que un inmortal se levanta solo. Pasar el daño crudo aquí haría que bloquear no
     * cambiara nada frente a la única cosa de la que hay que defenderse.
     */
    private static boolean isOverkillOnImmortal(PlayerStatsAttachment att, double finalDamage) {
        return att.isImmortal()
                && finalDamage >= att.getBodyMax() * ServerConfig.immortalOverkillFraction();
    }

    /**
     * Un inmortal acaba de recibir un golpe overkill: ni el derribado ni la regeneración de
     * ImmortalityEffect le dan una oportunidad. Esto es una muerte real, pero NUNCA se resuelve
     * aquí mismo. Llamar a sp.die() desde dentro de LivingDamageEvent.Pre es reentrante: el
     * evento se dispara en mitad de LivingEntity#actuallyHurt, y al volver, LivingEntity#hurt
     * ve isDeadOrDying() y llama a die() OTRA VEZ (ServerPlayer#die no tiene guardia de
     * reentrada) — con el paquete de muerte ya en camino al cliente. Esa segunda pasada acababa
     * en la rama de derribado normal de DownedDeathGuard, que hace setHealth(1.0F), y vanilla
     * ignora en silencio el botón "Reaparecer" de cualquier jugador con vida > 0. Ver el
     * comentario de DownedSystem.PENDING_OUTRIGHT_KILL para la cadena completa.
     *
     * Así que aquí solo suena el golpe y se marca; DownedSystem.handlePendingOutrightKill()
     * resuelve la muerte de verdad en el siguiente tick limpio (PlayerTickEvent.Post), el mismo
     * camino ya probado que usa el timeout del derribado normal — Otro Mundo, allowRealDeath,
     * causa real y reseteo completo de pose/lock incluidos.
     *
     * Un Totem of Undying SÍ salva de este golpe. Como la muerte sale de un tick limpio, o sea
     * fuera de hurt() — el único sitio donde vanilla revisa el totem —,
     * handlePendingOutrightKill replica el chequeo a mano, exactamente igual que
     * DownedSystem.handleDowned para el timeout del derribado.
     */
    private static void markImmortalOutrightKill(ServerPlayer sp) {
        playKnockout(sp);
        DownedSystem.markOutrightKill(sp);
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
     * CombatZenkaiHooks.isOverkillOnImmortal), caso que ni siquiera llega a esta función: se
     * marca antes, en applyToZenkaiVictim, y se resuelve en el siguiente tick limpio,
     * precisamente para que ni el derribado ni la regeneración le den una oportunidad.
     */
    private static void onBodyDepleted(Player victim, PlayerStatsAttachment att) {
        if (!(victim instanceof ServerPlayer sp)) return;

        if (att.flags().isDowned()) return; // ya está derribado

        playKnockout(sp);
        att.flags().setDowned(true);
        att.flags().setDownedUntil(sp.serverLevel().getGameTime() + DOWNED_TICKS);
        PlayerLifeCycle.sync(sp);
    }

    /** El golpe que derriba o mata de verdad a un jugador — ver onBodyDepleted y
     *  markImmortalOutrightKill, los dos únicos sitios que lo llaman. NO se llama desde
     *  DownedSystem: el timeout de 5 s sin curarse no es un golpe, es un temporizador. */
    private static void playKnockout(ServerPlayer sp) {
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                ModSounds.KNOCKOUT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
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
        if (!healer.getMainHandItem().isEmpty() || KiWeaponServer.activeVariant(healer) != null) return;   // mano vacía
        if (!(e.getTarget() instanceof ServerPlayer target)) return;

        // Un jugador derribado no puede revivir a nadie más — mismo chequeo que SenzuBean.
        // interactLivingEntity, necesario aquí a mano porque PlayerInteractEvent tampoco pasa
        // por ActionRules (el embudo que sí bloquea esto para bloquear/físicas/transformar).
        if (PlayerStatsAttachment.get(healer).flags().isDowned()) return;

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
        // Aturdido o agarrado: no puede pegar (el cliente ya descarta los clics; este es el guard autoritativo).
        if (e.getEntity() instanceof ServerPlayer sp
                && (com.hmc.zenkai.feature.combat.StunState.isStunned(sp)
                || com.hmc.zenkai.feature.technique.PhysicalCombatServer.isHeld(sp))) {
            e.setCanceled(true);
        }
        // Cargando una técnica de ki: las manos están ocupadas con la bola, no se puede pegar.
        // Guard del lado servidor del que ya hace el cliente en CombatModeClientState.
        if (e.getEntity() instanceof ServerPlayer sp
                && com.hmc.zenkai.feature.action.ActionStateServer.get(sp).chargingSlot() >= 0) {
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
        if (!ServerConfig.mirrorHealth()) return;
        if (!PlayerStatsAttachment.get(sp).isRaceChosen()) return;
        // Gamerule en false: la vida vanilla vuelve a ser la de verdad y debe regenerarse sola.
        if (sp.getServer() == null || !ModGameRules.enableRaceBoosts(sp.getServer())) return;
        e.setCanceled(true);
    }
}