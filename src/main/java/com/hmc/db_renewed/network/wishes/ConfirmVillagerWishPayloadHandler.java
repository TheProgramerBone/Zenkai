package com.hmc.db_renewed.network.wishes;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public final class ConfirmVillagerWishPayloadHandler {
    private ConfirmVillagerWishPayloadHandler() {}

    public static void handle(final ConfirmVillagerWishPayload payload, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            // 1) Resolver Holder<Enchantment> desde el ResourceLocation recibido
            ResourceLocation id = payload.enchantmentId();
            var reg = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var key = ResourceKey.create(Registries.ENCHANTMENT, id);
            Holder<Enchantment> holder = reg.get(key).orElse(null);

            if (holder == null) {
                player.displayClientMessage(Component.translatable("messages.db_renewed.enchant_not_found", id.toString()), false);
                return;
            }

            // 2) Calcular nivel y costo
            int maxLevel = holder.value().getMaxLevel(); // típico 1–5
            int levelChosen = maxLevel;                  // según tu requisito: siempre el nivel máximo
            int price = Math.min(64, 8 + levelChosen * 12); // coste simple; ajusta a gusto

            // 3) Crear libro encantado
            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, levelChosen));

            // 4) Crear oferta: (esmeraldas) + (libro normal) -> (libro encantado)
            MerchantOffer offer = new MerchantOffer(
                    new ItemCost(new ItemStack(Items.EMERALD, price).getItem()),
                    Optional.of(new ItemCost(new ItemStack(Items.BOOK, 1).getItem())),
                    book.copy(),
                    999_999, // maxUses (prácticamente infinito)
                    0,       // XP para el aldeano por trade
                    0.05F    // priceMult
            );

            // 5) Spawnear aldeano bibliotecario y fijar oferta
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) {
                player.displayClientMessage(Component.literal("No se pudo crear el aldeano."), false);
                return;
            }

            villager.moveTo(player.getX() + 1.0, player.getY(), player.getZ() + 1.0, player.getYRot(), 0);
            villager.setPersistenceRequired();
            villager.setVillagerData(new VillagerData(villager.getVillagerData().getType(), VillagerProfession.LIBRARIAN, 1));
            villager.setCustomName(Component.translatable("entity.db_renewed.wish_librarian"));
            villager.setCustomNameVisible(true);

            MerchantOffers offers = new MerchantOffers();
            offers.add(offer);
            villager.setOffers(offers);

            level.addFreshEntity(villager);

            player.displayClientMessage(Component.translatable("messages.db_renewed.wish_villager_ready"), false);
        });
    }
}