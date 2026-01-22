package com.hmc.db_renewed.client.gui.screens;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.client.gui.buttons.ArrowIconButton;
import com.hmc.db_renewed.core.network.ChooseStylePacket;
import com.hmc.db_renewed.core.network.feature.Style;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.race.UpdatePlayerVisualPacket;
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

    @Nullable private final Screen back;

    // ===== Fondo tipo common_screen =====
    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/gui/common_screen.png");
    private static final int BG_W = 256;
    private static final int BG_H = 256;

    private int leftPos;
    private int topPos;

    // ===== Estado Style =====
    private final Style[] styles = new Style[]{ Style.WARRIOR, Style.MARTIAL_ARTIST, Style.SPIRITUALIST };
    private int styleIndex = 0;

    // ===== HEX =====
    private EditBox hexBox;
    private int rgbValue = 0x33CCFF; // fallback si el texto está inválido

    // preview box
    private static final int COLOR_BOX = 70;

    public StyleSelectionScreen(@Nullable Screen back) {
        super(Component.translatable("screen." + DragonBlockRenewed.MOD_ID + ".choose_style.title"));
        this.back = back;
    }

    @Override
    protected void init() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.leftPos = (this.width - BG_W) / 2;
        this.topPos  = (this.height - BG_H) / 2;

        // Si ya tienes visual, toma el color actual como base
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        int rgb = visual.getAuraColorRgb() & 0xFFFFFF;
        this.rgbValue = rgb;

        // ========= Zona de estilo (izquierda) =========
        int styleCenterX = leftPos + 70;
        int styleTopY = topPos + 40;

        addRenderableWidget(new ArrowIconButton(styleCenterX - 55, styleTopY - 2, ArrowIconButton.Dir.LEFT, () -> {
            styleIndex = (styleIndex - 1 + styles.length) % styles.length;
        }));

        addRenderableWidget(new ArrowIconButton(styleCenterX + 45, styleTopY - 2, ArrowIconButton.Dir.RIGHT, () -> {
            styleIndex = (styleIndex + 1) % styles.length;
        }));

        // ========= Zona color (derecha) =========
        int colorAreaX = leftPos + 170;
        int colorAreaY = topPos + 55;

        // Caja HEX
        hexBox = makeHexBox(colorAreaX, colorAreaY + 30, rgb);
        addRenderableWidget(hexBox);

        // ========= Botones =========
        addRenderableWidget(Button.builder(Component.literal("X"), b -> {
            if (back != null) mc.setScreen(back);
            else mc.setScreen(null);
        }).bounds(leftPos + 12, topPos + BG_H - 28, 20, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen." + DragonBlockRenewed.MOD_ID + ".confirm"), b -> {
            onConfirm();
        }).bounds(leftPos + BG_W - 90, topPos + BG_H - 28, 78, 20).build());
    }

    private EditBox makeHexBox(int x, int y, int rgb) {
        EditBox box = new EditBox(this.font, x, y, 70, 16, Component.empty());
        box.setMaxLength(7); // permite "#RRGGBB"
        box.setValue(String.format("#%06X", rgb & 0xFFFFFF));

        box.setResponder(s -> {
            // parsea y actualiza preview si es válido
            Integer parsed = parseHexColor(s);
            if (parsed != null) {
                rgbValue = parsed;
                applyAuraClientOnly();
            }
        });

        return box;
    }

    /** Acepta "#RRGGBB" o "RRGGBB". Devuelve null si inválido. */
    private static Integer parseHexColor(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);

        // Debe ser exactamente 6 chars hex
        if (t.length() != 6) return null;

        for (int i = 0; i < 6; i++) {
            char c = t.charAt(i);
            boolean ok = (c >= '0' && c <= '9') ||
                    (c >= 'a' && c <= 'f') ||
                    (c >= 'A' && c <= 'F');
            if (!ok) return null;
        }

        try {
            return Integer.parseInt(t, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int currentRgb() {
        // si el texto es inválido, usa el último rgbValue válido
        if (hexBox != null) {
            Integer parsed = parseHexColor(hexBox.getValue());
            if (parsed != null) rgbValue = parsed;
        }
        return rgbValue & 0xFFFFFF;
    }

    /** Solo para que el preview “se vea” mientras editas. */
    private void applyAuraClientOnly() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        visual.setAuraColorRgb(currentRgb());
    }

    private void onConfirm() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1) Guardar color
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        visual.setAuraColorRgb(currentRgb());
        PacketDistributor.sendToServer(UpdatePlayerVisualPacket.from(visual));

        // 2) Elegir estilo
        Style chosen = styles[styleIndex];
        PacketDistributor.sendToServer(new ChooseStylePacket(chosen));

        mc.setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        // Fondo
        g.blit(BG, leftPos, topPos, 0, 0, BG_W, BG_H);

        // ====== Header ======
        g.drawString(this.font, this.title, leftPos + 12, topPos + 12, 0xFFFFFF);

        // ====== Zona estilo ======
        Style s = styles[styleIndex];
        String keyBase = "screen." + DragonBlockRenewed.MOD_ID + ".style." + s.name().toLowerCase();

        g.drawString(this.font, Component.translatable("screen." + DragonBlockRenewed.MOD_ID + ".style_label"),
                leftPos + 35, topPos + 38, 0xFFFFFF);

        g.drawString(this.font, Component.translatable(keyBase),
                leftPos + 35, topPos + 55, 0xFFDDAA);

        g.drawString(this.font, Component.translatable(keyBase + ".desc"),
                leftPos + 35, topPos + 75, 0xCCCCCC);

        // ====== Zona color ======
        int colorAreaX = leftPos + 170;
        int colorAreaY = topPos + 55;

        g.drawString(this.font, Component.literal("Color Ki"),
                colorAreaX, colorAreaY - 12, 0xFFFFFF);

        // Label HEX
        g.drawString(this.font, Component.literal("HEX"),
                colorAreaX, colorAreaY + 20, 0xFFFFFF);

        // Cuadro grande (preview)
        int boxX = colorAreaX;
        int boxY = topPos + 110;

        g.fill(boxX - 1, boxY - 1, boxX + COLOR_BOX + 1, boxY + COLOR_BOX + 1, 0xFFFFFFFF);
        g.fill(boxX, boxY, boxX + COLOR_BOX, boxY + COLOR_BOX, 0xFF000000 | currentRgb());

        // Texto hex abajo del cuadro (usa lo que sea válido)
        String hex = String.format("#%06X", currentRgb());
        g.drawString(this.font, Component.literal(hex), boxX, boxY + COLOR_BOX + 6, 0xFFFFFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.blit(BG, leftPos, topPos, 0, 0, BG_W, BG_H);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
