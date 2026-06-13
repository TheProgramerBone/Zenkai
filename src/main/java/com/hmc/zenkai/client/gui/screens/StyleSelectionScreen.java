package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.core.network.ChooseStylePacket;
import com.hmc.zenkai.core.network.feature.Style;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.network.feature.race.UpdatePlayerVisualPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StyleSelectionScreen extends Screen {

    // ── Assets ───────────────────────────────────────────────────────────────
    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");

    private static final int BG_W = 320;
    private static final int BG_H = 240;

    // Layout interno
    // Columna izquierda: info del estilo (x: 10–180)
    // Columna derecha:   color del Ki   (x: 195–310)
    private static final int COL_LEFT  = 12;
    private static final int COL_RIGHT = 195;
    private static final int COLOR_BOX = 60;

    // ── Estado ───────────────────────────────────────────────────────────────
    @Nullable private final Screen back;
    private int leftPos, topPos;

    private final Style[] styles = Style.values();
    private int styleIndex = 0;

    private EditBox hexBox;
    private int rgbValue = 0x33CCFF;

    public StyleSelectionScreen(@Nullable Screen back) {
        super(Component.translatable("screen.db_renewed.choose_style.title"));
        this.back = back;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.leftPos = (this.width  - BG_W) / 2;
        this.topPos  = (this.height - BG_H) / 2;

        // Tomar color actual del visual attachment
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        this.rgbValue = visual.getAuraColorRgb() & 0xFFFFFF;

        // Inicializar styleIndex si ya eligió estilo
        var stats = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        Style current = stats.getStyle();
        for (int i = 0; i < styles.length; i++) {
            if (styles[i] == current) { styleIndex = i; break; }
        }

        // ── Flechas de estilo ────────────────────────────────────────────────
        int arrowY = topPos + 48;
        addRenderableWidget(new ArrowIconButton(
                leftPos + COL_LEFT, arrowY,
                ArrowIconButton.Dir.LEFT,
                () -> styleIndex = (styleIndex - 1 + styles.length) % styles.length
        ));
        addRenderableWidget(new ArrowIconButton(
                leftPos + COL_LEFT + 150, arrowY,
                ArrowIconButton.Dir.RIGHT,
                () -> styleIndex = (styleIndex + 1) % styles.length
        ));

        // ── HEX input ────────────────────────────────────────────────────────
        hexBox = new EditBox(
                this.font,
                leftPos + COL_RIGHT,
                topPos + 80,
                70, 16,
                Component.empty()
        );
        hexBox.setMaxLength(7);
        hexBox.setValue(String.format("#%06X", rgbValue));
        hexBox.setResponder(s -> {
            Integer parsed = parseHex(s);
            if (parsed != null) {
                rgbValue = parsed;
                applyColorPreview();
            }
        });
        addRenderableWidget(hexBox);

        // ── Botones de acción ────────────────────────────────────────────────
        addRenderableWidget(Button.builder(
                Component.translatable("screen.db_renewed.back"),
                b -> { if (back != null) mc.setScreen(back); else mc.setScreen(null); }
        ).bounds(leftPos + 8, topPos + BG_H - 26, 50, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.db_renewed.confirm"),
                b -> onConfirm()
        ).bounds(leftPos + BG_W - 60, topPos + BG_H - 26, 52, 20).build());
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        Minecraft mc = Minecraft.getInstance();
        Style s = styles[styleIndex];
        String styleKey = "screen.db_renewed.style." + s.name().toLowerCase();

        // ── Columna izquierda: estilo ─────────────────────────────────────────
        int lx = leftPos + COL_LEFT;

        // Título de sección
        g.drawString(mc.font,
                Component.translatable("screen.db_renewed.label.style"),
                lx, topPos + 14, 0xAAAAAA);

        // Nombre del estilo (grande, centrado entre flechas)
        g.drawCenteredString(mc.font,
                Component.translatable(styleKey),
                leftPos + COL_LEFT + 82, topPos + 52, 0xFFDDAA);

        // Descripción (hasta ~160px de ancho, puede necesitar wrap manual)
        String[] descLines = getDescLines(styleKey + ".desc");
        for (int i = 0; i < descLines.length; i++) {
            g.drawString(mc.font,
                    Component.literal(descLines[i]),
                    lx, topPos + 75 + i * 11, 0xCCCCCC);
        }

        // Divisor vertical
        g.fill(leftPos + COL_RIGHT - 8, topPos + 10,
                leftPos + COL_RIGHT - 7, topPos + BG_H - 10,
                0x88FFFFFF);

        // ── Columna derecha: color de Ki ──────────────────────────────────────
        int rx = leftPos + COL_RIGHT;

        g.drawString(mc.font,
                Component.translatable("screen.db_renewed.label.ki_color"),
                rx, topPos + 14, 0xAAAAAA);

        g.drawString(mc.font,
                Component.translatable("screen.db_renewed.label.hex"),
                rx, topPos + 68, 0xFFFFFF);

        // Cuadro de preview del color
        int boxY = topPos + 105;
        g.fill(rx - 1,           boxY - 1,
                rx + COLOR_BOX + 1, boxY + COLOR_BOX + 1,
                0xFFFFFFFF); // borde blanco
        g.fill(rx, boxY,
                rx + COLOR_BOX, boxY + COLOR_BOX,
                0xFF000000 | currentRgb()); // color actual

        // Valor hex debajo del cuadro
        g.drawString(mc.font,
                Component.literal(String.format("#%06X", currentRgb())),
                rx, boxY + COLOR_BOX + 6, 0x888888);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, leftPos, topPos, 0, 0, BG_W, BG_H);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Divide la descripción en líneas de máximo ~22 caracteres para que
     * quepan en la columna izquierda sin salirse del panel.
     */
    private String[] getDescLines(String key) {
        String raw = Component.translatable(key).getString();
        if (raw.length() <= 28) return new String[]{ raw };

        // Corte simple por palabras
        String[] words = raw.split(" ");
        StringBuilder line = new StringBuilder();
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String w : words) {
            if (line.length() + w.length() > 26) {
                lines.add(line.toString().trim());
                line = new StringBuilder();
            }
            line.append(w).append(" ");
        }
        if (!line.isEmpty()) lines.add(line.toString().trim());
        return lines.toArray(new String[0]);
    }

    private int currentRgb() {
        if (hexBox != null) {
            Integer p = parseHex(hexBox.getValue());
            if (p != null) rgbValue = p;
        }
        return rgbValue & 0xFFFFFF;
    }

    private void applyColorPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.getData(DataAttachments.PLAYER_VISUAL.get()).setAuraColorRgb(currentRgb());
    }

    private static Integer parseHex(String s) {
        if (s == null) return null;
        String t = s.trim().replaceFirst("^#", "");
        if (t.length() != 6) return null;
        try { return Integer.parseInt(t, 16) & 0xFFFFFF; }
        catch (Exception e) { return null; }
    }

    private void onConfirm() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        visual.setAuraColorRgb(currentRgb());
        PacketDistributor.sendToServer(UpdatePlayerVisualPacket.from(visual));
        PacketDistributor.sendToServer(new ChooseStylePacket(styles[styleIndex]));
        mc.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}