package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.network.SaveNpcMarkerPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class NpcMarkerScreen extends Screen {

    private final BlockPos pos;
    private final String initType;
    private final float initYaw;
    private final double initX, initY, initZ;
    private final int initRespawnSeconds;

    private EditBox typeBox, yawBox, xBox, yBox, zBox, respawnBox;

    public NpcMarkerScreen(BlockPos pos, String type, float yaw, double x, double y, double z, int respawnSeconds) {
        super(Component.translatable("screen.zenkai.npc_marker"));
        this.pos = pos; this.initType = type; this.initYaw = yaw;
        this.initX = x; this.initY = y; this.initZ = z;
        this.initRespawnSeconds = respawnSeconds;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 78;

        typeBox = add(cx - 100, top + 12, 200, initType);
        typeBox.setMaxLength(128);

        yawBox = add(cx - 100, top + 44, 200, Float.toString(initYaw));

        xBox = add(cx - 100, top + 76, 62, fmt(initX));
        yBox = add(cx - 31,  top + 76, 62, fmt(initY));
        zBox = add(cx + 38,  top + 76, 62, fmt(initZ));

        // Vacío = el valor por defecto de siempre (el campo enseña el hint). Con valor propio, segundos.
        respawnBox = add(cx - 100, top + 108, 200, initRespawnSeconds < 0 ? "" : Integer.toString(initRespawnSeconds));
        respawnBox.setHint(Component.translatable("gui.zenkai.npc_respawn.hint"));

        addRenderableWidget(Button.builder(Component.translatable("gui.zenkai.apply_respawn"),
                b -> send(true)).bounds(cx - 100, top + 136, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.zenkai.save_only"),
                b -> send(false)).bounds(cx - 100, top + 160, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                b -> onClose()).bounds(cx + 2, top + 160, 98, 20).build());
    }

    private EditBox add(int x, int y, int w, String value) {
        EditBox box = new EditBox(this.font, x, y, w, 20, Component.empty());
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private static String fmt(double d) { return String.format(java.util.Locale.ROOT, "%.2f", d); }

    private static double parseD(EditBox box) {
        try { return Double.parseDouble(box.getValue().trim()); } catch (NumberFormatException e) { return 0.0D; }
    }

    private void send(boolean respawn) {
        float yaw;
        try { yaw = Float.parseFloat(yawBox.getValue().trim()); } catch (NumberFormatException e) { yaw = 0.0F; }

        // Vacío o ilegible = -1 = el valor por defecto (el servidor lo recorta al tope).
        int respawnSeconds;
        try { respawnSeconds = Integer.parseInt(respawnBox.getValue().trim()); } catch (NumberFormatException e) { respawnSeconds = -1; }

        PacketDistributor.sendToServer(new SaveNpcMarkerPayload(
                pos, typeBox.getValue().trim(), yaw,
                parseD(xBox), parseD(yBox), parseD(zBox), respawnSeconds, respawn));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int cx = this.width / 2;
        int top = this.height / 2 - 78;
        g.drawCenteredString(this.font, this.title, cx, top - 16, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("gui.zenkai.npc_type"), cx - 100, top + 2, 0xA0A0A0, false);
        g.drawString(this.font, Component.translatable("gui.zenkai.yaw"), cx - 100, top + 34, 0xA0A0A0, false);
        g.drawString(this.font, Component.translatable("gui.zenkai.offset"), cx - 100, top + 66, 0xA0A0A0, false);
        g.drawString(this.font, Component.translatable("gui.zenkai.npc_respawn"), cx - 100, top + 98, 0xA0A0A0, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}