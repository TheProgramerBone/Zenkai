package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.client.gui.menu.ArmorFabricatorMenu;
import com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.armor.ArmorFabricationRecipe;
import com.hmc.zenkai.feature.armor.ArmorSets;
import com.hmc.zenkai.feature.armor.AttributeTemplates;
import com.hmc.zenkai.feature.armor.ImbuedAttributes;
import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.armor.TemplateDuplicationRecipe;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModDataComponents;
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
 * Pantalla de la máquina de fabricación (Fase 5b de la 0.7.0). Común a las dos pestañas: barra de FE, tanque de Ki
 * (turbo de la fabricación y coste de imbuir) y la columna de seis mejoras a la derecha. Pestañas en la cabecera.
 *   FABRICAR: patrón, cuatro botones de pieza (con el icono de la pieza del set), material 3x3, progreso y salida;
 *             abajo, lectura del estado y del coste.
 *   ATRIBUTOS: pieza y sangre, botón Imbuir y a la derecha la lectura: lo imbuido de la pieza y el coste de esta sangre.
 * Cada pestaña tiene su PNG (armor_fabricator_craft.png / _attributes.png de tools/gen_armor_fabricator_screen.py);
 * geometría == ese script y ArmorFabricatorMenu. Lo de la máquina llega por ContainerData; el coste de imbuir se
 * calcula aquí con la MISMA función que el servidor (ArmorFabricatorBlockEntity.preview) y la config sincronizada.
 * Cifras por ZenkaiNumbers (regla de CLAUDE.md).
 *
 * El panel oscuro de Atributos solo tiene {@link #INFO_W} px de texto: cada línea se parte (font.split) o se recorta, y
 * en las filas «etiqueta … cifra» la etiqueta cede ante la cifra. Antes «Imbued in the piece» y «Insert a piece and a
 * blood» se salían del panel (captura del usuario 2026-11-22).
 */
public class ArmorFabricatorScreen extends TechPanelScreen<ArmorFabricatorMenu> {

    private static final ResourceLocation BG_CRAFT =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/armor_fabricator_craft.png");
    private static final ResourceLocation BG_ATTR =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/armor_fabricator_attributes.png");

    // ── Layout (== tools/gen_armor_fabricator_screen.py) ─────────────────────
    private static final int TITLE_X = 12, TITLE_Y = 7, LED_X = 126, LED_Y = 2;
    private static final int TAB_Y = 3, TAB_H = 14, TAB_W = 46, TAB1_X = 150, TAB2_X = 198;
    private static final int BAR_X = 18, BAR_Y = 32, BAR_W = 16, BAR_H = 104, TANK_X = 42;
    private static final int PIECE_X = 88, PIECE_Y = 32, PIECE_SIZE = 20, PIECE_STEP = 22;
    /** Públicas: la compat de JEI pone su zona clicable sobre la barra de progreso. */
    public static final int PROG_X = 126, PROG_Y = 81, PROG_W = 32, PROG_H = 6;
    /** Panel oscuro de lectura: abajo en Fabricar, a la derecha entera en Atributos. */
    private static final int CRAFT_INFO_X = 128, CRAFT_INFO_Y = 104, INFO_RIGHT = 214;
    private static final int ATTR_INFO_X = 128, ATTR_INFO_Y = 36, INFO_LH = 10;
    private static final int INFO_W = INFO_RIGHT - ATTR_INFO_X;
    /** Primera fila del bloque de coste (Atributos): deja sitio a cinco atributos imbuidos encima. */
    private static final int ATTR_COST_Y = 98;
    /** Interruptor de la fabricación: franja libre entre los botones de pieza y la barra de progreso. */
    private static final int TOGGLE_X = 124, TOGGLE_Y = 57, TOGGLE_W = 94, TOGGLE_H = 14;
    private static final int IMBUE_X = 62, IMBUE_Y = 90, IMBUE_W = 56, IMBUE_H = 18;
    /** Barra de carga de imbuir: centrada en el hueco vertical entre la ranura de la pieza (pozo hasta y=53) y la de la
     *  sangre (pozo desde y=63), con el ancho del ítem (x 66..82). Pequeña a propósito (pedido del usuario 2026-11-22;
     *  antes iba a la derecha de las ranuras). */
    private static final int CHARGE_X = 66, CHARGE_Y = 56, CHARGE_W = 16, CHARGE_H = 4;
    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 157;

    private static final String[] UPGRADE_KEYS = {
            "screen.zenkai.ki_chamber.slot.tolerance_super", "screen.zenkai.ki_chamber.slot.tolerance_divine",
            "screen.zenkai.ki_chamber.slot.tolerance_legendary", "screen.zenkai.ki_chamber.slot.speed",
            "screen.zenkai.ki_chamber.slot.storage", "screen.zenkai.armor_fabricator.slot.battery" };
    private static final String[] PIECE_KEYS = {
            "screen.zenkai.armor_fabricator.piece.helmet", "screen.zenkai.armor_fabricator.piece.chestplate",
            "screen.zenkai.armor_fabricator.piece.leggings", "screen.zenkai.armor_fabricator.piece.boots" };

    private final List<TechButton> pieceButtons = new ArrayList<>();
    private TechButton tabCraft, tabAttr, imbueButton, toggleButton;

    public ArmorFabricatorScreen(ArmorFabricatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation background() { return menu.tab() == ArmorFabricatorMenu.TAB_ATTR ? BG_ATTR : BG_CRAFT; }

    @Override
    protected void init() {
        super.init();
        pieceButtons.clear();
        tabCraft = new TechButton(leftPos + TAB1_X, topPos + TAB_Y, TAB_W, TAB_H,
                Component.translatable("screen.zenkai.armor_fabricator.tab.craft"), TechButton.Kind.SECONDARY,
                () -> switchTab(ArmorFabricatorMenu.TAB_CRAFT));
        tabAttr = new TechButton(leftPos + TAB2_X, topPos + TAB_Y, TAB_W, TAB_H,
                Component.translatable("screen.zenkai.armor_fabricator.tab.attributes"), TechButton.Kind.SECONDARY,
                () -> switchTab(ArmorFabricatorMenu.TAB_ATTR));
        addRenderableWidget(tabCraft);
        addRenderableWidget(tabAttr);
        for (int i = 0; i < ArmorFabricatorBlockEntity.PIECES.length; i++) {
            final int idx = i;
            TechButton b = new TechButton(leftPos + PIECE_X + i * PIECE_STEP, topPos + PIECE_Y, PIECE_SIZE, PIECE_SIZE,
                    Component.empty(), TechButton.Kind.SECONDARY, () -> press(ArmorFabricatorMenu.BTN_PIECE_FIRST + idx));
            pieceButtons.add(b);
            addRenderableWidget(b);
        }
        imbueButton = new TechButton(leftPos + IMBUE_X, topPos + IMBUE_Y, IMBUE_W, IMBUE_H,
                Component.translatable("screen.zenkai.armor_fabricator.imbue"), TechButton.Kind.PRIMARY,
                () -> press(ArmorFabricatorMenu.BTN_IMBUE));
        addRenderableWidget(imbueButton);
        toggleButton = new TechButton(leftPos + TOGGLE_X, topPos + TOGGLE_Y, TOGGLE_W, TOGGLE_H,
                Component.empty(), TechButton.Kind.SECONDARY, () -> press(ArmorFabricatorMenu.BTN_TOGGLE_CRAFT));
        addRenderableWidget(toggleButton);
        refresh();
    }

    private void switchTab(int tab) {
        menu.setTab(tab);   // en cliente a mano: el botón vanilla solo llega al servidor
        press(tab == ArmorFabricatorMenu.TAB_ATTR ? ArmorFabricatorMenu.BTN_TAB_ATTR : ArmorFabricatorMenu.BTN_TAB_CRAFT);
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
        boolean craft = menu.tab() == ArmorFabricatorMenu.TAB_CRAFT;
        for (TechButton b : pieceButtons) b.visible = craft;
        imbueButton.visible = !craft;
        toggleButton.visible = craft;
        toggleButton.setMessage(Component.translatable(menu.craftEnabled()
                ? "screen.zenkai.armor_fabricator.toggle.pause" : "screen.zenkai.armor_fabricator.toggle.resume"));
        boolean template = hasTemplate();
        imbueButton.setMessage(Component.translatable(template ? "screen.zenkai.armor_fabricator.template.apply"
                : "screen.zenkai.armor_fabricator.imbue"));
        AttributeTemplates.Plan plan = templatePlan();
        imbueButton.active = !menu.imbuing() && (template ? plan != null && plan.ok() : preview() != null);
        tabCraft.active = !craft;
        tabAttr.active = craft;
    }

    private boolean hasTemplate() { return !menu.machineStack(ArmorFabricatorBlockEntity.SLOT_TEMPLATE).isEmpty(); }

    @Nullable
    private AttributeTemplates.Plan templatePlan() {
        return ArmorFabricatorBlockEntity.templatePlan(minecraft == null ? null : minecraft.level,
                menu.machineStack(ArmorFabricatorBlockEntity.SLOT_TEMPLATE),
                menu.machineStack(ArmorFabricatorBlockEntity.SLOT_ARMOR),
                menu.machineStack(ArmorFabricatorBlockEntity.SLOT_TARGET));
    }

    /** Coste que enseña el bloque inferior de Atributos: el de la plantilla si hay una, si no el de la sangre. */
    private record Cost(long fe, long mb, @Nullable KiEnergyType fluid,
                        java.util.function.Function<KiEnergyType, Long> mbWith) {}

    @Nullable
    private Cost cost() {
        if (hasTemplate()) {
            AttributeTemplates.Plan p = templatePlan();
            if (p == null || !p.ok()) return null;
            return new Cost(p.fe(), p.mb(), p.fluid(), p::mbWith);
        }
        ArmorFabricatorBlockEntity.ImbuePreview p = preview();
        if (p == null) return null;
        return new Cost(p.fe(), p.mb(), p.fluid(), p::mbWith);
    }

    /** Receta de duplicado de la plantilla puesta en la ranura del patrón (pestaña Fabricar). */
    private Optional<RecipeHolder<TemplateDuplicationRecipe>> duplication() {
        if (minecraft == null || minecraft.level == null) return Optional.empty();
        ItemStack t = menu.machineStack(ArmorFabricatorBlockEntity.SLOT_PATTERN);
        if (AttributeTemplates.kindOf(t) == null) return Optional.empty();
        return minecraft.level.getRecipeManager().getRecipeFor(ModRecipes.TEMPLATE_DUPLICATION.get(),
                new TemplateDuplicationRecipe.Input(t), minecraft.level);
    }

    @Nullable
    private ArmorFabricatorBlockEntity.ImbuePreview preview() {
        return ArmorFabricatorBlockEntity.preview(minecraft == null ? null : minecraft.level,
                menu.machineStack(ArmorFabricatorBlockEntity.SLOT_ARMOR),
                menu.machineStack(ArmorFabricatorBlockEntity.SLOT_BLOOD));
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
        drawTitle(g, TITLE_X, TITLE_Y);
        drawLed(g, LED_X, LED_Y, menu.isWorking());
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);
        drawVBar(g, BAR_X, BAR_Y, BAR_W, BAR_H, menu.energyFraction(),
                menu.isWorking() ? ZenkaiTechPalette.LED_GREEN_MID : ZenkaiTechPalette.BAR_FILL);
        KiEnergyType inTank = menu.tankType();
        drawKiTank(g, TANK_X, BAR_Y, BAR_W, BAR_H, menu.tankFraction(), inTank == null ? null : sprite(inTank));

        if (menu.tab() == ArmorFabricatorMenu.TAB_CRAFT) renderCraft(g);
        else renderAttributes(g);
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

    /** Tipo de infusión con que saldría la pieza (el más bajo del material que se gastaría), o null. */
    @Nullable
    private KiEnergyType craftInfusion(ArmorFabricationRecipe rec) {
        return ArmorFabricatorBlockEntity.infusionOf(rec.material(), rec.count(), menu::machineStack);
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

    private int materialCount(net.minecraft.world.item.crafting.Ingredient material) {
        int n = 0;
        for (int i = 0; i < ArmorFabricatorBlockEntity.MATERIAL_SLOTS; i++) {
            ItemStack s = menu.machineStack(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST + i);
            if (!s.isEmpty() && material.test(s)) n += s.getCount();
        }
        return n;
    }

    private void renderAttributes(GuiGraphics g) {
        drawBar(g, CHARGE_X, CHARGE_Y, CHARGE_W, CHARGE_H, menu.imbueFraction());
        int x = ATTR_INFO_X, y = ATTR_INFO_Y;
        ItemStack piece = menu.machineStack(ArmorFabricatorBlockEntity.SLOT_ARMOR);
        PanelText.onDark(g, font, PanelText.fit(font, Component.translatable("screen.zenkai.armor_fabricator.imbued"), INFO_W),
                x, y, ZenkaiTechPalette.DIM_ON_SCREEN);
        y += INFO_LH;
        ImbuedAttributes imb = piece.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE);
        boolean any = false;
        for (ZenkaiAttributes a : ImbuedAttributes.IMBUABLE) {
            int v = imb.get(a);
            if (v <= 0) continue;
            any = true;
            PanelText.onDark(g, font, attrGain(a, v), x, y, ZenkaiTechPalette.OK_ON_SCREEN);
            y += INFO_LH;
        }
        if (!any) {
            PanelText.onDark(g, font, piece.isEmpty() ? Component.literal("—")
                    : Component.translatable("screen.zenkai.armor_fabricator.nothing"), x, y, ZenkaiTechPalette.DIM_ON_SCREEN);
        }

        y = ATTR_COST_Y;
        Cost c = cost();
        if (c == null) {
            // Sin coste que enseñar: el motivo de la plantilla si hay una, si no la pista de qué poner.
            AttributeTemplates.Plan plan = templatePlan();
            Component hint = plan != null ? Component.translatable(plan.problem().langKey())
                    : Component.translatable(hasTemplate() ? "screen.zenkai.armor_fabricator.template.no_piece"
                    : "screen.zenkai.armor_fabricator.imbue.hint");
            int color = plan != null && plan.problem() != AttributeTemplates.Problem.NO_PIECE
                    ? ZenkaiTechPalette.DENIED_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN;
            for (var line : font.split(hint, INFO_W)) {
                g.drawString(font, line, x, y, color, true);
                y += INFO_LH;
            }
            return;
        }
        if (hasTemplate()) {
            AttributeTemplates.Kind kind = AttributeTemplates.kindOf(menu.machineStack(ArmorFabricatorBlockEntity.SLOT_TEMPLATE));
            infoRow(g, x, y, "screen.zenkai.armor_fabricator.template.action",
                    Component.translatable("screen.zenkai.armor_fabricator.template.kind." + kind.id()),
                    ZenkaiTechPalette.OK_ON_SCREEN);
        } else {
            ArmorFabricatorBlockEntity.ImbuePreview p = preview();
            infoRow(g, x, y, "screen.zenkai.armor_fabricator.imbue.gain", attrGain(p.attribute(), p.points()),
                    ZenkaiTechPalette.OK_ON_SCREEN);
        }
        y += INFO_LH;
        boolean feOk = menu.energy() >= c.fe();
        infoRow(g, x, y, "screen.zenkai.armor_fabricator.cost",
                Component.literal(ZenkaiNumbers.screen(c.fe()) + " FE"),
                feOk ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        y += INFO_LH;
        if (c.fluid() == null) return;   // reiniciar y transferir no gastan fluido
        // Con un tipo válido en el tanque (el pedido o uno superior) se muestra ESE tipo y lo que gasta; si no, el pedido.
        KiEnergyType inTank = menu.tankType();
        long mb = c.mbWith().apply(inTank);
        KiEnergyType shown = mb >= 0 ? inTank : c.fluid();
        long shownMb = mb >= 0 ? mb : c.mb();
        boolean fluidOk = mb >= 0 && menu.tankAmount() >= mb;
        // El color del tipo va en la ETIQUETA: el Ki Divino es rojo y en la cifra se confundía con «falta» (captura 2026-11-22).
        infoRow(g, x, y, shown.langKey(), shown.argb(), Component.literal(ZenkaiNumbers.screen(shownMb) + " mB"),
                fluidOk ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
    }

    /** «+5 FUE» sin el espacio inicial que lleva la clave del tooltip. */
    private static Component attrGain(ZenkaiAttributes a, int points) {
        return Component.literal(Component.translatable("tooltip.zenkai.imbued." + key(a),
                ZenkaiNumbers.screen((long) points)).getString().trim());
    }

    private static String key(ZenkaiAttributes a) {
        return switch (a) {
            case STRENGTH -> "str";
            case CONSTITUTION -> "con";
            case DEXTERITY -> "dex";
            case WILLPOWER -> "wil";
            case SPIRIT -> "spi";
            case MIND -> "mnd";
        };
    }

    /** Etiqueta a la izquierda y cifra a la derecha; la etiqueta se recorta para no pisar la cifra. */
    private void infoRow(GuiGraphics g, int x, int y, String key, Component value, int valueColor) {
        infoRow(g, x, y, key, ZenkaiTechPalette.DIM_ON_SCREEN, value, valueColor);
    }

    private void infoRow(GuiGraphics g, int x, int y, String key, int labelColor, Component value, int valueColor) {
        int room = INFO_RIGHT - x - font.width(value) - 4;
        PanelText.onDark(g, font, PanelText.fit(font, Component.translatable(key), room), x, y, labelColor);
        PanelText.rightOnDark(g, font, value, INFO_RIGHT, y, valueColor);
    }

    private static ResourceLocation sprite(KiEnergyType type) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "block/" + type.fluidName() + "_still");
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        int lmx = localMouseX(mouseX), lmy = localMouseY(mouseY);
        if (inside(lmx, lmy, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.ki_chamber.fe").withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(ZenkaiNumbers.exact((long) menu.energy()) + " / "
                            + ZenkaiNumbers.exact((long) menu.energyCapacity()) + " FE"))));
            return;
        }
        if (inside(lmx, lmy, TANK_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            KiEnergyType t = menu.tankType();
            Component name = t == null ? Component.translatable("screen.zenkai.ki_chamber.tank_empty")
                    : Component.translatable(t.langKey()).withStyle(s -> s.withColor(t.argb() & 0xFFFFFF));
            List<TipLine> lines = new ArrayList<>(List.of(
                    TipLine.of(name.copy().withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(ZenkaiNumbers.exact((long) menu.tankAmount()) + " / "
                            + ZenkaiNumbers.exact((long) menu.tankCapacity()) + " mB")),
                    TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.tank.tip").withStyle(ChatFormatting.GRAY))));
            if (t != null && !menu.tolerates(t)) {
                lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.tank.no_tolerance")
                        .withStyle(ChatFormatting.RED)));
            }
            drawTooltip(g, mouseX, mouseY, lines);
            return;
        }
        if (menu.tab() == ArmorFabricatorMenu.TAB_CRAFT && toggleButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable(menu.craftEnabled() ? "screen.zenkai.armor_fabricator.status.enabled"
                            : "screen.zenkai.armor_fabricator.status.paused").withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.toggle.tip").withStyle(ChatFormatting.GRAY))));
            return;
        }
        if (menu.tab() == ArmorFabricatorMenu.TAB_ATTR && kiTooltip(g, mouseX, mouseY, lmx, lmy)) return;
        if (menu.tab() == ArmorFabricatorMenu.TAB_CRAFT && infusionTooltip(g, mouseX, mouseY, lmx, lmy)) return;
        if (menu.tab() == ArmorFabricatorMenu.TAB_CRAFT) {
            for (int i = 0; i < pieceButtons.size(); i++) {
                if (pieceButtons.get(i).isMouseOver(mouseX, mouseY)) {
                    drawTooltip(g, mouseX, mouseY, List.of(TipLine.of(Component.translatable(PIECE_KEYS[i]))));
                    return;
                }
            }
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int idx = menu.slots.indexOf(hoveredSlot);
            String k = slotKey(idx);
            if (k != null) {
                drawTooltip(g, mouseX, mouseY, List.of(
                        TipLine.of(Component.translatable(k).withStyle(ChatFormatting.BOLD)),
                        TipLine.of(Component.translatable(k + ".desc").withStyle(ChatFormatting.GRAY))));
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
        lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.infusion.lowest").withStyle(ChatFormatting.DARK_GRAY)));
        drawTooltip(g, mouseX, mouseY, lines);
        return true;
    }

    /** Tooltip de la fila de Ki de Atributos: tipo pedido y lo que gasta cada tipo que vale. */
    private boolean kiTooltip(GuiGraphics g, int mouseX, int mouseY, int lmx, int lmy) {
        int rowY = ATTR_COST_Y + 2 * INFO_LH;
        if (lmx < ATTR_INFO_X || lmx >= INFO_RIGHT || lmy < rowY - 1 || lmy >= rowY + INFO_LH - 1) return false;
        Cost p = cost();
        if (p == null || p.fluid() == null) return false;
        KiEnergyType req = p.fluid();
        List<TipLine> lines = new ArrayList<>();
        lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.ki.requires",
                Component.translatable(req.langKey()).withStyle(st -> st.withColor(req.argb() & 0xFFFFFF)))
                .withStyle(ChatFormatting.BOLD)));
        // Pieza infundida: el tipo sale de su infusión y el coste lleva su descuento (Fase 5c).
        ItemStack payer = menu.machineStack(hasTemplate() && AttributeTemplates.kindOf(
                menu.machineStack(ArmorFabricatorBlockEntity.SLOT_TEMPLATE)) == AttributeTemplates.Kind.COPY
                ? ArmorFabricatorBlockEntity.SLOT_TARGET : ArmorFabricatorBlockEntity.SLOT_ARMOR);
        KiEnergyType inf = KiInfusion.of(payer);
        if (inf != null) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.ki.infused",
                    ZenkaiNumbers.fmt2(KiInfusion.imbueCostMult(inf))).withStyle(ChatFormatting.GRAY)));
        }
        for (KiEnergyType t : KiEnergyType.values()) {
            long mb = p.mbWith().apply(t);
            if (mb < 0) continue;
            lines.add(TipLine.of(Component.translatable(t.langKey()).withStyle(st -> st.withColor(t.argb() & 0xFFFFFF))
                    .append(Component.literal(": " + ZenkaiNumbers.screen(mb) + " mB").withStyle(ChatFormatting.GRAY))));
        }
        lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.ki.higher").withStyle(ChatFormatting.GRAY)));
        drawTooltip(g, mouseX, mouseY, lines);
        return true;
    }

    @Nullable
    private static String slotKey(int idx) {
        if (idx < 0) return null;
        if (idx < ArmorFabricatorBlockEntity.UPGRADE_SLOTS) return UPGRADE_KEYS[idx];
        if (idx == ArmorFabricatorBlockEntity.SLOT_PATTERN) return "screen.zenkai.armor_fabricator.slot.pattern";
        if (idx >= ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST && idx < ArmorFabricatorBlockEntity.SLOT_OUTPUT) {
            return "screen.zenkai.armor_fabricator.slot.material";
        }
        if (idx == ArmorFabricatorBlockEntity.SLOT_ARMOR) return "screen.zenkai.armor_fabricator.slot.armor";
        if (idx == ArmorFabricatorBlockEntity.SLOT_BLOOD) return "screen.zenkai.armor_fabricator.slot.blood";
        if (idx == ArmorFabricatorBlockEntity.SLOT_TEMPLATE) return "screen.zenkai.armor_fabricator.slot.template";
        if (idx == ArmorFabricatorBlockEntity.SLOT_TARGET) return "screen.zenkai.armor_fabricator.slot.target";
        return null;
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
