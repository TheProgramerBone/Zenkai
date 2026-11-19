package com.hmc.zenkai.mixin;

import net.minecraft.world.entity.monster.ZombieVillager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.UUID;

/**
 * {@code ZombieVillager#startConverting} es privada y vanilla solo la llama al darle una manzana
 * dorada con Debilidad. El agua curativa de Namek (HealingWaterBlock) necesita iniciar la misma
 * curación, con el mismo procedimiento de vanilla, sin reimplementarla.
 */
@Mixin(ZombieVillager.class)
public interface ZombieVillagerInvoker {
    @Invoker("startConverting")
    void zenkai$startConverting(@Nullable UUID starter, int ticks);
}
