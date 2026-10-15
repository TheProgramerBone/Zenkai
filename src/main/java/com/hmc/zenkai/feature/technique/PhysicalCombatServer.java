package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.api.event.TechniqueFiredEvent;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
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
import net.minecraft.core.particles.ParticleTypes;
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
        return a != null && a.tech == PhysicalTechnique.DASH_PUNCH && a.ticksLeft > 0;
    }

    /** ¿Hay un movimiento CON DURACIÓN en curso? Lo consulta ServerActionContext. */
    public static boolean isBusy(java.util.UUID id) {
        return ACTIVE.containsKey(id);
    }

    public static double currentDefenseScale() { return defenseScale; }

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
        ACTIVE.remove(sp.getUUID());
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
        if (raw > 0.0) {
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
        Vec3 look = sp.getLookAngle();

        switch (t) {
            case DASH_PUNCH -> {
                dashImpulse(sp, look);
                ACTIVE.put(sp.getUUID(), new Active(t, str, look, DASH_TICKS));
            }
            case BARRAGE -> ACTIVE.put(sp.getUUID(), new Active(t, str, look, BARRAGE_TICKS));
            case HEAVY_BLOW -> heavyBlow(sp, str, t);
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
        }
        ZenkaiTriggers.TECHNIQUE_USED.get().trigger(sp,
                t.name().toLowerCase(java.util.Locale.ROOT), 0.0);
        PlayerLifeCycle.sync(sp);
    }

    /** Tick de los movimientos con duración. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
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

    /** Golpe único pesado: SIEMPRE se ejecuta. Con objetivo directo: daño completo +
     *  knockback en él. Sin objetivo: el golpe revienta donde termina la trayectoria
     *  (bloque mirado o aire a rango máximo). En ambos casos: explosión (partículas +
     *  sonido + AoE 50% con caída + empuje radial). Sin romper bloques: físico, no ki. */
    private static void heavyBlow(ServerPlayer sp, double str, PhysicalTechnique t) {
        smash(sp, str, t, false, 0.5, 2.5, 1.8);
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

    /** Tajo ascendente: cono frontal; todo el que lo recibe sale disparado hacia arriba (se aplica
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
        if (damageEnabled(sp)) {
            e.hurt(sp.damageSources().source(damageTypeOf(t), sp), (float) dmg);
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
        ACTIVE.remove(id);
    }
}