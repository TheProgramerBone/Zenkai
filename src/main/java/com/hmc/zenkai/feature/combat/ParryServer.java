package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parry. INNATO: cualquier jugador que empiece a bloquear tiene una ventana corta (parry.window_ticks)
 * durante la cual la guardia es un parry. La habilidad Parry Mastery solo lo MEJORA (ventana más
 * ancha, devuelve ki, cura body, contraataque más fuerte) — igual que el dash y el Paso rápido.
 *
 * Dentro de la ventana, un golpe que llega:
 *  - se ANULA por completo (se cancela en LivingIncomingDamageEvent, así no hay animación de
 *    daño), el atacante sale empujado y, si es un NPC, queda sin poder atacar unos ticks;
 *  - si es un PROYECTIL de ki, no se cancela el daño: el parry normal lo DESVÍA de lado (scatterAround) y
 *    el PERFECTO lo devuelve contra su dueño (deflect()).
 * Tras un parry logrado el defensor tiene una ventana de contraataque: su siguiente golpe lleva
 * un multiplicador (parry.counter_mult + bonus de la habilidad).
 *
 * ANTI-SPAM: la ventana se abre al EMPEZAR a bloquear, y un bloqueo nuevo solo la vuelve a abrir
 * pasado parry.cooldown_ticks desde la última vez que se abrió, acierte o no. Si no, tocar el
 * botón de guardia a ráfagas dejaría el parry encendido para siempre.
 * Todo es autoritativo del servidor; el cliente solo recibe la animación (ParryAnimPacket).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ParryServer {
    private ParryServer() {}

    /** UUID -> tick de juego hasta el que la ventana está abierta. */
    private static final Map<UUID, Long> WINDOW_UNTIL = new ConcurrentHashMap<>();
    /** UUID -> tick en que se ABRIÓ la ventana vigente (para el parry perfecto). */
    private static final Map<UUID, Long> OPENED_AT = new ConcurrentHashMap<>();
    /** UUID -> tick a partir del cual un bloqueo nuevo puede abrir otra ventana. */
    private static final Map<UUID, Long> READY_AT = new ConcurrentHashMap<>();
    /** UUID -> tick hasta el que dura el contraataque, y su multiplicador. */
    private static final Map<UUID, Long> COUNTER_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> COUNTER_MULT = new ConcurrentHashMap<>();

    /** Jugadores que han elegido (ClientConfig parry.trigger) que pulsar la guardia abra el parry.
     *  Por defecto NADIE: sin la preferencia, bloquear es una guardia inmediata como siempre. */
    private static final java.util.Set<UUID> PARRY_ON_BLOCK = ConcurrentHashMap.newKeySet();

    /** Lo llama ParryPrefPacket. */
    public static void setParryOnBlock(ServerPlayer sp, boolean on) {
        if (on) PARRY_ON_BLOCK.add(sp.getUUID());
        else {
            PARRY_ON_BLOCK.remove(sp.getUUID());
            PENDING_BLOCK.remove(sp.getUUID());
        }
    }

    /** UUID -> tick en que arranca la guardia real de un bloqueo que está esperando a que acabe la
     *  ventana de parry. */
    private static final Map<UUID, Long> PENDING_BLOCK = new ConcurrentHashMap<>();

    /**
     * Lo llama ActionResolver cuando el jugador PIDE bloquear. Si el parry por bloqueo está
     * permitido y no está en cooldown, abre la ventana y APLAZA la guardia real hasta que acabe:
     * pulsar y soltar = parry, mantener = bloqueo. Así los dos gestos no se pisan (la guardia no
     * puede estar levantada durante la ventana del parry).
     * @return true si el bloqueo queda aplazado (el llamante NO debe activar la guardia ahora).
     *         False si hay que bloquear ya (parry apagado, solo por tecla, o en cooldown).
     */
    public static boolean deferBlock(ServerPlayer sp) {
        if (!ServerConfig.parryEnabled() || !PARRY_ON_BLOCK.contains(sp.getUUID())) return false;
        if (!GuardHands.canGuard(sp)) return false;
        long end = open(sp, false);
        if (end < 0) return false;
        PENDING_BLOCK.put(sp.getUUID(), end);
        return true;
    }

    /** El jugador soltó la guardia antes de que arrancara: no hay bloqueo que activar. */
    public static void cancelPendingBlock(ServerPlayer sp) {
        PENDING_BLOCK.remove(sp.getUUID());
    }

    /**
     * Tecla de parry (ParryPacket). Abre la ventana sin exigir guardia: el gesto del parry es
     * propio, no depende de bloquear. Exige estar en modo combate, no abatido, fuera de un choque
     * de haces y sin otra acción exclusiva (salvo la guardia). En cooldown RECHAZA y le recuerda
     * al cliente cuánto falta.
     */
    public static void tryActivate(ServerPlayer sp) {
        if (!ServerConfig.parryEnabled()) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen() || att.flags().isDowned() || StunState.isStunned(sp)) return;
        if (!CombatModeServerState.isActive(sp.getUUID())) return;
        // Con las manos ocupadas por otra cosa que no sea un arma o un escudo no hay parry.
        if (!GuardHands.canGuard(sp)) return;
        var st = com.hmc.zenkai.feature.action.ActionStateServer.get(sp);
        if (!st.isNone() && st.type() != com.hmc.zenkai.feature.action.ActionType.BLOCK) return;
        open(sp, true);
    }

    /**
     * Abre la ventana. SIEMPRE ejecuta la animación del manotazo, acierte o no (así se ve el gesto
     * completo aunque falle) y suena un «whoosh».
     * @param notifyCooldown avisar al cliente si el cooldown impide abrirla (solo con la tecla).
     * @return el tick en que acaba la ventana, o -1 si no se pudo abrir (parry apagado o cooldown).
     */
    private static long open(ServerPlayer sp, boolean notifyCooldown) {
        if (!ServerConfig.parryEnabled()) return -1L;
        long now = sp.level().getGameTime();
        UUID id = sp.getUUID();
        long readyAt = READY_AT.getOrDefault(id, 0L);
        if (readyAt > now) {
            if (notifyCooldown) {
                PacketDistributor.sendToPlayer(sp, new ParryCooldownPacket((int) (readyAt - now)));
            }
            return -1L;
        }
        int window = ServerConfig.parryWindowTicks() + SkillEffects.parryWindowBonus(sp);
        long end = now + window;
        WINDOW_UNTIL.put(id, end);
        OPENED_AT.put(id, now);
        READY_AT.put(id, now + ServerConfig.parryCooldownTicks());
        if (sp.level() instanceof ServerLevel lvl) {
            lvl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.5f, 1.7f);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new ParryAnimPacket(sp.getId()));
        return end;
    }

    /**
     * Ventana que se cierra sin haber parado nada = parry FALLIDO: se avisa al jugador con el
     * cooldown que le queda. Un parry logrado quita la ventana antes (succeed), así que aquí solo
     * llegan los fallos.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        long now = sp.level().getGameTime();

        // Bloqueo aplazado: cuando acaba la ventana arranca la guardia real, si sigue pulsando.
        Long pending = PENDING_BLOCK.get(sp.getUUID());
        if (pending != null && pending <= now) {
            PENDING_BLOCK.remove(sp.getUUID());
            com.hmc.zenkai.feature.action.ActionResolver.setBlocking(sp, true, true);
        }

        if (WINDOW_UNTIL.isEmpty()) return;
        Long until = WINDOW_UNTIL.get(sp.getUUID());
        if (until == null) return;
        if (until > now) return;
        WINDOW_UNTIL.remove(sp.getUUID());
        OPENED_AT.remove(sp.getUUID());
        long left = READY_AT.getOrDefault(sp.getUUID(), 0L) - now;
        if (left > 0) PacketDistributor.sendToPlayer(sp, new ParryCooldownPacket((int) left));
    }

    private static boolean windowOpen(ServerPlayer sp) {
        if (!ServerConfig.parryEnabled()) return false;
        Long until = WINDOW_UNTIL.get(sp.getUUID());
        return until != null && until > sp.level().getGameTime();
    }

    /**
     * Golpe cuerpo a cuerpo / físico. @return true si se parreó: el llamante CANCELA el daño.
     * Los proyectiles de ki no pasan por aquí (tryParryProjectile los gestiona antes de hacer
     * daño), y tampoco las explosiones ni el daño sin atacante.
     */
    public static boolean tryParry(ServerPlayer defender, DamageSource src) {
        if (!windowOpen(defender)) return false;
        if (!(src.getEntity() instanceof LivingEntity attacker) || attacker == defender) return false;
        if (src.getDirectEntity() instanceof KiProjectileEntity) return false;
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return false;
        // Gancho de addons: cancelable, antes de resolver el parry (ver PlayerParryEvent).
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.hmc.zenkai.api.event.PlayerParryEvent(defender, attacker, false,
                isPerfectNow(defender.getUUID(), defender.level().getGameTime()))).isCanceled()) return false;
        succeed(defender, attacker, false);
        return true;
    }

    /**
     * Parry de un NPC con kiai en su ficha. No tiene ventana ni tecla: cada golpe CUERPO A CUERPO que le
     * llega tiene {@code parry.npc_chance} de pararse, con un cooldown propio por mob. El golpe se
     * CANCELA (sin animación de daño) y el atacante sale empujado los mismos bloques que con un parry de
     * jugador. No devuelve daño: el golpe de un mob se recalcula entero desde su STR en CombatZenkaiHooks,
     * así que devolverlo habría sido un golpe completo, no «un poco».
     * @return true si se paró: el llamante CANCELA el daño.
     */
    public static boolean tryMobParry(ZenkaiDefaultMob mob, DamageSource src) {
        if (!ServerConfig.parryEnabled() || mob.level().isClientSide()) return false;
        if (!(src.getEntity() instanceof LivingEntity attacker) || attacker == mob) return false;
        if (src.getDirectEntity() != attacker) return false;   // solo melee: sin proyectiles ni explosiones
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) || mob.isNoAi()) return false;
        double chance = ServerConfig.parryNpcChance();
        // Los guiones del muñeco de pruebas que apagan las físicas apagan también el parry.
        if (chance <= 0.0 || !mob.combatGoalAllowed(ZenkaiDefaultMob.GOAL_PHYSICAL) || !mob.knowsKiai()) return false;
        long now = mob.level().getGameTime();
        if (mob.parryReadyAt() > now) return false;
        // El cooldown corre aunque la suerte falle: si no, cada golpe sería una tirada nueva y la
        // probabilidad efectiva por pelea se dispararía.
        mob.setParryReadyAt(now + ServerConfig.parryCooldownTicks());
        if (mob.getRandom().nextDouble() >= chance) return false;

        Vec3 away = attacker.position().subtract(mob.position());
        away = new Vec3(away.x, 0.0, away.z);
        away = away.lengthSqr() < 1.0e-6 ? mob.getLookAngle() : away.normalize();
        double k = speedForDistance(ServerConfig.parryKnockbackBlocks(), KNOCKBACK_LIFT);
        attacker.setDeltaMovement(away.x * k, KNOCKBACK_LIFT, away.z * k);
        attacker.hurtMarked = true;

        if (mob.level() instanceof ServerLevel lvl) {
            Vec3 c = mob.position().add(0, mob.getBbHeight() * 0.65, 0).add(mob.getLookAngle().scale(0.6));
            lvl.sendParticles(ModParticles.impact(0xFFFFFF, 1.8f), c.x, c.y, c.z, 1, 0, 0, 0, 0);
            lvl.sendParticles(ModParticles.spark(0xFFFFFF, 1.0f), c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.25);
            lvl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.55f, 1.9f);
            lvl.playSound(null, c.x, c.y, c.z, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8f, 1.6f);
        }
        return true;
    }

    /** ¿Cae el golpe de ahora en los primeros {@code parry.perfect_ticks} de la ventana? No la consume. */
    private static boolean isPerfectNow(UUID id, long now) {
        Long openedAt = OPENED_AT.get(id);
        return openedAt != null && now - openedAt < ServerConfig.parryPerfectTicks();
    }

    /**
     * Proyectil de ki que acaba de tocar al defensor. @return true si se paró (no hace daño).
     * Parry NORMAL: el proyectil se DESVÍA de lado y sigue de largo, sin cambiar de dueño. Parry
     * PERFECTO: se DEVUELVE contra quien lo lanzó (y pasa a ser del defensor).
     */
    public static boolean tryParryProjectile(ServerPlayer defender, KiProjectileEntity proj) {
        if (!windowOpen(defender) || !proj.canBeDeflected()) return false;
        if (proj.getOwner() == defender) return false;
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.hmc.zenkai.api.event.PlayerParryEvent(defender,
                proj.getOwner() instanceof LivingEntity owner ? owner : null, true,
                isPerfectNow(defender.getUUID(), defender.level().getGameTime()))).isCanceled()) return false;
        if (isPerfectNow(defender.getUUID(), defender.level().getGameTime())) {
            Entity origin = proj.getOwner();
            double speed = proj.getDeltaMovement().length();
            if (!proj.deflect(defender)) return false;
            Vec3 dir = origin != null
                    ? origin.getBoundingBox().getCenter().subtract(proj.position()).normalize()
                    : proj.getDeltaMovement().scale(-1.0).normalize();
            proj.setDeltaMovement(dir.scale(Math.max(speed, 0.4)));
            proj.hasImpulse = true;
        } else {
            proj.scatterAround(new java.util.Random(defender.getRandom().nextLong()), defender);
        }
        succeed(defender, null, true);
        return true;
    }

    private static void succeed(ServerPlayer defender, LivingEntity attacker, boolean projectile) {
        com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(defender, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.PARRY_SUCCESS);
        UUID id = defender.getUUID();
        long now = defender.level().getGameTime();
        WINDOW_UNTIL.remove(id); // un parry por ventana

        // PARRY PERFECTO: acertar en los primeros parry.perfect_ticks de la ventana. Premia leer el golpe
        // en vez de abrir la ventana y esperar: mejora el contraataque y, contra un proyectil, es lo que
        // lo DEVUELVE (el parry normal solo lo desvía, ver tryParryProjectile).
        boolean perfect = isPerfectNow(id, now);
        OPENED_AT.remove(id);
        // El logro «devolver un proyectil» solo cuenta si de verdad volvió.
        if (projectile && perfect) com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(defender, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.PARRY_PROJECTILE);

        // Contraataque.
        COUNTER_UNTIL.put(id, now + ServerConfig.parryCounterTicks());
        COUNTER_MULT.put(id, ServerConfig.parryCounterMult() + SkillEffects.parryCounterBonus(defender)
                + (perfect ? ServerConfig.parryPerfectBonus() : 0.0));

        // El atacante sale empujado y, si es un NPC, pierde el siguiente ataque.
        if (attacker != null) {
            // Primero el daño devuelto y DESPUÉS el empujón: hurt() aplica su propio knockback y pisaría
            // la velocidad si fuera al revés (mismo orden que el kiai).
            if (!projectile) {
                double reflect = ServerConfig.parryReflectFraction()
                        * PlayerStatsAttachment.get(defender).computeMeleeFinal();
                com.hmc.zenkai.feature.technique.PhysicalCombatServer.bridgedHit(
                        defender, attacker, reflect, com.hmc.zenkai.registry.ModDamageTypes.PARRY);
            }
            Vec3 away = attacker.position().subtract(defender.position());
            away = new Vec3(away.x, 0.0, away.z);
            away = away.lengthSqr() < 1.0e-6 ? defender.getLookAngle() : away.normalize();
            double k = speedForDistance(ServerConfig.parryKnockbackBlocks(), KNOCKBACK_LIFT);
            attacker.setDeltaMovement(away.x * k, KNOCKBACK_LIFT, away.z * k);
            attacker.hurtMarked = true;
            if (attacker instanceof ZenkaiDefaultMob mob) {
                mob.getNavigation().stop();
                mob.markActionUsed(ServerConfig.parryStunTicks());
            }
        }

        // Ventajas de la habilidad.
        PlayerStatsAttachment att = PlayerStatsAttachment.get(defender);
        double ki = SkillEffects.parryKiReturn(defender);
        if (ki > 0.0) att.addEnergy((int) Math.round(att.getEnergyMax() * ki));
        double heal = SkillEffects.parryBodyHeal(defender);
        if (heal > 0.0) att.addBody((int) Math.round(att.getBodyMax() * heal));
        if (ki > 0.0 || heal > 0.0) PlayerLifeCycle.syncIfServer(defender);

        // Presentación: destello, sonido y el clip de la mano apartando el golpe.
        if (defender.level() instanceof ServerLevel lvl) {
            Vec3 c = defender.position().add(0, defender.getBbHeight() * 0.65, 0)
                    .add(defender.getLookAngle().scale(0.6));
            int rgb = AuraColors.resolve(defender);
            lvl.sendParticles(ModParticles.impact(rgb, 1.8f), c.x, c.y, c.z, 1, 0, 0, 0, 0);
            lvl.sendParticles(ModParticles.spark(rgb, 1.0f), c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.25);
            if (perfect) {
                // Destello dorado extra, más chispas y una campanada: se distingue del parry normal.
                lvl.sendParticles(ModParticles.impact(0xFFD24A, 2.4f), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                lvl.sendParticles(ModParticles.spark(0xFFD24A, 1.2f), c.x, c.y, c.z, 18, 0.4, 0.4, 0.4, 0.35);
                lvl.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.8f);
            }
            // Sonido de ACIERTO, en capas para que sea satisfactorio y distinto del bloqueo normal:
            // un «clang» metálico seco (yunque, agudo), un timbre cristalino que se queda un
            // instante (amatista) y el golpe de escudo de fondo.
            lvl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS,
                    0.55f, 1.9f);
            lvl.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 1.2f, 1.5f);
            lvl.playSound(null, c.x, c.y, c.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS,
                    0.8f, 1.6f);
        }
        if (perfect) {
            defender.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.zenkai.parry_perfect"), true);
        }
        // (La animación del manotazo ya se reprodujo al ABRIR la ventana, ver open().)
    }

    /** Salto vertical del empujón: lo justo para despegar del suelo y que no se frene en seco. */
    private static final double KNOCKBACK_LIFT = 0.2;

    /**
     * Velocidad horizontal inicial para que una entidad lanzada con {@code lift} de salto acabe a
     * unos {@code blocks} de distancia. Simula los ticks de vanilla (gravedad 0.08, rozamiento del aire
     * 0.98 vertical y 0.91 horizontal, 0.546 con el suelo) con búsqueda binaria, para que la config
     * hable en bloques y no en una velocidad que nadie sabe traducir.
     */
    public static double speedForDistance(double blocks, double lift) {
        if (blocks <= 0.0) return 0.0;
        double lo = 0.0, hi = 4.0;
        for (int i = 0; i < 24; i++) {
            double mid = (lo + hi) / 2.0;
            if (travelled(mid, lift) < blocks) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2.0;
    }

    public static double travelled(double v0, double lift) {
        double x = 0.0, y = 0.0, vx = v0, vy = lift;
        for (int tick = 0; tick < 200 && vx > 0.003; tick++) {
            x += vx;
            y += vy;
            boolean ground = y <= 0.0;
            if (ground) { y = 0.0; vy = 0.0; }
            vy = (vy - 0.08) * 0.98;
            vx *= ground ? 0.546 : 0.91;
        }
        return x;
    }

    /**
     * Multiplicador de contraataque para el siguiente golpe de este atacante, o 1.0. Se CONSUME:
     * solo mejora un golpe. Lo llama CombatZenkaiHooks sobre el daño saliente.
     */
    public static float consumeCounterMult(Entity attacker) {
        if (!(attacker instanceof ServerPlayer sp)) return 1.0f;
        UUID id = sp.getUUID();
        Long until = COUNTER_UNTIL.get(id);
        if (until == null) return 1.0f;
        if (until <= sp.level().getGameTime()) {
            COUNTER_UNTIL.remove(id);
            COUNTER_MULT.remove(id);
            return 1.0f;
        }
        COUNTER_UNTIL.remove(id);
        Double m = COUNTER_MULT.remove(id);
        return m == null ? 1.0f : m.floatValue();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        WINDOW_UNTIL.remove(id);
        OPENED_AT.remove(id);
        PENDING_BLOCK.remove(id);
        PARRY_ON_BLOCK.remove(id);
        READY_AT.remove(id);
        COUNTER_UNTIL.remove(id);
        COUNTER_MULT.remove(id);
    }
}
