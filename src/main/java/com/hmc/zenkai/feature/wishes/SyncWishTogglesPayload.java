package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.config.ServerConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Cliente: estado de los toggles de deseos (WishConfig.SPEC es SERVER,
 * así que el cliente no los puede leer solo). Se envía justo antes de abrir la
 * pantalla para que ShenlongWishScreen oculte los deseos desactivados.
 */
public record SyncWishTogglesPayload(
        boolean stack,
        boolean revivePlayer,
        boolean enchantVillager,
        boolean immortal,
        boolean trainingPoints
) implements CustomPacketPayload {

    public static final Type<SyncWishTogglesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "sync_wish_toggles"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncWishTogglesPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.stack);
                        buf.writeBoolean(p.revivePlayer);
                        buf.writeBoolean(p.enchantVillager);
                        buf.writeBoolean(p.immortal);
                        buf.writeBoolean(p.trainingPoints);
                    },
                    buf -> new SyncWishTogglesPayload(
                            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                            buf.readBoolean(), buf.readBoolean()
                    )
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Lee la config server-side y construye el payload. */
    public static SyncWishTogglesPayload current() {
        return new SyncWishTogglesPayload(
                ServerConfig.isEnabled(ServerConfig.WishType.STACK),
                ServerConfig.isEnabled(ServerConfig.WishType.REVIVE_PLAYER),
                ServerConfig.isEnabled(ServerConfig.WishType.ENCHANT_VILLAGER),
                ServerConfig.isEnabled(ServerConfig.WishType.IMMORTAL),
                ServerConfig.isEnabled(ServerConfig.WishType.TRAINING_POINTS)
        );
    }

    public boolean isEnabled(ServerConfig.WishType t) {
        return switch (t) {
            case STACK            -> stack;
            case REVIVE_PLAYER    -> revivePlayer;
            case ENCHANT_VILLAGER -> enchantVillager;
            case IMMORTAL         -> immortal;
            case TRAINING_POINTS  -> trainingPoints;
        };
    }
}