package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.party.ClientPartyState;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.party.PartySyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Pestaña PARTY. v1 "grouping básico": SOLO LISTA lo que hay, no muta nada por red.
 * Para invitar abre el chat vanilla precargado con "/zparty invite " (mismo hueco de
 * ChatScreen que usan los mods de chat; el jugador se beneficia del autocompletado de
 * Brigadier de EntityArgument.player()) en vez de duplicar la lógica de invitación en un
 * segundo camino (un EditBox + paquete C2S) cuando el comando ya la cubre entera.
 * Salir/expulsar/disolver quedan en los comandos por el mismo motivo — remitidos aquí con
 * una línea de ayuda. Botones dedicados con paquetes C2S son un v2 que no toca
 * PartySyncPacket ni PartyService (ver el plan de este feature).
 */
public class PartyScreen extends ZenkaiMenuScreen {

    private static final int MARGIN = 15;
    private static final int ROW_H = 16;
    private static final int LIST_Y = CONTENT_TOP + 34;

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

        boolean selfLeader = isSelfLeader(state);
        int rowY = panelTop + LIST_Y;
        for (PartySyncPacket.Member member : state.members()) {
            boolean isLeader = member.id().equals(state.leaderId());
            boolean isSelf = mc.player != null && member.id().equals(mc.player.getUUID());

            String tag = isLeader ? "★ " : "– "; // ★ / –
            int color = isSelf ? ZenkaiPalette.VALUE_ON_PANEL : ZenkaiPalette.LABEL_ON_PANEL;
            PanelText.centeredOnPanel(g, this.font, Component.literal(tag + member.name()),
                    cx, rowY, color);
            rowY += ROW_H;
        }

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable(selfLeader
                        ? "screen.zenkai.party.hint.leader"
                        : "screen.zenkai.party.hint.member"),
                cx, panelTop + BG_H - MARGIN - 34, ZenkaiPalette.MUTED_ON_PANEL);
    }
}
