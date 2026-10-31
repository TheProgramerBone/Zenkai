package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.buttons.VArrowButton;
import com.hmc.zenkai.client.gui.screens.wishes.EnchantVillagerWishScreen;
import com.hmc.zenkai.client.gui.screens.wishes.ImmortalWishScreen;
import com.hmc.zenkai.client.gui.screens.wishes.RevivePetWishScreen;
import com.hmc.zenkai.client.gui.screens.wishes.RevivePlayerWishScreen;
import com.hmc.zenkai.client.gui.screens.wishes.TrainingPointsWishScreen;
import com.hmc.zenkai.config.ServerConfig.WishType;
import com.hmc.zenkai.content.entity.overworld.ShenLongEntity;
import com.hmc.zenkai.feature.wishes.ClientWishToggles;
import com.hmc.zenkai.feature.wishes.OpenStackWishPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Menú de deseos de Shenlong.
 * Cambios respecto a la versión anterior:
 *   - hereda de ZenkaiPanelScreen, así que el panel, el título y el fondo dejan de estar
 *     duplicados aquí;
 *   - los deseos ya NO son texto suelto: cada uno es una fila con fondo que se ilumina al
 *     pasar el ratón, con un número de orden. Antes eran seis líneas centradas idénticas y no
 *     había forma de saber cuál estaba bajo el cursor hasta que cambiaba de color el texto;
 *   - el contador de deseos restantes pasa a ser una fila propia con separador, en vez de un
 *     texto marrón oscuro sobre la marca de agua 悟 (donde era casi ilegible);
 *   - sin pie de página: aquí no hay nada que confirmar, se elige un deseo. Un "Confirmar"
 *     en esta pantalla no tendría a qué aplicarse.
 * El título va en verde dragón por titleColor(), no con §2§l dentro del archivo de idioma:
 * los códigos de formato en el lang sobrevivían al toUpperCase de ScreenTitle y hacían que el
 * color del título dependiera de la traducción.
 */
public class ShenlongWishScreen extends ShenronPanelScreen {

    private static final int ROW_H = 22;
    private static final int ROW_GAP = 4;
    private static final int LIST_TOP = CONTENT_TOP + 24;

    /** Una entrada del menú. El registro existe para poder pintar la fila y el botón juntos. */
    private record WishRow(TextOnlyButton button, Component label, net.minecraft.world.item.ItemStack icon) {}

    /** Alto de las flechas de desplazamiento y aire sobre ellas. */
    private static final int ARROW_H = 12;
    private static final int ARROW_W = 12;
    /**
     * Posición de las flechas respecto al centro del panel (x del borde izquierdo de cada una). Cada una queda a
     * mitad de camino entre dos esferas del fondo (la 3ª y la 4ª, la 4ª y la 5ª): a 20,5 px del eje de las esferas,
     * que está en x=128,5 y no en 128,0 como el panel (la esfera mide 15 px, impar; el panel 256, par).
     */
    private static final int ARROW_UP_X = -26;
    private static final int ARROW_DOWN_X = 15;
    /** Distancia del borde inferior del panel al borde superior de las flechas: su centro cae en el punto medio de las dos alturas de las esferas. */
    private static final int ARROW_FROM_BOTTOM = 30;

    private final List<WishRow> rows = new ArrayList<>();
    private TextOnlyButton stackWishButton;
    private VArrowButton upArrow;
    private VArrowButton downArrow;
    /** Índice de la primera fila visible. Sobrevive a un redimensionado (se acota en layoutRows). */
    private int scroll = 0;
    private int visibleRows = 0;

    public ShenlongWishScreen() {
        super(Component.translatable("screen.zenkai.shenlong_wish"), null);
    }

    @Override protected boolean hasFooter() { return false; }

    @Override
    protected void initContent() {
        rows.clear();
        stackWishButton = null;

        int w = contentWidth() - 8;
        int x = centerX() - w / 2;
        int y = panelTop + LIST_TOP;

        // Solo se crean los deseos habilitados; el cursor avanza sin dejar huecos.
        if (ClientWishToggles.isEnabled(WishType.STACK)) {
            stackWishButton = addWish(x, y, w, "screen.zenkai.option.stack", WishIcons.stack(), () -> {
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) conn.send(new OpenStackWishPayload());
            });
            y += ROW_H + ROW_GAP;
        }
        if (ClientWishToggles.isEnabled(WishType.REVIVE_PLAYER)) {
            addWish(x, y, w, "screen.zenkai.wish.revive_player", WishIcons.revivePlayer(),
                    () -> mc.setScreen(new RevivePlayerWishScreen(this)));
            y += ROW_H + ROW_GAP;
        }
        if (ClientWishToggles.isEnabled(WishType.ENCHANT_VILLAGER)) {
            addWish(x, y, w, "screen.zenkai.wish.enchant_villager", WishIcons.enchantVillager(),
                    () -> mc.setScreen(new EnchantVillagerWishScreen(this)));
            y += ROW_H + ROW_GAP;
        }
        if (ClientWishToggles.isEnabled(WishType.IMMORTAL)) {
            addWish(x, y, w, "screen.zenkai.wish.immortal", WishIcons.immortal(),
                    () -> mc.setScreen(new ImmortalWishScreen(this)));
            y += ROW_H + ROW_GAP;
        }
        // Legendario: para cualquier raza con forma legendaria que aún no lo sea (el servidor lo vuelve a validar).
        if (ClientWishToggles.isEnabled(WishType.LEGENDARY) && mc.player != null) {
            var stats = mc.player.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_STATS.get());
            boolean canAsk = stats.getRace() == com.hmc.zenkai.feature.Race.SAIYAN
                    || com.hmc.zenkai.feature.forms.LegendaryForms.formOf(stats.getRace()) != null;
            boolean majinBlocks = com.hmc.zenkai.config.ServerConfig.majinLegendaryExclusive()
                    && mc.player.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_VISUAL.get()).isMajinControlled();
            if (canAsk && !stats.isLegendary() && !majinBlocks) {
                addWish(x, y, w, "screen.zenkai.wish.legendary", WishIcons.legendary(),
                        () -> mc.setScreen(new com.hmc.zenkai.client.gui.screens.wishes.LegendaryWishScreen(this)));
                y += ROW_H + ROW_GAP;
            }
        }
        if (ClientWishToggles.isEnabled(WishType.TRAINING_POINTS)) {
            addWish(x, y, w, "screen.zenkai.wish.training_points", WishIcons.trainingPoints(),
                    () -> mc.setScreen(new TrainingPointsWishScreen(this)));
            y += ROW_H + ROW_GAP;
        }
        // Revivir mascota no tiene toggle de configuración: siempre disponible.
        addWish(x, y, w, "screen.zenkai.wish.revive_pet", WishIcons.revivePet(),
                () -> mc.setScreen(new RevivePetWishScreen(this)));

        // Flechas de desplazamiento: botones aparte, no parte de la textura del panel. Abajo y centradas.
        int ay = panelTop + BG_H - ARROW_FROM_BOTTOM;
        upArrow = addRenderableWidget(new VArrowButton(centerX() + ARROW_UP_X, ay, VArrowButton.Dir.UP, () -> scrollBy(-1)));
        downArrow = addRenderableWidget(new VArrowButton(centerX() + ARROW_DOWN_X, ay, VArrowButton.Dir.DOWN, () -> scrollBy(1)));
        layoutRows();
    }

    /** Recoloca las filas según el desplazamiento: solo las de la ventana son visibles y clicables. */
    private void layoutRows() {
        int top = panelTop + LIST_TOP;
        int bottom = panelTop + BG_H - ARROW_FROM_BOTTOM - 4;
        // Si todo cabe no hacen falta flechas y se aprovecha ese hueco para filas.
        int fitAll = (panelTop + BG_H - PAD - top + ROW_GAP) / (ROW_H + ROW_GAP);
        boolean overflow = rows.size() > fitAll;
        if (overflow) fitAll = Math.max(1, (bottom - top + ROW_GAP) / (ROW_H + ROW_GAP));
        visibleRows = Math.min(rows.size(), fitAll);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows));
        for (int i = 0; i < rows.size(); i++) {
            TextOnlyButton b = rows.get(i).button();
            boolean shown = i >= scroll && i < scroll + visibleRows;
            b.visible = shown;
            b.setY(top + (i - scroll) * (ROW_H + ROW_GAP));
        }
        upArrow.visible = downArrow.visible = overflow;
        upArrow.active = scroll > 0;
        downArrow.active = scroll + visibleRows < rows.size();
    }

    private void scrollBy(int rowsDelta) {
        scroll += rowsDelta;
        layoutRows();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (upArrow != null && upArrow.visible && scrollY != 0) {
            scrollBy(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private TextOnlyButton addWish(int x, int y, int w, String langKey, net.minecraft.world.item.ItemStack icon,
                                   Runnable onClick) {
        Component label = Component.translatable(langKey);
        // El botón no pinta nada: la fila entera (icono, texto, fondo) la dibuja renderContent, alineada a la izquierda.
        TextOnlyButton b = addRenderableWidget(new TextOnlyButton(x, y, w, ROW_H, Component.empty(), onClick)
                .onDark());
        rows.add(new WishRow(b, label, icon));
        return b;
    }

    @Override
    public void tick() {
        super.tick();
        boolean full = mc.player != null && mc.player.getInventory().getFreeSlot() == -1;
        if (stackWishButton != null) {
            stackWishButton.active = !full;
            stackWishButton.setTooltip(full
                    ? Tooltip.create(Component.translatable("screen.zenkai.need_inventory_space"))
                    : null);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Cabecera: deseos restantes + separador.
        int remaining = nearbyWishesRemaining();
        if (remaining >= 0) {
            drawCenteredOnPanel(g,
                    Component.translatable("screen.zenkai.shenlong_wish.remaining", remaining),
                    panelTop + CONTENT_TOP, ZenkaiPalette.SHENRON_TEXT);
        }
        drawDivider(g, panelTop + CONTENT_TOP + 14);

        // Fondo de cada fila: hundido en reposo, iluminado bajo el cursor. Es lo que convierte
        // una lista de texto en una lista de opciones.
        for (int i = scroll; i < scroll + visibleRows && i < rows.size(); i++) {
            TextOnlyButton b = rows.get(i).button();
            boolean hovered = b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
                    && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();

            g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(),
                    hovered ? ZenkaiPalette.SHENRON_ROW_HOVER : ZenkaiPalette.SHENRON_ROW);
            // Cinta izquierda: ancla la vista y da a la fila un lado "fuerte".
            g.fill(b.getX(), b.getY(), b.getX() + 2, b.getY() + b.getHeight(),
                    hovered ? ZenkaiPalette.SHENRON_BORDER_OUT : ZenkaiPalette.SHENRON_BORDER_MID);

            // Icono del deseo (un ítem vanilla, ver WishIcons) y su nombre, alineados a la izquierda.
            WishRow r = rows.get(i);
            int iconY = b.getY() + (ROW_H - 16) / 2;
            if (!b.active) g.setColor(1f, 1f, 1f, 0.45f);
            g.renderFakeItem(r.icon(), b.getX() + 7, iconY);
            g.setColor(1f, 1f, 1f, 1f);
            PanelText.onDark(g, this.font, r.label(), b.getX() + 7 + 16 + 7, b.getY() + (ROW_H - 8) / 2,
                    !b.active ? ZenkaiPalette.SHENRON_MUTED : hovered ? ZenkaiPalette.TEXT_HOVER : ZenkaiPalette.SHENRON_TEXT);
        }
    }

    /** wishesRemaining del ShenLongEntity más cercano en cliente; -1 si no hay ninguno. */
    private int nearbyWishesRemaining() {
        if (mc.player == null || mc.level == null) return -1;
        return mc.level.getEntitiesOfClass(ShenLongEntity.class, mc.player.getBoundingBox().inflate(48))
                .stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)))
                .map(ShenLongEntity::getWishesRemaining)
                .orElse(-1);
    }
}