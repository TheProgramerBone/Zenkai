package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.menu.ScouterBenchMenu;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.hmc.zenkai.feature.sense.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI del banco de scouter. Izquierda: el slot. Derecha: TODAS las mejoras con su nivel actual
 * y máximo ("RANGO 2/5") y un botón + por fila.
 *
 * Se ven todas, incluidas las que no puedes pagar — con el + apagado. Ocultar lo que no
 * alcanzas convierte el banco en una lista que crece sola y no enseña a dónde vas.
 *
 * Reconstrucción por tick (convención del mod): el estado de los botones se recalcula en
 * containerTick desde el stack del slot, así que aplicar una mejora se refleja solo, sin
 * cerrar y reabrir.
 */
public class ScouterBenchScreen extends AbstractContainerScreen<ScouterBenchMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    private static final ResourceLocation SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/slot/empty_scouter_slot.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;

    private static final int LIST_X   = 92;
    private static final int ROW_H    = 20;
    private static final int PLUS_X   = 218;
    private static final int BAR_X    = 92;
    private static final int BAR_W    = 140;
    private static final int BAR_H    = 6;

    private static final int C_LABEL   = 0xFFFFFFFF;
    private static final int C_MAXED   = 0xFF7FE08A;
    private static final int C_BAR_BG  = 0xFF303030;
    private static final int C_BAR_ON  = 0xFFFFC94A;
    private static final int C_PAUSED  = 0xFFFF8060;

    private final List<PlusIconButton> plusButtons = new ArrayList<>();
    private TextOnlyButton repairButton;
    private TextOnlyButton cancelButton;

    public ScouterBenchScreen(ScouterBenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = BG_W;
        this.imageHeight = BG_H;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - BG_W) / 2;
        this.topPos = (this.height - BG_H) / 2;

        plusButtons.clear();
        ScouterUpgrade[] all = ScouterUpgrade.values();
        for (int i = 0; i < all.length; i++) {
            final int ordinal = i;
            int y = topPos + ScreenTitle.CONTENT_TOP + i * ROW_H;
            PlusIconButton b = new PlusIconButton(leftPos + PLUS_X, y,
                    () -> press(ordinal));
            plusButtons.add(b);
            addRenderableWidget(b);
        }

        int afterList = topPos + ScreenTitle.CONTENT_TOP + all.length * ROW_H + 16;

        repairButton = new TextOnlyButton(leftPos + LIST_X, afterList, 70, 16,
                Component.translatable("screen.zenkai.scouter_bench.repair"),
                () -> press(ScouterBenchMenu.BTN_REPAIR));
        addRenderableWidget(repairButton);

        cancelButton = new TextOnlyButton(leftPos + LIST_X + 74, afterList, 70, 16,
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

    /** Único sitio que decide qué botón está vivo. */
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

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BG, leftPos, topPos, 0, 0, BG_W, BG_H, BG_W, BG_H);
        g.blit(SLOT_BG, leftPos + 20, topPos + 40, 0, 0, 16, 16, 16, 16);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        ScreenTitle.drawAbovePanel(g, font, title, leftPos + BG_W / 2, topPos);

        ItemStack s = menu.scouter();
        ScouterUpgrades up = ScouterStacks.upgrades(s);
        ScouterUpgrade[] all = ScouterUpgrade.values();

        for (int i = 0; i < all.length; i++) {
            ScouterUpgrade u = all[i];
            int y = topPos + ScreenTitle.CONTENT_TOP + i * ROW_H;
            int lvl = s.isEmpty() ? 0 : up.level(u);
            boolean maxed = lvl >= u.maxLevel();

            Component label = Component.translatable(u.nameKey())
                    .append(Component.literal(" " + lvl + "/" + u.maxLevel()));
            g.drawString(font, label, leftPos + LIST_X, y + 2, maxed ? C_MAXED : C_LABEL, true);
        }

        if (menu.isWorking()) {
            int barY = topPos + ScreenTitle.CONTENT_TOP + all.length * ROW_H + 2;
            g.fill(leftPos + BAR_X, barY, leftPos + BAR_X + BAR_W, barY + BAR_H, C_BAR_BG);
            int w = Math.round(BAR_W * menu.progressFraction());
            g.fill(leftPos + BAR_X, barY, leftPos + BAR_X + w, barY + BAR_H, C_BAR_ON);

            if (menu.isPaused()) {
                g.drawString(font, Component.translatable("screen.zenkai.scouter_bench.paused"),
                        leftPos + BAR_X, barY + BAR_H + 2, C_PAUSED, true);
            }
        }

        renderUpgradeTooltip(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Tooltip del +: qué hace la mejora y qué materiales pide ESE nivel. */
    private void renderUpgradeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack s = menu.scouter();
        ScouterUpgrade[] all = ScouterUpgrade.values();

        for (int i = 0; i < all.length; i++) {
            PlusIconButton b = plusButtons.get(i);
            if (!b.isMouseOver(mouseX, mouseY)) continue;

            ScouterUpgrade u = all[i];
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(u.nameKey()));
            lines.add(Component.translatable(u.descKey()).withStyle(net.minecraft.ChatFormatting.GRAY));

            int next = s.isEmpty() ? 1 : ScouterStacks.upgrades(s).nextLevel(u);
            if (next < 0) {
                lines.add(Component.translatable("screen.zenkai.scouter_bench.maxed")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            } else {
                ScouterUpgradeCost cost = ScouterUpgradeCost.forLevel(u, next);
                for (ScouterUpgradeCost.Material m : cost.materials()) {
                    lines.add(materialLine(m));
                }
                if (cost.energy() > 0) {
                    lines.add(Component.translatable("screen.zenkai.scouter_bench.energy", cost.energy())
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                }
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
    }

    /** "4x Lingote de hierro", en verde si lo tienes y en rojo si no. */
    private Component materialLine(ScouterUpgradeCost.Material m) {
        List<Item> items = m.displayItems();
        Component name = items.isEmpty()
                ? Component.literal(m.id().toString())
                : items.get(0).getDescription();

        boolean have = minecraft != null && minecraft.player != null
                && m.countIn(minecraft.player.getInventory()) >= m.count();

        return Component.literal(m.count() + "x ").append(name)
                .withStyle(have ? net.minecraft.ChatFormatting.GREEN
                        : net.minecraft.ChatFormatting.RED);
    }

    /** El título va FUERA del panel (ScreenTitle), así que aquí no se dibuja nada. */
    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) { }
}