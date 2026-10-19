package com.hmc.zenkai.compat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Compat OPCIONAL con JEI (solo la API en compileOnly; sin JEI esta clase nunca se carga).
 * La gelatina del Janemba se tiñe con el componente BLOCK_STATE (ver JanembaBubbleBlock) y
 * JEI, sin intérprete de subtipos, trataba los 16 colores como el mismo ítem: avisaba de
 * "15 duplicate items" y ocultaba los teñidos.
 */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(
                ModBlocks.HFIL_JANEMBA_BUBBLE.get().asItem(),
                new ISubtypeInterpreter<ItemStack>() {
                    @Nullable
                    @Override
                    public Object getSubtypeData(ItemStack stack, UidContext context) {
                        // Subtipo = estado de bloque del ítem; sin él (rosa) es el subtipo base.
                        var props = stack.get(DataComponents.BLOCK_STATE);
                        return props == null || props.isEmpty() ? null : props;
                    }

                    /**
                     * JEI aún obliga a implementarlo (deprecated desde 19.9.0), pero solo sirve para leer configs
                     * ANTIGUAS que guardaban ids de subtipo como texto. Este plugin es nuevo y no tiene ninguna:
                     * la documentación de JEI dice que se devuelve "" en ese caso. Se evita a propósito
                     * IIngredientSubtypeInterpreter.NONE, que está marcada "forRemoval".
                     */
                    @SuppressWarnings("deprecation")
                    @Override
                    public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                        return "";
                    }
                });
    }
}
