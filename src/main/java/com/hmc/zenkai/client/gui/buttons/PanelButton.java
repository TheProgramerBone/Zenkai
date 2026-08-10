package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.client.gui.NineSlice;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Botón CON fondo, a cualquier tamaño, usando btn_wide.png en 9-slice.
 * TextOnlyButton sigue existiendo y sigue siendo lo correcto para elementos que viven dentro
 * de una lista (una fila de habilidad, un deseo del menú de Shenlong): ahí el marco añadiría
 * ruido. PanelButton es para las acciones TERMINALES — Confirmar, Volver, Guardar, Cancelar —
 * donde el jugador necesita ver dónde pulsar sin barrer la pantalla con el ratón.
 * Ese era el fallo de las pantallas de deseo: "Confirm" y "Back" eran texto suelto en mitad
 * del beige, indistinguible de la descripción que tenían justo encima.
 * Dos jerarquías:
 *   PRIMARY   → la acción que el jugador vino a hacer (Confirmar). Texto dorado.
 *   SECONDARY → la salida (Volver, Cancelar). Texto blanco.
 * Se distinguen solo por el color del texto, no por el fondo: dos fondos distintos exigirían
 * un asset más y la diferencia de peso ya la da el orden de lectura.
 */
public class PanelButton extends AbstractButton {

    /** Medidas estándar del pie de página. Cualquier pantalla que las cambie desentona. */
    public static final int W = 84;
    public static final int H = 20;

    public enum Kind { PRIMARY, SECONDARY }

    private final Runnable onClick;
    private final Kind kind;

    public PanelButton(int x, int y, int w, int h, Component message, Kind kind, Runnable onClick) {
        super(x, y, w, h, message);
        this.onClick = Objects.requireNonNull(onClick);
        this.kind = kind;
    }

    public static PanelButton primary(int x, int y, Component message, Runnable onClick) {
        return new PanelButton(x, y, W, H, message, Kind.PRIMARY, onClick);
    }

    public static PanelButton secondary(int x, int y, Component message, Runnable onClick) {
        return new PanelButton(x, y, W, H, message, Kind.SECONDARY, onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.active && isMouseOver(mouseX, mouseY);

        // Un botón inactivo se apaga TIÑENDO la textura, no pintando un velo sobre su rect.
        // Btn_wide tiene las esquinas recortadas, igual que el marco del panel, así que un
        // g.fill dejaba cuatro muescas oscuras sobre el beige alrededor del botón. setColor
        // multiplica solo los píxeles que se dibujan.
        //
        // Se apaga en vez de ocultarse para que el jugador vea que la acción EXISTE y le falta
        // algo (TP, un nombre escrito), en vez de creer que la pantalla no la tiene.
        if (!this.active) g.setColor(0.55f, 0.55f, 0.55f, 0.8f);
        else if (hovered) g.setColor(1.15f, 1.15f, 1.15f, 1f);
        NineSlice.button(g, getX(), getY(), getWidth(), getHeight());
        g.setColor(1f, 1f, 1f, 1f);

        int color = !this.active
                ? ZenkaiPalette.TEXT_OFF
                : hovered ? ZenkaiPalette.TEXT_HOVER
                : (kind == Kind.PRIMARY ? ZenkaiPalette.GOLD : ZenkaiPalette.TEXT);

        g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2,
                color);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}