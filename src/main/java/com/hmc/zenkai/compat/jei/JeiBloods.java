package com.hmc.zenkai.compat.jei;

import com.hmc.zenkai.content.item.BloodItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Las sangres en sus tres calidades (normal, concentrada, pura), en el orden del registro. */
final class JeiBloods {
    private JeiBloods() {}

    static final int[] QUALITIES = {1, 2, 5};

    static List<ItemStack> all() {
        List<ItemStack> out = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BloodItem)) continue;
            for (int q : QUALITIES) out.add(BloodItem.of(item, q, 1));
        }
        return out;
    }
}
