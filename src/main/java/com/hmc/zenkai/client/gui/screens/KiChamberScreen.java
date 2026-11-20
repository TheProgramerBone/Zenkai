package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.ConfirmIconButton;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.client.gui.menu.KiChamberMenu;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pantalla de la cámara de contención de ki. De izquierda a derecha:
 *   barra de FE (el búfer del conversor) y, pegado, el TANQUE DE KI con su fluido;
 *   tres botones: puerta (abrir, cerrar o liberar a quien esté dentro), el ajuste Forzar y el
 *   conversor a FE (la carga arranca sola al entrar alguien: no hay botón de cargar), y debajo las seis
 *   ranuras: tolerancias arriba; velocidad, almacenamiento y batería abajo;
 *   panel de lectura: tipo de energía y ki de QUIEN MIRA la pantalla (ya no hace falta estar
 *   dentro para abrirla), producción, FE y estado.
 *
 * QUÉ SALE DE DÓNDE. Lo de la máquina (tanque, FE, si carga) llega por el ContainerData del
 * menú. Lo del JUGADOR (su ki, su forma, su cooldown) se lee de sus attachments, que ya se
 * sincronizan solos cada tick: no se duplica en el menú. El tipo de energía sale de
 * KiEnergyType.of, el mismo embudo que usa el servidor, así la pantalla no puede anunciar un
 * tipo y la máquina producir otro.
 *
 * Geometría == tools/gen_ki_chamber_screen.py (pozos de la barra y del tanque). Los botones son
 * TechButton y no están en la textura. Coordenadas LOCALES en renderLabels y ABSOLUTAS en
 * renderFloating, como en el resto de la familia (ver TechPanelScreen).
 */
public class KiChamberScreen extends TechPanelScreen<KiChamberMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/ki_chamber.png");

    // ── Layout (coordenadas del PNG) ─────────────────────────────────────────
    private static final int TITLE_X = 12, TITLE_Y = 7;
    private static final int LED_X = 144, LED_Y = 2;

    /** == BAR_X/BAR_Y y TANK_X/TANK_Y de gen_ki_chamber_screen.py. */
    private static final int BAR_X = 18, BAR_Y = 32, BAR_W = 16, BAR_H = 104;
    private static final int TANK_X = 42, TANK_Y = 32;

    private static final int BTN_X = 63, BTN_W = 52, BTN_H = 18;
    private static final int BTN_DOOR_Y = 34, BTN_FORCE_Y = 54, BTN_CONVERT_Y = 74;

    private static final int INFO_X = 126, INFO_Y = 38, INFO_LH = 11;
    private static final int INFO_RIGHT = 236;

    /** Papelera 16x16 (btn_trash, gris a propósito) en la esquina inferior derecha del panel de lectura. */
    private static final int TRASH_SIZE = 16, TRASH_X = INFO_RIGHT - TRASH_SIZE, TRASH_Y = 114;
    private static final ResourceLocation TRASH =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/buttons/btn_trash.png");
    private static final ResourceLocation TRASH_HL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/buttons/btn_trash_highlight.png");

    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 157;

    /** Clave de lang de cada ranura de máquina, en el orden de KiContainmentChamberBlockEntity.SLOT_*. */
    private static final String[] SLOT_KEYS = {
            "screen.zenkai.ki_chamber.slot.tolerance_super",
            "screen.zenkai.ki_chamber.slot.tolerance_divine",
            "screen.zenkai.ki_chamber.slot.tolerance_legendary",
            "screen.zenkai.ki_chamber.slot.speed",
            "screen.zenkai.ki_chamber.slot.storage",
            "screen.zenkai.ki_chamber.slot.battery",
    };

    private TechButton doorButton;
    private TechButton forceButton;
    private TechButton convertButton;
    private ConfirmIconButton dumpButton;

    public KiChamberScreen(KiChamberMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation background() { return BG; }

    @Override
    protected void init() {
        super.init();
        doorButton = new TechButton(leftPos + BTN_X, topPos + BTN_DOOR_Y, BTN_W, BTN_H,
                Component.empty(), TechButton.Kind.PRIMARY, () -> press(KiChamberMenu.BTN_DOOR));
        forceButton = new TechButton(leftPos + BTN_X, topPos + BTN_FORCE_Y, BTN_W, BTN_H,
                Component.empty(),
                TechButton.Kind.SECONDARY, () -> press(KiChamberMenu.BTN_FORCE));
        convertButton = new TechButton(leftPos + BTN_X, topPos + BTN_CONVERT_Y, BTN_W, BTN_H,
                Component.empty(), TechButton.Kind.SECONDARY, () -> press(KiChamberMenu.BTN_CONVERT));
        addRenderableWidget(doorButton);
        addRenderableWidget(forceButton);
        addRenderableWidget(convertButton);
        // Dos clics (el primero arma, el segundo vacía): tirar 16 000 mB por un clic suelto duele.
        dumpButton = new ConfirmIconButton(leftPos + TRASH_X, topPos + TRASH_Y, TRASH_SIZE, TRASH, TRASH_HL,
                Component.translatable("screen.zenkai.ki_chamber.dump.tip"),
                Component.translatable("screen.zenkai.ki_chamber.dump.confirm"),
                () -> press(KiChamberMenu.BTN_DUMP));
        addRenderableWidget(dumpButton);
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

    // ── Estado del jugador (cliente) ─────────────────────────────────────────

    @Nullable
    private PlayerStatsAttachment stats() {
        return minecraft == null || minecraft.player == null ? null : PlayerStatsAttachment.get(minecraft.player);
    }

    @Nullable
    private KiEnergyType playerType() {
        return minecraft == null || minecraft.player == null ? null : KiEnergyType.of(minecraft.player);
    }

    /** Ticks de cooldown que le quedan al jugador; 0 si puede cargar. */
    private long cooldownTicks() {
        PlayerStatsAttachment att = stats();
        if (att == null || minecraft == null || minecraft.level == null) return 0L;
        return KiContainmentChamberBlockEntity.cooldownLeft(att.flags(), minecraft.level.getGameTime());
    }

    private float kiFraction() {
        PlayerStatsAttachment att = stats();
        return att == null || att.getKiPool() <= 0 ? 0f : att.getKiCurrent() / (float) att.getKiPool();
    }

    /** Único sitio que decide qué dice cada botón. Se recalcula cada tick. */
    private void refresh() {
        dumpButton.active = menu.tankAmount() > 0;
        doorButton.setMessage(Component.translatable(menu.isOccupied() ? "screen.zenkai.ki_chamber.door_release"
                : menu.isOpen() ? "screen.zenkai.ki_chamber.door_close" : "screen.zenkai.ki_chamber.door_open"));
        forceButton.setMessage(Component.translatable(menu.isForceSet()
                ? "screen.zenkai.ki_chamber.force_on" : "screen.zenkai.ki_chamber.force_off"));
        convertButton.setMessage(Component.translatable(menu.isConverting()
                ? "screen.zenkai.ki_chamber.convert_on" : "screen.zenkai.ki_chamber.convert_off"));
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawTitle(g, TITLE_X, TITLE_Y);
        drawLed(g, LED_X, LED_Y, menu.isCharging());
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);

        drawVBar(g, BAR_X, BAR_Y, BAR_W, BAR_H, menu.energyFraction(),
                menu.fePerTick() > 0 ? ZenkaiTechPalette.LED_GREEN_MID : ZenkaiTechPalette.BAR_FILL);

        KiEnergyType inTank = menu.tankType();
        drawKiTank(g, TANK_X, TANK_Y, BAR_W, BAR_H, menu.tankFraction(),
                inTank == null ? null : sprite(inTank));

        KiEnergyType mine = playerType();
        int y = INFO_Y;
        y = infoRow(g, y, "screen.zenkai.ki_chamber.energy_type",
                mine == null ? Component.literal("—") : Component.translatable(mine.langKey()),
                mine == null ? ZenkaiTechPalette.DENIED_ON_SCREEN : mine.argb());
        y = infoRow(g, y, "screen.zenkai.ki_chamber.ki",
                Component.literal(Math.round(kiFraction() * 100f) + " %"), ZenkaiTechPalette.TEXT_ON_SCREEN);
        y += 4;
        boolean charging = menu.isCharging();
        y = infoRow(g, y, "screen.zenkai.ki_chamber.output",
                Component.literal(charging ? ZenkaiNumbers.screen((long) menu.mbPerSecond()) + " mB/s" : "—"),
                charging ? ZenkaiTechPalette.MAXED_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN);
        y = infoRow(g, y, "screen.zenkai.ki_chamber.tank",
                Component.literal(ZenkaiNumbers.screen((long) menu.tankAmount()) + " mB"),
                inTank == null ? ZenkaiTechPalette.DIM_ON_SCREEN : inTank.argb());
        y = infoRow(g, y, "screen.zenkai.ki_chamber.fe",
                Component.literal(ZenkaiNumbers.screen((long) menu.energy()) + " FE"),
                ZenkaiTechPalette.TEXT_ON_SCREEN);
        y = infoRow(g, y, "screen.zenkai.ki_chamber.fe_rate",
                Component.literal(menu.fePerTick() > 0 ? menu.fePerTick() + " FE/t" : "—"),
                menu.fePerTick() > 0 ? ZenkaiTechPalette.OK_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN);

        y += 6;
        PanelText.onDark(g, font, statusLine(mine, inTank), INFO_X, y, statusColor(mine, inTank));

        // Segunda línea, solo de aviso: el calor si ya lo hay, o que este tipo no está tolerado.
        // Va aparte del estado para que «Cargando...» y «SOBRECARGA 60 %» se lean a la vez.
        y += INFO_LH;
        if (menu.heat() > 0) {
            boolean blink = menu.heat() >= 40 && System.currentTimeMillis() / 300 % 2 == 0;
            PanelText.onDark(g, font, Component.translatable("screen.zenkai.ki_chamber.overload", menu.heat()),
                    INFO_X, y, blink ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        } else if (mine != null && !menu.tolerates(mine)) {
            PanelText.onDark(g, font, Component.translatable("screen.zenkai.ki_chamber.no_tolerance"),
                    INFO_X, y, ZenkaiTechPalette.DENIED_ON_SCREEN);
        }
    }

    private static ResourceLocation sprite(KiEnergyType type) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "block/" + type.fluidName() + "_still");
    }

    /** Una línea con lo que pasa AHORA, por orden de gravedad. Primero la máquina; si está libre, quien mira. */
    private Component statusLine(@Nullable KiEnergyType mine, @Nullable KiEnergyType inTank) {
        if (menu.isForcing()) return Component.translatable("screen.zenkai.ki_chamber.status.forcing");
        if (menu.isCharging()) return Component.translatable("screen.zenkai.ki_chamber.status.charging");
        if (menu.tankAmount() >= menu.tankCapacity()) return Component.translatable("screen.zenkai.ki_chamber.status.full");
        if (mine == null) return Component.translatable("screen.zenkai.ki_chamber.status.no_energy");
        if (inTank != null && inTank != mine) return Component.translatable("screen.zenkai.ki_chamber.status.wrong_fluid");
        long cd = cooldownTicks();
        if (cd > 0L) return Component.translatable("screen.zenkai.ki_chamber.status.cooldown", (cd + 19L) / 20L);
        return Component.translatable(menu.isOpen()
                ? "screen.zenkai.ki_chamber.status.open" : "screen.zenkai.ki_chamber.status.ready");
    }

    private int statusColor(@Nullable KiEnergyType mine, @Nullable KiEnergyType inTank) {
        if (menu.isForcing()) return ZenkaiTechPalette.DENIED_ON_SCREEN;
        if (menu.isCharging()) return ZenkaiTechPalette.OK_ON_SCREEN;
        if (mine == null || (inTank != null && inTank != mine) || cooldownTicks() > 0L) {
            return ZenkaiTechPalette.DENIED_ON_SCREEN;
        }
        return ZenkaiTechPalette.DIM_ON_SCREEN;
    }

    /** Etiqueta a la izquierda, valor pegado al borde derecho del panel. */
    private int infoRow(GuiGraphics g, int y, String key, Component value, int valueColor) {
        PanelText.onDark(g, font, Component.translatable(key), INFO_X, y, ZenkaiTechPalette.DIM_ON_SCREEN);
        PanelText.rightOnDark(g, font, value, INFO_RIGHT, y, valueColor);
        return y + INFO_LH;
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        int lmx = localMouseX(mouseX), lmy = localMouseY(mouseY);

        if (inside(lmx, lmy, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.ki_chamber.fe").withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(menu.energy() + " / " + menu.energyCapacity() + " FE"))));
            return;
        }

        if (inside(lmx, lmy, TANK_X - 1, TANK_Y - 1, BAR_W + 2, BAR_H + 2)) {
            KiEnergyType t = menu.tankType();
            Component name = t == null
                    ? Component.translatable("screen.zenkai.ki_chamber.tank_empty")
                    : Component.translatable(t.langKey()).withStyle(s -> s.withColor(t.argb() & 0xFFFFFF));
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(name.copy().withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(menu.tankAmount() + " / " + menu.tankCapacity() + " mB"))));
            return;
        }

        // Ranura vacía bajo el cursor: qué va ahí. Con ítem dentro manda el tooltip del propio ítem.
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int idx = menu.slots.indexOf(hoveredSlot);
            if (idx >= 0 && idx < KiChamberMenu.MACHINE_SLOTS) {
                drawTooltip(g, mouseX, mouseY, List.of(
                        TipLine.of(Component.translatable(SLOT_KEYS[idx]).withStyle(ChatFormatting.BOLD)),
                        TipLine.of(Component.translatable(SLOT_KEYS[idx] + ".desc").withStyle(ChatFormatting.GRAY))));
                return;
            }
        }

        if (forceButton != null && forceButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.ki_chamber.force.title").withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.translatable("screen.zenkai.ki_chamber.force.tip1")
                            .withStyle(ChatFormatting.GRAY)),
                    TipLine.of(Component.translatable("screen.zenkai.ki_chamber.force.tip2")
                            .withStyle(ChatFormatting.RED))));
            return;
        }

        if (convertButton != null && convertButton.isMouseOver(mouseX, mouseY)) {
            KiEnergyType t = menu.tankType() != null ? menu.tankType() : playerType();
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.ki_chamber.convert.tip1")
                            .withStyle(ChatFormatting.GRAY)),
                    TipLine.of(Component.translatable("screen.zenkai.ki_chamber.convert.tip2",
                            t == null ? "—" : String.valueOf(t.fePerMb())).withStyle(ChatFormatting.AQUA))));
        }
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
