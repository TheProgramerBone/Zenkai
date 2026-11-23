package com.hmc.zenkai.client.fly;

import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Crucero de vuelo: tercer paso del ciclo de Control (OFF → Vuelo rápido → Crucero → Vuelo rápido → OFF, ver
 * ClientZenkaiPalTick). Es el vuelo rápido con "adelante" pulsado de forma virtual: sigue la
 * mirada a la velocidad que da flyingSpeed (rampa y turbo del servidor incluidos) sin tener que
 * sostener W. No fija una velocidad medida: eso obligaba a ir ya rápido para poder activarlo.
 *
 * Aquí solo vive el estado y las condiciones de corte; el movimiento lo aplica FlightMovement.
 * SOLO jugador local y solo cliente. Space/shift siguen siendo control vertical manual.
 * Se sale del crucero (volviendo al vuelo rápido, que sigue activo) con atrás (S) o al chocar
 * contra una pared; con el ciclo de Control o al dejar de volar se sale por completo.
 * En MODO COMBATE no existe (2026-11-22): el ciclo se queda en OFF → Vuelo rápido → OFF y entrar en combate
 * corta el crucero (ver ClientZenkaiPalTick).
 */
public final class FlightCruise {

    private FlightCruise() {}

    private static final double CANCEL_BACK_INPUT = -0.1;

    private static boolean active = false;

    public static boolean isActive() { return active; }

    public static void reset() { active = false; }

    /** Activa el crucero. Lo llama el ciclo de Control al pasar de vuelo rápido a crucero. */
    public static void start(LocalPlayer p) {
        active = true;
        p.displayClientMessage(Component.translatable("messages.zenkai.fly_cruise.on")
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** Apaga el crucero con aviso. Lo llama el ciclo de Control al volver a OFF. */
    public static void stop(LocalPlayer p) {
        if (!active) return;
        active = false;
        p.displayClientMessage(Component.translatable("messages.zenkai.fly_cruise.off")
                .withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * Comprueba los cortes automáticos. Devuelve true si el crucero SIGUE activo este tick.
     * Un corte deja el vuelo rápido encendido: solo se pierde el empuje automático.
     */
    public static boolean tickKeepActive(LocalPlayer p) {
        if (!active) return false;
        if (p.input.forwardImpulse < CANCEL_BACK_INPUT || p.horizontalCollision) {
            stop(p);
            return false;
        }
        return true;
    }
}
