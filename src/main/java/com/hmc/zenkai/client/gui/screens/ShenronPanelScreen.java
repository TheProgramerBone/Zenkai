package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Base de las pantallas de deseos de Shenlong/Porunga: la misma estructura que ZenkaiPanelScreen (título
 * encima, pie Atrás/Confirmar, mismo tamaño y posición que cualquier panel) con la familia VISUAL
 * divina — verde dragón sobre negro-noche y una serpiente de dragón de marca de agua en vez del
 * sello de Goku. Ver ZenkaiPalette.SHENRON_* y tools/gen_shenron_screen.py.
 *
 * Cada deseo se identifica con un ÍTEM vanilla (cofre, tótem, estrella del Nether…) en lugar de un número
 * o de estrellas: escala con deseos nuevos (cada uno trae su icono) y no necesita arte propio.
 */
public abstract class ShenronPanelScreen extends ZenkaiPanelScreen {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/shenron_screen.png");

    /** Lado del medallón del icono grande y escala a la que se pinta el ítem (16 * 3 = 48). */
    private static final int MEDAL = 52;
    private static final int ICON_SCALE = 3;

    protected ShenronPanelScreen(Component title, @Nullable Screen parent) {
        super(title, parent);
    }

    /** El ítem que representa este deseo (WishIcons). Vacío si la pantalla no lleva cabecera de icono. */
    protected ItemStack wishIcon() { return ItemStack.EMPTY; }

    @Override protected int titleColor() { return ZenkaiPalette.SHENLONG; }
    @Override protected boolean darkPanel() { return true; }
    @Override protected int dividerColor() { return ZenkaiPalette.SHENRON_DIVIDER; }

    @Override
    protected void drawPanel(GuiGraphics g) {
        g.blit(TEX, panelLeft, panelTop, BG_W, BG_H, 0, 0, 256, 256, 256, 256);
    }

    /**
     * Medallón con el icono del deseo en grande, centrado en x y con su borde superior en y. Devuelve el
     * primer Y libre debajo. Es la cabecera de las confirmaciones que son solo texto.
     */
    protected int drawWishHeader(GuiGraphics g, int y) {
        int x = centerX() - MEDAL / 2;
        g.fill(x, y, x + MEDAL, y + MEDAL, ZenkaiPalette.SHENRON_BORDER_IN);
        g.fill(x + 1, y + 1, x + MEDAL - 1, y + MEDAL - 1, ZenkaiPalette.SHENRON_BORDER_MID);
        g.fill(x + 2, y + 2, x + MEDAL - 2, y + MEDAL - 2, ZenkaiPalette.SHENRON_BG);
        g.pose().pushPose();
        g.pose().translate(centerX() - 8 * ICON_SCALE, y + (MEDAL - 16 * ICON_SCALE) / 2f, 0);
        g.pose().scale(ICON_SCALE, ICON_SCALE, 1f);
        g.renderFakeItem(wishIcon(), 0, 0);
        g.pose().popPose();
        return y + MEDAL + 8;
    }
}
