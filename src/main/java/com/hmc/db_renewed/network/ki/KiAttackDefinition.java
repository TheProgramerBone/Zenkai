package com.hmc.db_renewed.network.ki;

public record KiAttackDefinition(
        String id,            // id interno único del ataque
        String displayName,   // nombre mostrado
        KiAttackType type,    // BLAST / BEAM / DISK
        double basePower,     // daño base (medio-corazones equiv) antes de mults
        double speed,         // bloques/tick
        int cooldownTicks,    // enfriamiento
        int chargeTimeTicks,  // tiempo para 100% de carga
        int density,          // "peso" para choques/colisiones entre ataques
        int rgbColor          // color ARGB o RGB (usamos RGB 0xRRGGBB)
) {}