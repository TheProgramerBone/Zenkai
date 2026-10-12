package com.hmc.zenkai.client.fly;

import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Crucero de vuelo: fija la velocidad que llevas y sigue la mirada sin tener que mantener
 * adelante. Complementa la rampa del servidor (FlightSystem.tickThrottle): la rampa decide
 * cuánto cuesta ganar velocidad, el crucero decide cuándo se conserva.
 *
 * SOLO jugador local y solo cliente, igual que FlightMovement: la posición es predicción de
 * cliente y el servidor la acepta porque abilities.flying ya está puesto. La velocidad se mide
 * al activarlo (módulo 3D del movimiento actual), así que se puede fijar una velocidad
 * intermedia soltando antes de que la rampa llegue al tope.
 *
 * Mientras está activo el vuelo vanilla no manda: ni adelante ni los laterales cambian nada
 * (se gira con el ratón). Space/shift siguen siendo control vertical manual. Se corta con la
 * tecla de crucero, con atrás (S), al chocar o al dejar de volar.
 */
public final class FlightCruise {

    private FlightCruise() {}

    /** Por debajo de esta velocidad (bloques/tick) no hay nada que fijar: sería "quedarse
     *  parado en crucero" y no se distinguiría de un fallo. */
    private static final double MIN_SPEED = 0.2;

    /** Suavizado del giro del vector hacia la mirada. Más alto = gira antes, más brusco. */
    private static final double STEER_LERP = 0.35;

    private static final double CANCEL_BACK_INPUT = -0.1;

    private static boolean active = false;
    /** Módulo 3D del movimiento fijado, en bloques por tick. */
    private static double speed = 0.0;

    public static boolean isActive() { return active; }

    public static void reset() {
        active = false;
        speed = 0.0;
    }

    /** Pulsación de la tecla de crucero. Fuera de vuelo no hace nada. */
    public static void toggle(LocalPlayer p, boolean flying) {
        if (!flying) return;
        if (active) {
            reset();
            p.displayClientMessage(Component.translatable("messages.zenkai.fly_cruise.off")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        double now = p.getDeltaMovement().length();
        if (now < MIN_SPEED) {
            p.displayClientMessage(Component.translatable("messages.zenkai.fly_cruise.too_slow")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        active = true;
        speed = now;
        p.displayClientMessage(Component.translatable("messages.zenkai.fly_cruise.on")
                .withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * Un paso del crucero. Devuelve true si este tick ha movido al jugador (entonces
     * FlightMovement no debe aplicar su reparto por mirada ni su frenazo).
     */
    public static boolean tick(LocalPlayer p) {
        if (!active) return false;

        if (p.input.forwardImpulse < CANCEL_BACK_INPUT || p.horizontalCollision || p.verticalCollision) {
            reset();
            p.displayClientMessage(Component.translatable("messages.zenkai.fly_cruise.off")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        Vec3 target = p.getLookAngle().scale(speed);
        Vec3 d = p.getDeltaMovement();
        boolean manualVertical = p.input.jumping || p.input.shiftKeyDown;
        double y = manualVertical ? d.y : Mth.lerp(STEER_LERP, d.y, target.y);
        p.setDeltaMovement(Mth.lerp(STEER_LERP, d.x, target.x), y, Mth.lerp(STEER_LERP, d.z, target.z));
        return true;
    }
}
