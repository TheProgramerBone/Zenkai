package com.hmc.zenkai.client.gui.widgets;

import com.hmc.zenkai.client.gui.FaceThumbnails;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.feature.race.layer.FaceOverlayGeoLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

/**
 * Casilla de la pestaña «Marcas» de HeadAppearanceScreen (2026-11-01): la cara del jugador con ESA marca encima, mismo
 * marco que {@link FacePartTile}. A diferencia de los rasgos, las marcas se activan y desactivan cada una por separado
 * (hasta {@code PlayerVisualAttachment.MAX_MARKINGS}), así que el marco naranja indica «puesta», no «la elegida».
 * La marca es un PNG de 256x256 con la cara canónica en x16-31/y16-31: se pinta esa región a escala {@link FacePartTile#SCALE}.
 */
public class MarkingTile extends AbstractWidget {

    private final ResourceLocation markId;
    private final BooleanSupplier on;
    private final Runnable onClick;

    public MarkingTile(int x, int y, ResourceLocation markId, Component name, BooleanSupplier on, Runnable onClick) {
        super(x, y, FacePartTile.SIZE, FacePartTile.SIZE, name);
        this.markId = markId;
        this.on = on;
        this.onClick = onClick;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), size = FacePartTile.SIZE;
        int frame = on.getAsBoolean() ? ZenkaiPalette.BORDER_MID
                : isHovered() ? ZenkaiPalette.LABEL_ON_PANEL
                : ZenkaiPalette.MUTED_ON_PANEL;
        g.fill(x, y, x + size, y + size, frame);
        var player = Minecraft.getInstance().player;
        if (player != null) FaceThumbnails.draw(g, player, null, null, x + 1, y + 1, FacePartTile.SCALE);
        int side = FaceThumbnails.size(FacePartTile.SCALE);
        // Si este cliente no tiene la textura de la marca, la casilla queda con la cara sola (como en el render 3D).
        ResourceLocation tex = FaceOverlayGeoLayer.markingTexture(markId);
        if (Minecraft.getInstance().getResourceManager().getResource(tex).isPresent()) {
            g.blit(tex, x + 1, y + 1, side, side, 16f, 16f, 16, 16, 256, 256);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.visible && isValidClickButton(button) && clicked(mouseX, mouseY)) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            onClick.run();
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
