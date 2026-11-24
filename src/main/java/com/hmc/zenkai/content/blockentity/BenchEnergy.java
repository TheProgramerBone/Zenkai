package com.hmc.zenkai.content.blockentity;

/**
 * Búfer de FE del banco de scouter. Consume; NO genera ni reparte.
 *
 * Por qué no se usa el EnergyStorage de NeoForge tal cual: hace falta que extractEnergy()
 * devuelva 0 SIEMPRE de cara al exterior (un cable no puede vaciar el banco) pero que el
 * propio banco sí pueda gastar. Con la clase de NeoForge eso obliga a un wrapper aparte para
 * las caras, y acaba habiendo dos objetos donde basta uno: aquí lo externo es la interfaz y
 * lo interno es spend(), que no forma parte de ella.
 *
 * Los dos límites son de diseño, no de balance fino:
 *   CAPACITY     — un búfer lleno termina cualquier mejora del catálogo de una sentada.
 *   MAX_RECEIVE  — techo de entrada por tick, para que una red grande no lo llene de golpe
 *                  y el jugador vea la barra subir.
 */
public class BenchEnergy extends MachineEnergy {

    public static final int CAPACITY = 100_000;
    public static final int MAX_RECEIVE = 1_000;

    // Desde el 2026-11-23 la lógica vive en MachineEnergy: aquí solo la dirección (entrada) y los dos límites.
    public BenchEnergy() {
        super(Io.INPUT, () -> CAPACITY, () -> MAX_RECEIVE, true, () -> { });
    }
}
