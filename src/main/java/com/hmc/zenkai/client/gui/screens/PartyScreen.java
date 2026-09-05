package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.StatBar;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.AtlasIconButton;
import com.hmc.zenkai.client.gui.buttons.ConfirmIconButton;
import com.hmc.zenkai.client.gui.buttons.FriendlyFireIconButton;
import com.hmc.zenkai.client.gui.buttons.MinusIconButton;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.client.party.ClientPartyState;
import com.hmc.zenkai.feature.party.PartySyncPacket;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Pestaña PARTY. v1 "grouping básico": SOLO LISTA lo que hay, no muta nada por red — pero
 * SÍ ejecuta comandos: cada botón (Invitar, Expulsar, Salir, Disolver, Fuego amigo, Config)
 * llama a {@link #runCommand} con el mismo texto que el jugador teclearía en /zparty, no un
 * paquete C2S nuevo. Eso es DELIBERADO y no un atajo: PartyService y PartySyncPacket ya son
 * la única fuente de reglas (validación, mensajes de chat, quién es líder); duplicarlas en un
 * paquete de botón habría significado dos caminos que mantener sincronizados por la misma
 * regla. La única excepción sigue siendo Invitar, que necesita texto libre (el nombre) y por
 * eso abre ChatScreen precargado en vez de mandar el comando directo — Invitar existe TANTO
 * sin party (crea una, ver PartyService.invite) como dentro de una con hueco libre.
 * REFRESCO: los botones (Invitar/Disolver/Fuego amigo/Config del pie y cabecera) dependen de
 * quién es el líder y de quién está en la party — cosas que cambian por un PartySyncPacket
 * que llega SIN que el jugador toque esta pantalla. tick() compara la referencia cacheada en
 * ClientPartyState contra la última vista y llama a rebuildWidgets() si cambió, así que un
 * kick ajeno o una fusión de invitación no dejan un botón huérfano apuntando a un miembro que
 * ya no está (ni una cabeza clicable de alguien que ya se fue: la lista sobre la que itera
 * mouseClicked se relee de ClientPartyState en cada clic, no se cachea).
 * CABEZA + NOMBRE + PL + BODY por fila, dos líneas por miembro (ver drawMemberRow):
 *   línea 1: nombre a la izquierda, PL a la derecha — mismo patrón etiqueta-izq/valor-der que
 *            StatBar.row usa en la línea de abajo, así las dos leen como una sola tabla.
 *   línea 2: la MISMA StatBar que dibuja StatsScreen para Body (mismo marco, canal y
 *            redondeo de "5.8K/5.8K") — no vale la pena una segunda implementación solo
 *            porque aquí vive dentro de una fila en vez de un panel propio.
 * Ninguna de las dos viaja en PartySyncPacket: ambas salen del mismo PlayerStatsAttachment que
 * ya sincroniza PlayerLifeCycle a quien tenga a ese jugador cargado como entidad (cerca, o al
 * menos en la misma dimensión). Fuera de rango se muestra "—" en vez de un número viejo, para
 * no mentir con un dato desactualizado — dibujar una barra o un PL con el último valor visto
 * sería peor que no dibujar nada. El PL es el APARENTE (getApparentPowerLevel, el mismo que
 * lee un scouter), no el real: ocultar tu ki con Ki Control también te oculta de tu propia
 * party, igual que de cualquiera.
 * HEAD_SIZE subió de 16 a 24 y las dos líneas de texto se centran verticalmente contra ese
 * alto (ver topPad en drawMemberRow) — con 16 la cabeza solo alineaba con la línea del nombre
 * y la barra de abajo quedaba "flotando" sin nada a su izquierda, que era la queja original
 * ("se ve raro"). ROW_H creció con ella (32) pero LIST_BOTTOM se movió hacia el pie del panel
 * para que la party por defecto (4) siga cabiendo sin scroll — ver visibleRows().
 * SCROLL de la lista: existe porque el tamaño máximo de una party ya no es una constante de
 * 4 (ver PartyConfig más abajo), puede llegar a 32 y el panel nunca tuvo sitio para eso.
 * Mismo patrón que SkillsScreen (scrollRow + rueda del ratón + una barra de scroll DIBUJADA,
 * sin arrastre) — no hacía falta reinventarlo. El hint de "/zparty chat" solo se dibuja
 * cuando NO hace falta scroll (maxScroll()==0): con la lista llena no hay hueco para él y de
 * cualquier caso el comando sigue funcionando sin el recordatorio visual.
 * PARTYCONFIG (ícono de engranaje, solo líder): abre un popup pequeño y oscuro FUERA del
 * panel beige, a su IZQUIERDA (popupLeft()) — no dentro de su contorno y no centrado. Mismo
 * caso EXACTO que el popup lateral de StatsScreen.renderPopup: mismo hueco (POPUP_GAP),
 * mismo criterio de posición (panelLeft - POPUP_W - POPUP_GAP, clampado a la ventana) y la
 * misma nota de ZenkaiPalette.POPUP_BG (alfa parcial porque el panel opaco de detrás ya cubre
 * el mundo — el popup flota JUNTO al panel principal, no encima ni dentro de él). Usa esos
 * mismos colores oscuros con sombra (PanelText.*OnDark), no los de panel. Pinta
 * su fondo desde renderBackground() (después del panel principal, antes de los widgets — ver
 * la convención de orden de la clase) para que sus propios botones queden ENCIMA de esa caja
 * y no al revés. Dentro: número grande + Minus/PlusIconButton para ajuste fino, una rueda del
 * ratón que mueve el valor un paso por muesca, y una barra horizontal (pista + marcador) que
 * enseña dónde cae ese valor entre el tamaño actual del grupo y el tope del servidor
 * (ServerConfig.partyMaxSizeCeiling(), viaja en el propio PartySyncPacket para no necesitar
 * una ida y vuelta aparte). Confirmar/Cancelar son el MISMO checkmark verde / X roja que
 * FriendlyFireIconButton (celdas CHECK_U/CANCEL_U de icons.png), no btn_x.png — así los cuatro
 * íconos del popup salen del mismo atlas que el resto de la pantalla. Se cierra de TRES formas
 * equivalentes, cada una sin aplicar nada salvo Confirmar: Cancelar, un clic FUERA de la caja
 * (mouseClicked hace hit-test contra popupLeft()/popupTop()) o un segundo clic sobre el propio
 * engranaje (que alterna configOpen, ver initContent — por eso el engranaje sigue visible
 * mientras el popup está abierto, a diferencia de fuego amigo y el pie). Confirmar manda
 * "/zparty maxsize N" — PartyService.setMaxSize vuelve a validar del lado servidor, el
 * cliente nunca decide.
 * BOTONES: NINGUNO es btn_wide.png (el pill naranja/beige de 9-slice) salvo Salir — ese marco
 * es literalmente el mismo naranja/amarillo que ya forma el borde del panel, así que un botón
 * grande CON el mismo marco DENTRO del panel se lee como un segundo contorno compitiendo con
 * el de fuera (la queja original: "se ve chinchoso"). Invitar era la excepción hasta que pasó
 * a ser un ícono también (el sobre de correo, AtlasIconButton) — Salir queda como el único
 * caso pendiente, sin tocar porque nadie lo ha pedido todavía.
 * EXPULSAR no es un botón propio: pasar el ratón sobre la CABEZA de un compañero (solo si eres
 * líder y no es tu propia fila) la oscurece un poco y pinta el ícono de "prohibido" encima —
 * el punto de clic ES la cabeza (ver mouseClicked). Antes era un ícono aparte flotando al borde
 * de la fila; esto ahorra un elemento visual permanente por fila y el gesto "clic en la
 * cabeza para expulsar" no necesita explicación con la cabeza ya oscurecida.
 * Disolver y Fuego amigo siguen siendo íconos chicos sin marco: ConfirmIconButton (papelera,
 * doble pulsación) reusa la paleta naranja/amarilla de btn_x.png; FriendlyFireIconButton,
 * Invitar y Config leen su celda de textures/gui/icons.png (fila v=60 — ver
 * tools/gen_party_icons.py, su única fuente) en vez de texto sobre otro btn_wide.
 */
public class PartyScreen extends ZenkaiMenuScreen {

    private static final ResourceLocation TEX_TRASH =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_trash.png");
    private static final ResourceLocation TEX_TRASH_HL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_trash_highlight.png");
    private static final int DISBAND_SIZE = 16;

    /** Mismo atlas que FriendlyFireIconButton/AtlasIconButton, para el blit manual del ícono
     *  de expulsar sobre la cabeza (ver drawMemberRow) — ese no es un botón, así que no pasa
     *  por ninguna de esas dos clases. Coordenadas: ver tools/gen_party_icons.py. */
    private static final ResourceLocation ICONS_ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ATLAS_W = 256, ATLAS_H = 256;
    private static final int MAIL_U = 40, MAIL_V = 60;
    private static final int KICK_U = 60, KICK_V = 60;
    private static final int CONFIG_U = 100, CONFIG_V = 20;
    /** Mismo checkmark verde / X roja que FriendlyFireIconButton pinta para OFF/ON — reutilizados
     *  aquí como Confirmar/Cancelar del popup de PartyConfig en vez de un ícono de otra familia
     *  (btn_x.png, "flat bevel"): así los cuatro íconos del popup salen del MISMO atlas
     *  textures/gui/icons.png que ya usa el resto de esta pantalla (correo, expulsar, engranaje),
     *  y no dos estilos de pincel distintos en la misma caja. Ambos ámbitos nunca se ven a la vez
     *  (el popup sustituye al ícono de fuego amigo mientras está abierto, ver initContent), así
     *  que no hay ambigüedad de qué significa cada celda en pantalla. */
    private static final int CHECK_U = 0, CHECK_V = 60;
    private static final int CANCEL_U = 20, CANCEL_V = 60;
    private static final int ICON_CELL = 20;

    private static final int MARGIN = 15;
    /** 24, no 16: con la cabeza chica solo alineaba con la línea del nombre y la barra de
     *  Body de abajo quedaba sin nada a su izquierda — la queja original ("se ve raro"). Con
     *  24 caben las DOS líneas (nombre+PL, barra) centradas contra el alto de la cabeza. */
    private static final int HEAD_SIZE = 24;
    private static final int ROW_TEXT_GAP = 2;
    private static final int ROW_H = HEAD_SIZE + 8;
    private static final int LIST_Y = CONTENT_TOP + 40;
    /** Fondo de la lista, pegado al pie salvo un hueco antes de los íconos de acción — así
     *  la party por defecto (4) sigue cabiendo entera sin scroll (ver visibleRows()). */
    private static final int LIST_BOTTOM = BG_H - MARGIN - ICON_CELL - 6;
    private static final int SCROLLBAR_W = 4;
    /** Columna reservada SIEMPRE (haga falta scroll o no) para que el texto de la fila (PL,
     *  "actual/máximo" de la barra) no salte de sitio en cuanto una party cruza el umbral de
     *  necesitar scroll. */
    private static final int SCROLLBAR_GUTTER = SCROLLBAR_W + 4;

    private static final int POPUP_W = 120;
    private static final int POPUP_H = 96;
    /** Mismo hueco que StatsScreen deja entre su popup lateral y el panel (POPUP_GAP) —
     *  ver popupLeft(). */
    private static final int POPUP_GAP = 8;

    public PartyScreen() { super(Component.translatable(ZenkaiTab.PARTY.titleKey())); }

    @Override protected ZenkaiTab currentTab() { return ZenkaiTab.PARTY; }

    /** Última referencia de estado vista, para que tick() detecte un PartySyncPacket nuevo
     *  sin comparar campo a campo — cada paquete es un objeto nuevo (ver PartySyncPacket). */
    private PartySyncPacket lastSeenState;

    /** Desplazamiento (en filas) de la lista de miembros. Se clampa en initContent() cada vez
     *  que se reconstruyen los widgets, así que un kick/leave ajeno que encoge la lista no lo
     *  deja apuntando a filas que ya no existen. */
    private int scrollRow = 0;

    /** ¿Está abierto el popup de PartyConfig? Vive en la instancia, no en un campo estático:
     *  rebuildWidgets() (tick(), cualquier click) recrea el conjunto de widgets desde cero, así
     *  que este flag es lo único que sobrevive de un rebuild al siguiente para saber qué
     *  conjunto de widgets tocaba construir. */
    private boolean configOpen = false;
    /** Valor que el popup está proponiendo, antes de confirmar. Se re-clampa contra
     *  [miembros actuales, tope admin] en cada rebuild por si algo cambió mientras el popup
     *  seguía abierto (alguien se unió, p. ej.). */
    private int pendingMaxSize;

    @Override
    public void tick() {
        super.tick();
        if (ClientPartyState.current() != lastSeenState) {
            // rebuildWidgets() = clearWidgets() + init() + setInitialFocus(): reconstruye
            // el conjunto de widgets (pestañas incluidas) desde cero, igual que un resize de
            // ventana. Para una party de hasta 32 no compensa la complejidad de un camino de
            // actualización parcial.
            this.rebuildWidgets();
        }
    }

    @Override
    protected void initContent() {
        var state = ClientPartyState.current();
        lastSeenState = state;

        if (state == null) {
            // Sin party todavía: el ÚNICO botón posible es Invitar — no hay líder, no hay
            // fuego amigo, no hay nada más que mostrar. PartyService.invite() crea la party
            // sola en cuanto la invitación se manda, así que este botón hace doble función
            // ("empezar una party" Y "invitar") sin que el jugador tenga que saberlo.
            configOpen = false;
            int x = panelLeft + (BG_W - ICON_CELL) / 2;
            int y = panelTop + BG_H / 2 + 14;
            AtlasIconButton invite = new AtlasIconButton(x, y, MAIL_U, MAIL_V,
                    () -> mc.setScreen(new ChatScreen("/zparty invite ")));
            invite.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.party.invite_button")));
            addRenderableWidget(invite);
            return;
        }

        scrollRow = Mth.clamp(scrollRow, 0, maxScroll(state.members().size()));
        boolean selfLeader = isSelfLeader(state);
        int rowRight = panelLeft + BG_W - MARGIN;

        if (selfLeader) {
            // Engranaje de PartyConfig: SIEMPRE presente para el líder (a diferencia de los
            // demás botones de esta pantalla, que se ocultan mientras el popup está abierto)
            // porque es también lo que lo CIERRA — un segundo clic sobre el mismo ícono es el
            // gesto natural de "ya terminé", igual que abrirlo. Clicar fuera del popup hace lo
            // mismo, ver mouseClicked().
            AtlasIconButton config = new AtlasIconButton(
                    panelLeft + MARGIN, panelTop + CONTENT_TOP, CONFIG_U, CONFIG_V,
                    () -> {
                        configOpen = !configOpen;
                        if (configOpen) pendingMaxSize = state.maxSize();
                        this.rebuildWidgets();
                    });
            config.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.party.config_icon.tooltip")));
            addRenderableWidget(config);
        }

        if (configOpen) {
            if (selfLeader) {
                initMaxSizePopup(state);
                return;
            }
            // Salvaguarda: hoy no hay forma de perder el liderazgo sin disolver la party
            // (leave() como líder disuelve, ver PartyService), así que esta rama es
            // inalcanzable en la práctica — pero si algún día hubiera cesión de liderazgo,
            // el popup no debe quedarse abierto para alguien que ya no puede confirmarlo.
            configOpen = false;
        }

        if (selfLeader) {
            // Ícono de fuego amigo: arriba a la derecha, al lateral del texto de cabecera
            // (que sigue centrado) en vez de otro botón ancho abajo. Solo el líder puede
            // tocarlo (PartyService.setFriendlyFire lo exige igual del lado servidor), así
            // que a los demás miembros ni se les añade — el texto de estado de más abajo ya
            // les dice el valor actual, y un ícono sin acción real detrás sobra.
            FriendlyFireIconButton ff = new FriendlyFireIconButton(
                    rowRight - 20, panelTop + CONTENT_TOP,
                    state::friendlyFire,
                    () -> runCommand("zparty friendlyfire " + (state.friendlyFire() ? "off" : "on")));
            ff.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.party.ff_icon.tooltip")));
            addRenderableWidget(ff);
        }

        // Fila del pie: ambos íconos comparten alto (20, el mayor de los dos) para quedar
        // centrados entre sí; el borde inferior sigue a ras del margen, igual que el único
        // botón que había antes aquí.
        int footerY = panelTop + BG_H - MARGIN - ICON_CELL;
        boolean canInvite = state.members().size() < state.maxSize();

        if (selfLeader) {
            ConfirmIconButton disband = new ConfirmIconButton(0, 0, DISBAND_SIZE, TEX_TRASH, TEX_TRASH_HL,
                    Component.translatable("screen.zenkai.party.disband_button.tooltip"),
                    Component.translatable("screen.zenkai.party.disband_confirm.tooltip"),
                    () -> runCommand("zparty disband"));
            if (canInvite) {
                int rowW = ICON_CELL + 8 + DISBAND_SIZE;
                int rowX = panelLeft + (BG_W - rowW) / 2;
                AtlasIconButton invite = new AtlasIconButton(rowX, footerY, MAIL_U, MAIL_V,
                        () -> mc.setScreen(new ChatScreen("/zparty invite ")));
                invite.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.party.invite_button")));
                addRenderableWidget(invite);
                disband.setX(rowX + ICON_CELL + 8);
                disband.setY(footerY + (ICON_CELL - DISBAND_SIZE) / 2);
            } else {
                disband.setX(panelLeft + (BG_W - DISBAND_SIZE) / 2);
                disband.setY(footerY + (ICON_CELL - DISBAND_SIZE) / 2);
            }
            addRenderableWidget(disband);
        } else {
            int x = panelLeft + (BG_W - PanelButton.W) / 2;
            addRenderableWidget(PanelButton.secondary(x, footerY,
                    Component.translatable("screen.zenkai.party.leave_button"),
                    () -> runCommand("zparty leave")));
        }
    }

    /** Construye los widgets del popup de PartyConfig — número + Minus/Plus + Confirmar/
     *  Cancelar. El fondo del popup NO es un widget, se pinta desde renderBackground() (ver
     *  el javadoc de la clase) para quedar DEBAJO de estos botones; el texto/pista tampoco
     *  son widgets, se pintan al final de render() — ver drawMaxSizePopupContent(). */
    private void initMaxSizePopup(PartySyncPacket state) {
        int min = state.members().size();
        int ceiling = Math.max(min, state.maxSizeCeiling());
        pendingMaxSize = Mth.clamp(pendingMaxSize, min, ceiling);

        int cx = popupLeft() + POPUP_W / 2;
        int py = popupTop();

        MinusIconButton minus = new MinusIconButton(cx - 8 - 12 - 6, py + 25,
                () -> {
                    pendingMaxSize = Mth.clamp(pendingMaxSize - 1, min, ceiling);
                    this.rebuildWidgets();
                });
        minus.active = pendingMaxSize > min;
        addRenderableWidget(minus);

        PlusIconButton plus = new PlusIconButton(cx + 8 + 6, py + 25,
                () -> {
                    pendingMaxSize = Mth.clamp(pendingMaxSize + 1, min, ceiling);
                    this.rebuildWidgets();
                });
        plus.active = pendingMaxSize < ceiling;
        addRenderableWidget(plus);

        // Cancelar: la MISMA X roja de FriendlyFireIconButton (ON), no btn_x.png — ver el
        // comentario de CANCEL_U/CHECK_U más arriba.
        AtlasIconButton cancel = new AtlasIconButton(cx - 12 - 16, py + POPUP_H - 26, CANCEL_U, CANCEL_V,
                () -> {
                    configOpen = false;
                    this.rebuildWidgets();
                });
        cancel.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.gui.cancel")));
        addRenderableWidget(cancel);

        AtlasIconButton confirm = new AtlasIconButton(cx + 16, py + POPUP_H - 26, CHECK_U, CHECK_V,
                () -> {
                    runCommand("zparty maxsize " + pendingMaxSize);
                    configOpen = false;
                    this.rebuildWidgets();
                });
        confirm.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.gui.confirm")));
        addRenderableWidget(confirm);
    }

    /** Esquina superior izquierda del popup de PartyConfig. FUERA del panel, a su izquierda —
     *  no dentro de él y no centrado: mismo hueco (POPUP_GAP) y mismo criterio que el popup
     *  lateral de StatsScreen.renderPopup (panelLeft - POPUP_W - POPUP_GAP), que es justo el
     *  caso ya documentado para POPUP_BG ("flota junto al panel PRINCIPAL, que ya es opaco").
     *  Clampado a la ventana (igual que StatsScreen) para que en una ventana angosta no se
     *  salga por el borde izquierdo y quede recortado o inalcanzable.
     *  popupLeft()/popupTop() son la ÚNICA fuente de esta posición — initMaxSizePopup,
     *  drawMaxSizePopupContent, renderBackground() y el hit-test de "clic fuera cierra" en
     *  mouseClicked() la comparten, así que no hay cuatro cálculos que puedan desincronizarse
     *  entre sí. */
    private int popupLeft() {
        return Mth.clamp(panelLeft - POPUP_W - POPUP_GAP, 2, this.width - POPUP_W - 2);
    }

    private int popupTop() { return panelTop + CONTENT_TOP; }

    /** Filas visibles a la vez en la lista, según el hueco real del panel. Como mínimo 1,
     *  para que ROW_H nunca pueda dejar la lista en blanco por un cálculo raro. */
    private int visibleRows() {
        return Math.max(1, (LIST_BOTTOM - LIST_Y) / ROW_H);
    }

    private int maxScroll(int totalMembers) {
        return Math.max(0, totalMembers - visibleRows());
    }

    /** Fila i-ésima de la lista, en coordenadas de pantalla, YA con el scroll aplicado. UNA
     *  sola fuente para initContent (nada la usa hoy, pero drawMemberRow y mouseClicked la
     *  comparten) — dos cálculos por separado son dos sitios que desincronizar en cuanto
     *  cambie ROW_H, LIST_Y o scrollRow. */
    private int rowY(int index) { return panelTop + LIST_Y + (index - scrollRow) * ROW_H; }

    /** ¿La fila index cae dentro del hueco visible ahora mismo? */
    private boolean rowVisible(int index) {
        int rel = index - scrollRow;
        return rel >= 0 && rel < visibleRows();
    }

    private void runCommand(String command) {
        if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
    }

    private boolean isSelfLeader(PartySyncPacket state) {
        return mc.player != null && state.leaderId() != null
                && state.leaderId().equals(mc.player.getUUID());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        var state = ClientPartyState.current();
        if (state == null || scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        if (configOpen) {
            // El popup ocupa la pantalla mientras está abierto: cualquier rueda de ratón
            // ajusta el valor propuesto, sin importar dónde esté el cursor exactamente —
            // no hay nada más scrollable detrás que tenga sentido priorizar.
            int min = state.members().size();
            int ceiling = Math.max(min, state.maxSizeCeiling());
            int next = Mth.clamp(pendingMaxSize + (int) Math.signum(scrollY), min, ceiling);
            if (next != pendingMaxSize) {
                pendingMaxSize = next;
                this.rebuildWidgets();
            }
            return true;
        }

        int max = maxScroll(state.members().size());
        if (max > 0) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);

        int cx = panelLeft + BG_W / 2;
        var state = ClientPartyState.current();

        if (state == null) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.party.empty"),
                    cx, panelTop + BG_H / 2 - 14, ZenkaiPalette.LABEL_ON_PANEL);
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.party.empty.hint"),
                    cx, panelTop + BG_H / 2 - 2, ZenkaiPalette.MUTED_ON_PANEL);
            return;
        }

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.party.header",
                        state.members().size(), state.maxSize()),
                cx, panelTop + CONTENT_TOP + 6, ZenkaiPalette.LABEL_ON_PANEL);

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable(state.friendlyFire()
                        ? "screen.zenkai.party.ff.on"
                        : "screen.zenkai.party.ff.off"),
                cx, panelTop + CONTENT_TOP + 18,
                // OK_ON_PANEL, NO OK: OK es el verde brillante pensado para fondo oscuro (ver
                // la regla de ZenkaiPalette) — sobre el beige del panel se veía "chinchoso".
                // OK_ON_PANEL es el mismo rol pero en el verde bosque apagado de la escala
                // tierra del panel.
                state.friendlyFire() ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.OK_ON_PANEL);

        boolean selfLeader = isSelfLeader(state);
        int rowLeft = panelLeft + MARGIN;
        int rowRight = panelLeft + BG_W - MARGIN - SCROLLBAR_GUTTER;
        int listTop = panelTop + LIST_Y;
        int listBottom = panelTop + LIST_BOTTOM;

        // Recorta la lista a su hueco: con scroll, la última fila visible puede quedar
        // partida a media altura y sin esto se saldría por encima del pie de la pantalla.
        g.enableScissor(panelLeft, listTop, panelLeft + BG_W, listBottom);
        // Se difiere a DESPUÉS del bucle: GuiGraphics.renderTooltip pinta encima lo
        // que se dibuje a continuación, así que llamarlo fila a fila haría que el tooltip de
        // una fila de arriba quedara tapado por la cabeza de la fila de abajo.
        Component kickTooltip = null;
        for (int i = 0; i < state.members().size(); i++) {
            if (!rowVisible(i)) continue;
            PartySyncPacket.Member member = state.members().get(i);
            boolean kickable = selfLeader
                    && !(mc.player != null && member.id().equals(mc.player.getUUID()));
            Component t = drawMemberRow(g, member, state, rowLeft, rowRight, rowY(i),
                    kickable, mouseX, mouseY);
            if (t != null) kickTooltip = t;
        }
        g.disableScissor();
        if (kickTooltip != null) g.renderTooltip(this.font, kickTooltip, mouseX, mouseY);

        drawListScrollbar(g, state.members().size());

        if (maxScroll(state.members().size()) == 0) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.party.hint.chat"),
                    cx, panelTop + 184, ZenkaiPalette.MUTED_ON_PANEL);
        }

        if (configOpen && selfLeader) drawMaxSizePopupContent(g, state);
    }

    private void drawListScrollbar(GuiGraphics g, int total) {
        int max = maxScroll(total);
        if (max <= 0) return;

        int x = panelLeft + BG_W - MARGIN - SCROLLBAR_W;
        int top = panelTop + LIST_Y;
        int h = visibleRows() * ROW_H;
        g.fill(x, top, x + SCROLLBAR_W, top + h, ZenkaiPalette.BAR_BG);

        int thumbH = Math.max(12, h * visibleRows() / total);
        int thumbY = top + (h - thumbH) * scrollRow / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.VALUE_ON_PANEL);
    }

    /** Devuelve el tooltip de "Expulsar a X" si el ratón está sobre la cabeza de esta fila
     *  (y es expulsable), o null. El llamador lo renderiza al final, ver render(). */
    @org.jetbrains.annotations.Nullable
    private Component drawMemberRow(GuiGraphics g, PartySyncPacket.Member member, PartySyncPacket state,
                                    int rowLeft, int rowRight, int rowY,
                                    boolean kickable, int mouseX, int mouseY) {
        boolean isLeader = member.id().equals(state.leaderId());
        boolean isSelf = mc.player != null && member.id().equals(mc.player.getUUID());
        int nameColor = isSelf ? ZenkaiPalette.VALUE_ON_PANEL : ZenkaiPalette.LABEL_ON_PANEL;

        PlayerFaceRenderer.draw(g, skinOf(member.id()), rowLeft, rowY, HEAD_SIZE);

        Component tooltip = null;
        boolean hoveringHead = kickable
                && mouseX >= rowLeft && mouseX < rowLeft + HEAD_SIZE
                && mouseY >= rowY && mouseY < rowY + HEAD_SIZE;
        if (hoveringHead) {
            // Oscurece la cabeza "un poco" (no un velo opaco: sigue reconociéndose de quién
            // es) para que el ícono de "prohibido" destaque encima — el punto de clic ES la
            // cabeza, ver mouseClicked(). Reemplaza al botón aparte que había al borde de
            // la fila.
            g.fill(rowLeft, rowY, rowLeft + HEAD_SIZE, rowY + HEAD_SIZE, 0x99000000);
            int off = (HEAD_SIZE - ICON_CELL) / 2;
            g.blit(ICONS_ATLAS, rowLeft + off, rowY + off, KICK_U, KICK_V,
                    ICON_CELL, ICON_CELL, ATLAS_W, ATLAS_H);
            tooltip = Component.translatable("screen.zenkai.party.kick_button.tooltip", member.name());
        }

        // Las dos líneas (nombre+PL, barra de Body) se centran contra el alto de la cabeza —
        // con HEAD_SIZE=24 y lineHeight=9 sobra hueco a los dos lados, así que no hace falta
        // que las líneas toquen el borde superior de la cabeza como pasaba con 16.
        int contentH = this.font.lineHeight * 2 + ROW_TEXT_GAP;
        int topPad = Math.max(0, (HEAD_SIZE - contentH) / 2);
        int nameY = rowY + topPad;
        int barY = nameY + this.font.lineHeight + ROW_TEXT_GAP;

        int barX = rowLeft + HEAD_SIZE + 6;
        String tag = isLeader ? "★ " : "";
        PanelText.onPanel(g, this.font, Component.literal(tag + member.name()),
                barX, nameY, nameColor);

        Player p = mc.level == null ? null : mc.level.getPlayerByUUID(member.id());
        if (p != null) {
            var att = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            // PL APARENTE, no el real: esconder el ki con Ki Control también te oculta de tu
            // propia party — mismo dato que vería un scouter.
            PanelText.rightOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.party.pl_format",
                            ZenkaiNumbers.format(att.getApparentPowerLevel())),
                    rowRight, nameY, ZenkaiPalette.VALUE_ON_PANEL);
            // Misma StatBar.row que StatsScreen usa para Body — sin etiqueta (el nombre ya
            // va en la línea de arriba) pero con marco, canal y "actual/máximo" idénticos.
            StatBar.row(g, this.font, barX, barX, rowRight, barY, Component.empty(),
                    att.getBody(), att.getBodyMax(), ZenkaiPalette.BAR_BODY);
        } else {
            PanelText.rightOnPanel(g, this.font, Component.literal("—"), rowRight, barY,
                    ZenkaiPalette.MUTED_ON_PANEL);
        }
        return tooltip;
    }

    private void drawMaxSizePopupContent(GuiGraphics g, PartySyncPacket state) {
        int x0 = popupLeft();
        int py = popupTop();
        int cx = x0 + POPUP_W / 2;

        PanelText.centeredOnDark(g, this.font,
                Component.translatable("screen.zenkai.party.config.title"),
                cx, py + 10, ZenkaiPalette.GOLD);

        PanelText.centeredOnDark(g, this.font, Component.literal(String.valueOf(pendingMaxSize)),
                cx, py + 27, ZenkaiPalette.VALUE);

        int min = state.members().size();
        int ceiling = Math.max(min, state.maxSizeCeiling());
        int trackX0 = x0 + 14;
        int trackX1 = x0 + POPUP_W - 14;
        int trackY = py + 44;
        // "Barra de scroll" del picker: no se arrastra (igual que la de la lista, ver
        // drawListScrollbar/SkillsScreen), pero enseña de un vistazo dónde cae el valor
        // propuesto entre el tamaño actual del grupo y el tope del servidor.
        g.fill(trackX0, trackY, trackX1, trackY + 4, ZenkaiPalette.BAR_BG_DARK);
        float ratio = ceiling <= min ? 0f : (float) (pendingMaxSize - min) / (ceiling - min);
        int thumbX = trackX0 + Math.round((trackX1 - trackX0 - 4) * ratio);
        g.fill(thumbX, trackY - 1, thumbX + 4, trackY + 5, ZenkaiPalette.GOLD);

        PanelText.centeredOnDark(g, this.font,
                Component.literal(min + "–" + ceiling), cx, py + 52, ZenkaiPalette.TEXT_DIM);
    }

    /** Pinta el fondo del popup ANTES de que super.render() dibuje los widgets (ver la
     *  convención de orden de render de la clase) — así los botones de Minus/Plus/Confirmar/
     *  Cancelar quedan ENCIMA de esta caja y no al revés. Mismo caso de uso que el popup
     *  lateral de Stats: alfa parcial porque el panel opaco de detrás ya cubre el mundo (ver
     *  ZenkaiPalette.POPUP_BG), así que NO es el DIALOG_BG opaco de una pantalla flotante
     *  sin nada sólido detrás. */
    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        if (!configOpen) return;

        int x0 = popupLeft();
        int y0 = popupTop();
        // Marco de tres anillos, igual que el popup lateral de StatsScreen: misma familia
        // visual que el panel principal aunque este popup viva fuera de su contorno.
        g.fill(x0 - 2, y0 - 2, x0 + POPUP_W + 2, y0 + POPUP_H + 2, ZenkaiPalette.BORDER_IN);
        g.fill(x0 - 1, y0 - 1, x0 + POPUP_W + 1, y0 + POPUP_H + 1, ZenkaiPalette.BORDER_MID);
        g.fill(x0, y0, x0 + POPUP_W, y0 + POPUP_H, ZenkaiPalette.POPUP_BG);
    }

    /** El clic de expulsar vive aquí y no en un widget: el punto de clic es la cabeza del
     *  compañero (ver drawMemberRow), no un botón con su propio hueco en el layout. Primero
     *  se deja actuar a los widgets normales (botones del pie, pestañas) — las cabezas nunca
     *  se solapan con ellos, así que el orden no cambia nada salvo dejar que el caso normal
     *  se resuelva por el camino normal. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        if (configOpen) {
            // super.mouseClicked ya dejó actuar a los botones del PROPIO popup (Minus/Plus/
            // Confirmar/Cancelar) y a la pestaña/engranaje que lo cerraría por su cuenta —
            // si llegamos aquí, el clic no cayó en ninguno de ellos. Fuera de la caja del
            // popup, eso significa "clic fuera", el gesto estándar para cerrar un modal sin
            // aplicar nada; dentro de la caja pero sin widget bajo el cursor (p. ej. sobre el
            // número o la pista) no hace nada — no hay cabezas que expulsar mientras está
            // abierto, ver el javadoc de la clase.
            int x0 = popupLeft(), y0 = popupTop();
            boolean inside = mouseX >= x0 && mouseX < x0 + POPUP_W
                    && mouseY >= y0 && mouseY < y0 + POPUP_H;
            if (!inside) {
                configOpen = false;
                this.rebuildWidgets();
                return true;
            }
            return false;
        }

        var state = ClientPartyState.current();
        if (state == null || !isSelfLeader(state)) return false;

        int rowLeft = panelLeft + MARGIN;
        for (int i = 0; i < state.members().size(); i++) {
            if (!rowVisible(i)) continue;
            PartySyncPacket.Member member = state.members().get(i);
            if (mc.player != null && member.id().equals(mc.player.getUUID())) continue;
            int rowY = rowY(i);
            if (mouseX >= rowLeft && mouseX < rowLeft + HEAD_SIZE
                    && mouseY >= rowY && mouseY < rowY + HEAD_SIZE) {
                runCommand("zparty kick " + member.name());
                return true;
            }
        }
        return false;
    }

    /** PlayerInfo cubre a CUALQUIER jugador conectado (tab list), no solo a los cercanos —
     *  ya trae su propio respaldo a DefaultPlayerSkin mientras la textura real carga. Solo
     *  hace falta un respaldo aparte cuando el jugador está DESCONECTADO y no hay entrada. */
    private PlayerSkin skinOf(java.util.UUID id) {
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(id);
    }
}
