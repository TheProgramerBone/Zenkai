package com.hmc.zenkai.core.network.feature.wishes;

import com.hmc.zenkai.core.config.WishConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record ConfirmVillagerWishPayload(ResourceLocation enchantmentId) implements CustomPacketPayload {
    public static final Type<ConfirmVillagerWishPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "confirm_villager_wish"));

    public static final StreamCodec<ByteBuf, ConfirmVillagerWishPayload> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(ConfirmVillagerWishPayload::new, ConfirmVillagerWishPayload::enchantmentId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final class ConfirmVillagerWishPayloadHandler {
        private ConfirmVillagerWishPayloadHandler() {}

        public static void handle(final ConfirmVillagerWishPayload payload, final IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer player)) return;
                if (!(player.level() instanceof ServerLevel level)) return;

                // Toggle de config (seguridad server-side).
                if (!WishConfig.isEnabled(WishConfig.WishType.ENCHANT_VILLAGER)) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.wish_disabled"), false);
                    return;
                }

                // 1) Resolver Holder<Enchantment> desde el ResourceLocation recibido
                ResourceLocation id = payload.enchantmentId();
                var reg = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                var key = ResourceKey.create(Registries.ENCHANTMENT, id);
                Holder<Enchantment> holder = reg.get(key).map(h -> (Holder<Enchantment>) h).orElse(null);

                if (holder == null) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.enchant_not_found", id.toString()), false);
                    return;
                }

                // 2) Nivel máximo y precio (desde WishConfig)
                int levelChosen = Math.max(1, holder.value().getMaxLevel());
                int base = Math.max(1, WishConfig.villagerBookBasePrice());
                int perLevel = Math.max(0, WishConfig.villagerBookPricePerLevel());
                int price = Math.min(64, Math.max(1, base + perLevel * (levelChosen - 1)));

                // 3) Libro encantado
                var book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, levelChosen));

                // 4) Oferta: (esmeraldas x price) + (libro) -> (libro encantado)
                //    FIX: antes se pasaba .getItem() sin count => siempre costaba 1 esmeralda.
                MerchantOffer offer = new MerchantOffer(
                        new ItemCost(Items.EMERALD, price),
                        Optional.of(new ItemCost(Items.BOOK, 1)),
                        book.copy(),
                        999_999, // maxUses
                        0,       // XP para el aldeano
                        0.05F    // priceMult
                );

                // 5) Spawnear aldeano bibliotecario y fijar oferta
                Villager villager = EntityType.VILLAGER.create(level);
                if (villager == null) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.villager_spawn_failed"), false);
                    return;
                }

                villager.moveTo(player.getX() + 1.0, player.getY(), player.getZ() + 1.0, player.getYRot(), 0);
                villager.setPersistenceRequired();
                villager.setVillagerData(new VillagerData(
                        villager.getVillagerData().getType(), VillagerProfession.LIBRARIAN, 1));
                // XP > 0: marca el oficio como "asentado" para que el brain NO lo devuelva a desempleado
                // (sin esto, un aldeano con XP 0 y sin atril pierde la profesión y los trades al primer tick).
                villager.setVillagerXp(1);
                villager.setCustomName(Component.translatable("entity.zenkai.wish_librarian"));
                villager.setCustomNameVisible(true);

                MerchantOffers offers = new MerchantOffers();
                offers.add(offer);
                villager.setOffers(offers);

                level.addFreshEntity(villager);

                player.displayClientMessage(Component.translatable("messages.zenkai.wish_villager_ready"), false);

                // Cierre común del deseo (sonido, mensaje, quitar Shenlong).
                WishFinalizer.finalizeWish(player);
            });
        }
    }
}