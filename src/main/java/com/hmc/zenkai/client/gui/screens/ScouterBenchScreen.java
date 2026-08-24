package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.BenchPlusButton;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.client.gui.menu.ScouterBenchMenu;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.client.render_and_model_entities.blockentity.ScouterBenchRenderer;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.content.blockentity.PauseReason;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.hmc.zenkai.content.item.ScouterItem;
import com.hmc.zenkai.feature.sense.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GUI del banco de scouter. Primer integrante de la familia tecnológica.
 * DOS FONDOS EN LA MISMA PANTALLA, y de ahí sale casi lo demás:
 *   - CARCASA clara: título, resumen del aparato, etiqueta del inventario. Texto oscuro y
 *     SIN sombra (_ON_CHASSIS).
 *   - PANTALLA hundida (94,26 - 152x108): lista de mejoras y tira de trabajo. Texto claro
 *     CON sombra (_ON_SCREEN).
 * LA PANTALLA MIDE 152 Y NO 160 porque la fila de arriba manda: tres pozos de 18 más el
 * botón de tinte son 72 px, y con la pantalla en x=86 quedaban 12 para dos márgenes y tres
 * huecos, o sea pegado. Estrecharla 8 px da margen de 3 y huecos de 5.
 * DÓNDE VA EL TEXTO DE "INSTALANDO": dentro de la pantalla, en una tira al pie. La aritmética
 * manda — 108 de alto menos cinco filas de 18 deja 18 px, lo justo para una línea de 8 con
 * aire. Dos líneas no caben sin comprimir las filas, así que va en un renglón y lo que no
 * quepa lo recorta PanelText.fit; el tooltip de la barra enseña la versión entera.
 */
public class ScouterBenchScreen extends TechPanelScreen<ScouterBenchMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/scouter_bench.png");
    private static final ResourceLocation SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/slot/empty_scouter_slot.png");
    private static final ResourceLocation CURIOS_BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/slot/empty_curios_slot.png");
    private static final ResourceLocation TINT_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_tint.png");

    // ── Layout (coordenadas del PNG) ─────────────────────────────────────────
    private static final int TITLE_X = 12, TITLE_Y = 7;
    private static final int LED_X = 140, LED_Y = 4;

    /** Fila superior. Esquina del ÍTEM; el pozo de la textura va en (x-1, y-1). */
    private static final int BENCH_SLOT_X = 4, BENCH_SLOT_Y = 32;
    private static final int CURIOS_SLOT_X = 27, CURIOS_SLOT_Y = 32;
    private static final int TINT_BTN_X = 73, TINT_BTN_Y = 33;

    /** Placa hundida de la izquierda: resumen + barra. Empieza en 54 y no en 50 porque los
     *  pozos acaban en 49 y las dos zonas se tocaban. */
    private static final int PLATE_X = 11, PLATE_Y = 54, PLATE_W = 74, PLATE_H = 80;

    private static final int SUM_X = 16, SUM_Y = 59, SUM_LH = 10;
    /** Ancho útil para el texto del resumen antes de tocar el borde de la placa. */
    private static final int SUM_W = 66;

    private static final int BAR_X = 14, BAR_Y = 116, BAR_W = 68, BAR_H = 6;

    /** Canal vertical de FE, pegado al borde izquierdo. Interior del hueco de la textura. */
    private static final int EN_X = 5, EN_Y = 53, EN_W = 4, EN_H = 78;

    private static final int ROW_Y0 = 32, ROW_H = 18;
    private static final int ROW_LEFT = 98, ROW_RIGHT = 242;
    private static final int LIST_X = 104;
    private static final int SEG_W = 7, SEG_H = 4, SEG_Y_OFF = 11;
    private static final int PLUS_X = 220, PLUS_Y_OFF = 1;

    /** Tira de estado, al pie de la pantalla y por debajo de la última fila. */
    private static final int WORK_X = 104, WORK_Y = 124, WORK_W = 134;

    private static final int FOOTER_Y = 138;
    private static final int FOOTER_LEFT_X = 40;
    private static final int FOOTER_RIGHT_X = 132;

    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 162;

    // ── Popup de tinte ───────────────────────────────────────────────────────
    /** El panel ocupa la pantalla entera de mejoras: 22..134 de alto, 112 px. */
    private static final int TINT_TOP = 22, TINT_BOT = 134;
    /** El picker mide 118x98 fijos. Empieza en 24 y acaba en 122, dejando 12 para el precio.
     *  Por eso los presets NO van en fila encima: no quedaba altura para las dos cosas. */
    private static final int PICKER_X = 98, PICKER_Y = 24;
    private static final int PRESET_X = 222, PRESET_Y = 26, PRESET_SZ = 12, PRESET_GAP = 14;
    private static final int PRICE_Y = 124;

    /** Presets: verde clásico, rojo, azul y morado. Solo COLOCAN el color en el picker;
     *  aplicarlo al pulsarlos convertiría un vistazo en una compra accidental. */
    private static final int[] PRESETS = { 0x43E88D, 0xD82624, 0x2E7BD8, 0x8B3FD8 };

    private boolean tintOpen = false;
    private int tintColor = ScouterItem.DEFAULT_TINT;
    private ColorPickerWidget picker;
    private TechButton tintConfirm, tintReset;

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
    public boolean mouseClicked(double mx, double my, int button) {
        int lmx = (int) mx - leftPos;
        int lmy = (int) my - topPos;

        if (tintEnabled() && inside(lmx, lmy, TINT_BTN_X, TINT_BTN_Y, 16, 16)) {
            openTint();
            return true;
        }
        if (tintOpen) {
            for (int i = 0; i < PRESETS.length; i++) {
                int py = PRESET_Y + i * PRESET_GAP;
                if (inside(lmx, lmy, PRESET_X, py, PRESET_SZ, PRESET_SZ)) {
                    tintColor = PRESETS[i];
                    picker.setColor(0xFF000000 | tintColor);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /**
     * AbstractContainerScreen.mouseDragged existe para el reparto de ítems entre slots: hace
     * lo suyo y devuelve true SIN llamar a super, así que el evento nunca baja a los widgets
     * hijos. Sin este reenvío, el picker se puede clicar pero no arrastrar.
     */
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (tintOpen && picker != null && picker.mouseDragged(mx, my, button, dx, dy)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    /** Mismo motivo: hay que soltar el arrastre del picker o se queda enganchado. */
    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (tintOpen && picker != null) picker.mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
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
        // Con el picker abierto los controles del banco se OCULTAN, no solo se apagan: viven
        // encima de la pantalla de mejoras, que es justo donde se dibuja el panel del tinte.
        // Apagados seguirían pintándose por delante.
        for (int i = 0; i < all.length; i++) {
            BenchPlusButton b = plusButtons.get(i);
            b.visible = !tintOpen;
            b.active = !tintOpen && hasScouter && !broken && !working && canBuy(s, all[i]);
        }
        repairButton.visible = !tintOpen;
        cancelButton.visible = !tintOpen;
        repairButton.active = !tintOpen && broken && !working;
        cancelButton.active = !tintOpen && working;
        if (tintConfirm != null) tintConfirm.active = canPayTint();
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

        // El panel del tinte va AQUÍ y no en renderLabels. El orden de vanilla es
        // renderBg -> widgets -> slots -> renderLabels, así que pintarlo en renderLabels lo
        // dejaba por ENCIMA del picker, que es un widget: se veía el fondo casi opaco y el
        // picker apagado por debajo.
        if (tintOpen) drawTintBackdrop(g);
    }

    /** Fondo y marco del panel de tinte. Coordenadas ABSOLUTAS: esto corre sin la traslación
     *  al panel que sí tiene renderLabels. */
    private void drawTintBackdrop(GuiGraphics g) {
        int x0 = leftPos + ROW_LEFT - 4, x1 = leftPos + ROW_RIGHT + 4;
        int y0 = topPos + TINT_TOP, y1 = topPos + TINT_BOT;
        g.fill(x0, y0, x1, y1, ZenkaiTechPalette.TOOLTIP_BG);
        g.fill(x0, y0, x1, y0 + 1, ZenkaiTechPalette.CYAN_DARK);
        g.fill(x0, y1 - 1, x1, y1, ZenkaiTechPalette.CYAN_DARK);
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
        drawTintButton(g, localMouseX(mouseX), localMouseY(mouseY));

        if (tintOpen) {
            drawTintPanel(g);
        } else {
            drawRows(g);
            drawWorkStrip(g);
            // El velo va aquí y no en renderBg porque los ítems de los slots se dibujan ENTRE
            // renderBg y renderLabels: pintado antes, quedaría debajo del propio ítem.
            ScouterUpgrade hovered = hoveredUpgrade(localMouseX(mouseX), localMouseY(mouseY));
            if (hovered != null) highlightMaterials(g, hovered);
        }

        // Publica el color de vista previa para el renderer del bloque. -1 = sin preview.
        ScouterBenchRenderer.previewTint = tintOpen ? tintColor : -1;
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        if (tintOpen) return;   // el panel tapa la lista: nada que explicar debajo

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
        if (repairButton != null && repairButton.visible
                && repairButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, repairTip());
            return;
        }
        if (inside(lmx, lmy, TINT_BTN_X, TINT_BTN_Y, 16, 16)) {
            drawTooltip(g, mouseX, mouseY, tintButtonTip());
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
            String[] lines = Component.translatable("screen.zenkai.scouter_bench.insert")
                    .getString().split("\n");
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
                ? "∞"
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
        Component line = Component.literal(has ? "✔ " : "✘ ")
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
            Component label = Component.literal(name + " · " + lvl + "/" + u.maxLevel());

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

    /**
     * Coste de reparar. Con el scouter intacto NO enseña el precio: un botón apagado con una
     * lista de materiales al lado parece un fallo ("los tengo, ¿por qué no funciona?"), así
     * que ahí dice lo único que hace falta saber, que es que no hay nada que arreglar.
     */
    private List<TipLine> repairTip() {
        List<TipLine> lines = new ArrayList<>();
        lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.repair")
                .withStyle(ChatFormatting.BOLD)));

        ItemStack s = menu.scouter();
        if (s.isEmpty()) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.insert")
                    .withStyle(ChatFormatting.GRAY)));
            return lines;
        }
        if (!ScouterStacks.isBroken(s)) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.undamaged")
                    .withStyle(ChatFormatting.GRAY)));
            return lines;
        }

        ScouterRepairCost repair = ScouterRepairCost.get();
        for (ScouterUpgradeCost.Material m : repair.materials()) {
            lines.add(materialLine(m));
        }
        // La FE se colorea con el mismo criterio que los materiales: verde si el búfer llega,
        // rojo si no. Es lo que responde a "¿por qué sigue apagado?".
        boolean power = menu.energy() >= repair.energy();
        lines.add(TipLine.of(Component.literal(
                        String.format(Locale.ROOT, "%,d FE", repair.energy()))
                .withStyle(power ? ChatFormatting.GREEN : ChatFormatting.RED)));
        return lines;
    }

    /** Por qué el botón de tinte está apagado, que es lo único que hace falta explicar. */
    private List<TipLine> tintButtonTip() {
        List<TipLine> lines = new ArrayList<>();
        lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.tint")
                .withStyle(ChatFormatting.BOLD)));

        ItemStack s = menu.scouter();
        if (s.isEmpty()) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.insert")
                    .withStyle(ChatFormatting.GRAY)));
        } else if (ScouterStacks.isBroken(s)) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.tint_damaged")
                    .withStyle(ChatFormatting.RED)));
        } else if (menu.isWorking()) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.working")
                    .withStyle(ChatFormatting.GRAY)));
        }
        return lines;
    }

    /** Estado del banco, colgado del piloto, de la barra y del canal de energía. */
    private List<TipLine> statusTip() {
        List<TipLine> lines = new ArrayList<>();

        lines.add(TipLine.of(Component.translatable("screen.zenkai.scouter_bench.energy_title")
                .withStyle(ChatFormatting.BOLD)));
        lines.add(TipLine.of(Component.literal(String.format(Locale.ROOT, "%,d / %,d FE",
                        menu.energy(), menu.energyCapacity()))
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
            Component line = Component.literal(Component.translatable(u.nameKey()).getString()
                    + "  " + lvl + "/" + u.maxLevel());
            lines.add(TipLine.of(line.copy().withStyle(
                    lvl > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
        }
        return lines;
    }

    /** "4x Iron Ingot" con su icono, en verde si lo tienes y en rojo si no. Si el material es
     *  un TAG con varios items válidos, icono y nombre van ROTANDO cada segundo entre ellos
     *  (mismo ritmo que usa vanilla en las tiras fantasma del libro de recetas) — mostrar
     *  siempre el primero de la lista daría a entender que ese ítem concreto es el único que
     *  vale, cuando el coste en realidad acepta el tag entero. */
    private TipLine materialLine(ScouterUpgradeCost.Material m) {
        List<Item> items = m.displayItems();
        Item item = items.isEmpty() ? null : rotatingItem(items);
        ItemStack icon = item == null ? ItemStack.EMPTY : new ItemStack(item);
        Component name = item == null
                ? Component.literal(m.id().toString())
                : item.getDescription();

        boolean have = minecraft != null && minecraft.player != null
                && m.countIn(minecraft.player.getInventory()) >= m.count();

        Component text = Component.literal(m.count() + "x ").append(name)
                .withStyle(have ? ChatFormatting.GREEN : ChatFormatting.RED);
        return new TipLine(icon.isEmpty() ? null : icon, text);
    }

    /** Un item por segundo, en orden, dando la vuelta al llegar al final. */
    private static Item rotatingItem(List<Item> items) {
        if (items.size() == 1) return items.getFirst();
        int idx = (int) (Util.getMillis() / 1000L % items.size());
        return items.get(idx);
    }

    // ── Tinte ────────────────────────────────────────────────────────────────

    /**
     * Abre el picker. Manda el color DEL SCOUTER, no el guardado en config: si abres un
     * scouter rojo y el picker sale azul porque era tu último color, el preview miente sobre
     * lo que tienes delante. La config solo entra si el scouter no está teñido.
     */
    private void openTint() {
        ItemStack s = menu.scouter();
        tintColor = s.has(DataComponents.DYED_COLOR)
                ? DyedItemColor.getOrDefault(s, ScouterItem.DEFAULT_TINT) & 0xFFFFFF
                : ClientConfig.scouterLastTint();

        tintOpen = true;
        // .noFrame(): drawTintBackdrop (en renderBg) ya pinta el fondo y el borde de esta
        // zona; el marco propio del picker no lo sabe y se sale del hueco por encima, tapando
        // el título y la fila de slots (ver el javadoc de noFrame()).
        picker = new ColorPickerWidget(leftPos + PICKER_X, topPos + PICKER_Y,
                0xFF000000 | tintColor, "", c -> tintColor = c & 0xFFFFFF)
                .style(ColorPickerWidget.Style.TECH)
                .noFrame();
        addRenderableWidget(picker);

        // Ocupan el sitio de Repair y Cancel, que quedan ocultos: es el mismo pie y no tiene
        // sentido tener dos filas de botones para dos flujos que nunca coexisten.
        tintConfirm = TechButton.primary(leftPos + FOOTER_LEFT_X, topPos + FOOTER_Y,
                Component.translatable("screen.zenkai.scouter_bench.tint_apply"),
                this::confirmTint);
        tintReset = TechButton.secondary(leftPos + FOOTER_RIGHT_X, topPos + FOOTER_Y,
                Component.translatable("screen.zenkai.scouter_bench.tint_reset"),
                () -> { sendTint(0, true); closeTint(); });
        addRenderableWidget(tintConfirm);
        addRenderableWidget(tintReset);
        refresh();
    }

    private void closeTint() {
        tintOpen = false;
        if (picker != null) { removeWidget(picker); picker = null; }
        if (tintConfirm != null) removeWidget(tintConfirm);
        if (tintReset != null) removeWidget(tintReset);
        tintConfirm = null;
        tintReset = null;
        refresh();
    }

    private void confirmTint() {
        sendTint(tintColor, false);
        ClientConfig.setScouterLastTint(tintColor);
        closeTint();
    }

    private void sendTint(int rgb, boolean reset) {
        PacketDistributor.sendToServer(new ScouterTintPacket(rgb, reset));
    }

    /** Icono del botón de tinte. Atlas de 48x16: normal, hover y pulsado. */
    private void drawTintButton(GuiGraphics g, int lmx, int lmy) {
        boolean on = tintEnabled();
        boolean hover = on && inside(lmx, lmy, TINT_BTN_X, TINT_BTN_Y, 16, 16);
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, on ? 1f : 0.45f);
        g.blit(TINT_TEX, TINT_BTN_X, TINT_BTN_Y, hover ? 16 : 0, 0, 16, 16, 48, 16);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private boolean tintEnabled() {
        ItemStack s = menu.scouter();
        return !tintOpen && !s.isEmpty() && !ScouterStacks.isBroken(s) && !menu.isWorking();
    }

    /**
     * Presets y precio. El FONDO no está aquí: lo pinta drawTintBackdrop desde renderBg, o
     * taparía al picker (ver el comentario de allí).
     * Los presets van en COLUMNA a la derecha y no en fila arriba: el picker mide 98 px de
     * alto y el panel 112, así que una fila de muestras más el precio no cabían debajo.
     */
    private void drawTintPanel(GuiGraphics g) {
        for (int i = 0; i < PRESETS.length; i++) {
            int py = PRESET_Y + i * PRESET_GAP;
            g.fill(PRESET_X - 1, py - 1, PRESET_X + PRESET_SZ + 1, py + PRESET_SZ + 1,
                    ZenkaiTechPalette.STEEL_DARK);
            g.fill(PRESET_X, py, PRESET_X + PRESET_SZ, py + PRESET_SZ,
                    0xFF000000 | PRESETS[i]);
        }

        // Precio en vivo, en una sola línea. Es lo que evita tener que confirmar para saber
        // cuánto cuesta. Cada mitad se colorea por separado: puedes tener el tinte y no la FE.
        ScouterTintCost.Quote q = ScouterTintCost.get().quote(tintColor);
        boolean mats = minecraft != null && minecraft.player != null
                && q.canAfford(minecraft.player.getInventory());
        boolean power = menu.energy() >= q.energy();

        Component matText = Component.literal(q.count() + "x ")
                .append(q.item().getDescription());
        PanelText.onDark(g, font, PanelText.fit(font, matText, 90), ROW_LEFT + 2, PRICE_Y,
                mats ? ZenkaiTechPalette.OK_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        PanelText.onDark(g, font,
                Component.literal(String.format(Locale.ROOT, "%,d FE", q.energy())),
                ROW_LEFT + 98, PRICE_Y,
                power ? ZenkaiTechPalette.OK_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
    }

    private boolean canPayTint() {
        if (!tintOpen || minecraft == null || minecraft.player == null) return false;
        ScouterTintCost.Quote q = ScouterTintCost.get().quote(tintColor);
        return q.canAfford(minecraft.player.getInventory()) && menu.energy() >= q.energy();
    }

    @Override
    public void removed() {
        super.removed();
        ScouterBenchRenderer.previewTint = -1;
    }
}