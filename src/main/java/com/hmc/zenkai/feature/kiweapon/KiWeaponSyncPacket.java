package com.hmc.zenkai.feature.kiweapon;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C: qué arma de ki tiene materializada un jugador ("" = ninguna) y qué escudo de ki en la mano
 * secundaria ("" = ninguno). El arma no es un ítem, así
 * que el render de los demás jugadores (y la guardia predicha en cliente) depende de este aviso.
 * Se manda al cambiar y cuando un jugador empieza a seguir a otro.
 * Los ids de variante miden 8 y 9 caracteres; 32 sobra y un id absurdo de un datapack no puede
 * matar la conexión porque la variante sale de las constantes de SkillEffects, no de datos.
 */
public record KiWeaponSyncPacket(int entityId, String variant, String offhand) implements CustomPacketPayload {

    public static final Type<KiWeaponSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_weapon_sync"));

    public static final StreamCodec<FriendlyByteBuf, KiWeaponSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.entityId());
                        buf.writeUtf(pkt.variant(), 32);
                        buf.writeUtf(pkt.offhand(), 32);
                    },
                    buf -> new KiWeaponSyncPacket(buf.readVarInt(), buf.readUtf(32), buf.readUtf(32)));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KiWeaponSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            long now = level == null ? 0L : level.getGameTime();
            KiWeaponState.clientSet(pkt.entityId(), pkt.variant(), pkt.offhand(), now);
        });
    }
}
