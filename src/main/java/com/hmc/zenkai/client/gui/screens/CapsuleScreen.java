package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.TechHeader;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.client.gui.widgets.CapsulePreview;
import com.hmc.zenkai.content.block.CapsuleBlock;
import com.hmc.zenkai.feature.capsule.CapsuleArmPayload;
import com.hmc.zenkai.feature.capsule.CapsuleRenamePayload;
import com.hmc.zenkai.feature.capsule.CapsuleTier;
import com.hmc.zenkai.feature.capsule.OpenCapsuleScreenPayload;
import com.hmc.zenkai.util.ZenkaiNumbers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Pantalla de una cápsula Hoi-Poi colocada (Fase 6). Familia TECNOLÓGICA desde el 2026-11-24 (antes, panel beige de
 * ZenkaiPanelScreen): la cápsula es un aparato de la Capsule Corp, no una pantalla de personaje. SIN menú (no hay
 * inventario), así que no extiende TechPanelScreen y repite su vocabulario como KiMotorScreen: placa de cabecera con
 * piloto, carcasa, bandeja, TechButton y barra.
 *
 * LAYOUT (256x256, fondo == tools/gen_capsule_screen.py):
 *   visor (mitad de arriba)  lo que se guardaría, en 3D con {@link CapsulePreview}: arrastrar gira, rueda acerca, clic
 *                            derecho vuelve a la vista inicial; abajo a la derecha, el OJO (tools/gen_tech_eye.py) que
 *                            oculta o muestra el render (idea del usuario: con capturas enormes puede trabar);
 *   bandeja, izquierda       resumen (zona, bloques, mobs, espacio) y barra de ocupación;
 *   bandeja, derecha         Guardar / Cerrar y lo que se queda en el mundo (tooltip con la lista);
 *   bandeja, abajo           la nota de lo que pasa al pulsar Guardar (o por qué no se puede).
 *
 * NOMBRE (2026-11-25, para distinguir cápsulas en el control remoto): campo a la derecha de la placa del título, con el
 * mismo dibujo de placa; se manda al servidor al cerrar la pantalla (o al Guardar) si cambió. Vale igual el yunque.
 *
 * El RESUMEN lo calcula el servidor al abrir ({@link OpenCapsuleScreenPayload}; el cliente no ve dentro de los cofres).
 * El visor sale del mundo del cliente y es solo una vista previa. Cada cifra por {@link ZenkaiNumbers}.
 *
 * Render-order: variante A (renderBackground = dim de vanilla + panel; render = super, luego visor, luego texto).
 */
public class CapsuleScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/capsule.png");
    private static final int PANEL_W = 256, PANEL_H = 256;

    /**
     * Visor (incluidos) == VIEW_* de gen_capsule_screen.py. El 3D se recorta 2 px dentro (borde y anillo). Empieza en
     * {@link TechHeader#PLATE_X} para que su contorno y el de la placa del título queden alineados en vertical.
     */
    private static final int VIEW_X0 = TechHeader.PLATE_X, VIEW_Y0 = 25, VIEW_X1 = 256 - 1 - TechHeader.PLATE_X, VIEW_Y1 = 139;

    /** Ojo de mostrar/ocultar: atlas 32x16 (abierto, cerrado), en la esquina inferior derecha del visor. */
    private static final ResourceLocation EYE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/tech_eye.png");
    private static final int EYE_SIZE = 16, EYE_X = VIEW_X1 - 3 - EYE_SIZE, EYE_Y = VIEW_Y1 - 3 - EYE_SIZE;

    /** Bandeja (interior C4C2BB de y 151 a 250). */
    private static final int ROW_X = 12, ROW_RIGHT = 150, ROW_Y = 161, ROW_LH = 11;
    private static final int BAR_Y = 206, BAR_H = 4;
    private static final int BTN_X = 160, BTN_W = 84, BTN_H = 20, SAVE_Y = 160, CLOSE_Y = 184;
    private static final int SKIPPED_Y = 210;
    private static final int NOTE_X = 12, NOTE_Y = 218, NOTE_W = 232, NOTE_LH = 10;

    private final OpenCapsuleScreenPayload data;
    private final CapsulePreview preview;
    private int leftPos, topPos;
    private TechButton saveButton;
    private net.minecraft.client.gui.components.EditBox nameBox;
    /** Placa del campo de nombre (local): de NAME_X0 a la columna simétrica del borde de la placa del título. */
    private int nameX0;
    private static final int NAME_X1 = 256 - 1 - TechHeader.PLATE_X, NAME_GAP = 6;
    private boolean renameSent;

    public CapsuleScreen(OpenCapsuleScreenPayload data) {
        super(Component.translatable("item.zenkai.capsule_" + CapsuleTier.byOrdinal(data.tier()).id()));
        this.data = data;
        this.preview = new CapsulePreview(data.pos(), data.radius());
    }

    private boolean nothing() { return data.blocks() == 0 && data.mobs() == 0; }
    private boolean overBudget() { return data.used() > data.budget(); }
    private boolean canSave() { return !nothing() && !overBudget(); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        leftPos = (width - PANEL_W) / 2;
        topPos = (height - PANEL_H) / 2;
        saveButton = addRenderableWidget(new TechButton(leftPos + BTN_X, topPos + SAVE_Y, BTN_W, BTN_H,
                Component.translatable("screen.zenkai.capsule.save"), TechButton.Kind.PRIMARY, this::save));
        saveButton.active = canSave();

        nameX0 = TechHeader.plateRight(font, TechHeader.styled(title)) + 1 + NAME_GAP;
        String typed = nameBox != null ? nameBox.getValue() : data.name();
        nameBox = new net.minecraft.client.gui.components.EditBox(font, leftPos + nameX0 + 4, topPos + TechHeader.TITLE_Y,
                NAME_X1 - nameX0 - 6, 9, Component.translatable("screen.zenkai.capsule.name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(CapsuleRenamePayload.MAX_LENGTH);
        nameBox.setTextColor(ZenkaiTechPalette.TITLE);
        nameBox.setHint(Component.translatable("screen.zenkai.capsule.name_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        nameBox.setValue(typed);
        addRenderableWidget(nameBox);
        addRenderableWidget(new TechButton(leftPos + BTN_X, topPos + CLOSE_Y, BTN_W, BTN_H,
                Component.translatable("gui.cancel"), TechButton.Kind.SECONDARY, this::onClose));
    }

    private void save() {
        if (!canSave()) return;
        sendRename();
        PacketDistributor.sendToServer(new CapsuleArmPayload(data.pos()));
        onClose();
    }

    /** Manda el nombre si cambió (una sola vez: Guardar y el cierre pasan los dos por aquí). */
    private void sendRename() {
        if (renameSent || nameBox == null) return;
        String v = nameBox.getValue().strip();
        if (v.equals(data.name().strip())) return;
        renameSent = true;
        PacketDistributor.sendToServer(new CapsuleRenamePayload(data.pos(), v));
    }

    @Override
    public void removed() {
        sendRename();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        // La cápsula se rompió, se recogió o el jugador se alejó: no queda nada que guardar desde aquí.
        if (minecraft == null || minecraft.level == null || minecraft.player == null
                || !(minecraft.level.getBlockState(data.pos()).getBlock() instanceof CapsuleBlock)
                || minecraft.player.distanceToSqr(data.pos().getCenter()) > 64.0) {
            onClose();
            return;
        }
        preview.tick();
    }

    // ── Ratón: el visor se queda con lo que caiga dentro ─────────────────────

    private boolean overEye(double mx, double my) {
        return mx >= leftPos + EYE_X && mx < leftPos + EYE_X + EYE_SIZE && my >= topPos + EYE_Y && my < topPos + EYE_Y + EYE_SIZE;
    }

    private boolean inView(double mx, double my) {
        return mx >= leftPos + VIEW_X0 && mx <= leftPos + VIEW_X1 && my >= topPos + VIEW_Y0 && my <= topPos + VIEW_Y1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overEye(mx, my)) {
            preview.toggleHidden();
            if (minecraft != null) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
            }
            return true;
        }
        if (inView(mx, my)) {
            if (preview.hidden()) return true;
            if (button == 0) preview.startDrag();
            else if (button == 1) preview.reset();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && preview.dragging()) {
            preview.drag(dx, dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && preview.dragging()) {
            preview.stopDrag();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (inView(mx, my) && scrollY != 0 && !preview.hidden()) {
            preview.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(BG, leftPos, topPos, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        // Placa del nombre, aquí y no en renderContent: tiene que quedar DEBAJO del EditBox, que se dibuja con los widgets.
        // Colores de la placa del título (TechHeader); con el foco, el relleno sube al tono de las líneas de barrido.
        int x0 = leftPos + nameX0, x1 = leftPos + NAME_X1;
        int y0 = topPos + TechHeader.PLATE_Y, y1 = topPos + TechHeader.PLATE_Y + TechHeader.PLATE_H - 1;
        g.fill(x0, y0, x1 + 1, y1 + 1, 0xFF0A0C10);
        g.fill(x0 + 1, y0 + 1, x1, y1, nameBox != null && nameBox.isFocused() ? 0xFF1A202C : 0xFF12161E);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        preview.render(g, leftPos + VIEW_X0 + 2, topPos + VIEW_Y0 + 2, leftPos + VIEW_X1 - 1, topPos + VIEW_Y1 - 1,
                partialTick);

        g.pose().pushPose();
        g.pose().translate(leftPos, topPos, 0f);
        renderContent(g);
        g.pose().popPose();

        if (overEye(mouseX, mouseY)) {
            // Resalte: el borde de la placa del ojo, en cian.
            int ex = leftPos + EYE_X, ey = topPos + EYE_Y;
            g.renderOutline(ex, ey, EYE_SIZE, EYE_SIZE, ZenkaiTechPalette.CYAN_HI);
            List<FormattedCharSequence> tip = new java.util.ArrayList<>();
            tip.add(Component.translatable(preview.hidden() ? "screen.zenkai.capsule.view_show" : "screen.zenkai.capsule.view_hide")
                    .getVisualOrderText());
            tip.addAll(font.split(Component.translatable("screen.zenkai.capsule.view_toggle_tip")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), com.hmc.zenkai.client.gui.TooltipText.maxWidth(width)));
            g.renderTooltip(font, tip, mouseX, mouseY);
        } else if (data.skipped() > 0 && mouseX >= leftPos + BTN_X && mouseX < leftPos + BTN_X + BTN_W
                && mouseY >= topPos + SKIPPED_Y - 1 && mouseY < topPos + SKIPPED_Y + 9) {
            g.renderTooltip(font, font.split(Component.translatable("screen.zenkai.capsule.note_skipped"),
                    com.hmc.zenkai.client.gui.TooltipText.maxWidth(width)), mouseX, mouseY);
        }
    }

    private void renderContent(GuiGraphics g) {
        // Sin piloto (decisión del usuario, 2026-11-24): la cápsula es la excepción de la familia; un LED verde no dice nada
        // aquí, el estado ya lo dan el botón Guardar y la nota.
        TechHeader.draw(g, font, title, null);

        // Visor: aviso centrado si está oculto o vacío; si no, el rótulo de ayuda abajo a la izquierda, partido para no
        // pisar el ojo (sobre la pantalla oscura: con sombra).
        int midX = (VIEW_X0 + VIEW_X1) / 2, midY = (VIEW_Y0 + VIEW_Y1) / 2 - 4;
        if (preview.hidden()) {
            PanelText.centeredOnDark(g, font, Component.translatable("screen.zenkai.capsule.view_hidden"), midX, midY,
                    ZenkaiTechPalette.DIM_ON_SCREEN);
        } else {
            if (!preview.hasBlocks() && data.mobs() == 0) {
                PanelText.centeredOnDark(g, font, Component.translatable("screen.zenkai.capsule.view_empty"), midX, midY,
                        ZenkaiTechPalette.DIM_ON_SCREEN);
            }
            List<FormattedCharSequence> hint = font.split(Component.translatable("screen.zenkai.capsule.view_hint"),
                    EYE_X - 4 - (VIEW_X0 + 5));
            int hy = VIEW_Y1 - 11 - (hint.size() - 1) * 10;
            for (FormattedCharSequence line : hint) {
                g.drawString(font, line, VIEW_X0 + 5, hy, ZenkaiTechPalette.DIM_ON_SCREEN, true);
                hy += 10;
            }
        }
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(EYE, EYE_X, EYE_Y, preview.hidden() ? EYE_SIZE : 0, 0, EYE_SIZE, EYE_SIZE, EYE_SIZE * 2, EYE_SIZE);

        // Resumen (carcasa: oscuro y sin sombra).
        int side = data.radius() * 2 + 1;
        int y = ROW_Y;
        y = row(g, y, "screen.zenkai.capsule.zone", ZenkaiNumbers.screen(side) + " × " + ZenkaiNumbers.screen(side)
                + " × " + ZenkaiNumbers.screen(side), ZenkaiTechPalette.BODY_ON_CHASSIS);
        y = row(g, y, "screen.zenkai.capsule.blocks", ZenkaiNumbers.screen(data.blocks()), ZenkaiTechPalette.BODY_ON_CHASSIS);
        y = row(g, y, "screen.zenkai.capsule.mobs", Component.translatable("screen.zenkai.capsule.mobs_value",
                ZenkaiNumbers.screen(data.mobs()), ZenkaiNumbers.screen(data.mobVolume())).getString(),
                ZenkaiTechPalette.BODY_ON_CHASSIS);
        row(g, y, "screen.zenkai.capsule.used", ZenkaiNumbers.screen(data.used()) + " / " + ZenkaiNumbers.screen(data.budget()),
                overBudget() ? ZenkaiTechPalette.DENIED_ON_CHASSIS : ZenkaiTechPalette.OK_ON_CHASSIS);

        // Barra de ocupación (el canal siempre, como drawBar de TechPanelScreen). Roja si no cabe.
        int bw = ROW_RIGHT - ROW_X;
        g.fill(ROW_X - 1, BAR_Y - 1, ROW_RIGHT + 1, BAR_Y + BAR_H + 1, ZenkaiTechPalette.BAR_FRAME);
        g.fill(ROW_X, BAR_Y, ROW_RIGHT, BAR_Y + BAR_H, ZenkaiTechPalette.BAR_BG);
        float f = data.budget() <= 0 ? 0f : Math.min(1f, data.used() / (float) data.budget());
        int filled = Math.round(bw * f);
        if (filled > 0) {
            g.fill(ROW_X, BAR_Y, ROW_X + filled, BAR_Y + BAR_H,
                    overBudget() ? ZenkaiTechPalette.LED_RED : ZenkaiTechPalette.BAR_FILL);
        }

        // Lo que se queda en el mundo (la lista completa, en el tooltip).
        if (data.skipped() > 0) {
            PanelText.onPanel(g, font, Component.translatable("screen.zenkai.capsule.skipped",
                    ZenkaiNumbers.screen(data.skipped())), BTN_X, SKIPPED_Y, ZenkaiTechPalette.DENIED_ON_CHASSIS);
        }

        // Nota de abajo.
        Component note;
        int color = ZenkaiTechPalette.BODY_ON_CHASSIS;
        if (nothing()) {
            note = Component.translatable("screen.zenkai.capsule.note_nothing");
            color = ZenkaiTechPalette.DENIED_ON_CHASSIS;
        } else if (overBudget()) {
            note = Component.translatable("screen.zenkai.capsule.note_too_big");
            color = ZenkaiTechPalette.DENIED_ON_CHASSIS;
        } else {
            note = Component.translatable("screen.zenkai.capsule.note_leave");
        }
        int ny = NOTE_Y;
        List<FormattedCharSequence> lines = font.split(note, NOTE_W);
        for (FormattedCharSequence line : lines) {
            PanelText.onPanel(g, font, line, NOTE_X, ny, color);
            ny += NOTE_LH;
        }
    }

    /** Etiqueta a la izquierda y valor a la derecha, en la misma línea; devuelve la y siguiente. */
    private int row(GuiGraphics g, int y, String labelKey, String value, int valueColor) {
        PanelText.onPanel(g, font, Component.translatable(labelKey), ROW_X, y, ZenkaiTechPalette.LABEL_ON_CHASSIS);
        PanelText.rightOnPanel(g, font, Component.literal(value), ROW_RIGHT, y, valueColor);
        return y + ROW_LH;
    }
}
