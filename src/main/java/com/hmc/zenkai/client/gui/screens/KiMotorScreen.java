package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.compat.create.KiMotorActionPacket;
import com.hmc.zenkai.compat.create.KiMotorControl;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.feature.transport.SideMode;
import com.hmc.zenkai.feature.sideconfig.SideConfigurable;
import com.hmc.zenkai.client.gui.widgets.SideConfigWidget;
import com.hmc.zenkai.util.ZenkaiNumbers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla del Motor de Ki (compat con Create). Familia TECNOLÓGICA, pero SIN menú: el motor no
 * tiene inventario, así que no extiende TechPanelScreen (que es un AbstractContainerScreen) y
 * repite aquí, en pequeño, su vocabulario (título sobre la placa, piloto, barra de FE y tanque).
 *
 * QUÉ SALE DE DÓNDE. El estado se lee cada frame del block entity del CLIENTE, que el servidor
 * mantiene al día con sendData (al cambiar un ajuste y cada 10 ticks si se movió el FE, el tanque,
 * la carga o el ritmo). Cada botón solo manda un KiMotorActionPacket; el cambio se ve cuando
 * vuelve el sendData, así la pantalla nunca enseña un ajuste que el servidor rechazó.
 * Solo nombra KiMotorControl (sin clases de Create).
 *
 * LAYOUT (256x256, fondo == tools/gen_ki_motor_screen.py):
 *   arriba izquierda   barra de FE y tanque de ki (pozos heredados de la cámara de contención);
 *   columna central    modo, sentido y las cuatro velocidades;
 *   pantalla derecha   lectura: modo, RPM, capacidad, carga, FE, FE/t, ki y estado;
 *   bandeja inferior   PUERTOS: la cruz de caras (SideConfigWidget, la misma que abre la llave) con
 *                      dos pestañas, Energía y Fluido de Ki, y la leyenda de colores. Hasta el
 *                      2026-11-17 era una tabla de 6x2 botones; el usuario pidió el estilo Mekanism.
 *
 * Render-order: variante A (renderBackground = dim de vanilla + panel; render = widgets + texto).
 */
public class KiMotorScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/ki_motor.png");
    /** == TechPanelScreen.TANK_GLASS. */
    private static final ResourceLocation TANK_GLASS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/tech_tank.png");
    private static final int TANK_GLASS_W = 16, TANK_GLASS_H = 26;

    private static final int PANEL_W = 256, PANEL_H = 256;

    // ── Layout (coordenadas LOCALES del PNG) ─────────────────────────────────

    /** == pozos de ki_chamber.png (gen_ki_chamber_screen.py), heredados por gen_ki_motor_screen.py. */
    private static final int BAR_X = 18, BAR_Y = 32, BAR_W = 16, BAR_H = 104;
    private static final int TANK_X = 42, TANK_Y = 32;

    /** Columna central (== MID_X0..MID_X1 de gen_ki_motor_screen.py). */
    private static final int MID_X = 63, MID_W = 52, MID_BTN_H = 18;
    private static final int MODE_Y = 32, REVERSE_Y = 54;
    private static final int RPM_LABEL_Y = 78, RPM_Y = 90, RPM_W = 25, RPM_H = 16, RPM_GAP = 2;

    /** Pantalla de lectura. */
    private static final int INFO_X = 126, INFO_Y = 36, INFO_LH = 11, INFO_RIGHT = 236;

    /** Bandeja de puertos (== TRAY_* de gen_ki_motor_screen.py): título, pestañas, cruz y leyenda. */
    private static final int PORTS_TITLE_X = 12, PORTS_TITLE_Y = 162;
    // Pestañas a la izquierda, leyenda en medio y la cruz pegada a la derecha: con la leyenda a la
    // derecha «Desconectada» se salía de la bandeja (capturas del 2026-11-17).
    private static final int TAB_X = 12, TAB_Y = 178, TAB_W = 76, TAB_H = 16, TAB_STEP = 20;
    private static final int CROSS_X = 180, CROSS_Y = 164;
    private static final int LEGEND_X = 96, LEGEND_Y = 168, LEGEND_LH = 14;

    private final BlockPos pos;
    private int leftPos, topPos;

    private TechButton modeButton, reverseButton;
    private final TechButton[] rpmButtons = new TechButton[KiMotorControl.RPM_STEPS.length];
    private TechButton energyTab, fluidTab;
    private final SideConfigWidget cross;

    public KiMotorScreen(BlockPos pos) {
        super(Component.translatable("block.zenkai.ki_motor"));
        this.pos = pos;
        // Frente de la cruz = la cara que mira al jugador al abrir (igual que con la llave).
        Direction front = net.minecraft.client.Minecraft.getInstance().player != null
                ? net.minecraft.client.Minecraft.getInstance().player.getDirection().getOpposite() : Direction.NORTH;
        this.cross = new SideConfigWidget(pos, front, SideConfigurable.Channel.ENERGY);
    }

    @Nullable
    private KiMotorControl motor() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(pos) instanceof KiMotorControl m ? m : null;
    }

    private void send(int action, int arg) {
        PacketDistributor.sendToServer(new KiMotorActionPacket(pos, action, arg));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        leftPos = (width - PANEL_W) / 2;
        topPos = (height - PANEL_H) / 2;

        modeButton = addRenderableWidget(new TechButton(leftPos + MID_X, topPos + MODE_Y, MID_W, MID_BTN_H,
                Component.empty(), TechButton.Kind.PRIMARY, () -> {
                    KiMotorControl m = motor();
                    if (m == null) return;
                    KiMotorControl.Mode next = m.mode() == KiMotorControl.Mode.MOTOR
                            ? KiMotorControl.Mode.GENERATOR : KiMotorControl.Mode.MOTOR;
                    send(KiMotorActionPacket.SET_MODE, next.ordinal());
                }));
        reverseButton = addRenderableWidget(new TechButton(leftPos + MID_X, topPos + REVERSE_Y, MID_W, MID_BTN_H,
                Component.empty(), TechButton.Kind.SECONDARY, () -> send(KiMotorActionPacket.TOGGLE_REVERSE, 0)));

        for (int i = 0; i < rpmButtons.length; i++) {
            final int step = i;
            int bx = leftPos + MID_X + (i % 2) * (RPM_W + RPM_GAP);
            int by = topPos + RPM_Y + (i / 2) * (RPM_H + RPM_GAP);
            rpmButtons[i] = addRenderableWidget(new TechButton(bx, by, RPM_W, RPM_H,
                    Component.literal(String.valueOf(KiMotorControl.RPM_STEPS[i])), TechButton.Kind.SECONDARY,
                    () -> send(KiMotorActionPacket.SET_SPEED, step)));
        }

        energyTab = addRenderableWidget(new TechButton(leftPos + TAB_X, topPos + TAB_Y, TAB_W, TAB_H,
                Component.translatable(SideConfigurable.Channel.ENERGY.langKey()), TechButton.Kind.SECONDARY,
                () -> cross.setChannel(SideConfigurable.Channel.ENERGY)));
        fluidTab = addRenderableWidget(new TechButton(leftPos + TAB_X, topPos + TAB_Y + TAB_STEP, TAB_W, TAB_H,
                Component.translatable("screen.zenkai.ki_motor.port.fluid_tab"), TechButton.Kind.SECONDARY,
                () -> cross.setChannel(SideConfigurable.Channel.FLUID)));
        cross.setPosition(leftPos + CROSS_X, topPos + CROSS_Y);
        refresh();
    }

    @Override
    public void tick() {
        super.tick();
        KiMotorControl m = motor();
        // El bloque se rompió o se descargó: la pantalla no tiene nada que mostrar.
        if (m == null || minecraft == null || minecraft.player == null
                || minecraft.player.distanceToSqr(pos.getCenter()) > 64.0) {
            onClose();
            return;
        }
        refresh();
    }

    /** Único sitio que decide el texto y el estado de cada botón. */
    private void refresh() {
        KiMotorControl m = motor();
        if (m == null) return;
        boolean motorMode = m.mode() == KiMotorControl.Mode.MOTOR;
        // Texto corto aparte del de la lectura: «Generador» no cabe en 52 px.
        modeButton.setMessage(Component.translatable(motorMode
                ? "screen.zenkai.ki_motor.mode.button.motor" : "screen.zenkai.ki_motor.mode.button.generator"));
        reverseButton.setMessage(Component.translatable(m.reversed()
                ? "screen.zenkai.ki_motor.reverse_on" : "screen.zenkai.ki_motor.reverse_off"));
        // El sentido y la velocidad solo mandan en modo motor: en generador gira lo que diga la red.
        reverseButton.active = motorMode;
        for (int i = 0; i < rpmButtons.length; i++) {
            rpmButtons[i].active = motorMode && i != m.speedStep();
        }
        // La pestaña activa se apaga, como el selector de RPM.
        energyTab.active = cross.channel() != SideConfigurable.Channel.ENERGY;
        fluidTab.active = cross.channel() != SideConfigurable.Channel.FLUID;
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(BG, leftPos, topPos, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        KiMotorControl m = motor();
        if (m == null) return;

        g.pose().pushPose();
        g.pose().translate(leftPos, topPos, 0f);
        renderContent(g, m);
        g.pose().popPose();
        cross.render(g, mouseX, mouseY);

        renderTooltips(g, m, mouseX, mouseY);
    }

    private void renderContent(GuiGraphics g, KiMotorControl m) {
        boolean motorMode = m.mode() == KiMotorControl.Mode.MOTOR;
        boolean running = motorMode ? m.active() : m.feRateX10() > 0;

        com.hmc.zenkai.client.gui.TechHeader.draw(g, font, this.title, running);   // placa al título + LED (2026-11-23)

        drawVBar(g, BAR_X, BAR_Y, BAR_W, BAR_H, fraction(m.fe(), m.feCapacity()),
                running ? ZenkaiTechPalette.LED_GREEN_MID : ZenkaiTechPalette.BAR_FILL);
        FluidStack ki = m.kiFluid();
        KiEnergyType kiType = ki.isEmpty() ? null : KiEnergyType.ofFluid(ki.getFluid());
        drawKiTank(g, TANK_X, TANK_Y, BAR_W, BAR_H, fraction(ki.getAmount(), m.kiCapacity()),
                kiType == null ? null : ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                        "block/" + kiType.fluidName() + "_still"));

        // Columna central: etiqueta de la velocidad sobre la carcasa (oscuro, sin sombra).
        PanelText.onPanel(g, font, Component.translatable("screen.zenkai.ki_motor.speed"),
                MID_X, RPM_LABEL_Y, motorMode ? ZenkaiTechPalette.LABEL_ON_CHASSIS : ZenkaiTechPalette.MUTED_ON_CHASSIS);

        // Pantalla de lectura.
        int y = INFO_Y;
        y = infoRow(g, y, "screen.zenkai.ki_motor.info.mode", Component.translatable(motorMode
                ? "screen.zenkai.ki_motor.mode.motor" : "screen.zenkai.ki_motor.mode.generator"),
                ZenkaiTechPalette.MAXED_ON_SCREEN);
        y = infoRow(g, y, "screen.zenkai.ki_motor.info.rpm",
                Component.literal(motorMode ? (m.reversed() ? "-" : "") + m.rpm() + " RPM" : "—"),
                motorMode ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN);
        int su = motorMode ? ServerConfig.kiMotorRatedSu() : ServerConfig.kiMotorGeneratorImpact();
        y = infoRow(g, y, motorMode ? "screen.zenkai.ki_motor.info.capacity" : "screen.zenkai.ki_motor.info.impact",
                Component.literal(motorMode ? su + " SU" : su + " SU/RPM"), ZenkaiTechPalette.TEXT_ON_SCREEN);
        y = infoRow(g, y, "screen.zenkai.ki_motor.info.load",
                Component.literal(motorMode && m.active() ? m.loadPercent() + " %" : "—"),
                motorMode && m.active() ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN);
        y += 4;
        y = infoRow(g, y, "screen.zenkai.ki_motor.info.fe",
                Component.literal(ZenkaiNumbers.screen((long) m.fe()) + " FE"), ZenkaiTechPalette.TEXT_ON_SCREEN);
        y = infoRow(g, y, motorMode ? "screen.zenkai.ki_motor.info.consumption" : "screen.zenkai.ki_motor.info.production",
                Component.literal(m.feRateX10() > 0 ? formatRate(m.feRateX10()) + " FE/t" : "—"),
                m.feRateX10() > 0 ? ZenkaiTechPalette.OK_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN);
        y = infoRow(g, y, "screen.zenkai.ki_motor.info.ki",
                Component.literal(ZenkaiNumbers.screen((long) ki.getAmount()) + " mB"),
                kiType == null ? ZenkaiTechPalette.DIM_ON_SCREEN : kiType.argb());
        y += 6;
        PanelText.onDark(g, font, statusLine(m), INFO_X, y, statusColor(m));

        // Bandeja de puertos (carcasa: oscuro y sin sombra). La cruz se pinta en render(), en absolutas.
        PanelText.onPanel(g, font, Component.translatable("screen.zenkai.ki_motor.ports")
                .withStyle(ChatFormatting.BOLD), PORTS_TITLE_X, PORTS_TITLE_Y, ZenkaiTechPalette.LABEL_ON_CHASSIS);
        int ly = LEGEND_Y;
        for (SideMode sm : SideMode.values()) {
            g.fill(LEGEND_X, ly, LEGEND_X + 9, ly + 9, ZenkaiTechPalette.EDGE_DARK);
            g.fill(LEGEND_X + 1, ly + 1, LEGEND_X + 8, ly + 8, SideConfigWidget.frameColor(sm));
            PanelText.onPanel(g, font, Component.translatable(sm.langKey()), LEGEND_X + 13, ly + 1,
                    ZenkaiTechPalette.BODY_ON_CHASSIS);
            ly += LEGEND_LH;
        }
    }

    private static String formatRate(int x10) {
        return x10 % 10 == 0 ? String.valueOf(x10 / 10) : String.format(Locale.ROOT, "%.1f", x10 / 10.0);
    }

    private static float fraction(int v, int max) {
        return max <= 0 ? 0f : Math.max(0f, Math.min(1f, v / (float) max));
    }


    /** Lo que pasa AHORA, por orden de gravedad. */
    private Component statusLine(KiMotorControl m) {
        if (m.mode() == KiMotorControl.Mode.MOTOR) {
            if (m.active()) return Component.translatable("screen.zenkai.ki_motor.status.running");
            return Component.translatable("screen.zenkai.ki_motor.status.no_energy");
        }
        if (m.feRateX10() > 0) {
            return Component.translatable(m.fe() >= m.feCapacity()
                    ? "screen.zenkai.ki_motor.status.full" : "screen.zenkai.ki_motor.status.generating");
        }
        return Component.translatable("screen.zenkai.ki_motor.status.no_rotation");
    }

    private int statusColor(KiMotorControl m) {
        boolean ok = m.mode() == KiMotorControl.Mode.MOTOR ? m.active() : m.feRateX10() > 0;
        return ok ? ZenkaiTechPalette.OK_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN;
    }

    private int infoRow(GuiGraphics g, int y, String key, Component value, int valueColor) {
        PanelText.onDark(g, font, Component.translatable(key), INFO_X, y, ZenkaiTechPalette.DIM_ON_SCREEN);
        PanelText.rightOnDark(g, font, value, INFO_RIGHT, y, valueColor);
        return y + INFO_LH;
    }

    // ── Piezas de la familia (== TechPanelScreen, que no se puede heredar sin menú) ──

    private void drawVBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int fill) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, ZenkaiTechPalette.BAR_FRAME);
        g.fill(x, y, x + w, y + h, ZenkaiTechPalette.BAR_BG);
        int filled = Math.round(h * fraction);
        if (filled > 0) g.fill(x, y + h - filled, x + w, y + h, fill);
    }

    /** Copia de TechPanelScreen.drawKiTank (marco, sprite del fluido sin tinte y cristal encima). */
    private void drawKiTank(GuiGraphics g, int x, int y, int w, int h, float fraction,
                            @Nullable ResourceLocation stillSprite) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, ZenkaiTechPalette.BAR_FRAME);
        g.fill(x, y, x + w, y + h, ZenkaiTechPalette.BAR_BG);
        int filled = Math.round(h * fraction);
        if (filled > 0 && stillSprite != null && minecraft != null) {
            TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillSprite);
            // El scissor va en coordenadas ABSOLUTAS aunque la matriz esté trasladada al panel.
            g.enableScissor(leftPos + x, topPos + y + h - filled, leftPos + x + w, topPos + y + h);
            for (int cy = y + h - 16; cy > y + h - filled - 16; cy -= 16) {
                for (int cx = x; cx < x + w; cx += 16) g.blit(cx, cy, 0, 16, 16, sprite);
            }
            g.disableScissor();
        }
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        for (int gy = y + h - TANK_GLASS_H; gy >= y; gy -= TANK_GLASS_H) {
            int skip = gy == y ? 1 : 0;
            g.blit(TANK_GLASS, x, gy + skip, 0, skip, Math.min(w, TANK_GLASS_W), TANK_GLASS_H - skip,
                    TANK_GLASS_W, TANK_GLASS_H);
        }
    }

    // ── Tooltips (coordenadas absolutas) ─────────────────────────────────────

    private void renderTooltips(GuiGraphics g, KiMotorControl m, int mouseX, int mouseY) {
        int lx = mouseX - leftPos, ly = mouseY - topPos;
        List<Component> lines = new ArrayList<>();

        if (inside(lx, ly, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            lines.add(Component.translatable("screen.zenkai.ki_motor.info.fe").withStyle(ChatFormatting.BOLD));
            lines.add(Component.literal(m.fe() + " / " + m.feCapacity() + " FE"));
        } else if (inside(lx, ly, TANK_X - 1, TANK_Y - 1, BAR_W + 2, BAR_H + 2)) {
            FluidStack ki = m.kiFluid();
            KiEnergyType t = ki.isEmpty() ? null : KiEnergyType.ofFluid(ki.getFluid());
            lines.add(t == null
                    ? Component.translatable("screen.zenkai.ki_motor.tank_empty").withStyle(ChatFormatting.BOLD)
                    : Component.translatable(t.langKey()).withStyle(s -> s.withColor(t.argb() & 0xFFFFFF).withBold(true)));
            lines.add(Component.literal(ki.getAmount() + " / " + m.kiCapacity() + " mB"));
            if (t != null) {
                lines.add(Component.translatable("screen.zenkai.ki_motor.tank_rate", t.fePerMb())
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (modeButton.isMouseOver(mouseX, mouseY)) {
            lines.add(Component.translatable("screen.zenkai.ki_motor.mode.tip.motor").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.zenkai.ki_motor.mode.tip.generator").withStyle(ChatFormatting.GRAY));
        } else if (fluidTab.isMouseOver(mouseX, mouseY) && m.mode() == KiMotorControl.Mode.GENERATOR) {
            lines.add(Component.translatable("screen.zenkai.ki_motor.port.fluid_unused").withStyle(ChatFormatting.GRAY));
        } else {
            lines.addAll(cross.tooltip(mouseX, mouseY));
        }
        if (!lines.isEmpty()) g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (cross.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }
}
