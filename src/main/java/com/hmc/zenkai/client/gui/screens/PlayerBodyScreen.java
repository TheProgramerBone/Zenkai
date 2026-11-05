package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.menu.PlayerBodyMenu;
import com.hmc.zenkai.client.gui.menu.PlayerBodyMenu.Layout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pantalla de un cuerpo: el aspecto del inventario de vanilla (panel gris con pozos de 18x18) para que se lea a
 * primera vista, con la fila de armadura, mochila, barra y —si hay— las filas de Curios del cuerpo, y encima los
 * botones «Llevarse todo» y «Enterrar» (este último solo con el cuerpo vacío). Los pozos se pintan con rellenos
 * y no con una textura: la altura cambia con el número de ranuras de Curios, y así no hay un PNG por cada altura.
 * La geometría sale de {@link Layout}, la misma que usa el menú para colocar los huecos.
 */
public class PlayerBodyScreen extends AbstractContainerScreen<PlayerBodyMenu> {

    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555, WELL = 0xFF8B8B8B, WELL_DARK = 0xFF373737;
    private static final int TEXT = 0xFF404040;

    private Button bury, prev, next;

    public PlayerBodyScreen(PlayerBodyMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = Layout.W;
        this.imageHeight = Layout.height(menu.curioCount());
        this.inventoryLabelY = Layout.playerY(menu.curioCount()) - 11;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("zenkai.body.take_all"), b -> press(PlayerBodyMenu.BTN_TAKE_ALL))
                .bounds(leftPos + 8, topPos + Layout.BTN_Y, 100, 16)
                .tooltip(Tooltip.create(Component.translatable("zenkai.body.take_all.tooltip"))).build());
        bury = addRenderableWidget(Button.builder(Component.translatable("zenkai.body.bury"), b -> press(PlayerBodyMenu.BTN_BURY))
                .bounds(leftPos + 112, topPos + Layout.BTN_Y, 56, 16)
                .tooltip(Tooltip.create(Component.translatable("zenkai.body.bury.tooltip"))).build());
        if (menu.curioCount() > PlayerBodyMenu.PAGE) {   // más ranuras de Curios de las que caben en una fila: flechas de página
            int y = topPos + Layout.CURIO_Y;
            prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> press(PlayerBodyMenu.BTN_PREV))
                    .bounds(leftPos + 116, y, 14, 12).build());
            next = addRenderableWidget(Button.builder(Component.literal(">"), b -> press(PlayerBodyMenu.BTN_NEXT))
                    .bounds(leftPos + 154, y, 14, 12).build());
        }
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (bury != null) bury.visible = menu.bodyIsEmpty();
        if (prev != null) prev.active = menu.page() > 0;
        if (next != null) next.active = menu.page() < menu.pages() - 1;
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);                       // contorno
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, LIGHT);            // luz arriba-izquierda
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, DARK);             // sombra abajo-derecha
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL);
    }

    private static void well(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, WELL_DARK);
        g.fill(x + 1, y + 1, x + 18, y + 18, LIGHT);
        g.fill(x + 1, y + 1, x + 17, y + 17, WELL);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        panel(g, leftPos, topPos, imageWidth, imageHeight);
        // Un pozo por cada hueco del menú, en el sitio exacto donde el menú puso el hueco (-1: el pozo rodea al hueco).
        for (var slot : menu.slots) well(g, leftPos + slot.x - 1, topPos + slot.y - 1);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        if (menu.curioCount() > 0)
            g.drawString(font, Component.translatable("zenkai.body.curios"), Layout.GRID_X, Layout.CURIO_Y + 2, TEXT, false);
        if (menu.curioCount() > PlayerBodyMenu.PAGE)
            g.drawCenteredString(font, (menu.page() + 1) + "/" + menu.pages(), 142, Layout.CURIO_Y + 2, TEXT);
        int xp = menu.storedXp();
        if (xp > 0) {   // la XP que guarda el cuerpo (solo la reclama quien puede; «Llevarse todo» la entrega)
            Component t = Component.translatable("zenkai.body.xp", xp, PlayerBodyMenu.levelOf(xp));
            g.drawString(font, t, Layout.W - 8 - font.width(t), titleLabelY, 0xFF2E7D32, false);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
