package com.hmc.zenkai.feature.transport;

/**
 * Modo de cada cara de un cable, tubería o tanque (el mismo vocabulario que Mekanism).
 *
 * <ul>
 *   <li>NORMAL: conecta y deja pasar en los dos sentidos. Acepta lo que le empujen (un generador
 *       pegado, otra tubería) y se vacía hacia lo que tenga delante y lo acepte.</li>
 *   <li>PUSH: solo EMPUJA hacia lo de delante; no acepta nada desde ese lado.</li>
 *   <li>PULL: solo TIRA de lo de delante (extrae de su capability); no le entrega nada.</li>
 *   <li>NONE: la cara está desconectada: ni brazo visible ni transferencia ni capability.</li>
 * </ul>
 *
 * Entre dos bloques de transporte la regla es simétrica: A puede darle a B si A.canGive() y
 * B.canTake(), así un PUSH frente a un PULL funciona y un PUSH frente a un PUSH no hace nada.
 */
public enum SideMode implements net.minecraft.util.StringRepresentable {
    NORMAL, PUSH, PULL, NONE;

    @Override
    public @org.jetbrains.annotations.NotNull String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public SideMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** El anterior: clic derecho en la pantalla de caras (como en Mekanism). */
    public SideMode prev() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    /** ¿Puede entregar a lo que tiene delante por esta cara? */
    public boolean canGive() { return this == NORMAL || this == PUSH; }

    /** ¿Puede recibir de lo que tiene delante por esta cara? */
    public boolean canTake() { return this == NORMAL || this == PULL; }

    public String langKey() { return "messages.zenkai.transport.mode." + name().toLowerCase(java.util.Locale.ROOT); }
}
