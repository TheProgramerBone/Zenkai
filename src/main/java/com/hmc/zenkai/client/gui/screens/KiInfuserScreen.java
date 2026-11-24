package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.menu.KiInfuserMenu;
import com.hmc.zenkai.content.blockentity.KiInfuserBlockEntity;
import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.armor.KiInfusionRecipe;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Pantalla de la infusora de Ki (Fase 5c de la 0.7.0; sobre la base KiMachineScreen desde el 2026-11-23). Hermana de la máquina de fabricación: barra de FE, tanque de Ki y
 * columna de cuatro mejoras; en el centro entrada → barra de progreso → salida, y abajo el panel de lectura con el estado,
 * el tipo, la cantidad y el coste de la receta zenkai:ki_infusion que casa. Fondo de tools/gen_ki_infuser_screen.py (geometría == ese script y
 * KiInfuserMenu). El estado se deduce aquí con las MISMAS reglas que KiInfuserBlockEntity.state, a partir de lo que el
 * menú sincroniza. Cifras por ZenkaiNumbers (regla de CLAUDE.md).
 */
public class KiInfuserScreen extends KiMachineScreen<KiInfuserMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/ki_infuser.png");

    // ── Layout (== tools/gen_ki_infuser_screen.py) ───────────────────────────
    public static final int PROG_X = 116, PROG_Y = 57, PROG_W = 32, PROG_H = 6;
    private static final int INFO_X = 68, INFO_Y = 88, INFO_LH = 10;
    private static final int INFO_W = INFO_RIGHT - INFO_X;

    private static final String[] UPGRADE_KEYS = {
            "screen.zenkai.ki_chamber.slot.tolerance", "screen.zenkai.ki_chamber.slot.speed",
            "screen.zenkai.ki_chamber.slot.storage", "screen.zenkai.armor_fabricator.slot.efficiency",
            "screen.zenkai.armor_fabricator.slot.battery" };

    public KiInfuserScreen(KiInfuserMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override protected String tankTipKey() { return "screen.zenkai.ki_infuser.tank.tip"; }
    @Override protected String noToleranceKey() { return "screen.zenkai.ki_infuser.status.no_tolerance"; }

    @Override
    protected ResourceLocation background() { return BG; }

    /** Receta para lo que hay en la entrada y en el tanque (copia del cliente del recipe manager). */
    private @Nullable KiInfusionRecipe recipe() {
        if (minecraft == null || minecraft.level == null) return null;
        return KiInfusionRecipe.find(minecraft.level.getRecipeManager(), menu.machineStack(KiInfuserBlockEntity.SLOT_INPUT),
                menu.tankType(), minecraft.level).map(RecipeHolder::value).orElse(null);
    }

    /** Las MISMAS reglas que KiInfuserBlockEntity.state (función compartida), con lo que sincroniza el menú. */
    private KiInfuserBlockEntity.State state(@Nullable KiInfusionRecipe r) {
        if (minecraft == null || minecraft.level == null) return KiInfuserBlockEntity.State.NO_INPUT;
        ItemStack out = menu.machineStack(KiInfuserBlockEntity.SLOT_OUTPUT);
        return KiInfuserBlockEntity.evaluate(minecraft.level.getRecipeManager(), menu.machineStack(KiInfuserBlockEntity.SLOT_INPUT),
                menu.tankType(), menu.tankAmount(), r, menu::tolerates,
                res -> out.isEmpty() || ItemStack.isSameItemSameComponents(out, res)
                        && out.getCount() + res.getCount() <= out.getMaxStackSize(),
                menu.energy(), menu.machineStack(KiInfuserBlockEntity.SLOT_EFFICIENCY).getCount());
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawHeader(g, menu.isWorking());
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);
        drawMachineBars(g, menu.isWorking());
        KiEnergyType inTank = menu.tankType();
        drawBar(g, PROG_X, PROG_Y, PROG_W, PROG_H, menu.progressFraction());

        KiInfusionRecipe r = recipe();
        KiInfuserBlockEntity.State s = menu.isWorking() ? KiInfuserBlockEntity.State.READY : state(r);
        Component status = Component.translatable(menu.isWorking() ? "screen.zenkai.ki_infuser.status.working"
                : "screen.zenkai.ki_infuser.status." + s.name().toLowerCase(java.util.Locale.ROOT));
        int color = menu.isWorking() ? ZenkaiTechPalette.OK_ON_SCREEN
                : s == KiInfuserBlockEntity.State.NO_INPUT ? ZenkaiTechPalette.DIM_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN;
        int y = INFO_Y;
        PanelText.onDark(g, font, PanelText.fit(font, status, INFO_W), INFO_X, y, color);
        y += INFO_LH + 2;

        // Tipo y cantidad: el del tanque, en su color en la ETIQUETA (el rojo Divino en la cifra parecía «falta»); la
        // cantidad, la de la receta (sin receta, «—»).
        Component amount = Component.literal(r == null ? "—" : ZenkaiNumbers.screen(menu.fluidCost(r.fluidAmount())) + " mB");
        boolean fluidOk = r != null && menu.tankAmount() >= menu.fluidCost(r.fluidAmount()) && menu.tolerates(r.ki());
        if (inTank == null) {
            infoRow(g, INFO_X, y, Component.translatable("screen.zenkai.ki_infuser.ki_any"), ZenkaiTechPalette.DIM_ON_SCREEN,
                    amount, ZenkaiTechPalette.DENIED_ON_SCREEN);
        } else {
            infoRow(g, INFO_X, y, Component.translatable(inTank.langKey()), inTank.argb(), amount,
                    fluidOk ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        }
        y += INFO_LH;
        infoRow(g, INFO_X, y, Component.translatable("screen.zenkai.armor_fabricator.cost"), ZenkaiTechPalette.DIM_ON_SCREEN,
                Component.literal(r == null ? "—" : ZenkaiNumbers.screen((long) r.energy()) + " FE"),
                r != null && menu.energy() >= r.energyPerTick() ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        y += INFO_LH;
        // Qué da a la armadura: solo si lo que sale es katchin infundido (una receta de addon puede sacar otra cosa).
        KiEnergyType outLevel = r == null ? null : KiInfusion.materialLevel(r.output());
        if (outLevel != null) {
            Component gives = Component.translatable("screen.zenkai.ki_infuser.gives",
                    ZenkaiNumbers.fmt2(KiInfusion.durabilityMult(outLevel)), ZenkaiNumbers.fmt2(KiInfusion.imbueCostMult(outLevel)));
            PanelText.onDark(g, font, PanelText.fit(font, gives, INFO_W), INFO_X, y, ZenkaiTechPalette.DIM_ON_SCREEN);
        }
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        machineTooltip(g, mouseX, mouseY);
    }


    @Override
    protected @Nullable ResourceLocation workGhost(int idx) {
        if (idx == KiInfuserBlockEntity.SLOT_INPUT || idx == KiInfuserBlockEntity.SLOT_OUTPUT) return ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "textures/slot/empty_ingot.png");
        return null;
    }

    @Override
    protected @Nullable String slotKey(int idx) {
        if (idx < 0) return null;
        if (idx == KiInfuserBlockEntity.SLOT_INPUT) return "screen.zenkai.ki_infuser.slot.input";
        if (idx == KiInfuserBlockEntity.SLOT_OUTPUT) return "screen.zenkai.ki_infuser.slot.output";
        return null;
    }
}
