package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.BenchPlusButton;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.menu.ScouterBenchMenu;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.hmc.zenkai.feature.sense.ScouterStacks;
import com.hmc.zenkai.feature.sense.ScouterUpgrade;
import com.hmc.zenkai.feature.sense.ScouterUpgradeCost;
import com.hmc.zenkai.feature.sense.ScouterUpgrades;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
 * GUI del banco de scouter, reescrita para leerse como un contenedor de Minecraft y no como
 * un panel del mod con slots invisibles encima.
 * QUÉ CAMBIA RESPECTO A LA VERSIÓN ANTERIOR Y POR QUÉ:
 *  - Fondo propio (scouter_bench.png). El inventario del jugador estaba REGISTRADO pero no se
 *    dibujaba ningún pozo: 36 slots funcionales sobre beige liso. Los pozos ahora van
 *    horneados en la textura, que es donde vanilla los pone.
 *  - Título DENTRO del panel, arriba a la izquierda, y "Inventory" sobre la rejilla. Sobre el
 *    beige el título va en el dorado quemado de panel (VALUE_ON_PANEL) y SIN sombra: el
 *    dorado claro de ScreenTitle solo funciona sobre el mundo.
 *  - El dibujo de contenido vive en renderLabels(), que se ejecuta con la matriz YA
 *    trasladada al panel. Así las constantes de layout son coordenadas del PNG y se pueden
 *    comparar directamente con la textura. OJO: renderLabels recibe el ratón en coordenadas
 *    ABSOLUTAS aunque dibuje en locales — de ahí las restas de leftPos/topPos.
 *  - La barra de progreso es FIJA: existe siempre bajo el slot, vacía cuando no hay trabajo.
 *    Antes aparecía y desaparecía debajo de la lista y movía la lectura.
 *  - Tooltip propio en vez de renderComponentTooltip: hace falta pintar el ICONO del material
 *    en cada línea y la ruta de vanilla para eso (ClientTooltipComponent) es privada.
 */
public class ScouterBenchScreen extends AbstractContainerScreen<ScouterBenchMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/scouter_bench.png");
    private static final ResourceLocation SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/slot/empty_scouter_slot.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;

    // ── Layout, en coordenadas del PNG (panel-local) ─────────────────────────
    private static final int TITLE_X = 10, TITLE_Y = 8;

    /** Esquina del ÍTEM del slot. El pozo de la textura va en (x-1, y-1), 18x18. */
    private static final int SCOUTER_SLOT_X = 18, SCOUTER_SLOT_Y = 32;

    private static final int SUM_X = 14, SUM_Y = 52, SUM_LH = 10;

    private static final int BAR_X = 14, BAR_Y = 106, BAR_W = 68, BAR_H = 6;
    private static final int BAR_LABEL_Y = 116;

    private static final int ROW_Y0 = 24, ROW_H = 20;
    private static final int ROW_LEFT = 88, ROW_RIGHT = 244;
    private static final int LIST_X = 94;
    private static final int PIP = 6, PIP_GAP = 2, PIP_Y_OFF = 12;
    private static final int PLUS_X = 222, PLUS_Y_OFF = 2;

    private static final int FOOTER_Y = 128;
    private static final int FOOTER_LEFT_X = 40;
    private static final int FOOTER_RIGHT_X = 132;

    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 152;

    private final List<BenchPlusButton> plusButtons = new ArrayList<>();
    private PanelButton repairButton;
    private PanelButton cancelButton;

    public ScouterBenchScreen(ScouterBenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = BG_W;
        this.imageHeight = BG_H;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - BG_W) / 2;
        this.topPos = (this.height - BG_H) / 2;

        plusButtons.clear();
        ScouterUpgrade[] all = ScouterUpgrade.values();
        for (int i = 0; i < all.length; i++) {
            final int ordinal = i;
            int y = topPos + ROW_Y0 + i * ROW_H + PLUS_Y_OFF;
            BenchPlusButton b = new BenchPlusButton(leftPos + PLUS_X, y, () -> press(ordinal));
            plusButtons.add(b);
            addRenderableWidget(b);
        }

        repairButton = PanelButton.primary(leftPos + FOOTER_LEFT_X, topPos + FOOTER_Y,
                Component.translatable("screen.zenkai.scouter_bench.repair"),
                () -> press(ScouterBenchMenu.BTN_REPAIR));
        addRenderableWidget(repairButton);

        cancelButton = PanelButton.secondary(leftPos + FOOTER_RIGHT_X, topPos + FOOTER_Y,
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
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(BG, leftPos, topPos, 0, 0, BG_W, BG_H, BG_W, BG_H);

        // Fantasma del slot: solo si está vacío, o taparía al scouter de verdad.
        if (menu.scouter().isEmpty()) {
            g.blit(SLOT_BG, leftPos + SCOUTER_SLOT_X, topPos + SCOUTER_SLOT_Y,
                    0, 0, 16, 16, 16, 16);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        // Después de super: el tooltip propio tiene que quedar por encima de lo demás, incluidos
        // los slots y el ítem que el jugador lleve agarrado.
        renderUpgradeTooltip(g, mouseX, mouseY);
    }

    // ── Contenido (matriz ya trasladada al panel: coordenadas locales) ───────

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        int lmx = mouseX - leftPos;
        int lmy = mouseY - topPos;

        // Título en mayúsculas y negrita como el resto del mod, pero en el dorado QUEMADO y
        // sin sombra: es la única versión del dorado que se lee sobre el beige.
        Component styled = Component.literal(title.getString().toUpperCase(Locale.ROOT))
                .withStyle(ChatFormatting.BOLD);
        PanelText.onPanel(g, font, styled, TITLE_X, TITLE_Y, ZenkaiPalette.VALUE_ON_PANEL);

        PanelText.onPanel(g, font, playerInventoryTitle, INV_LABEL_X, INV_LABEL_Y,
                ZenkaiPalette.MUTED_ON_PANEL);

        drawSummary(g);
        drawBar(g);
        drawRows(g);

        // El velo va aquí y no en renderBg porque los ítems de los slots se dibujan ENTRE
        // renderBg y renderLabels: pintado antes, quedaría debajo del propio ítem.
        ScouterUpgrade hovered = hoveredUpgrade(lmx, lmy);
        if (hovered != null) highlightMaterials(g, hovered);
    }

    /** Columna izquierda: qué es capaz de hacer HOY el scouter que hay dentro. */
    private void drawSummary(GuiGraphics g) {
        ItemStack s = menu.scouter();

        if (s.isEmpty()) {
            PanelText.onPanel(g, font,
                    Component.translatable("screen.zenkai.scouter_bench.insert"),
                    SUM_X, SUM_Y, ZenkaiPalette.DENIED_ON_PANEL);
            return;
        }

        ScouterUpgrades up = ScouterStacks.upgrades(s);
        int y = SUM_Y;

        int rangeLvl = up.level(ScouterUpgrade.RANGE);
        PanelText.onPanel(g, font, Component.translatable(
                        "screen.zenkai.scouter_bench.range",
                        String.valueOf((int) ScouterUpgrade.rangeFor(rangeLvl))),
                SUM_X, y, ZenkaiPalette.LABEL_ON_PANEL);
        y += SUM_LH;

        int capLvl = up.level(ScouterUpgrade.PL_CAP);
        String cap = capLvl >= ScouterUpgrade.PL_CAP.maxLevel()
                ? "∞"
                : String.format(Locale.ROOT, "%,d", ScouterUpgrade.plCapFor(capLvl));
        PanelText.onPanel(g, font,
                Component.translatable("screen.zenkai.scouter_bench.pl", cap),
                SUM_X, y, ZenkaiPalette.LABEL_ON_PANEL);
        y += SUM_LH + 2;

        // Las tres binarias como lista de estado. Un renglón por función y no una fila de
        // siglas: "RDR STA SCN" no lo entiende nadie sin abrir el tooltip.
        y = drawUnlock(g, ScouterUpgrade.DRAGON_RADAR, up, y);
        y = drawUnlock(g, ScouterUpgrade.ANALYZER, up, y);
        drawUnlock(g, ScouterUpgrade.AREA_SCANNER, up, y);
    }

    private int drawUnlock(GuiGraphics g, ScouterUpgrade u, ScouterUpgrades up, int y) {
        boolean has = up.has(u);
        String mark = has ? "✔ " : "✘ ";
        Component line = Component.literal(mark).append(Component.translatable(u.nameKey()));
        PanelText.onPanel(g, font, line, SUM_X, y,
                has ? ZenkaiPalette.OWNED_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL);
        return y + SUM_LH;
    }

    /** Barra fija: el canal existe siempre, el relleno solo cuando hay trabajo. */
    private void drawBar(GuiGraphics g) {
        g.fill(BAR_X - 1, BAR_Y - 1, BAR_X + BAR_W + 1, BAR_Y + BAR_H + 1, ZenkaiPalette.BAR_FRAME);
        g.fill(BAR_X, BAR_Y, BAR_X + BAR_W, BAR_Y + BAR_H, ZenkaiPalette.BAR_BG);

        if (!menu.isWorking()) return;

        int w = Math.round(BAR_W * menu.progressFraction());
        g.fill(BAR_X, BAR_Y, BAR_X + w, BAR_Y + BAR_H, ZenkaiPalette.BAR_CONTROL);

        Component label = menu.isPaused()
                ? Component.translatable("screen.zenkai.scouter_bench.paused")
                : jobLabel();
        PanelText.onPanel(g, font, PanelText.fit(font, label, ROW_LEFT - SUM_X - 4),
                SUM_X, BAR_LABEL_Y,
                menu.isPaused() ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.BODY_ON_PANEL);
    }

    /** Qué se está haciendo: reparar, o el nombre de la mejora en curso. */
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

    /** Lista de mejoras: banda alterna, nombre · nivel, y pips debajo. */
    private void drawRows(GuiGraphics g) {
        ItemStack s = menu.scouter();
        boolean empty = s.isEmpty();
        ScouterUpgrades up = ScouterStacks.upgrades(s);
        ScouterUpgrade[] all = ScouterUpgrade.values();

        for (int i = 0; i < all.length; i++) {
            ScouterUpgrade u = all[i];
            int y = ROW_Y0 + i * ROW_H;

            if ((i & 1) == 1) {
                g.fill(ROW_LEFT, y, ROW_RIGHT, y + ROW_H, ZenkaiPalette.ROW_BAND);
            }

            int lvl = empty ? 0 : up.level(u);
            boolean maxed = !empty && lvl >= u.maxLevel();

            String name = Component.translatable(u.nameKey()).getString().toUpperCase(Locale.ROOT);
            Component label = Component.literal(name + " · " + lvl + "/" + u.maxLevel());

            int color = empty ? ZenkaiPalette.MUTED_ON_PANEL
                    : maxed ? ZenkaiPalette.MAXED_ON_PANEL
                    : ZenkaiPalette.LABEL_ON_PANEL;
            PanelText.onPanel(g, font, label, LIST_X, y + 3, color);

            drawPips(g, LIST_X, y + PIP_Y_OFF, u.maxLevel(), lvl, empty);
        }
    }

    /**
     * Pips de nivel. Las binarias tienen maxLevel 1 y salen con un solo pip sin ningún caso
     * especial: el nivel ES el dato, no hay ramas de "esta es booleana".
     */
    private void drawPips(GuiGraphics g, int x, int y, int max, int lvl, boolean dim) {
        for (int i = 0; i < max; i++) {
            int px = x + i * (PIP + PIP_GAP);
            g.fill(px, y, px + PIP, y + PIP, ZenkaiPalette.BAR_FRAME);
            int fill = i < lvl
                    ? (dim ? ZenkaiPalette.MUTED_ON_PANEL : ZenkaiPalette.BAR_CONTROL)
                    : ZenkaiPalette.BAR_BG;
            g.fill(px + 1, y + 1, px + PIP - 1, y + PIP - 1, fill);
        }
    }

    /** Velo dorado sobre los slots del inventario que contienen material de esta mejora. */
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
                    g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, ZenkaiPalette.SELECT_VEIL);
                    break;
                }
            }
        }
    }

    // ── Tooltip del + (coordenadas absolutas) ────────────────────────────────

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

    /** Una línea del tooltip: icono opcional + texto ya estilizado. */
    private record TipLine(@Nullable ItemStack icon, Component text) {}

    private void renderUpgradeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        ScouterUpgrade u = hoveredUpgrade(mouseX - leftPos, mouseY - topPos);
        if (u == null) return;

        ItemStack s = menu.scouter();
        List<TipLine> lines = new ArrayList<>();
        lines.add(new TipLine(null, Component.translatable(u.nameKey())
                .withStyle(ChatFormatting.BOLD)));
        lines.add(new TipLine(null, Component.translatable(u.descKey())
                .withStyle(ChatFormatting.GRAY)));

        int next = s.isEmpty() ? 1 : ScouterStacks.upgrades(s).nextLevel(u);
        if (next < 0) {
            lines.add(new TipLine(null, Component.translatable("screen.zenkai.scouter_bench.maxed")
                    .withStyle(ChatFormatting.AQUA)));
        } else {
            lines.add(new TipLine(null, Component.translatable(
                            "screen.zenkai.scouter_bench.cost", next)
                    .withStyle(ChatFormatting.DARK_GRAY)));
            ScouterUpgradeCost cost = ScouterUpgradeCost.forLevel(u, next);
            for (ScouterUpgradeCost.Material m : cost.materials()) {
                lines.add(materialLine(m));
            }
            if (cost.energy() > 0) {
                lines.add(new TipLine(null, Component.translatable(
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

    /**
     * Tooltip propio. Vanilla no puede: renderComponentTooltip solo pinta texto y la ruta con
     * iconos (renderTooltipInternal + ClientTooltipComponent) es privada.
     * Fondo oscuro, así que el texto va CON sombra — la regla de PanelText, al revés que en
     * el resto de esta pantalla.
     */
    private void drawTooltip(GuiGraphics g, int mouseX, int mouseY, List<TipLine> lines) {
        if (lines.isEmpty()) return;

        int w = 0;
        int h = 0;
        for (TipLine l : lines) {
            int lw = (l.icon() != null ? 20 : 0) + font.width(l.text());
            w = Math.max(w, lw);
            h += l.icon() != null ? 18 : 11;
        }

        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + w + 5 > this.width) x = mouseX - w - 16;
        if (y + h + 5 > this.height) y = this.height - h - 5;
        if (y < 5) y = 5;

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 400f);

        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, ZenkaiPalette.POPUP_BG);
        g.fill(x - 4, y - 5, x + w + 4, y - 4, ZenkaiPalette.BORDER_MID);
        g.fill(x - 4, y + h + 4, x + w + 4, y + h + 5, ZenkaiPalette.BORDER_MID);
        g.fill(x - 5, y - 4, x - 4, y + h + 4, ZenkaiPalette.BORDER_MID);
        g.fill(x + w + 4, y - 4, x + w + 5, y + h + 4, ZenkaiPalette.BORDER_MID);

        int cy = y;
        for (TipLine l : lines) {
            if (l.icon() != null) {
                g.renderFakeItem(l.icon(), x, cy);
                PanelText.onDark(g, font, l.text(), x + 20, cy + 4, ZenkaiPalette.TEXT);
                cy += 18;
            } else {
                PanelText.onDark(g, font, l.text(), x, cy, ZenkaiPalette.TEXT);
                cy += 11;
            }
        }

        g.pose().popPose();
    }
}