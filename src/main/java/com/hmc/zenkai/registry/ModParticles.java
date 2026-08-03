package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.particle.ZenkaiParticleOptions;
import com.hmc.zenkai.content.particle.ZenkaiParticleType;
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

    /** Núcleo del Black Flash: misma mecánica que KI_IMPACT (6 frames por edad, quieto). */
    public static final DeferredHolder<ParticleType<?>, ZenkaiParticleType> BLACK_FLASH_CORE =
            PARTICLES.register("black_flash_core", ZenkaiParticleType::new);

    /** Filo del Black Flash: mismo perfil dentado que el núcleo, solo el borde. */
    public static final DeferredHolder<ParticleType<?>, ZenkaiParticleType> BLACK_FLASH_RIM =
            PARTICLES.register("black_flash_rim", ZenkaiParticleType::new);

    // Los dos colores del Black Flash viven AQUÍ y en ningún otro sitio. No salen de
    // AuraColors a propósito: es el único efecto del mod con paleta fija, porque su lectura
    // depende del CONTRASTE entre el negro y el rojo. Si el núcleo tomara el color del aura,
    // un aura roja lo volvería ilegible.
    private static final int BF_CORE_RGB = 0x0A0000;
    private static final int BF_RIM_RGB  = 0xFF2814;

    public static ZenkaiParticleOptions blackFlashCore(float scale) {
        return new ZenkaiParticleOptions(BLACK_FLASH_CORE.get(), BF_CORE_RGB, scale);
    }

    public static ZenkaiParticleOptions blackFlashRim(float scale) {
        return new ZenkaiParticleOptions(BLACK_FLASH_RIM.get(), BF_RIM_RGB, scale);
    }

    /** Chispas del Black Flash: la KI_SPARK de siempre, forzada al rojo del filo. */
    public static ZenkaiParticleOptions blackFlashSpark(float scale) {
        return new ZenkaiParticleOptions(KI_SPARK.get(), BF_RIM_RGB, scale);
    }

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}