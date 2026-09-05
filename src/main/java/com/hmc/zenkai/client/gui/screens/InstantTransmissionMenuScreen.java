package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.InstantTransmissionClientState;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.party.ClientPartyState;
import com.hmc.zenkai.feature.party.PartySyncPacket;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.teleport.GenericDimensionDestinations;
import com.hmc.zenkai.feature.teleport.GenericDimensionTeleportPacket;
import com.hmc.zenkai.feature.teleport.GenericSubDestination;
import com.hmc.zenkai.feature.teleport.PartyTeleportRequestPacket;
import com.hmc.zenkai.feature.teleport.TeleportDestination;
import com.hmc.zenkai.feature.teleport.TeleportRealm;
import com.hmc.zenkai.feature.teleport.TeleportRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Menú de planetas de la Fase 2+3 de Instant Transmission. Se abre desde el juego (nunca una
 * pestaña de ZenkaiMenuScreen) cuando el servidor manda OpenInstantTransmissionMenuPayload —
 * soltar TAB tras mantenerlo quieto 2s (InstantTransmissionAttachment.MENU_ARM_TICKS) con la
 * skill a nivel 3+ (ver InstantTransmissionSystem).
 * Sin panel opaco detrás (nada del mundo lo cubre): diálogo flotante estilo MasterScreen.
 * Cuatro modos, mismo patrón hub->lista que MasterScreen: REALMS (planetas/dimensiones) ->
 * DESTINATIONS (estructuras dentro de un planeta CURADO), GENERIC_DESTINATIONS (sub-destinos de
 * una dimensión GENÉRICA con 2+ entradas, ver GenericDimensionDestinations) o PARTY_MEMBERS. El
 * estado (disponible/bloqueado) se resuelve aquí con la MISMA lógica que ya valida el servidor
 * (TeleportRequestPacket.handle / GenericDimensionTeleportPacket.handle) — un cliente sin
 * modificar nunca debería poder pulsar algo que el servidor fuera a rechazar, pero el rechazo
 * real vive del lado servidor de todos modos.
 * REALMS mezcla TRES tipos de fila en un único espacio de índices (ver {@link RealmRow}):
 *  - CURADOS (Overworld/Otherworld, {@link TeleportRealm}): tienen varios destinos con ancla fija
 *    compartida (Home/Kami's Palace/Korin's Tower; Yemma/Kaiosama) — abren DESTINATIONS al
 *    pulsarlos.
 *  - GENÉRICOS (cualquier OTRA dimensión visitada, de cualquier mod — pedido explícito del
 *    usuario: "el uv del ícono se guíe por el nombre de la dimensión independientemente del
 *    mod"): el Nether y el End YA NO son casos especiales, son el primer y segundo ejemplo de
 *    este mecanismo genérico. La mayoría (incluido el Nether hoy) tienen un solo destino
 *    implícito ("tu última llegada a esa dimensión", ver DimensionEntryTracker) y pulsarlas
 *    teletransporta directamente, sin submenú — una dimensión con 2+ entradas propias en
 *    GenericDimensionDestinations (el End: isla principal fija + islas exteriores por última
 *    visita) abre en cambio GENERIC_DESTINATIONS, MISMO comportamiento visual que un realm
 *    curado. Pedido explícito del usuario tras la Fase 2: "no se abre el submenú... limita mucho
 *    para añadir nuevos tps" — antes de esta ronda una dimensión genérica solo podía tener un
 *    destino posible.
 *  - PARTY: caso fijo, siempre la ÚLTIMA fila (ya no hay una fila "Dimensión Desconocida" que
 *    tuviera que ir después — ese placeholder se retiró al generalizar el sistema: ahora
 *    cualquier dimensión de un mod de terceros aparece como una fila GENÉRICA real, con ícono de
 *    reserva si no se reconoce, en vez de una fila fija "sin implementar todavía").
 * Scroll (los cuatro modos): MISMO patrón que MasterScreen (scroll/rowCount/maxScroll/onScreen +
 * scissor + drawScrollbar) — con más de una dimensión modeada visitada, o una party grande, la
 * lista se desplaza en vez de salirse del marco del diálogo.
 */
public class InstantTransmissionMenuScreen extends Screen {

    private enum Mode { REALMS, DESTINATIONS, GENERIC_DESTINATIONS, PARTY_MEMBERS }

    private static final int BG_W = 210;
    private static final int BG_H = 180;
    private static final int PADDING = 10;
    private static final int CONTENT_TOP = 26;
    private static final int ROW_H = 24;
    private static final int ROW_ICON_GAP = 6;
    private static final int BACK_ROW_H = 12;
    private static final int TOOLTIP_W = 180;

    /** Mismos valores que MasterScreen.SCROLLBAR_W/GAP — no hay razón para que la barra de
     *  scroll de este diálogo se vea distinta a la del resto del mod. */
    private static final int SCROLLBAR_W = 4;
    private static final int SCROLLBAR_GAP = 6;

    /** Tamaño de la cara del jugador en la fila de compañeros de party. Un icono DISTINTO al
     *  resto (PlayerFaceRenderer dibuja directo de la skin, no de icons_instant_transmision.png),
     *  así que no comparte constante con ICON_CELL aunque el valor visual sea parecido. */
    private static final int FACE_ICON = 16;

    // =========================
    // Icons atlas — mismo patrón que ClientZenkaiHooks (IconUV.grid + ICON_DRAW = ancho de la
    // celda ENTERA, nunca recortada).
    // =========================
    private static final ResourceLocation ICONS_TEX = ResourceLocation
            .fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons_instant_transmision.png");
    /** Atlas COMPARTIDO del resto del mod (pestañas de ZenkaiMenuScreen, TabIconButton) — la fila
     *  Party de este menú pide expresamente el MISMO ícono que ZenkaiTab.PARTY en vez de tener
     *  su propia celda en icons_instant_transmision.png, así que necesita su propio par
     *  textura+u/v en vez de ICONS_TEX. Mismo tamaño de grid (20px, 256x256) que ese atlas, así
     *  que IconUV.grid(...) sirve igual para las dos texturas. */
    private static final ResourceLocation SHARED_ICONS_TEX = ResourceLocation
            .fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ICONS_ATLAS = 256;
    private static final int ICON_CELL = 20;     // tamaño real de celda en el atlas
    private static final int ICON_DRAW = 20;     // tamaño al dibujar el icono (== ICON_CELL)

    private record IconUV(int u, int v) {
        static IconUV grid(int col, int row) {
            return new IconUV(col * ICON_CELL, row * ICON_CELL);
        }
    }

    /** Fondo del diálogo: campo de estrellas + nebulosa + marco de tres anillos, ver
     *  tools/gen_instant_transmission_menu.py — "aires de espacio", pedido explícito del
     *  usuario, en vez del marco genérico DIALOG_BG/BORDER_IN que llevaba esta pantalla antes.
     *  El marco viene YA HORNEADO dentro de los BG_W×BG_H píxeles (mismo criterio que
     *  master_screen.png), no dibujado aparte por fuera como antes. */
    private static final ResourceLocation BG_TEX = ResourceLocation
            .fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/instant_transmission_menu.png");

    private static void drawIcon(GuiGraphics g, int x, int y, IconUV icon) {
        drawIcon(g, x, y, ICONS_TEX, icon);
    }

    /** Ídem, de una textura distinta a ICONS_TEX (ver SHARED_ICONS_TEX) — la fila Party es hoy
     *  la única llamante, pero cualquier ícono futuro que deba salir de icons.png en vez del
     *  atlas propio de este menú pasa por aquí igual. */
    private static void drawIcon(GuiGraphics g, int x, int y, ResourceLocation tex, IconUV icon) {
        g.blit(tex, x, y, icon.u(), icon.v(), ICON_DRAW, ICON_DRAW, ICONS_ATLAS, ICONS_ATLAS);
    }

    /** Mismo drawIcon, atenuado (gris 55%, igual que el resto del mod trata una fila bloqueada)
     *  cuando {@code dim} es true — evita repetir el par setColor/blit/setColor en cada sitio. */
    private static void drawIcon(GuiGraphics g, int x, int y, IconUV icon, boolean dim) {
        if (dim) g.setColor(0.55F, 0.55F, 0.55F, 1.0F);
        drawIcon(g, x, y, icon);
        if (dim) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Ídem, de SHARED_ICONS_TEX (ver el overload de arriba) — usado por la fila Party. */
    private static void drawIcon(GuiGraphics g, int x, int y, ResourceLocation tex, IconUV icon, boolean dim) {
        if (dim) g.setColor(0.55F, 0.55F, 0.55F, 1.0F);
        drawIcon(g, x, y, tex, icon);
        if (dim) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ── Destinos (Home/Kami/Korin/Yemma/Kaiosama): celdas 5..9 de la fila v=0, +5 = número de
    // realms curados históricos. El orden del enum TeleportDestination IMPORTA aquí: cada
    // columna ya está pintada a mano en el atlas real (ver el comentario de KORIN_TOWER en ese
    // enum), así que insertar un valor nuevo en cualquier posición que no sea "justo donde su
    // columna ya existe" desalinea todos los destinos que van después de él. ──
    private static IconUV destIcon(TeleportDestination dest) { return IconUV.grid(5 + dest.ordinal(), 0); }

    /** El MISMO ícono que la pestaña Party del menú Zenkai (ZenkaiTab.PARTY, u=80/v=20 dentro de
     *  icons.png) — pedido explícito del usuario, en vez de una celda propia dentro de
     *  icons_instant_transmision.png: las dos "Party" del mod (la pestaña y esta fila) deben
     *  leerse como el mismo concepto, así que comparten arte en vez de tener cada una la suya.
     *  Vive en SHARED_ICONS_TEX, no en ICONS_TEX — ver los overloads de drawIcon. */
    private static final IconUV ICON_PARTY = new IconUV(ZenkaiTab.PARTY.u, ZenkaiTab.PARTY.v);

    /** Ícono de una dimensión GENÉRICA (Nether, End, o cualquier dimensión de un mod de
     *  terceros), resuelto por su ResourceLocation — pedido explícito del usuario: "que el uv
     *  del ícono se guíe por el nombre de la dimensión independientemente del mod y si no
     *  encuentra la dimensión rebote a una default". Nether/End usan las columnas 1/2 que YA
     *  tenían dibujadas a mano en el atlas cuando eran TeleportRealm.NETHER/END; cualquier otra
     *  dimensión cae en la columna 4 — el mismo "?" que antes era el placeholder fijo de
     *  "Dimensión Desconocida", reaprovechado como ícono de reserva de verdad en vez de una fila
     *  aparte que nunca hacía nada. */
    private static final Map<ResourceLocation, Integer> KNOWN_DIM_ICON_COLUMN = Map.of(
            Level.NETHER.location(), 1,
            Level.END.location(), 2);
    private static final int DEFAULT_DIM_ICON_COLUMN = 4;

    private static IconUV genericDimIcon(ResourceLocation dim) {
        return IconUV.grid(KNOWN_DIM_ICON_COLUMN.getOrDefault(dim, DEFAULT_DIM_ICON_COLUMN), 0);
    }

    /** Nombre legible de una dimensión GENÉRICA sin lang key propia (no podemos tener una clave
     *  de idioma para cada dimensión de cada mod de antemano): humaniza el path del
     *  ResourceLocation ("the_nether" -> "The Nether", "frozen_realm" -> "Frozen Realm").
     *  Vainilla ya encaja razonablemente bien con este esquema sin necesitar caso especial. */
    private static Component genericDimName(ResourceLocation dim) {
        return Component.literal(humanize(dim.getPath()));
    }

    /** "the_nether" -> "The Nether", "outer_islands" -> "Outer Islands" — extraído de
     *  genericDimName para que subDestName pueda reusar el mismo criterio sin necesitar
     *  construir un ResourceLocation falso solo para pasárselo. */
    private static String humanize(String path) {
        String[] words = path.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    // ── Filas de REALMS: un único espacio de índices para 3 tipos de fila — ver el javadoc de
    // clase. sealed + record patterns (Java 21) en vez de un enum "tipo" + campos opcionales. ──
    private sealed interface RealmRow {}
    private record CuratedRow(TeleportRealm realm) implements RealmRow {}
    private record GenericRow(ResourceLocation dimension) implements RealmRow {}
    private record PartyRow() implements RealmRow {}

    private record LockedTooltip(String key, Object[] args) {}

    private Mode mode = Mode.REALMS;
    private TeleportRealm selectedRealm;
    private List<TeleportDestination> destRows = List.of();

    /** Análogo a selectedRealm/destRows, pero para el submenú de una dimensión GENÉRICA con 2+
     *  sub-destinos (ver GenericDimensionDestinations) — Mode.GENERIC_DESTINATIONS. */
    private ResourceLocation selectedGenericDim;
    private List<GenericSubDestination> genericDestRows = List.of();

    /** Fila superior visible de la lista ACTUAL (unidad: filas, no píxeles) — un solo campo
     *  compartido por los tres modos, igual que MasterScreen.scroll; se resetea a 0 en cada
     *  transición de modo (ver clickRealms/mouseClicked), nunca se arrastra de una lista a otra. */
    private int scroll = 0;

    private int left, top;

    public InstantTransmissionMenuScreen() {
        super(Component.translatable("screen.zenkai.instant_transmission.title"));
    }

    @Override
    protected void init() {
        left = (this.width - BG_W) / 2;
        top = (this.height - BG_H) / 2;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    // ── Geometría ────────────────────────────────────────────────────────────

    private int contentLeft()  { return left + PADDING; }
    /** Borde exterior de la lista, donde la scrollbar queda a ras — MISMO rol que
     *  MasterScreen.trackRight(). */
    private int trackRight()   { return left + BG_W - PADDING; }
    private int scrollbarX()   { return trackRight() - SCROLLBAR_W; }
    /** Borde derecho REAL del texto/hover/separador: deja sitio FIJO a la scrollbar, se dibuje o
     *  no en el modo actual — mismo criterio que MasterScreen.listRight(). */
    private int contentRight() { return scrollbarX() - SCROLLBAR_GAP; }
    private int listTop()      { return top + CONTENT_TOP; }
    private int rowsTop()      { return listTop() + (mode != Mode.REALMS ? BACK_ROW_H : 0); }
    private int listHeight()   { return BG_H - CONTENT_TOP - PADDING - (mode != Mode.REALMS ? BACK_ROW_H : 0); }
    private int visibleRows()  { return Math.max(1, listHeight() / ROW_H); }
    private int rowTop(int i)  { return rowsTop() + (i - scroll) * ROW_H; }

    private boolean onScreen(int i) {
        int rel = i - scroll;
        return rel >= 0 && rel < visibleRows();
    }

    private boolean rowHovered(int y, int mouseX, int mouseY) {
        return mouseX >= contentLeft() - 2 && mouseX <= contentRight() && mouseY >= y && mouseY < y + ROW_H - 1;
    }

    private int rowCount() {
        return switch (mode) {
            case REALMS -> realmRows().size();
            case DESTINATIONS -> destRows.size();
            case GENERIC_DESTINATIONS -> genericDestRows.size();
            case PARTY_MEMBERS -> partyMemberRows(ClientPartyState.current()).size();
        };
    }

    private int maxScroll() { return Math.max(0, rowCount() - visibleRows()); }

    private boolean backHovered(int mouseX, int mouseY) {
        return mode != Mode.REALMS && mouseX >= contentLeft() - 2 && mouseX <= contentRight()
                && mouseY >= listTop() && mouseY < listTop() + BACK_ROW_H;
    }

    /** Filas de nivel 1, en orden: Overworld (curado) -> dimensiones GENÉRICAS visitadas
     *  (cualquier mod, orden alfabético por nombre para que sea determinista sin depender de un
     *  orden de enum que ya no existe) -> Otherworld (curado, si se visitó) -> Party (SIEMPRE la
     *  última). Ver el javadoc de clase para el porqué de cada tipo.
     *  Una dimensión BLOQUEADA por datapack (ver InstantTransmissionClientState.isBlocked,
     *  espejo de InstantTransmissionBlocklist) directamente no entra en esta lista — ni como
     *  realm curado ni como fila genérica — pedido explícito del usuario: "la dimensión no
     *  aparece en el menú y ya está", sin fila atenuada ni tooltip, a diferencia de "nivel
     *  insuficiente" o "no descubierta todavía". */
    private List<RealmRow> realmRows() {
        List<RealmRow> out = new ArrayList<>();
        if (!InstantTransmissionClientState.isBlocked(TeleportRealm.OVERWORLD.dimension().location().toString())) {
            out.add(new CuratedRow(TeleportRealm.OVERWORLD));
        }
        for (ResourceLocation dim : visibleGenericDimensions()) out.add(new GenericRow(dim));
        String otherworldId = TeleportRealm.OTHERWORLD.dimension().location().toString();
        if (InstantTransmissionClientState.hasVisitedDimension(otherworldId)
                && !InstantTransmissionClientState.isBlocked(otherworldId)) {
            out.add(new CuratedRow(TeleportRealm.OTHERWORLD));
        }
        out.add(new PartyRow());
        return out;
    }

    /** Toda dimensión visitada que NO sea una de las dos curadas (Overworld/Otherworld, que
     *  tienen su propia fila con destinos y ancla fija) y que NO esté bloqueada por datapack —
     *  pedido explícito del usuario: "que se pueda hacer de manera universal para cada
     *  dimensión modeada", con la posibilidad de bloquear algunas por completo. */
    private List<ResourceLocation> visibleGenericDimensions() {
        List<ResourceLocation> out = new ArrayList<>();
        for (String id : InstantTransmissionClientState.visitedDimensionIdsView()) {
            if (InstantTransmissionClientState.isBlocked(id)) continue;
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc == null) continue;
            if (loc.equals(Level.OVERWORLD.location())) continue;
            if (loc.equals(TeleportRealm.OTHERWORLD.dimension().location())) continue;
            out.add(loc);
        }
        out.sort(Comparator.comparing(loc -> genericDimName(loc).getString(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** ¿Puede el jugador saltar a esta dimensión ahora mismo? Ya estar en ella nunca necesita el
     *  nivel de salto entre dimensiones (mismo criterio que TeleportDestination.
     *  executableThisPhase ya aplica para los destinos curados). */
    private boolean crossDimensionOk(ResourceLocation dim) {
        assert this.minecraft != null && this.minecraft.level != null;
        if (dim.equals(this.minecraft.level.dimension().location())) return true;
        return SkillEffects.instantTransmissionCrossDimensionUnlocked(this.minecraft.player);
    }

    // ── Render — VARIANTE A: renderBackground pinta el marco, render() dibuja encima.
    // Nunca llamar this.renderBackground(...) a mano dentro de render(), nunca super.render()
    // más de una vez (ver CLAUDE.md / la skill add-gui-screen: esto causó una regresión real
    // en MasterScreen). ──

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG_TEX, left, top, 0, 0, BG_W, BG_H, BG_W, BG_H);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        PanelText.centeredOnDark(g, this.font, this.title, left + BG_W / 2, top + PADDING,
                ZenkaiPalette.TRANSMISSION_ACCENT);

        switch (mode) {
            case REALMS -> renderRealms(g, mouseX, mouseY);
            case DESTINATIONS -> renderDestinations(g, mouseX, mouseY);
            case GENERIC_DESTINATIONS -> renderGenericDestinations(g, mouseX, mouseY);
            case PARTY_MEMBERS -> renderPartyMembers(g, mouseX, mouseY);
        }
    }

    // ── Nivel 1: planetas/dimensiones ───────────────────────────────────────

    private void renderRealms(GuiGraphics g, int mouseX, int mouseY) {
        List<RealmRow> rows = realmRows();
        LockedTooltip tooltip = null;

        // Recorte a la ventana visible — con scroll de verdad hace falta, o una fila a medio
        // salir se pintaría encima del marco superior/inferior del diálogo (mismo criterio que
        // MasterScreen.renderSkillsList).
        g.enableScissor(left, rowsTop(), left + BG_W, rowsTop() + visibleRows() * ROW_H);
        for (int i = 0; i < rows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);

            LockedTooltip rowTooltip = switch (rows.get(i)) {
                case CuratedRow(TeleportRealm realm) -> paintCuratedRow(g, y, realm, mouseX, mouseY);
                case GenericRow(ResourceLocation dim) -> paintGenericRow(g, y, dim, mouseX, mouseY);
                case PartyRow ignored -> paintPartyRow(g, y, mouseX, mouseY);
            };
            if (rowTooltip != null) tooltip = rowTooltip;

            if (i < rows.size() - 1) {
                g.fill(contentLeft() - 2, y + ROW_H - 1, contentRight(), y + ROW_H, ZenkaiPalette.SEPARATOR_DARK);
            }
        }
        g.disableScissor();
        drawScrollbar(g);

        if (tooltip != null) {
            Component tip = tooltip.args() == null
                    ? Component.translatable(tooltip.key())
                    : Component.translatable(tooltip.key(), tooltip.args());
            g.renderTooltip(this.font, this.font.split(tip, TOOLTIP_W), mouseX, mouseY);
        }
    }

    /** Realm CURADO (Overworld/Otherworld) — SIEMPRE disponible: solo aparece en la lista si ya
     *  tiene al menos un destino real (Home siempre cuenta para Overworld). */
    private LockedTooltip paintCuratedRow(GuiGraphics g, int y, TeleportRealm realm, int mouseX, int mouseY) {
        boolean hovered = rowHovered(y, mouseX, mouseY);
        if (hovered) g.fill(contentLeft() - 2, y, contentRight(), y + ROW_H - 1, ZenkaiPalette.HOVER_VEIL);

        int iconY = y + (ROW_H - ICON_DRAW) / 2;
        drawIcon(g, contentLeft(), iconY, IconUV.grid(realm.iconColumn(), 0));

        int textX = contentLeft() + ICON_DRAW + ROW_ICON_GAP;
        int textY = y + (ROW_H - 9) / 2;
        PanelText.onDark(g, this.font, Component.translatable(realm.nameKey()), textX, textY, ZenkaiPalette.TEXT);
        return null;
    }

    /** Dimensión GENÉRICA (Nether/End/mod de terceros). La mayoría solo tienen un destino
     *  implícito ("tu última llegada ahí") y esta fila las teletransporta directo al pulsarlas;
     *  una dimensión con 2+ entradas en GenericDimensionDestinations (hoy, el End) abre en
     *  cambio el submenú de nivel 2 (ver clickRealms/renderGenericDestinations) — MISMO criterio
     *  visual que un realm curado, solo que resuelto por ResourceLocation en vez de enum.
     *  Bloqueada solo por nivel de salto entre dimensiones (crossDimensionOk), nunca por "no
     *  descubierta" (ya solo aparece aquí si fue visitada, ver visibleGenericDimensions). */
    private LockedTooltip paintGenericRow(GuiGraphics g, int y, ResourceLocation dim, int mouseX, int mouseY) {
        boolean enabled = crossDimensionOk(dim);
        boolean hovered = rowHovered(y, mouseX, mouseY);
        if (hovered && enabled) g.fill(contentLeft() - 2, y, contentRight(), y + ROW_H - 1, ZenkaiPalette.HOVER_VEIL);

        int iconY = y + (ROW_H - ICON_DRAW) / 2;
        drawIcon(g, contentLeft(), iconY, genericDimIcon(dim), !enabled);

        int textX = contentLeft() + ICON_DRAW + ROW_ICON_GAP;
        int textY = y + (ROW_H - 9) / 2;
        PanelText.onDark(g, this.font, genericDimName(dim), textX, textY,
                enabled ? ZenkaiPalette.TEXT : ZenkaiPalette.TEXT_OFF);

        if (!enabled && hovered) {
            return new LockedTooltip("screen.zenkai.instant_transmission.locked.level",
                    new Object[] { SkillEffects.crossDimensionMinLevel() });
        }
        return null;
    }

    /** Fila "Party" (nivel 8+, ver SkillEffects.instantTransmissionPartyUnlocked) — SIEMPRE
     *  visible (bloqueada con el motivo que corresponda en vez de desaparecer) y SIEMPRE la
     *  última fila del menú: "existe una opción de Party" es información útil aunque todavía no
     *  se pueda usar. */
    private LockedTooltip paintPartyRow(GuiGraphics g, int y, int mouseX, int mouseY) {
        PartyRowLock partyLock = partyRowLock();
        boolean enabled = partyLock == PartyRowLock.AVAILABLE;
        boolean hovered = rowHovered(y, mouseX, mouseY);
        if (hovered && enabled) g.fill(contentLeft() - 2, y, contentRight(), y + ROW_H - 1, ZenkaiPalette.HOVER_VEIL);

        int iconY = y + (ROW_H - ICON_DRAW) / 2;
        drawIcon(g, contentLeft(), iconY, SHARED_ICONS_TEX, ICON_PARTY, !enabled);

        int textX = contentLeft() + ICON_DRAW + ROW_ICON_GAP;
        int textY = y + (ROW_H - 9) / 2;
        PanelText.onDark(g, this.font, Component.translatable("screen.zenkai.instant_transmission.realm.party"),
                textX, textY, enabled ? ZenkaiPalette.TEXT : ZenkaiPalette.TEXT_OFF);

        if (!enabled && hovered) {
            return switch (partyLock) {
                case LEVEL_LOCKED -> new LockedTooltip("screen.zenkai.instant_transmission.locked.level",
                        new Object[] { SkillEffects.partyMinLevel() });
                case NO_PARTY -> new LockedTooltip("screen.zenkai.party.empty", null);
                default -> null;
            };
        }
        return null;
    }

    /** ¿Puede el jugador entrar al modo Party ahora mismo? */
    private enum PartyRowLock { AVAILABLE, LEVEL_LOCKED, NO_PARTY }

    private PartyRowLock partyRowLock() {
        assert this.minecraft != null;
        if (!SkillEffects.instantTransmissionPartyUnlocked(this.minecraft.player)) return PartyRowLock.LEVEL_LOCKED;
        if (ClientPartyState.current() == null) return PartyRowLock.NO_PARTY;
        return PartyRowLock.AVAILABLE;
    }

    // ── Nivel 2: destinos dentro del planeta CURADO elegido ─────────────────

    private void renderDestinations(GuiGraphics g, int mouseX, int mouseY) {
        renderBackRow(g, mouseX, mouseY);

        if (destRows.isEmpty()) {
            PanelText.onDark(g, this.font, Component.translatable("screen.zenkai.master.nothing"),
                    contentLeft(), rowsTop(), ZenkaiPalette.TEXT_OFF);
            return;
        }

        Component hoveredTooltip = null;
        g.enableScissor(left, rowsTop(), left + BG_W, rowsTop() + visibleRows() * ROW_H);
        for (int i = 0; i < destRows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            TeleportDestination dest = destRows.get(i);
            LockState lock = lockStateOf(dest);
            boolean hovered = rowHovered(y, mouseX, mouseY);

            if (hovered && lock == LockState.AVAILABLE) {
                g.fill(contentLeft() - 2, y, contentRight(), y + ROW_H - 1, ZenkaiPalette.HOVER_VEIL);
            }

            int iconY = y + (ROW_H - ICON_DRAW) / 2;
            drawIcon(g, contentLeft(), iconY, destIcon(dest), lock != LockState.AVAILABLE);

            int textX = contentLeft() + ICON_DRAW + ROW_ICON_GAP;
            int textY = y + (ROW_H - 9) / 2;
            int color = lock == LockState.AVAILABLE
                    ? (hovered ? ZenkaiPalette.TEXT_HOVER : ZenkaiPalette.OK)
                    : ZenkaiPalette.DENIED;
            PanelText.onDark(g, this.font, Component.translatable(dest.nameKey()), textX, textY, color);

            if (lock != LockState.AVAILABLE && hovered) {
                hoveredTooltip = lock == LockState.LEVEL_LOCKED
                        ? Component.translatable(lock.tooltipKey(), SkillEffects.crossDimensionMinLevel())
                        : Component.translatable(lock.tooltipKey());
            }
            if (i < destRows.size() - 1) {
                g.fill(contentLeft() - 2, y + ROW_H - 1, contentRight(), y + ROW_H, ZenkaiPalette.SEPARATOR_DARK);
            }
        }
        g.disableScissor();
        drawScrollbar(g);

        if (hoveredTooltip != null) {
            g.renderTooltip(this.font, this.font.split(hoveredTooltip, TOOLTIP_W), mouseX, mouseY);
        }
    }

    // ── Nivel 2 (variante genérica): sub-destinos de una dimensión GENÉRICA con 2+ entradas ──

    /** Nombre legible de un sub-destino: clave propia si existe
     *  ("screen.zenkai.instant_transmission.subdest.<path de la dimensión>.<id>"), o el id
     *  humanizado como fallback — mismo criterio que genericDimName para no exigir una clave de
     *  idioma por cada combinación dimensión×sub-destino que un mod de terceros pudiera definir
     *  en el futuro (hoy solo el End del propio juego usa este mecanismo, así que sí tiene clave). */
    private static Component subDestName(ResourceLocation dim, GenericSubDestination sub) {
        String key = "screen.zenkai.instant_transmission.subdest." + dim.getPath() + "." + sub.id();
        if (I18n.exists(key)) return Component.translatable(key);
        return Component.literal(humanize(sub.id()));
    }

    /** Igual que lockStateOf (destinos curados), pero para un sub-destino GENÉRICO: un
     *  {@link GenericSubDestination.Waypoint} sin waypoint grabado todavía (nunca se usó un End
     *  Gateway de verdad, ver EndOuterIslandTracker) se bloquea como UNDISCOVERED — antes esta
     *  fila se pintaba disponible con solo entrar a la dimensión, sin haber descubierto nada
     *  todavía (bug real reportado por el usuario). Fixed/LastEntry no necesitan este chequeo
     *  extra: están disponibles en cuanto la dimensión misma aparece en el menú. */
    private LockState genericLockStateOf(ResourceLocation dim, GenericSubDestination sub) {
        if (sub instanceof GenericSubDestination.Waypoint(String ignored, String key, int ignoredCol, int ignoredRow)
                && !InstantTransmissionClientState.hasWaypoint(key)) {
            return LockState.UNDISCOVERED;
        }
        if (!crossDimensionOk(dim)) return LockState.LEVEL_LOCKED;
        return LockState.AVAILABLE;
    }

    private void renderGenericDestinations(GuiGraphics g, int mouseX, int mouseY) {
        renderBackRow(g, mouseX, mouseY);
        ResourceLocation dim = selectedGenericDim;

        Component hoveredTooltip = null;
        g.enableScissor(left, rowsTop(), left + BG_W, rowsTop() + visibleRows() * ROW_H);
        for (int i = 0; i < genericDestRows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            GenericSubDestination sub = genericDestRows.get(i);
            LockState lock = genericLockStateOf(dim, sub);
            boolean hovered = rowHovered(y, mouseX, mouseY);

            if (hovered && lock == LockState.AVAILABLE) {
                g.fill(contentLeft() - 2, y, contentRight(), y + ROW_H - 1, ZenkaiPalette.HOVER_VEIL);
            }

            int iconY = y + (ROW_H - ICON_DRAW) / 2;
            drawIcon(g, contentLeft(), iconY, IconUV.grid(sub.iconColumn(), sub.iconRow()), lock != LockState.AVAILABLE);

            int textX = contentLeft() + ICON_DRAW + ROW_ICON_GAP;
            int textY = y + (ROW_H - 9) / 2;
            int color = lock == LockState.AVAILABLE
                    ? (hovered ? ZenkaiPalette.TEXT_HOVER : ZenkaiPalette.OK)
                    : ZenkaiPalette.DENIED;
            PanelText.onDark(g, this.font, subDestName(dim, sub), textX, textY, color);

            if (lock != LockState.AVAILABLE && hovered) {
                hoveredTooltip = lock == LockState.LEVEL_LOCKED
                        ? Component.translatable(lock.tooltipKey(), SkillEffects.crossDimensionMinLevel())
                        : Component.translatable(lock.tooltipKey());
            }
            if (i < genericDestRows.size() - 1) {
                g.fill(contentLeft() - 2, y + ROW_H - 1, contentRight(), y + ROW_H, ZenkaiPalette.SEPARATOR_DARK);
            }
        }
        g.disableScissor();
        drawScrollbar(g);

        if (hoveredTooltip != null) {
            g.renderTooltip(this.font, this.font.split(hoveredTooltip, TOOLTIP_W), mouseX, mouseY);
        }
    }

    private void renderBackRow(GuiGraphics g, int mouseX, int mouseY) {
        boolean hovered = backHovered(mouseX, mouseY);
        Component text = Component.literal("‹ ").append(Component.translatable("screen.zenkai.back"));
        PanelText.onDark(g, this.font, text, contentLeft(), listTop() + 1,
                hovered ? ZenkaiPalette.TEXT_HOVER : ZenkaiPalette.TEXT_OFF);
    }

    // ── Nivel 2 (variante Party): compañeros de la party, TP a su posición en vivo ──────────

    private void renderPartyMembers(GuiGraphics g, int mouseX, int mouseY) {
        renderBackRow(g, mouseX, mouseY);

        PartySyncPacket state = ClientPartyState.current();
        List<PartySyncPacket.Member> members = partyMemberRows(state);
        if (members.isEmpty()) {
            // NO es "screen.zenkai.party.empty" (esa dice "no estás en ninguna party", que sería
            // FALSO aquí — partyRowLock() ya garantiza que sí hay una para llegar a este modo).
            // Este mensaje es para el caso real: una party recién creada, con una invitación
            // todavía sin aceptar — el líder es el único miembro hasta que alguien la acepte.
            PanelText.onDark(g, this.font, Component.translatable("screen.zenkai.instant_transmission.party_alone"),
                    contentLeft(), rowsTop(), ZenkaiPalette.TEXT_OFF);
            return;
        }

        Component hoveredTooltip = null;
        g.enableScissor(left, rowsTop(), left + BG_W, rowsTop() + visibleRows() * ROW_H);
        for (int i = 0; i < members.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            PartySyncPacket.Member member = members.get(i);
            boolean online = isOnline(member.id());
            boolean hovered = rowHovered(y, mouseX, mouseY);

            if (hovered && online) {
                g.fill(contentLeft() - 2, y, contentRight(), y + ROW_H - 1, ZenkaiPalette.HOVER_VEIL);
            }

            int iconY = y + (ROW_H - FACE_ICON) / 2;
            if (!online) g.setColor(0.55F, 0.55F, 0.55F, 1.0F);
            PlayerFaceRenderer.draw(g, skinOf(member.id()), contentLeft(), iconY, FACE_ICON);
            if (!online) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

            int textX = contentLeft() + FACE_ICON + ROW_ICON_GAP;
            int textY = y + (ROW_H - 9) / 2;
            boolean isLeader = state != null && member.id().equals(state.leaderId());
            String label = (isLeader ? "★ " : "") + member.name();
            int color = online ? (hovered ? ZenkaiPalette.TEXT_HOVER : ZenkaiPalette.OK) : ZenkaiPalette.DENIED;
            PanelText.onDark(g, this.font, Component.literal(label), textX, textY, color);

            if (!online && hovered) {
                hoveredTooltip = Component.translatable("screen.zenkai.instant_transmission.locked.offline");
            }
            if (i < members.size() - 1) {
                g.fill(contentLeft() - 2, y + ROW_H - 1, contentRight(), y + ROW_H, ZenkaiPalette.SEPARATOR_DARK);
            }
        }
        g.disableScissor();
        drawScrollbar(g);

        if (hoveredTooltip != null) {
            g.renderTooltip(this.font, this.font.split(hoveredTooltip, TOOLTIP_W), mouseX, mouseY);
        }
    }

    /** Compañeros a listar, sin uno mismo — nadie necesita un botón para teletransportarse a
     *  donde ya está. Vacío si {@code state} es null (se salió de la party mientras el menú
     *  seguía abierto: ClientPartyState.current() puede volverse null en caliente). Se calcula
     *  cada vez en vez de cachearse como destRows porque, a diferencia de una lista de destinos
     *  elegida una vez al entrar al modo, la party puede cambiar mientras el menú sigue abierto
     *  (alguien se une/sale) — recalcularlo mantiene la lista siempre fresca sin ningún evento
     *  extra que la invalide. */
    private List<PartySyncPacket.Member> partyMemberRows(PartySyncPacket state) {
        if (state == null || Objects.requireNonNull(this.minecraft).player == null) return List.of();
        List<PartySyncPacket.Member> out = new ArrayList<>();
        for (PartySyncPacket.Member m : state.members()) {
            if (!m.id().equals(this.minecraft.player.getUUID())) out.add(m);
        }
        return out;
    }

    /** PlayerInfo (tab list) cubre a cualquier jugador CONECTADO esté donde esté — a diferencia
     *  de mc.level.getPlayerByUUID, que solo encuentra jugadores en la MISMA dimensión que uno
     *  mismo. Un compañero de party puede estar en cualquier dimensión, así que hace falta esto
     *  (mismo criterio que PartyScreen.skinOf ya documenta). */
    private boolean isOnline(UUID id) {
        assert this.minecraft != null;
        return this.minecraft.getConnection() != null && this.minecraft.getConnection().getPlayerInfo(id) != null;
    }

    private PlayerSkin skinOf(UUID id) {
        assert this.minecraft != null;
        if (this.minecraft.getConnection() != null) {
            PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(id);
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(id);
    }

    // ── Estado de una fila de destino (planetas CURADOS) ────────────────────

    private enum LockState {
        AVAILABLE(null),
        UNDISCOVERED("screen.zenkai.instant_transmission.locked.undiscovered"),
        /** Salto de ida real a otra dimensión sin el nivel 6+ — ver
         *  SkillEffects.instantTransmissionCrossDimensionUnlocked. El tooltip lleva %s (el nivel
         *  exacto), a diferencia de los otros dos: se construye aparte en renderDestinations. */
        LEVEL_LOCKED("screen.zenkai.instant_transmission.locked.level");

        private final String tooltipKey;
        LockState(String tooltipKey) { this.tooltipKey = tooltipKey; }
        String tooltipKey() { return tooltipKey; }
    }

    /** MISMO orden de comprobación que TeleportRequestPacket.handle en servidor: descubierto
     *  primero, luego si esta fase sabe ejecutar ese destino (mismo nivel de cross-dimension que
     *  el servidor calcula, para que la fila nunca se pinte disponible cuando el servidor la
     *  rechazaría). Lee el espejo de cliente (InstantTransmissionClientState) — el attachment
     *  del servidor no llega solo aquí, ver InstantTransmissionMenuStatePacket. */
    private LockState lockStateOf(TeleportDestination dest) {
        boolean discovered = !dest.requiresDiscovery()
                || InstantTransmissionClientState.isDiscovered(dest.id());
        if (!discovered) return LockState.UNDISCOVERED;
        assert this.minecraft != null;
        boolean crossOk = SkillEffects.instantTransmissionCrossDimensionUnlocked(this.minecraft.player);
        assert this.minecraft.level != null;
        if (!dest.executableThisPhase(this.minecraft.level.dimension(), crossOk)) return LockState.LEVEL_LOCKED;
        return LockState.AVAILABLE;
    }

    /** Barra de scroll a la derecha de la lista; oculta si cabe sin desplazar. MISMO patrón que
     *  MasterScreen.drawScrollbar (mismos colores oscuros: la pantalla entera es un diálogo
     *  flotante sobre el mundo, nunca beige de panel). */
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
            if (mode != Mode.REALMS && backHovered((int) mouseX, (int) mouseY)) {
                mode = Mode.REALMS;
                scroll = 0;
                return true;
            }
            boolean handled = switch (mode) {
                case REALMS -> clickRealms(mouseX, mouseY);
                case DESTINATIONS -> clickDestinations(mouseX, mouseY);
                case GENERIC_DESTINATIONS -> clickGenericDestinations(mouseX, mouseY);
                case PARTY_MEMBERS -> clickPartyMembers(mouseX, mouseY);
            };
            if (handled) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll();
        if (max > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean clickRealms(double mouseX, double mouseY) {
        List<RealmRow> rows = realmRows();
        for (int i = 0; i < rows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            if (mouseX < contentLeft() - 2 || mouseX > contentRight()) continue;
            if (mouseY < y || mouseY >= y + ROW_H - 1) continue;

            switch (rows.get(i)) {
                case CuratedRow(TeleportRealm realm) -> {
                    List<TeleportDestination> dests = realm.destinations();
                    if (dests.isEmpty()) return true; // por si acaso; hoy ningún curado llega vacío
                    selectedRealm = realm;
                    destRows = dests;
                    mode = Mode.DESTINATIONS;
                    scroll = 0;
                }
                case GenericRow(ResourceLocation dim) -> {
                    if (!crossDimensionOk(dim)) return true; // bloqueada: absorbe el clic
                    List<GenericSubDestination> subs = GenericDimensionDestinations.of(dim);
                    if (subs.size() >= 2) {
                        selectedGenericDim = dim;
                        genericDestRows = subs;
                        mode = Mode.GENERIC_DESTINATIONS;
                        scroll = 0;
                    } else {
                        PacketDistributor.sendToServer(GenericDimensionTeleportPacket.lastEntry(dim.toString()));
                        onClose();
                    }
                }
                case PartyRow ignored -> {
                    if (partyRowLock() != PartyRowLock.AVAILABLE) return true; // bloqueada: absorbe el clic
                    mode = Mode.PARTY_MEMBERS;
                    scroll = 0;
                }
            }
            return true;
        }
        return false;
    }

    private boolean clickDestinations(double mouseX, double mouseY) {
        for (int i = 0; i < destRows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            if (mouseX < contentLeft() - 2 || mouseX > contentRight()) continue;
            if (mouseY < y || mouseY >= y + ROW_H - 1) continue;

            TeleportDestination dest = destRows.get(i);
            if (lockStateOf(dest) != LockState.AVAILABLE) return true; // bloqueado: absorbe el clic

            PacketDistributor.sendToServer(new TeleportRequestPacket(dest.id()));
            onClose();
            return true;
        }
        return false;
    }

    private boolean clickGenericDestinations(double mouseX, double mouseY) {
        ResourceLocation dim = selectedGenericDim;
        for (int i = 0; i < genericDestRows.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            if (mouseX < contentLeft() - 2 || mouseX > contentRight()) continue;
            if (mouseY < y || mouseY >= y + ROW_H - 1) continue;

            GenericSubDestination sub = genericDestRows.get(i);
            if (genericLockStateOf(dim, sub) != LockState.AVAILABLE) return true; // bloqueado: absorbe el clic

            PacketDistributor.sendToServer(new GenericDimensionTeleportPacket(dim.toString(), sub.id()));
            onClose();
            return true;
        }
        return false;
    }

    private boolean clickPartyMembers(double mouseX, double mouseY) {
        List<PartySyncPacket.Member> members = partyMemberRows(ClientPartyState.current());
        for (int i = 0; i < members.size(); i++) {
            if (!onScreen(i)) continue;
            int y = rowTop(i);
            if (mouseX < contentLeft() - 2 || mouseX > contentRight()) continue;
            if (mouseY < y || mouseY >= y + ROW_H - 1) continue;

            PartySyncPacket.Member member = members.get(i);
            if (!isOnline(member.id())) return true; // desconectado: absorbe el clic

            PacketDistributor.sendToServer(new PartyTeleportRequestPacket(member.id()));
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
