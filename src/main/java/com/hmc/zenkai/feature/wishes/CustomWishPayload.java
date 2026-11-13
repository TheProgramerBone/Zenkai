package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.api.wish.CustomWish;
import com.hmc.zenkai.api.wish.DragonKind;
import com.hmc.zenkai.api.wish.WishRegistry;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * C2S: pedir un deseo de addon por su id. Validación 100 % servidor: sesión de deseo abierta
 * ({@link WishSessions#validate}), deseo registrado, dragón adecuado y {@code availableTo}; solo entonces se llama a
 * {@code grant}. Si lo concede, {@link WishFinalizer} cierra la sesión y descuenta un deseo del pool.
 */
public record CustomWishPayload(ResourceLocation id) implements CustomPacketPayload {

    public static final Type<CustomWishPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "custom_wish"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CustomWishPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, CustomWishPayload::id, CustomWishPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CustomWishPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!WishSessions.validate(sp)) return;
            CustomWish wish = WishRegistry.get(pkt.id());
            var dragon = WishSessions.dragonOf(sp);
            if (wish == null || dragon == null) return;
            DragonKind kind = dragon.getDragonKind();
            boolean ok;
            try {
                ok = wish.dragons().contains(kind) && wish.availableTo(sp, kind);
            } catch (Exception ex) {
                Zenkai.LOGGER.warn("[Zenkai] El deseo {} falló en availableTo", wish.id(), ex);
                ok = false;
            }
            if (!ok) {
                sp.displayClientMessage(Component.translatable("messages.zenkai.custom_wish_unavailable"), false);
                return;
            }
            Optional<Component> desc;
            try {
                desc = wish.grant(sp, kind);
            } catch (Exception ex) {
                Zenkai.LOGGER.warn("[Zenkai] El deseo {} falló en grant: no se concede", wish.id(), ex);
                sp.displayClientMessage(Component.translatable("messages.zenkai.custom_wish_unavailable"), false);
                return;
            }
            if (desc == null || desc.isEmpty()) return;   // rechazado por el addon: no se gasta nada
            WishFinalizer.finalizeWish(sp, desc.get());
            ZenkaiTriggers.WISH_GRANTED.get().trigger(sp, wish.id().toString());
        });
    }
}
