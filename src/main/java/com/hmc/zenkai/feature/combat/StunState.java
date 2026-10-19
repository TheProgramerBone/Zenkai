package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aturdimiento AUTORITATIVO (servidor): mientras dura, la víctima no puede moverse ni actuar.
 * Un único punto de entrada, {@link #apply}, que llaman la guardia rota por daño y cualquier
 * efecto futuro (agarre, picado). Lo consultan ActionRules (vía ActionContext.stunned), el
 * bloqueo, los goals de los mobs y el input del cliente (StunClientState, por sync).
 *
 * Para que nadie pueda encadenar aturdimientos hay tres frenos, todos en ServerConfig:
 * tope de duración (stun.max_ticks), rendimientos decrecientes si el siguiente llega pronto
 * tras el anterior (stun.diminishing_returns) e inmunidad al terminar (stun.immunity_ticks).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class StunState {
    private StunState() {}

    /** UUID -> tick de juego hasta el que está aturdido. */
    private static final Map<UUID, Long> STUNNED_UNTIL = new ConcurrentHashMap<>();
    /** UUID -> tick hasta el que es inmune a un nuevo aturdimiento. */
    private static final Map<UUID, Long> IMMUNE_UNTIL = new ConcurrentHashMap<>();
    /** UUID -> tick hasta el que el siguiente aturdimiento cuenta como encadenado. */
    private static final Map<UUID, Long> CHAIN_UNTIL = new ConcurrentHashMap<>();
    /** UUID -> aturdimientos consecutivos (para los rendimientos decrecientes). */
    private static final Map<UUID, Integer> CHAIN = new ConcurrentHashMap<>();

    /**
     * Aturde a la víctima. Devuelve los ticks que realmente se aplicaron, o 0 si no se aplicó
     * (ya aturdida, inmune, jugador con stun.affect_players apagado o duración final nula).
     * También rompe la guardia: un aturdido no puede seguir bloqueando.
     */
    public static int apply(LivingEntity target, int ticks) {
        if (target.level().isClientSide() || ticks <= 0) return 0;
        if (target instanceof ServerPlayer && !ServerConfig.stunAffectsPlayers()) return 0;
        UUID id = target.getUUID();
        long now = target.level().getGameTime();
        if (isStunned(target)) return 0;
        Long immune = IMMUNE_UNTIL.get(id);
        if (immune != null && immune > now) return 0;

        // Rendimientos decrecientes: cada aturdimiento que llega dentro de la ventana tras el
        // anterior dura una fracción más corta. Fuera de la ventana la cadena se reinicia.
        Long chainUntil = CHAIN_UNTIL.get(id);
        int chain = (chainUntil != null && chainUntil > now) ? CHAIN.getOrDefault(id, 0) : 0;
        double dr = ServerConfig.stunDiminishingReturns();
        int applied = (int) Math.round(ticks * Math.pow(dr, chain));
        applied = Math.min(applied, ServerConfig.stunMaxTicks());
        if (applied < 1) return 0;

        long until = now + applied;
        STUNNED_UNTIL.put(id, until);
        IMMUNE_UNTIL.put(id, until + ServerConfig.stunImmunityTicks());
        CHAIN_UNTIL.put(id, until + ServerConfig.stunDrWindowTicks());
        CHAIN.put(id, chain + 1);

        GuardBreak.breakGuard(target, applied);
        if (target instanceof Mob mob) mob.getNavigation().stop();
        if (target instanceof ServerPlayer sp) com.hmc.zenkai.feature.action.ActionResolver.interruptByStun(sp);
        broadcast(target, applied, ServerConfig.stunImmunityTicks());
        return applied;
    }

    /**
     * Sujeción de un AGARRE: deja a la víctima inmóvil durante {@code ticks} sin pasar por el tope, los rendimientos
     * decrecientes ni la inmunidad (el agarre es una mecánica propia con su salida: {@link #release}). Rompe la
     * guardia y corta lo que hiciera, como cualquier aturdimiento. Un jugador con {@code stun.affect_players}
     * apagado no queda aturdido (el cliente le anula el input por su propio estado de agarre).
     */
    public static void hold(LivingEntity target, int ticks) {
        if (target.level().isClientSide() || ticks <= 0) return;
        if (target instanceof ServerPlayer && !ServerConfig.stunAffectsPlayers()) return;
        UUID id = target.getUUID();
        STUNNED_UNTIL.put(id, target.level().getGameTime() + ticks);
        GuardBreak.breakGuard(target, ticks);
        if (target instanceof Mob mob) mob.getNavigation().stop();
        if (target instanceof ServerPlayer sp) com.hmc.zenkai.feature.action.ActionResolver.interruptByStun(sp);
        broadcast(target, ticks, 0);
    }

    /**
     * Corta el aturdimiento antes de tiempo (el agarrado que se zafa o al que sueltan).
     * @param keepImmunity true = arranca la inmunidad normal desde ESTE tick; false = sin inmunidad (el lanzado debe
     *                     poder aturdirse al aterrizar). No toca los rendimientos decrecientes.
     * @return si había algo que cortar
     */
    public static boolean release(LivingEntity target, boolean keepImmunity) {
        if (target.level().isClientSide() || !isStunned(target)) return false;
        UUID id = target.getUUID();
        STUNNED_UNTIL.remove(id);
        int imm = keepImmunity ? ServerConfig.stunImmunityTicks() : 0;
        if (keepImmunity) IMMUNE_UNTIL.put(id, target.level().getGameTime() + imm);
        else IMMUNE_UNTIL.remove(id);
        broadcast(target, 0, imm);
        return true;
    }

    /** ¿Está aturdido ahora mismo? Caduca sola y se limpia al consultarla. */
    public static boolean isStunned(LivingEntity e) {
        Long until = STUNNED_UNTIL.get(e.getUUID());
        if (until == null) return false;
        if (until <= e.level().getGameTime()) {
            STUNNED_UNTIL.remove(e.getUUID());
            return false;
        }
        return true;
    }

    /** Ticks que le quedan de aturdimiento (0 si no lo está). */
    public static int remaining(LivingEntity e) {
        Long until = STUNNED_UNTIL.get(e.getUUID());
        if (until == null) return 0;
        return (int) Math.max(0, until - e.level().getGameTime());
    }

    private static void broadcast(LivingEntity target, int ticks, int immunityTicks) {
        StunSyncPacket pkt = new StunSyncPacket(target.getId(), ticks, immunityTicks);
        if (target instanceof ServerPlayer sp) PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, pkt);
        else PacketDistributor.sendToPlayersTrackingEntity(target, pkt);
    }

    private static void forget(UUID id) {
        STUNNED_UNTIL.remove(id);
        IMMUNE_UNTIL.remove(id);
        CHAIN_UNTIL.remove(id);
        CHAIN.remove(id);
    }

    /** Un mob aturdido se queda clavado: sin navegación y sin deslizar horizontalmente. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post e) {
        if (STUNNED_UNTIL.isEmpty()) return;
        if (!(e.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        if (!isStunned(mob)) return;
        mob.getNavigation().stop();
        Vec3 d = mob.getDeltaMovement();
        mob.setDeltaMovement(0, d.y, 0);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!e.getEntity().level().isClientSide()) {
            if (isStunned(e.getEntity())) broadcast(e.getEntity(), 0, 0);
            forget(e.getEntity().getUUID());
        }
    }

    /** Cubre logout, descarga y muerte de mobs: nada de UUID colgados en los mapas. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        Entity en = e.getEntity();
        if (!en.level().isClientSide()) forget(en.getUUID());
    }

    /** Quien empieza a ver a un aturdido recibe cuánto le queda. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getEntity() instanceof ServerPlayer viewer)) return;
        if (!(e.getTarget() instanceof LivingEntity t)) return;
        int left = remaining(t);
        if (left > 0) PacketDistributor.sendToPlayer(viewer, new StunSyncPacket(t.getId(), left, 0));
    }
}
