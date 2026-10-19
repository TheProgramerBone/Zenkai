package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.event.CombatZenkaiHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aguante de la guardia (servidor autoritativo). Cada golpe bloqueado lo gasta en proporción al
 * daño que logró pasar la guardia; a 0 la guardia se rompe y el defensor queda aturdido. Sin
 * esto un jugador con ki de sobra bloqueaba para siempre.
 *
 * Sirve igual a jugadores y a mobs BlockingMob: el contador vive en un mapa por UUID, así que
 * la Sombra lo tiene sin tocar su entidad. Una entrada ausente significa aguante lleno (no se
 * guarda nada hasta el primer golpe bloqueado). Se regenera por tick, y solo si NO bloquea y ya
 * pasó el retardo desde el último golpe.
 *
 * El gasto se mide en % de la vida de cuerpo máxima del defensor, no en daño bruto: el daño del
 * mod escala con el Power Level y un número fijo no significaría lo mismo a PL 300 que a PL 3M.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class GuardStamina {
    private GuardStamina() {}

    /** UUID -> aguante actual. Ausente = lleno. */
    private static final Map<UUID, Float> CURRENT = new ConcurrentHashMap<>();
    /** UUID -> último tick en que recibió (o mantuvo) un golpe bloqueado; ancla del retardo. */
    private static final Map<UUID, Long> LAST_HIT = new ConcurrentHashMap<>();

    /** Aguante máximo base (sin Constitución); 0 = la función está apagada por config. */
    public static float max() { return (float) ServerConfig.guardMaxBase(); }

    /** Curva de salida suave 0..1 que ALCANZA 1 exactamente en {@code full}: 1 - (1 - x/full)^2. Antes era x/(x+mitad),
     *  que solo se acerca a 1 sin tocarlo (con 1000 de stats el aguante quedaba en 199 de 200). */
    private static double saturate(double x, double full) {
        double t = Math.min(1.0, Math.max(0.0, x) / Math.max(1.0, full));
        return 1.0 - (1.0 - t) * (1.0 - t);
    }

    /** Aguante máximo de ESTA entidad: el base más un extra por Constitución (curva de salida suave con tope exacto). */
    public static float max(LivingEntity e) {
        float base = max();
        if (base <= 0f) return 0f;
        var st = com.hmc.zenkai.feature.combat.ZenkaiStats.of(e);
        if (st == null) return base;
        return (float) (base * (1.0 + ServerConfig.guardConBonus()
                * saturate(st.computeConFinal(), ServerConfig.guardConFull())));
    }

    /** Regeneración por tick de ESTA entidad: la base más un extra por Destreza. */
    public static float regenPerTick(LivingEntity e) {
        double base = ServerConfig.guardRegenPerTick();
        var st = com.hmc.zenkai.feature.combat.ZenkaiStats.of(e);
        if (st == null) return (float) base;
        return (float) (base * (1.0 + ServerConfig.guardDexBonus()
                * saturate(st.computeDefenseFinal(), ServerConfig.guardDexFull())));
    }

    public static float current(LivingEntity e) {
        Float c = CURRENT.get(e.getUUID());
        return c == null ? max(e) : c;
    }

    /**
     * Un golpe llegó a una guardia levantada. {@code damage} es lo que pasó tras la mitigación de
     * la guardia; {@code bodyMax} la vida de cuerpo máxima del defensor.
     */
    public static void onBlockedHit(LivingEntity defender, double damage, double bodyMax) {
        float max = max(defender);
        if (max <= 0f || bodyMax <= 0.0 || damage <= 0.0) return;
        long now = defender.level().getGameTime();
        double drain = damage / bodyMax * 100.0 * ServerConfig.guardDrainMult();
        float cur = (float) Math.max(0.0, current(defender) - drain);
        CURRENT.put(defender.getUUID(), cur);
        LAST_HIT.put(defender.getUUID(), now);

        if (cur <= 0f) {
            int ticks = ServerConfig.guardBreakStunTicks();
            int breakTicks = ticks > 0 ? ticks : 40;
            // StunState.apply ya rompe la guardia; si no pudo aturdir (inmune, ya aturdido,
            // jugador con el stun apagado) la rotura sigue valiendo por sí sola.
            if (ticks <= 0 || StunState.apply(defender, ticks) <= 0) {
                GuardBreak.breakGuard(defender, breakTicks);
            }
            // El impacto de la rotura lo ven todos: el aviso al dueño va por el sync de abajo.
            if (defender.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.playSound(null, defender.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BREAK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.9f);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        defender.getX(), defender.getY() + defender.getBbHeight() * 0.6, defender.getZ(),
                        18, 0.4, 0.4, 0.4, 0.25);
            }
        }
        sync(defender, cur, max);
    }

    private static void sync(LivingEntity e, float cur, float max) {
        if (e instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new GuardSyncPacket(cur, max, -1));
    }

    /** Regeneración: solo mientras hay una entrada (o sea, el aguante no está lleno). */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post e) {
        if (CURRENT.isEmpty()) return;
        if (!(e.getEntity() instanceof LivingEntity le) || le.level().isClientSide()) return;
        UUID id = le.getUUID();
        Float cur = CURRENT.get(id);
        if (cur == null) return;

        long now = le.level().getGameTime();
        // Mantener la guardia levantada tras un golpe no deja regenerar: el retardo se ancla al
        // último tick bloqueando, no solo al último golpe.
        if (CombatZenkaiHooks.isBlockingNow(le)) { LAST_HIT.put(id, now); return; }
        Long last = LAST_HIT.get(id);
        if (last != null && now - last < ServerConfig.guardRegenDelayTicks()) return;

        float max = max(le);
        float next = Math.min(max, cur + regenPerTick(le));
        if (next >= max) {
            CURRENT.remove(id);
            LAST_HIT.remove(id);
            sync(le, max, max);
        } else {
            CURRENT.put(id, next);
            if (now % 4 == 0) sync(le, next, max);
        }
    }

    private static void forget(UUID id) {
        CURRENT.remove(id);
        LAST_HIT.remove(id);
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (!e.getEntity().level().isClientSide()) forget(e.getEntity().getUUID());
    }

    /** Al reaparecer, aguante lleno (la entidad vieja ya salió del nivel, pero por si acaso). */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            forget(sp.getUUID());
            sync(sp, max(sp), max(sp));
        }
    }
}
