package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PlusIconButton extends AbstractButton {

    // Ruta directa al PNG (16x16 recomendado)
    private static final ResourceLocation TEX_NORMAL =
            ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID,
                    "textures/gui/btn_plus.png"
            );

    private static final ResourceLocation TEX_HOVER =
            ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID,
                    "textures/gui/btn_plus_highlight.png"
            );

    // Mismo PNG que XIconButton (12x12): reutilizarlo aquí evita un asset nuevo solo para
    // "este + está bloqueado" — la cruz ya es el lenguaje visual de "no" en este mod.
    private static final ResourceLocation TEX_CROSS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x.png");

    // Acción al pulsar
    private final Runnable onClick;

    /** Si es true, inactivo se pinta como una cruz OSCURA (btn_x.png teñido, no solo el +
     *  atenuado) — para cuando la razón de bloqueo es algo que el jugador debe distinguir de
     *  un simple "todavía no" a secas, como "no te alcanza el TP" (ver StatsScreen). Opt-in:
     *  por defecto false, así que SkillsScreen/PartyScreen no cambian de aspecto. */
    private boolean crossWhenDisabled = false;

    public PlusIconButton(int x, int y, Runnable onClick) {
        super(x, y, 12, 12, Component.empty());
        this.onClick = Objects.requireNonNull(onClick);
    }

    public PlusIconButton disabledAsCross() {
        this.crossWhenDisabled = true;
        return this;
    }

    @Override
    public void onPress() {
        onClick.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.active && crossWhenDisabled) {
            g.setColor(0.4F, 0.4F, 0.4F, 1.0F); // más oscuro que el simple atenuado: se lee como "no", no como "espera"
            g.blit(TEX_CROSS, this.getX(), this.getY(), 0, 0, this.width, this.height, 12, 12);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        ResourceLocation tex = (this.active && this.isHoveredOrFocused()) ? TEX_HOVER : TEX_NORMAL;
        if (!this.active) g.setColor(0.45F, 0.45F, 0.45F, 1.0F); // sin TP/MND: apagado
        g.blit(tex, this.getX(), this.getY(), 0, 0, this.width, this.height, 12, 12);
        if (!this.active) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
    }
}
