package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.wishes.WishRevivePetPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista las mascotas muertas del jugador (sincronizadas en PlayerStatsAttachment),
 * con render en vivo de cada una, y al confirmar envía WishRevivePetPayload(index).
 */
public class RevivePetWishScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    private static final int BG_W = 256, BG_H = 256;
    private static final int COLOR_TITLE = 0x4A3726, TXT_NORMAL = 0xFFFFFF,
            TXT_HOVER = 0xFFF149, TXT_INACTIVE = 0xA0A0A0;

    private final Screen parent;
    /** Entidades reconstruidas en cliente solo para render. */
    private final List<LivingEntity> pets = new ArrayList<>();
    private int index = 0;
    private int panelLeft, panelTop;

    public RevivePetWishScreen(Screen parent) {
        super(Component.translatable("screen.zenkai.wish.revive_pet"));
        this.parent = parent;
    }
    @Override
    protected void init() {
        this.clearWidgets();
        this.panelLeft = (this.width - BG_W) / 2;
        this.panelTop  = (this.height - BG_H) / 2;
        rebuildPets();

        int cx = panelLeft + BG_W / 2;

        addRenderableWidget(new ArrowIconButton(cx - 80, panelTop + 120, ArrowIconButton.Dir.LEFT, () -> cycle(-1)));
        addRenderableWidget(new ArrowIconButton(cx + 60, panelTop + 120, ArrowIconButton.Dir.RIGHT, () -> cycle(1)));

        addRenderableWidget(new TextOnlyButton(cx - 60, panelTop + 190, 120, 16,
                Component.translatable("screen.zenkai.gui.confirm"), this::confirm)
                .textColors(TXT_NORMAL, TXT_HOVER, TXT_INACTIVE));

        addRenderableWidget(new TextOnlyButton(cx - 60, panelTop + 210, 120, 16,
                Component.translatable("screen.zenkai.gui.back"),
                () -> { if (minecraft != null) minecraft.setScreen(parent); })
                .textColors(TXT_NORMAL, TXT_HOVER, TXT_INACTIVE));
    }

    /** Reconstruye las entidades a partir del NBT sincronizado en el attachment del jugador. */
    private void rebuildPets() {
        pets.clear();
        index = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(mc.player);
        for (CompoundTag tag : stats.getDeadPets()) {
            Entity e = EntityType.loadEntityRecursive(tag, mc.level, ent -> ent);
            if (e instanceof LivingEntity le) {
                le.setHealth(le.getMaxHealth());
                le.hurtTime = 0;
                le.hurtDuration = 0;
                le.deathTime = 0;
                pets.add(le);
            }
        }
    }

    private void cycle(int delta) {
        if (pets.isEmpty()) return;
        index = (index + delta + pets.size()) % pets.size();
    }

    private void confirm() {
        if (pets.isEmpty()) return;
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new WishRevivePetPayload(index));
        if (minecraft != null) minecraft.setScreen(parent); // el server revive y sincroniza
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.renderBackground(g, mouseX, mouseY, pt);
        g.blit(BG, panelLeft, panelTop, 0, 0, BG_W, BG_H);
        drawCentered(g, this.title, panelLeft + BG_W / 2, panelTop + 25, 0x04a500);

        if (pets.isEmpty()) {
            drawCentered(g, Component.translatable("screen.zenkai.wish.no_pets"),
                    panelLeft + BG_W / 2, panelTop + 60, TXT_INACTIVE);
        } else {
            LivingEntity pet = pets.get(index);
            Component name = pet.hasCustomName() ? pet.getCustomName() : pet.getType().getDescription();
            drawCentered(g, name, panelLeft + BG_W / 2, panelTop + 44, 0xFFFFFF);

            int ex = panelLeft + BG_W / 2;
            int ey = panelTop + 150;
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, ex - 30, ey - 60, ex + 30, ey, 30, 0.0625F, (float) mouseX, (float) mouseY, pet);
        }

        super.render(g, mouseX, mouseY, pt);
    }

    @Override public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}

    private void drawCentered(GuiGraphics g, Component t, int cx, int y, int color) {
        g.drawString(this.font, t, cx - this.font.width(t) / 2, y, color, true);
    }

    @Override public boolean isPauseScreen() { return false; }
}