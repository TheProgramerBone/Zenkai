package com.hmc.db_renewed.network.wishes;


import com.hmc.db_renewed.config.WishConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Handler para el deseo: “Aparecer un aldeano con el encantamiento elegido como primer trade”.
 * Espera un payload con el id del encantamiento.
 *
 * Adapta el tipo del payload a tu implementación real (nombre de clase, getters, etc.).
 */
public final class VillagerBookWishPayloadHandler {

    private VillagerBookWishPayloadHandler() {}

    public static void handle(final VillagerBookWishPayload payload, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            // 1) Resolver Holder<Enchantment> desde el id del payload
            ResourceLocation enchId = payload.enchantmentId();
            var enchRegistry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            ResourceKey<Enchantment> enchKey = ResourceKey.create(Registries.ENCHANTMENT, enchId);
            var holderOpt = enchRegistry.getHolder(enchKey);

            if (holderOpt.isEmpty()) {
                player.displayClientMessage(Component.translatable("messages.db_renewed.invalid_enchantment"), false);
                return;
            }
            Holder<Enchantment> enchHolder = holderOpt.get();

            // 2) Nivel máximo y precio
            int maxLvl = Math.max(1, enchHolder.value().getMaxLevel());
            int base = safeBasePrice();
            int perLevel = safePerLevel();
            int price = Math.max(1, base + perLevel * (maxLvl - 1));

            // 3) Libro encantado (API nueva con Holder)
            var resultBook = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchHolder, maxLvl));

            // 4) Trade con ItemCost (sin ItemCost.of)
            ItemCost costA = new ItemCost(Items.EMERALD, price);
            Optional<ItemCost> costB = Optional.of(new ItemCost(Items.BOOK, 1));

            MerchantOffer offer = new MerchantOffer(
                    costA,                 // costo principal (esmeraldas)
                    costB,                 // costo secundario opcional (libro)
                    resultBook.copy(),     // resultado
                    999_999,               // usos máximos
                    0,                     // XP al aldeano
                    0.05F                  // multiplicador de precio
            );

            // 5) Spawnear aldeano bibliotecario frente al jugador
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) {
                player.displayClientMessage(Component.translatable("messages.db_renewed.villager_spawn_failed"), false);
                return;
            }

            BlockPos spawnAt = findSafeSpotNearPlayer(player);
            villager.moveTo(spawnAt.getX() + 0.5, spawnAt.getY(), spawnAt.getZ() + 0.5, player.getYRot(), 0);

            villager.setVillagerData(
                    villager.getVillagerData()
                            .setProfession(VillagerProfession.LIBRARIAN)
                            .setLevel(1)
            );
            villager.setPersistenceRequired();

            villager.getOffers().clear();
            villager.getOffers().add(offer);

            level.addFreshEntity(villager);

            String enchName = enchRegistry.getKey(enchHolder.value()) != null
                    ? enchRegistry.getKey(enchHolder.value()).toString()
                    : "unknown";
            player.displayClientMessage(
                    Component.translatable("messages.db_renewed.villager_ready", enchName, maxLvl, price),
                    false
            );
        });
    }

    // ——— Helpers ———

    private static int safeBasePrice() {
        try {
            return Math.max(1, WishConfig.villagerBookBasePrice());
        } catch (Throwable t) {
            return 16; // fallback si no tienes WishConfig
        }
    }

    private static int safePerLevel() {
        try {
            return Math.max(0, WishConfig.villagerBookPricePerLevel());
        } catch (Throwable t) {
            return 4;  // fallback
        }
    }

    /** Busca un spot sencillo delante del jugador con 2 bloques de aire. */
    private static BlockPos findSafeSpotNearPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos base = player.blockPosition();
        Direction facing = player.getDirection();

        for (int i = 1; i <= 3; i++) {
            BlockPos ahead = base.relative(facing, i);
            BlockPos feet = ahead;
            BlockPos head = ahead.above();

            boolean feetFree = level.isEmptyBlock(feet);
            boolean headFree = level.isEmptyBlock(head);
            boolean floorSolid = level.getBlockState(ahead.below()).isSolidRender(level, ahead.below());

            if (feetFree && headFree && floorSolid) {
                return feet;
            }
        }
        // Fallback: a un lado del jugador
        return base.relative(facing);
    }

    // ——— Payload de ejemplo (usa el tuyo si ya lo tienes) ———
    public record VillagerBookWishPayload(ResourceLocation enchantmentId) {}
}
