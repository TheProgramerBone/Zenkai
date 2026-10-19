package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.content.entity.ai.BlockingMob;
import com.hmc.zenkai.feature.action.ActionStateServer;
import com.hmc.zenkai.feature.action.ActionType;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ruptura de guardia (la provoca el empujón del kiai): quien estaba bloqueando pierde la guardia
 * de golpe y no puede volver a levantarla durante unos ticks. Es lo que el parry NO hace: el parry
 * defiende, esto abre al rival a un ataque.
 *
 * Jugadores: se limpia el ActionState BLOCK y su ralentización, y ActionResolver.setBlocking
 * rechaza bloquear mientras dure. Mobs (BlockingMob): se les apaga la guardia y BlockHabitGoal no
 * la vuelve a encender mientras dure. Se llama ANTES de aplicar el daño del kiai, para que ese
 * mismo golpe ya no sea mitigado por la guardia que acaba de romper.
 */
public final class GuardBreak {
    private GuardBreak() {}

    /** UUID -> tick de juego hasta el que no puede bloquear. */
    private static final Map<UUID, Long> BROKEN_UNTIL = new ConcurrentHashMap<>();

    /** @return true si tenía la guardia levantada y se la ha roto. */
    public static boolean breakGuard(LivingEntity target, int ticks) {
        if (ticks <= 0) return false;
        boolean wasBlocking = false;
        if (target instanceof ServerPlayer sp) {
            wasBlocking = KiCombatServer.isBlocking(sp);
            if (wasBlocking) {
                ActionStateServer.clearIf(sp, ActionType.BLOCK);
                KiCombatServer.applyBlocking(sp, false);
            }
        } else if (target instanceof BlockingMob bm && bm.isBlockingNow()) {
            wasBlocking = true;
            bm.setBlocking(false);
        }
        if (wasBlocking) {
            BROKEN_UNTIL.put(target.getUUID(), target.level().getGameTime() + ticks);
            // El cliente del propio jugador tiene que dejar de mandar «estoy bloqueando» aunque siga
            // pulsando la tecla (el packet de bloqueo solo viaja al cambiar, no se reenvía).
            if (target instanceof ServerPlayer sp) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new GuardSyncPacket(GuardStamina.current(sp), GuardStamina.max(sp), ticks));
            }
        }
        return wasBlocking;
    }

    /** ¿Tiene la guardia rota ahora mismo? Caduca sola y se limpia al consultarla. */
    public static boolean isBroken(LivingEntity e) {
        Long until = BROKEN_UNTIL.get(e.getUUID());
        if (until == null) return false;
        if (until <= e.level().getGameTime()) {
            BROKEN_UNTIL.remove(e.getUUID());
            return false;
        }
        return true;
    }
}
