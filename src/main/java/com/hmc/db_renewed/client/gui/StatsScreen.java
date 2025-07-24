package com.hmc.db_renewed.client.gui;

import com.hmc.db_renewed.api.PlayerStatData;
import com.hmc.db_renewed.common.capability.ModCapabilities;
import com.hmc.db_renewed.common.race.ModRaces;
import com.hmc.db_renewed.common.stats.StatCalculator;
import com.hmc.db_renewed.common.style.ModCombatStyles;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.opengl.GL11;

import java.util.Map;

public class StatsScreen extends Screen {

    private static final ResourceLocation STATS_BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("db_renewed", "textures/gui/stats_background.png");

    private final Player player;
    private PlayerStatData data;

    private final int bgWidth = 256;
    private final int bgHeight = 256;

    public StatsScreen(Player player) {
        super(Component.literal("Stats"));
        this.player = player;
    }

    @Override
    protected void init() {
        this.data = player.getCapability(ModCapabilities.PLAYER_STATS, null);
        if (data == null) {
            this.onClose();
            return;
        }

        int left = (this.width - bgWidth) / 2;
        int top = (this.height - bgHeight) / 2;

        Map<String, Integer> finalStats = StatCalculator.calculateFinalStats(data);

        int yStart = top + 100;
        int statIndex = 0;

        for (String stat : finalStats.keySet()) {
            int value = finalStats.get(stat);
            int cost = data.getCostToIncrease(stat);

            int statY = yStart + statIndex * 22;

            // Botón "+"
            this.addRenderableWidget(Button.builder(
                    Component.literal("+"),
                    btn -> {
                        if (data.investTPIntoStat(stat)) {
                            Minecraft.getInstance().setScreen(new StatsScreen(player)); // Recarga para actualizar valores
                        }
                    }).bounds(left + 200, statY, 20, 20).build()
            );

            statIndex++;
        }

        // Botón cerrar
        this.addRenderableWidget(Button.builder(
                Component.literal("Close"),
                btn -> this.onClose()
        ).bounds(left + 90, top + bgHeight - 30, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        int left = (this.width - bgWidth) / 2;
        int top = (this.height - bgHeight) / 2;

        // Fondo opaco
        guiGraphics.fill(0, 0, this.width, this.height, 0xAA000000);

        // Textura personalizada de fondo
        guiGraphics.blit(STATS_BACKGROUND_TEXTURE, left, top, 0, 0, bgWidth, bgHeight,
                bgWidth, bgHeight);

        RenderSystem.setShaderTexture(0, STATS_BACKGROUND_TEXTURE);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        // Datos de jugador
        if (data != null) {
            String race = TextUtils.formatName(data.getRaceId());
            String style = TextUtils.formatName(data.getCombatStyleId());

            guiGraphics.drawCenteredString(this.font, "Stats", this.width / 2, top + 10, 0xFFFFFF);
            guiGraphics.drawString(this.font, "TP: " + data.getTotalTP(), left + 12, top + 30, 0x00FF00);
            guiGraphics.drawString(this.font, "Level: 1", left + 180, top + 30, 0xFFAA00);

            guiGraphics.drawString(this.font, "Race: " + race, left + 12, top + 50, 0xAAAAFF);
            guiGraphics.drawString(this.font, "Style: " + style, left + 12, top + 64, 0xAAAAFF);

            // Render del jugador
            renderPlayer(guiGraphics, left + bgWidth / 2, top + 95, 40, mouseX, mouseY);

            // Stats
            Map<String, Integer> finalStats = StatCalculator.calculateFinalStats(data);
            int yStart = top + 100;
            int statIndex = 0;

            for (String stat : finalStats.keySet()) {
                int value = finalStats.get(stat);
                int cost = data.getCostToIncrease(stat);

                int statY = yStart + statIndex * 22;

                Component label = Component.literal(stat.toUpperCase() + ": " + value + "  (+" + cost + " TP)");
                guiGraphics.drawString(this.font, label, left + 20, statY + 6, 0xFFFFFF);

                // Tooltips
                if (mouseX >= left + 20 && mouseX <= left + 180 && mouseY >= statY && mouseY <= statY + 20) {
                    double raceMod = ModRaces.getModifier(data.getRaceId(), stat);
                    double styleMod = ModCombatStyles.getModifier(data.getCombatStyleId(), stat);

                    guiGraphics.renderTooltip(this.font,
                            Component.literal("Multipliers: Race x" + raceMod + ", Style x" + styleMod),
                            mouseX, mouseY);
                }

                statIndex++;
            }
        }
    }

    private void renderPlayer(GuiGraphics graphics, int x, int y, int scale, int mouseX, int mouseY) {
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                x - 25, y - 50,
                x + 25, y + 50,
                scale,
                0.0F,
                (float) mouseX, (float) mouseY,
                this.player
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static class TextUtils {
        public static String formatName(String id) {
            if (id == null || id.isEmpty()) return "Unknown";
            String[] parts = id.split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    sb.append(Character.toUpperCase(part.charAt(0)))
                            .append(part.substring(1).toLowerCase())
                            .append(" ");
                }
            }
            return sb.toString().trim();
        }
    }

}
