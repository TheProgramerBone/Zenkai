package com.hmc.zenkai.feature.player.death;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.UUID;

/**
 * Cuerpo que un jugador lleva en brazos (estilo CarryOn). El cuerpo sale del mundo y su estado entero, con el
 * inventario, viaja en {@code nbt}; al soltarlo se vuelve a crear la entidad con él.
 *
 * SINCRONIZACIÓN: el NBT (puede pesar miles de bytes) es SOLO del servidor. Los demás clientes reciben nada más
 * el dueño y su nombre —lo justo para pintar el cuerpo en sus brazos—, de ahí el {@link #SYNC} propio, que
 * descarta el NBT al decodificar.
 */
public record CarriedBody(CompoundTag nbt, UUID owner, String ownerName, String skin, float scale, BodyGear gear) {
    public static final UUID NIL = new UUID(0L, 0L);
    public static final CarriedBody NONE = new CarriedBody(new CompoundTag(), NIL, "", "", 1f, BodyGear.EMPTY);

    public boolean isNone() { return NIL.equals(owner); }

    public static final Codec<CarriedBody> CODEC = CompoundTag.CODEC.xmap(
            t -> t.contains("Body") ? new CarriedBody(t.getCompound("Body"), t.getUUID("OwnerId"), t.getString("OwnerNm"), t.getString("SkinTx"), t.contains("Sc") ? t.getFloat("Sc") : 1f, BodyGear.EMPTY) : NONE,
            c -> {
                CompoundTag t = new CompoundTag();
                if (!c.isNone()) {
                    t.put("Body", c.nbt);
                    t.putUUID("OwnerId", c.owner);
                    t.putString("OwnerNm", c.ownerName);
                    t.putString("SkinTx", c.skin);
                    t.putFloat("Sc", c.scale);
                }
                return t;
            });

    public static final StreamCodec<RegistryFriendlyByteBuf, CarriedBody> SYNC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CarriedBody::owner,
            ByteBufCodecs.STRING_UTF8, CarriedBody::ownerName,
            ByteBufCodecs.STRING_UTF8, CarriedBody::skin,
            ByteBufCodecs.FLOAT, CarriedBody::scale,
            net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(BodyGear::fromList, BodyGear::toList), CarriedBody::gear,
            (o, n, k, s, g) -> new CarriedBody(new CompoundTag(), o, n, k, s, g));
}
