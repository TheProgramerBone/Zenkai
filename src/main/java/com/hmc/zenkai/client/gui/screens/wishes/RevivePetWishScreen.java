package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.screens.ZenkaiPanelScreen;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.wishes.WishRevivePetPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Deseo de revivir una mascota. Carrusel con render en vivo de cada una.
 * Cambios: pie de página estándar (antes Confirmar/Volver eran texto suelto a media altura),
 * indicador "n de N" para que se sepa cuántas hay sin girar el carrusel entero, Confirmar
 * apagado cuando no hay ninguna (antes se podía pulsar y no pasaba nada), y las flechas se
 * ocultan si hay una sola mascota.
 * El marco del retrato es lo que le faltaba visualmente: la entidad se dibujaba flotando sobre
 * el beige, y con la marca de agua 悟 detrás no se leía como un objeto seleccionable.
 */
public class RevivePetWishScreen extends ZenkaiPanelScreen {

    private static final int FRAME_W = 96, FRAME_H = 96;

    /** Entidades reconstruidas SOLO para render. Nunca se añaden al mundo. */
    private final List<LivingEntity> pets = new ArrayList<>();
    private int index = 0;

    private ArrowIconButton leftArrow, rightArrow;

    public RevivePetWishScreen(Screen parent) {
        super(Component.translatable("screen.zenkai.wish.revive_pet"), parent);
    }

    @Override protected int titleColor() { return ZenkaiPalette.SHENLONG; }

    @Override
    protected void initContent() {
        rebuildPets();

        int frameY = panelTop + CONTENT_TOP + 26;
        int arrowY = frameY + FRAME_H / 2 - 6;

        leftArrow  = new ArrowIconButton(centerX() - FRAME_W / 2 - 20, arrowY,
                ArrowIconButton.Dir.LEFT, () -> cycle(-1));
        rightArrow = new ArrowIconButton(centerX() + FRAME_W / 2 + 8, arrowY,
                ArrowIconButton.Dir.RIGHT, () -> cycle(1));

        // Con 0 o 1 mascota las flechas no tienen a dónde llevar: se ocultan en vez de quedarse
        // muertas. Un control visible que no hace nada se lee como un bug.
        leftArrow.visible = rightArrow.visible = pets.size() > 1;

        addRenderableWidget(leftArrow);
        addRenderableWidget(rightArrow);
    }

    @Override protected boolean confirmEnabled() { return !pets.isEmpty(); }

    @Override
    protected void onConfirm() {
        if (pets.isEmpty()) return;
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new WishRevivePetPayload(index));
        onClose();   // el servidor revive y sincroniza
    }

    private void rebuildPets() {
        pets.clear();
        index = 0;
        if (mc.player == null || mc.level == null) return;
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(mc.player);
        for (CompoundTag tag : stats.getDeadPets()) {
            Entity e = EntityType.loadEntityRecursive(tag, mc.level, ent -> ent);
            if (e instanceof LivingEntity le) {
                // Se sanean los marcadores de muerte o la mascota aparecería tumbada y en rojo:
                // el jugador está eligiendo a quién devolver a la vida, no viendo su cadáver.
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

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (pets.isEmpty()) {
            drawCenteredOnPanel(g, Component.translatable("screen.zenkai.wish.no_pets"),
                    panelTop + CONTENT_TOP + 60, ZenkaiPalette.MUTED_ON_PANEL);
            return;
        }

        LivingEntity pet = pets.get(index);

        int fx = centerX() - FRAME_W / 2;
        int fy = panelTop + CONTENT_TOP + 26;

        // Nombre + posición en la lista.
        Component name = pet.hasCustomName() ? pet.getCustomName() : pet.getType().getDescription();
        drawCenteredOnPanel(g, name, panelTop + CONTENT_TOP + 8, ZenkaiPalette.LABEL_ON_PANEL);
        if (pets.size() > 1) {
            drawCenteredOnPanel(g,
                    Component.translatable("screen.zenkai.wish.pet_index", index + 1, pets.size()),
                    fy + FRAME_H + 6, ZenkaiPalette.MUTED_ON_PANEL);
        }

        // Marco del retrato.
        g.fill(fx - 1, fy - 1, fx + FRAME_W + 1, fy + FRAME_H + 1, ZenkaiPalette.BORDER_IN);
        g.fill(fx, fy, fx + FRAME_W, fy + FRAME_H, ZenkaiPalette.BEIGE_DEEP);

        // El recorte impide que una mascota grande (un caballo) se salga del marco.
        g.enableScissor(fx, fy, fx + FRAME_W, fy + FRAME_H);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, fx, fy, fx + FRAME_W, fy + FRAME_H, 30, 0.0625F,
                (float) mouseX, (float) mouseY, pet);
        g.disableScissor();
    }
}