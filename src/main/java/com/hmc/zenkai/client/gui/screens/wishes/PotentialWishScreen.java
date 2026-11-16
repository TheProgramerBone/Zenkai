package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.screens.ShenronPanelScreen;
import com.hmc.zenkai.client.gui.screens.WishIcons;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.wishes.WishPotentialPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Deseo de despertar el potencial: Ki Control al máximo para cualquier raza y, para el Namekiano, además su primera
 * transformación. Confirmación pura (como {@link LegendaryWishScreen}): lo que concede y, aparte, el aviso de que es
 * permanente. El texto del Namekiano añade su extra.
 */
public class PotentialWishScreen extends ShenronPanelScreen {

    public PotentialWishScreen(Screen parent) {
        super(Component.translatable("screen.zenkai.wish.potential"), parent);
    }

    @Override protected net.minecraft.world.item.ItemStack wishIcon() { return WishIcons.potential(); }

    @Override
    protected void initContent() { /* solo el pie de página estándar */ }

    @Override
    protected void onConfirm() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new WishPotentialPayload());
        onClose();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int y = drawWishHeader(g, panelTop + CONTENT_TOP - 2);
        boolean namek = Minecraft.getInstance().player != null
                && PlayerStatsAttachment.get(Minecraft.getInstance().player).getRace() == Race.NAMEKIAN;
        y = drawWrappedOnPanel(g, Component.translatable("screen.zenkai.wish.potential.desc"), y, ZenkaiPalette.SHENRON_TEXT);
        if (namek) {
            y += 4;
            y = drawWrappedOnPanel(g, Component.translatable("screen.zenkai.wish.potential.desc.namekian"), y, ZenkaiPalette.SHENRON_TEXT);
        }
        y += 5;
        drawDivider(g, y);
        y += 6;
        drawWrappedOnPanel(g, Component.translatable("screen.zenkai.wish.potential.warning"), y, ZenkaiPalette.SHENRON_WARN);
    }
}
