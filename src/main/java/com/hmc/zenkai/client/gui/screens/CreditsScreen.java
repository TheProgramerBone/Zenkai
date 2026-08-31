package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.credits.ZenkaiUiCredits;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.registry.ModDataMaps;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña de créditos: centraliza en un solo sitio el reconocimiento de quien hizo el mod,
 * repartido hoy en dos almacenes que no tienen por qué mezclarse:
 *   Modelos     -&gt; ModDataMaps.MODEL_CREDITS (DataMap sobre Item, ya existente, sin tocar aquí).
 *   UI/Íconos/Animaciones -&gt; ZenkaiUiCredits (registro Java puramente cliente, ver esa clase
 *                             para por qué NO es un DataMap ni un datapack).
 * Reemplaza al antiguo tooltip de ítem por autor (ZenkaiCreditsTooltip, eliminado junto con su
 * opción de ClientConfig): esta pantalla LEE el mismo DataMap, agregado junto a las otras tres
 * categorías, así que el dato de créditos de modelos no se pierde al quitar el tooltip.
 */
public class CreditsScreen extends ZenkaiMenuScreen {

    private static final int MARGIN = 20;
    private static final int CAT_BTN_H = 20;
    private static final int ROW_H = 26;
    private static final int ICON_SIZE = 16;
    private static final int SCROLLBAR_W = 4;

    /** Mismo atlas que TabIconButton (icons.png, 256x256, grid 20px) — para pintar la miniatura
     *  real de una entrada de Category.ICONS, igual que Modelos pinta el ItemStack. */
    private static final ResourceLocation ICONS_ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ATLAS_W = 256, ATLAS_H = 256;
    private static final int ATLAS_ICON_SIZE = 20;

    private enum Category {
        MODELS("screen.zenkai.credits.category.models"),
        UI("screen.zenkai.credits.category.ui"),
        ICONS("screen.zenkai.credits.category.icons"),
        ANIMATIONS("screen.zenkai.credits.category.animations");

        private final String key;
        Category(String key) { this.key = key; }
        String titleKey() { return key; }
    }

    /** atlasU/atlasV: celda de ICONS_ATLAS a pintar, -1 si esta fila no tiene una (Modelos usa
     *  icon en su lugar; UI/Animaciones no tienen miniatura). Nunca las dos formas a la vez. */
    private record Row(@Nullable ItemStack icon, int atlasU, int atlasV,
                        String name, String author, String detail) {}
    private record RowArea(int index, int y) {}

    private Category category = Category.MODELS;
    private final List<Row> rows = new ArrayList<>();
    private final List<RowArea> rowAreas = new ArrayList<>();
    private int scroll = 0;

    public CreditsScreen() {
        super(Component.translatable("screen.zenkai.credits.title"));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.CREDITS; }

    // ── Geometría ────────────────────────────────────────────────────────────

    private int listTop()    { return panelTop + CONTENT_TOP + CAT_BTN_H + 8; }
    private int listHeight() { return BG_H - (listTop() - panelTop) - MARGIN; }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }
    private int maxScroll()  { return Math.max(0, rows.size() - visibleRows()); }
    private int rowLeft()    { return panelLeft + MARGIN; }
    private int rowRight()   { return panelLeft + BG_W - MARGIN; }

    // ── Construcción ─────────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        rebuildRows();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        addCategoryButtons();
    }

    private void addCategoryButtons() {
        Category[] cats = Category.values();
        int totalW = BG_W - 2 * MARGIN;
        int w = totalW / cats.length;
        int y = panelTop + CONTENT_TOP;
        int x = panelLeft + MARGIN;
        for (Category c : cats) {
            TextOnlyButton b = new TextOnlyButton(x, y, w, CAT_BTN_H,
                    Component.translatable(c.titleKey()), () -> selectCategory(c));
            b.onPanel();
            b.selected(category == c);
            addRenderableWidget(b);
            x += w;
        }
    }

    private void selectCategory(Category c) {
        if (category == c) return;
        category = c;
        scroll = 0;
        // init() es final en la base: refrescar los botones (para que el nuevo "selected"
        // se dibuje) pasa por rehacer los widgets, mismo patrón que ClientConfigScreen.rebuild().
        this.clearWidgets();
        this.rebuildWidgets();
    }

    /** Recalcula las filas visibles de la categoría actual. NO toca scroll (lo hace el llamador). */
    private void rebuildRows() {
        rows.clear();
        switch (category) {
            case MODELS -> {
                for (Item item : BuiltInRegistries.ITEM) {
                    ItemStack stack = new ItemStack(item);
                    ModDataMaps.ModelCredit c = stack.getItemHolder().getData(ModDataMaps.MODEL_CREDITS);
                    if (c == null) continue;
                    rows.add(new Row(stack, -1, -1, stack.getHoverName().getString(), c.author(), c.detail()));
                }
            }
            case UI -> addFrom(ZenkaiUiCredits.Category.UI);
            case ICONS -> addFrom(ZenkaiUiCredits.Category.ICONS);
            case ANIMATIONS -> addFrom(ZenkaiUiCredits.Category.ANIMATIONS);
        }
    }

    private void addFrom(ZenkaiUiCredits.Category c) {
        for (var e : ZenkaiUiCredits.byCategory(c)) {
            rows.add(new Row(null, e.iconU(), e.iconV(), e.displayName(), e.author(), e.detail()));
        }
    }

    // ── Interacción ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Las filas son solo dibujo (sin widget propio), así que desplazar el scroll no
        // necesita rehacer nada: el próximo render() ya lee el campo actualizado.
        if (maxScroll() > 0 && scrollY != 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);

        rowAreas.clear();
        int shown = Math.min(visibleRows(), rows.size() - scroll);
        for (int i = 0; i < shown; i++) {
            int index = scroll + i;
            int rowY = listTop() + i * ROW_H;
            rowAreas.add(new RowArea(index, rowY));
            Row row = rows.get(index);

            if ((index & 1) == 0) {
                g.fill(rowLeft() - 4, rowY, rowRight() + 4, rowY + ROW_H - 2, ZenkaiPalette.ROW_BAND);
            }

            int textX = rowLeft();
            if (row.icon() != null) {
                g.renderFakeItem(row.icon(), rowLeft(), rowY + (ROW_H - ICON_SIZE) / 2 - 1);
                textX = rowLeft() + ICON_SIZE + 6;
            } else if (row.atlasU() >= 0 && row.atlasV() >= 0) {
                g.blit(ICONS_ATLAS, rowLeft(), rowY + (ROW_H - ATLAS_ICON_SIZE) / 2,
                        row.atlasU(), row.atlasV(), ATLAS_ICON_SIZE, ATLAS_ICON_SIZE, ATLAS_W, ATLAS_H);
                textX = rowLeft() + ATLAS_ICON_SIZE + 6;
            }
            int textW = rowRight() - textX;

            Component name = PanelText.fit(this.font, Component.literal(row.name()), textW);
            PanelText.onPanel(g, this.font, name, textX, rowY + 3, ZenkaiPalette.LABEL_ON_PANEL);

            Component author = PanelText.fit(this.font, Component.literal(authorLine(row)), textW);
            PanelText.onPanel(g, this.font, author, textX, rowY + 14, ZenkaiPalette.MUTED_ON_PANEL);
        }

        if (rows.isEmpty()) {
            PanelText.centeredOnPanel(g, this.font, Component.translatable("screen.zenkai.credits.empty"),
                    panelLeft + BG_W / 2, listTop() + 10, ZenkaiPalette.MUTED_ON_PANEL);
        }

        drawScrollbar(g);
        renderRowTooltip(g, mx, my);
    }

    private static String authorLine(Row row) {
        return row.detail().isEmpty() ? row.author() : row.author() + " — " + row.detail();
    }

    /** Nombre/autor completos al pasar el ratón, para cuando PanelText.fit los recorta. */
    private void renderRowTooltip(GuiGraphics g, int mx, int my) {
        if (mx < rowLeft() || mx > rowRight()) return;
        for (RowArea area : rowAreas) {
            if (my < area.y() || my >= area.y() + ROW_H - 2) continue;
            Row row = rows.get(area.index());

            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(row.name()));
            lines.add(Component.literal(authorLine(row)).withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(this.font, lines, mx, my);
            return;
        }
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;
        int x = panelLeft + BG_W - 10;
        int top = listTop(), h = visibleRows() * ROW_H;
        g.fill(x, top, x + SCROLLBAR_W, top + h, ZenkaiPalette.BAR_BG);
        int thumbH = Math.max(12, h * visibleRows() / rows.size());
        int thumbY = top + (h - thumbH) * scroll / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.VALUE_ON_PANEL);
    }
}
