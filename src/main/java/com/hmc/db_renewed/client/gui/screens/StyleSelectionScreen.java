package com.hmc.db_renewed.client.gui.screens;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.client.gui.buttons.ArrowIconButton;
import com.hmc.db_renewed.core.network.ChooseStylePacket;
import com.hmc.db_renewed.core.network.feature.Style;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.stats.UpdatePlayerVisualPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class StyleSelectionScreen extends Screen {

    private static final int BUTTON_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;

    @Nullable private final Screen back;

    // Paleta simple (luego puedes poner picker real)
    private final int[] auraColors = new int[]{
            0x33CCFF, 0x00FF66, 0xFFCC00, 0xFF3366, 0xAA66FF, 0xFFFFFF
    };
    private int auraIndex = 0;

    public StyleSelectionScreen(@Nullable Screen back) {
        super(Component.translatable("screen." + DragonBlockRenewed.MOD_ID + ".choose_style.title"));
        this.back = back;
    }

    @Override
    protected void init() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int centerX = this.width / 2;
        int startY  = this.height / 2 - 40;
        int spacing = 24;

        // Aura picker arriba
        int ax = centerX + 110;
        int ay = startY - 26;

        addRenderableWidget(new ArrowIconButton(ax, ay, ArrowIconButton.Dir.LEFT, () -> {
            auraIndex = (auraIndex - 1 + auraColors.length) % auraColors.length;
            applyAuraAndSync();
        }));
        addRenderableWidget(new ArrowIconButton(ax + 46, ay, ArrowIconButton.Dir.RIGHT, () -> {
            auraIndex = (auraIndex + 1) % auraColors.length;
            applyAuraAndSync();
        }));

        addStyleButton(Style.WARRIOR,        centerX - BUTTON_WIDTH / 2, startY);
        addStyleButton(Style.MARTIAL_ARTIST, centerX - BUTTON_WIDTH / 2, startY + spacing);
        addStyleButton(Style.SPIRITUALIST,   centerX - BUTTON_WIDTH / 2, startY + spacing * 2);

        // Back
        if (back != null) {
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> mc.setScreen(back))
                    .bounds(centerX - 70, startY + spacing * 3 + 12, 60, 20)
                    .build());
        }
    }

    private void applyAuraAndSync() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        visual.setAuraColorRgb(auraColors[auraIndex]);

        // Guardar incluso si no eligió estilo
        PacketDistributor.sendToServer(UpdatePlayerVisualPacket.from(visual));
    }

    private void addStyleButton(Style style, int x, int y) {
        String keyBase = "screen." + DragonBlockRenewed.MOD_ID + ".style." + style.name().toLowerCase();

        Component label   = Component.translatable(keyBase);
        Component tooltip = Component.translatable(keyBase + ".desc");

        Button btn = Button.builder(label, b -> onStyleChosen(style))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(tooltip))
                .build();

        this.addRenderableWidget(btn);
    }

    private void onStyleChosen(Style style) {
        PacketDistributor.sendToServer(new ChooseStylePacket(style));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}