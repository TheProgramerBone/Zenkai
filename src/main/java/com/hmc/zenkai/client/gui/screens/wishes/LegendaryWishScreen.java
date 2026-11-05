package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.screens.ShenronPanelScreen;
import com.hmc.zenkai.client.gui.screens.WishIcons;
import com.hmc.zenkai.feature.wishes.WishLegendaryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Deseo de ser Legendario (todas las razas; el texto cambia según la raza). Confirmación pura, con la misma estructura que ImmortalWishScreen: lo que
 * concede y, aparte y en rojo, lo que cuesta, porque es irreversible.
 */
public class LegendaryWishScreen extends ShenronPanelScreen {

    public LegendaryWishScreen(Screen parent) {
        super(Component.translatable("screen.zenkai.wish.legendary"), parent);
    }


    @Override protected net.minecraft.world.item.ItemStack wishIcon() { return WishIcons.legendary(); }

    @Override
    protected void initContent() { /* solo el pie de página estándar */ }

    @Override
    protected void onConfirm() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new WishLegendaryPayload());
        onClose();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int y = drawWishHeader(g, panelTop + CONTENT_TOP - 2);
        // El Saiyan conserva sus textos de siempre; las demás razas tienen su descripción y un aviso común.
        var race = Minecraft.getInstance().player == null ? null
                : com.hmc.zenkai.feature.player.PlayerStatsAttachment.get(Minecraft.getInstance().player).getRace();
        boolean saiyan = race == null || race == com.hmc.zenkai.feature.Race.SAIYAN;
        String descKey = saiyan ? "screen.zenkai.wish.legendary.desc"
                : "screen.zenkai.wish.legendary.desc." + race.name().toLowerCase(java.util.Locale.ROOT);
        y = drawWrappedOnPanel(g, Component.translatable(descKey), y, ZenkaiPalette.SHENRON_TEXT);
        // Huecos de 5 px (no 10): el pie de los deseos subió sobre la banda de esferas y este es el texto más largo.
        y += 5;
        drawDivider(g, y);
        y += 6;
        drawWrappedOnPanel(g, Component.translatable(saiyan ? "screen.zenkai.wish.legendary.warning"
                : "screen.zenkai.wish.legendary.warning.other"), y, ZenkaiPalette.SHENRON_WARN);
    }
}
