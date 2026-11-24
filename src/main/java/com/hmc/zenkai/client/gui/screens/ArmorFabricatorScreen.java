package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.client.gui.menu.ArmorFabricatorMenu;
import com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity;
import com.hmc.zenkai.feature.armor.ArmorFabricationRecipe;
import com.hmc.zenkai.feature.armor.ArmorSets;
import com.hmc.zenkai.feature.armor.AttributeTemplates;
import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.armor.TemplateDuplicationRecipe;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModRecipes;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pantalla de la Fabricadora (Fase 5b de la 0.7.0; desde el 2026-11-23 solo la pestaña Fabricar: Atributos e Infundir
 * pasaron a la estación de imbuido, ImbuingStationScreen). Barra de FE, tanque de Ki (turbo de la fabricación) y la
 * columna de cuatro mejoras a la derecha. Patrón, cuatro botones de pieza (con el icono de la pieza del set), material
 * 3x3, interruptor, progreso y salida; abajo, lectura del estado y del coste.
 * Fondo armor_fabricator_craft.png de tools/gen_armor_fabricator_screen.py; geometría == ese script y ArmorFabricatorMenu.
 * Cifras por ZenkaiNumbers (regla de CLAUDE.md).
 */
public class ArmorFabricatorScreen extends KiMachineScreen<ArmorFabricatorMenu> {

    private static final ResourceLocation BG_CRAFT =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/armor_fabricator_craft.png");

    // ── Layout (== tools/gen_armor_fabricator_screen.py) ─────────────────────
    private static final int PIECE_X = 88, PIECE_Y = 32, PIECE_SIZE = 20, PIECE_STEP = 22;
    /** Públicas: la compat de JEI pone su zona clicable sobre la barra de progreso. */
    public static final int PROG_X = 126, PROG_Y = 81, PROG_W = 32, PROG_H = 6;
    /** Panel oscuro de lectura, abajo. */
    private static final int CRAFT_INFO_X = 128, CRAFT_INFO_Y = 104, INFO_LH = 10;
    private static final int INFO_W = INFO_RIGHT - CRAFT_INFO_X;
    /** Interruptor de la fabricación: franja libre entre los botones de pieza y la barra de progreso. */
    private static final int TOGGLE_X = 124, TOGGLE_Y = 57, TOGGLE_W = 94, TOGGLE_H = 14;

    private static final String[] UPGRADE_KEYS = {
            "screen.zenkai.ki_chamber.slot.tolerance", "screen.zenkai.ki_chamber.slot.speed",
            "screen.zenkai.ki_chamber.slot.storage", "screen.zenkai.armor_fabricator.slot.efficiency",
            "screen.zenkai.armor_fabricator.slot.battery" };
    private static final String[] PIECE_KEYS = {
            "screen.zenkai.armor_fabricator.piece.helmet", "screen.zenkai.armor_fabricator.piece.chestplate",
            "screen.zenkai.armor_fabricator.piece.leggings", "screen.zenkai.armor_fabricator.piece.boots" };

    private final List<TechButton> pieceButtons = new ArrayList<>();
    private TechButton toggleButton;

    public ArmorFabricatorScreen(ArmorFabricatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override protected String tankTipKey() { return "screen.zenkai.armor_fabricator.tank.tip"; }
    @Override protected String noToleranceKey() { return "screen.zenkai.armor_fabricator.tank.no_tolerance"; }

    @Override
    protected ResourceLocation background() { return BG_CRAFT; }

    @Override
    protected void init() {
        super.init();
        pieceButtons.clear();
        for (int i = 0; i < ArmorFabricatorBlockEntity.PIECES.length; i++) {
            final int idx = i;
            TechButton b = new TechButton(leftPos + PIECE_X + i * PIECE_STEP, topPos + PIECE_Y, PIECE_SIZE, PIECE_SIZE,
                    Component.empty(), TechButton.Kind.SECONDARY, () -> press(ArmorFabricatorMenu.BTN_PIECE_FIRST + idx));
            pieceButtons.add(b);
            addRenderableWidget(b);
        }
        toggleButton = new TechButton(leftPos + TOGGLE_X, topPos + TOGGLE_Y, TOGGLE_W, TOGGLE_H,
                Component.empty(), TechButton.Kind.SECONDARY, () -> press(ArmorFabricatorMenu.BTN_TOGGLE_CRAFT));
        addRenderableWidget(toggleButton);
        refresh();
    }

    private void press(int id) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        toggleButton.setMessage(Component.translatable(menu.craftEnabled()
                ? "screen.zenkai.armor_fabricator.toggle.pause" : "screen.zenkai.armor_fabricator.toggle.resume"));
    }

    /** Receta de duplicado de la plantilla puesta en la ranura del patrón (pestaña Fabricar). */
    private Optional<RecipeHolder<TemplateDuplicationRecipe>> duplication() {
        if (minecraft == null || minecraft.level == null) return Optional.empty();
        ItemStack t = menu.machineStack(ArmorFabricatorBlockEntity.SLOT_PATTERN);
        if (AttributeTemplates.kindOf(t) == null) return Optional.empty();
        return minecraft.level.getRecipeManager().getRecipeFor(ModRecipes.TEMPLATE_DUPLICATION.get(),
                new TemplateDuplicationRecipe.Input(t), minecraft.level);
    }

    /** Receta del patrón puesto para esa pieza (las recetas se sincronizan al cliente). */
    private Optional<RecipeHolder<ArmorFabricationRecipe>> recipeFor(int piece) {
        if (minecraft == null || minecraft.level == null) return Optional.empty();
        ResourceLocation set = ArmorSets.setOf(menu.machineStack(ArmorFabricatorBlockEntity.SLOT_PATTERN));
        if (set == null) return Optional.empty();
        return minecraft.level.getRecipeManager().getRecipeFor(ModRecipes.ARMOR_FABRICATION.get(),
                new ArmorFabricationRecipe.Input(set, ArmorFabricatorBlockEntity.PIECES[piece]), minecraft.level);
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawHeader(g, menu.isWorking());
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);
        drawMachineBars(g, menu.isWorking());
        renderCraft(g);
    }

    private void renderCraft(GuiGraphics g) {
        // Iconos de las piezas del set dentro de los botones; la elegida, enmarcada en cian.
        for (int i = 0; i < pieceButtons.size(); i++) {
            int x = PIECE_X + i * PIECE_STEP, y = PIECE_Y;
            Optional<RecipeHolder<ArmorFabricationRecipe>> r = recipeFor(i);
            if (r.isPresent()) g.renderItem(r.get().value().result(), x + 2, y + 2);
            if (i == menu.selectedPiece()) {
                int c = ZenkaiTechPalette.CYAN_HI;
                g.fill(x - 1, y - 1, x + PIECE_SIZE + 1, y, c);
                g.fill(x - 1, y + PIECE_SIZE, x + PIECE_SIZE + 1, y + PIECE_SIZE + 1, c);
                g.fill(x - 1, y, x, y + PIECE_SIZE, c);
                g.fill(x + PIECE_SIZE, y, x + PIECE_SIZE + 1, y + PIECE_SIZE, c);
            }
        }
        drawBar(g, PROG_X, PROG_Y, PROG_W, PROG_H, menu.progressFraction());

        Optional<RecipeHolder<TemplateDuplicationRecipe>> dup = duplication();
        if (AttributeTemplates.kindOf(menu.machineStack(ArmorFabricatorBlockEntity.SLOT_PATTERN)) != null) {
            renderDuplication(g, dup);
            return;
        }

        int y = CRAFT_INFO_Y;
        Optional<RecipeHolder<ArmorFabricationRecipe>> r = recipeFor(menu.selectedPiece());
        Component status;
        int color;
        if (ArmorSets.setOf(menu.machineStack(ArmorFabricatorBlockEntity.SLOT_PATTERN)) == null) {
            status = Component.translatable("screen.zenkai.armor_fabricator.status.no_pattern");
            color = ZenkaiTechPalette.DIM_ON_SCREEN;
        } else if (r.isEmpty()) {
            status = Component.translatable("screen.zenkai.armor_fabricator.status.no_piece");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else if (!menu.craftEnabled()) {
            status = Component.translatable("screen.zenkai.armor_fabricator.status.paused");
            color = ZenkaiTechPalette.DIM_ON_SCREEN;
        } else if (menu.isWorking()) {
            status = Component.translatable(menu.isTurbo() ? "screen.zenkai.armor_fabricator.status.turbo"
                    : "screen.zenkai.armor_fabricator.status.working");
            color = ZenkaiTechPalette.OK_ON_SCREEN;
        } else if (!menu.machineStack(ArmorFabricatorBlockEntity.SLOT_OUTPUT).isEmpty()) {
            status = Component.translatable("screen.zenkai.armor_fabricator.status.output_full");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else if (materialCount(r.get().value().material()) < r.get().value().count()) {
            status = Component.translatable("screen.zenkai.armor_fabricator.status.no_material");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else {
            status = Component.translatable("screen.zenkai.armor_fabricator.status.no_energy");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        }
        PanelText.onDark(g, font, PanelText.fit(font, status, INFO_W), CRAFT_INFO_X, y, color);
        y += INFO_LH + 2;
        if (r.isPresent()) {
            ArmorFabricationRecipe rec = r.get().value();
            int have = materialCount(rec.material());
            // Fase 5c: con material infundido la etiqueta pasa a ser el tipo con que saldrá la pieza, en su color (no
            // cabe una cuarta fila en el panel). Detalle en el tooltip de la fila.
            KiEnergyType inf = have >= rec.count() ? craftInfusion(rec) : null;
            String label = inf == null ? "screen.zenkai.armor_fabricator.material" : inf.langKey();
            infoRow(g, CRAFT_INFO_X, y, label, inf == null ? ZenkaiTechPalette.DIM_ON_SCREEN : inf.argb(),
                    Component.literal(ZenkaiNumbers.screen((long) have) + " / " + ZenkaiNumbers.screen((long) rec.count())),
                    have >= rec.count() ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
            y += INFO_LH;
            infoRow(g, CRAFT_INFO_X, y, "screen.zenkai.armor_fabricator.cost",
                    Component.literal(ZenkaiNumbers.screen((long) rec.energy()) + " FE"), ZenkaiTechPalette.TEXT_ON_SCREEN);
        }
    }

    /** Nivel de infusión con que saldría la pieza (el del material que manda), o null. */
    @Nullable
    private KiEnergyType craftInfusion(ArmorFabricationRecipe rec) {
        return ArmorFabricatorBlockEntity.infusionOf(rec.material(), menu::machineStack);
    }

    /** Pestaña Fabricar con una plantilla en la ranura del patrón: duplicarla. */
    private void renderDuplication(GuiGraphics g, Optional<RecipeHolder<TemplateDuplicationRecipe>> dup) {
        int y = CRAFT_INFO_Y;
        Component status;
        int color;
        if (dup.isEmpty()) {
            status = Component.translatable("screen.zenkai.armor_fabricator.status.no_duplication");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else if (!menu.craftEnabled()) {
            status = Component.translatable("screen.zenkai.armor_fabricator.status.paused");
            color = ZenkaiTechPalette.DIM_ON_SCREEN;
        } else if (menu.isWorking()) {
            status = Component.translatable(menu.isTurbo() ? "screen.zenkai.armor_fabricator.status.turbo"
                    : "screen.zenkai.armor_fabricator.status.duplicating");
            color = ZenkaiTechPalette.OK_ON_SCREEN;
        } else if (materialCount(dup.get().value().material()) < dup.get().value().count()) {
            status = Component.translatable("screen.zenkai.armor_fabricator.status.no_material");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else {
            status = Component.translatable("screen.zenkai.armor_fabricator.status.no_energy_or_output");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        }
        PanelText.onDark(g, font, PanelText.fit(font, status, INFO_W), CRAFT_INFO_X, y, color);
        y += INFO_LH + 2;
        if (dup.isEmpty()) return;
        TemplateDuplicationRecipe d = dup.get().value();
        int have = materialCount(d.material());
        infoRow(g, CRAFT_INFO_X, y, "screen.zenkai.armor_fabricator.material",
                Component.literal(ZenkaiNumbers.screen((long) have) + " / " + ZenkaiNumbers.screen((long) d.count())),
                have >= d.count() ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        y += INFO_LH;
        infoRow(g, CRAFT_INFO_X, y, "screen.zenkai.armor_fabricator.cost",
                Component.literal(ZenkaiNumbers.screen((long) d.energy()) + " FE"), ZenkaiTechPalette.TEXT_ON_SCREEN);
    }

    /** Misma cuenta que el servidor: solo el material igual al de la primera ranura que casa (sin mezcla). */
    private int materialCount(net.minecraft.world.item.crafting.Ingredient material) {
        return ArmorFabricatorBlockEntity.materialCount(material, menu::machineStack);
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        int lmx = localMouseX(mouseX), lmy = localMouseY(mouseY);
        if (machineTooltip(g, mouseX, mouseY)) return;
        if (toggleButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable(menu.craftEnabled() ? "screen.zenkai.armor_fabricator.status.enabled"
                            : "screen.zenkai.armor_fabricator.status.paused").withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.toggle.tip").withStyle(ChatFormatting.GRAY))));
            return;
        }
        if (infusionTooltip(g, mouseX, mouseY, lmx, lmy)) return;
        for (int i = 0; i < pieceButtons.size(); i++) {
            if (pieceButtons.get(i).isMouseOver(mouseX, mouseY)) {
                drawTooltip(g, mouseX, mouseY, List.of(TipLine.of(Component.translatable(PIECE_KEYS[i]))));
                return;
            }
        }
    }

    /** Tooltip de la fila de material de Fabricar: con qué infusión saldría la pieza y qué le da (Fase 5c). */
    private boolean infusionTooltip(GuiGraphics g, int mouseX, int mouseY, int lmx, int lmy) {
        int rowY = CRAFT_INFO_Y + INFO_LH + 2;
        if (lmx < CRAFT_INFO_X || lmx >= INFO_RIGHT || lmy < rowY - 1 || lmy >= rowY + INFO_LH - 1) return false;
        if (AttributeTemplates.kindOf(menu.machineStack(ArmorFabricatorBlockEntity.SLOT_PATTERN)) != null) return false;
        Optional<RecipeHolder<ArmorFabricationRecipe>> r = recipeFor(menu.selectedPiece());
        if (r.isEmpty()) return false;
        ArmorFabricationRecipe rec = r.get().value();
        KiEnergyType inf = materialCount(rec.material()) >= rec.count() ? craftInfusion(rec) : null;
        List<TipLine> lines = new ArrayList<>();
        if (inf == null) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.infusion.none").withStyle(ChatFormatting.BOLD)));
            lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.infusion.none.tip").withStyle(ChatFormatting.GRAY)));
        } else {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.infusion.result",
                    Component.translatable(inf.langKey()).withStyle(st -> st.withColor(inf.argb() & 0xFFFFFF)))
                    .withStyle(ChatFormatting.BOLD)));
            lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.infusion.durability",
                    ZenkaiNumbers.fmt2(KiInfusion.durabilityMult(inf))).withStyle(ChatFormatting.GRAY)));
            lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.infusion.imbue",
                    ZenkaiNumbers.fmt2(KiInfusion.imbueCostMult(inf)),
                    Component.translatable(inf.langKey())).withStyle(ChatFormatting.GRAY)));
        }
        lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.infusion.no_mix").withStyle(ChatFormatting.DARK_GRAY)));
        drawTooltip(g, mouseX, mouseY, lines);
        return true;
    }


    @Override
    protected @Nullable ResourceLocation workGhost(int idx) {
        if (idx == ArmorFabricatorBlockEntity.SLOT_PATTERN) return ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "textures/slot/empty_pattern.png");
        if (idx == ArmorFabricatorBlockEntity.SLOT_OUTPUT) return ResourceLocation.withDefaultNamespace("textures/item/empty_armor_slot_chestplate.png");
        return null;
    }

    @Override
    protected @Nullable String slotKey(int idx) {
        if (idx < 0) return null;
        if (idx == ArmorFabricatorBlockEntity.SLOT_PATTERN) return "screen.zenkai.armor_fabricator.slot.pattern";
        if (idx >= ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST && idx < ArmorFabricatorBlockEntity.SLOT_OUTPUT) {
            return "screen.zenkai.armor_fabricator.slot.material";
        }
        return null;
    }
}
