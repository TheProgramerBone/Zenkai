package com.hmc.zenkai.feature.race.hair;

import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * El stack virtual del item de pelo genérico (zenkai:hair) y la lectura de su peinado. Funciones
 * que el item usa como stackModel/stackTexture: devolver null = usar el modelo/textura de respaldo
 * del item.
 */
public final class HairStacks {
    private HairStacks() {}

    /** Stack virtual (solo para render) con el peinado dado. */
    public static ItemStack of(ResourceLocation styleId) {
        ItemStack stack = ModItems.HAIR.get().getDefaultInstance();
        stack.set(ModDataComponents.HAIR_STYLE.get(), styleId);
        return stack;
    }

    @Nullable
    private static HairStyleDef def(ItemStack stack) {
        return HairCatalog.get(stack.get(ModDataComponents.HAIR_STYLE.get()));
    }

    @Nullable
    public static ResourceLocation model(ItemStack stack) {
        HairStyleDef d = def(stack);
        return d == null ? null : d.model();
    }

    @Nullable
    public static ResourceLocation texture(ItemStack stack) {
        HairStyleDef d = def(stack);
        return d == null ? null : d.texture();
    }
}
