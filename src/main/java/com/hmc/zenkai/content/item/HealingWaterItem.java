package com.hmc.zenkai.content.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Agua curativa de Namek. Se bebe, no se come.
 *
 * UseAnim.DRINK es lo que hace que LivingEntity elija getDrinkingSound() en vez de
 * getEatingSound(), y también cambia la animación de la mano.
 *
 * La botella vacía se devuelve en finishUsingItem: craftRemainder solo actúa cuando el item
 * es ingrediente de una receta, nunca al consumirlo.
 */
public class HealingWaterItem extends Item {

    public HealingWaterItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public @NotNull SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    /** 40 ticks, como la botella de miel: se nota que estás bebiendo algo. */
    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 40;
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level,
                                              @NotNull LivingEntity entity) {
        super.finishUsingItem(stack, level, entity);

        if (entity instanceof ServerPlayer sp) {
            CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
            sp.awardStat(Stats.ITEM_USED.get(this));
        }

        if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);

        if (entity instanceof Player p && !p.hasInfiniteMaterials()) {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (!p.getInventory().add(bottle)) p.drop(bottle, false);
        }
        return stack;
    }
}