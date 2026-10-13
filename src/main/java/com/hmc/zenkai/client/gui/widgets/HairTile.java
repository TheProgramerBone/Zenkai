package com.hmc.zenkai.client.gui.widgets;

import com.hmc.zenkai.client.gui.LibraryIcons;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.feature.race.RaceTextureUtil;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Casilla de la biblioteca de la pestaña Pelo (HeadAppearanceScreen), mismo tamaño y marco que
 * FacePartTile. Cuatro clases:
 *  · STYLE: tu propia cabeza con ESE peinado, en 3D con la cámara fija a la cara (un peinado de
 *    cubos no cabe en una miniatura plana). Se pinta poniendo el peinado en tu visual solo
 *    durante el render de la casilla y devolviendo el que tenías.
 *  · FOLDER: una carpeta (ícono propio por convención o el genérico).
 *  · PLUS: la carpeta Custom (importados).  · BACK: volver a la raíz.
 * Clic derecho en un STYLE = favorito (Favorites), como en las caras.
 */
public class HairTile extends AbstractWidget {

    public enum Kind { STYLE, FOLDER, PLUS, BACK }

    public static final int SIZE = FacePartTile.SIZE;
    /** Cámara de la miniatura: escala del jugador y altura del encuadre (cabeza + pelo). */
    private static final int RENDER_SCALE = 30;
    private static final float Y_OFFSET = 0.78f;

    private final Kind kind;
    @Nullable private final String styleId;
    @Nullable private final ResourceLocation folderIcon;
    private final BooleanSupplier selected;
    private final Runnable onClick;
    @Nullable private Runnable onRightClick;
    private BooleanSupplier favorite = () -> false;

    private HairTile(int x, int y, Kind kind, @Nullable String styleId, @Nullable ResourceLocation folderIcon,
                     Component name, BooleanSupplier selected, Runnable onClick) {
        super(x, y, SIZE, SIZE, name);
        this.kind = kind;
        this.styleId = styleId;
        this.folderIcon = folderIcon;
        this.selected = selected;
        this.onClick = onClick;
    }

    public static HairTile style(int x, int y, String styleId, Component name, BooleanSupplier selected, Runnable onClick) {
        return new HairTile(x, y, Kind.STYLE, styleId, null, name, selected, onClick);
    }

    public static HairTile folder(int x, int y, ResourceLocation icon, Component name, Runnable onClick) {
        return new HairTile(x, y, Kind.FOLDER, null, icon, name, () -> false, onClick);
    }

    public static HairTile special(int x, int y, Kind kind, Component name, Runnable onClick) {
        return new HairTile(x, y, kind, null, null, name, () -> false, onClick);
    }

    public HairTile favorite(BooleanSupplier isFavorite, Runnable toggle) {
        this.favorite = isFavorite;
        this.onRightClick = toggle;
        return this;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        int frame = selected.getAsBoolean() ? ZenkaiPalette.BORDER_MID
                : isHovered() ? ZenkaiPalette.LABEL_ON_PANEL
                : ZenkaiPalette.MUTED_ON_PANEL;
        g.fill(x, y, x + SIZE, y + SIZE, frame);
        g.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, ZenkaiPalette.BEIGE);
        int icon = 20, ix = x + (SIZE - icon) / 2, iy = y + (SIZE - icon) / 2;
        switch (kind) {
            case STYLE -> drawHead(g, x, y);
            case FOLDER -> {
                if (folderIcon != null && RaceTextureUtil.resourceExists(folderIcon)) {
                    g.blit(folderIcon, x + 3, y + 3, 0, 0, SIZE - 6, SIZE - 6, SIZE - 6, SIZE - 6);
                } else {
                    LibraryIcons.FOLDER.draw(g, ix, iy, icon);
                }
            }
            case PLUS -> LibraryIcons.PLUS.draw(g, ix, iy, icon);
            case BACK -> {
                LibraryIcons.FOLDER.draw(g, ix, iy, icon);
                g.drawString(Minecraft.getInstance().font, "..", ix + 6, iy + 7, 0xFF3A2416, false);
            }
        }
        if (favorite.getAsBoolean()) LibraryIcons.favoriteBadge(g, x, y, SIZE);
    }

    private void drawHead(GuiGraphics g, int x, int y) {
        var player = Minecraft.getInstance().player;
        if (player == null || styleId == null) return;
        var visual = player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        String previous = visual.getHairStyleId();
        visual.setHairStyleId(styleId);
        try {
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, x + 1, y + 1, x + SIZE - 1, y + SIZE - 1,
                    RENDER_SCALE, Y_OFFSET, 0.35f, 0.05f, player);
        } finally {
            visual.setHairStyleId(previous);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onClick.run();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && onRightClick != null && this.active && this.visible && isMouseOver(mouseX, mouseY)) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            onRightClick.run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
