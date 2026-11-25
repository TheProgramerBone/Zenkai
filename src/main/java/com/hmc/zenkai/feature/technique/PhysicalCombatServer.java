package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.api.event.TechniqueFiredEvent;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.combat.ComboTracker;
import com.hmc.zenkai.feature.combat.StunState;
import com.hmc.zenkai.feature.action.ActionReject;
import com.hmc.zenkai.feature.combat.GuardBreak;
import com.hmc.zenkai.feature.combat.KiFist;
import com.hmc.zenkai.feature.combat.KiInfusion;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.registry.ModSounds;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.mastery.MasteryEffects;
import com.hmc.zenkai.feature.combat.CombatModeServerState;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.technique.vfx.ZenkaiVfxEffect;
import com.hmc.zenkai.feature.technique.vfx.ZenkaiVfxRegistry;
import com.hmc.zenkai.registry.ModDamageTypes;
import com.hmc.zenkai.registry.ModTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ejecución server de técnicas físicas: validación (modo combate, manos vacías, no
 * bloqueando/derribado, desbloqueada, cooldown, stamina) + efecto. El daño sale por el
 * pipeline normal (playerAttack) con dos puentes hacia CombatZenkaiHooks:
 *  - FIRING evita que la rama atacante recalcule el golpe como melee básico.
 *  - defenseScale: la defensa del rival escala con el dmgMult (regla unificada con el ki).
 * Movimientos con DURACIÓN (tick propio, PlayerTickEvent.Post):
 *  - DASH_PUNCH: 8 ticks de embestida, golpea al que toque (una vez a cada uno);
 *    chocar contra pared lo corta.
 *  - BARRAGE: 6 golpes, uno cada 2 ticks (12 ticks = 0.6 s), cono frontal por pulso.
 * Técnicas de espada (weapon_requirement "sword", las enseña Tori-Bot; siempre con el arma en la mano
 * principal, por eso cada tajo hace swing() de la mano: el brazo del arma de ki y su estela lo siguen):
 *  - RISING_SLASH: cono frontal instantáneo que lanza al rival hacia arriba.
 *  - CROSS_SLASH: dos tajos en X, uno cada 4 ticks (8 ticks en total), cono frontal por pulso.
 *  - PIERCING_THRUST: estocada en línea recta (6 ticks), atraviesa y golpea a cada uno una vez.
 *  - SPIN_SLASH: giro completo, círculo alrededor del jugador con empuje radial.
 *  - SKY_CLEAVE: tajo vertical pesado que rompe la guardia; mismo golpe-explosión que HEAVY_BLOW.
 * Morir, caer derribado, bloquear o salir del modo combate cancela el movimiento activo.
 * COSTE: absoluto y escalado con STR (staminaCost), igual que el golpe básico y que el ki.
 * El cliente NO reimplementa la fórmula: llama a staminaCost() para su predicción, porque
 * tenerla duplicada dejó las técnicas muertas cuando la fórmula cambió en un solo lado.
 * Ki Fist NO cambia ese recurso: la estamina se paga igual, y el ki solo cubre el bonus.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PhysicalCombatServer {
    private PhysicalCombatServer() {}

    private static final int DASH_TICKS = 8;      // 0.4 s de trayecto
    private static final int BARRAGE_TICKS = 12;  // 6 pulsos (cada 2 ticks)
    private static final double DASH_SPEED = 1.1;
    private static final int CROSS_TICKS = 8;     // dos tajos: pulsos en ticksLeft 6 y 2
    private static final int THRUST_TICKS = 6;    // estocada; la velocidad sale de range()/THRUST_TICKS

    /**
     * Techo del coste como fracción del pool. El coste escala con STR y el pool con CON,
     * así que un build con STR alta y CON baja llegaba a costes por encima de su barra
     * entera y la técnica quedaba inutilizable PARA SIEMPRE. Con el techo, subir STR la
     * encarece hasta este límite y ahí se queda: siempre quedan al menos 1/0.40 = 2 usos.
     */
    private static final double MAX_COST_PCT_OF_POOL = 0.40;

    /** Cooldowns por jugador+técnica (gameTime de disponibilidad). */
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

    /** Movimiento con duración activo por jugador. */
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private static final class Active {
        final PhysicalTechnique tech;
        final double str;
        final Vec3 dir;
        final Set<Integer> hitIds = new HashSet<>();
        int ticksLeft;
        /** Ticks vividos (DIVE: no choca contra el suelo en los primeros; GRAB: no suelta nada más agarrar). */
        int age;
        /** Altura de salida del picado, para medir cuánto ha caído. */
        double startY;
        /** GRAB: id de la entidad sujeta (-1 si ninguna). */
        int heldId = -1;
        /** GRAB: uuid de la entidad sujeta (para soltarla aunque ya no esté en el nivel del agarrador). */
        UUID heldUuid;
        /** GRAB, skill check del agarrado (solo si es un jugador): aciertos, check en curso, su inicio (-1 = pausa),
         *  cuándo toca el siguiente y la semilla de las zonas. */
        int checksDone;
        int checkIndex = -1;
        long checkStart = -1;
        long nextCheckAt;
        long seed;

        Active(PhysicalTechnique tech, double str, Vec3 dir, int ticks) {
            this.tech = tech;
            this.str = str;
            this.dir = dir;
            this.ticksLeft = ticks;
        }
    }

    // Puente con CombatZenkaiHooks (server thread único).
    private static boolean firing = false;
    private static double defenseScale = 1.0;
    public static boolean isFiring()           { return firing; }
    /** ¿Está en los i-frames de un movimiento? DASH_PUNCH no es solo un golpe: la embestida
     *  ES la esquiva, así que durante sus 8 ticks el jugador no recibe daño. */
    public static boolean hasIFrames(UUID id) {
        Active a = ACTIVE.get(id);
        return a != null && (a.tech == PhysicalTechnique.DASH_PUNCH || a.tech == PhysicalTechnique.EVADE)
                && a.ticksLeft > 0;
    }

    /** ¿Hay un movimiento CON DURACIÓN en curso? Lo consulta ServerActionContext. */
    public static boolean isBusy(java.util.UUID id) {
        return ACTIVE.containsKey(id);
    }

    public static double currentDefenseScale() { return defenseScale; }

    /** Tras zafarse, gameTime hasta el que el rival no puede ser agarrado de nuevo (UUID de la víctima). */
    private static final Map<UUID, Long> REGRAB_BLOCK = new HashMap<>();
    /** «Sin límite» para la cuenta atrás del agarre y la sujeción (20 horas de juego: la suelta es siempre explícita). */
    private static final int HOLD_FOREVER = 1_440_000;
    /** Ticks sin poder volver a ser agarrado tras zafarse. */
    private static final int REGRAB_BLOCK_TICKS = 60;

    /** El agarre en curso cuya víctima es {@code victim}, o null. ACTIVE es diminuto: recorrerlo sale gratis. */
    private static Map.Entry<UUID, Active> holdOf(LivingEntity victim) {
        for (var en : ACTIVE.entrySet()) {
            Active a = en.getValue();
            if (a.tech == PhysicalTechnique.GRAB && a.heldId == victim.getId()) return en;
        }
        for (var en : DEBUG_HOLDS.entrySet()) {
            if (en.getValue().heldId == victim.getId()) return en;
        }
        return null;
    }

    /**
     * Agarres de PRUEBA cuyo agarrador no es un jugador (el muñeco de «/zenkai debug dummy grab»), por UUID del
     * agarrador. Mismo {@link Active}, mismo skill check y mismas salidas que un agarre real; se tickea desde el
     * PlayerTickEvent de la VÍCTIMA ({@link #tickDebugHold}) porque el agarrador no es un jugador. Nunca entra en ACTIVE
     * para que no cuente como «movimiento en curso» de nadie.
     */
    private static final Map<UUID, Active> DEBUG_HOLDS = new HashMap<>();

    /** Empieza un agarre de prueba: {@code grabber} (un muñeco) sujeta a {@code victim} hasta que este se zafe. */
    public static void startDebugGrab(LivingEntity grabber, ServerPlayer victim) {
        releaseHeld(victim);
        Active a = new Active(PhysicalTechnique.GRAB, 0.0, grabber.getLookAngle(), HOLD_FOREVER);
        a.heldId = victim.getId();
        a.heldUuid = victim.getUUID();
        a.seed = victim.getRandom().nextLong();
        a.nextCheckAt = victim.level().getGameTime() + ServerConfig.grabCheckGapTicks();
        DEBUG_HOLDS.put(grabber.getUUID(), a);
        StunState.hold(victim, HOLD_FOREVER);
        sendGrabState(victim, a, 0);
    }

    /** Tick del agarre de prueba que sujeta a este jugador. El muñeco que agarra se queda quieto delante de él. */
    private static void tickDebugHold(ServerPlayer victim) {
        for (var it = DEBUG_HOLDS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Active a = en.getValue();
            if (a.heldId != victim.getId()) continue;
            Entity grabber = victim.serverLevel().getEntity(en.getKey());
            if (grabber == null || !grabber.isAlive() || !victim.isAlive()) {
                it.remove();
                dropHold(victim.getServer(), a, true);
                return;
            }
            a.age++;
            Vec3 pos = grabber.getEyePosition().add(grabber.getLookAngle().scale(1.6))
                    .subtract(0, victim.getBbHeight() * 0.5, 0);
            holdInPlace(victim, pos);
            victim.setDeltaMovement(Vec3.ZERO);
            victim.hurtMarked = true;
            victim.fallDistance = 0.0f;
            tickEscapeCheck(victim, a);
            return;
        }
    }

    /** ¿Está esta entidad sujeta por un agarre ahora mismo? */
    public static boolean isHeld(LivingEntity victim) {
        return holdOf(victim) != null;
    }

    /** ¿Tiene este jugador un agarre en curso (como agarrador)? */
    public static boolean isHolding(ServerPlayer sp) {
        Active a = ACTIVE.get(sp.getUUID());
        return a != null && a.tech == PhysicalTechnique.GRAB && a.heldId >= 0;
    }

    /**
     * Coste de estamina de una técnica. ÚNICA fuente de verdad: la llaman el servidor al
     * ejecutar y el cliente para decidir si merece la pena mandar el packet.
     * Absoluto y escalado con el poder físico (no % del pool): así subir CON da usos de
     * verdad. staminaPct() del datapack ya NO es un porcentaje, es "cuántos golpes básicos
     * cuesta" — de ahí que sus valores estén en el rango 1.9-3.1 y no en 0.15-0.25.
     */
    public static int staminaCost(PlayerStatsAttachment att, PhysicalTechnique t) {
        double raw = att.computeMeleeFinal() * ServerConfig.meleeStaminaPerHit()
                * att.staminaCostMult()
                * t.staminaPct() * MasteryEffects.techCostFactor(att, t.name());
        return (int) Math.max(1, Math.ceil(
                Math.min(raw, att.getStaminaMax() * MAX_COST_PCT_OF_POOL)));
    }

    /**
     * Daño POR IMPACTO del movimiento, sin el bonus de Ki Fist. Misma razón de existir que
     * {@link #staminaCost}: el cliente NO reimplementa la fórmula — la lista de técnicas la
     * usa para enseñar el daño real del jugador en vez del multiplicador crudo del datapack
     * ("x1.2 dmg" no dice nada sin saber contra qué se multiplica).
     * Se excluye el bonus de Ki Fist a propósito: es condicional (hace falta ki suficiente en
     * el momento del golpe) y se cobra aparte, así que meterlo en la vista previa prometería
     * un número que el jugador no siempre va a ver.
     * OJO, es daño POR IMPACTO: BARRAGE y DASH_PUNCH golpean varias veces por uso (ver
     * {@link #hitsPerUse}), así que su número por separado se lee bajo a propósito.
     */
    public static double previewDamage(PlayerStatsAttachment att, PhysicalTechnique t) {
        return att.computeMeleeFinal()
                * MasteryEffects.techDamageFactor(att, t.name())
                * t.dmgMult();
    }

    /**
     * Cuántas veces golpea un uso. 1 para los instantáneos; BARRAGE pulsa cada 2 ticks durante
     * BARRAGE_TICKS, y DASH_PUNCH puede golpear cada tick del trayecto (tope teórico: el
     * trayecto se corta antes si choca con una pared, y hitAll no repite objetivo).
     * Expuesto para que la vista previa pueda avisar de que el daño por impacto no es el daño
     * por uso — sin esto, "x0.3" de BARRAGE se lee como una técnica inútil.
     */
    public static int hitsPerUse(PhysicalTechnique t) {
        return switch (t) {
            case BARRAGE -> BARRAGE_TICKS / 2;
            case CROSS_SLASH -> 2;
            case DASH_PUNCH -> 1;   // hitAll no repite objetivo: un blanco recibe UN golpe
            default -> 1;
        };
    }

    /**
     * Daño PURO de una técnica física a partir de la fuerza efectiva de quien la usa — mismo
     * papel que {@link KiCombatServer#computeDamage(double, KiTechniqueType, int)} para ki. No
     * lee PlayerStatsAttachment ni nada exclusivo de jugador: el input es un double cualquiera
     * (att.computeMeleeFinal() para un jugador, EntityStats.computeMeleeFinal() para un mob),
     * así que {@link com.hmc.zenkai.content.entity.ai.PhysicalAttackGoal} puede usarla sin
     * tocar el resto de esta clase (mastería, bonus de Ki Fist, movimientos con duración) que
     * sigue siendo 100% ServerPlayer.
     */
    public static double computeDamage(double strength, PhysicalTechnique t, double damageMult) {
        return strength * t.dmgMult() * damageMult;
    }

    /** Consulta pura de cooldown. NO lo arranca. La usa ActionResolver. */
    public static boolean isReady(ServerPlayer sp, PhysicalTechnique t) {
        long[] cds = COOLDOWNS.get(sp.getUUID());
        return cds == null || sp.level().getGameTime() >= cds[t.ordinal()];
    }

    /** Corta el movimiento con duración en curso, si lo hay. */
    public static void cancelActive(ServerPlayer sp) {
        Active a = ACTIVE.remove(sp.getUUID());
        if (a != null) dropHold(sp.getServer(), a, true);
    }

    /**
     * Suelta a la víctima de un agarre que termina SIN lanzamiento (interrumpido, agarrador muerto, distancia, fin
     * de la espera): le quita el aturdimiento del agarre y le avisa de que ya no está sujeta. Tolerante a que la
     * víctima ya no exista.
     * @param keepImmunity true = la inmunidad normal de un aturdimiento; false = sin ella, para que el golpe de
     *                     aterrizaje de un lanzamiento todavía pueda aturdir.
     */
    private static void dropHold(net.minecraft.server.MinecraftServer server, Active a, boolean keepImmunity) {
        if (a.tech != PhysicalTechnique.GRAB || a.heldUuid == null || server == null) return;
        for (var lvl : server.getAllLevels()) {
            if (lvl.getEntity(a.heldUuid) instanceof LivingEntity victim) {
                StunState.release(victim, keepImmunity);
                if (victim instanceof ServerPlayer vp) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(vp,
                            com.hmc.zenkai.feature.combat.GrabStatePacket.released());
                }
                return;
            }
        }
    }

    /**
     * El jugador agarrado se zafa por otra vía que el skill check (evasiva, Instant Transmission): termina el
     * agarre de su agarrador sin lanzamiento y le impide volver a agarrarlo al instante.
     * @return si había un agarre que soltar
     */
    public static boolean releaseHeld(ServerPlayer victim) {
        var en = holdOf(victim);
        if (en == null) return false;
        Active a = en.getValue();
        ACTIVE.remove(en.getKey());
        DEBUG_HOLDS.remove(en.getKey());
        dropHold(victim.getServer(), a, true);
        REGRAB_BLOCK.put(victim.getUUID(), victim.level().getGameTime() + REGRAB_BLOCK_TICKS);
        ServerPlayer grabber = victim.getServer().getPlayerList().getPlayer(en.getKey());
        if (grabber != null) {
            com.hmc.zenkai.feature.action.ActionStateServer.clearIf(
                    grabber, com.hmc.zenkai.feature.action.ActionType.PHYSICAL);
        }
        return true;
    }

    /** Pulsación del skill check del agarrado (GrabEscapePressPacket). El servidor juzga con su reloj. */
    public static void onEscapePress(ServerPlayer victim, int checkIndex, float clientElapsed) {
        var en = holdOf(victim);
        if (en == null) return;
        Active a = en.getValue();
        if (a.checkStart < 0 || checkIndex != a.checkIndex) return;   // pausa o check viejo: se ignora
        long now = victim.level().getGameTime();
        double serverElapsed = now - a.checkStart;
        // El cliente ve la aguja con el retraso de la red: se le deja ir algo por detrás del reloj del servidor, pero
        // no por delante ni por mucho detrás.
        double e = Mth.clamp(clientElapsed,
                serverElapsed - com.hmc.zenkai.feature.combat.GrabSkillCheck.LATENCY_TICKS, serverElapsed);
        double pos = com.hmc.zenkai.feature.combat.GrabSkillCheck.needlePos(
                Math.max(0.0, e), ServerConfig.grabNeedleTicks());
        double zs = com.hmc.zenkai.feature.combat.GrabSkillCheck.zoneStart(
                a.seed, a.checkIndex, ServerConfig.grabZoneSize());
        boolean ok = com.hmc.zenkai.feature.combat.GrabSkillCheck.inZone(pos, zs, ServerConfig.grabZoneSize());
        resolveCheck(victim, a, ok);
    }

    /** Cierra el check en curso (acierto, fallo o caducidad) y, si ya hay suficientes aciertos, suelta al agarrado. */
    private static void resolveCheck(ServerPlayer victim, Active a, boolean success) {
        long now = victim.level().getGameTime();
        a.checkStart = -1;
        a.nextCheckAt = now + ServerConfig.grabCheckGapTicks();
        if (success) {
            a.checksDone++;
            if (a.checksDone >= ServerConfig.grabChecksToEscape()) {
                victim.serverLevel().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 1.3f);
                releaseHeld(victim);
                return;
            }
        } else {
            // Fallar (o dejar caducar) castiga con una pausa más larga hasta el siguiente check.
            a.nextCheckAt = now + ServerConfig.grabCheckGapTicks() + ServerConfig.grabFailPenaltyTicks();
        }
        sendGrabState(victim, a, success ? 1 : 2);
    }

    /** Estado del agarre y del check para el agarrado. */
    private static void sendGrabState(ServerPlayer victim, Active a, int lastResult) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(victim,
                new com.hmc.zenkai.feature.combat.GrabStatePacket(true,
                        a.checkStart >= 0 ? a.checkIndex : -1, a.seed, (float) ServerConfig.grabZoneSize(),
                        ServerConfig.grabNeedleTicks(), a.checksDone, ServerConfig.grabChecksToEscape(),
                        a.ticksLeft, lastResult));
    }

    /**
     * Ejecuta el movimiento. NO VALIDA: las guardas, la Matriz A y las cancelaciones ya las
     * resolvió ActionResolver. Llamar desde otro sitio se salta eso.
     */
    public static void execute(ServerPlayer sp, PhysicalTechnique t, int staminaCost) {
        var firedEvent = new TechniqueFiredEvent(sp,
                t.name().toLowerCase(java.util.Locale.ROOT), true, -1);
        NeoForge.EVENT_BUS.post(firedEvent);
        if (firedEvent.isCanceled()) return;
        // El esquive de Ultra Instinto del rival se coordina con la técnica que llega.
        com.hmc.zenkai.feature.skills.UiDodgeChoice.notePhysical(sp, t);

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        long now = sp.level().getGameTime();
        long[] cds = COOLDOWNS.computeIfAbsent(sp.getUUID(),
                k -> new long[PhysicalTechnique.values().length]);

        att.consumeStamina(staminaCost);
        cds[t.ordinal()] = now + t.cooldownTicks();
        att.addTechniqueMastery(t.name(), (float) ServerConfig.techMasteryPerUse());

        // Ki Fist: bonus aparte, cobrado en KI. Sin ki suficiente no hay bonus y el
        // movimiento sale igual: la estamina ya se pagó.
        double fistBonus = 0.0;
        double raw = KiFist.rawBonus(sp, att);
        if (raw > 0.0 && t != PhysicalTechnique.ABSORB && t != PhysicalTechnique.FALSE_MOON) {
            double effective = raw * t.dmgMult() * MasteryEffects.techCostFactor(att, t.name());
            int kiCost = Math.min(KiInfusion.kiCost(att, effective),
                    (int) (att.getEnergyMax() * MAX_COST_PCT_OF_POOL));
            if (att.getEnergy() >= kiCost) {
                att.consumeEnergy(kiCost);
                fistBonus = raw;
            }
        }

        // Técnicas de espada: aplican la MITAD del multiplicador del arma, 1 + (mult - 1) / 2 (Sword x1,25, Hammer
        // x1,8). Antes lo ignoraban y pegaban igual con cualquier arma (2026-10-15). Solo es seguro con la fórmula
        // de defensa única (CombatZenkaiHooks.computeDefense): con la antigua, los multiplicadores rendían más que
        // lineal y esto se habría disparado.
        double weaponF = t.weaponRequirement() == WeaponRequirement.SWORD
                ? 1.0 + (com.hmc.zenkai.feature.combat.KiInfusion.weaponMultiplier(sp) - 1.0) * 0.5 : 1.0;
        double str = (att.computeMeleeFinal() + fistBonus) * weaponF
                * MasteryEffects.techDamageFactor(att, t.name());
        // Orden de multiplicadores fijado en ComboTracker: combo × carga (× Black Flash, que no aplica aquí).
        // La evasiva no golpea: ni suma ni alimenta la cadena.
        if (t != PhysicalTechnique.EVADE && t != PhysicalTechnique.ABSORB && t != PhysicalTechnique.FALSE_MOON) str *= ComboTracker.registerHit(sp);
        int chargeLevel = t == PhysicalTechnique.HEAVY_BLOW ? consumeChargeLevel(sp) : 0;
        if (chargeLevel > 0) {
            str *= 1.0 + chargeLevel * ServerConfig.heavyDamagePerLevel();
            att.consumeStamina((int) Math.ceil(staminaCost * ServerConfig.heavyStaminaPerLevel() * chargeLevel));
        }
        Vec3 look = sp.getLookAngle();

        switch (t) {
            case DASH_PUNCH -> {
                dashImpulse(sp, look);
                ACTIVE.put(sp.getUUID(), new Active(t, str, look, DASH_TICKS));
            }
            case BARRAGE -> ACTIVE.put(sp.getUUID(), new Active(t, str, look, BARRAGE_TICKS));
            case HEAVY_BLOW -> smash(sp, str, t, false, 0.5, 2.5 + chargeLevel * 0.4,
                    1.8 + chargeLevel * ServerConfig.heavyKnockbackPerLevel());
            case KIAI -> kiai(sp, str, t);
            case RISING_SLASH -> risingSlash(sp, str, t);
            case CROSS_SLASH -> ACTIVE.put(sp.getUUID(), new Active(t, str, look, CROSS_TICKS));
            case PIERCING_THRUST -> {
                swingFx(sp, 1.3f, t, 0);
                ACTIVE.put(sp.getUUID(), new Active(t, str, look, THRUST_TICKS));
            }
            case SPIN_SLASH -> spinSlash(sp, str, t);
            case SKY_CLEAVE -> smash(sp, str, t, true, 0.4, 3.0, 1.2);
            case LIGHT_BLADE -> lightBlade(sp, str, t);
            case DIVE -> startDive(sp, str, t);
            case GRAB -> startGrab(sp, str, t);
            case EVADE -> startEvade(sp, str, t);
            case ABSORB -> com.hmc.zenkai.feature.race.MajinAbsorption.throwMass(sp);
            case FALSE_MOON -> com.hmc.zenkai.feature.forms.FalseMoon.cast(sp);
        }
        ZenkaiTriggers.TECHNIQUE_USED.get().trigger(sp,
                t.name().toLowerCase(java.util.Locale.ROOT), 0.0);
        PlayerLifeCycle.sync(sp);
    }

    /** Mantiene la carga de HEAVY_BLOW: se pierde al quedar aturdido, derribado, bloqueando o fuera de combate,
     *  y cada nivel alcanzado suena más agudo. */
    private static void tickCharge(ServerPlayer sp) {
        if (!CHARGE_START.containsKey(sp.getUUID())) return;
        if (StunState.isStunned(sp) || PlayerStatsAttachment.get(sp).flags().isDowned()
                || KiCombatServer.isBlocking(sp) || !CombatModeServerState.isActive(sp.getUUID())) {
            cancelCharge(sp.getUUID());
            return;
        }
        int level = chargeLevelOf(sp);
        if (level > CHARGE_ANNOUNCED.getOrDefault(sp.getUUID(), 0)) {
            CHARGE_ANNOUNCED.put(sp.getUUID(), level);
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS,
                    0.6f, 0.8f + 0.3f * level);
        }
    }

    /** Tick de los movimientos con duración. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        tickCharge(sp);
        tickThrown(sp);
        if (!DEBUG_HOLDS.isEmpty()) tickDebugHold(sp);
        Active a = ACTIVE.get(sp.getUUID());
        if (a == null) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        // isBlocking ya no puede ser cierto aquí: si el jugador empieza a defender, el
        // resolver llama a cancelActive() antes de escribir BLOCK, así que ACTIVE ya está
        // vacío. Se deja como red de seguridad por si alguna ruta futura pone BLOCK sin
        // pasar por el resolver — que es justo lo que no debe ocurrir.
        boolean cancel = att.flags().isDowned()
                || KiCombatServer.isBlocking(sp) || !CombatModeServerState.isActive(sp.getUUID());
        if (cancel) {
            ACTIVE.remove(sp.getUUID());
            dropHold(sp.getServer(), a, true);
            com.hmc.zenkai.feature.action.ActionStateServer.clearIf(
                    sp, com.hmc.zenkai.feature.action.ActionType.PHYSICAL);
            return;
        }

        a.ticksLeft--;
        switch (a.tech) {
            case DASH_PUNCH -> {
                // Mantener velocidad el trayecto y golpear al que toque.
                sp.setDeltaMovement(a.dir.x * DASH_SPEED,
                        Math.max(sp.getDeltaMovement().y, a.dir.y * DASH_SPEED * 0.4),
                        a.dir.z * DASH_SPEED);
                sp.hurtMarked = true;
                if (damageEnabled(sp)) {
                    hitAll(sp, a, sp.getBoundingBox().inflate(1.2), a.str * a.tech.dmgMult());
                }
                if (sp.horizontalCollision) a.ticksLeft = 0; // pared: fin del trayecto
            }
            case BARRAGE -> {
                if (a.ticksLeft % 2 == 0 && damageEnabled(sp)) {
                    // Un pulso: cono frontal (los objetivos pueden recibir varios pulsos).
                    pulse(sp, a.str * a.tech.dmgMult(), a.tech);
                }
            }
            case CROSS_SLASH -> {
                if (a.ticksLeft == 6 || a.ticksLeft == 2) {
                    swingFx(sp, a.ticksLeft == 6 ? 1.2f : 1.0f, a.tech, a.ticksLeft == 6 ? 0 : 1);
                    if (damageEnabled(sp)) slashCone(sp, a.str * a.tech.dmgMult(), a.tech, 0.3);
                }
            }
            case PIERCING_THRUST -> {
                // Línea recta a velocidad fija: el alcance del JSON es lo que recorre en total.
                double speed = a.tech.range() / THRUST_TICKS;
                sp.setDeltaMovement(a.dir.x * speed, Math.max(sp.getDeltaMovement().y, a.dir.y * speed * 0.3),
                        a.dir.z * speed);
                sp.hurtMarked = true;
                if (damageEnabled(sp)) {
                    hitAll(sp, a, sp.getBoundingBox().inflate(1.0), a.str * a.tech.dmgMult());
                }
                if (sp.horizontalCollision) a.ticksLeft = 0;
            }
            case DIVE -> tickDive(sp, a);
            case GRAB -> tickGrab(sp, a);
            case EVADE -> tickEvade(sp, a);
            default -> a.ticksLeft = 0; // no debería: los instantáneos no se registran
        }
        if (a.ticksLeft <= 0) {
            ACTIVE.remove(sp.getUUID());
            // El movimiento terminó por las buenas. Sin esto, ActionState se queda en
            // PHYSICAL/ACTIVE de forma permanente y la Matriz A rechaza lo demás salvo BLOCK.
            com.hmc.zenkai.feature.action.ActionStateServer.clearIf(
                    sp, com.hmc.zenkai.feature.action.ActionType.PHYSICAL);
        }
    }

    // ── Efectos ──────────────────────────────────────────────────────────────

    private static void dashImpulse(ServerPlayer sp, Vec3 look) {
        sp.setDeltaMovement(look.x * 1.6, Math.min(look.y * 1.6, 0.3) + 0.1, look.z * 1.6);
        sp.hurtMarked = true;
    }

    // ── Carga de HEAVY_BLOW ──────────────────────────────────────────────────

    /** gameTime en que el jugador empezó a mantener la tecla de HEAVY_BLOW. El nivel lo calcula SIEMPRE el
     *  servidor al soltar: el cliente solo avisa de cuándo empezó y cuándo suelta, nunca manda el nivel. */
    private static final Map<UUID, Long> CHARGE_START = new HashMap<>();
    /** Último nivel anunciado por sonido, para no repetirlo cada tick. */
    private static final Map<UUID, Integer> CHARGE_ANNOUNCED = new HashMap<>();

    public static void beginCharge(ServerPlayer sp) {
        if (ServerConfig.heavyMaxLevel() <= 0) return;
        CHARGE_START.put(sp.getUUID(), sp.level().getGameTime());
        CHARGE_ANNOUNCED.put(sp.getUUID(), 0);
    }

    public static void cancelCharge(UUID id) {
        CHARGE_START.remove(id);
        CHARGE_ANNOUNCED.remove(id);
    }

    private static int chargeLevelOf(ServerPlayer sp) {
        Long start = CHARGE_START.get(sp.getUUID());
        if (start == null) return 0;
        long held = sp.level().getGameTime() - start;
        return (int) Math.min(ServerConfig.heavyMaxLevel(),
                held / Math.max(1, ServerConfig.heavyChargeTicksPerLevel()));
    }

    /** Nivel de carga al soltar, y apaga la carga. Un toque rápido da 0 (golpe sin cargar de siempre). */
    private static int consumeChargeLevel(ServerPlayer sp) {
        int level = chargeLevelOf(sp);
        cancelCharge(sp.getUUID());
        return level;
    }

    /** Cuerpo común de HEAVY_BLOW y SKY_CLEAVE: golpe directo + onda expansiva. {@code sword} añade la
     *  ruptura de guardia (ANTES del daño, como el kiai) y dibuja tajos además de la explosión. */
    private static void smash(ServerPlayer sp, double str, PhysicalTechnique t, boolean sword,
                              double aoeFrac, double radius, double knock) {
        double dmg = str * t.dmgMult();
        if (sword) swingFx(sp, 0.8f, t, 0);
        Vec3 look = sp.getLookAngle();
        LivingEntity target = firstInRay(sp, t.range(), 1.0);

        Vec3 c;
        if (target != null) {
            c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        } else {
            // Punto de impacto: el bloque en la mirada, o el aire a rango máximo.
            Vec3 eye = sp.getEyePosition();
            Vec3 end = eye.add(look.scale(t.range()));
            var clip = sp.level().clip(new net.minecraft.world.level.ClipContext(
                    eye, end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
            c = clip.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                    ? clip.getLocation() : end;
        }

        if (sword) {
            int breakTicks = ServerConfig.kiaiGuardBreakTicks();
            for (LivingEntity e : sp.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(c, radius * 2, radius * 2, radius * 2),
                    x -> x != sp && x.isAlive())) {
                if (com.hmc.zenkai.feature.combat.GuardBreak.breakGuard(e, breakTicks)) {
                    sp.serverLevel().playSound(null, e.getX(), e.getY(), e.getZ(),
                            net.minecraft.sounds.SoundEvents.SHIELD_BREAK,
                            SoundSource.PLAYERS, 0.9f, 1.1f);
                }
            }
        }

        withBridge(t.dmgMult(), () -> {
            if (target != null) {
                hit(sp, target, dmg, t);
                target.knockback(knock, -look.x, -look.z);
            }

            // Onda expansiva: fracción del daño con caída lineal y empuje radial. Siempre.
            for (LivingEntity e : sp.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(c, radius * 2, radius * 2, radius * 2),
                    x -> x != sp && x != target && x.isAlive())) {
                double dist = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(c);
                if (dist > radius) continue;
                double falloff = 1.0 - dist / radius;
                hit(sp, e, dmg * aoeFrac * falloff, t);
                Vec3 away = e.position().subtract(c).normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * 0.8, 0.25, away.z * 0.8));
                e.hurtMarked = true;
            }
        });

        sp.serverLevel().sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
    }

    /** Tajo ascendente: cono frontal; cualquiera que lo reciba sale disparado hacia arriba (se aplica
     *  DESPUÉS del daño para que el knockback vanilla de hurt() no lo pise). */
    private static void risingSlash(ServerPlayer sp, double str, PhysicalTechnique t) {
        swingFx(sp, 1.4f, t, 0);
        List<LivingEntity> targets = slashCone(sp, str * t.dmgMult(), t, 0.5);
        for (LivingEntity e : targets) {
            Vec3 m = e.getDeltaMovement();
            e.setDeltaMovement(m.x * 0.3, 0.85, m.z * 0.3);
            e.hurtMarked = true;
        }
    }

    /** Tajo giratorio: círculo completo alrededor del jugador, empuje radial. */
    private static void spinSlash(ServerPlayer sp, double str, PhysicalTechnique t) {
        swingFx(sp, 0.9f, t, 0);
        List<LivingEntity> targets = inCone(sp, t.range(), -1.1);
        double dmg = str * t.dmgMult();
        if (damageEnabled(sp)) {
            withBridge(t.dmgMult(), () -> { for (LivingEntity e : targets) hit(sp, e, dmg, t); });
        }
        for (LivingEntity e : targets) {
            double dx = e.getX() - sp.getX(), dz = e.getZ() - sp.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0e-4) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(dx / len, 0.25, dz / len));
            e.hurtMarked = true;
        }
    }

    /** Un tajo en cono frontal. Resetea i-frames como el pulso del barrage: dos tajos seguidos del
     *  mismo uso tienen que conectar los dos, no quedar tapados por la invulnerabilidad vanilla. */
    private static List<LivingEntity> slashCone(ServerPlayer sp, double dmg, PhysicalTechnique t, double minDot) {
        List<LivingEntity> targets = inCone(sp, t.range(), minDot);
        if (!damageEnabled(sp)) return targets;
        withBridge(t.dmgMult(), () -> {
            for (LivingEntity e : targets) {
                e.invulnerableTime = 0;
                hit(sp, e, dmg, t);
            }
        });
        return targets;
    }

    /** Swing de la mano principal (mueve el arma de ki y dispara su estela) + silbido del filo + el arco de
     *  ki del tajo (SwordFxPacket, lo dibuja SwordFx en todos los clientes cercanos), sin la partícula
     *  de barrido vanilla (la sustituyen esos VFX). {@code variant} distingue los dos tajos del corte cruzado. */
    private static void swingFx(ServerPlayer sp, float pitch, PhysicalTechnique t, int variant) {
        sp.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        var lvl = sp.serverLevel();
        lvl.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, pitch);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new SwordFxPacket(sp.getId(), t.ordinal(), variant));
        if (t == PhysicalTechnique.SPIN_SLASH || t == PhysicalTechnique.SKY_CLEAVE) {
            // Polvo levantado alrededor de los pies: el golpe pesa.
            lvl.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 0.1, sp.getZ(), 14, 1.4, 0.05, 1.4, 0.08);
        }
    }

    /** LIGHT_BLADE: suelta la hoja de luz hacia donde mira. Velocidad fija; el alcance del JSON es lo que recorre
     *  antes de apagarse (o hasta chocar con un bloque). */
    private static void lightBlade(ServerPlayer sp, double str, PhysicalTechnique t) {
        swingFx(sp, 1.1f, t, 0);
        double speed = 1.4;
        Vec3 look = sp.getLookAngle();
        var wave = new com.hmc.zenkai.content.entity.technique.SwordWaveEntity(
                com.hmc.zenkai.registry.ModEntities.SWORD_WAVE.get(), sp.level());
        wave.configure(sp, str * t.dmgMult(), (int) Math.ceil(t.range() / speed), look.scale(speed),
                com.hmc.zenkai.feature.aura.AuraColors.resolve(sp), 30);
        Vec3 start = sp.getEyePosition().add(look.scale(0.9)).add(0, -0.35, 0);
        wave.setPos(start.x, start.y, start.z);
        sp.level().addFreshEntity(wave);
    }

    /** Impacto de la hoja de luz (SwordWaveEntity): mismo puente de defensa y mismo funnel que cualquier física. */
    public static void waveHit(ServerPlayer sp, LivingEntity target, double dmg, PhysicalTechnique t) {
        withBridge(t.dmgMult(), () -> hit(sp, target, dmg, t));
    }

    /** Kiai: cono frontal con daño moderado + empujón ESCALADO CON STR (softcap: 0.7 débiles
     *  -> ~1.6 fuerte); desvía proyectiles ki ajenos. El empuje se aplica DESPUÉS del daño
     *  para que no lo pise el knockback vanilla de hurt(). */
    private static void kiai(ServerPlayer sp, double str, PhysicalTechnique t) {
        double push = 0.7 + 0.9 * (str / (str + 100.0)); // 100 = perilla de "STR media"
        double dmg = str * t.dmgMult();
        Vec3 look = sp.getLookAngle();
        // Control de zona: por defecto en CÍRCULO COMPLETO (para cuando te rodean) y no solo el
        // cono frontal. minDot -1 admite cualquier dirección; 0.4 es el cono de siempre.
        boolean omni = ServerConfig.kiaiOmnidirectional();
        List<LivingEntity> targets = inCone(sp, t.range(), omni ? -1.1 : 0.4);

        // RUPTURA DE GUARDIA, ANTES del daño: el golpe del propio kiai ya no se mitiga con una
        // guardia que acaba de romper. Es lo que el parry no hace (el parry defiende).
        int breakTicks = ServerConfig.kiaiGuardBreakTicks();
        int broken = 0;
        for (LivingEntity e : targets) {
            if (com.hmc.zenkai.feature.combat.GuardBreak.breakGuard(e, breakTicks)) {
                broken++;
                com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sp, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.GUARD_BROKEN);
                sp.serverLevel().playSound(null, e.getX(), e.getY(), e.getZ(),
                        net.minecraft.sounds.SoundEvents.SHIELD_BREAK,
                        SoundSource.PLAYERS, 0.9f, 1.1f);
            }
        }

        if (dmg > 0.0 && damageEnabled(sp)) {
            withBridge(t.dmgMult(), () -> {
                for (LivingEntity e : targets) {
                    hit(sp, e, dmg, t);
                }
            });
        }

        // Empujón: radial desde el jugador si es omnidireccional (los aparta a todos), y a lo largo
        // de la mirada si no. Se aplica DESPUÉS del daño para que no lo pise el knockback de hurt().
        for (LivingEntity e : targets) {
            double dx = look.x, dz = look.z;
            if (omni) {
                dx = e.getX() - sp.getX();
                dz = e.getZ() - sp.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 1.0e-4) { dx = look.x; dz = look.z; len = Math.sqrt(dx * dx + dz * dz); }
                if (len > 1.0e-4) { dx /= len; dz /= len; }
            }
            e.setDeltaMovement(e.getDeltaMovement()
                    .add(dx * push, 0.3 + push * 0.15, dz * push));
            e.hurtMarked = true;
        }

        // DESVÍO DE PROYECTILES: se APARTAN hacia un lado y arriba, SIN devolverlos (devolver un
        // proyectil es cosa del parry). Siguen siendo de su dueño y pasan de largo.
        AABB box = sp.getBoundingBox().inflate(t.range());
        java.util.Random rng = new java.util.Random(sp.getRandom().nextLong());
        int scattered = 0;
        for (var proj : sp.serverLevel().getEntitiesOfClass(
                com.hmc.zenkai.content.entity.technique.KiProjectileEntity.class, box,
                pr -> pr.getOwner() != sp && pr.canBeDeflected() && !pr.isClashHeld())) {
            proj.scatter(rng);
            scattered++;
        }

        // COSTE CLARO: cada objetivo extra (enemigo empujado o proyectil desviado) suma una fracción
        // del coste base, con tope. Un kiai que limpia una sala cuesta más que uno a un solo rival.
        int extra = Math.min(Math.max(0, targets.size() + scattered - 1),
                ServerConfig.kiaiExtraTargetCap());
        double frac = ServerConfig.kiaiExtraTargetStamina();
        if (extra > 0 && frac > 0.0) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            att.consumeStamina((int) Math.ceil(staminaCost(att, t) * frac * extra));
        }
    }

    /** Pulso de barrage: cono frontal corto. Resetea i-frames: la ráfaga define su propio
     *  ritmo (6 golpes/0.6 s), no la ventana de invulnerabilidad vanilla. */
    private static void pulse(ServerPlayer sp, double dmgPerHit, PhysicalTechnique t) {
        withBridge(t.dmgMult(), () -> {
            for (LivingEntity e : inCone(sp, t.range(), 0.5)) {
                e.invulnerableTime = 0;
                hit(sp, e, dmgPerHit, t);
            }
        });
    }

    /** Daña una vez a cada entidad nueva dentro de la caja (dash). */
    private static void hitAll(ServerPlayer sp, Active a, AABB box, double dmg) {
        withBridge(a.tech.dmgMult(), () -> {
            for (LivingEntity e : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                    x -> x != sp && x.isAlive() && !a.hitIds.contains(x.getId()))) {
                a.hitIds.add(e.getId());
                hit(sp, e, dmg, a.tech);
            }
        });
    }

    // ── Picado y agarre (tanda D) ────────────────────────────────────────────

    /** Rival lanzado por un Agarre: se vigila desde el tick de SU lanzador hasta que choca o caduca. */
    private static final class Thrown {
        final UUID owner;
        final double str;
        int ticksLeft = 40;
        int age;

        Thrown(UUID owner, double str) {
            this.owner = owner;
            this.str = str;
        }
    }

    private static final Map<UUID, Thrown> THROWN = new HashMap<>();

    /**
     * Condiciones PROPIAS de picado y agarre, que ActionRules no conoce (dependen de vuelo, altura y
     * entidades cercanas). Las llama ActionResolver antes de ejecutar; así el servidor rechaza sin gastar
     * estamina ni cooldown y el cliente deshace su cooldown optimista con el rechazo.
     */
    public static ActionReject checkStart(ServerPlayer sp, PhysicalTechnique t) {
        if (t == PhysicalTechnique.DIVE) {
            if (!sp.getAbilities().flying || groundDistance(sp) < ServerConfig.diveMinHeight()) {
                sp.displayClientMessage(Component.translatable("hud.zenkai.dive_needs_flight"), true);
                return ActionReject.NO_TARGET;
            }
        } else if (t == PhysicalTechnique.GRAB) {
            if (findGrabTarget(sp) == null) {
                sp.displayClientMessage(Component.translatable("hud.zenkai.grab_no_target"), true);
                return ActionReject.NO_TARGET;
            }
        } else if (t == PhysicalTechnique.FALSE_MOON) {
            // Solo un Saiyan la lanza; sin serlo no se gasta estamina ni enfriamiento.
            return com.hmc.zenkai.feature.forms.FalseMoon.canCast(sp) ? ActionReject.OK : ActionReject.NO_RESOURCE;
        } else if (t == PhysicalTechnique.ABSORB) {
            // Sin ser Majin o sin el ki que cuesta NO se gasta estamina ni enfriamiento (MajinAbsorption avisa del
            // motivo). A quién se absorbe se decide al impacto de la masa, no aquí.
            return com.hmc.zenkai.feature.race.MajinAbsorption.canThrow(sp)
                    ? ActionReject.OK : ActionReject.NO_RESOURCE;
        }
        return ActionReject.OK;
    }

    /** Distancia vertical hasta el suelo bajo el jugador (256 si no hay nada debajo). */
    public static double groundDistance(net.minecraft.world.entity.player.Player sp) {
        Vec3 from = sp.position();
        var clip = sp.level().clip(new net.minecraft.world.level.ClipContext(
                from, from.add(0, -256, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
        return clip.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? 256.0 : from.y - clip.getLocation().y;
    }

    private static void startDive(ServerPlayer sp, double str, PhysicalTechnique t) {
        // Siempre hacia abajo: lo que se mira solo reparte el rumbo horizontal.
        Vec3 look = sp.getLookAngle();
        Vec3 dir = new Vec3(look.x * 0.5, Math.min(look.y, -0.7), look.z * 0.5).normalize();
        Active a = new Active(t, str, dir, ServerConfig.diveMaxTicks());
        a.startY = sp.getY();
        ACTIVE.put(sp.getUUID(), a);
    }

    private static void tickDive(ServerPlayer sp, Active a) {
        double speed = ServerConfig.diveSpeed();
        sp.setDeltaMovement(a.dir.scale(speed));
        sp.hurtMarked = true;
        sp.fallDistance = 0.0f;
        a.age++;
        // groundDistance cubre el túnel: a 2 bloques por tick el servidor puede no ver nunca el choque.
        if (a.age > 2 && (sp.onGround() || sp.verticalCollision || sp.horizontalCollision
                || groundDistance(sp) < speed * 0.6)) {
            diveImpact(sp, a);
            a.ticksLeft = 0;
        }
    }

    /** Impacto del picado: onda radial cuyo alcance y daño crecen con lo que se ha caído. Solo visual, sin
     *  romper bloques (decisión de diseño: un cráter real sería un grief en servidores). */
    private static void diveImpact(ServerPlayer sp, Active a) {
        PhysicalTechnique t = PhysicalTechnique.DIVE;
        double fallF = Mth.clamp((a.startY - sp.getY()) / 16.0, 0.5, 1.5);
        double radius = Math.min(ServerConfig.diveMaxRadius(),
                ServerConfig.diveRadiusPerSpeed() * ServerConfig.diveSpeed() * fallF);
        double dmg = a.str * t.dmgMult() * fallF;
        Vec3 c = sp.position();
        var lvl = sp.serverLevel();

        withBridge(t.dmgMult(), () -> {
            for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(c, radius * 2, radius * 2, radius * 2), x -> x != sp && x.isAlive())) {
                double dist = e.position().distanceTo(c);
                if (dist > radius) continue;
                double falloff = 1.0 - 0.6 * dist / radius;
                hit(sp, e, dmg * falloff, t);
                Vec3 away = e.position().subtract(c);
                away = new Vec3(away.x, 0, away.z);
                away = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize();
                double k = ServerConfig.diveKnockback() * falloff;
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * k, 0.4 * falloff, away.z * k));
                e.hurtMarked = true;
            }
        });

        lvl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 3, radius * 0.25, 0.1, radius * 0.25, 0);
        lvl.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, (int) (12 + radius * 6),
                radius * 0.5, 0.05, radius * 0.5, 0.12);
        lvl.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.9f, 0.8f);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hurtMarked = true;
    }

    /** Agarrable: cualquiera a la vista y cerca que no sea jefe ni esté ya lanzado. El más cercano. */
    private static LivingEntity findGrabTarget(ServerPlayer sp) {
        double range = ServerConfig.grabRange();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : inCone(sp, range, 0.5)) {
            if (e.getType().is(ModTags.EntityTypes.UNGRABBABLE) || THROWN.containsKey(e.getUUID())) continue;
            Long blocked = REGRAB_BLOCK.get(e.getUUID());
            if (blocked != null && blocked > sp.level().getGameTime()) continue;   // acaba de zafarse
            if (isHeld(e)) continue;
            if (e instanceof ServerPlayer other && other.isSpectator()) continue;
            double d = e.distanceTo(sp);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    /**
     * EVADE = SUPER EXPLOSIVE WAVE: el jugador se planta INMÓVIL (en el suelo o en el aire), carga el ki comprimido
     * unos ticks (la pose de sentadilla y brazos recogidos la pone el clip phys_evade), abre los brazos y libera una
     * onda omnidireccional sin daño; mantiene la pose rígida mientras la cúpula barre el entorno y se descarga. Es
     * intocable durante el rato entero (hasIFrames). El empuje sale en {@link #tickEvade} al terminar la carga; si estaba
     * agarrado, ese es el momento en que se suelta (y la onda aparta al agarrador).
     */
    private static void startEvade(ServerPlayer sp, double str, PhysicalTechnique t) {
        ACTIVE.put(sp.getUUID(), new Active(t, str, Vec3.ZERO, ServerConfig.evadeTicks()));
        var lvl = sp.serverLevel();
        lvl.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
    }

    /**
     * Onda expansiva SIN daño: empuja en horizontal (y un poco hacia arriba) a cada ser vivo del radio, más fuerte
     * cuanto más cerca, y dibuja un anillo de polvo que se abre desde {@code c}. La evasiva la usa para quitarse de
     * encima a quien la rodea (mobs y jugadores). El aturdimiento opcional respeta los frenos de StunState, y los
     * jefes (UNGRABBABLE) se empujan igual pero no se aturden.
     */
    private static void shockwave(ServerPlayer sp, Vec3 c, double radius, double knock, int stunTicks) {
        if (radius <= 0.0) return;
        var lvl = sp.serverLevel();
        for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(c, radius * 2, radius * 2, radius * 2), x -> x != sp && x.isAlive())) {
            if (e instanceof ServerPlayer o && o.isSpectator()) continue;
            double dist = e.position().distanceTo(c);
            if (dist > radius) continue;
            double falloff = 1.0 - 0.5 * dist / radius;
            Vec3 away = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            away = away.lengthSqr() < 1.0e-4 ? sp.getLookAngle().multiply(1, 0, 1) : away;
            away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
            double k = knock * falloff;
            e.setDeltaMovement(e.getDeltaMovement().add(away.x * k, 0.35 * falloff, away.z * k));
            e.hurtMarked = true;
            if (stunTicks > 0 && !e.getType().is(ModTags.EntityTypes.UNGRABBABLE)) StunState.apply(e, stunTicks);
        }
        // Los ataques de ki ajenos que toca la cúpula salen despedidos (sin devolverlos: eso es del parry).
        java.util.Random rng = new java.util.Random(sp.getRandom().nextLong());
        for (var proj : lvl.getEntitiesOfClass(com.hmc.zenkai.content.entity.technique.KiProjectileEntity.class,
                AABB.ofSize(c, radius * 2, radius * 2, radius * 2),
                pr -> pr.getOwner() != sp && pr.canBeDeflected() && !pr.isClashHeld())) {
            proj.scatter(rng);
        }
        // Cúpula con el VFX del mod y el color del ki del usuario: destello, chispas y arcos (efecto data-driven
        // movement/evade_burst) y tres anillos de motas de energía a distinta altura que se abren a la vez (parece un
        // hemisferio). Partículas con velocidad radial (count 0 = dx,dy,dz es la dirección y speed la fuerza).
        int rgb = com.hmc.zenkai.feature.aura.AuraColors.resolve(sp);
        Vec3 chest = c.add(0, sp.getBbHeight() * 0.6, 0);
        spawnVfx(lvl, "movement/evade_burst", chest, rgb, radius * 0.15);
        int points = 36;
        double[] heights = {0.15, 0.9, 1.7};
        double[] lift = {0.0, 0.05, 0.12};
        float[] scale = {1.3f, 1.0f, 0.8f};
        for (int h = 0; h < heights.length; h++) {
            for (int i = 0; i < points; i++) {
                double ang = i * (Math.PI * 2.0 / points);
                double dx = Math.cos(ang), dz = Math.sin(ang);
                lvl.sendParticles(com.hmc.zenkai.registry.ModParticles.energyMote(rgb, scale[h]),
                        c.x + dx * 0.5, c.y + heights[h], c.z + dz * 0.5, 0, dx, lift[h], dz, radius * 0.1);
            }
        }
        // «Cráter»: solo visual (nada de tocar bloques). Rocas del mod que saltan si hay suelo bajo los pies.
        var below = lvl.getBlockState(net.minecraft.core.BlockPos.containing(c.x, c.y - 0.5, c.z));
        if (!below.isAir()) {
            lvl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            com.hmc.zenkai.registry.ModParticles.AURA_ROCK.get(), below),
                    c.x, c.y + 0.2, c.z, 28, radius * 0.25, 0.1, radius * 0.25, 0.3);
        }
        lvl.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /** Dispara un efecto data-driven del mod (zenkai_vfx_effects/<path>.json) tintado en {@code rgb}. */
    private static void spawnVfx(net.minecraft.server.level.ServerLevel lvl, String path, Vec3 at, int rgb, double spread) {
        ZenkaiVfxEffect fx = ZenkaiVfxRegistry.get(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path));
        if (fx == null) {
            Zenkai.LOGGER.warn("[Zenkai] Sin efecto VFX '{}' (datapack roto/borrado).", path);
            return;
        }
        fx.spawn(lvl, at, rgb, spread);
    }

    private static void tickEvade(ServerPlayer sp, Active a) {
        var lvl = sp.serverLevel();
        int elapsed = Math.max(1, ServerConfig.evadeTicks()) - a.ticksLeft;
        int charge = Math.min(ServerConfig.evadeChargeTicks(), Math.max(1, ServerConfig.evadeTicks() - 1));
        // Anclado: el centro de la onda no se mueve (salvo que lo estén lanzando). En el aire se queda suspendido.
        if (!THROWN.containsKey(sp.getUUID())) {
            double y = sp.getAbilities().flying ? 0.0 : Math.min(sp.getDeltaMovement().y, 0.0);
            sp.setDeltaMovement(0, y, 0);
            sp.hurtMarked = true;
        }
        Vec3 chest = sp.position().add(0, sp.getBbHeight() * 0.6, 0);
        int rgb = com.hmc.zenkai.feature.aura.AuraColors.resolve(sp);
        if (elapsed < charge) {
            // Compresión: el aura vibra pegada al cuerpo (arcos y chispas del VFX del mod, con el color del ki del
            // usuario) y unas motas de energía convergen hacia el pecho.
            if (elapsed % 2 == 0) {
                spawnVfx(lvl, "movement/evade_charge", chest, rgb, 0.6);
                for (int i = 0; i < 6; i++) {
                    double ang = sp.getRandom().nextDouble() * Math.PI * 2.0;
                    double h = sp.getRandom().nextDouble() * 1.6 - 0.6;
                    double r = 1.7;
                    double ox = Math.cos(ang) * r, oz = Math.sin(ang) * r;
                    lvl.sendParticles(com.hmc.zenkai.registry.ModParticles.energyMote(rgb, 1.0f),
                            chest.x + ox, chest.y + h, chest.z + oz, 0, -ox, -h, -oz, 0.09);
                }
            }
        } else if (elapsed == charge) {
            // Punto cero: destello en el pecho y estallido. Un agarrado se suelta AHORA.
            releaseHeld(sp);
            shockwave(sp, sp.position().add(0, 0.1, 0), ServerConfig.evadeWaveRadius(),
                    ServerConfig.evadeWaveKnockback(), ServerConfig.evadeWaveStunTicks());
        }
    }

    private static void startGrab(ServerPlayer sp, double str, PhysicalTechnique t) {
        LivingEntity target = findGrabTarget(sp);
        if (target == null) return;   // checkStart ya lo garantizó en este mismo tick
        // El agarre se hace con la mano CONTRARIA a la principal: la principal queda libre para golpear al agarrado.
        sp.swing(grabHand(sp), true);
        // Agarre SIN límite de tiempo: dura hasta que el agarrado se zafa (skill check, Evasiva, Instant Transmission) o
        // el agarrador lo lanza. grab.hold_ticks > 0 pone un tope opcional (0 = sin tope).
        Active a = new Active(t, str, sp.getLookAngle(),
                ServerConfig.grabHoldTicks() > 0 ? ServerConfig.grabHoldTicks() : HOLD_FOREVER);
        a.heldId = target.getId();
        a.heldUuid = target.getUUID();
        a.seed = sp.getRandom().nextLong();
        a.nextCheckAt = sp.level().getGameTime() + ServerConfig.grabCheckGapTicks();
        ACTIVE.put(sp.getUUID(), a);
        // Inmóvil mientras lo sostienen. hold ignora tope, rendimientos e inmunidad: el agarre manda y lo
        // termina SIEMPRE una suelta explícita (dropHold), así que dura de sobra aunque los fallos lo alarguen.
        StunState.hold(target, HOLD_FOREVER);
        if (target instanceof ServerPlayer vp) sendGrabState(vp, a, 0);
        withBridge(t.dmgMult(), () -> hit(sp, target, 0.0, t));
    }

    /** Mano con la que se agarra: la contraria a la principal. */
    public static net.minecraft.world.InteractionHand grabHand(net.minecraft.world.entity.player.Player p) {
        return p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
                ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND;
    }

    /** Sujeta al rival delante del jugador. Se lanza al acabar la espera o antes, volviendo a activar la técnica
     *  ({@link #throwNow}); los golpes con la mano libre NO lanzan, golpean al agarrado. Si el agarrado es un
     *  jugador, aquí corre también su skill check de escape. */
    private static void tickGrab(ServerPlayer sp, Active a) {
        Entity ent = sp.serverLevel().getEntity(a.heldId);
        if (!(ent instanceof LivingEntity held) || !held.isAlive() || held.distanceTo(sp) > 8.0) {
            dropHold(sp.getServer(), a, true);
            a.ticksLeft = 0;
            return;
        }
        a.age++;
        // Los mobs no juegan el skill check: se zafan solos pasado grab.mob_max_hold_ticks (sin lanzamiento).
        if (!(held instanceof ServerPlayer) && a.age >= ServerConfig.grabMobHoldTicks()) {
            dropHold(sp.getServer(), a, true);
            REGRAB_BLOCK.put(held.getUUID(), sp.level().getGameTime() + REGRAB_BLOCK_TICKS);
            sp.serverLevel().playSound(null, held.getX(), held.getY(), held.getZ(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 1.3f);
            a.ticksLeft = 0;
            return;
        }
        Vec3 pos = sp.getEyePosition().add(sp.getLookAngle().scale(1.6))
                .subtract(0, held.getBbHeight() * 0.5, 0);
        if (held instanceof ServerPlayer hp) {
            holdInPlace(hp, pos);
        } else {
            held.moveTo(pos.x, pos.y, pos.z);
        }
        held.setDeltaMovement(Vec3.ZERO);
        held.hurtMarked = true;
        held.fallDistance = 0.0f;

        if (held instanceof ServerPlayer hp) tickEscapeCheck(hp, a);

        // Sin tope de tiempo: solo se lanza al llegar (si se configuró) a 0 o por throwNow.
        if (a.ticksLeft <= 1) {
            throwHeld(sp, a, held);
            a.ticksLeft = 0;
        }
    }

    /**
     * Coloca al agarrado en {@code pos} SIN tocar hacia dónde mira: la rotación va como RELATIVA con delta 0, así que su
     * cliente conserva la suya. Con la rotación absoluta (getYRot/getXRot del servidor) cada tick le devolvía la cámara a
     * donde el servidor creía que miraba y no podía girarla agarrado.
     * <p>
     * OJO: {@code ServerGamePacketListenerImpl.teleport} manda {@code yaw - rotaciónActualDelServidor} como delta, no el
     * {@code yaw} tal cual. Pasar {@code 0f, 0f} (lo que hacía la primera versión de este arreglo) enviaba
     * {@code -rotaciónDelServidor}: en cuanto el servidor conocía la rotación del cliente, cada tick le reiniciaba la vista
     * a ~0 y la cámara seguía sin girar. Se pasa la rotación actual del servidor para que la delta sea exactamente 0
     * ({@code absMoveTo} del servidor también la conserva).
     */
    private static void holdInPlace(ServerPlayer victim, Vec3 pos) {
        victim.connection.teleport(pos.x, pos.y, pos.z, victim.getYRot(), victim.getXRot(), java.util.EnumSet.of(
                net.minecraft.world.entity.RelativeMovement.X_ROT, net.minecraft.world.entity.RelativeMovement.Y_ROT));
    }

    /** Arranca el siguiente check cuando toca y da por fallido el que caducó sin pulsar. */
    private static void tickEscapeCheck(ServerPlayer victim, Active a) {
        long now = victim.level().getGameTime();
        int needle = ServerConfig.grabNeedleTicks();
        if (a.checkStart < 0) {
            if (now >= a.nextCheckAt) {
                a.checkIndex++;
                a.checkStart = now;
                sendGrabState(victim, a, 0);
            }
        } else if (now - a.checkStart > 2L * needle + 2) {
            resolveCheck(victim, a, false);   // caducó sin pulsar
        }
    }

    /** Re-activar Agarre mientras se sostiene = lanzar ya. Lo llama ActionResolver.firePhysical. */
    public static boolean throwNow(ServerPlayer sp) {
        Active a = ACTIVE.get(sp.getUUID());
        if (a == null || a.tech != PhysicalTechnique.GRAB || a.heldId < 0 || a.age < 3) return false;
        a.ticksLeft = 1;   // el siguiente tick de tickGrab lo lanza con todas sus comprobaciones
        return true;
    }

    private static void throwHeld(ServerPlayer sp, Active a, LivingEntity held) {
        Vec3 look = sp.getLookAngle();
        double v = ServerConfig.grabThrowSpeed();
        // Sin inmunidad: el aturdimiento del agarre se corta y el del aterrizaje todavía tiene que poder entrar.
        dropHold(sp.getServer(), a, false);
        held.setDeltaMovement(look.x * v, look.y * v + 0.15, look.z * v);
        held.hurtMarked = true;
        THROWN.put(held.getUUID(), new Thrown(sp.getUUID(), a.str));
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /** Vigila a los rivales lanzados por ESTE jugador: al chocar con pared, suelo u otra entidad cobran el
     *  impacto (DamageType SLAM) y quedan aturdidos un momento. */
    private static void tickThrown(ServerPlayer sp) {
        if (THROWN.isEmpty()) return;
        var it = THROWN.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Thrown th = en.getValue();
            if (!th.owner.equals(sp.getUUID())) continue;
            Entity ent = sp.serverLevel().getEntities().get(en.getKey());
            if (!(ent instanceof LivingEntity victim) || !victim.isAlive() || --th.ticksLeft <= 0) {
                it.remove();
                continue;
            }
            th.age++;
            LivingEntity other = null;
            for (LivingEntity o : sp.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    victim.getBoundingBox().inflate(0.3), x -> x != victim && x != sp && x.isAlive())) {
                other = o;
                break;
            }
            boolean crashed = other != null
                    || (th.age > 2 && (victim.horizontalCollision || victim.verticalCollision || victim.onGround()));
            if (!crashed) continue;

            it.remove();
            PhysicalTechnique t = PhysicalTechnique.GRAB;
            double dmg = th.str * t.dmgMult();
            LivingEntity hitOther = other;
            withBridge(t.dmgMult(), () -> {
                hit(sp, victim, dmg, t, ModDamageTypes.SLAM);
                if (hitOther != null) hit(sp, hitOther, dmg * 0.5, t, ModDamageTypes.SLAM);
            });
            victim.setDeltaMovement(Vec3.ZERO);
            victim.hurtMarked = true;
            StunState.apply(victim, ServerConfig.grabLandingStunTicks());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Ejecuta el daño con el puente hacia CombatZenkaiHooks activado. */
    private static void withBridge(double dmgMult, Runnable r) {
        firing = true;
        defenseScale = Math.max(0.1, dmgMult);
        try {
            r.run();
        } finally {
            firing = false;
            defenseScale = 1.0;
        }
    }

    /**
     * Daño devuelto por un efecto que NO es una técnica física (hoy, el parry): pasa por el mismo
     * puente que las técnicas, así que CombatZenkaiHooks lo toma tal cual (sin recalcularlo como
     * melee básico) y lo mitiga con la defensa del objetivo. Respeta la gamerule de daño.
     */
    public static void bridgedHit(ServerPlayer sp, LivingEntity target, double dmg,
                                  ResourceKey<DamageType> type) {
        if (dmg <= 0.0 || !damageEnabled(sp)) return;
        withBridge(1.0, () -> target.hurt(sp.damageSources().source(type, sp), (float) dmg));
    }

    /** ÚNICO sitio donde se dibuja el impacto de una técnica física DE UN JUGADOR (partículas +
     *  sonido). El tinte sale del aura del ATACANTE, resuelto en servidor: viaja en el packet
     *  de partículas, así que el conjunto de clientes ve el mismo color — por eso esto exige un
     *  ServerPlayer y un mob no puede reusarlo tal cual. El SONIDO en sí (mismo por técnica
     *  bloqueada/no bloqueada, ver playImpactSound) SÍ es compartido: PhysicalAttackGoal lo llama
     *  directo para que un mob "suene" igual sin necesitar un atacante con aura.
     *
     *  Partículas: {@link ZenkaiVfxEffect} data-driven desde
     *  {@code data/zenkai/zenkai_vfx_effects/physical_impact/<id>.json} — primer efecto migrado
     *  al formato propuesto en {@code .claude/pendiente/cobblemon-audit-vfx-animaciones.md}
     *  (§17/§20, paso 1). Antes era un {@code switch} fijo en Java (flash/chispas/dispersión por
     *  técnica); ahora es la MISMA forma pero editable sin recompilar. Sin JSON para una técnica
     *  (datapack roto o borrado a mano), se avisa y no se dibuja nada — no hay fallback a un
     *  valor fijo en Java, porque eso escondería el propio aviso. */
    private static void impactFx(ServerPlayer sp, LivingEntity e, PhysicalTechnique t) {
        var lvl = sp.serverLevel();
        int rgb = com.hmc.zenkai.feature.aura.AuraColors.resolve(sp);
        double x = e.getX(), y = e.getY() + e.getBbHeight() * 0.6, z = e.getZ();
        double s = e.getBbWidth() * 0.4;

        ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                "physical_impact/" + t.id());
        ZenkaiVfxEffect fx = ZenkaiVfxRegistry.get(effectId);
        if (fx == null) {
            Zenkai.LOGGER.warn("[Zenkai] Sin efecto VFX '{}' (datapack roto/borrado): "
                    + "impacto de {} sin partículas.", effectId, t.id());
        } else {
            fx.spawn(lvl, new Vec3(x, y, z), rgb, s);
        }

        playImpactSound(lvl, e, t);
    }

    /** El sonido de impacto (uno solo por bloqueado/no bloqueado, PHYSICAL_IMPACT/_BLOCK, ver
     *  ModSounds; volumen/pitch propios de cada técnica) SIN las partículas ni el tinte de aura
     *  — la parte que SÍ tiene sentido para cualquier atacante, jugador o mob. Público para que
     *  {@link com.hmc.zenkai.content.entity.ai.PhysicalAttackGoal} lo reutilice tal cual en vez
     *  de duplicar la elección de sonido/volumen/pitch por técnica (pedido explícito del usuario:
     *  "el sistema de sonidos de las técnicas físicas que lo apliquen los enemigos también"). */
    public static void playImpactSound(net.minecraft.world.level.Level level, LivingEntity target, PhysicalTechnique t) {
        record Vol(float vol, float pitch) {}
        Vol v = switch (t) {
            case DASH_PUNCH -> new Vol(1.0f, 1.1f);
            case HEAVY_BLOW -> new Vol(1.0f, 0.8f);
            case BARRAGE    -> new Vol(0.7f, 1.6f);
            case KIAI       -> new Vol(0.9f, 1.4f);
            case RISING_SLASH -> new Vol(0.9f, 1.3f);
            case CROSS_SLASH -> new Vol(0.7f, 1.5f);
            case PIERCING_THRUST -> new Vol(0.9f, 1.2f);
            case SPIN_SLASH -> new Vol(0.8f, 1.0f);
            case SKY_CLEAVE -> new Vol(1.0f, 0.7f);
            case LIGHT_BLADE -> new Vol(0.9f, 1.3f);
            case DIVE       -> new Vol(1.0f, 0.6f);
            case GRAB       -> new Vol(0.9f, 0.9f);
            case EVADE      -> new Vol(0.6f, 1.5f);
            case ABSORB     -> new Vol(0.8f, 0.6f);
            case FALSE_MOON -> new Vol(0.5f, 1.0f);
        };
        boolean blocked = target instanceof ServerPlayer defSp
                ? KiCombatServer.isBlocking(defSp)
                : target instanceof com.hmc.zenkai.content.entity.ai.BlockingMob bm && bm.isBlockingNow();
        var sound = blocked ? ModSounds.PHYSICAL_IMPACT_BLOCK.get() : ModSounds.PHYSICAL_IMPACT.get();
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                sound, SoundSource.PLAYERS, v.vol(), v.pitch());
    }

    /** Funnel de daño físico: aquí (y SOLO aquí) se pega y se dibuja el impacto.
     *  Las partículas salen aunque el daño esté desactivado por gamerule. */
    private static void hit(ServerPlayer sp, LivingEntity e, double dmg, PhysicalTechnique t) {
        hit(sp, e, dmg, t, damageTypeOf(t));
    }

    /** Igual, con otro DamageType (el impacto de un lanzado es SLAM aunque la técnica sea GRAB). */
    private static void hit(ServerPlayer sp, LivingEntity e, double dmg, PhysicalTechnique t,
                            ResourceKey<DamageType> type) {
        if (damageEnabled(sp)) {
            e.hurt(sp.damageSources().source(type, sp), (float) dmg);
        }
        impactFx(sp, e, t);
    }

    /** Un DamageType propio por técnica (ver ModDamageTypes): así el mensaje de muerte nombra
     *  la técnica concreta ("aplastado por un Heavy Blow") en vez del genérico de un puñetazo. */
    private static ResourceKey<DamageType> damageTypeOf(PhysicalTechnique t) {
        return switch (t) {
            case DASH_PUNCH -> ModDamageTypes.DASH_PUNCH;
            case HEAVY_BLOW -> ModDamageTypes.HEAVY_BLOW;
            case BARRAGE -> ModDamageTypes.BARRAGE;
            case KIAI -> ModDamageTypes.KIAI;
            case RISING_SLASH -> ModDamageTypes.RISING_SLASH;
            case CROSS_SLASH -> ModDamageTypes.CROSS_SLASH;
            case PIERCING_THRUST -> ModDamageTypes.PIERCING_THRUST;
            case SPIN_SLASH -> ModDamageTypes.SPIN_SLASH;
            case SKY_CLEAVE -> ModDamageTypes.SKY_CLEAVE;
            case LIGHT_BLADE -> ModDamageTypes.LIGHT_BLADE;
            case DIVE -> ModDamageTypes.DIVE;
            case GRAB -> ModDamageTypes.GRAB;
            case EVADE -> ModDamageTypes.DASH_PUNCH;   // no daña: el tipo no se usa nunca
            case ABSORB -> ModDamageTypes.GRAB;        // absorber mata por MajinAbsorption, no por hit()
            case FALSE_MOON -> ModDamageTypes.GRAB;    // no daña: el tipo no se usa nunca
        };
    }

    private static boolean damageEnabled(ServerPlayer sp) {
        return sp.getServer() == null || ModGameRules.enableRaceBoosts(sp.getServer());
    }

    /** Primer LivingEntity dentro del cilindro (radio width) a lo largo de la mirada. */
    private static LivingEntity firstInRay(ServerPlayer sp, double range, double width) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        AABB box = sp.getBoundingBox().expandTowards(look.scale(range)).inflate(width);
        for (LivingEntity e : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                x -> x != sp && x.isAlive())) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double along = to.dot(look);
            if (along < 0 || along > range) continue;
            double off = to.subtract(look.scale(along)).length();
            if (off > width + e.getBbWidth() * 0.5) continue;
            if (along < bestDist) { bestDist = along; best = e; }
        }
        return best;
    }

    /** LivingEntity en cono frontal (dot(mirada, dirección) > minDot). */
    private static List<LivingEntity> inCone(ServerPlayer sp, double range, double minDot) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        return sp.serverLevel().getEntitiesOfClass(LivingEntity.class,
                sp.getBoundingBox().inflate(range),
                e -> {
                    if (e == sp || !e.isAlive()) return false;
                    Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
                    if (to.length() > range) return false;
                    return to.normalize().dot(look) > minDot;
                });
    }

    /** Limpieza de mecánica al morir/desconectar. ActionState NO se toca aquí: lo limpian
     *  LivingDeathEvent y PlayerLoggedOutEvent en ActionStateServer, que sí pueden avisar
     *  al sync. */
    public static void clear(UUID id) {
        COOLDOWNS.remove(id);
        REGRAB_BLOCK.remove(id);
        DEBUG_HOLDS.values().removeIf(h -> id.equals(h.heldUuid));
        Active gone = ACTIVE.remove(id);
        if (gone != null) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            dropHold(server, gone, true);
        }
        cancelCharge(id);
        // Un lanzado cuyo dueño se va deja de vigilarse (no hay quién cobre el impacto).
        THROWN.values().removeIf(th -> th.owner.equals(id));
    }
}