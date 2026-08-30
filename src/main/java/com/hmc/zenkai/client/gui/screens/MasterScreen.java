package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.PhysicalIcons;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.feature.player.MindBudget;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.feature.technique.PhysicalTechniquePacket;
import com.hmc.zenkai.feature.technique.TechniquePacket;
import com.hmc.zenkai.network.MasterServicePacket;
import com.hmc.zenkai.network.OpenMasterPayload;
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
import java.util.Locale;

/**
 * Tienda de un maestro. Pantalla PROPIA, no una pestaña de ZenkaiMenuScreen: es apaisada
 * (el maestro ocupa el tercio izquierdo) y no comparte navegación con el menú del jugador.
 *
 * HUB de tres botones grandes ("Skills"/"Técnicas"/"Servicios") que llevan a la lista
 * correspondiente:
 *   - Skills: habilidades cuyo campo "master" es este maestro (sin cambios respecto a antes).
 *   - Técnicas: KiTechniqueType/PhysicalTechnique cuyo master() es este maestro ("técnica
 *     firma", ver TechniqueDef).
 *   - Servicios: lo que ZenkaiMasterEntity.services() devuelva para este maestro — favores
 *     puntuales sin nivel ni coste en TP (Kami: cola; Korin: semilla diaria; Kaiosama: pesas
 *     de entrenamiento — ver feature/master/MasterService). Llega ya resuelto en
 *     OpenMasterPayload (el constructor recibe la lista) y se refresca tras un claim con
 *     MasterServicesUpdatePayload/updateServices(), sin reabrir la pantalla.
 * Un botón cuyo maestro no ofrece nada de esa clase se ve oscurecido con tooltip en vez de
 * desaparecer — mismo patrón que SkillsScreen usa para "Forget" bajo el suelo permanente: el
 * layout no salta de tamaño según el maestro.
 * Estado por fila (ambas listas comparten el mismo lenguaje):
 *   - no aprendida y puedes pagar  -> coste en blanco, clic compra
 *   - no aprendida y no puedes     -> coste en ROJO, clic no hace nada
 *   - aprendida                    -> gris, "Aprendida" (el maestro solo da el nivel 1/la
 *                                     técnica; el resto se gestiona desde las pantallas del
 *                                     jugador — SkillsScreen, TechniqueEditScreen, PhysicalScreen)
 * Fondo: master_screen.png (ver tools/gen_master_screen.py), compartido por el conjunto de
 * maestros — lo que distingue a cada uno es su retrato 3D, no el fondo.
 */
public class MasterScreen extends Screen {

    private enum Mode { HUB, SKILLS, TECHNIQUES, SERVICES }

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
    /** Icono de fila de la lista de técnicas — las de Skills no llevan icono, propio de cada
     *  tipo de fila. */
    private static final int ROW_ICON     = 16;
    private static final int ROW_ICON_GAP = 4;
    /** Alto de la fila "‹ Back" que aparece sobre la lista en modo SKILLS/TECHNIQUES. */
    private static final int BACK_ROW_H = 12;
    /** Separación entre los dos botones grandes del hub. */
    private static final int HUB_GAP = 8;

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
    // tres anillos IN/MID/OUT + brillo de esquina y el panel del retrato ya horneados dentro,
    // esta vez con la paleta fría/azulada de ZenkaiPalette.MASTER_* (ver su comentario): un
    // diálogo con un NPC se lee distinto del naranja/dorado de los paneles del jugador.
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

    // Icono del hub: "Skills" reutiliza el ya existente de ZenkaiTab.SKILLS (mismo concepto
    // visual que la pestaña del menú del jugador); "Técnicas" y "Servicios" son nuevos (ver
    // tools/gen_master_icons.py), fila v=80, primera libre del atlas — (0,80) y (20,80).
    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ICONS_ATLAS = 256;
    private static final int ICON_CELL = 20;
    private static final int ICON_SKILLS_U = 160, ICON_SKILLS_V = 0;
    private static final int ICON_TECHNIQUES_U = 0, ICON_TECHNIQUES_V = 80;
    private static final int ICON_SERVICES_U = 20, ICON_SERVICES_V = 80;

    private final String masterId;
    private final int entityId;

    private Mode mode = Mode.HUB;
    private final List<SkillDef> skillRows = new ArrayList<>();
    private final List<TechEntry> techRows = new ArrayList<>();
    private final List<OpenMasterPayload.ServiceEntry> serviceRows = new ArrayList<>();
    private int scroll = 0;

    private int left, top;

    public MasterScreen(String masterId, int entityId, List<OpenMasterPayload.ServiceEntry> services) {
        super(Component.translatable("master.zenkai." + masterId));
        this.masterId = masterId;
        this.entityId = entityId;
        this.serviceRows.addAll(services);
    }

    /** Ver ClientPayloadHandlers.updateMasterServices: refresca solo las etiquetas (contador
     *  de Korin, texto de Kami/Kaio) sin tocar mode/scroll — reabrir la pantalla entera
     *  volvería siempre al hub, perdiendo dónde estaba mirando el jugador. */
    public void updateServices(List<OpenMasterPayload.ServiceEntry> services) {
        serviceRows.clear();
        serviceRows.addAll(services);
    }

    /**
     * Una fila de la lista TECHNIQUES puede ser ki o física: KiTechniqueType/PhysicalTechnique
     * no comparten ninguna interfaz común (son enums de identidad independientes, ver sus
     * javadocs), así que este record envuelve el que corresponda y despacha por {@code ki} en
     * vez de duplicar el render/hit-test de la fila para cada uno.
     */
    private record TechEntry(boolean ki, KiTechniqueType kiType, PhysicalTechnique physType) {
        String nameKey() { return ki ? kiType.nameKey() : physType.nameKey(); }
        int tpCost()     { return ki ? kiType.tpCost() : physType.tpCost(); }
        int mindReq()    { return ki ? kiType.mindReq() : physType.mindReq(); }

        boolean unlocked(PlayerStatsAttachment st) {
            return ki ? st.techniques().isUnlocked(kiType) : st.techniques().isUnlocked(physType);
        }

        /** Mismo criterio que valida el servidor (TP + MindBudget), igual que
         *  TechniqueEditScreen/PhysicalScreen ya usan para sus propios botones de desbloqueo. */
        boolean canAfford(PlayerStatsAttachment st) {
            if (st == null || st.getTP() < tpCost()) return false;
            return ki ? MindBudget.canUnlock(st, kiType) : MindBudget.canUnlock(st, physType);
        }

        void sendUnlock(String masterId) {
            if (ki) PacketDistributor.sendToServer(TechniquePacket.unlock(kiType, masterId));
            else    PacketDistributor.sendToServer(PhysicalTechniquePacket.unlock(physType, masterId));
        }

        void drawIcon(GuiGraphics g, int x, int y, int size) {
            if (ki) TechniqueIcons.draw(g, x, y, size, kiType, kiType.defaultRgb());
            else    PhysicalIcons.draw(g, x, y, size, physType);
        }

        /** Descripción del tooltip: las ki tienen su propia clave .desc; las físicas no (ver
         *  PhysicalTechnique), así que se muestra su ficha técnica en su lugar — mismo formato
         *  que ya usa la fila de PhysicalScreen. */
        Component tooltip() {
            if (ki) return Component.translatable(kiType.descKey());
            return Component.translatable("screen.zenkai.physical.stats",
                    fmt(physType.dmgMult()), fmt(Math.round(physType.staminaPct() * 1000.0) / 10.0),
                    fmt(physType.cooldownTicks() / 20.0), fmt(physType.range()));
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, v == Math.floor(v) ? "%.0f" : "%.1f", v);
    }

    @Override
    protected void init() {
        left = (this.width - BG_W) / 2;
        top  = (this.height - BG_H) / 2;

        skillRows.clear();
        skillRows.addAll(SkillDef.taughtBy(masterId));

        techRows.clear();
        for (KiTechniqueType t : KiTechniqueType.values()) {
            if (masterId.equals(t.master())) techRows.add(new TechEntry(true, t, null));
        }
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            if (masterId.equals(t.master())) techRows.add(new TechEntry(false, null, t));
        }

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
    /** Techo del área de contenido (bajo el título/TP/MND). En el hub los dos botones grandes
     *  arrancan aquí; en las listas es donde va la fila "‹ Back". */
    private int listTop()    { return top + CONTENT_TOP; }
    /** Techo de las FILAS propiamente dichas: bajo la fila "Back" en SKILLS/TECHNIQUES, sin
     *  desplazamiento en el hub (que no tiene filas). */
    private int rowsTop()    { return listTop() + (mode == Mode.HUB ? 0 : BACK_ROW_H); }
    private int listHeight() { return BG_H - CONTENT_TOP - PADDING - (mode == Mode.HUB ? 0 : BACK_ROW_H); }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }

    private int rowCount() {
        return switch (mode) {
            case SKILLS -> skillRows.size();
            case TECHNIQUES -> techRows.size();
            case SERVICES -> serviceRows.size();
            case HUB -> 0;
        };
    }

    private int maxScroll()  { return Math.max(0, rowCount() - visibleRows()); }
    private int rowTop(int i){ return rowsTop() + (i - scroll) * ROW_H; }
    /** Y del texto de una fila, centrado verticalmente en ROW_H (fuente ≈ 9px de alto). */
    private int rowTextY(int y) { return y + (ROW_H - 9) / 2; }

    private boolean onScreen(int i) {
        int rel = i - scroll;
        return rel >= 0 && rel < visibleRows();
    }

    private boolean backHovered(int mouseX, int mouseY) {
        return mode != Mode.HUB && mouseX >= listLeft() - 2 && mouseX <= listRight()
                && mouseY >= listTop() && mouseY < listTop() + BACK_ROW_H;
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

        switch (mode) {
            case HUB -> renderHub(g, mouseX, mouseY);
            case SKILLS -> renderSkillsList(g, mouseX, mouseY);
            case TECHNIQUES -> renderTechniquesList(g, mouseX, mouseY);
            case SERVICES -> renderServicesList(g, mouseX, mouseY);
        }
    }

    // ── Hub ──────────────────────────────────────────────────────────────────

    /** Ancho de CADA UNO de los 3 botones del hub, repartiendo el mismo espacio total entre
     *  3 en vez de 2 (dos huecos HUB_GAP, no uno) — el diálogo no crece, solo se reparte
     *  distinto. Compartido por renderHub/clickHub para que nunca se desincronicen. */
    private int hubButtonWidth() {
        return (trackRight() - listLeft() - 2 * HUB_GAP) / 3;
    }

    private void renderHub(GuiGraphics g, int mouseX, int mouseY) {
        int cLeft = listLeft(), cTop = listTop(), cBottom = top + BG_H - PADDING;
        int btnW = hubButtonWidth();
        int btnH = cBottom - cTop;

        renderHubOption(g, cLeft, cTop, btnW, btnH, ICON_SKILLS_U, ICON_SKILLS_V,
                Component.translatable("screen.zenkai.tab.skills"), !skillRows.isEmpty(),
                mouseX, mouseY);
        renderHubOption(g, cLeft + btnW + HUB_GAP, cTop, btnW, btnH, ICON_TECHNIQUES_U, ICON_TECHNIQUES_V,
                Component.translatable("screen.zenkai.master.hub.techniques"), !techRows.isEmpty(),
                mouseX, mouseY);
        renderHubOption(g, cLeft + 2 * (btnW + HUB_GAP), cTop, btnW, btnH, ICON_SERVICES_U, ICON_SERVICES_V,
                Component.translatable("screen.zenkai.master.hub.services"), !serviceRows.isEmpty(),
                mouseX, mouseY);
    }

    private void renderHubOption(GuiGraphics g, int x, int y, int w, int h, int iconU, int iconV,
                                  Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hoveredRect = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        boolean hovered = enabled && hoveredRect;

        g.fill(x, y, x + w, y + h, ZenkaiPalette.MASTER_DIALOG_PANEL);
        if (hovered) g.fill(x, y, x + w, y + h, ZenkaiPalette.HOVER_VEIL);
        // Marco de 1px, mismo tono frío que el resto del diálogo (ZenkaiPalette.MASTER_BORDER_IN).
        g.fill(x, y, x + w, y + 1, ZenkaiPalette.MASTER_BORDER_IN);
        g.fill(x, y + h - 1, x + w, y + h, ZenkaiPalette.MASTER_BORDER_IN);
        g.fill(x, y, x + 1, y + h, ZenkaiPalette.MASTER_BORDER_IN);
        g.fill(x + w - 1, y, x + w, y + h, ZenkaiPalette.MASTER_BORDER_IN);

        int iconX = x + (w - ICON_CELL) / 2;
        int iconY = y + h / 2 - ICON_CELL - 4;
        if (!enabled) g.setColor(0.5F, 0.5F, 0.5F, 1.0F);
        g.blit(ICONS_TEX, iconX, iconY, iconU, iconV, ICON_CELL, ICON_CELL, ICONS_ATLAS, ICONS_ATLAS);
        if (!enabled) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        PanelText.centeredOnDark(g, this.font, label, x + w / 2, iconY + ICON_CELL + 6,
                enabled ? ZenkaiPalette.TEXT : ZenkaiPalette.TEXT_OFF);

        if (!enabled && hoveredRect) {
            Component tip = Component.translatable("screen.zenkai.master.hub.locked");
            g.renderTooltip(this.font, this.font.split(tip, TOOLTIP_W), mouseX, mouseY);
        }
    }

    // ── Lista de habilidades ────────────────────────────────────────────────

    private void renderSkillsList(GuiGraphics g, int mouseX, int mouseY) {
        renderBackRow(g, mouseX, mouseY);

        if (skillRows.isEmpty()) {
            PanelText.onDark(g, this.font, Component.translatable("screen.zenkai.master.nothing"),
                    listLeft(), rowsTop(), ZenkaiPalette.TEXT_OFF);
            return;
        }

        PlayerStatsAttachment st = stats();

        // Recorte a la ventana visible: con scrollbar de verdad hace falta, o una fila a medio
        // salir se pintaría encima del marco superior/inferior del diálogo.
        g.enableScissor(left, rowsTop(), left + BG_W, rowsTop() + visibleRows() * ROW_H);
        SkillDef hoveredDef = null;
        for (int i = 0; i < skillRows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            renderSkillRow(g, st, skillRows.get(i), y, i, mouseX, mouseY);
            if (mouseX >= listLeft() - 2 && mouseX <= listRight()
                    && mouseY >= y && mouseY < y + ROW_H - 1) {
                hoveredDef = skillRows.get(i);
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

    private void renderSkillRow(GuiGraphics g, PlayerStatsAttachment st, SkillDef def, int y, int index,
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
        boolean lastVisible = index == skillRows.size() - 1 || !onScreen(index + 1);
        if (!lastVisible) {
            g.fill(listLeft() - 2, y + ROW_H - 1, listRight(), y + ROW_H, ZenkaiPalette.SEPARATOR_DARK);
        }
    }

    // ── Lista de técnicas ───────────────────────────────────────────────────

    private void renderTechniquesList(GuiGraphics g, int mouseX, int mouseY) {
        renderBackRow(g, mouseX, mouseY);

        if (techRows.isEmpty()) {
            PanelText.onDark(g, this.font, Component.translatable("screen.zenkai.master.nothing"),
                    listLeft(), rowsTop(), ZenkaiPalette.TEXT_OFF);
            return;
        }

        PlayerStatsAttachment st = stats();

        g.enableScissor(left, rowsTop(), left + BG_W, rowsTop() + visibleRows() * ROW_H);
        TechEntry hovered = null;
        for (int i = 0; i < techRows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            renderTechRow(g, st, techRows.get(i), y, i, mouseX, mouseY);
            if (mouseX >= listLeft() - 2 && mouseX <= listRight()
                    && mouseY >= y && mouseY < y + ROW_H - 1) {
                hovered = techRows.get(i);
            }
        }
        g.disableScissor();
        drawScrollbar(g);

        if (hovered != null) {
            g.renderTooltip(this.font, this.font.split(hovered.tooltip(), TOOLTIP_W), mouseX, mouseY);
        }
    }

    private void renderTechRow(GuiGraphics g, PlayerStatsAttachment st, TechEntry entry, int y, int index,
                                int mouseX, int mouseY) {
        boolean learned = st != null && entry.unlocked(st);
        boolean hovered = !learned && mouseX >= listLeft() - 2 && mouseX <= listRight()
                && mouseY >= y && mouseY < y + ROW_H - 1;

        if (hovered) {
            g.fill(listLeft() - 2, y, listRight(), y + ROW_H - 1, ZenkaiPalette.HOVER_VEIL);
        }

        int iconY = y + (ROW_H - ROW_ICON) / 2;
        entry.drawIcon(g, listLeft(), iconY, ROW_ICON);

        int textX = listLeft() + ROW_ICON + ROW_ICON_GAP;
        Component name = Component.translatable(entry.nameKey());
        int nameY = rowTextY(y);
        PanelText.onDark(g, this.font, name, textX, nameY,
                learned ? ZenkaiPalette.TEXT_OFF : ZenkaiPalette.OK);

        Component right;
        int color;
        if (learned) {
            right = Component.translatable("screen.zenkai.master.learned");
            color = ZenkaiPalette.TEXT_OFF;
        } else {
            right = Component.translatable("screen.zenkai.master.cost", entry.tpCost(), entry.mindReq());
            color = entry.canAfford(st) ? ZenkaiPalette.TP_ON_DARK : ZenkaiPalette.DENIED;
        }
        PanelText.rightOnDark(g, this.font, right, listRight(), nameY, color);

        boolean lastVisible = index == techRows.size() - 1 || !onScreen(index + 1);
        if (!lastVisible) {
            g.fill(listLeft() - 2, y + ROW_H - 1, listRight(), y + ROW_H, ZenkaiPalette.SEPARATOR_DARK);
        }
    }

    // ── Lista de servicios ───────────────────────────────────────────────────
    //
    // Sin barra de nivel/coste como Skills/Técnicas a propósito: un servicio no se compra ni
    // se sube de nivel, es una acción puntual — la fila entera es el label YA resuelto
    // server-side (ver MasterService.label / OpenMasterPayload.ServiceEntry), no un nombre +
    // un coste calculados aquí.

    private void renderServicesList(GuiGraphics g, int mouseX, int mouseY) {
        renderBackRow(g, mouseX, mouseY);

        if (serviceRows.isEmpty()) {
            PanelText.onDark(g, this.font, Component.translatable("screen.zenkai.master.nothing"),
                    listLeft(), rowsTop(), ZenkaiPalette.TEXT_OFF);
            return;
        }

        g.enableScissor(left, rowsTop(), left + BG_W, rowsTop() + visibleRows() * ROW_H);
        for (int i = 0; i < serviceRows.size(); i++) {
            if (!onScreen(i)) continue;
            renderServiceRow(g, serviceRows.get(i), rowTop(i), i, mouseX, mouseY);
        }
        g.disableScissor();
        drawScrollbar(g);
    }

    private void renderServiceRow(GuiGraphics g, OpenMasterPayload.ServiceEntry entry, int y, int index,
                                  int mouseX, int mouseY) {
        boolean hovered = mouseX >= listLeft() - 2 && mouseX <= listRight()
                && mouseY >= y && mouseY < y + ROW_H - 1;

        if (hovered) {
            g.fill(listLeft() - 2, y, listRight(), y + ROW_H - 1, ZenkaiPalette.HOVER_VEIL);
        }

        PanelText.onDark(g, this.font, Component.literal(entry.label()), listLeft(), rowTextY(y),
                hovered ? ZenkaiPalette.TEXT_HOVER : ZenkaiPalette.OK);

        boolean lastVisible = index == serviceRows.size() - 1 || !onScreen(index + 1);
        if (!lastVisible) {
            g.fill(listLeft() - 2, y + ROW_H - 1, listRight(), y + ROW_H, ZenkaiPalette.SEPARATOR_DARK);
        }
    }

    // ── "‹ Back" (SKILLS/TECHNIQUES -> HUB) ─────────────────────────────────

    private void renderBackRow(GuiGraphics g, int mouseX, int mouseY) {
        boolean hovered = backHovered(mouseX, mouseY);
        Component text = Component.literal("‹ ").append(Component.translatable("screen.zenkai.back"));
        PanelText.onDark(g, this.font, text, listLeft(), listTop() + 1,
                hovered ? ZenkaiPalette.TEXT_HOVER : ZenkaiPalette.TEXT_OFF);
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

    /** Barra de scroll a la derecha de la lista; oculta si cabe sin desplazar. Mismo
     *  patrón que SkillsScreen.drawScrollbar, con colores de fondo oscuro (BAR_BG_DARK/
     *  TP_ON_DARK) en vez de los de panel beige que usa aquella pantalla. */
    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;

        int x = scrollbarX();
        int trackTop = rowsTop(), trackH = visibleRows() * ROW_H;
        g.fill(x, trackTop, x + SCROLLBAR_W, trackTop + trackH, ZenkaiPalette.BAR_BG_DARK);

        int count = rowCount();
        int thumbH = Math.max(12, trackH * visibleRows() / count);
        int thumbY = trackTop + (trackH - thumbH) * scroll / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.TP_ON_DARK);
    }

    // ── Entrada ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mode != Mode.HUB && backHovered((int) mouseX, (int) mouseY)) {
                mode = Mode.HUB;
                return true;
            }
            boolean handled = switch (mode) {
                case HUB -> clickHub(mouseX, mouseY);
                case SKILLS -> clickSkills(mouseX, mouseY);
                case TECHNIQUES -> clickTechniques(mouseX, mouseY);
                case SERVICES -> clickServices(mouseX, mouseY);
            };
            if (handled) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickHub(double mouseX, double mouseY) {
        int cLeft = listLeft(), cTop = listTop(), cBottom = top + BG_H - PADDING;
        int btnW = hubButtonWidth();
        if (mouseY < cTop || mouseY >= cBottom) return false;

        if (mouseX >= cLeft && mouseX < cLeft + btnW) {
            if (!skillRows.isEmpty()) { mode = Mode.SKILLS; scroll = 0; }
            return true;
        }
        int techX = cLeft + btnW + HUB_GAP;
        if (mouseX >= techX && mouseX < techX + btnW) {
            if (!techRows.isEmpty()) { mode = Mode.TECHNIQUES; scroll = 0; }
            return true;
        }
        int servicesX = cLeft + 2 * (btnW + HUB_GAP);
        if (mouseX >= servicesX && mouseX < servicesX + btnW) {
            if (!serviceRows.isEmpty()) { mode = Mode.SERVICES; scroll = 0; }
            return true;
        }
        return false;
    }

    private boolean clickSkills(double mouseX, double mouseY) {
        PlayerStatsAttachment st = stats();
        for (int i = 0; i < skillRows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            if (mouseX < listLeft() - 2 || mouseX > listRight()) continue;
            if (mouseY < y || mouseY >= y + ROW_H - 1) continue;

            SkillDef def = skillRows.get(i);
            if (st != null && st.skills().level(def.id()) > 0) return true;  // ya aprendida
            if (!canAfford(st, def)) return true;                            // sin fondos

            PacketDistributor.sendToServer(new SkillBuyPacket(def.id(), masterId));
            return true;
        }
        return false;
    }

    private boolean clickTechniques(double mouseX, double mouseY) {
        PlayerStatsAttachment st = stats();
        for (int i = 0; i < techRows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            if (mouseX < listLeft() - 2 || mouseX > listRight()) continue;
            if (mouseY < y || mouseY >= y + ROW_H - 1) continue;

            TechEntry entry = techRows.get(i);
            if (st != null && entry.unlocked(st)) return true;   // ya aprendida
            if (!entry.canAfford(st)) return true;                // sin fondos

            entry.sendUnlock(masterId);
            return true;
        }
        return false;
    }

    private boolean clickServices(double mouseX, double mouseY) {
        for (int i = 0; i < serviceRows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            if (mouseX < listLeft() - 2 || mouseX > listRight()) continue;
            if (mouseY < y || mouseY >= y + ROW_H - 1) continue;

            OpenMasterPayload.ServiceEntry entry = serviceRows.get(i);
            PacketDistributor.sendToServer(new MasterServicePacket(masterId, entityId, entry.id()));
            return true;
        }
        return false;
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
