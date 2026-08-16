package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.feature.race.RacePassives;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.feature.race.layer.RaceLayerDiscovery;
import com.hmc.zenkai.feature.race.RaceSkinSlots;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class RaceSelectionScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");

    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_wide.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    private static final int IN_X1 = 10;
    private static final int IN_Y1 = 10;
    private static final int IN_X2 = 245;
    private static final int IN_Y2 = 240;
    private static final int BTN_BAR_Y = 260;
    private static final int BTN_W     = 60;

    private static final int PAD     = 8;
    private static final int ARROW_W = 12;
    private static final int TITLE_H = 11;

    private static final int B1_TITLE_Y = ScreenTitle.CONTENT_TOP;
    private static final int B1_VALUE_Y = B1_TITLE_Y + TITLE_H;
    private static final int DIV1_Y     = B1_VALUE_Y + 14;
    private static final int B2_TITLE_Y = DIV1_Y + 6;
    private static final int B2_VALUE_Y = B2_TITLE_Y + TITLE_H;
    private static final int DIV2_Y     = B2_VALUE_Y + 14;
    /** Bajado de 50 para hacer sitio al bloque de pasiva sin recortar al jugador por la cintura. */
    private static final int PREVIEW_SIZE = 40;
    /** Alto reservado al bloque de texto inferior (raza + pasiva). */
    private static final int TEXT_BLOCK_H = 92;
    private static final int MAX_RACE_DESC_LINES    = 2;
    private static final int MAX_PASSIVE_DESC_LINES = 3;

    /** Etiqueta del bloque. Antes 0x4A3726: el valor correcto pero SIN canal alfa. */
    private static final int COLOR_TITLE  = ZenkaiPalette.LABEL_ON_PANEL;
    /** Valor elegido. Era blanco CON sombra sobre el beige: ilegible. Ahora dorado
     *  quemado y sin sombra, como el resto de valores del mod. */
    private static final int COLOR_VALUE  = ZenkaiPalette.VALUE_ON_PANEL;
    private static final int COLOR_SECTION = 0xB5401A;
    private static final int COLOR_DESC    = 0x5A4636;

    private int panelLeft, panelTop;
    private CompoundTag statsSnapshot, visualSnapshot;
    private boolean confirmed = false;
    private boolean goingNext = false;

    private final Race[] races = Race.values();
    private int raceIndex = 0;
    private boolean useCustomSkin = true;

    // Recuerda la última raza aplicada para no resetear colores en cada repintado.
    private Race lastAppliedRace = null;

    private ArrowIconButton raceLeft, raceRight;
    private ArrowIconButton skinLeft, skinRight;

    public RaceSelectionScreen() {
        super(Component.translatable("screen.zenkai.race.title"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.clearWidgets();
        this.panelLeft = (this.width  - BG_W) / 2;
        this.panelTop  = (this.height - BG_H) / 2;
        goingNext = false;

        var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());

        if (statsSnapshot == null) statsSnapshot  = stats.save();
        if (visualSnapshot == null) visualSnapshot = visual.save();

        Race cur = stats.getRace();
        for (int i = 0; i < races.length; i++) {
            if (races[i] == cur) { raceIndex = i; break; }
        }
        useCustomSkin = visual.shouldRenderRaceSkin();

        int pl = panelLeft;
        int pt = panelTop;

        int raceArrowY = pt + B1_VALUE_Y - 2;
        raceLeft  = new ArrowIconButton(pl + IN_X1 + PAD, raceArrowY,
                ArrowIconButton.Dir.LEFT,  () -> { raceIndex = (raceIndex - 1 + races.length) % races.length; applyPreview(); });
        raceRight = new ArrowIconButton(pl + IN_X2 - PAD - ARROW_W, raceArrowY,
                ArrowIconButton.Dir.RIGHT, () -> { raceIndex = (raceIndex + 1) % races.length; applyPreview(); });
        addRenderableWidget(raceLeft);
        addRenderableWidget(raceRight);

        int skinArrowY = pt + B2_VALUE_Y - 2;
        skinLeft  = new ArrowIconButton(pl + IN_X1 + PAD, skinArrowY,
                ArrowIconButton.Dir.LEFT,  () -> { useCustomSkin = !useCustomSkin; applyPreview(); });
        skinRight = new ArrowIconButton(pl + IN_X2 - PAD - ARROW_W, skinArrowY,
                ArrowIconButton.Dir.RIGHT, () -> { useCustomSkin = !useCustomSkin; applyPreview(); });
        addRenderableWidget(skinLeft);
        addRenderableWidget(skinRight);

        addRenderableWidget(new TextOnlyButton(
                pl + IN_X1, pt + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.cancel"),
                TEX_BTN, null,
                () -> { confirmed = false; goingNext = false; restoreSnapshots(); mc.setScreen(null); }));

        addRenderableWidget(new TextOnlyButton(
                pl + IN_X2 - BTN_W, pt + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.next"),
                TEX_BTN, null,
                () -> { goingNext = true; mc.setScreen(new AppearanceScreen(this, statsSnapshot, visualSnapshot)); }));

        applyPreview();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int pl = panelLeft;
        int pt = panelTop;
        Race r = races[raceIndex];
        boolean humanSaiyan = (r == Race.HUMAN || r == Race.SAIYAN);
        int cx = pl + BG_W / 2;

        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, pl, pt, 0, 0, BG_W, BG_H);

        ScreenTitle.drawAbovePanel(g, mc.font, this.title, cx, pt);

        drawCenteredNoShadow(g, Component.translatable("screen.zenkai.label.race"),
                cx, pt + B1_TITLE_Y, COLOR_TITLE);
        PanelText.centeredOnPanel(g, mc.font,
                Component.translatable("screen.zenkai.race." + r.name().toLowerCase()),
                cx, pt + B1_VALUE_Y, COLOR_VALUE);

        g.fill(pl + IN_X1 + PAD, pt + DIV1_Y, pl + IN_X2 - PAD, pt + DIV1_Y + 1, ZenkaiPalette.SEPARATOR);

        if (humanSaiyan) {
            drawCenteredNoShadow(g, Component.translatable("screen.zenkai.label.skin"),
                    cx, pt + B2_TITLE_Y, COLOR_TITLE);
            PanelText.centeredOnPanel(g, mc.font,
                    Component.translatable(useCustomSkin
                            ? "screen.zenkai.skin.custom"
                            : "screen.zenkai.skin.vanilla"),
                    cx, pt + B2_VALUE_Y, COLOR_VALUE);
        }

        g.fill(pl + IN_X1 + PAD, pt + DIV2_Y, pl + IN_X2 - PAD, pt + DIV2_Y + 1, ZenkaiPalette.SEPARATOR);

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g,
                cx - 50, pt + DIV2_Y + 6,
                cx + 50, pt + IN_Y2 - TEXT_BLOCK_H,
                PREVIEW_SIZE, 0.0625f,
                (float) mouseX, (float) mouseY, mc.player);

        int descX = pl + IN_X1 + PAD + 2;
        int descW = IN_X2 - IN_X1 - PAD * 2;
        int y = pt + IN_Y2 - TEXT_BLOCK_H + 4;
        String key = r.name().toLowerCase();

        // Bloque 1: qué es la raza.
        g.drawString(mc.font, Component.translatable("screen.zenkai.race." + key),
                descX, y, COLOR_SECTION, false);
        y += 12;
        y = drawWrapped(g, mc.font,
                Component.translatable("screen.zenkai.race." + key + ".desc").getString(),
                descX, y, descW, MAX_RACE_DESC_LINES);

        // Bloque 2: qué HACE la raza. Separado del anterior a propósito: la descripción es
        // sabor y la pasiva es mecánica, y el jugador está eligiendo por lo segundo.
        y += 4;
        g.fill(descX, y, descX + descW, y + 1, ZenkaiPalette.SEPARATOR);
        y += 5;
        g.drawString(mc.font,
                Component.translatable("screen.zenkai.race.passive_label",
                        Component.translatable(RacePassives.nameKey(r))),
                descX, y, COLOR_SECTION, false);
        y += 12;
        drawWrapped(g, mc.font, Component.translatable(RacePassives.descKey(r)).getString(),
                descX, y, descW, MAX_PASSIVE_DESC_LINES);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}
    public void markConfirmed() { this.confirmed = true; }

    /** Delega en PanelText: la regla de sombra vive en un solo sitio. */
    private void drawCenteredNoShadow(GuiGraphics g, Component text, int cx, int y, int color) {
        PanelText.centeredOnPanel(g, Minecraft.getInstance().font, text, cx, y, color);
    }

    @Override
    public void removed() {
        if (!confirmed && !goingNext) restoreSnapshots();
        super.removed();
    }

    private void applyPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        Race r = races[raceIndex];
        boolean humanSaiyan = (r == Race.HUMAN || r == Race.SAIYAN);
        stats.setRaceChosen(true);
        stats.setRace(r);

        // Al CAMBIAR de raza, fija los colores por defecto de ESA raza (cada raza tiene su look base).
        // Esto también arregla el tinte de boca/nariz (que usa skinColorRgb).
        if (r != lastAppliedRace) {
            lastAppliedRace = r;
            // Limpia overrides de capas de la raza anterior (si no, p.ej. el Detail de Namek
            // en layerColors[1] se aplicaría a la capa 1 de la nueva raza).
            visual.clearLayerColors();
            // Piel: default del JSON de la capa 0 de la raza (data-driven); fallback al valor en código.
            // Los demás colores por capa ya NO se siembran aquí: salen del JSON de cada capa
            // (layerColors vacío => cada capa usa su default) tras el clearLayerColors() de arriba.
            visual.setSkinColorRgb(seedSkinColorFor(mc.player, r));
            // Majin entra aquí con Namek/Arcosian: su color ES la identidad del personaje
            // (rosa, azul, gordo) y arrancar en tono de piel humano no tiene sentido.
            visual.setCustomSkinColor(r == Race.NAMEKIAN || r == Race.ARCOSIAN || r == Race.MAJIN);
        }

        setVisible(skinLeft, humanSaiyan);
        setVisible(skinRight, humanSaiyan);
        if (!humanSaiyan) useCustomSkin = true;
        if (humanSaiyan) {
            visual.setRenderRaceSkin(useCustomSkin);
            visual.setHideVanillaBody(useCustomSkin);
        } else {
            visual.setRenderRaceSkin(true);
            visual.setHideVanillaBody(true);
        }
    }

    /**
     * Color de piel inicial de la raza: el "color" del JSON de su capa 0
     * (<base>_layer_0.json). Si la raza no tiene capas, cae al default en código.
     * NOTA: stats.setRace(r) debe llamarse ANTES (el slot resuelve el item por la raza actual).
     */
    private static int seedSkinColorFor(Player player, Race r) {
        ItemStack body = RaceSkinSlots.getVirtualRaceArmor(player, EquipmentSlot.CHEST);
        if (body.getItem() instanceof GeoLayerArmorItem gi) {
            for (RaceLayerDiscovery.Layer L : RaceLayerDiscovery.layersFor(gi)) {
                if (L.index() == 0) return L.defaultRgb();
            }
        }
        return PlayerVisualAttachment.defaultSkinColorFor(r);
    }

    void restoreSnapshots() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        if (statsSnapshot  != null) stats.load(statsSnapshot);
        if (visualSnapshot != null) visual.load(visualSnapshot);
    }

    private static void setVisible(ArrowIconButton w, boolean v) {
        if (w == null) return; w.visible = v; w.active = v;
    }

    private String[] wrapText(String text, net.minecraft.client.gui.Font font, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.isEmpty() ? w : line + " " + w;
            if (font.width(test) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString()); line = new StringBuilder(w);
            } else line = new StringBuilder(test);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.toArray(new String[0]);
    }

    /** Pinta el texto envuelto y devuelve la Y siguiente. El tope de líneas no es estético:
     *  sin él una traducción larga se sale por debajo del panel y no hay forma de leerla. */
    private int drawWrapped(GuiGraphics g, net.minecraft.client.gui.Font font, String text,
                            int x, int y, int maxWidth, int maxLines) {
        String[] lines = wrapText(text, font, maxWidth);
        int n = Math.min(lines.length, maxLines);
        for (int i = 0; i < n; i++) {
            String s = (i == maxLines - 1 && lines.length > maxLines) ? lines[i] + "..." : lines[i];
            g.drawString(font, Component.literal(s), x, y + i * 10, COLOR_DESC, false);
        }
        return y + n * 10;
    }

    @Override public boolean isPauseScreen() { return false; }
}