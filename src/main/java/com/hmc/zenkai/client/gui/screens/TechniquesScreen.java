package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.PhysicalIcons;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.ConfirmIconButton;
import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.buttons.VArrowButton;
import com.hmc.zenkai.client.gui.widgets.BindBar;
import com.hmc.zenkai.client.gui.widgets.SlotCell;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.MindBudget;
import com.hmc.zenkai.feature.player.PlayerTechniques;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import com.hmc.zenkai.feature.technique.KiTechnique;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.PhysicalCombatServer;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.feature.technique.PhysicalTechniquePacket;
import com.hmc.zenkai.feature.technique.TechniqueEffect;
import com.hmc.zenkai.feature.technique.TechniquePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Pestaña TÉCNICAS: una sola pantalla para las tres familias (ki, físicas y de maestro).
 *
 * POR QUÉ UNA SOLA. Antes eran cuatro clases — un hub de tres filas más una pantalla por
 * familia — y el jugador tenía que ir y venir entre ellas para montar un loadout que en
 * realidad es UNO: las nueve posiciones de PlayerTechniques.bindings son compartidas, una
 * técnica física y una de ki compiten por la misma tecla. Peor aún, cada pantalla resolvía la
 * misma acción de forma distinta (la ✕ desasignaba en físicas y BORRABA en ki; desasignar una
 * de ki solo se podía haciendo clic en su casilla), así que el mismo icono significaba dos
 * cosas según en qué pestaña estuvieras. Aquí la barra de posiciones y el vocabulario de fila
 * son los mismos siempre y lo único que cambia es QUÉ LISTA se muestra, mediante el selector
 * de categoría (flechas + icono) centrado bajo la barra.
 *
 * VOCABULARIO DE FILA, idéntico en las tres categorías:
 *   [ Assign ]  arriba a la derecha — arma la técnica; luego clic en una casilla o tecla 1-9.
 *   lápiz / aspa / papelera, abajo a la derecha — editar, DESASIGNAR (nunca borra), y
 *   borrar u olvidar. Las flechas verticales, a la izquierda, solo en ki: mueven la técnica
 *   dentro de la lista.
 * El aspa solo aparece si la técnica está asignada, y la papelera pide confirmación
 * (ConfirmIconButton).
 *
 * ORDEN DE LA LISTA vs ORDEN DE LAS CASILLAS: son dos cosas distintas y las dos son
 * ajustables. Las flechas verticales mueven la técnica dentro del INVENTARIO (y arrastran su
 * asignación con ella, ver PlayerTechniques.swapSlots); arrastrar una casilla sobre otra en la
 * barra reordena las CASILLAS (BindBar). Solo las de ki tienen flechas: las físicas y las de
 * maestro salen del enum/datapack, no de una lista que el jugador posea.
 *
 * La categoría es STATIC a propósito: ZenkaiMenuScreen.createScreen() construye una Screen
 * NUEVA cada vez que se entra a la pestaña (mismo caso que StatsScreen.showStats), así que un
 * campo de instancia devolvería siempre a Ki al volver de otra pestaña o del editor.
 */
public class TechniquesScreen extends ZenkaiMenuScreen {

    // ── Categorías ───────────────────────────────────────────────────────────

    /** Las tres familias. Los (u,v) son las mismas celdas de icons.png que pintaba el hub. */
    private enum Category {
        KI(40, 20, "screen.zenkai.techniques.category.ki"),
        PHYSICAL(120, 20, "screen.zenkai.techniques.category.physical"),
        MASTER(0, 80, "screen.zenkai.techniques.category.master");

        final int u, v;
        final String labelKey;

        Category(int u, int v, String labelKey) { this.u = u; this.v = v; this.labelKey = labelKey; }
    }

    /** Una fila de la lista. MasterKi lleva el índice de su instancia, o -1 si no existe
     *  (borrada con la papelera): esa es la única rama que ofrece recrearla. */
    private sealed interface Row {
        record Ki(int index, KiTechnique tech) implements Row {}
        record Phys(PhysicalTechnique tech, boolean fromMaster) implements Row {}
        record MasterKi(KiTechniqueType type, int index) implements Row {}
    }

    /** Widget de una fila con su desplazamiento vertical dentro de ella y si debe poder
     *  pulsarse cuando la fila se ve — layoutRows lo necesita para no reactivar por error una
     *  flecha que estaba apagada por ser la primera o la última fila. */
    private record RowWidget(AbstractWidget widget, int dy, boolean enabled) {}

    /** Tooltip que hay que pintar A MANO porque su widget puede estar inactivo, y un widget
     *  inactivo no enseña su Tooltip — justo el caso en el que hace falta leerlo. El
     *  Supplier se evalúa cada frame: el texto depende del TP/MND del momento. */
    private record HoverTip(AbstractWidget widget, Supplier<List<Component>> lines) {}

    // ── Geometría ────────────────────────────────────────────────────────────

    private static final int ROW_H = 30;
    private static final int BAR_Y_OFF = CONTENT_TOP + 18;
    private static final int CAT_Y_OFF = BAR_Y_OFF + SlotCell.SIZE + 4;
    private static final int CAT_H = 20;
    private static final int LIST_Y_OFF = CAT_Y_OFF + CAT_H + 6;
    /**
     * Banda de pie, IGUAL en las tres categorías. Antes ki reservaba 34 px para un
     * PanelButton "New technique" a lo ancho y las otras dos solo 16, así que ki enseñaba una
     * fila MENOS que sus hermanas sin ninguna razón de contenido. Con el botón convertido en
     * un "+" de 12 px la banda basta para las tres y las tres caben a 5 filas.
     * En esa banda conviven el aviso de asignación (centrado) y el "+" (a la derecha): no
     * chocan porque el aviso solo aparece con algo armado y el "+" solo en la categoría ki.
     */
    private static final int LIST_BOTTOM = 22;
    private static final int SCROLLBAR_W = 4;

    private static final int ICON = 18;
    private static final int ARROW = 12;
    private static final int ICON_BTN = 12;
    private static final int TRASH = 16;
    /** Lado del botón "+" de nueva técnica (PlusIconButton es 12x12). */
    private static final int PLUS = 12;
    /** Celda de icons.png (256x256, rejilla de 20) para el icono de la categoria. */
    private static final int CAT_ICON = 20;

    /**
     * Reparto de una fila (alto ROW_H). Las bandas verticales son SEMIABIERTAS y NUNCA se
     * solapan: AbstractWidget acepta el clic con {@code mouseY >= getY() && mouseY < getY()+h},
     * así que dos widgets que compartan columna y un solo píxel de altura se roban el clic
     * entre ellos según cuál se registró antes — un fallo que no da error, solo un botón que
     * "a veces no responde".
     *   y+2  ..y+16   botón de acción (h 14) y flecha de subir
     *   y+16 ..y+28   lápiz, aspa y flecha de bajar (h 12)
     *   y+7  ..y+23   papelera (h 16), en su PROPIA columna a la derecha del resto
     * La papelera está centrada en la fila entera en vez de en un renglón porque no cabe en
     * ninguno de los dos: con 16 px se salía por abajo sobre la línea separadora. Al darle
     * columna propia (el botón de acción termina a su izquierda, ver xActionRight) el solape
     * horizontal con ese botón desaparece y su altura deja de importar.
     */
    private static final int DY_ACTION = 2;
    private static final int DY_ICONS = 16;
    private static final int DY_TRASH = (ROW_H - TRASH) / 2;
    private static final int ACTION_H = 14;
    /** Aire real entre botones. La versión anterior los pegaba (los corchetes de [ Assign ] y
     *  [ Edit ] se tocaban) y metía el aspa encima del borde del panel. */
    private static final int BTN_GAP = 6;
    private static final int BTN_PAD = 6;
    /** Mitad FIJA del selector de categoría: si dependiera del ancho de la etiqueta, las
     *  flechas saltarían de sitio al cambiar de categoría (mismo criterio que
     *  StyleSelectionScreen.KI_ROW_HALF). */
    private static final int CAT_HALF = 66;

    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final ResourceLocation TEX_X =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x.png");
    private static final ResourceLocation TEX_X_HL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x_highlight.png");
    private static final ResourceLocation TEX_PENCIL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_pencil.png");
    private static final ResourceLocation TEX_PENCIL_HL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_pencil_highlight.png");
    private static final ResourceLocation TEX_TRASH =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_trash.png");
    private static final ResourceLocation TEX_TRASH_HL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_trash_highlight.png");

    // ── Estado ───────────────────────────────────────────────────────────────

    private static Category category = Category.KI;

    /** Índice de la técnica ki armada para asignar; -1 = ninguna. */
    private int armedKi = -1;
    /** Técnica física armada para asignar, o null. Nunca hay las dos a la vez. */
    private PhysicalTechnique armedPhys = null;

    private int scrollRow = 0;
    /** Huella del estado con el que se construyeron los widgets: si cambia, hay que
     *  reconstruir (llegó un sync con más o menos técnicas, o cambió el TP y con él qué se
     *  puede pagar). */
    private long builtSignature = Long.MIN_VALUE;

    private BindBar bindBar;
    private final List<Row> rows = new ArrayList<>();
    private final List<List<RowWidget>> rowWidgets = new ArrayList<>();
    private final List<HoverTip> hoverTips = new ArrayList<>();

    public TechniquesScreen() {
        super(Component.translatable(ZenkaiTab.TECHNIQUES.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.TECHNIQUES; }

    // ── Geometría derivada ───────────────────────────────────────────────────

    /**
     * Borde derecho del CONTENIDO. Deja libre el canalón de la barra de scroll, que se reserva
     * SIEMPRE aunque no haya scroll para que la fila no se encoja al pasar de 5 técnicas.
     * Muestreando los píxeles de common_screen.png, el beige del panel va de x=12 a x=243 y de
     * ahí en adelante empieza el marco naranja. La barra heredada de las pantallas viejas se
     * dibujaba en BG_W-10 = 246, o sea ENCIMA del marco — no se notaba porque ninguna de
     * aquellas listas llegaba a scrollear en la práctica, y aquí sí (12 técnicas de ki y 5
     * filas visibles). Con el contenido acabando en 238 la barra cabe entera sobre beige.
     */
    private int rightEdge()  { return panelLeft + BG_W - 18; }
    /** X de la barra de scroll: pegada al borde del contenido, sobre el beige. */
    private int scrollbarX() { return rightEdge() + 1; }
    private int listTop()    { return panelTop + LIST_Y_OFF; }
    private int listHeight() { return BG_H - LIST_Y_OFF - LIST_BOTTOM; }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }
    private int viewHeight() { return visibleRows() * ROW_H; }
    private int rowTop(int i){ return listTop() + (i - scrollRow) * ROW_H; }
    /** Y de la banda de pie (aviso de asignación y botón "+"). */
    private int footerY()    { return listTop() + viewHeight() + 3; }
    private int maxScroll()  { return Math.max(0, rows.size() - visibleRows()); }
    private boolean onScreen(int i) { int r = i - scrollRow; return r >= 0 && r < visibleRows(); }

    /**
     * Canalón de las flechas verticales. Solo ki lo usa, así que las demás categorías empiezan
     * más a la izquierda y su sub-línea gana esos píxeles.
     * El canalón arranca en panelLeft+15 y no en +6: el marco de common_screen.png ocupa los
     * primeros 14 px del panel, así que a +6 las flechas se dibujaban ENCIMA del borde naranja,
     * fuera de la zona de contenido. 14 es la misma sangría que ya usan la regla de la cabecera
     * y rightEdge() por el otro lado (256-242).
     */
    private static final int CONTENT_INSET = 15;
    private int gutter()  { return category == Category.KI ? ARROW + 3 : 0; }
    private int xArrows() { return panelLeft + CONTENT_INSET; }
    private int xIcon()   { return panelLeft + CONTENT_INSET + 1 + gutter(); }
    private int xText()   { return xIcon() + ICON + 4; }

    private int xTrash()    { return rightEdge() - TRASH; }
    private int xUnassign() { return xTrash() - BTN_GAP - ICON_BTN; }
    private int xEdit()     { return xUnassign() - BTN_GAP - ICON_BTN; }
    /**
     * Borde derecho del botón de acción: se detiene antes del cluster de iconos ENTERO, no
     * solo de la papelera.
     * Terminaba en xTrash()-BTN_GAP, o sea justo encima del lápiz y del aspa, y los tres
     * quedaban apilados en la misma columna de 30 px — se leía como un amasijo aunque las
     * cajas de clic no se tocaran. Retrasándolo hasta xEdit() los iconos se quedan con una
     * columna limpia y sin nada encima. El precio es ancho de NOMBRE, que se compensa con el
     * tooltip de la fila: ahí va el nombre completo sin recortar.
     */
    private int xActionRight() { return xEdit() - BTN_GAP; }

    // ── Construcción ─────────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        rows.clear();
        rowWidgets.clear();
        hoverTips.clear();
        if (att == null) return;

        buildRows();
        builtSignature = signature();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());

        bindBar = BindBar.create(
                panelLeft + (BG_W - BindBar.WIDTH) / 2, panelTop + BAR_Y_OFF,
                att,
                () -> armedKi >= 0 || armedPhys != null,
                this::onPositionClicked,
                this::addRenderableWidget);

        buildCategorySelector();

        for (int i = 0; i < rows.size(); i++) {
            List<RowWidget> bucket = new ArrayList<>();
            rowWidgets.add(bucket);
            buildRow(bucket, i, rows.get(i));
        }

        // "Nueva técnica" como icono y no como botón ancho: el botón de PanelButton lleva su
        // propio marco naranja, el MISMO anillo que ya enmarca el panel, y dentro del panel se
        // lee como un segundo contorno compitiendo con el primero (mismo motivo por el que
        // PartyScreen pasó sus acciones a iconos, ver CLAUDE.md). Además liberó los 12 px que
        // hacían que ki enseñara una fila menos que las otras categorías.
        if (category == Category.KI
                && att.techniques().slotCount() < ServerConfig.techniqueMaxSlots()) {
            PlusIconButton add = new PlusIconButton(rightEdge() - PLUS, footerY(),
                    () -> mc.setScreen(new TechniqueEditScreen(-1)));
            add.setTooltip(Tooltip.create(
                    Component.translatable("screen.zenkai.technique.create")));
            addRenderableWidget(add);
        }
    }

    private void buildRows() {
        PlayerTechniques tech = att.techniques();
        switch (category) {
            case KI -> {
                List<KiTechnique> slots = tech.slots();
                for (int i = 0; i < slots.size(); i++) rows.add(new Row.Ki(i, slots.get(i)));
            }
            case PHYSICAL -> {
                for (PhysicalTechnique t : PhysicalTechnique.values()) {
                    if (t.enabled()) rows.add(new Row.Phys(t, false));
                }
            }
            case MASTER -> {
                // Cualquier técnica firma, de cualquier maestro (ver TechniqueDef.master).
                // Hoy la única con 'master' puesto en el datapack es SPIRIT_BOMB
                // (zenkai_techniques/ki/spirit_bomb.json, "master": "kaio"), así que esta
                // categoría sale con una sola fila, no vacía.
                for (KiTechniqueType t : KiTechniqueType.values()) {
                    if (!t.master().isEmpty()) rows.add(new Row.MasterKi(t, instanceIndexOf(t)));
                }
                for (PhysicalTechnique t : PhysicalTechnique.values()) {
                    if (!t.master().isEmpty()) rows.add(new Row.Phys(t, true));
                }
            }
        }
    }

    /** Primera instancia creada de ese tipo, o -1. Una técnica firma se crea sola al
     *  aprenderla (TechniquePacket.handleUnlock), así que normalmente existe. */
    private int instanceIndexOf(KiTechniqueType type) {
        List<KiTechnique> slots = att.techniques().slots();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).type() == type) return i;
        }
        return -1;
    }

    private void buildCategorySelector() {
        int cx = panelLeft + BG_W / 2;
        int y = panelTop + CAT_Y_OFF;
        addRenderableWidget(new ArrowIconButton(cx - CAT_HALF - ARROW, y + (CAT_H - ARROW) / 2,
                ArrowIconButton.Dir.LEFT, () -> cycleCategory(-1)));
        // El centro también cicla al pulsarlo, igual que TechniqueEditScreen.cyclerRow: el
        // icono y la etiqueta se pintan en render() dentro de esta caja invisible.
        addRenderableWidget(new TextOnlyButton(cx - CAT_HALF + 2, y, CAT_HALF * 2 - 4, CAT_H,
                Component.empty(), () -> cycleCategory(1)));
        addRenderableWidget(new ArrowIconButton(cx + CAT_HALF, y + (CAT_H - ARROW) / 2,
                ArrowIconButton.Dir.RIGHT, () -> cycleCategory(1)));
    }

    private void cycleCategory(int dir) {
        Category[] all = Category.values();
        category = all[Math.floorMod(category.ordinal() + dir, all.length)];
        clearArmed();
        scrollRow = 0;
        rebuildWidgets();
    }

    private void clearArmed() {
        armedKi = -1;
        armedPhys = null;
    }

    // ── Filas ────────────────────────────────────────────────────────────────

    private void buildRow(List<RowWidget> bucket, int index, Row row) {
        PlayerTechniques tech = att.techniques();

        if (row instanceof Row.Ki(int slot, KiTechnique ignored)) {
            addRow(bucket, actionButton(assignLabel(armedKi == slot), () -> toggleKi(slot)),
                    DY_ACTION, true);

            VArrowButton up = new VArrowButton(xArrows(), 0, VArrowButton.Dir.UP,
                    () -> moveKi(slot, -1));
            up.setTooltip(Tooltip.create(
                    Component.translatable("screen.zenkai.technique.reorder_up")));
            VArrowButton down = new VArrowButton(xArrows(), 0, VArrowButton.Dir.DOWN,
                    () -> moveKi(slot, 1));
            down.setTooltip(Tooltip.create(
                    Component.translatable("screen.zenkai.technique.reorder_down")));
            addRow(bucket, up, DY_ACTION, index > 0);
            addRow(bucket, down, DY_ICONS, index < rows.size() - 1);

            buildKiIcons(bucket, slot, tech);

        } else if (row instanceof Row.MasterKi(KiTechniqueType type, int slot)) {
            if (!tech.isUnlocked(type)) {
                buildKiUnlockButton(bucket, type);
                return;
            }

            if (slot < 0) {
                // Instancia borrada con la papelera: se puede recrear gratis (el tipo sigue
                // desbloqueado). Se apaga si no queda hueco en la lista de ki.
                boolean room = tech.slotCount() < ServerConfig.techniqueMaxSlots();
                TextOnlyButton create = actionButton(
                        Component.translatable("screen.zenkai.master_techniques.create"),
                        () -> createSignature(type));
                create.active = room;
                addRow(bucket, create, DY_ACTION, room);
                if (!room) {
                    hoverTips.add(new HoverTip(create, () -> List.of(
                            Component.translatable("screen.zenkai.master_techniques.create_full",
                                    ServerConfig.techniqueMaxSlots())
                                    .withStyle(ChatFormatting.RED))));
                }
                return;
            }

            addRow(bucket, actionButton(assignLabel(armedKi == slot), () -> toggleKi(slot)),
                    DY_ACTION, true);
            buildKiIcons(bucket, slot, tech);

        } else if (row instanceof Row.Phys(PhysicalTechnique t, boolean ignored)) {
            if (!tech.isUnlocked(t)) {
                buildUnlockButton(bucket, t);
                return;
            }
            addRow(bucket, actionButton(assignLabel(armedPhys == t), () -> togglePhys(t)),
                    DY_ACTION, true);
            if (tech.positionOf(t) >= 0) {
                addRow(bucket, iconButton(xUnassign(), TEX_X, TEX_X_HL, ICON_BTN,
                        "screen.zenkai.technique.unassign", () -> unbindPhys(t)), DY_ICONS, true);
            }
            // Olvidar una física DEVUELVE el TP (PhysicalTechniquePacket.forget), a diferencia
            // de borrar una instancia de ki, que solo la destruye: el tipo de ki ya se pagó
            // aparte y no se pierde al borrar una de sus instancias.
            addRow(bucket, trashButton(
                    Component.translatable("screen.zenkai.physical.forget", t.tpCost()),
                    Component.translatable("screen.zenkai.physical.forget_confirm"),
                    () -> forgetPhys(t)), DY_TRASH, true);
        }
    }

    /** Lápiz, aspa y papelera de una instancia de ki. Idénticos en la categoría Ki y en la de
     *  maestro: las dos enseñan LA MISMA instancia, así que sus controles no pueden divergir. */
    private void buildKiIcons(List<RowWidget> bucket, int slot, PlayerTechniques tech) {
        addRow(bucket, iconButton(xEdit(), TEX_PENCIL, TEX_PENCIL_HL, ICON_BTN,
                "screen.zenkai.technique.edit",
                () -> mc.setScreen(new TechniqueEditScreen(slot))), DY_ICONS, true);

        if (tech.positionOf(slot) >= 0) {
            addRow(bucket, iconButton(xUnassign(), TEX_X, TEX_X_HL, ICON_BTN,
                    "screen.zenkai.technique.unassign", () -> unbindKi(slot)), DY_ICONS, true);
        }
        addRow(bucket, trashButton("screen.zenkai.technique.delete",
                "screen.zenkai.technique.delete_confirm", () -> deleteKi(slot)), DY_TRASH, true);
    }

    /**
     * Botón de desbloqueo de una física. Heredado del que tenía la antigua PhysicalScreen,
     * incluida su razón de ser: una técnica firma (master() no vacío) se compra DELANTE del
     * maestro (MasterScreen manda el masterId que el servidor exige), así que aquí queda
     * siempre apagada — sin este chequeo se veía clickeable y fallaba en silencio.
     */
    private void buildUnlockButton(List<RowWidget> bucket, PhysicalTechnique t) {
        boolean afford = t.master().isEmpty()
                && att.getTP() >= t.tpCost() && MindBudget.canUnlock(att, t);
        addUnlockButton(bucket, afford,
                () -> PacketDistributor.sendToServer(PhysicalTechniquePacket.unlock(t)),
                () -> unlockTip(Component.translatable(t.nameKey()), t.master(),
                        t.tpCost(), t.mindReq(), MindBudget.costOf(t)));
    }

    /**
     * El mismo botón para un TIPO de ki bloqueado. Solo lo usa la categoría de maestro (en la
     * de ki no hay filas de tipo, sino de instancia: el desbloqueo genérico vive en el editor),
     * así que en la práctica sale siempre apagado — que es justo el punto: la fila de una
     * técnica firma de ki tiene que leerse igual que la de una física bloqueada, con el mismo
     * botón en el mismo sitio diciendo por qué no se puede pulsar, en vez de quedarse sin
     * ningún control y romper la simetría de la lista.
     */
    private void buildKiUnlockButton(List<RowWidget> bucket, KiTechniqueType t) {
        boolean afford = t.master().isEmpty()
                && att.getTP() >= t.tpCost() && MindBudget.canUnlock(att, t);
        addUnlockButton(bucket, afford,
                () -> PacketDistributor.sendToServer(TechniquePacket.unlock(t)),
                () -> unlockTip(Component.translatable(t.nameKey()), t.master(),
                        t.tpCost(), t.mindReq(), MindBudget.costOf(t)));
    }

    private void addUnlockButton(List<RowWidget> bucket, boolean afford, Runnable onClick,
                                 Supplier<List<Component>> tip) {
        TextOnlyButton unlock = actionButton(
                Component.translatable("screen.zenkai.physical.unlock_action"), onClick)
                .textColors(
                        // El color ES la respuesta rápida; el tooltip, la detallada.
                        afford ? ZenkaiPalette.TP_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL,
                        afford ? ZenkaiPalette.VALUE_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL,
                        ZenkaiPalette.MUTED_ON_PANEL);
        unlock.active = afford;
        addRow(bucket, unlock, DY_ACTION, afford);
        hoverTips.add(new HoverTip(unlock, tip));
    }

    /**
     * Requisitos de una técnica bloqueada. Toma los números sueltos en vez del enum porque
     * KiTechniqueType y PhysicalTechnique no comparten ninguna interfaz común pese a exponer
     * los mismos accesores (tpCost/mindReq/master): un tipo común solo para esto obligaría a
     * tocar los dos enums del datapack por una línea de tooltip.
     * Solo enumera lo que FALTA: leer "necesitas 6 MND" teniendo 10 es ruido que compite con
     * el dato que importa.
     */
    private List<Component> unlockTip(Component name, String master, int tpCost, int mindReq,
                                      int mindCost) {
        List<Component> lines = new ArrayList<>();
        lines.add(name);
        lines.add((mindReq > 0
                ? Component.translatable("screen.zenkai.physical.unlock_mnd", tpCost, mindReq)
                : Component.translatable("screen.zenkai.physical.unlock", tpCost))
                .withStyle(ChatFormatting.GRAY));

        if (!master.isEmpty()) {
            // El bloqueo real es "ve a hablar con tu maestro", no TP/MND — enseñar un déficit
            // de fondos aquí sería engañoso: puede sobrarle de sobra y seguir sin poder.
            lines.add(Component.translatable("screen.zenkai.technique.masterOnly",
                    Component.translatable("master.zenkai." + master))
                    .withStyle(ChatFormatting.RED));
            return lines;
        }
        if (att.getTP() < tpCost) {
            lines.add(Component.translatable("screen.zenkai.physical.need_tp",
                    tpCost - att.getTP()).withStyle(ChatFormatting.RED));
        }
        if (MindBudget.free(att) < mindCost) {
            lines.add(Component.translatable("screen.zenkai.physical.need_mnd",
                    mindCost - MindBudget.free(att)).withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private Component assignLabel(boolean armed) {
        return Component.translatable(armed
                ? "screen.zenkai.technique.assigning" : "screen.zenkai.technique.assign");
    }

    /**
     * Botón de acción anclado a la DERECHA y del ancho de su propio texto: con un ancho fijo
     * el texto quedaba centrado en una caja invisible mucho mayor y se leía descolgado del
     * borde. Se suma lo que añade asAction() (corchetes + negrita) o el texto se saldría de la
     * zona clicable.
     * El hover NO usa .onPanel(): esa variante lo pinta en DENIED_ON_PANEL (granate), que en la
     * tabla de ZenkaiPalette significa "no puedes pagarlo" — un rol equivocado para Assign o
     * Edit. Aquí el hover es oro quemado, el color de "valor destacado".
     */
    private TextOnlyButton actionButton(Component label, Runnable onClick) {
        int w = this.font.width(Component.literal("[ ").append(label).append(" ]")) + 2 + BTN_PAD;
        return new TextOnlyButton(xActionRight() - w, 0, w, ACTION_H, label, onClick)
                .textColors(ZenkaiPalette.LABEL_ON_PANEL, ZenkaiPalette.VALUE_ON_PANEL,
                        ZenkaiPalette.MUTED_ON_PANEL)
                .noShadow().asAction();
    }

    /** Icono de acción no destructiva (lápiz o aspa). El aspa es la del mod, no el glifo
     *  Unicode: ese dependía de la fuente instalada y a GUI Scale bajo salía de otro tamaño
     *  que el resto de iconos. */
    private TextOnlyButton iconButton(int x, ResourceLocation tex, ResourceLocation hl,
                                      int size, String tooltipKey, Runnable onClick) {
        TextOnlyButton b = new TextOnlyButton(x, 0, size, size, Component.empty(), tex, hl, onClick);
        b.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return b;
    }

    private ConfirmIconButton trashButton(String idleKey, String confirmKey, Runnable onConfirm) {
        return trashButton(Component.translatable(idleKey), Component.translatable(confirmKey),
                onConfirm);
    }

    private ConfirmIconButton trashButton(Component idle, Component confirm, Runnable onConfirm) {
        return new ConfirmIconButton(xTrash(), 0, TRASH, TEX_TRASH, TEX_TRASH_HL,
                idle, confirm, onConfirm);
    }

    private void addRow(List<RowWidget> bucket, AbstractWidget w, int dy, boolean enabled) {
        bucket.add(new RowWidget(w, dy, enabled));
        addRenderableWidget(w);
    }

    // ── Acciones ─────────────────────────────────────────────────────────────

    private void toggleKi(int slot) {
        armedPhys = null;
        armedKi = (armedKi == slot) ? -1 : slot;
        rebuildWidgets();
    }

    private void togglePhys(PhysicalTechnique t) {
        armedKi = -1;
        armedPhys = (armedPhys == t) ? null : t;
        rebuildWidgets();
    }

    private void unbindKi(int slot) {
        att.techniques().bind(-1, slot);                                 // optimista
        PacketDistributor.sendToServer(TechniquePacket.bind(slot, -1));
        rebuildWidgets();
    }

    private void unbindPhys(PhysicalTechnique t) {
        att.techniques().bindPhysical(-1, t);                            // optimista
        PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(t, -1));
        rebuildWidgets();
    }

    private void deleteKi(int slot) {
        att.techniques().removeSlot(slot);                               // optimista
        PacketDistributor.sendToServer(TechniquePacket.delete(slot));
        clearArmed();
        rebuildWidgets();
    }

    private void forgetPhys(PhysicalTechnique t) {
        // Sin optimista: el reembolso de TP lo calcula el servidor, y adelantarlo aquí dejaría
        // la cabecera enseñando un número que aún no es cierto si el paquete se rechaza. El
        // sync de vuelta cambia la huella y tick() reconstruye solo.
        PacketDistributor.sendToServer(PhysicalTechniquePacket.forget(t));
        if (armedPhys == t) armedPhys = null;
    }

    private void createSignature(KiTechniqueType type) {
        // Mismos valores de partida que usa el servidor al enseñarla (ver
        // TechniquePacket.handleUnlock): el color se fuerza igualmente en handleSave.
        PacketDistributor.sendToServer(TechniquePacket.save(-1, type, "", type.defaultRgb(), 3,
                TechniqueEffect.NONE, null, null, 1));
    }

    /**
     * Mueve una técnica ki dentro de la lista. La asignación viaja CON ella (swapSlots
     * reescribe los bindings), así que subir una técnica no le cambia la tecla.
     */
    private void moveKi(int slot, int dir) {
        int dest = slot + dir;
        if (!att.techniques().swapSlots(slot, dest)) return;             // optimista
        PacketDistributor.sendToServer(TechniquePacket.move(slot, dir));
        if (armedKi == slot) armedKi = dest;
        else if (armedKi == dest) armedKi = slot;
        // Que el destino no se vaya fuera de la ventana visible: si el jugador empuja una fila
        // contra el borde del scroll, la lista lo acompaña en vez de perderla de vista.
        if (dest < scrollRow) scrollRow = dest;
        else if (dest >= scrollRow + visibleRows()) scrollRow = dest - visibleRows() + 1;
        rebuildWidgets();
    }

    /** Clic en una casilla: asigna lo armado, o desasigna al ocupante si no hay nada armado. */
    private void onPositionClicked(int pos) {
        if (att == null) return;
        PlayerTechniques tech = att.techniques();
        if (armedKi >= 0) {
            tech.bind(pos, armedKi);                                     // optimista
            PacketDistributor.sendToServer(TechniquePacket.bind(armedKi, pos));
            armedKi = -1;
            rebuildWidgets();
            return;
        }
        if (armedPhys != null) {
            tech.bindPhysical(pos, armedPhys);                           // optimista
            PacketDistributor.sendToServer(PhysicalTechniquePacket.bind(armedPhys, pos));
            armedPhys = null;
            rebuildWidgets();
            return;
        }
        PhysicalTechnique occPhys = tech.physicalBinding(pos);
        if (occPhys != null) { unbindPhys(occPhys); return; }
        int occKi = tech.binding(pos);
        if (occKi >= 0) unbindKi(occKi);
        // Casilla vacía y nada armado: NO se reconstruye. Hacerlo siempre reseteaba el foco y
        // el hover en cada clic en vacío.
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    /** Lo que obliga a reconstruir: cuántas técnicas hay, cuántas desbloqueadas, y el TP/MND
     *  (deciden qué botón de desbloqueo puede pulsarse y con qué color se pinta). */
    private long signature() {
        if (att == null) return Long.MIN_VALUE;
        PlayerTechniques tech = att.techniques();
        long s = tech.slotCount();
        s = s * 31 + tech.unlockedTypesOrdered().size();
        s = s * 31 + tech.unlockedPhysicalOrdered().size();
        s = s * 31 + att.getTP();
        s = s * 31 + MindBudget.free(att);
        return s;
    }

    @Override
    public void tick() {
        super.tick();
        if (att != null && signature() != builtSignature) this.rebuildWidgets();
    }

    // ── Ratón: el BindBar mira primero ───────────────────────────────────────
    // Tiene que interceptar ANTES que super porque distinguir un clic de un arrastre exige
    // esperar al mouseReleased, y AbstractWidget dispara su acción ya en el mouseClicked.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bindBar != null && bindBar.mouseClicked(att, mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (bindBar != null && bindBar.mouseDragged(att, mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // true = era un arrastre y ya está resuelto. false = era un clic simple, y entonces se
        // deja pasar a super para que la celda ejecute su acción normal.
        if (bindBar != null && bindBar.mouseReleased(att, mouseX, mouseY)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            // Un arrastre en curso se cancela: las filas se mueven bajo el cursor y el destino
            // dejaría de ser el que el jugador estaba mirando.
            if (bindBar != null) bindBar.cancelDrag();
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Teclas 1-9 mientras hay algo armado. Es el mismo gesto que dispara la técnica en
     * combate, así que asignar con la tecla que la va a lanzar deja la asociación hecha en la
     * cabeza del jugador. Fuera del modo asignación los números no deben hacer nada aquí.
     */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (armedKi >= 0 || armedPhys != null) {
            // 49..57 = teclas 1..9 de la fila superior; 321..329 = las del teclado numérico.
            int pos = (key >= 49 && key <= 57) ? key - 49
                    : (key >= 321 && key <= 329) ? key - 321
                    : -1;
            if (pos >= 0) {
                onPositionClicked(pos);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────

    /** Coloca los widgets ANTES de super.render(): es super quien los dibuja, y ajustarlos
     *  después los dejaría un frame por detrás del scroll. */
    private void layoutRows() {
        for (int i = 0; i < rowWidgets.size(); i++) {
            boolean vis = onScreen(i);
            int top = rowTop(i);
            for (RowWidget rw : rowWidgets.get(i)) {
                rw.widget().setY(top + rw.dy());
                rw.widget().visible = vis;
                rw.widget().active = vis && rw.enabled();
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (att != null) layoutRows();

        super.render(g, mouseX, mouseY, partialTick);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);
        if (att == null) return;

        drawHeader(g);
        drawCategorySelector(g, mouseX, mouseY);

        if (bindBar != null) {
            bindBar.refreshIcons(att);
            bindBar.renderFrame(g, this.font, null);
        }

        Component hovered = drawRows(g, mouseX, mouseY);
        drawScrollbar(g);

        if (armedKi >= 0 || armedPhys != null) {
            // Centrado en el hueco QUE DEJA el "+", no en el panel entero: los dos comparten
            // banda y en la categoría ki el botón se come el extremo derecho, así que un
            // centrado ingenuo mete la última palabra del aviso debajo del icono. Se recorta
            // además a ese hueco porque la longitud del aviso depende del idioma.
            int hintRight = rightEdge() - PLUS - BTN_GAP;
            int hintLeft = panelLeft + CONTENT_INSET;
            PanelText.centeredOnPanel(g, this.font,
                    PanelText.fit(this.font,
                            Component.translatable("screen.zenkai.technique.assign_hint"),
                            hintRight - hintLeft),
                    (hintLeft + hintRight) / 2, footerY() + 2, ZenkaiPalette.TP_ON_PANEL);
        }

        // Los tooltips van FUERA del scissor o se recortarían con la lista.
        Component barTip = (bindBar != null) ? bindBar.tooltipAt(att, mouseX, mouseY) : null;
        if (barTip != null) {
            g.renderTooltip(this.font, barTip, mouseX, mouseY);
        } else if (!drawHoverTips(g, mouseX, mouseY)) {
            if (rowTooltip != null) g.renderComponentTooltip(this.font, rowTooltip, mouseX, mouseY);
            else if (hovered != null) g.renderTooltip(this.font, hovered, mouseX, mouseY);
        }

        // El icono que viaja con el cursor va el ÚLTIMO: por encima de las celdas, de la lista
        // y de los tooltips.
        if (bindBar != null) bindBar.renderDragGhost(g, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics g) {
        int y = panelTop + CONTENT_TOP;
        PanelText.onPanel(g, this.font,
                Component.translatable("screen.zenkai.technique.tp", att.getTP()),
                panelLeft + 16, y, ZenkaiPalette.TP_ON_PANEL);

        // Los DOS contadores siempre, en las tres categorías: ki y físicas comparten las nueve
        // casillas, así que ocultar uno al cambiar de categoría escondería justo la mitad del
        // presupuesto que el jugador está repartiendo.
        // Libre / total en MND, no solo el total: con MIND finito el número que decide una
        // compra es lo que queda, y obligar a restar de cabeza es pedirle la cuenta que ya
        // hace el servidor.
        int free = MindBudget.free(att);
        Component mnd = Component.translatable("screen.zenkai.physical.mnd_free",
                free, MindBudget.total(att));
        Component sep = Component.literal(" · ");
        Component ki = Component.translatable("screen.zenkai.technique.count_ki",
                att.techniques().slotCount(), ServerConfig.techniqueMaxSlots());

        int wMnd = this.font.width(mnd), wSep = this.font.width(sep), wKi = this.font.width(ki);
        int x = rightEdge() - wMnd - wSep - wKi;
        PanelText.onPanel(g, this.font, ki, x, y, ZenkaiPalette.MUTED_ON_PANEL);
        PanelText.onPanel(g, this.font, sep, x + wKi, y, ZenkaiPalette.MUTED_ON_PANEL);
        PanelText.onPanel(g, this.font, mnd, x + wKi + wSep, y,
                free < 0 ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.MIND_ON_PANEL);

        g.fill(panelLeft + 14, y + 12, rightEdge(), y + 13, ZenkaiPalette.SEPARATOR);
    }

    /**
     * El icono y la etiqueta se pintan a mano dentro de la caja clicable invisible que monta
     * buildCategorySelector (así el grupo entero queda centrado, en vez de centrar solo el
     * texto y dejar el icono colgando a un lado). Como el TextOnlyButton de debajo no dibuja
     * nada, el realce del cursor hay que resolverlo aquí: sin él la caja se pulsa pero no
     * parece pulsable, y las dos flechas de al lado sí se encienden — la incoherencia se lee
     * como "el centro no hace nada".
     */
    private void drawCategorySelector(GuiGraphics g, int mouseX, int mouseY) {
        int cx = panelLeft + BG_W / 2;
        int y = panelTop + CAT_Y_OFF;
        Component label = PanelText.fit(this.font, Component.translatable(category.labelKey),
                CAT_HALF * 2 - CAT_ICON - 10);
        int groupW = CAT_ICON + 4 + this.font.width(label);
        int x = cx - groupW / 2;
        boolean hover = mouseX >= cx - CAT_HALF + 2 && mouseX < cx + CAT_HALF - 2
                && mouseY >= y && mouseY < y + CAT_H;
        if (hover) g.setColor(1.15F, 1.15F, 1.15F, 1.0F);
        g.blit(ICONS, x, y, category.u, category.v, CAT_ICON, CAT_ICON, 256, 256);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        PanelText.onPanel(g, this.font, label, x + CAT_ICON + 4, y + (CAT_H - 8) / 2,
                hover ? ZenkaiPalette.VALUE_ON_PANEL : ZenkaiPalette.LABEL_ON_PANEL);
        g.fill(panelLeft + 14, y + CAT_H + 2, rightEdge(), y + CAT_H + 3, ZenkaiPalette.SEPARATOR);
    }

    /**
     * Tooltip de la fila bajo el cursor, resuelto en drawRows y pintado después del scissor.
     * Es una LISTA y no un Component suelto porque las filas de ki y las físicas necesitan
     * varias líneas con estilos distintos (el efecto va con su propio color).
     */
    private @Nullable List<Component> rowTooltip;

    /** Dibuja la lista y devuelve el tooltip simple de la fila señalada, o null. */
    private @Nullable Component drawRows(GuiGraphics g, int mouseX, int mouseY) {
        rowTooltip = null;
        if (rows.isEmpty()) {
            PanelText.centeredOnPanel(g, this.font, Component.translatable(switch (category) {
                        case KI -> "screen.zenkai.technique.empty";
                        case PHYSICAL -> "screen.zenkai.physical.empty";
                        case MASTER -> "screen.zenkai.master_techniques.empty";
                    }),
                    panelLeft + BG_W / 2, listTop() + viewHeight() / 2 - 4,
                    ZenkaiPalette.MUTED_ON_PANEL);
            return null;
        }

        Component hovered = null;
        g.enableScissor(panelLeft + 4, listTop(), panelLeft + BG_W - 4, listTop() + viewHeight());

        int last = Math.min(rows.size(), scrollRow + visibleRows());
        for (int i = scrollRow; i < last; i++) {
            int y = rowTop(i);
            Component tip = drawRow(g, i, rows.get(i), y);

            // El tooltip de la fila solo se ofrece sobre la ZONA DE TEXTO, no sobre el cluster
            // de botones: cada botón tiene ya su propio Tooltip, y como este se pinta después
            // de super.render() acabaría encima del suyo, dos tooltips solapados sobre el
            // mismo cursor.
            if (mouseX >= xIcon() && mouseX < xEdit() - BTN_GAP
                    && mouseY >= y && mouseY < y + ROW_H - 2) {
                hovered = tip;
                rowTooltip = rowTooltipFor(rows.get(i));
            }

            // La regla va por DEBAJO del cluster (los iconos ocupan hasta y+ROW_H-2): dibujarla
            // una fila de píxeles más arriba la pintaba encima del borde inferior del lápiz y
            // el aspa, porque esta lista se dibuja DESPUÉS de super.render().
            if (i < last - 1) {
                g.fill(xIcon(), y + ROW_H - 2, rightEdge(), y + ROW_H - 1, ZenkaiPalette.SEPARATOR);
            }
        }
        g.disableScissor();
        return hovered;
    }

    private @Nullable Component drawRow(GuiGraphics g, int index, Row row, int y) {
        PlayerTechniques tech = att.techniques();
        int nameX = xText();
        // Ancho útil de cada renglón: el de arriba lo limita el botón de acción y el de abajo
        // el cluster de iconos, y los dos empiezan más a la derecha en las filas sin flechas.
        int topRight = actionLeft(index) - BTN_GAP;
        int botRight = rowIconsLeft(index) - BTN_GAP;

        if (row instanceof Row.Ki(int slot, KiTechnique t)) {
            if (armedKi == slot) veil(g, y);
            TechniqueIcons.draw(g, xIcon(), y + 5, ICON, t);
            drawNameAndSlot(g, nameX, y, topRight, t.displayName(), tech.positionOf(slot), true);
            PanelText.onPanel(g, this.font,
                    PanelText.fit(this.font, kiRowStats(t), botRight - nameX),
                    nameX, y + DY_ICONS + 1, ZenkaiPalette.BODY_ON_PANEL);
            return null;   // el tooltip de esta fila es compuesto, ver kiRowTooltip
        }

        if (row instanceof Row.MasterKi(KiTechniqueType type, int slot)) {
            boolean unlocked = tech.isUnlocked(type);
            if (slot >= 0 && armedKi == slot) veil(g, y);
            TechniqueIcons.draw(g, xIcon(), y + 5, ICON, type, type.defaultRgb());
            Component name = Component.translatable(type.nameKey());
            PanelText.onPanel(g, this.font, PanelText.fit(this.font, name, topRight - nameX),
                    nameX, y + 4,
                    unlocked ? ZenkaiPalette.OWNED_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL);
            Component master = Component.translatable("master.zenkai." + type.master());
            // Con instancia ya creada la sub-línea enseña sus NÚMEROS, igual que cualquier otra
            // fila de ki; quién la enseña pasa al tooltip. Sin instancia (bloqueada, o borrada)
            // el dato que falta es justo el maestro, así que ese ocupa la sub-línea.
            KiTechnique inst = slot >= 0 ? tech.slot(slot) : null;
            Component sub = inst != null
                    ? kiRowStats(inst)
                    : Component.translatable(unlocked
                            ? "screen.zenkai.master_techniques.taught_by"
                            : "screen.zenkai.master_techniques.locked", master);
            PanelText.onPanel(g, this.font, PanelText.fit(this.font, sub, botRight - nameX),
                    nameX, y + DY_ICONS + 1,
                    unlocked ? ZenkaiPalette.BODY_ON_PANEL : ZenkaiPalette.DENIED_ON_PANEL);
            return inst != null ? null : name;
        }

        if (row instanceof Row.Phys(PhysicalTechnique t, boolean fromMaster)) {
            boolean unlocked = tech.isUnlocked(t);
            if (armedPhys == t) veil(g, y);
            PhysicalIcons.draw(g, xIcon(), y + 5, ICON, t);
            Component name = Component.translatable(t.nameKey());
            PanelText.onPanel(g, this.font, PanelText.fit(this.font, name, topRight - nameX),
                    nameX, y + 4,
                    unlocked ? ZenkaiPalette.OWNED_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL);

            // La marca [n] va pegada al nombre, no en columna fija: con nombres de ancho
            // distinto quedaba un salto vacío y se leía suelta de lo que anuncia.
            int pos = tech.positionOf(t);
            if (pos >= 0) {
                PanelText.onPanel(g, this.font, Component.literal("[" + (pos + 1) + "]"),
                        nameX + this.font.width(name) + 5, y + 4, ZenkaiPalette.OK_ON_PANEL);
            }

            // En la categoría de maestro la sub-línea dice QUIÉN la enseña: los números se
            // pueden consultar en la categoría Physical y aquí el dato que falta es otro.
            Component sub = fromMaster
                    ? Component.translatable(unlocked
                            ? "screen.zenkai.master_techniques.taught_by"
                            : "screen.zenkai.master_techniques.locked",
                            Component.translatable("master.zenkai." + t.master()))
                    : physRowStats(t);
            PanelText.onPanel(g, this.font, PanelText.fit(this.font, sub, botRight - nameX),
                    nameX, y + DY_ICONS + 1,
                    unlocked ? ZenkaiPalette.BODY_ON_PANEL
                             : (fromMaster ? ZenkaiPalette.DENIED_ON_PANEL
                                           : ZenkaiPalette.MUTED_ON_PANEL));
            return null;   // tooltip compuesto, ver physRowTooltip
        }
        return null;
    }

    /** Nombre + la tecla asignada, pegada a él. En columna fija quedaba un salto vacío entre
     *  los dos y el "[1]" se leía suelto, sin relación visual con lo que anuncia. */
    private void drawNameAndSlot(GuiGraphics g, int nameX, int y, int right,
                                 Component name, int pos, boolean owned) {
        Component fitted = PanelText.fit(this.font, name,
                right - nameX - (pos >= 0 ? this.font.width(" [9]") : 0));
        PanelText.onPanel(g, this.font, fitted, nameX, y + 4,
                owned ? ZenkaiPalette.OWNED_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL);
        if (pos >= 0) {
            PanelText.onPanel(g, this.font, Component.literal(" [" + (pos + 1) + "]"),
                    nameX + this.font.width(fitted), y + 4, ZenkaiPalette.OK_ON_PANEL);
        }
    }

    /**
     * Sub-línea de una técnica de ki: DAÑO y COSTE reales a carga 100 %, no el tamaño.
     * El tamaño es una entrada de la fórmula, no un resultado: "Size: 5" no dice si la técnica
     * pega más o menos que la de al lado ni si el jugador puede permitírsela. Los dos números
     * salen de KiCombatServer, la misma autoridad que cobra el disparo — el cliente no
     * reimplementa la fórmula (ver el javadoc de chargeTicksFor sobre los doce sitios que
     * tienen que pasar por ahí). El tamaño sigue disponible, en el tooltip.
     * BARRIER no hace daño (damage_mult 0.0 en su datapack), así que enseña su absorción, el
     * mismo caso especial que TechniqueEditScreen.renderCombatTab ya resuelve con
     * type.defensive().
     */
    private Component kiRowStats(KiTechnique t) {
        double kiPower = att.computeKiPowerFinal();
        int cost = KiCombatServer.computeCost(att, t.type(), t.size(), t.effect());
        if (t.type().defensive()) {
            return Component.translatable("screen.zenkai.technique.row_ki_barrier",
                    fmt(KiCombatServer.barrierPool(kiPower, t.size(), 1.0)), cost);
        }
        double dmg = KiCombatServer.computeDamage(kiPower, t.type(), t.size())
                * Math.max(1, t.type().count());
        return Component.translatable("screen.zenkai.technique.row_ki", fmt(dmg), cost);
    }

    /** Sub-línea de una física: daño y estamina REALES del jugador, no los multiplicadores del
     *  datapack. "x1.2 dmg · 220% stam" no dice nada sin saber contra qué se multiplican; los
     *  multiplicadores pasan al tooltip, que es donde sirven para comparar entre técnicas. */
    private Component physRowStats(PhysicalTechnique t) {
        return Component.translatable("screen.zenkai.technique.row_phys",
                fmt(PhysicalCombatServer.previewDamage(att, t)),
                PhysicalCombatServer.staminaCost(att, t));
    }

    /**
     * Qué tooltip le toca a cada fila. Solo las que TIENEN instancia o están desbloqueadas
     * llevan ficha: en una bloqueada el dato que importa (quién la enseña, qué falta para
     * pagarla) ya está en la sub-línea o en el tooltip del propio botón de desbloqueo, y
     * duplicarlo taparía ese con este.
     */
    private @Nullable List<Component> rowTooltipFor(Row row) {
        PlayerTechniques tech = att.techniques();
        if (row instanceof Row.Ki(int slot, KiTechnique t)) return kiRowTooltip(t);
        if (row instanceof Row.MasterKi(KiTechniqueType type, int slot)) {
            KiTechnique inst = slot >= 0 ? tech.slot(slot) : null;
            if (inst == null) return null;
            List<Component> lines = kiRowTooltip(inst);
            lines.add(Component.translatable("screen.zenkai.master_techniques.taught_by",
                            Component.translatable("master.zenkai." + type.master()))
                    .withStyle(ChatFormatting.GRAY));
            return lines;
        }
        if (row instanceof Row.Phys(PhysicalTechnique t, boolean ignored)) {
            return tech.isUnlocked(t) ? physRowTooltip(t) : null;
        }
        return null;
    }

    /** Tooltip de una fila de ki: lo que ya no cabe en la sub-línea. El efecto va con SU color
     *  (TechniqueEffect.panelRgb, la variante legible sobre el beige) porque el color es su
     *  identidad — el mismo criterio con el que el editor pinta esa fila. */
    private List<Component> kiRowTooltip(KiTechnique t) {
        List<Component> lines = new ArrayList<>();
        lines.add(t.displayName());
        lines.add(Component.translatable("screen.zenkai.technique.tip_size", t.size())
                .withStyle(ChatFormatting.GRAY));
        if (t.effect() != TechniqueEffect.NONE) {
            lines.add(Component.translatable(t.effect().langKey()).withStyle(
                    st -> st.withColor(TextColor.fromRgb(t.effect().panelRgb() & 0xFFFFFF))));
        }
        lines.add(Component.translatable("screen.zenkai.technique.tip_charge",
                        fmt(KiCombatServer.chargeTicksFor(t.type(), t.size()) / 20.0),
                        fmt(KiCombatServer.cooldownTicksFor(t.type(), t.size()) / 20.0))
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** Tooltip de una física: los multiplicadores crudos y el resto de la ficha técnica.
     *  El porcentaje se redondea ANTES de formatear: 2.2 * 100 da 220.00000000000003 en coma
     *  flotante, así que fmt() lo veía como no-entero y escribía "220.0%" mientras 2.5 * 100
     *  salía limpio y daba "250%" — dos formatos en la misma columna. */
    private List<Component> physRowTooltip(PhysicalTechnique t) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(t.nameKey()));
        lines.add(Component.translatable("screen.zenkai.technique.tip_phys",
                        fmt(t.dmgMult()), fmt(t.cooldownTicks() / 20.0), fmt(t.range()))
                .withStyle(ChatFormatting.GRAY));
        int hits = PhysicalCombatServer.hitsPerUse(t);
        if (hits > 1) {
            // Sin esto el x0.3 de BARRAGE se lee como una técnica inútil: su daño por impacto
            // es bajo porque golpea seis veces.
            lines.add(Component.translatable("screen.zenkai.technique.tip_hits", hits)
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** Realce de la fila armada. Se ciñe a la zona de beige (ver rightEdge): a panelLeft+10,
     *  como estaba heredado, el velo dorado se derramaba sobre el marco naranja del panel. */
    private void veil(GuiGraphics g, int y) {
        g.fill(panelLeft + CONTENT_INSET - 3, y, scrollbarX() + SCROLLBAR_W, y + ROW_H - 2,
                ZenkaiPalette.SELECT_VEIL);
    }

    /** X donde empieza el botón de acción de esa fila, o su columna si no lo tiene: hasta ahí
     *  puede llegar el nombre. Cada botón mide lo que mide su propia etiqueta, así que no vale
     *  una columna fija. */
    private int actionLeft(int index) {
        if (index < rowWidgets.size()) {
            for (RowWidget rw : rowWidgets.get(index)) {
                if (rw.dy() == DY_ACTION && rw.widget() instanceof TextOnlyButton b) {
                    return b.getX();
                }
            }
        }
        return xActionRight();
    }

    /**
     * X del widget más a la izquierda del CLUSTER DERECHO de esa fila (lápiz, aspa, papelera),
     * o el borde si no tiene ninguno: hasta ahí puede llegar la sub-línea.
     * Filtra por COLUMNA y no por renglón: la flecha de bajar comparte renglón con el cluster
     * pero vive en el canalón IZQUIERDO, así que incluirla devolvía la X de la flecha y dejaba
     * la sub-línea con un ancho negativo.
     */
    private int rowIconsLeft(int index) {
        if (index >= rowWidgets.size()) return rightEdge();
        int left = rightEdge();
        for (RowWidget rw : rowWidgets.get(index)) {
            int x = rw.widget().getX();
            if (x >= xEdit()) left = Math.min(left, x);
        }
        return left;
    }

    /** Tooltips de widgets que pueden estar inactivos (desbloqueo, recrear). Devuelve true si
     *  pintó alguno, para que no compita con el de la fila. */
    private boolean drawHoverTips(GuiGraphics g, int mouseX, int mouseY) {
        for (HoverTip tip : hoverTips) {
            AbstractWidget w = tip.widget();
            if (!w.visible) continue;
            if (mouseX < w.getX() || mouseX >= w.getX() + w.getWidth()
                    || mouseY < w.getY() || mouseY >= w.getY() + w.getHeight()) continue;
            g.renderComponentTooltip(this.font, tip.lines().get(), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;
        int x = scrollbarX();
        int top = listTop(), h = viewHeight();
        g.fill(x, top, x + SCROLLBAR_W, top + h, ZenkaiPalette.BAR_BG);
        int thumbH = Math.max(12, h * visibleRows() / rows.size());
        int thumbY = top + (h - thumbH) * scrollRow / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.VALUE_ON_PANEL);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, v == Math.floor(v) ? "%.0f" : "%.1f", v);
    }
}
