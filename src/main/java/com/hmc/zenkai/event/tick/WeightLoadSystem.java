package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.weights.WeightSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recalcula la carga de las pesas y vuelca sus dos derivados en el attachment. Es el ÚNICO
 * punto de escritura de weightLoad/weightFactor, igual que FormSystem lo es de statMultiplier.
 * Va antes que lo demás en el orquestador a propósito: FormSystem puede cortar el tick
 * (transformación en curso) y dejaría el factor congelado. Como capacidad usa el
 * statMultiplier del tick ANTERIOR, hay un tick de desfase al transformarse — invisible.
 * El salto se aplica aquí porque no hay ningún otro sistema que toque JUMP_STRENGTH.
 */
public final class WeightLoadSystem {
    private WeightLoadSystem() {}

    private static final ResourceLocation JUMP_MOD_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "weight_jump");

    /** Cada cuántos ticks se repite el aviso de sobrecarga. 5 s. */
    private static final int WARN_INTERVAL = 100;

    /** Último tick en que cada jugador PIDIÓ saltar. `LivingEntity.jumping` es protected y no
     *  tiene getter, así que la intención de salto llega por LivingJumpEvent (que se dispara
     *  aunque la fuerza de salto sea 0, que es exactamente el caso de la sobrecarga).
     *  ConcurrentHashMap: el evento y el tick pueden venir de hilos distintos. */
    private static final Map<UUID, Long> LAST_JUMP = new ConcurrentHashMap<>();

    /** Lo llama ZenkaiTickHandlers desde LivingJumpEvent. */
    public static void noteJump(Player p) {
        LAST_JUMP.put(p.getUUID(), p.level().getGameTime());
    }

    /** Limpieza al desloguear: si no, el mapa crece sin fin en servidores. */
    public static void forget(UUID id) {
        LAST_JUMP.remove(id);
    }

    public static void tick(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();

        double load = WeightSystem.computeLoad(p);
        att.setWeightLoad(load);
        att.setWeightFactor(WeightSystem.statFactor(load));

        applyJump(p, WeightSystem.jumpFactor(load));

        // El aviso solo salta cuando el jugador INTENTA moverse, no de forma continua:
        // destransformarse con 2000 t encima te sobrecarga al instante y sería spam.
        if (WeightSystem.isOverloaded(load) && isTryingToMove(p)
                && p.tickCount % WARN_INTERVAL == 0) {
            p.displayClientMessage(Component.translatable("messages.zenkai.weight.overloaded"), true);
        }
    }

    private static boolean isTryingToMove(Player p) {
        if (p.zza != 0.0F || p.xxa != 0.0F) return true;
        // Ventana igual al intervalo del aviso: si saltó en los últimos 5 s, sigue "intentando".
        Long last = LAST_JUMP.get(p.getUUID());
        return last != null && p.level().getGameTime() - last <= WARN_INTERVAL;
    }

    private static void applyJump(Player p, double factor) {
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH); // ⚠
        if (jump == null) return;
        double amount = factor - 1.0;
        if (amount >= 0.0) {
            if (jump.getModifier(JUMP_MOD_ID) != null) jump.removeModifier(JUMP_MOD_ID);
            return;
        }
        AttributeModifier current = jump.getModifier(JUMP_MOD_ID);
        if (current != null && Math.abs(current.amount() - amount) < 1.0e-6) return;
        jump.removeModifier(JUMP_MOD_ID);
        jump.addTransientModifier(new AttributeModifier(
                JUMP_MOD_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }


}