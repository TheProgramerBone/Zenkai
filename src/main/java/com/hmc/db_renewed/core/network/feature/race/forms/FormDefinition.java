package com.hmc.db_renewed.core.network.feature.race.forms;

import com.hmc.db_renewed.core.network.feature.Race;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;

public record FormDefinition(
        ResourceLocation id,
        EnumSet<Race> allowedRaces,
        int holdTicksRequired,
        double kiDrainPerTick,              // 0.0 si no drena
        ResourceLocation nextFormId          // para MVP: 1 siguiente. Luego puedes usar List<ResourceLocation>
) {}