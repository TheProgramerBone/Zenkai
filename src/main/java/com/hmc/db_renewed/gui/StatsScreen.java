package com.hmc.db_renewed.gui;

import com.hmc.db_renewed.config.StatsConfig;
import com.hmc.db_renewed.network.stats.Dbrattributes;
import com.hmc.db_renewed.network.stats.DataAttachments;
import com.hmc.db_renewed.network.stats.PlayerStatsAttachment;
import com.hmc.db_renewed.network.stats.SpendTpPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class StatsScreen extends Screen {
    private static final int PAD = 8;
    private final Minecraft mc = Minecraft.getInstance();
    private PlayerStatsAttachment att;

    private static final List<Dbrattributes> ORDER = List.of(
            Dbrattributes.STRENGTH, Dbrattributes.DEXTERITY, Dbrattributes.CONSTITUTION,
            Dbrattributes.WILLPOWER, Dbrattributes.MIND, Dbrattributes.SPIRIT
    );

    public StatsScreen() {
        super(Component.literal("Your Stats"));
    }

    @Override
    protected void init() {
        if (mc.player != null) {
            att = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        }
        this.clearWidgets();
        int x = PAD + 10;
        int y = PAD + 30;

        for (Dbrattributes a : ORDER) {
            final String name = a.name();
            addRenderableWidget(Button.builder(Component.literal("+1"),
                    b -> spend(name, 1)).bounds(x + 140, y + 32, 28, 18).build());
            addRenderableWidget(Button.builder(Component.literal("+10"),
                    b -> spend(name, 10)).bounds(x + 172, y + 32, 34, 18).build());
            y += 18;
        }

        addRenderableWidget(Button.builder(Component.literal("Close"),
                b -> onClose()).bounds(width - 80 - PAD, height - 24 - PAD, 80, 20).build());
    }

    private void spend(String attrName, int points) {
        PacketDistributor.sendToServer(new SpendTpPacket(attrName, points));
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        int left = PAD;
        int top = PAD;

        g.drawString(font, this.title, left, top, 0xFFFFFF);

        if (mc.player == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        att = mc.player.getData(DataAttachments.PLAYER_STATS.get());

        int panelW = Math.min(325, width - PAD*8);
        int panelH = Math.min(145, height - PAD*8 - 30);
        g.fill(left, top + 12, left + panelW, top + 36 + panelH, 0xAA000000);

        int x = left + 8;
        int y = top + 20;

        // TP y general
        g.drawString(font, Component.literal("Race: ").append(Component.literal(att.getRace().name()).withStyle(ChatFormatting.AQUA)), x, y, 0xFFFFFF);
        y += 10;
        g.drawString(font, Component.literal("Style: ").append(Component.literal(att.getStyle().name()).withStyle(ChatFormatting.AQUA)), x, y, 0xFFFFFF);
        y += 10;
        g.drawString(font, Component.literal("TP: ").append(Component.literal(String.valueOf(att.getTP())).withStyle(ChatFormatting.GOLD)), x, y, 0xFFFFFF);
        y += 12;

        // Encabezados
        g.drawString(font, Component.literal("Attributes"), x, y, 0xFFD0D0);
        g.drawString(font, Component.literal("Stats"), x + 220, y, 0xFFD0D0);
        y += 10;

        // Atributos con valores y botones (ya añadidos en init)
        int ay = y;
        for (Dbrattributes a : ORDER) {
            String line = a.name() + ": " + att.getAttribute(a);
            g.drawString(font, line, x, ay+5, 0xFFFFFF);
            ay += 18;
        }

        // Estadísticas derivadas (columna derecha)
        int sx = x + 220;
        int sy = y;
        var melee   = att.computeMeleeFinal();
        var defense = att.computeDefenseFinal();
        var speed   = att.computeSpeedFinal();
        var fly     = att.computeFlyFinal();
        var body    = att.getBody() + "/" + att.getBodyMax();
        var stam    = att.getStamina() + "/" + att.getStaminaMax();
        var ki      = att.getEnergy() + "/" + att.getEnergyMax();

        List<String> stats = new ArrayList<>();
        stats.add(String.format("Melee: %.1f", melee));
        stats.add(String.format("Defense: %.1f", defense));
        stats.add(String.format("Body: %s", body));
        stats.add(String.format("Stamina: %s", stam));
        stats.add(String.format("Ki: %s", ki));
        double moveMult = Math.min(1.0 + (speed/100.0)* StatsConfig.movementScaling(), StatsConfig.speedMultiplierCap());
        double flyMult  = Math.min(1.0 + (fly/100.0)* StatsConfig.flyScaling(),      StatsConfig.flyMultiplierCap());
        stats.add(String.format("Running: %d%%", (int)Math.round(moveMult*100)));
        stats.add(String.format("Flying: %d%%",  (int)Math.round(flyMult*100)));

        for (String s : stats) {
            g.drawString(font, s, sx, sy, 0xFFFFFF);
            sy += 10;
        }
        g.drawString(font, Component.literal("Work in Progress").withStyle(ChatFormatting.GRAY),
                left, height - 12 - PAD, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}