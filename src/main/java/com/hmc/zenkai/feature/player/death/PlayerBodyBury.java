package com.hmc.zenkai.feature.player.death;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterrar un cuerpo. Con {@code permadeath.bury_duration_ticks} = 0 es instantáneo; con más, queda una tarea con barra de
 * progreso en la action bar que se cancela si el jugador se aleja, muere o (si {@code bury_requires_shovel}) suelta la pala.
 * Solo se puede con el cuerpo VACÍO (objetos y XP). El alineamiento solo sube si el cuerpo es AJENO: enterrar el propio no da
 * nada, o morir a propósito sería una fuente de alineamiento.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PlayerBodyBury {
    private PlayerBodyBury() {}

    private static final double MAX_DIST_SQR = 16.0;

    private static final class Task {
        final UUID body; final int total; int left;
        Task(UUID body, int total) { this.body = body; this.total = total; this.left = total; }
    }

    private static final Map<UUID, Task> TASKS = new ConcurrentHashMap<>();

    public static boolean canBury(ServerPlayer sp, PlayerBodyEntity body) {
        if (body.hasItems() || body.xp() > 0 || !PlayerBodyAccess.canOpen(sp, body)) return false;
        return !ServerConfig.PERMADEATH_BURY_NEEDS_SHOVEL.get() || sp.getMainHandItem().is(ItemTags.SHOVELS);
    }

    /** Empieza a enterrar. Devuelve true si el menú debe cerrarse (la tarea sigue en segundo plano). */
    public static boolean start(ServerPlayer sp, PlayerBodyEntity body) {
        if (!canBury(sp, body)) {
            if (ServerConfig.PERMADEATH_BURY_NEEDS_SHOVEL.get() && !sp.getMainHandItem().is(ItemTags.SHOVELS))
                sp.displayClientMessage(Component.translatable("zenkai.body.bury_needs_shovel"), true);
            return false;
        }
        int ticks = ServerConfig.PERMADEATH_BURY_TICKS.get();
        if (ticks <= 0) { finish(sp, body); return true; }
        TASKS.put(sp.getUUID(), new Task(body.getUUID(), ticks));
        return true;
    }

    private static void finish(ServerPlayer sp, PlayerBodyEntity body) {
        boolean own = body.owner().map(o -> o.equals(sp.getUUID())).orElse(false);
        if (!own) {
            int delta = ServerConfig.PERMADEATH_BURY_ALIGNMENT.get();
            if (delta != 0) {
                PlayerStatsAttachment.get(sp).addAlignment(delta);
                PlayerLifeCycle.sync(sp);
            }
        }
        PlayerBodyInteractEvent.fire(sp, body, PlayerBodyInteractEvent.Kind.BURY);
        sp.displayClientMessage(Component.translatable("zenkai.body.buried", body.ownerName()), true);
        body.retire(false);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Task t = TASKS.get(sp.getUUID());
        if (t == null) return;
        if (!(sp.level() instanceof ServerLevel lvl) || !(lvl.getEntity(t.body) instanceof PlayerBodyEntity body)
                || !sp.isAlive() || sp.distanceToSqr(body) > MAX_DIST_SQR || !canBury(sp, body)) {
            TASKS.remove(sp.getUUID());
            sp.displayClientMessage(Component.translatable("zenkai.body.bury_cancelled"), true);
            return;
        }
        if (--t.left <= 0) {
            TASKS.remove(sp.getUUID());
            finish(sp, body);
            return;
        }
        sp.displayClientMessage(Component.translatable("zenkai.body.burying", (t.total - t.left) * 100 / t.total), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        TASKS.remove(e.getEntity().getUUID());
    }
}
