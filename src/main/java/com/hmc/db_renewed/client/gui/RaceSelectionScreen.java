package com.hmc.db_renewed.client.gui;

import com.hmc.db_renewed.common.capability.ModCapabilities;
import com.hmc.db_renewed.common.race.ModRaces;
import com.hmc.db_renewed.common.style.ModCombatStyles;
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
import com.hmc.db_renewed.api.PlayerStatData;

import java.util.List;

public class RaceSelectionScreen extends Screen {

    private final Player player;
    private int currentBodyType = 0;
    private int currentHairType = 0;

    private final int maxBodyType = 4;
    private final int maxHairType = 4;

    private final List<String> raceIds = ModRaces.getAllRaceIds();
    private final List<String> styleIds = ModCombatStyles.getAllStyleIds();

    private int currentRaceIndex = 0;
    private int currentStyleIndex = 0;

    private Button confirmButton;

    private float mouseX;
    private float mouseY;

    public static String formatId(String id) {
        String[] parts = id.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                builder.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1))
                        .append(" ");
            }
        }
        return builder.toString().trim();
    }

    public RaceSelectionScreen() {
        super(Component.literal("Character Selection"));
        this.player = Minecraft.getInstance().player;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int arrowWidth = 20;
        int labelWidth = 100;
        int spacing = 5;

        // === BODY TYPE ===
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            currentBodyType = (currentBodyType - 1 + maxBodyType + 1) % (maxBodyType + 1);
            updateCapability();
        }).pos(centerX - 100, centerY + 20).size(20, 20).build());

        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            currentBodyType = (currentBodyType + 1) % (maxBodyType + 1);
            updateCapability();
        }).pos(centerX + 80, centerY + 20).size(20, 20).build());

        // === HAIR TYPE ===
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            currentHairType = (currentHairType - 1 + maxHairType + 1) % (maxHairType + 1);
            updateCapability();
        }).pos(centerX - 100, centerY + 45).size(20, 20).build());

        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            currentHairType = (currentHairType + 1) % (maxHairType + 1);
            updateCapability();
        }).pos(centerX + 80, centerY + 45).size(20, 20).build());

        // === RAZA a la derecha ===
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            currentRaceIndex = (currentRaceIndex - 1 + raceIds.size()) % raceIds.size();
            updateCapability();
        }).pos(centerX + 80, centerY - 10).size(arrowWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            currentRaceIndex = (currentRaceIndex + 1) % raceIds.size();
            updateCapability();
        }).pos(centerX + 80 + labelWidth + spacing, centerY - 10).size(arrowWidth, 20).build());

        // === ESTILO a la izquierda ===
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            currentStyleIndex = (currentStyleIndex - 1 + styleIds.size()) % styleIds.size();
            updateCapability();
        }).pos(centerX - 80 - labelWidth - spacing, centerY - 10).size(arrowWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            currentStyleIndex = (currentStyleIndex + 1) % styleIds.size();
            updateCapability();
        }).pos(centerX - 80, centerY - 10).size(arrowWidth, 20).build());

        // === CONFIRMAR ===
        confirmButton = Button.builder(Component.literal("Confirm"), b -> {
            player.sendSystemMessage(Component.literal("Character created!"));
            this.onClose();
        }).pos(centerX - 50, centerY + 90).size(100, 20).build();

        confirmButton.active = false;
        addRenderableWidget(confirmButton);

        updateCapability(); // Carga inicial
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);

        this.mouseX = (float) mouseX;
        this.mouseY = (float) mouseY;

        int centerX = this.width / 2;
        int centerY = this.height / 2 - 60;

        float angleX = (float) Math.atan((centerX - mouseX) / 40.0F);
        float angleY = (float) Math.atan((centerY - mouseY) / 40.0F);

        renderEntity(graphics, centerX, centerY + 55, 50, 0.0f, angleX, angleY, player);

        String selectedRaceId = raceIds.get(currentRaceIndex);
        String selectedStyleId = styleIds.get(currentStyleIndex);

        graphics.drawCenteredString(this.font,
                "Race: " + formatId(selectedRaceId),
                centerX + 145, centerY + 100, 0xFFFFFF);

        graphics.drawCenteredString(this.font,
                "Style: " + formatId(selectedStyleId),
                centerX - 125, centerY + 100, 0xFFFFFF);

        graphics.drawCenteredString(this.font,
                "Body Type: " + currentBodyType,
                centerX, centerY + 105, 0xFFFFFF);

        graphics.drawCenteredString(this.font,
                "Hair Type: " + currentHairType,
                centerX, centerY + 120, 0xFFFFFF);
    }

    private void updateCapability() {
        String raceId = raceIds.get(currentRaceIndex);
        String styleId = styleIds.get(currentStyleIndex);

        PlayerStatData data = player.getCapability(ModCapabilities.PLAYER_STATS);
        if (data != null) {
            data.setRaceId(raceId);
            data.setCombatStyleId(styleId);
            data.setbodyType(currentBodyType);
            data.sethairType(currentHairType);
        }
        confirmButton.active = raceId != null && styleId != null;
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
