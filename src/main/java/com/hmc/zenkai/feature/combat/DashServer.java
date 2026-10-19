package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.action.ActionStateServer;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import net.minecraft.core.particles.ParticleTypes;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.technique.vfx.ZenkaiVfxEffect;
import com.hmc.zenkai.feature.technique.vfx.ZenkaiVfxRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * Dash de movilidad del modo combate (doble toque de W/A/S/D), en tierra y en el aire pero NO
 * volando. No cuesta estamina: el límite es el cooldown corto (dash.cooldown_ticks). Es distinto
 * de DASH_PUNCH, que es un ataque con coste, y del antiguo Shunpo aéreo que se quitó.
 *
 * PASO RÁPIDO: habilidad comprable (skill quick_step, Korin). Con ella aprendida, el mismo doble
 * toque SOBREESCRIBE el dash: cuesta ki, llega más lejos, el modelo desaparece mientras dura el
 * impulso, da i-frames y la animación es más brusca. No es un teletransporte: el movimiento es
 * físico y continuo. Si no hay ki suficiente cae al dash normal; en cooldown no hace nada (nunca
 * se encadenan un dash y un Paso rápido). Un interruptor de la rueda (encendido por defecto)
 * alterna entre Paso rápido y dash normal.
 *
 * Todo es autoritativo del servidor: el cliente solo detecta el doble toque y manda la
 * dirección. Se rechaza en silencio (sin packet de rechazo: es un gesto de movimiento, no una
 * acción con coste que haya que revertir en el cliente) si no estás en modo combate, estás
 * abatido, volando, defendiendo, o con otra acción exclusiva en curso.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class DashServer {
    private DashServer() {}

    public static final int DIR_FORWARD = 0, DIR_BACK = 1, DIR_LEFT = 2, DIR_RIGHT = 3;

    /** Subida del saltito inicial al esquivar desde el suelo (antes 0.1, se quedaba rasante). */
    private static final double GROUND_HOP = 0.3;

    /** UUID -> tick de juego en el que vuelve a estar disponible el dash. */
    private static final Map<UUID, Long> READY_AT = new ConcurrentHashMap<>();
    /** Ídem para el Paso rápido (cooldown propio, más largo que el del dash). */
    private static final Map<UUID, Long> STEP_READY_AT = new ConcurrentHashMap<>();
    /** UUID -> tick de juego hasta el que dura la invulnerabilidad del Paso rápido. */
    private static final Map<UUID, Long> IFRAMES_UNTIL = new ConcurrentHashMap<>();

    private static final ResourceLocation QUICK_STEP_START = fx("movement/quick_step_start");
    private static final ResourceLocation QUICK_STEP_TRAIL = fx("movement/quick_step_trail");
    private static final ResourceLocation QUICK_STEP_ARRIVE = fx("movement/quick_step_arrive");
    /** UUID -> tick en el que termina la invisibilidad: ahí se reaparece con un destello. */
    private static final Map<UUID, Long> ARRIVE_AT = new ConcurrentHashMap<>();

    private static ResourceLocation fx(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
    }

    /** Efecto data-driven (zenkai_vfx_effects) en el centro del cuerpo, con el color del aura. */
    private static void spawnFx(ServerLevel lvl, ResourceLocation id, ServerPlayer sp, double spread) {
        ZenkaiVfxEffect effect = ZenkaiVfxRegistry.get(id);
        if (effect == null) return;
        effect.spawn(lvl, sp.position().add(0, sp.getBbHeight() * 0.5, 0),
                AuraColors.resolve(sp), spread);
    }

    /** Estela de chispas mientras dura el impulso y destello al reaparecer. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel lvl)) return;
        if (ARRIVE_AT.isEmpty()) return;
        Long end = ARRIVE_AT.get(sp.getUUID());
        if (end == null) return;
        long now = lvl.getGameTime();
        if (now < end) {
            spawnFx(lvl, QUICK_STEP_TRAIL, sp, 0.25);
        } else {
            ARRIVE_AT.remove(sp.getUUID());
            spawnFx(lvl, QUICK_STEP_ARRIVE, sp, 0.3);
        }
    }

    /** ¿Está dentro de los i-frames de un Paso rápido? Lo consultan los hooks de daño. */
    public static boolean hasIFrames(ServerPlayer sp) {
        Long until = IFRAMES_UNTIL.get(sp.getUUID());
        return until != null && until > sp.level().getGameTime();
    }

    public static boolean tryDash(ServerPlayer sp, int dir) {
        if (dir < DIR_FORWARD || dir > DIR_RIGHT) return false;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen() || att.flags().isDowned() || StunState.isStunned(sp)) return false;
        if (!CombatModeServerState.isActive(sp.getUUID())) return false;
        // Volando no se esquiva: el vuelo ya tiene su propio movimiento (y su impulso).
        if (att.flags().isFlyEnabled() || sp.getAbilities().flying) return false;
        if (KiCombatServer.isBlocking(sp) || !ActionStateServer.get(sp).isNone()) return false;

        long now = sp.level().getGameTime();
        UUID id = sp.getUUID();

        boolean step = false;
        // El interruptor de la rueda (encendido por defecto) decide si el doble toque es Paso rápido
        // o dash normal; isOn ya exige tener la habilidad.
        if (SkillToggles.isOn(sp, SkillEffects.QUICK_STEP)) {
            // Con la habilidad, el doble toque ES el Paso rápido: en cooldown no hace nada. Caer al
            // dash normal aquí dejaba encadenar un dash justo después de un Paso rápido.
            if (STEP_READY_AT.getOrDefault(id, 0L) > now) return false;
            int cost = (int) Math.ceil(att.getEnergyMax() * ServerConfig.stepEnergyPct()
                    * SkillEffects.quickStepKiCostMult(sp));
            if (att.getEnergy() >= cost) {
                att.consumeEnergy(cost);
                step = true;
            }
        }
        if (step) {
            STEP_READY_AT.put(id, now + Math.round(ServerConfig.stepCooldownTicks()
                    * SkillEffects.quickStepCooldownMult(sp)));
            IFRAMES_UNTIL.put(id, now + ServerConfig.stepIFrameTicks());
            // Un dash normal tampoco puede arrancar pegado al Paso rápido.
            READY_AT.put(id, now + ServerConfig.dashCooldownTicks());
        } else {
            // Sin la habilidad, o con la habilidad pero sin ki suficiente: dash normal.
            if (READY_AT.getOrDefault(id, 0L) > now) return false;
            READY_AT.put(id, now + ServerConfig.dashCooldownTicks());
        }

        // Eje horizontal según hacia dónde mira el cuerpo (yaw), sin la inclinación vertical.
        double yaw = Math.toRadians(sp.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        Vec3 d = switch (dir) {
            case DIR_BACK -> fwd.scale(-1.0);
            case DIR_LEFT -> right.scale(-1.0);
            case DIR_RIGHT -> right;
            default -> fwd;
        };

        Vec3 cur = sp.getDeltaMovement();
        double speed = step ? ServerConfig.stepSpeed() : ServerConfig.dashSpeed();
        // En suelo, un saltito evita que la fricción se coma el impulso; en el aire se conserva
        // la altura (sin caer más rápido de lo que ya caías) para que el dash aéreo sea horizontal.
        double vy = sp.onGround() ? GROUND_HOP : Math.max(cur.y, -0.1);
        sp.setDeltaMovement(d.x * speed, vy, d.z * speed);
        sp.hurtMarked = true;

        if (step) {
            // Invisibilidad vainilla sin partículas ni icono: las capas propias (armadura racial,
            // halo, pesas, scouter) ya respetan isInvisible(), así que desaparece el conjunto.
            sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                    ServerConfig.stepInvisibleTicks(), 0, false, false, false));
        }

        if (sp.level() instanceof ServerLevel lvl) {
            if (step) {
                // Destello de ki en el punto de salida, tintado con el color del aura. Sin humo.
                spawnFx(lvl, QUICK_STEP_START, sp, 0.3);
                ARRIVE_AT.put(id, now + ServerConfig.stepInvisibleTicks());
            } else {
                lvl.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 0.1, sp.getZ(),
                        6, 0.25, 0.05, 0.25, 0.02);
            }
            lvl.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, step ? 1.9f : 1.4f);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new DashAnimPacket(sp.getId(), dir, step));
        com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sp,
                step ? com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.QUICK_STEP_USED : com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.DASH_USED);
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        READY_AT.remove(id);
        STEP_READY_AT.remove(id);
        IFRAMES_UNTIL.remove(id);
        ARRIVE_AT.remove(id);
    }
}
