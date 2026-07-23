package com.hmc.zenkai.content.particle;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, Zenkai.MOD_ID);

    /** Destello de impacto: 6 frames, animado por edad, no se mueve. */
    public static final DeferredHolder<ParticleType<?>, ZenkaiParticleType> KI_IMPACT =
            PARTICLES.register("ki_impact", ZenkaiParticleType::new);

    /** Chispa: frame aleatorio, con gravedad y rozamiento. */
    public static final DeferredHolder<ParticleType<?>, ZenkaiParticleType> KI_SPARK =
            PARTICLES.register("ki_spark", ZenkaiParticleType::new);

    // Fábricas: úsalas siempre en vez de construir el record a mano.
    public static ZenkaiParticleOptions impact(int rgb, float scale) {
        return new ZenkaiParticleOptions(KI_IMPACT.get(), rgb, scale);
    }

    public static ZenkaiParticleOptions spark(int rgb, float scale) {
        return new ZenkaiParticleOptions(KI_SPARK.get(), rgb, scale);
    }

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}