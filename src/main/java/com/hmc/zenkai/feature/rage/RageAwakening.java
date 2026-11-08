package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.feature.Race;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Un despertar declarado en {@code data/<ns>/zenkai_rage_awakenings/}: qué forma, para qué raza, con qué acontecimientos y
 * qué exige además de tener el padre. Es contenido (un addon puede declarar el de su forma); el balance (minutos, enfriamiento)
 * vive en ServerConfig y los detectores en código.
 *
 * @param minParentMastery maestría mínima (0-100, en %) del padre para que el despertar se conceda; SOLO para esta vía, no
 *                         cambia lo que pide la forma por compra o ritual. Si no llega, el hecho suma a la maestría del padre
 * @param awakens          false = entrada solo de PROGRESO: suma maestría a la forma si YA la posee y nunca la desbloquea
 *                         (también el sitio donde dejar un despertar a la espera de un requisito aún sin hacer)
 */
public record RageAwakening(ResourceLocation form, Race race, Set<RageTrigger> triggers, RageIntensity minIntensity,
                            double minParentMastery, boolean awakens) {

    public boolean matches(RageTrigger trigger, RageIntensity intensity) {
        return triggers.contains(trigger) && intensity.atLeast(minIntensity);
    }
}
