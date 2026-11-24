package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.config.ServerConfig;

/**
 * Búfer de FE del generador. Al revés que BenchEnergy: este SÍ deja extraer y NO deja meter.
energy_generator * La asimetría es deliberada y es lo que hace que la pareja generador + banco funcione sin
 * configurar nada: el generador solo empuja, el banco solo recibe, y no hay forma de montar
 * un bucle en el que dos máquinas se pasen la misma energía. Con un IEnergyStorage genérico
 * en ambos lados habría que decidir por cara qué hace cada una.
energy_generator * receiveEnergy devuelve 0 de cara al exterior pero generate() sí llena: lo mismo que hace
 * BenchEnergy con spend(), en espejo. Lo interno no pasa por la interfaz.
 */
public class GeneratorEnergy extends MachineEnergy {
    // Desde el 2026-11-23 la lógica vive en MachineEnergy: aquí solo la dirección (salida) y las claves de config.
    public GeneratorEnergy() {
        super(Io.OUTPUT, ServerConfig::energyGeneratorCapacity, ServerConfig::energyGeneratorMaxExtract, true, () -> { });
    }
}
