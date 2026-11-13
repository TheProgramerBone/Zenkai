package com.hmc.zenkai.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Se postea cuando un jugador GANA TP por entrenar (golpear, matar, Meditación, Target Practice),
 * en {@code TrainingHooks.grant}, DESPUÉS de aplicar fatiga, pesas, HTC y bonos de raza y ANTES de
 * sumarlo. Mutable ({@link #setAmount}) y cancelable (no se suma nada). NO se postea al gastar TP,
 * al devolverlo por olvidar una skill, ni por comandos o deseos: solo es la ganancia por entrenamiento.
 * Un valor {@code <= 0} tras los oyentes equivale a cancelarlo. La fatiga y el resto fraccional ya
 * se contabilizaron con el valor ORIGINAL, así que bonificar aquí no altera el ritmo de cansancio.
 */
public final class TPGainedEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final String category;
    private int amount;

    public TPGainedEvent(ServerPlayer player, String category, int amount) {
        this.player = player;
        this.category = category;
        this.amount = amount;
    }

    public ServerPlayer player() { return player; }
    /** {@code combat}, {@code meditation} o {@code target_practice}. */
    public String category() { return category; }
    public int amount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
