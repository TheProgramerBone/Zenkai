package com.hmc.zenkai.compat.jei;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Puente de desarrollo para las capturas automáticas (ScreenShots modo «fabricator»): abre una categoría de JEI.
 * Solo se toca si JEI está cargado (quien llama lo comprueba con ModList); el runtime lo deja JeiCompat.
 */
public final class JeiDev {
    private JeiDev() {}

    @Nullable public static IJeiRuntime runtime;

    /** 0 = fabricación, 1 = imbuir, 2 = sangre, 3 = infusión de Ki, 4 = triturador. Devuelve false si JEI aún no está listo. */
    public static boolean show(int category) {
        if (runtime == null) return false;
        RecipeType<?> type = switch (category) {
            case 0 -> ArmorFabricationCategory.TYPE;
            case 1 -> ArmorImbuingCategory.TYPE;
            case 3 -> KiInfusionCategory.TYPE;
            case 4 -> CrushingCategory.TYPE;
            default -> BloodDropsCategory.TYPE;
        };
        runtime.getRecipesGui().showTypes(List.of(type));
        return true;
    }
}
