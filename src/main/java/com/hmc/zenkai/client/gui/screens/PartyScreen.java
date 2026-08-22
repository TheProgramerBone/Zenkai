package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.party.ClientPartyState;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.party.PartySyncPacket;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Pestaña PARTY. v1 "grouping básico": SOLO LISTA lo que hay, no muta nada por red.
 * Para invitar abre el chat vanilla precargado con "/zparty invite " (mismo hueco de
 * ChatScreen que usan los mods de chat; el jugador se beneficia del autocompletado de
 * Brigadier de EntityArgument.player()) en vez de duplicar la lógica de invitación en un
 * segundo camino (un EditBox + paquete C2S) cuando el comando ya la cubre entera.
 * Salir/expulsar/disolver/fuego-amigo quedan en los comandos por el mismo motivo —
 * remitidos aquí con una línea de ayuda. Botones dedicados con paquetes C2S son un v2 que
 * no toca PartySyncPacket ni PartyService (ver el plan de este feature).
 * CABEZA + VIDA por fila: la cabeza sale del tab list del cliente (PlayerInfo), que existe
 * para CUALQUIER jugador conectado sin importar la distancia — por eso funciona aunque el
 * compañero esté en otra punta del mapa. La vida NO viaja en PartySyncPacket: se lee del
 * mismo PlayerStatsAttachment que ya sincroniza PlayerLifeCycle a quien tenga a ese jugador
 * cargado como entidad (ver StartTracking/sync en PlayerLifeCycle) — así que solo se ve
 * cuando el compañero está cerca o en la misma dimensión. Fuera de rango se muestra "—" en
 * vez de un número viejo, para no mentir con un dato desactualizado.
 */
public class PartyScreen extends ZenkaiMenuScreen {

    private static final int MARGIN = 15;
    private static final int HEAD_SIZE = 16;
    private static final int ROW_H = 20;
    private static final int LIST_Y = CONTENT_TOP + 40;

    public PartyScreen() { super(Component.translatable(ZenkaiTab.PARTY.titleKey())); }

    @Override protected ZenkaiTab currentTab() { return ZenkaiTab.PARTY; }

    @Override
    protected void initContent() {
        var state = ClientPartyState.current();
        boolean canInvite = state == null
                || (isSelfLeader(state) && state.members().size() < PartyManager.MAX_SIZE);
        if (!canInvite) return;

        int y = panelTop + BG_H - MARGIN - PanelButton.H;
        int x = panelLeft + (BG_W - PanelButton.W) / 2;
        addRenderableWidget(PanelButton.primary(x, y,
                Component.translatable("screen.zenkai.party.invite_button"),
                () -> mc.setScreen(new ChatScreen("/zparty invite "))));
    }

    private boolean isSelfLeader(PartySyncPacket state) {
        return mc.player != null && state.leaderId() != null
                && state.leaderId().equals(mc.player.getUUID());
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
                        state.members().size(), PartyManager.MAX_SIZE),
                cx, panelTop + CONTENT_TOP + 6, ZenkaiPalette.LABEL_ON_PANEL);

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable(state.friendlyFire()
                        ? "screen.zenkai.party.ff.on"
                        : "screen.zenkai.party.ff.off"),
                cx, panelTop + CONTENT_TOP + 18,
                state.friendlyFire() ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.OK);

        boolean selfLeader = isSelfLeader(state);
        int rowLeft = panelLeft + MARGIN;
        int rowRight = panelLeft + BG_W - MARGIN;
        int rowY = panelTop + LIST_Y;
        for (PartySyncPacket.Member member : state.members()) {
            drawMemberRow(g, member, state, rowLeft, rowRight, rowY);
            rowY += ROW_H;
        }

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable(selfLeader
                        ? "screen.zenkai.party.hint.leader"
                        : "screen.zenkai.party.hint.member"),
                cx, panelTop + BG_H - MARGIN - 34, ZenkaiPalette.MUTED_ON_PANEL);
    }

    private void drawMemberRow(GuiGraphics g, PartySyncPacket.Member member, PartySyncPacket state,
                               int rowLeft, int rowRight, int rowY) {
        boolean isLeader = member.id().equals(state.leaderId());
        boolean isSelf = mc.player != null && member.id().equals(mc.player.getUUID());
        int nameColor = isSelf ? ZenkaiPalette.VALUE_ON_PANEL : ZenkaiPalette.LABEL_ON_PANEL;

        PlayerFaceRenderer.draw(g, skinOf(member.id()), rowLeft, rowY, HEAD_SIZE);

        int textY = rowY + (HEAD_SIZE - this.font.lineHeight) / 2;
        String tag = isLeader ? "★ " : "";
        PanelText.onPanel(g, this.font, Component.literal(tag + member.name()),
                rowLeft + HEAD_SIZE + 6, textY, nameColor);

        PanelText.rightOnPanel(g, this.font, healthOf(member.id()), rowRight, textY,
                ZenkaiPalette.MUTED_ON_PANEL);
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

    /** "5.8K/5.8K" si el compañero está cargado como entidad (cerca, o al menos en la misma
     *  dimensión); si no, un guion — mentir con el último valor visto sería peor que no
     *  mostrar nada. */
    private Component healthOf(java.util.UUID id) {
        Player p = mc.level == null ? null : mc.level.getPlayerByUUID(id);
        if (p == null) return Component.literal("—");
        var att = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        return Component.literal(
                ZenkaiNumbers.format(att.getBody()) + "/" + ZenkaiNumbers.format(att.getBodyMax()));
    }
}
