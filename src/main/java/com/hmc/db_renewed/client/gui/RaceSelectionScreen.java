package com.hmc.db_renewed.client.gui;

import com.hmc.db_renewed.common.player.RaceDataHandler;
import com.hmc.db_renewed.common.race.ModRaces;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class RaceSelectionScreen extends Screen {

    private final Player player;
    private ModRaces selectedModRaces = ModRaces.HUMAN;

    public RaceSelectionScreen() {
        super(Component.literal("Select Your Race"));
        this.player = Minecraft.getInstance().player;
    }

    private String formatRaceName(ModRaces modRaces) {
        String[] parts = modRaces.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder("Race: ");
        for (String part : parts) {
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }
        return builder.toString().trim();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int labelOffsetY = 70; // Texto está debajo del modelo
        int buttonOffsetY = 100;

        int labelWidth = 120;
        int arrowWidth = 20;
        int arrowSpacing = 5;

        // Botón Izquierda
        this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
            int index = (selectedModRaces.ordinal() - 1 + ModRaces.values().length) % ModRaces.values().length;
            selectedModRaces = ModRaces.values()[index];
        }).pos(centerX - labelWidth / 2 - arrowWidth - arrowSpacing, centerY + labelOffsetY).size(arrowWidth, 20).build());

        // Botón Derecha
        this.addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
            int index = (selectedModRaces.ordinal() + 1) % ModRaces.values().length;
            selectedModRaces = ModRaces.values()[index];
        }).pos(centerX + labelWidth / 2 + arrowSpacing, centerY + labelOffsetY).size(arrowWidth, 20).build());

        // Botón Confirmar
        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), btn -> {
            RaceDataHandler handler = player.getCapability(RaceDataHandler.CAPABILITY, null);
            player.sendSystemMessage(Component.literal("You have selected the " + formatRaceName(selectedModRaces)));
            this.onClose();
        }).pos(centerX - 50, centerY + buttonOffsetY).size(100, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);

        int centerX = this.width / 2;
        int centerY = this.height / 2 - 60;
        int scale = 50;
        float offsetY = 0.0f;

        float angleX = (float) Math.atan(((float) centerX - mouseX) / 40.0F);
        float angleY = (float) Math.atan(((float) centerY - mouseY) / 40.0F);

        renderEntity(
                graphics,
                centerX,
                centerY+55,
                scale,
                offsetY,
                angleX,
                angleY,
                this.player
        );

        //Texto de Race:Human
        graphics.drawCenteredString(
                this.font,
                formatRaceName(selectedModRaces),
                centerX,
                centerY + 137,
                0xFFFFFF
        );






    }

    private void renderEntity(GuiGraphics graphics, int x, int y, float scale, float yOffset, float angleX, float angleY, Player entity) {
        float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRot = entity.yHeadRot;
        float headRotO = entity.yHeadRotO;

        entity.yBodyRot = 180.0F + angleX * 20.0F;
        entity.setYRot(180.0F + angleX * 40.0F);
        entity.setXRot(-angleY * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(angleY * 20.0F * ((float) Math.PI / 180F));
        pose.mul(camera);

        Vector3f translation = new Vector3f(0.0F, entity.getBbHeight() / 2.0F + yOffset, 0.0F);
        float adjustedScale = scale / entity.getScale();

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 50.0F);
        graphics.pose().scale(adjustedScale, adjustedScale, -adjustedScale);
        graphics.pose().translate(translation.x(), translation.y(), translation.z());
        graphics.pose().mulPose(pose);

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.overrideCameraOrientation(camera.conjugate(new Quaternionf()).rotateY((float) Math.PI));
        dispatcher.setRenderShadow(false);
        dispatcher.render(entity, 0.0, 0, 0.0, 0.0F, 1.0F, graphics.pose(), graphics.bufferSource(), 15728880);
        graphics.flush();
        dispatcher.setRenderShadow(true);
        graphics.pose().popPose();
        Lighting.setupFor3DItems();

        entity.yBodyRot = bodyRot;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRot = headRot;
        entity.yHeadRotO = headRotO;
    }



    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
