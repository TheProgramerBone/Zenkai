package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.core.network.feature.wishes.ConfirmVillagerWishPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

public class EnchantVillagerWishScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");

    private static final int BG_W = 256, BG_H = 256;
    private static final int ARROW_W = 12;
    private static final int COLOR_TITLE = 0x4A3726, COLOR_VALUE = 0x2A1F14, COLOR_SUB = 0x5A4636;

    private final Screen parent;
    private List<Holder.Reference<Enchantment>> enchants = List.of();
    private int index = 0;
    private int panelLeft, panelTop;

    public EnchantVillagerWishScreen(Screen parent) {
        super(Component.translatable("screen.zenkai.wish.enchant_villager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.panelLeft = (this.width  - BG_W) / 2;
        this.panelTop  = (this.height - BG_H) / 2;

        // Cargar lista de encantamientos del registro (cliente).
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            var reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            this.enchants = reg.listElements()
                    .sorted(Comparator.comparing(h -> h.key().location().toString()))
                    .toList();
        }
        if (index >= enchants.size()) index = 0;

        int cx = panelLeft + BG_W / 2;
        int valueY = panelTop + 70;
        int arrowY = valueY - 1;

        // Flechas ← valor →
        addRenderableWidget(new ArrowIconButton(cx - 90, arrowY, ArrowIconButton.Dir.LEFT,
                () -> { if (!enchants.isEmpty()) index = (index - 1 + enchants.size()) % enchants.size(); }));
        addRenderableWidget(new ArrowIconButton(cx + 90 - ARROW_W, arrowY, ArrowIconButton.Dir.RIGHT,
                () -> { if (!enchants.isEmpty()) index = (index + 1) % enchants.size(); }));

        // Confirmar / Volver
        addRenderableWidget(new TextOnlyButton(cx - 60, panelTop + 150, 120, 16,
                Component.translatable("screen.zenkai.gui.confirm"), this::confirm)
                .textColors(COLOR_TITLE, 0x8A6A1E, 0xA0A0A0));
        addRenderableWidget(new TextOnlyButton(cx - 60, panelTop + 172, 120, 16,
                Component.translatable("screen.zenkai.gui.back"), this::onClose)
                .textColors(COLOR_TITLE, 0x8A6A1E, 0xA0A0A0));
    }

    private void confirm() {
        if (enchants.isEmpty()) { onClose(); return; }
        ResourceLocation id = enchants.get(index).key().location();
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new ConfirmVillagerWishPayload(id));
        onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, panelLeft, panelTop, 0, 0, BG_W, BG_H);

        int cx = panelLeft + BG_W / 2;
        drawCentered(g, this.title, cx, panelTop + 22, COLOR_TITLE);

        if (enchants.isEmpty()) {
            drawCentered(g, Component.translatable("screen.zenkai.no_enchantments"), cx, panelTop + 70, COLOR_VALUE);
        } else {
            Holder.Reference<Enchantment> h = enchants.get(index);
            drawCentered(g, h.value().description(), cx, panelTop + 70, COLOR_VALUE);
            drawCentered(g, Component.translatable("screen.zenkai.enchant.max_level", h.value().getMaxLevel()),
                    cx, panelTop + 86, COLOR_SUB);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}

    private void drawCentered(GuiGraphics g, Component text, int cx, int y, int color) {
        g.drawString(this.font, text, cx - this.font.width(text) / 2, y, color, false);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}