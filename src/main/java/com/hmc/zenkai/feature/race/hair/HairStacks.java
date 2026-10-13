package com.hmc.zenkai.feature.race.hair;

import com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes;
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

    /**
     * Peinado por mechones (importado o del catálogo por código): el componente lleva directamente
     * el id del modelo construido al vuelo. Su color no es un tinte multiplicado sino una rampa de
     * tonos (GeoLayerArmorRenderer + HairRampTextures).
     */
    public static boolean isStrandHair(ItemStack stack) {
        ResourceLocation id = stack.get(ModDataComponents.HAIR_STYLE.get());
        return id != null && CustomHairCodes.MODEL_NAMESPACE.equals(id.getNamespace());
    }

    @Nullable
    public static ResourceLocation model(ItemStack stack) {
        if (isStrandHair(stack)) return stack.get(ModDataComponents.HAIR_STYLE.get());
        HairStyleDef d = def(stack);
        return d == null ? null : d.model();
    }

    @Nullable
    public static ResourceLocation texture(ItemStack stack) {
        if (isStrandHair(stack)) return FLAT_WHITE;
        HairStyleDef d = def(stack);
        return d == null ? null : d.texture();
    }

    /** Textura blanca de los peinados por mechones: el color sale del tinte. */
    public static final ResourceLocation FLAT_WHITE =
            ResourceLocation.fromNamespaceAndPath("zenkai", "textures/customization/hair/flat_white.png");
}
