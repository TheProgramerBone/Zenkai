package com.hmc.zenkai.content.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Opciones compartidas por TODAS las partículas del mod: color (RGB, sin alfa) y escala.
 * Él {@code type} viaja en el record, pero NO se serializa: lo aporta el ParticleType al
 * construir su propio codec, así una sola clase sirve para ki_impact, ki_spark y las que vengan.
 * El color lo resuelve el SERVIDOR con AuraColors.resolve(player) y viaja en el packet de
 * partículas, así que todos los clientes ven el mismo tinte sin sincronizar nada extra.
 */
public record ZenkaiParticleOptions(ParticleType<ZenkaiParticleOptions> type, int rgb, float scale)
        implements ParticleOptions {

    @Override
    public @NotNull ParticleType<?> getType() {
        return type;
    }

    public float red()   { return ((rgb >> 16) & 0xFF) / 255.0f; }
    public float green() { return ((rgb >> 8)  & 0xFF) / 255.0f; }
    public float blue()  { return (rgb & 0xFF) / 255.0f; }

    public static MapCodec<ZenkaiParticleOptions> codec(ParticleType<ZenkaiParticleOptions> t) {
        return RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.fieldOf("rgb").forGetter(ZenkaiParticleOptions::rgb),
                Codec.FLOAT.fieldOf("scale").forGetter(ZenkaiParticleOptions::scale)
        ).apply(inst, (rgb, scale) -> new ZenkaiParticleOptions(t, rgb, scale)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ZenkaiParticleOptions> streamCodec(
            ParticleType<ZenkaiParticleOptions> t) {
        return StreamCodec.composite(
                ByteBufCodecs.INT,   ZenkaiParticleOptions::rgb,
                ByteBufCodecs.FLOAT, ZenkaiParticleOptions::scale,
                (rgb, scale) -> new ZenkaiParticleOptions(t, rgb, scale));
    }
}