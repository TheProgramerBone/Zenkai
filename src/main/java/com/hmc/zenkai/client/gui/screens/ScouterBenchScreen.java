package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.BenchPlusButton;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.client.gui.menu.ScouterBenchMenu;
import com.hmc.zenkai.content.blockentity.PauseReason;
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
 *
 * DOS FONDOS EN LA MISMA PANTALLA, y de ahí sale casi todo lo demás:
 *   - CARCASA clara: título, resumen del aparato, etiqueta del inventario. Texto oscuro y
 *     SIN sombra (_ON_CHASSIS).
 *   - PANTALLA hundida (86,18 - 160x110): lista de mejoras y tira de trabajo. Texto claro
 *     CON sombra (_ON_SCREEN).
 *
 * DÓNDE VA EL TEXTO DE "INSTALANDO", que es lo que más cuesta encajar: dentro de la pantalla,
 * en una tira al pie. La aritmética manda — la pantalla mide 110 px de alto y cinco filas de
 * 19 ocupan 95, así que quedan 10 para una sola línea de 8. Dos líneas pedirían 20 y no los
 * hay sin bajar la fila a 17 o meter los botones en la bandeja del inventario. Por eso el
 * texto es "INSTALLING RANGE · 12S" en un renglón y no dos: entero y sin recortar, que era
 * el problema real.
 */
public class ScouterBenchScreen extends TechPanelScreen<ScouterBenchMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/scouter_bench.png");
    private static final ResourceLocation SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/slot/empty_scouter_slot.png");
    private static final ResourceLocation CURIOS_BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/slot/empty_curios_slot.png");

    // ── Layout (coordenadas del PNG) ─────────────────────────────────────────
    private static final int TITLE_X = 12, TITLE_Y = 7;
    private static final int LED_X = 140, LED_Y = 4;

    /** Esquina del ÍTEM del slot del banco. El pozo está en (x-1, y-1). */
    private static final int BENCH_SLOT_X = 18, BENCH_SLOT_Y = 32;
    private static final int CURIOS_SLOT_X = 40, CURIOS_SLOT_Y = 32;

    /** Placa hundida de la izquierda: resumen + barra. */
    private static final int PLATE_X = 11, PLATE_Y = 50, PLATE_W = 74, PLATE_H = 84;

    private static final int SUM_X = 16, SUM_Y = 55, SUM_LH = 10;
    /** Ancho útil para el texto del resumen antes de tocar el borde de la placa. */
    private static final int SUM_W = 66;

    private static final int BAR_X = 14, BAR_Y = 116, BAR_W = 68, BAR_H = 6;

    /** Canal vertical de FE, pegado al borde izquierdo. Interior del hueco de la textura. */
    private static final int EN_X = 5, EN_Y = 53, EN_W = 4, EN_H = 78;

    // La pantalla arranca en y=26 y no en 18: el bisel del piloto llega hasta y=22 y la
    // lista quedaba pegada a él y al título. Los 8 px que baja se pagan con 108 de alto en
    // vez de 110 y con la fila a 18 en vez de 19.
    private static final int ROW_Y0 = 32, ROW_H = 18;
    private static final int ROW_LEFT = 90, ROW_RIGHT = 242;
    private static final int LIST_X = 96;
    private static final int SEG_W = 7, SEG_H = 4, SEG_Y_OFF = 11;
    private static final int PLUS_X = 220, PLUS_Y_OFF = 1;

    /** Tira de estado, al pie de la pantalla y por debajo de la última fila. */
    private static final int WORK_X = 96, WORK_Y = 124, WORK_W = 146;

    private static final int FOOTER_Y = 138;
    private static final int FOOTER_LEFT_X = 40;
    private static final int FOOTER_RIGHT_X = 132;

    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 162;

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

        // Fantasmas de hueco vacío. NO van horneados en scouter_bench.png porque el fondo no
        // sabe si el hueco está ocupado: pintados ahí, asomarían por detrás del scouter
        // puesto. El del casco no está aquí — lo dibuja vanilla desde Slot.setBackground.
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        if (menu.scouter().isEmpty()) {
            g.blit(SLOT_BG, leftPos + BENCH_SLOT_X, topPos + BENCH_SLOT_Y, 0, 0, 16, 16, 16, 16);
        }
        if (menu.slots.get(ScouterBenchMenu.SLOT_CURIOS).getItem().isEmpty()) {
            g.blit(CURIOS_BG, leftPos + CURIOS_SLOT_X, topPos + CURIOS_SLOT_Y, 0, 0, 16, 16, 16, 16);
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
        // Verde cuando hay corriente, rojo cuando el trabajo está parado justo por eso.
        drawVBar(g, EN_X, EN_Y, EN_W, EN_H, menu.energyFraction(),
                menu.pauseReason() == PauseReason.ENERGY
                        ? ZenkaiTechPalette.LED_RED
                        : ZenkaiTechPalette.LED_GREEN_MID);
        drawRows(g);
        drawWorkStrip(g);

        // El velo va aquí y no en renderBg porque los ítems de los slots se dibujan ENTRE
        // renderBg y renderLabels: pintado antes, quedaría debajo del propio ítem.
        ScouterUpgrade hovered = hoveredUpgrade(localMouseX(mouseX), localMouseY(mouseY));
        if (hovered != null) highlightMaterials(g, hovered);
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        int lmx = localMouseX(mouseX);
        int lmy = localMouseY(mouseY);

        ScouterUpgrade u = hoveredUpgrade(lmx, lmy);
        if (u != null) {
            drawTooltip(g, mouseX, mouseY, upgradeTip(u));
            return;
        }
        if (inside(lmx, lmy, LED_X, LED_Y, LED_SIZE, LED_SIZE)
                || inside(lmx, lmy, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)
                || inside(lmx, lmy, EN_X - 1, EN_Y - 1, EN_W + 2, EN_H + 2)) {
            drawTooltip(g, mouseX, mouseY, statusTip());
            return;
        }
        // El resumen se recorta si el nombre es largo; el tooltip enseña la versión entera.
        if (inside(lmx, lmy, PLATE_X, PLATE_Y, PLATE_W, PLATE_H) && !menu.scouter().isEmpty()) {
            drawTooltip(g, mouseX, mouseY, summaryTip());
            return;
        }
        drawEmptySlotTip(g, mouseX, mouseY, lmx, lmy);
    }

    /**
     * Qué va en cada hueco. SOLO con el hueco vacío: si hay un ítem, el tooltip del ítem ya
     * lo explica y dos tooltips a la vez se pisan.
     */
    private void drawEmptySlotTip(GuiGraphics g, int mouseX, int mouseY, int lmx, int lmy) {
        int[] indices = { ScouterBenchMenu.SLOT_BENCH, ScouterBenchMenu.SLOT_CURIOS,
                ScouterBenchMenu.SLOT_HELMET };
        String[] keys = { "screen.zenkai.scouter_bench.slot.bench",
                "screen.zenkai.scouter_bench.slot.curios",
                "screen.zenkai.scouter_bench.slot.helmet" };

        for (int i = 0; i < indices.length; i++) {
            Slot slot = menu.slots.get(indices[i]);
            if (!slot.getItem().isEmpty()) continue;
            if (!inside(lmx, lmy, slot.x, slot.y, 16, 16)) continue;

            List<TipLine> lines = new ArrayList<>();
            lines.add(TipLine.of(Component.translatable(keys[i]).withStyle(ChatFormatting.BOLD)));
            lines.add(TipLine.of(Component.translatable(keys[i] + ".desc")
                    .withStyle(ChatFormatting.GRAY)));
            drawTooltip(g, mouseX, mouseY, lines);
            return;
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Placa izquierda: qué es capaz de hacer HOY el scouter que hay en el banco. */
    private void drawSummary(GuiGraphics g) {
        ItemStack s = menu.scouter();

        if (s.isEmpty()) {
            String[] lines = Component.translatable("screen.zenkai.scouter_bench.insert").getString().split("\n");
            int y = SUM_Y;
            for (String line : lines) {
                PanelText.onPanel(g, font,
                        PanelText.fit(font, Component.literal(line), SUM_W),
                        SUM_X, y, ZenkaiTechPalette.DENIED_ON_CHASSIS);
                y += SUM_LH;
            }
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
                ? "\u221E"
                : String.format(Locale.ROOT, "%,d", ScouterUpgrade.plCapFor(capLvl));
        PanelText.onPanel(g, font,
                Component.translatable("screen.zenkai.scouter_bench.pl", cap),
                SUM_X, y, ZenkaiTechPalette.LABEL_ON_CHASSIS);
        y += SUM_LH + 2;

        y = drawUnlock(g, ScouterUpgrade.DRAGON_RADAR, up, y);
        y = drawUnlock(g, ScouterUpgrade.ANALYZER, up, y);
        drawUnlock(g, ScouterUpgrade.AREA_SCANNER, up, y);
    }

    /**
     * Una función desbloqueada. El nombre se RECORTA al ancho de la placa: "Dragon Radar" con
     * su marca pide 83 px y la placa da 66, así que sin fit se metía por debajo de la pantalla
     * de mejoras. El nombre entero está a un hover de distancia, en summaryTip().
     */
    private int drawUnlock(GuiGraphics g, ScouterUpgrade u, ScouterUpgrades up, int y) {
        boolean has = up.has(u);
        Component line = Component.literal(has ? "\u2714 " : "\u2718 ")
                .append(Component.translatable(u.nameKey()));
        PanelText.onPanel(g, font, PanelText.fit(font, line, SUM_W), SUM_X, y,
                has ? ZenkaiTechPalette.OK_ON_CHASSIS : ZenkaiTechPalette.MUTED_ON_CHASSIS);
        return y + SUM_LH;
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
            if (i < all.length - 1) {
                g.fill(ROW_LEFT, y + ROW_H - 1, ROW_RIGHT, y + ROW_H,
                        ZenkaiTechPalette.ROW_SEP);
            }

            int lvl = empty ? 0 : up.level(u);
            boolean maxed = !empty && lvl >= u.maxLevel();

            String name = Component.translatable(u.nameKey()).getString().toUpperCase(Locale.ROOT);
            Component label = Component.literal(name + " \u00B7 " + lvl + "/" + u.maxLevel());

            int color = empty ? ZenkaiTechPalette.DIM_ON_SCREEN
                    : maxed ? ZenkaiTechPalette.MAXED_ON_SCREEN
                    : ZenkaiTechPalette.TEXT_ON_SCREEN;
            PanelText.onDark(g, font,
                    PanelText.fit(font, label, PLUS_X - LIST_X - 6), LIST_X, y + 2, color);

            drawSegments(g, LIST_X, y + SEG_Y_OFF, u.maxLevel(), lvl, SEG_W, SEG_H, empty);
        }
    }

    /** Tira al pie de la pantalla: qué está haciendo el banco y cuánto le queda. */
    private void drawWorkStrip(GuiGraphics g) {
        if (!menu.isWorking()) return;

        Component text;
        int color;
        if (menu.isPaused()) {
            text = Component.translatable(menu.pauseReason().langKey());
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else {
            String head = Component.translatable("screen.zenkai.scouter_bench.installing",
                    jobName().getString()).getString().toUpperCase(Locale.ROOT);
            text = Component.literal(head + " · " + menu.secondsLeft() + "S");
            color = ZenkaiTechPalette.CYAN_HI;
        }
        PanelText.onDark(g, font, PanelText.fit(font, text, WORK_W), WORK_X, WORK_Y, color);
    }

    /** Nombre de lo que se está haciendo, sin verbo. */
    private Component jobName() {
        int job = menu.job();
        if (job == ScouterBenchBlockEntity.JOB_REPAIR) {
            return Component.translatable("screen.zenkai.scouter_bench.repair");
        }
        ScouterUpgrade[] all = ScouterUpgrade.values();
        if (job >= 0 && job < all.length) return Component.translatable(all[job].nameKey());
        return Component.empty();
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

    // ── Tooltips ─────────────────────────────────────────────────────────────

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

    private List<TipLine> upgradeTip(ScouterUpgrade u) {
        ItemStack s = menu.scouter();
        List<TipLine> lines = new ArrayList<>();
        lines.add(TipLine.of(Component.translatable(u.nameKey()).withStyle(ChatFormatting.BOLD)));
        lines.add(TipLine.of(Component.translatable(u.descKey()).withStyle(ChatFormatting.GRAY)));

        int next = s.isEmpty() ? 1 : ScouterStacks.upgrades(s).nextLevel(u);
        if (next < 0) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.maxed")
                    .withStyle(ChatFormatting.AQUA)));
            return lines;
        }

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
        return lines;
    }

    /** Estado del banco, colgado del piloto, de la barra y del canal de energía. */
    private List<TipLine> statusTip() {
        List<TipLine> lines = new ArrayList<>();

        lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.energy_title")
                .withStyle(ChatFormatting.BOLD)));
        lines.add(TipLine.of(Component.literal(
                        String.format(Locale.ROOT, "%,d / %,d FE", menu.energy(), menu.energyCapacity()))
                .withStyle(ChatFormatting.GRAY)));

        if (!menu.isWorking()) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.idle")
                    .withStyle(ChatFormatting.DARK_GRAY)));
            return lines;
        }

        lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.installing",
                jobName().getString())));
        PauseReason r = menu.pauseReason();
        lines.add(r.isPaused()
                ? TipLine.of(Component.translatable(r.langKey()).withStyle(ChatFormatting.RED))
                : TipLine.of(Component.translatable("screen.zenkai.scouter_bench.remaining",
                menu.secondsLeft()).withStyle(ChatFormatting.GRAY)));
        return lines;
    }

    /** El resumen entero, sin los recortes que impone el ancho de la placa. */
    private List<TipLine> summaryTip() {
        ItemStack s = menu.scouter();
        ScouterUpgrades up = ScouterStacks.upgrades(s);
        List<TipLine> lines = new ArrayList<>();

        for (ScouterUpgrade u : ScouterUpgrade.values()) {
            int lvl = up.level(u);
            Component line = Component.literal(
                    Component.translatable(u.nameKey()).getString() + "  " + lvl + "/" + u.maxLevel());
            lines.add(TipLine.of(line.copy().withStyle(
                    lvl > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
        }
        return lines;
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