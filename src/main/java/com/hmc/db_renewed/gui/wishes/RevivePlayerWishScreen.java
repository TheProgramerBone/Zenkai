package com.hmc.db_renewed.gui.wishes;

import com.hmc.db_renewed.network.wishes.WishRevivePlayerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class RevivePlayerWishScreen extends Screen {
    private final Screen parent;
    private EditBox nameBox;

    public RevivePlayerWishScreen(Screen parent) {
        super(Component.translatable("screen.db_renewed.wish.revive_player"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.nameBox = new EditBox(this.font, cx - 80, cy - 10, 160, 20, Component.literal("PlayerName"));
        this.nameBox.setMaxLength(32);
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(Button.builder(Component.translatable("screen.db_renewed.gui.confirm"), b -> {
            String target = nameBox.getValue().trim();
            if (!target.isEmpty()) {
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) conn.send(new WishRevivePlayerPayload(target)); // <-- envia nombre
            }
            this.onClose();
        }).bounds(cx - 60, cy + 20, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.db_renewed.gui.back"), b -> this.onClose())
                .bounds(cx - 60, cy + 44, 120, 20).build());
    }

    @Override public void onClose() { this.minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(this.font, this.title, this.width/2, 20, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("screen.db_renewed.wish.revive_player.desc"),
                this.width/2, this.height/2 - 30, 0xAAAAAA);
    }
}
