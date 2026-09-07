package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Flecha ARRIBA/ABAJO de 12x12, para mover una fila dentro de una lista.
 * NO tiene textura propia: reutiliza la de {@link ArrowIconButton} (btn_arrow_left) girándola
 * con el PoseStack. En coordenadas de GUI la Y crece hacia ABAJO, así que Axis.ZP gira en
 * sentido horario en pantalla: la punta de la flecha izquierda (que apunta a -X) rotada +90º
 * queda mirando hacia ARRIBA, y -90º hacia ABAJO. Un solo asset da las dos direcciones con el
 * mismo biselado, y si algún día se retoca la flecha horizontal estas heredan el cambio solas.
 * El giro es del quad entero, luces incluidas, así que el bisel queda iluminado desde un lado
 * distinto que el de las flechas horizontales — a 12 px y con la paleta de 6 colores planos de
 * la familia btn_x/btn_trash no se nota, y evita mantener cuatro PNG más.
 */
public class VArrowButton extends AbstractButton {

    public enum Dir { UP, DOWN }

    private static final int SIZE = 12;

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(
            Zenkai.MOD_ID, "textures/gui/btn_arrow_left.png");
    private static final ResourceLocation TEX_HL = ResourceLocation.fromNamespaceAndPath(
            Zenkai.MOD_ID, "textures/gui/btn_arrow_left_highlight.png");

    private final Dir dir;
    private final Runnable onClick;

    public VArrowButton(int x, int y, Dir dir, Runnable onClick) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.dir = dir;
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Mismo criterio de hover que ArrowIconButton: isMouseOver y no isHoveredOrFocused,
        // para que el foco del teclado no deje una flecha encendida sin el ratón encima.
        ResourceLocation tex = (this.active && this.isMouseOver(mouseX, mouseY)) ? TEX_HL : TEX;
        if (!this.active) g.setColor(0.45F, 0.45F, 0.45F, 1.0F);

        PoseStack pose = g.pose();
        pose.pushPose();
        // Girar alrededor del centro del icono: trasladar al centro, rotar, y volver.
        pose.translate(this.getX() + SIZE / 2.0F, this.getY() + SIZE / 2.0F, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(dir == Dir.UP ? 90.0F : -90.0F));
        pose.translate(-SIZE / 2.0F, -SIZE / 2.0F, 0.0F);
        g.blit(tex, 0, 0, 0, 0, SIZE, SIZE, SIZE, SIZE);
        pose.popPose();

        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
