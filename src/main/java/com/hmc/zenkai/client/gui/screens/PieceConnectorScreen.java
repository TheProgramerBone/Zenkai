package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.network.SavePieceConnectorPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/**
 * Editor de un {@code piece_connector} — a la vez sirve de recordatorio en juego de cómo
 * funciona el motor de piezas (ver {@code .claude/docs/piece-graph.md} y la sección 5 de
 * {@code .claude/tutoriales/tutoriales.md}): el texto de arriba explica el mecanismo de
 * "palillo con color que engancha con otro palillo del mismo color" cada vez que se abre, no
 * solo la primera vez que se lee la documentación. El botón "?" junto al título amplía eso con
 * un ejemplo concreto de dos piezas encajando (por tooltip, sin otra pantalla).
 *
 * Girar la cara NO manda un packet aparte al pulsar el botón — solo cambia
 * {@link #currentFacing} en el cliente y su blockstate real se aplica junto con el socket en
 * un único {@link SavePieceConnectorPayload} al pulsar Guardar (un solo viaje de ida y vuelta).
 *
 * Todas las posiciones Y se calculan UNA SOLA VEZ en init() y se guardan en campos — render()
 * solo las lee, nunca repite la misma cuenta con otra fórmula que pueda desincronizarse.
 *
 * Bare Screen sin panel de fondo (mismo estilo que NpcMarkerScreen) — es una herramienta de
 * desarrollador, no una pantalla de jugador, así que no usa la familia de texturas
 * common_screen.png/ZenkaiPalette.
 */
public class PieceConnectorScreen extends Screen {

    private static final int EXPLAIN_WIDTH = 240;
    private static final int ROW_GAP = 8;

    private final BlockPos pos;
    private final String initSocket;
    private Direction currentFacing;

    private EditBox socketBox;
    private List<FormattedCharSequence> explainLines;

    private int titleY;
    private int explainY;
    private int socketLabelY;

    public PieceConnectorScreen(BlockPos pos, String socket, String facing) {
        super(Component.translatable("screen.zenkai.piece_connector"));
        this.pos = pos;
        this.initSocket = socket;
        Direction parsed = Direction.byName(facing);
        this.currentFacing = parsed != null ? parsed : Direction.NORTH;
    }

    @Override
    protected void init() {
        explainLines = this.font.split(
                Component.translatable("gui.zenkai.piece_connector.explain"), EXPLAIN_WIDTH);

        int contentHeight = 12 + 4 + explainLines.size() * 10   // título + explicación
                + ROW_GAP + 20                                   // botón "Girar cara"
                + ROW_GAP + 10 + 20                               // etiqueta "Socket:" + caja
                + ROW_GAP + 20;                                   // botones Guardar/Cancelar

        int cx = this.width / 2;
        titleY = this.height / 2 - contentHeight / 2;
        explainY = titleY + 12 + 4;

        int y = explainY + explainLines.size() * 10 + ROW_GAP;

        Button rotateButton = Button.builder(facingLabel(), b -> {
            currentFacing = next(currentFacing);
            b.setMessage(facingLabel());
        }).bounds(cx - 100, y, 200, 20).build();
        rotateButton.setTooltip(Tooltip.create(Component.translatable("gui.zenkai.piece_connector.rotate.tooltip")));
        addRenderableWidget(rotateButton);
        y += 20 + ROW_GAP;

        socketLabelY = y;
        y += 10;

        socketBox = new EditBox(this.font, cx - 100, y, 200, 20, Component.empty());
        socketBox.setMaxLength(256);
        socketBox.setValue(initSocket);
        addRenderableWidget(socketBox);
        y += 20 + ROW_GAP;

        addRenderableWidget(Button.builder(Component.translatable("gui.zenkai.save"),
                b -> save()).bounds(cx - 100, y, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                b -> onClose()).bounds(cx + 2, y, 98, 20).build());

        // Botón de ayuda: junto al título, un "?" cuyo tooltip trae un ejemplo concreto de dos
        // piezas encajando — no hace falta otra pantalla para explicarlo.
        Button help = Button.builder(Component.literal("?"), b -> {})
                .bounds(cx + 108, titleY - 2, 16, 16).build();
        help.setTooltip(Tooltip.create(Component.translatable("gui.zenkai.piece_connector.example")));
        addRenderableWidget(help);

        setInitialFocus(socketBox);
    }

    private Component facingLabel() {
        return Component.translatable("gui.zenkai.piece_connector.facing",
                currentFacing.getSerializedName().toUpperCase(Locale.ROOT));
    }

    private static Direction next(Direction d) {
        Direction[] all = Direction.values();
        return all[(d.ordinal() + 1) % all.length];
    }

    private void save() {
        PacketDistributor.sendToServer(new SavePieceConnectorPayload(
                pos, socketBox.getValue().trim(), currentFacing.getSerializedName()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);

        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, titleY, 0xFFFFFF);

        int y = explainY;
        for (FormattedCharSequence line : explainLines) {
            g.drawCenteredString(this.font, line, cx, y, 0xA0A0A0);
            y += 10;
        }

        g.drawString(this.font, Component.translatable("gui.zenkai.piece_connector.socket"),
                cx - 100, socketLabelY, 0xA0A0A0, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
