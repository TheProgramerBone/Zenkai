package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.menu.EnergyGeneratorMenu;
import com.hmc.zenkai.feature.generator.GeneratorFuel;
import com.hmc.zenkai.feature.generator.GeneratorFuels;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla del generador. Tres lecturas y nada más:
 *   barra vertical de FE a la izquierda (depósito: sube y baja),
 *   cola de seis huecos en el centro con el progreso del que arde debajo,
 *   panel de datos a la derecha con capacidad, carga actual y FE/tick.
 * POR QUÉ NO HAY BOTONES: el generador no decide nada. Metes combustible y quema. Cualquier
 * control que se le añadiera sería un ajuste que el jugador tendría que entender para usar una
 * máquina cuyo contrato cabe en una frase.
 * LA PLACA DE CABECERA ES ESTRECHA A PROPÓSITO (8..140, las mismas medidas que la del banco de
 * scouter). Ocupando el ancho entero, el título parecía el doble de grande sin haber cambiado
 * de fuente: lo que desproporciona un texto es el marco vacío alrededor, no su tamaño.
 * COORDENADAS LOCALES en renderLabels, ABSOLUTAS en renderFloating. Es la regla de
 * TechPanelScreen y la fuente habitual de tooltips descolocados: el ratón que llega a
 * renderLabels sigue siendo absoluto aunque la matriz esté trasladada.
 */
public class EnergyGeneratorScreen extends TechPanelScreen<EnergyGeneratorMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/energy_generator.png");

    // ── Layout (coordenadas del PNG) ─────────────────────────────────────────
    private static final int TITLE_X = 12, TITLE_Y = 7;
    private static final int LED_X = 144, LED_Y = 2;

    /** Barra vertical de FE. El marco ya está pintado; esto es el interior. */
    private static final int BAR_X = 18, BAR_Y = 32, BAR_W = 16, BAR_H = 104;

    /** Canal del progreso de quemado, bajo la rejilla. */
    private static final int BURN_X = 46, BURN_Y = 87, BURN_W = 60, BURN_H = 6;

    /** Panel de lectura de la derecha. */
    private static final int INFO_X = 126, INFO_Y = 38, INFO_LH = 11;
    private static final int INFO_RIGHT = 236;

    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 157;

    public EnergyGeneratorScreen(EnergyGeneratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation background() { return BG; }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawTitle(g, TITLE_X, TITLE_Y);
        drawLed(g, LED_X, LED_Y, menu.isBurning());
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);

        // Depósito de FE. Verde cuando arde y cian en reposo: el color dice de un vistazo si
        // la barra está subiendo o solo vaciándose, sin tener que mirarla dos segundos.
        drawVBar(g, BAR_X, BAR_Y, BAR_W, BAR_H, menu.energyFraction(),
                menu.isBurning() ? ZenkaiTechPalette.LED_GREEN_MID : ZenkaiTechPalette.BAR_FILL);

        // Progreso del ítem que arde. El canal se dibuja siempre, aunque no haya nada
        // quemando: una barra que aparece y desaparece mueve el resto de la lectura.
        drawBar(g, BURN_X, BURN_Y, BURN_W, BURN_H, menu.burnFraction());

        int y = INFO_Y;
        y = infoRow(g, y, "screen.zenkai.generator.stored",
                fmtFe(menu.energy()), ZenkaiTechPalette.TEXT_ON_SCREEN);
        y = infoRow(g, y, "screen.zenkai.generator.capacity",
                fmtFe(menu.capacity()), ZenkaiTechPalette.DIM_ON_SCREEN);

        y += 4;
        boolean burning = menu.isBurning();
        y = infoRow(g, y, "screen.zenkai.generator.output",
                burning ? menu.fePerTick() + " FE/t" : "—",
                burning ? ZenkaiTechPalette.MAXED_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN);

        // Segundos restantes del ítem actual. En segundos y no en ticks: nadie sabe cuánto
        // dura un tick, y el mundo sabe cuánto dura un minuto.
        infoRow(g, y, "screen.zenkai.generator.remaining",
                burning ? fmtTime(menu.burnTicks()) : "—",
                burning ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN);
    }

    /** Etiqueta a la izquierda, valor pegado al borde derecho del panel. */
    private int infoRow(GuiGraphics g, int y, String key, String value, int valueColor) {
        PanelText.onDark(g, font, Component.translatable(key), INFO_X, y,
                ZenkaiTechPalette.DIM_ON_SCREEN);
        PanelText.rightOnDark(g, font, Component.literal(value), INFO_RIGHT, y, valueColor);
        return y + INFO_LH;
    }

    /**
     * Cancela el tooltip de vanilla cuando vamos a dibujar el nuestro.
         * TechPanelScreen llama a renderTooltip() y DESPUÉS a renderFloating(), así que sin esto
     * los dos se pintan uno encima del otro y el resultado es ilegible. No basta con mover el
     * nuestro: el de vanilla ya estaría dibujado debajo.
         * El criterio vive en ownTooltipStack() y lo usan las dos funciones. Duplicarlo con dos
     * condiciones parecidas devolvería el solape en cuanto se separaran — o dejaría ítems sin
     * ningún tooltip, que es el mismo fallo por el otro lado.
     */
    @Override
    protected void renderTooltip(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        if (ownTooltipStack() != null) return;
        super.renderTooltip(g, mouseX, mouseY);
    }

    /**
     * El ítem cuyo tooltip dibujamos nosotros, o null si le toca a vanilla.
         * SOLO LOS SEIS HUECOS DE LA COLA. En el inventario del jugador manda vanilla: ahí un
     * carbón es un ítem cualquiera y el jugador espera ver su nombre, sus encantamientos y su
     * descripción, no una ficha técnica de combustible.
         * El índice se saca de menu.slots y NO de hoveredSlot.index: ese último es el índice
     * dentro de SU contenedor, así que el hueco 0 de la cola y el hueco 0 del inventario del
     * jugador valen los dos 0, y el filtro dejaría pasar la hotbar entera.
     */
    private ItemStack ownTooltipStack() {
        // Con algo agarrado en el cursor manda vanilla: el jugador está moviendo, no leyendo.
        if (!this.menu.getCarried().isEmpty()) return null;
        if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) return null;
        if (this.menu.slots.indexOf(this.hoveredSlot) >= EnergyGeneratorMenu.FUEL_SLOTS) return null;

        ItemStack stack = this.hoveredSlot.getItem();
        return GeneratorFuels.isFuel(stack) ? stack : null;
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        int lmx = localMouseX(mouseX), lmy = localMouseY(mouseY);

        if (inside(lmx, lmy, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.generator.stored")
                            .withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(menu.energy() + " / " + menu.capacity() + " FE"))));
            return;
        }

        if (inside(lmx, lmy, BURN_X - 1, BURN_Y - 1, BURN_W + 2, BURN_H + 2) && menu.isBurning()) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.generator.remaining")
                            .withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(fmtTime(menu.burnTicks())))));
            return;
        }

        // Tooltip del combustible bajo el cursor. Es el dato por el que un jugador abre esta
        // pantalla: saber si le compensa gastar el cristal aquí en vez de guardarlo.
        ItemStack stack = ownTooltipStack();
        if (stack == null) return;
        GeneratorFuel fuel = GeneratorFuels.of(stack);
        if (fuel == null) return;

        List<TipLine> lines = new ArrayList<>();
        lines.add(new TipLine(stack, stack.getHoverName().copy().withStyle(ChatFormatting.WHITE)));
        lines.add(TipLine.of(Component.translatable(
                        "screen.zenkai.generator.tip.output", fuel.fePerTick())
                .withStyle(ChatFormatting.AQUA)));
        lines.add(TipLine.of(Component.translatable(
                        "screen.zenkai.generator.tip.duration", fmtTime(fuel.ticks()))
                .withStyle(ChatFormatting.GRAY)));
        lines.add(TipLine.of(Component.translatable(
                        "screen.zenkai.generator.tip.total", fmtFe(fuel.totalFe()))
                .withStyle(ChatFormatting.GRAY)));
        if (stack.getCount() > 1) {
            lines.add(TipLine.of(Component.translatable(
                            "screen.zenkai.generator.tip.stack",
                            fmtFe(fuel.totalFe() * stack.getCount()))
                    .withStyle(ChatFormatting.DARK_GRAY)));
        }
        drawTooltip(g, mouseX, mouseY, lines);
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 1.600 -> "1.6k". Los números de FE llegan a seis cifras y el panel tiene 110 px. */
    private static String fmtFe(int fe) {
        if (fe < 1000) return fe + " FE";
        if (fe < 1_000_000) return String.format(Locale.ROOT, "%.1fk FE", fe / 1000.0);
        return String.format(Locale.ROOT, "%.2fM FE", fe / 1_000_000.0);
    }

    /** Ticks a mm:ss. Por debajo de un minuto, solo segundos. */
    private static String fmtTime(int ticks) {
        int seconds = ticks / 20;
        if (seconds < 60) return seconds + "s";
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}