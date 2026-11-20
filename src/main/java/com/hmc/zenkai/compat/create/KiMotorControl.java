package com.hmc.zenkai.compat.create;

import com.hmc.zenkai.feature.transport.SideMode;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Lo que la pantalla y el packet del Motor de Ki ven del bloque. Es una interfaz SIN ninguna clase
 * de Create a propósito: {@link KiMotorBlockEntity} extiende una clase de Create, y el packet (que
 * se registra en ModNetworking, común a todos los entornos) y la pantalla (cliente) no pueden
 * nombrarlo sin arrastrar Create. Con la interfaz basta {@code instanceof KiMotorControl}.
 * El enum {@link Mode} vive aquí y {@code KiMotorBlockEntity.Mode} sigue funcionando por herencia.
 */
public interface KiMotorControl {

    enum Mode { MOTOR, GENERATOR }

    /** Velocidades del selector de la pantalla (aquí y no en el block entity por lo mismo que Mode). */
    int[] RPM_STEPS = {32, 64, 128, 256};

    // ── Lectura (la pantalla la pinta con lo que llega por sendData) ─────────
    Mode mode();
    int speedStep();
    int rpm();
    boolean reversed();
    boolean active();
    int fe();
    int feCapacity();
    FluidStack kiFluid();
    int kiCapacity();
    /** Cara del eje (el FACING del bloque): no es configurable. */
    Direction shaftSide();
    SideMode energyMode(Direction face);
    SideMode fluidMode(Direction face);
    /** Carga que pide la red de Create, 0..100 (modo MOTOR). Sincronizada por el propio bloque. */
    int loadPercent();
    /** FE/t medios de los últimos 10 ticks, por 10 (un entero viaja mejor que un decimal). */
    int feRateX10();

    // ── Escritura (solo el servidor la ejecuta; el packet valida antes) ──────
    void setMode(Mode mode);
    void setSpeedStep(int step);
    void toggleReversed();
    SideMode cyclePort(Direction face, boolean fluid);
    /** Ejecuta {@code change} (un cambio de orientación del bloque) sacando al motor de la red de Create antes y volviéndolo a meter después, como hace la llave de Create. */
    void rotateKinetics(Runnable change);
}
