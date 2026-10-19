package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cadena de golpes seguidos de un jugador (solo servidor, estado en memoria: no se guarda).
 * Cada golpe dentro de {@code combo.window_ticks} del anterior suma uno a la cadena; la cadena se rompe
 * por caducidad (se comprueba al golpear, no hay tick) o al recibir daño. El bonus es lineal y con tope.
 *
 * ORDEN DE MULTIPLICADORES, fijado AQUÍ y en ningún otro sitio: combo × carga × Black Flash.
 * El combo y la carga entran en el daño en crudo del golpe (PhysicalCombatServer.execute para las técnicas,
 * CombatZenkaiHooks.playerMeleeDamage para el golpe básico) y el Black Flash se aplica DESPUÉS sobre el total,
 * que es lo que ya hacía. Si se añade otro multiplicador de golpe, va por este mismo camino.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ComboTracker {
    private ComboTracker() {}

    /** Desde este golpe se enseña el contador (los dos primeros no son un combo todavía). */
    private static final int SHOW_FROM = 3;

    private record Chain(long lastHit, int hits) {}

    private static final Map<UUID, Chain> CHAINS = new HashMap<>();

    /**
     * Registra un golpe del jugador y devuelve el multiplicador de daño que le toca a ESTE golpe.
     * El primero de una cadena vale 1.0.
     */
    public static double registerHit(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        Chain prev = CHAINS.get(sp.getUUID());
        int hits = (prev != null && now - prev.lastHit() <= ServerConfig.comboWindowTicks())
                ? prev.hits() + 1 : 1;
        CHAINS.put(sp.getUUID(), new Chain(now, hits));

        if (hits >= SHOW_FROM) {
            sp.displayClientMessage(Component.translatable("hud.zenkai.combo", hits), true);
        }
        int counted = Math.min(hits, ServerConfig.comboMaxHits());
        return 1.0 + (counted - 1) * ServerConfig.comboDamagePerHit();
    }

    /**
     * ¿El último golpe registrado de este jugador es un REMATE (cada {@code combo.finisher_every} golpes seguidos)?
     * El remate de mano vacía aleja al rival: separa a los combatientes y le da aire al PvP, y de paso hace que el
     * spam de clics no rinda más que un ritmo cómodo. 0 = sin remate.
     */
    public static boolean isFinisher(ServerPlayer sp) {
        int every = ServerConfig.comboFinisherEvery();
        Chain c = CHAINS.get(sp.getUUID());
        return every > 0 && c != null && c.hits() % every == 0;
    }

    /** Remates pendientes: atacante -> víctima. El empuje se aplica al FINAL del tick del atacante (ver onPlayerTick),
     *  porque el knockback de vanilla de Player.attack() corre después del daño y pisaría uno aplicado aquí. */
    private static final Map<UUID, net.minecraft.world.entity.LivingEntity> PENDING_FINISH = new HashMap<>();

    public static void queueFinisher(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity victim) {
        PENDING_FINISH.put(attacker.getUUID(), victim);
    }

    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (PENDING_FINISH.isEmpty() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        var victim = PENDING_FINISH.remove(sp.getUUID());
        if (victim == null || !victim.isAlive()
                || com.hmc.zenkai.feature.technique.PhysicalCombatServer.isHeld(victim)) return;
        net.minecraft.world.phys.Vec3 away = new net.minecraft.world.phys.Vec3(
                victim.getX() - sp.getX(), 0, victim.getZ() - sp.getZ());
        away = away.lengthSqr() < 1.0e-4 ? sp.getLookAngle().multiply(1, 0, 1) : away;
        if (away.lengthSqr() < 1.0e-4) return;
        away = away.normalize();
        double k = ServerConfig.comboFinisherKnockback();
        victim.setDeltaMovement(away.x * k, Math.max(victim.getDeltaMovement().y, 0.3), away.z * k);
        victim.hurtMarked = true;
        sp.serverLevel().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK, net.minecraft.sounds.SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    /** Rompe la cadena (recibir daño, morir, desconectar). */
    public static void reset(UUID id) {
        CHAINS.remove(id);
        PENDING_FINISH.remove(id);
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && e.getNewDamage() > 0f) reset(sp.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        reset(e.getEntity().getUUID());
    }
}
