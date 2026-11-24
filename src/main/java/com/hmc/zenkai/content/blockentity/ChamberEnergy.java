package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.config.ServerConfig;

/**
 * Búfer de FE de la cámara de contención de ki. Misma asimetría que GeneratorEnergy y por el
 * mismo motivo: SÍ deja extraer y NO deja meter, así la cámara solo empuja y no se puede montar
 * un bucle con otra máquina. Lo interno (el conversor de fluido a FE) no pasa por la interfaz:
 * usa generate().
 * Es una clase aparte y no una reutilización de GeneratorEnergy porque la capacidad y el tope
 * de salida salen de OTRAS claves de config (ki_chamber.*), y compartirlas ataría el balance de
 * las dos máquinas.
 */
public class ChamberEnergy extends MachineEnergy {
    // Desde el 2026-11-23 la lógica vive en MachineEnergy (setLimits, generate, sin recorte al cargar): aquí solo la
    // dirección (salida) y las claves ki_chamber.*. Sin recorte al cargar: las mejoras aún no han fijado los límites y
    // recortar al valor base tiraría la energía que cabía gracias al almacenamiento extra.
    public ChamberEnergy() {
        super(Io.OUTPUT, ServerConfig::kiChamberFeCapacity, ServerConfig::kiChamberFeMaxPerTick, false, () -> { });
    }

    /** Tope de salida por tick efectivo (con las mejoras de velocidad). */
    public int maxOut() { return rate(); }
}
