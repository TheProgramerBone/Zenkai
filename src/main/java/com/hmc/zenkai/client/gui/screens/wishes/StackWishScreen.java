package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.menu.StackWishMenu;
import com.hmc.zenkai.client.gui.screens.ShenlongWishScreen;
import com.hmc.zenkai.feature.wishes.SetGhostSlotPayload;
import com.hmc.zenkai.feature.wishes.StackWishPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Deseo "quiero más de...": el jugador pone un objeto en el slot fantasma y Shenlong le da una
 * pila.
 *
 * Es la ÚNICA pantalla de deseo que tiene que ser un AbstractContainerScreen (necesita slots
 * reales del inventario), así que no puede heredar de ZenkaiPanelScreen. Lo que sí puede — y
 * ahora hace — es usar los mismos assets: fondo beige con marco dorado en vez de la textura
 * gris de vanilla, título con ScreenTitle y botones PanelButton en vez de Button.builder.
 *
 * Correcciones funcionales:
 *   - los botones se colocaban en centerY + 135 con imageHeight = 133: caían 2 px POR DEBAJO
 *     del borde del cuadro y quedaban flotando sobre el mundo. Ahora cuelgan del pie con una
 *     separación explícita, como en el resto de pantallas.
 *   - Confirmar se apaga si el slot fantasma está vacío. Antes se podía gastar el deseo en nada.
 *   - Volver reutiliza la pantalla padre en vez de construir una ShenlongWishScreen nueva, que
 *     perdía el contador de deseos restantes hasta el siguiente frame.
 */
public class StackWishScreen extends AbstractContainerScreen<StackWishMenu> {

    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/stack_wish.png");

    /** Zona útil de la textura (el PNG es 256x256 por potencia de dos). */
    private static final int PANEL_W = 176, PANEL_H = 133;
    private static final int FOOTER_GAP = 6;

    private PanelButton confirmButton;

    public StackWishScreen(StackWishMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        // Etiqueta de inventario desactivada: la textura ya separa visualmente las dos zonas y
        // el texto gris de vanilla sobre el beige quedaba ilegible.
        this.titleLabelY = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.leftPos;
        int y = this.topPos + PANEL_H + FOOTER_GAP;
        int gap = 8;
        int totalW = PanelButton.W * 2 + gap;
        int bx = x + (PANEL_W - totalW) / 2;

        this.addRenderableWidget(PanelButton.secondary(bx, y,
                Component.translatable("screen.zenkai.gui.back"),
                () -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.closeContainer();
                    }
                    Minecraft.getInstance().setScreen(new ShenlongWishScreen());
                }));

        this.confirmButton = PanelButton.primary(bx + PanelButton.W + gap, y,
                Component.translatable("screen.zenkai.gui.confirm"),
                this::confirm);
        this.addRenderableWidget(this.confirmButton);
    }

    private void confirm() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.translatable("message.zenkai.no_connection"), false);
            }
            return;
        }
        conn.send(new StackWishPayload());
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.closeContainer();
        }
        this.onClose();
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BG_TEXTURE, this.leftPos, this.topPos, 0, 0, PANEL_W, PANEL_H, 256, 256);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        // Título fuera del cuadro, igual que el resto del mod.
        ScreenTitle.drawAbovePanel(g, this.font, this.title,
                this.leftPos + PANEL_W / 2, this.topPos, ZenkaiPalette.SHENLONG);

        // Pista sobre el slot fantasma: sin ella nadie adivina que hay que soltar un objeto ahí.
        if (this.menu.getChosenItem().isEmpty()) {
            Component hint = Component.translatable("screen.zenkai.wish.stack.hint");
            g.drawString(this.font, hint,
                    this.leftPos + PANEL_W / 2 - this.font.width(hint) / 2,
                    this.topPos + 8, ZenkaiPalette.LABEL_ON_PANEL, false);
        }

        if (this.confirmButton != null) {
            this.confirmButton.active = !this.menu.getChosenItem().isEmpty();
        }

        this.renderTooltip(g, mouseX, mouseY);
    }

    /** El slot 0 es fantasma: copia lo que lleva el cursor sin consumirlo. */
    @Override
    protected void slotClicked(@NotNull Slot slot, int slotId, int mouseButton, @NotNull ClickType clickType) {
        if (slotId == 0) {
            ItemStack cursor = this.menu.getCarried();
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new SetGhostSlotPayload(cursor.copy()));
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, clickType);
    }
}