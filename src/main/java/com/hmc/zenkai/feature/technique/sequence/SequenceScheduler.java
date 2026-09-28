package com.hmc.zenkai.feature.technique.sequence;

import com.hmc.zenkai.Zenkai;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Temporizador en ticks para {@code PauseStep}, DELIBERADAMENTE independiente del tick de
 * jugador de {@code ZenkaiTickHandlers} (cuyo orden de sistemas es semántica delicada, ver su
 * javadoc de clase — una secuencia de técnica no tiene ningún motivo para acoplarse a eso).
 * Cuelga de {@code ServerTickEvent.Post} global (una vez por tick de servidor, no por jugador),
 * y como el bucle del servidor es de un solo hilo, la lista no necesita sincronización.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class SequenceScheduler {
    private SequenceScheduler() {}

    private static final class Pending {
        int ticksLeft;
        final CompletableFuture<Void> future;
        Pending(int ticksLeft, CompletableFuture<Void> future) {
            this.ticksLeft = ticksLeft;
            this.future = future;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** {@code ticks <= 0} completa ya mismo, en vez de esperar un tick de más por un PauseStep(0). */
    public static CompletableFuture<Void> after(int ticks) {
        if (ticks <= 0) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> future = new CompletableFuture<>();
        PENDING.add(new Pending(ticks, future));
        return future;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (--p.ticksLeft <= 0) {
                it.remove();
                p.future.complete(null);
            }
        }
    }
}
