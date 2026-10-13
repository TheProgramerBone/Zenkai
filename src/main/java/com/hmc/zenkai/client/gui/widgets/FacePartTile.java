package com.hmc.zenkai.client.gui.widgets;

import com.hmc.zenkai.client.gui.FaceThumbnails;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Casilla de la cuadrícula de rasgos de HeadAppearanceScreen: la cara del jugador con ESTE rasgo
 * puesto (FaceThumbnails), marco de 1 px y clic para elegirlo. {@code id == null} es "ninguno".
 *
 * Marco: BORDER_MID (naranja del mod) si es el elegido — mismo lenguaje que ColorBoxButton
 * activo —, LABEL_ON_PANEL al pasar el ratón, MUTED_ON_PANEL en reposo. El nombre va en el
 * tooltip y bajo la cuadrícula, no dentro: a 34 px no cabe texto legible encima de la cara.
 */
public class FacePartTile extends AbstractWidget {

    /** Escala de la cara (16 texels × 2 = 32 px) + 1 px de marco por lado. */
    public static final int SCALE = 2;
    public static final int SIZE = FaceThumbnails.size(SCALE) + 2;

    private final FaceCategory category;
    @Nullable private final ResourceLocation id;
    private final BooleanSupplier selected;
    private final Runnable onClick;
    /** Clic derecho: favorito (Favorites). null = la casilla no admite favorito ("ninguno"). */
    @Nullable private Runnable onRightClick;
    private BooleanSupplier favorite = () -> false;

    public FacePartTile(int x, int y, FaceCategory category, @Nullable ResourceLocation id,
                        Component name, BooleanSupplier selected, Runnable onClick) {
        super(x, y, SIZE, SIZE, name);
        this.category = category;
        this.id = id;
        this.selected = selected;
        this.onClick = onClick;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        int frame = selected.getAsBoolean() ? ZenkaiPalette.BORDER_MID
                : isHovered() ? ZenkaiPalette.LABEL_ON_PANEL
                : ZenkaiPalette.MUTED_ON_PANEL;
        g.fill(x, y, x + SIZE, y + SIZE, frame);
        var player = Minecraft.getInstance().player;
        if (player != null) FaceThumbnails.draw(g, player, category, id, x + 1, y + 1, SCALE);
        if (favorite.getAsBoolean()) com.hmc.zenkai.client.gui.LibraryIcons.favoriteBadge(g, x, y, SIZE);
    }

    /** Activa el favorito con clic derecho: {@code isFavorite} pinta la estrella, {@code toggle} la cambia. */
    public FacePartTile favorite(BooleanSupplier isFavorite, Runnable toggle) {
        this.favorite = isFavorite;
        this.onRightClick = toggle;
        return this;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && onRightClick != null && this.active && this.visible && isMouseOver(mouseX, mouseY)) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            onRightClick.run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onClick.run();
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
