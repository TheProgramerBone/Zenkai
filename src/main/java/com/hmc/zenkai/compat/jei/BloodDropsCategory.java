package com.hmc.zenkai.compat.jei;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.content.item.BloodItem;
import com.hmc.zenkai.registry.ModDataMaps;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.util.ZenkaiNumbers;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * JEI: qué mob suelta qué sangre (data map {@code zenkai:blood_sources}, sincronizado al cliente para esto). A la
 * izquierda el huevo del mob si tiene (así «usos» del huevo lleva aquí); a la derecha sus sangres (un mob puede soltar
 * varias, cada una con su probabilidad: va en el tooltip de cada hueco); en medio el nombre del mob, la probabilidad si
 * es la misma para todas y que solo cuenta si lo mata un jugador (BloodDrops).
 */
public class BloodDropsCategory extends AbstractRecipeCategory<BloodDropsCategory.Drop> {

    public static final RecipeType<Drop> TYPE = RecipeType.create(com.hmc.zenkai.Zenkai.MOD_ID, "blood_drops", Drop.class);

    public record Drop(EntityType<?> entity, List<ModDataMaps.BloodSource> sources) {
        public int maxQuality() { return sources.stream().mapToInt(ModDataMaps.BloodSource::quality).max().orElse(0); }
    }

    private static final int W = 184, H = 34, TEXT_X = 22, SLOT_STEP = 20, MAX_SLOTS = 3;

    public BloodDropsCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("jei.zenkai.blood_drops"),
                gui.createDrawableItemLike(ModItems.STRENGTH_BLOOD.get()), W, H);
    }

    private static int shown(Drop d) { return Math.min(MAX_SLOTS, d.sources().size()); }

    private static String pct(float chance) {
        // Redondeo a una décima antes de formatear: 0.1f * 100 da 10.0000001 y salía «10.0%».
        return ZenkaiNumbers.screen(Math.round(chance * 1000.0) / 10.0) + "%";
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder b, @NotNull Drop d, @NotNull IFocusGroup focuses) {
        SpawnEggItem egg = SpawnEggItem.byId(d.entity());
        if (egg != null) b.addSlot(RecipeIngredientRole.INPUT, 1, 9).setStandardSlotBackground().addItemStack(new ItemStack(egg));
        int n = shown(d);
        for (int i = 0; i < n; i++) {
            ModDataMaps.BloodSource src = d.sources().get(i);
            b.addSlot(RecipeIngredientRole.OUTPUT, W - 19 - (n - 1 - i) * SLOT_STEP, 9).setOutputSlotBackground()
                    .addItemStack(BloodItem.of(src.blood(), src.quality(), 1))
                    .addRichTooltipCallback((view, tip) -> tip.add(Component.translatable("jei.zenkai.blood_drops.chance", pct(src.chance()))
                            .withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }

    @Override
    public void draw(@NotNull Drop d, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        int maxW = W - TEXT_X - shown(d) * SLOT_STEP - 4;
        g.drawString(font, PanelText.fit(font, d.entity().getDescription(), maxW), TEXT_X, 2, 0xFF404040, false);
        boolean same = d.sources().stream().allMatch(s -> s.chance() == d.sources().get(0).chance());
        Component chance = !same ? Component.translatable("jei.zenkai.blood_drops.chance_varies")
                : Component.translatable(d.sources().size() > 1 ? "jei.zenkai.blood_drops.chance_each" : "jei.zenkai.blood_drops.chance",
                        pct(d.sources().get(0).chance()));
        g.drawString(font, PanelText.fit(font, chance, maxW), TEXT_X, 13, 0xFF404040, false);
        g.drawString(font, PanelText.fit(font, Component.translatable("jei.zenkai.blood_drops.killed_by_player"), maxW),
                TEXT_X, 24, 0xFF808080, false);
    }
}
