package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Deseo de ser LEGENDARIO (todas las razas). Marca {@code isLegendary}, que revela y habilita la línea del Super
 * Saiyan Legendario (ssj_green / ssj_green_max) o la forma Legendaria de la raza (ver SuperForms.unlocked). Irreversible, como la inmortalidad. La
 * validación es TODA del servidor: la pantalla solo esconde la opción a quien no puede pedirla.
 */
public record WishLegendaryPayload() implements CustomPacketPayload {
    public static final Type<WishLegendaryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "wish_legendary"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WishLegendaryPayload> STREAM_CODEC =
            StreamCodec.unit(new WishLegendaryPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final class Handler {
        public static void handle(WishLegendaryPayload payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) ctx.player();
                if (!WishSessions.validate(player)) return;
                if (!ServerConfig.isEnabled(ServerConfig.WishType.LEGENDARY)) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.wish_disabled"), false);
                    return;
                }
                PlayerStatsAttachment att = PlayerStatsAttachment.get(player);
                // Cualquier raza con una forma Legendaria puede pedirlo (el Saiyan tiene la cadena de la película).
                if (att.getRace() != Race.SAIYAN
                        && com.hmc.zenkai.feature.forms.LegendaryForms.formOf(att.getRace()) == null) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.wish.legendary_saiyan_only"), false);
                    return;
                }
                if (att.isLegendary()) return; // ya lo es: no se gasta el deseo
                // Exclusión mutua con la marca Majin (config): no se concede mientras la lleve.
                if (ServerConfig.majinLegendaryExclusive()
                        && com.hmc.zenkai.content.effect.MajinEffect.isActive(player)) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.wish.legendary_majin_exclusive"), false);
                    return;
                }
                att.setLegendary(true);
                PlayerLifeCycle.sync(player);
                player.displayClientMessage(Component.translatable("messages.zenkai.legendary"), false);

                WishFinalizer.finalizeWish(player, Component.translatable("messages.zenkai.wish_desc.legendary"));
                ZenkaiTriggers.WISH_GRANTED.get().trigger(player, "legendary");
            });
        }
    }
}
