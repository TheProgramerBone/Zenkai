package com.hmc.zenkai.client.fly;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Vuelo estilo elytra: el movimiento sigue la MIRADA, no solo el yaw.
 * El vuelo creativo de vanilla mueve en horizontal según yBodyRot e ignora el pitch por
 * completo; el eje vertical es exclusivamente espacio/shift. Aquí se reorienta el vector de
 * velocidad DESPUÉS de la física del tick: lo horizontal se escala por cos(pitch) y la
 * componente vertical que falta se añade, de modo que el módulo total se conserva y mirar
 * arriba sube en vez de sumar velocidad.
 * SOLO jugador local y solo cliente: la posición del jugador es predicción de cliente y el
 * servidor la acepta porque abilities.flying ya está puesto. No hace falta paquete ni mixin.
 * Espacio y shift siguen mandando: si los pulsas, esto se aparta y el control vertical vuelve
 * a ser manual. Es deliberado — subir en vertical pura sin cambiar de rumbo tiene que seguir
 * siendo posible.
 */
public final class FlightMovement {

    private FlightMovement() {}

    /** Umbral de input para considerar que hay avance. */
    private static final float INPUT_DEADZONE = 0.1f;

    /** Suavizado de la componente vertical. Más alto = responde antes, más brusco. */
    private static final double VERTICAL_LERP = 0.5;

    /** Suelo del coseno: mirando a 90° el horizontal se anularía y el vuelo se sentiría
     *  clavado. Con 0.15 siempre queda algo de avance. */
    private static final double MIN_COS = 0.15;

    /**
     * Ganancia vertical. 1.0 = subes tan rápido como avanzas (reparto por ángulo puro).
     * Por encima de 1, ganar altura sale más barato de lo que la física diría; por debajo,
     * cuesta. Separadas porque en DBZ subir y caer no se sienten igual: dejar caer el vuelo
     * suele querer ser más rápido que treparlo.
     */
    private static final double ASCEND_GAIN  = 2;
    private static final double DESCEND_GAIN = 2;

    public static void tick(LocalPlayer p, boolean flying) {
        if (!flying) return;

        float fwd = p.input.forwardImpulse;
        if (Math.abs(fwd) < INPUT_DEADZONE) return;
        // Control vertical manual: tiene prioridad sobre la mirada.
        if (p.input.jumping || p.input.shiftKeyDown) return;

        double pitch = Math.toRadians(p.getXRot());   // xRot positivo = mirando ABAJO
        double sinUp = -Math.sin(pitch);              // mirar arriba -> componente +Y
        double cos = Math.max(MIN_COS, Math.cos(pitch));

        Vec3 d = p.getDeltaMovement();
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        if (horiz < 1.0e-3) return;

        // El módulo se reparte entre horizontal y vertical en vez de sumarse: mirar arriba
        // no debe ir más rápido que mirar al frente.
        double targetY = horiz * sinUp * Math.signum(fwd);
        targetY *= (targetY >= 0 ? ASCEND_GAIN : DESCEND_GAIN);
        double newY = Mth.lerp(VERTICAL_LERP, d.y, targetY);

        p.setDeltaMovement(d.x * cos, newY, d.z * cos);
    }
}