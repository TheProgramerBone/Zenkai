package com.hmc.zenkai.feature.capsule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Data component {@code zenkai:capsule_content} de una cápsula LLENA. Ausente = vacía.
 *
 * El ítem NO lleva los bloques: solo el id del archivo donde los guarda {@link CapsuleStorage} y el resumen del tooltip.
 * Por dos motivos: (1) los componentes de un stack viajan al cliente en cada sincronía de inventario, y una cápsula de
 * netherita puede guardar más de 12 000 bloques con sus inventarios (cientos de KB por stack, el mismo problema que los
 * libros gigantes de vanilla); (2) ANTI-DUPLICACIÓN: si alguien clona el stack (creativo, un fallo de otro mod), las dos
 * copias apuntan al MISMO archivo, y al soltar la primera el archivo se borra, así que la segunda queda vacía.
 *
 * {@code radius} es el de la zona al guardar: si el admin cambia {@code capsules.radii} después, la cápsula pega lo que
 * guardó con su tamaño original.
 */
public record CapsuleContent(UUID id, int blocks, int mobs, int radius) {

    public static final Codec<CapsuleContent> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(CapsuleContent::id),
            Codec.INT.fieldOf("blocks").forGetter(CapsuleContent::blocks),
            Codec.INT.fieldOf("mobs").forGetter(CapsuleContent::mobs),
            Codec.INT.fieldOf("radius").forGetter(CapsuleContent::radius)
    ).apply(i, CapsuleContent::new));

    public static final StreamCodec<ByteBuf, CapsuleContent> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CapsuleContent::id,
            ByteBufCodecs.VAR_INT, CapsuleContent::blocks,
            ByteBufCodecs.VAR_INT, CapsuleContent::mobs,
            ByteBufCodecs.VAR_INT, CapsuleContent::radius,
            CapsuleContent::new);
}
