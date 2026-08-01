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

    private EditBox typeBox, yawBox, xBox, yBox, zBox;

    public NpcMarkerScreen(BlockPos pos, String type, float yaw, double x, double y, double z) {
        super(Component.translatable("screen.zenkai.npc_marker"));
        this.pos = pos; this.initType = type; this.initYaw = yaw;
        this.initX = x; this.initY = y; this.initZ = z;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 62;

        typeBox = add(cx - 100, top + 12, 200, initType);
        typeBox.setMaxLength(128);

        yawBox = add(cx - 100, top + 44, 200, Float.toString(initYaw));

        xBox = add(cx - 100, top + 76, 62, fmt(initX));
        yBox = add(cx - 31,  top + 76, 62, fmt(initY));
        zBox = add(cx + 38,  top + 76, 62, fmt(initZ));

        addRenderableWidget(Button.builder(Component.translatable("gui.zenkai.apply_respawn"),
                b -> send(true)).bounds(cx - 100, top + 104, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.zenkai.save_only"),
                b -> send(false)).bounds(cx - 100, top + 128, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                b -> onClose()).bounds(cx + 2, top + 128, 98, 20).build());
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

        PacketDistributor.sendToServer(new SaveNpcMarkerPayload(
                pos, typeBox.getValue().trim(), yaw,
                parseD(xBox), parseD(yBox), parseD(zBox), respawn));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int cx = this.width / 2;
        int top = this.height / 2 - 62;
        g.drawCenteredString(this.font, this.title, cx, top - 16, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("gui.zenkai.npc_type"), cx - 100, top + 2, 0xA0A0A0, false);
        g.drawString(this.font, Component.translatable("gui.zenkai.yaw"), cx - 100, top + 34, 0xA0A0A0, false);
        g.drawString(this.font, Component.translatable("gui.zenkai.offset"), cx - 100, top + 66, 0xA0A0A0, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}