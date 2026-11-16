package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.api.event.CriticalSurvivalEvent;

import com.hmc.zenkai.api.event.PartyMemberDiedEvent;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.api.event.PlayerBodyInteractEvent;
import com.hmc.zenkai.feature.player.death.PlayerBodyManager;
import com.hmc.zenkai.api.event.PlayerDownedEvent;
import com.hmc.zenkai.api.event.PlayerRescuedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.List;

/**
 * Detectores de Rage Forms: traducen los eventos del mod a «pasó {@link RageTrigger} a este beneficiario» y se lo pasan a
 * {@link RageSystem}. Un único lector de muertes (también el de mascotas, que antes tenía BeastFury).
 *
 * Solo cuenta la muerte REAL: DownedDeathGuard cancela la primera y esa cancelación no llega aquí (los oyentes no reciben
 * eventos cancelados); la de verdad, al acabarse el derribo, sí. Sirve aunque no haya cuerpo (keepInventory, gamerule apagada),
 * por eso no se usa PartyMemberDiedEvent. Testigos y radio: {@link PlayerBodyManager#partyWitnesses}, un solo criterio.
 * Antiabuso: quien mató (él o su mascota o proyectil) no se premia a sí mismo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class RageListeners {
    private RageListeners() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide() || dead.getServer() == null) return;
        long now = dead.level().getGameTime();

        // Mascota con dueño: furia solo para SU dueño, si estaba cerca (mismo radio de testigos).
        if (dead instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            int radius = ServerConfig.PERMADEATH_WITNESS_RADIUS.get();
            ServerPlayer owner = dead.getServer().getPlayerList().getPlayer(pet.getOwnerUUID());
            if (radius > 0 && owner != null && owner != dead && owner.isAlive() && owner.level() == dead.level()
                    && owner.distanceToSqr(dead) <= (double) radius * radius && !killedBy(e.getSource(), owner)
                    // Sin causante (lava, caída, ahogo...): la muerte de una mascota por el entorno no es un acontecimiento, es farmeable.
                    && e.getSource().getEntity() != null) {
                RageSystem.handle(owner, RageTrigger.PET_DEATH, dead.getUUID() + ":" + now, dead.getName().getString());
            }
            return;
        }

        if (!(dead instanceof ServerPlayer victim)) return;
        String id = victim.getUUID() + ":" + now;
        for (ServerPlayer w : PlayerBodyManager.partyWitnesses(victim)) {
            if (killedBy(e.getSource(), w)) continue;
            // Un aliado muere mientras el testigo está derribado: la fila Excepcional.
            RageTrigger t = PlayerStatsAttachment.get(w).flags().isDowned()
                    ? RageTrigger.ALLY_DIES_WHILE_DOWNED : RageTrigger.ALLY_DEATH;
            RageSystem.handle(w, t, id, victim.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public static void onDowned(PlayerDownedEvent e) {
        String id = e.downed().getUUID() + ":" + e.downedUntil();
        for (ServerPlayer w : e.witnesses()) RageSystem.handle(w, RageTrigger.ALLY_DOWNED, id, e.downed().getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onRescued(PlayerRescuedEvent e) {
        // Salvar a un aliado: el rescatado y el rescatador comparten party (estar cerca no basta, ni salvar a un desconocido).
        if (!sameParty(e.rescuer(), e.rescued())) return;
        long until = e.rescued().level().getGameTime() + e.ticksLeft();
        RageSystem.handle(e.rescuer(), RageTrigger.ALLY_RESCUED, e.rescued().getUUID() + ":" + until,
                e.rescued().getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onBodyInteract(PlayerBodyInteractEvent e) {
        if (e.who() == null || !e.ally()) return;
        if (e.kind() != PlayerBodyInteractEvent.Kind.CARRY && e.kind() != PlayerBodyInteractEvent.Kind.BURY) return;
        RageSystem.handle(e.who(), RageTrigger.BODY_GESTURE, e.body().getUUID() + ":" + e.kind(), e.body().ownerName());
    }

    @SubscribeEvent
    public static void onCriticalSurvival(CriticalSurvivalEvent e) {
        // Solo con un aliado caído (derribado) cerca: sobrevivir en solitario no es este acontecimiento.
        List<ServerPlayer> near = PlayerBodyManager.partyWitnesses(e.survivor());
        ServerPlayer fallen = near.stream().filter(p -> PlayerStatsAttachment.get(p).flags().isDowned()).findFirst().orElse(null);
        if (fallen == null) return;
        RageSystem.handle(e.survivor(), RageTrigger.CRITICAL_SURVIVAL,
                e.survivor().getUUID() + ":" + e.survivor().level().getGameTime(), fallen.getGameProfile().getName());
    }

    private static boolean killedBy(DamageSource src, ServerPlayer p) {
        var attacker = src.getEntity();
        if (attacker == p) return true;
        return attacker instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID());
    }

    private static boolean sameParty(ServerPlayer a, ServerPlayer b) {
        if (a == null || b == null || a == b) return false;
        PartyManager.Party party = PartyManager.get(a.server).partyOf(a.getUUID());
        return party != null && party.members.contains(b.getUUID());
    }
}
