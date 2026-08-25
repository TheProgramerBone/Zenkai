package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Tienda de un maestro. Pantalla PROPIA, no una pestaña de ZenkaiMenuScreen: es apaisada
 * (el maestro ocupa el tercio izquierdo) y no comparte navegación con el menú del jugador.
 * Solo se listan las habilidades cuyo campo "master" es este maestro. Estado por fila:
 *   - nivel 0 y puedes pagar  -> coste en blanco, clic compra
 *   - nivel 0 y no puedes     -> coste en ROJO, clic no hace nada
 *   - nivel >= 1              -> gris, "Aprendida" (el maestro solo da el nivel 1; los
 *                                siguientes se suben con TP desde la pantalla de skills)
 * Fondo: master_screen.png (ver tools/gen_master_screen.py), compartido por el conjunto de
 * maestros — lo que distingue a cada uno es su retrato 3D, no el fondo.
 */
public class MasterScreen extends Screen {

    // 320×180 con filas de 22px solo daba sitio a "Be able to fly using k…" antes de recortar:
    // la descripción no cabía ni truncada a una palabra útil. Se agranda el diálogo entero
    // (mismo lenguaje visual, solo más aire) en vez de encoger más el texto.
    private static final int BG_W = 360;
    private static final int BG_H = 210;

    /** Ancho del panel del maestro (izquierda): retrato + su nombre debajo. */
    private static final int PORTRAIT_W = 116;
    private static final int PADDING    = 10;
    /** Una sola línea por fila (nombre + coste): la descripción vive en el tooltip. */
    private static final int ROW_H      = 24;
    /** Separación bajo el título/TP/MND antes de la primera fila. */
    private static final int CONTENT_TOP = 34;

    private static final int SCROLLBAR_W   = 4;
    /** Aire entre el texto de coste y la barra: sin esto el número roza el thumb. */
    private static final int SCROLLBAR_GAP = 6;
    private static final int TOOLTIP_W     = 200;

    // ═══ FONDO: TEXTURA, NO DIBUJADO ═══
    //
    // Esta pantalla tenía paleta propia —fondo 0xF0100D14, borde 0xFF3B3550, pistas
    // 0xFF9A93AD— que no aparecía en ningún otro sitio del mod: los maestros parecían de otro
    // mod. Era deuda visual, no una decisión. Luego pasó a dibujar el marco de tres anillos y
    // los rellenos a mano con g.fill() (ver historial), con los colores ya de ZenkaiPalette,
    // para no bloquear a que existiera el asset. Ahora existe: master_screen.png, generado por
    // tools/gen_master_screen.py (ÚNICA fuente, no editar el PNG a mano), con el mismo marco de
    // tres anillos IN/MID/OUT + brillo de esquina y el panel del retrato ya horneados dentro.
    // Es UN solo archivo para el conjunto de maestros —Kami, Kaio, Korin y los que añada el
    // datapack—, igual que common_screen.png es uno solo para el conjunto de pestañas del menú: lo
    // que distingue a cada maestro es su retrato 3D, no el fondo.
    //
    // Sigue siendo opaco (no el POPUP_BG/BAR_BG_DARK de alfa parcial del popup lateral de la
    // ficha): ese popup flota junto al panel PRINCIPAL de Stats, que ya es opaco, así que un
    // pelín de transparencia ahí es acabado. MasterScreen no tiene ningún panel opaco detrás:
    // con alfa parcial el mundo se colaba bajo cada fila y la volvía ilegible.
    //
    // ACOPLAMIENTO A VIGILAR: BG_W/BG_H/PORTRAIT_W/PADDING de aquí abajo están DUPLICADOS en el
    // script Python (no puede leer las constantes Java). Si cambian aquí, regenerar la textura.
    private static final ResourceLocation BG_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/master_screen.png");

    private final String masterId;
    private final int entityId;

    private final List<SkillDef> rows = new ArrayList<>();
    private int scroll = 0;

    private int left, top;

    public MasterScreen(String masterId, int entityId) {
        super(Component.translatable("master.zenkai." + masterId));
        this.masterId = masterId;
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        left = (this.width - BG_W) / 2;
        top  = (this.height - BG_H) / 2;

        rows.clear();
        rows.addAll(SkillDef.taughtBy(masterId));
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    // ── Geometría ────────────────────────────────────────────────────────────

    private int listLeft()   { return left + PORTRAIT_W + PADDING; }
    /** Borde exterior de la lista, donde la scrollbar queda a ras. */
    private int trackRight() { return left + BG_W - PADDING; }
    private int scrollbarX() { return trackRight() - SCROLLBAR_W; }
    /** Borde derecho REAL del texto (nombre/coste/hover): deja sitio fijo a la scrollbar,
     *  se dibuje o no, para que la fila no salte de ancho al aparecer un maestro con más
     *  habilidades de las que caben. */
    private int listRight()  { return scrollbarX() - SCROLLBAR_GAP; }
    private int listTop()    { return top + CONTENT_TOP; }
    private int listHeight() { return BG_H - CONTENT_TOP - PADDING; }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }
    private int maxScroll()  { return Math.max(0, rows.size() - visibleRows()); }
    private int rowTop(int i){ return listTop() + (i - scroll) * ROW_H; }
    /** Y del texto de una fila, centrado verticalmente en ROW_H (fuente ≈ 9px de alto). */
    private int rowTextY(int y) { return y + (ROW_H - 9) / 2; }

    private boolean onScreen(int i) {
        int rel = i - scroll;
        return rel >= 0 && rel < visibleRows();
    }

    // ── Estado de una fila ───────────────────────────────────────────────────

    private PlayerStatsAttachment stats() {
        return minecraft == null || minecraft.player == null ? null
                : minecraft.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
    }

    /** ¿Le alcanza para el nivel 1? Mismo criterio que valida el servidor: TP y MIND libre. */
    private boolean canAfford(PlayerStatsAttachment st, SkillDef def) {
        if (st == null) return false;
        return st.getTP() >= def.tpCost() && st.mindFree() >= def.mindReqFor(1);
    }

    // ── Render ───────────────────────────────────────────────────────────────
    //
    // ORDEN DE RENDER (convención del mod, ver WeightScreen/TechniqueEditScreen):
    //   renderBackground() -> dim de vanilla + marco/fondo del panel.
    //   render() -> super.render() PRIMERO (dispara renderBackground()) y el contenido DESPUÉS.
    // Esta pantalla llamaba a this.renderBackground(...) A MANO al principio de render() Y
    // ADEMÁS terminaba con super.render(...), que vuelve a invocar renderBackground() por su
    // cuenta: el dim/blur de vanilla se pintaba DOS veces, la segunda ya con el retrato, el
    // título y las filas dibujados encima — por eso se veía borroso el panel entero y no solo
    // el mundo de fondo.

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG_TEX, left, top, 0, 0, BG_W, BG_H, BG_W, BG_H);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        renderMaster(g, mouseX, mouseY);

        PanelText.onDark(g, this.font, ScreenTitle.styled(this.title), listLeft(), top + PADDING,
                ZenkaiPalette.GOLD);

        PlayerStatsAttachment st = stats();
        if (st != null) {
            // TP y MIND separados y con SUS colores de rol, igual que en SkillsScreen: aquí
            // salían los dos en el mismo gris malva y había que leerlos para distinguirlos.
            Component tp = Component.translatable("screen.zenkai.skills.tp", st.getTP());
            PanelText.onDark(g, this.font, tp, listLeft(), top + PADDING + 11,
                    ZenkaiPalette.TP_ON_DARK);
            int free = st.mindFree();
            PanelText.onDark(g, this.font,
                    Component.translatable("screen.zenkai.skills.mind",
                            free, st.getAttribute(com.hmc.zenkai.feature.ZenkaiAttributes.MIND)),
                    listLeft() + this.font.width(tp) + 10, top + PADDING + 11,
                    free < 0 ? ZenkaiPalette.ERROR : ZenkaiPalette.MIND_ON_DARK);
        }

        if (rows.isEmpty()) {
            PanelText.onDark(g, this.font, Component.translatable("screen.zenkai.master.nothing"),
                    listLeft(), listTop(), ZenkaiPalette.TEXT_OFF);
        }

        // Recorte a la ventana visible: con scrollbar de verdad hace falta, o una fila a medio
        // salir se pintaría encima del marco superior/inferior del diálogo.
        g.enableScissor(left, listTop(), left + BG_W, listTop() + visibleRows() * ROW_H);
        SkillDef hoveredDef = null;
        for (int i = 0; i < rows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            renderRow(g, st, rows.get(i), y, i, mouseX, mouseY);
            if (mouseX >= listLeft() - 2 && mouseX <= listRight()
                    && mouseY >= y && mouseY < y + ROW_H - 1) {
                hoveredDef = rows.get(i);
            }
        }
        g.disableScissor();
        drawScrollbar(g);

        // El tooltip va fuera del scissor (y al final) o se recortaría o quedaría
        // tapado por la siguiente fila.
        if (hoveredDef != null) {
            Component desc = Component.translatable(hoveredDef.descKey());
            g.renderTooltip(this.font, this.font.split(desc, TOOLTIP_W), mouseX, mouseY);
        }
    }

    private void renderRow(GuiGraphics g, PlayerStatsAttachment st, SkillDef def, int y, int index,
                           int mouseX, int mouseY) {
        boolean learned = st != null && st.skills().level(def.id()) > 0;
        boolean hovered = !learned && mouseX >= listLeft() - 2 && mouseX <= listRight()
                && mouseY >= y && mouseY < y + ROW_H - 1;

        if (hovered) {
            g.fill(listLeft() - 2, y, listRight(), y + ROW_H - 1, ZenkaiPalette.HOVER_VEIL);
        }

        Component name = Component.translatable(def.nameKey());
        // Aprendida = apagada; disponible = verde de "algo tuyo", el MISMO que usa la pestaña
        // de habilidades para lo mismo.
        int nameY = rowTextY(y);
        PanelText.onDark(g, this.font, name, listLeft(), nameY,
                learned ? ZenkaiPalette.TEXT_OFF : ZenkaiPalette.OK);

        Component right;
        int color;
        if (learned) {
            right = Component.translatable("screen.zenkai.master.learned");
            color = ZenkaiPalette.TEXT_OFF;
        } else {
            right = Component.translatable("screen.zenkai.master.cost",
                    def.tpCost(), def.mindReqFor(1));
            color = canAfford(st, def) ? ZenkaiPalette.TP_ON_DARK : ZenkaiPalette.DENIED;
        }
        PanelText.rightOnDark(g, this.font, right, listRight(), nameY, color);

        // Separador tenue bajo cada fila salvo la última visible: sin él, con una sola línea
        // por skill, la lista se lee como un bloque continuo en vez de habilidades separadas.
        boolean lastVisible = index == rows.size() - 1 || !onScreen(index + 1);
        if (!lastVisible) {
            g.fill(listLeft() - 2, y + ROW_H - 1, listRight(), y + ROW_H, ZenkaiPalette.SEPARATOR_DARK);
        }
    }

    /** Barra de scroll a la derecha de la lista; oculta si cabe sin desplazar. Mismo
     *  patrón que SkillsScreen.drawScrollbar, con colores de fondo oscuro (BAR_BG_DARK/
     *  TP_ON_DARK) en vez de los de panel beige que usa aquella pantalla. */
    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;

        int x = scrollbarX();
        int trackTop = listTop(), trackH = visibleRows() * ROW_H;
        g.fill(x, trackTop, x + SCROLLBAR_W, trackTop + trackH, ZenkaiPalette.BAR_BG_DARK);

        int thumbH = Math.max(12, trackH * visibleRows() / rows.size());
        int thumbY = trackTop + (trackH - thumbH) * scroll / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.TP_ON_DARK);
    }

    /**
     * El maestro real, rotando hacia el ratón. Si la entidad ya no está cargada (te alejaste,
     * el chunk se descargó) el panel se queda vacío en vez de petar: la pantalla sigue siendo
     * usable y el servidor rechazará la compra por distancia en cualquier caso.
     */
    private void renderMaster(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.level == null) return;
        Entity e = minecraft.level.getEntity(entityId);
        if (!(e instanceof LivingEntity le)) return;

        int cx = left + PORTRAIT_W / 2;
        int cyTop = top + PADDING;
        int cyBot = top + BG_H - PADDING;

        InventoryScreen.renderEntityInInventoryFollowsMouse(          // ⚠ firma
                g,
                left + PADDING / 2, cyTop, left + PORTRAIT_W, cyBot,
                50,                    // escala; ajústala al alto real del geo
                0.0625F,
                mouseX, mouseY,
                le);

        PanelText.centeredOnDark(g, this.font, le.getDisplayName(), cx, cyBot - 10,
                ZenkaiPalette.TEXT);
    }

    // ── Entrada ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            PlayerStatsAttachment st = stats();
            for (int i = 0; i < rows.size(); i++) {
                if (!onScreen(i)) continue;
                int y = rowTop(i);
                if (mouseX < listLeft() - 2 || mouseX > listRight()) continue;
                if (mouseY < y || mouseY >= y + ROW_H - 1) continue;

                SkillDef def = rows.get(i);
                if (st != null && st.skills().level(def.id()) > 0) return true;  // ya aprendida
                if (!canAfford(st, def)) return true;                            // sin fondos

                PacketDistributor.sendToServer(new SkillBuyPacket(def.id(), masterId));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (maxScroll() > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}