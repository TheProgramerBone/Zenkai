package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.feature.dialogue.DialogueChoicePayload;
import com.hmc.zenkai.feature.dialogue.OpenDialoguePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Diálogo con un NPC (Yemma, maestros): el NPC a la izquierda, su nombre, el texto escrito letra a letra y, tras la última
 * página, las opciones. Clic, Espacio o Intro adelantan (primero terminan de escribir la frase, luego pasan de página);
 * las teclas 1-9 eligen opción. No pausa el juego. El marco reutiliza la paleta de los maestros (MASTER_*) dibujada con
 * rellenos: si más adelante se quiere textura propia, va en un {@code tools/gen_dialogue_screen.py}.
 */
public class DialogueScreen extends Screen {

    private static final int PANEL_W = 400;
    /** Alto mínimo: el retrato de Yemma/maestros necesita este espacio aunque el texto sea corto. */
    private static final int PANEL_H_MIN = 118;
    private static final int PANEL_MARGIN = 10;
    private static final int PORTRAIT_W = 84;
    private static final int PADDING = 8;
    /** Letras reveladas por tick. */
    private static final float CHARS_PER_TICK = 1.6f;
    private static final int BTN_H = 18;
    private static final int BTN_GAP = 6;
    private static final int BTN_STACK_GAP = 4;
    /** Margen interior de un botón a cada lado del texto de su etiqueta. */
    private static final int BTN_PAD_X = 8;
    private static final int BTN_MIN_W = 70;
    /** Cabecera: nombre del que habla + hueco antes del texto. */
    private static final int HEADER_H = 14;

    private final int entityId;
    private final Component speaker;
    private final List<Component> pages;
    private final List<OpenDialoguePayload.Choice> choices;

    private int page = 0;
    private float revealed = 0f;
    /**
     * Cada página ya partida en líneas del ancho del texto (respeta los saltos de línea del texto) y troceada para que NUNCA pase
     * del alto de la pantalla: una página demasiado larga se reparte en varias. Se rehace en {@link #init()} (cambio de ventana).
     */
    private List<List<String>> wrapped = List.of(List.of());
    /** Líneas de la página actual y su total de letras (para la máquina de escribir). */
    private List<String> lines = List.of();
    private int totalChars = 0;
    private boolean initialized = false;

    private int left, top, textW, panelH;
    /** Las opciones no caben en una fila: van apiladas, una por fila. */
    private boolean stacked;
    private int[] buttonW = new int[0];

    public DialogueScreen(OpenDialoguePayload p) {
        super(p.speaker());
        this.entityId = p.entityId();
        this.speaker = p.speaker();
        this.pages = new ArrayList<>(p.pages());
        this.choices = new ArrayList<>(p.choices());
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        int w = Math.min(PANEL_W, this.width - 2 * PANEL_MARGIN);
        left = (this.width - w) / 2;
        textW = w - PORTRAIT_W - 3 * PADDING;

        // Las opciones primero: su altura (una fila o apiladas) decide cuánto sitio queda para el texto.
        int n = choices.size();
        buttonW = new int[n];
        int total = Math.max(0, n - 1) * BTN_GAP;
        for (int i = 0; i < n; i++) {
            buttonW[i] = Math.max(BTN_MIN_W, font.width((i + 1) + ". " + choices.get(i).label().getString()) + 2 * BTN_PAD_X);
            total += buttonW[i];
        }
        stacked = total > textW;
        int choiceBlockH = n == 0 ? 0 : stacked ? n * BTN_H + (n - 1) * BTN_STACK_GAP : BTN_H;

        int lineH = font.lineHeight + 2;
        int chrome = PADDING + HEADER_H + 4 + (n == 0 ? 0 : choiceBlockH + 4) + PADDING;
        int maxPanelH = Math.max(PANEL_H_MIN, this.height - 2 * PANEL_MARGIN);
        int maxLines = Math.max(3, (maxPanelH - chrome) / lineH);

        // Parte cada página en líneas (font.split respeta los saltos de línea del texto) y trocea las que no caben en pantalla.
        List<List<String>> out = new ArrayList<>();
        int most = 1;
        for (Component pg : pages) {
            List<String> all = new ArrayList<>();
            for (FormattedCharSequence seq : font.split(pg, textW)) {
                StringBuilder sb = new StringBuilder();
                seq.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
                all.add(sb.toString());
            }
            if (all.isEmpty()) all.add("");
            for (int i = 0; i < all.size(); i += maxLines) {
                List<String> chunk = new ArrayList<>(all.subList(i, Math.min(all.size(), i + maxLines)));
                most = Math.max(most, chunk.size());
                out.add(chunk);
            }
        }
        if (out.isEmpty()) out.add(List.of(""));
        wrapped = out;

        // Alto = lo que pide la página más larga (constante entre páginas: que el marco no salte al pasar de una a otra).
        panelH = Math.min(maxPanelH, Math.max(PANEL_H_MIN, chrome + most * lineH));
        top = this.height - panelH - PANEL_MARGIN;

        page = Math.min(page, wrapped.size() - 1);
        wrapPage();
        // Si es un cambio de tamaño de ventana, no se vuelve a escribir la frase desde cero.
        if (initialized) revealed = totalChars;
        initialized = true;
        rebuildChoices();
    }

    private void wrapPage() {
        lines = wrapped.get(page);
        int n = 0;
        for (String l : lines) n += l.length();
        totalChars = n;
    }

    private boolean pageDone() { return revealed >= totalChars; }
    private boolean lastPage() { return page >= wrapped.size() - 1; }

    private void rebuildChoices() {
        clearWidgets();
        if (!(pageDone() && lastPage())) return;
        int n = choices.size();
        int x0 = left + PORTRAIT_W + 2 * PADDING;
        for (int i = 0; i < n; i++) {
            OpenDialoguePayload.Choice c = choices.get(i);
            final int idx = i;
            int bx, by, bw;
            if (stacked) {
                // Una por fila, de ancho completo, de arriba abajo hasta el borde inferior del panel.
                bw = textW;
                bx = x0;
                by = top + panelH - PADDING - (n * BTN_H + (n - 1) * BTN_STACK_GAP) + i * (BTN_H + BTN_STACK_GAP);
            } else {
                bw = buttonW[i];
                bx = x0;
                for (int k = 0; k < i; k++) bx += buttonW[k] + BTN_GAP;
                by = top + panelH - PADDING - BTN_H;
            }
            addRenderableWidget(new ChoiceButton(bx, by, bw, BTN_H,
                    Component.literal((i + 1) + ". ").append(c.label()), () -> choose(idx)));
        }
    }

    private void choose(int i) {
        if (i < 0 || i >= choices.size()) return;
        String id = choices.get(i).id();
        // "leave" no necesita viajar: solo cierra. El resto lo valida el servidor contra el estado real.
        if (!"leave".equals(id)) PacketDistributor.sendToServer(new DialogueChoicePayload(entityId, id));
        onClose();
    }

    private void advance() {
        if (!pageDone()) {
            revealed = totalChars;
        } else if (!lastPage()) {
            page++;
            revealed = 0f;
            wrapPage();
        } else if (choices.isEmpty()) {
            onClose();
            return;
        }
        rebuildChoices();
    }

    @Override
    public void tick() {
        super.tick();
        if (!pageDone()) {
            revealed = Math.min(totalChars, revealed + CHARS_PER_TICK);
            if (pageDone()) rebuildChoices();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 32 || key == 257 || key == 335) {   // espacio, intro
            if (pageDone() && lastPage() && choices.size() == 1) choose(0);
            else advance();
            return true;
        }
        if (key >= 49 && key <= 57 && pageDone() && lastPage()) {   // 1-9
            choose(key - 49);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Sin el dim de vanilla: el mundo se ve detrás, como en una conversación y no en un menú.
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int w = Math.min(PANEL_W, this.width - 2 * PANEL_MARGIN);
        int right = left + w, bottom = top + panelH;

        // Marco de tres anillos + fondo, con la paleta de los maestros.
        g.fill(left, top, right, bottom, ZenkaiPalette.MASTER_BORDER_OUT);
        g.fill(left + 1, top + 1, right - 1, bottom - 1, ZenkaiPalette.MASTER_BORDER_MID);
        g.fill(left + 2, top + 2, right - 2, bottom - 2, ZenkaiPalette.MASTER_BORDER_IN);
        g.fill(left + 3, top + 3, right - 3, bottom - 3, ZenkaiPalette.MASTER_DIALOG_BG);
        g.fill(left + 3, top + 3, left + PORTRAIT_W + PADDING, bottom - 3, ZenkaiPalette.MASTER_DIALOG_PANEL);

        renderPortrait(g, mouseX, mouseY);

        int tx = left + PORTRAIT_W + 2 * PADDING;
        g.drawString(font, speaker, tx, top + PADDING, ZenkaiPalette.GOLD, true);

        int shown = (int) revealed;
        int y = top + PADDING + HEADER_H;
        for (String line : lines) {
            if (shown <= 0) break;
            String part = line.length() <= shown ? line : line.substring(0, shown);
            g.drawString(font, part, tx, y, ZenkaiPalette.TEXT, true);
            shown -= line.length();
            y += font.lineHeight + 2;
        }

        // Indicador de "hay más": parpadea cuando la frase ya está escrita y quedan páginas.
        if (pageDone() && !lastPage() && (System.currentTimeMillis() / 400) % 2 == 0) {
            g.drawString(font, "▼", right - PADDING - 8, bottom - PADDING - 8, ZenkaiPalette.GOLD, true);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPortrait(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.level == null) return;
        Entity e = minecraft.level.getEntity(entityId);
        if (!(e instanceof LivingEntity le)) return;
        // Encuadre por entidad: el alto de la caja (sin su SCALE) decide la escala, para que Yemma (enorme) y
        // Tori-Bot (pequeño) llenen el retrato igual que un Korin. Tope arriba para que lo pequeño no se infle.
        float baseH = Math.max(0.3f, le.getBbHeight() / Math.max(0.1f, le.getScale()));
        int boxH = panelH - 14;
        int scale = Mth.clamp((int) (boxH * 0.92f / baseH), 14, 56);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g,
                left + 4, top + 4, left + PORTRAIT_W + PADDING - 1, top + panelH - 4,
                scale, 0.0625F, mouseX, mouseY, le);
    }

    /** Botón de opción con el marco del diálogo (anillos ciruela, relleno oscuro, dorado al pasar el ratón). */
    private final class ChoiceButton extends Button {
        ChoiceButton(int x, int y, int w, int h, Component label, Runnable onPress) {
            super(x, y, w, h, label, b -> onPress.run(), DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = isHoveredOrFocused();
            int x = getX(), y = getY(), r = x + width, b = y + height;
            g.fill(x, y, r, b, hot ? ZenkaiPalette.MASTER_BORDER_HI : ZenkaiPalette.MASTER_BORDER_OUT);
            g.fill(x + 1, y + 1, r - 1, b - 1, ZenkaiPalette.MASTER_BORDER_MID);
            g.fill(x + 2, y + 2, r - 2, b - 2, hot ? ZenkaiPalette.MASTER_BORDER_MID : ZenkaiPalette.MASTER_DIALOG_PANEL);
            if (hot) g.fill(x + 2, y + 2, r - 2, y + 3, ZenkaiPalette.MASTER_BORDER_OUT);   // brillo superior
            g.drawCenteredString(font, getMessage(), x + width / 2, y + (height - font.lineHeight) / 2 + 1,
                    hot ? ZenkaiPalette.GOLD : ZenkaiPalette.TEXT);
        }
    }
}
