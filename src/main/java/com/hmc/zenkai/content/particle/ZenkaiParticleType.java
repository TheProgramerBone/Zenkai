package com.hmc.zenkai.content.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Tipo de partícula genérico del mod. Cada instancia registrada (ki_impact, ki_spark...)
 * construye sus codecs pasándose a sí misma, de modo que ZenkaiParticleOptions.getType()
 * devuelve el tipo correcto sin duplicar clases.
 */
public class ZenkaiParticleType extends ParticleType<ZenkaiParticleOptions> {

    private final MapCodec<ZenkaiParticleOptions> codec = ZenkaiParticleOptions.codec(this);
    private final StreamCodec<RegistryFriendlyByteBuf, ZenkaiParticleOptions> streamCodec =
            ZenkaiParticleOptions.streamCodec(this);

    public ZenkaiParticleType() {
        super(true);
    }

    @Override
    public MapCodec<ZenkaiParticleOptions> codec() {
        return codec;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, ZenkaiParticleOptions> streamCodec() {
        return streamCodec;
    }
}