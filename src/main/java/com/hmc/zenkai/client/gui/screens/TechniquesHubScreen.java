package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Hub de la pestaña Techniques: fusiona lo que antes eran las pestañas separadas
 * KI_TECHNIQUES/PHYSICAL_TECHNIQUES en un solo botón de la barra superior que redirige aquí, con
 * 3 filas ("Ki Techniques" / "Physical Techniques" / "Master Techniques") que abren
 * {@link KiTechniquesScreen} / {@link PhysicalScreen} / {@link MasterTechniquesScreen} — mismo
 * patrón hub->subscreen que {@code AppearanceScreen} (ver su javadoc: renderHubOption(), un
 * botón grande por fila, ícono izq + etiqueta der) en vez del enum-Mode interno de
 * {@code MasterScreen}, porque las subscreens ya son ZenkaiMenuScreen completas con su propia
 * barra de pestañas, no un modo interno de esta misma instancia.
 *
 * Las filas Ki/Physical reusan los íconos VIEJOS de pestaña de icons.png (Ki: 40,20 · Physical:
 * 120,20); "Master Techniques" reusa el ícono de MasterScreen para su propia lista de técnicas
 * (0,80) — mismo concepto, "técnicas que enseña un maestro". El ícono NUEVO de la pestaña en sí
 * también es (0,80) (ver ZenkaiTab.TECHNIQUES), reutilizado una tercera vez a propósito.
 *
 * Las 3 filas se reparten TODA la altura útil del panel (mismo criterio que
 * MasterScreen.hubButtonWidth() reparte su ancho entre 3 botones) en vez de quedarse arriba con
 * hueco vacío debajo.
 *
 * Navegación de vuelta: {@link ZenkaiMenuScreen#createScreen} + su guard de no-op ya reconstruyen
 * este hub cada vez que se pulsa la pestaña Techniques, incluso estando dentro de cualquier
 * subscreen (ver ALWAYS_REOPEN en ZenkaiMenuScreen) — este hub no necesita su propio botón "Back".
 */
public class TechniquesHubScreen extends ZenkaiMenuScreen {

    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ICONS_ATLAS = 256;
    private static final int ICON_CELL = 20;
    private static final int ICON_KI_U = 40, ICON_KI_V = 20;
    private static final int ICON_PHYSICAL_U = 120, ICON_PHYSICAL_V = 20;
    private static final int ICON_MASTER_U = 0, ICON_MASTER_V = 80;

    private static final int IN_X1 = 10;
    private static final int IN_X2 = 245;
    private static final int ROWS = 3;
    private static final int HUB_GAP = 8;
    private static final int BOTTOM_MARGIN = 12;

    private int rowsTop() { return CONTENT_TOP + 8; }
    private int rowsBottom() { return BG_H - BOTTOM_MARGIN; }
    /** Alto de CADA fila, repartiendo TODA la altura libre entre las 3 — mismo criterio que
     *  MasterScreen.hubButtonWidth() para su propio hub de 3 botones, en el eje vertical. */
    private int rowH() { return (rowsBottom() - rowsTop() - (ROWS - 1) * HUB_GAP) / ROWS; }
    private int rowY(int i) { return panelTop + rowsTop() + i * (rowH() + HUB_GAP); }

    public TechniquesHubScreen() {
        super(Component.translatable(ZenkaiTab.TECHNIQUES.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.TECHNIQUES; }

    @Override
    protected void initContent() {
        // Sin widgets propios: las filas se dibujan/hit-testean a mano, igual que
        // AppearanceScreen.renderHub()/clickHub().
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);

        int x = panelLeft + IN_X1;
        int w = IN_X2 - IN_X1;
        int h = rowH();
        renderHubOption(g, x, rowY(0), w, h, ICON_KI_U, ICON_KI_V,
                Component.translatable("screen.zenkai.techniques_hub.row.ki"), mouseX, mouseY);
        renderHubOption(g, x, rowY(1), w, h, ICON_PHYSICAL_U, ICON_PHYSICAL_V,
                Component.translatable("screen.zenkai.techniques_hub.row.physical"), mouseX, mouseY);
        renderHubOption(g, x, rowY(2), w, h, ICON_MASTER_U, ICON_MASTER_V,
                Component.translatable("screen.zenkai.techniques_hub.row.master"), mouseX, mouseY);
    }

    /** Botón grande horizontal (ícono izq + etiqueta der) sobre el panel beige — mismo idioma
     *  que AppearanceScreen.renderHubOption(), sin la rama "deshabilitado" porque las filas
     *  de este hub siempre están disponibles. */
    private void renderHubOption(GuiGraphics g, int x, int y, int w, int h, int iconU, int iconV,
                                  Component label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        g.fill(x, y, x + w, y + h, hovered ? ZenkaiPalette.ROW_HOVER : ZenkaiPalette.INSET_BG);
        g.fill(x, y, x + w, y + 1, ZenkaiPalette.BORDER_IN);
        g.fill(x, y + h - 1, x + w, y + h, ZenkaiPalette.BORDER_IN);
        g.fill(x, y, x + 1, y + h, ZenkaiPalette.BORDER_IN);
        g.fill(x + w - 1, y, x + w, y + h, ZenkaiPalette.BORDER_IN);

        int iconX = x + 10;
        int iconY = y + (h - ICON_CELL) / 2;
        g.blit(ICONS_TEX, iconX, iconY, iconU, iconV, ICON_CELL, ICON_CELL, ICONS_ATLAS, ICONS_ATLAS);

        PanelText.onPanel(g, this.font, label, iconX + ICON_CELL + 8, y + h / 2 - 4,
                ZenkaiPalette.LABEL_ON_PANEL);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && clickHub(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickHub(double mouseX, double mouseY) {
        int x = panelLeft + IN_X1;
        int w = IN_X2 - IN_X1;
        if (mouseX < x || mouseX >= x + w) return false;
        int h = rowH();

        if (mouseY >= rowY(0) && mouseY < rowY(0) + h) {
            mc.setScreen(new KiTechniquesScreen());
            return true;
        }
        if (mouseY >= rowY(1) && mouseY < rowY(1) + h) {
            mc.setScreen(new PhysicalScreen());
            return true;
        }
        if (mouseY >= rowY(2) && mouseY < rowY(2) + h) {
            mc.setScreen(new MasterTechniquesScreen());
            return true;
        }
        return false;
    }
}
