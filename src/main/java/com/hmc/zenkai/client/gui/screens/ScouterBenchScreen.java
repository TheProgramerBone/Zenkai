package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.BenchPlusButton;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.client.gui.menu.ScouterBenchMenu;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.hmc.zenkai.feature.sense.ScouterStacks;
import com.hmc.zenkai.feature.sense.ScouterUpgrade;
import com.hmc.zenkai.feature.sense.ScouterUpgradeCost;
import com.hmc.zenkai.feature.sense.ScouterUpgrades;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GUI del banco de scouter. Primer integrante de la familia tecnológica.
 * DOS FONDOS EN LA MISMA PANTALLA, y de ahí sale casi lo demás:
 *   - CARCASA clara: título de zona, resumen del aparato, etiqueta del inventario. Texto
 *     oscuro y sin sombra (_ON_CHASSIS).
 *   - PANTALLA hundida (86,18 - 160x110): la lista de mejoras. Texto claro con sombra
 *     (_ON_SCREEN). Es lo que hace que la lista se lea como la lectura del aparato y no como
 *     una tabla flotando sobre plástico.
 * La placa oscura de cabecera existe para lo mismo: el título va en cian y el cian sobre
 * blanco no se lee.
 * Las coordenadas son las del PNG. Cada pozo de slot está pintado en la textura, así que
 * tocar un número de aquí obliga a repintarla — por eso están todas juntas arriba.
 */
public class ScouterBenchScreen extends TechPanelScreen<ScouterBenchMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/scouter_bench.png");
    private static final ResourceLocation SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/slot/empty_scouter_slot.png");

    // ── Layout (coordenadas del PNG) ─────────────────────────────────────────
    private static final int TITLE_X = 12, TITLE_Y = 8;

    /** Esquina del ÍTEM. El pozo de la textura está en (x-1, y-1), 18x18. */
    private static final int SCOUTER_SLOT_X = 18, SCOUTER_SLOT_Y = 32;

    private static final int LED_X = 62, LED_Y = 32;

    private static final int SUM_X = 16, SUM_Y = 55, SUM_LH = 10;

    private static final int BAR_X = 14, BAR_Y = 106, BAR_W = 68, BAR_H = 6;
    private static final int BAR_LABEL_Y = 116;

    private static final int ROW_Y0 = 24, ROW_H = 20;
    private static final int ROW_LEFT = 88, ROW_RIGHT = 244;
    private static final int LIST_X = 94;
    private static final int SEG_W = 7, SEG_H = 5, SEG_Y_OFF = 13;
    private static final int PLUS_X = 222, PLUS_Y_OFF = 2;

    private static final int FOOTER_Y = 128;
    private static final int FOOTER_LEFT_X = 40;
    private static final int FOOTER_RIGHT_X = 132;

    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 152;

    private final List<BenchPlusButton> plusButtons = new ArrayList<>();
    private TechButton repairButton;
    private TechButton cancelButton;

    public ScouterBenchScreen(ScouterBenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation background() { return BG; }

    @Override
    protected void init() {
        super.init();

        plusButtons.clear();
        ScouterUpgrade[] all = ScouterUpgrade.values();
        for (int i = 0; i < all.length; i++) {
            final int ordinal = i;
            int y = topPos + ROW_Y0 + i * ROW_H + PLUS_Y_OFF;
            BenchPlusButton b = new BenchPlusButton(leftPos + PLUS_X, y, () -> press(ordinal));
            plusButtons.add(b);
            addRenderableWidget(b);
        }

        repairButton = TechButton.primary(leftPos + FOOTER_LEFT_X, topPos + FOOTER_Y,
                Component.translatable("screen.zenkai.scouter_bench.repair"),
                () -> press(ScouterBenchMenu.BTN_REPAIR));
        addRenderableWidget(repairButton);

        cancelButton = TechButton.secondary(leftPos + FOOTER_RIGHT_X, topPos + FOOTER_Y,
                Component.translatable("screen.zenkai.cancel"),
                () -> press(ScouterBenchMenu.BTN_CANCEL));
        addRenderableWidget(cancelButton);

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

    /** Único sitio que decide qué botón está vivo. Se recalcula cada tick desde el slot. */
    private void refresh() {
        ItemStack s = menu.scouter();
        boolean hasScouter = !s.isEmpty();
        boolean broken = hasScouter && ScouterStacks.isBroken(s);
        boolean working = menu.isWorking();

        ScouterUpgrade[] all = ScouterUpgrade.values();
        for (int i = 0; i < all.length; i++) {
            plusButtons.get(i).active = hasScouter && !broken && !working && canBuy(s, all[i]);
        }
        // Visible SIEMPRE, apagado cuando no procede: el jugador tiene que saber que el banco
        // repara aunque hoy no tenga nada roto que meterle.
        repairButton.active = broken && !working;
        cancelButton.active = working;
    }

    private boolean canBuy(ItemStack s, ScouterUpgrade u) {
        int next = ScouterStacks.upgrades(s).nextLevel(u);
        if (next < 0) return false;
        if (minecraft == null || minecraft.player == null) return false;
        return ScouterUpgradeCost.forLevel(u, next).canAfford(minecraft.player.getInventory());
    }

    // ── Fondo ────────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);

        // Fantasma del slot: solo si está vacío, o taparía al scouter de verdad.
        if (menu.scouter().isEmpty()) {
            RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, 1f);
            g.blit(SLOT_BG, leftPos + SCOUTER_SLOT_X, topPos + SCOUTER_SLOT_Y,
                    0, 0, 16, 16, 16, 16);
        }
    }

    // ── Contenido (matriz ya trasladada al panel: coordenadas locales) ───────

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawTitle(g, TITLE_X, TITLE_Y);
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);

        drawLed(g, LED_X, LED_Y, menu.isWorking());

        drawSummary(g);
        drawBar(g, BAR_X, BAR_Y, BAR_W, BAR_H, menu.progressFraction());
        drawBarLabel(g);
        drawRows(g);

        // El velo va aquí y no en renderBg porque los ítems de los slots se dibujan ENTRE
        // renderBg y renderLabels: pintado antes, quedaría debajo del propio ítem.
        ScouterUpgrade hovered = hoveredUpgrade(localMouseX(mouseX), localMouseY(mouseY));
        if (hovered != null) highlightMaterials(g, hovered);
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        renderUpgradeTooltip(g, mouseX, mouseY);
    }

    /** Placa izquierda: qué es capaz de hacer HOY el scouter que hay dentro. */
    private void drawSummary(GuiGraphics g) {
        ItemStack s = menu.scouter();

        if (s.isEmpty()) {
            PanelText.onPanel(g, font,
                    Component.translatable("screen.zenkai.scouter_bench.insert"),
                    SUM_X, SUM_Y, ZenkaiTechPalette.DENIED_ON_CHASSIS);
            return;
        }

        ScouterUpgrades up = ScouterStacks.upgrades(s);
        int y = SUM_Y;

        int rangeLvl = up.level(ScouterUpgrade.RANGE);
        PanelText.onPanel(g, font, Component.translatable(
                        "screen.zenkai.scouter_bench.range",
                        String.valueOf((int) ScouterUpgrade.rangeFor(rangeLvl))),
                SUM_X, y, ZenkaiTechPalette.LABEL_ON_CHASSIS);
        y += SUM_LH;

        int capLvl = up.level(ScouterUpgrade.PL_CAP);
        String cap = capLvl >= ScouterUpgrade.PL_CAP.maxLevel()
                ? "∞"
                : String.format(Locale.ROOT, "%,d", ScouterUpgrade.plCapFor(capLvl));
        PanelText.onPanel(g, font,
                Component.translatable("screen.zenkai.scouter_bench.pl", cap),
                SUM_X, y, ZenkaiTechPalette.LABEL_ON_CHASSIS);
        y += SUM_LH + 2;

        // Las tres binarias como lista de estado. Un renglón por función y no una fila de
        // siglas: "RDR STA SCN" no lo entiende nadie sin abrir el tooltip.
        y = drawUnlock(g, ScouterUpgrade.DRAGON_RADAR, up, y);
        y = drawUnlock(g, ScouterUpgrade.ANALYZER, up, y);
        drawUnlock(g, ScouterUpgrade.AREA_SCANNER, up, y);
    }

    private int drawUnlock(GuiGraphics g, ScouterUpgrade u, ScouterUpgrades up, int y) {
        boolean has = up.has(u);
        Component line = Component.literal(has ? "✔ " : "✘ ")
                .append(Component.translatable(u.nameKey()));
        PanelText.onPanel(g, font, line, SUM_X, y,
                has ? ZenkaiTechPalette.OK_ON_CHASSIS : ZenkaiTechPalette.MUTED_ON_CHASSIS);
        return y + SUM_LH;
    }

    /** Qué se está haciendo. Va sobre la placa clara, así que sin sombra. */
    private void drawBarLabel(GuiGraphics g) {
        if (!menu.isWorking()) return;

        Component label = menu.isPaused()
                ? Component.translatable("screen.zenkai.scouter_bench.paused")
                : jobLabel();
        PanelText.onPanel(g, font, PanelText.fit(font, label, ROW_LEFT - SUM_X - 8),
                SUM_X, BAR_LABEL_Y,
                menu.isPaused() ? ZenkaiTechPalette.DENIED_ON_CHASSIS
                        : ZenkaiTechPalette.BODY_ON_CHASSIS);
    }

    private Component jobLabel() {
        int job = menu.job();
        if (job == ScouterBenchBlockEntity.JOB_REPAIR) {
            return Component.translatable("screen.zenkai.scouter_bench.repairing");
        }
        ScouterUpgrade[] all = ScouterUpgrade.values();
        if (job >= 0 && job < all.length) {
            return Component.translatable("screen.zenkai.scouter_bench.installing",
                    Component.translatable(all[job].nameKey()));
        }
        return Component.translatable("screen.zenkai.scouter_bench.working");
    }

    /** Lista de mejoras. Va DENTRO de la pantalla: texto claro con sombra. */
    private void drawRows(GuiGraphics g) {
        ItemStack s = menu.scouter();
        boolean empty = s.isEmpty();
        ScouterUpgrades up = ScouterStacks.upgrades(s);
        ScouterUpgrade[] all = ScouterUpgrade.values();

        for (int i = 0; i < all.length; i++) {
            ScouterUpgrade u = all[i];
            int y = ROW_Y0 + i * ROW_H;

            if ((i & 1) == 1) {
                g.fill(ROW_LEFT, y, ROW_RIGHT, y + ROW_H, ZenkaiTechPalette.ROW_BAND);
            }

            int lvl = empty ? 0 : up.level(u);
            boolean maxed = !empty && lvl >= u.maxLevel();

            String name = Component.translatable(u.nameKey()).getString().toUpperCase(Locale.ROOT);
            Component label = Component.literal(name + " · " + lvl + "/" + u.maxLevel());

            int color = empty ? ZenkaiTechPalette.DIM_ON_SCREEN
                    : maxed ? ZenkaiTechPalette.MAXED_ON_SCREEN
                    : ZenkaiTechPalette.TEXT_ON_SCREEN;
            PanelText.onDark(g, font, label, LIST_X, y + 3, color);

            drawSegments(g, LIST_X, y + SEG_Y_OFF, u.maxLevel(), lvl, SEG_W, SEG_H, empty);
        }
    }

    /** Velo cian sobre los slots del inventario que contienen material de esta mejora. */
    private void highlightMaterials(GuiGraphics g, ScouterUpgrade u) {
        ItemStack s = menu.scouter();
        if (s.isEmpty()) return;

        int next = ScouterStacks.upgrades(s).nextLevel(u);
        if (next < 0) return;

        ScouterUpgradeCost cost = ScouterUpgradeCost.forLevel(u, next);
        if (cost.materials().isEmpty()) return;

        for (int i = ScouterBenchMenu.INV_FIRST; i < ScouterBenchMenu.INV_END; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            for (ScouterUpgradeCost.Material m : cost.materials()) {
                if (m.matches(st)) {
                    g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16,
                            ZenkaiTechPalette.SELECT_VEIL);
                    break;
                }
            }
        }
    }

    // ── Tooltip ──────────────────────────────────────────────────────────────

    /** Qué mejora está bajo el cursor, en coordenadas LOCALES del panel. */
    @Nullable
    private ScouterUpgrade hoveredUpgrade(int lmx, int lmy) {
        if (lmx < ROW_LEFT || lmx > ROW_RIGHT) return null;
        ScouterUpgrade[] all = ScouterUpgrade.values();
        for (int i = 0; i < all.length; i++) {
            int y = ROW_Y0 + i * ROW_H;
            if (lmy >= y && lmy < y + ROW_H) return all[i];
        }
        return null;
    }

    private void renderUpgradeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        ScouterUpgrade u = hoveredUpgrade(localMouseX(mouseX), localMouseY(mouseY));
        if (u == null) return;

        ItemStack s = menu.scouter();
        List<TipLine> lines = new ArrayList<>();
        lines.add(TipLine.of(Component.translatable(u.nameKey()).withStyle(ChatFormatting.BOLD)));
        lines.add(TipLine.of(Component.translatable(u.descKey()).withStyle(ChatFormatting.GRAY)));

        int next = s.isEmpty() ? 1 : ScouterStacks.upgrades(s).nextLevel(u);
        if (next < 0) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.maxed")
                    .withStyle(ChatFormatting.AQUA)));
        } else {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.cost", next)
                    .withStyle(ChatFormatting.DARK_GRAY)));
            ScouterUpgradeCost cost = ScouterUpgradeCost.forLevel(u, next);
            for (ScouterUpgradeCost.Material m : cost.materials()) {
                lines.add(materialLine(m));
            }
            if (cost.energy() > 0) {
                lines.add(TipLine.of(Component.translatable(
                                "screen.zenkai.scouter_bench.energy", cost.energy())
                        .withStyle(ChatFormatting.DARK_GRAY)));
            }
        }

        drawTooltip(g, mouseX, mouseY, lines);
    }

    /** "4x Iron Ingot" con su icono, en verde si lo tienes y en rojo si no. */
    private TipLine materialLine(ScouterUpgradeCost.Material m) {
        List<Item> items = m.displayItems();
        ItemStack icon = items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.getFirst());
        Component name = items.isEmpty()
                ? Component.literal(m.id().toString())
                : items.getFirst().getDescription();

        boolean have = minecraft != null && minecraft.player != null
                && m.countIn(minecraft.player.getInventory()) >= m.count();

        Component text = Component.literal(m.count() + "x ").append(name)
                .withStyle(have ? ChatFormatting.GREEN : ChatFormatting.RED);
        return new TipLine(icon.isEmpty() ? null : icon, text);
    }
}