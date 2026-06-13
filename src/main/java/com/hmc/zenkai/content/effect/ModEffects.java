package com.hmc.zenkai.content.effect;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    private ModEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Zenkai.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> IMMORTALITY =
            EFFECTS.register("immortality", ImmortalityEffect::new);

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }
}