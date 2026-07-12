package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.network.feature.technique.TechniquePacket;
import com.hmc.zenkai.core.technique.KiTechnique;
import com.hmc.zenkai.core.technique.KiTechniqueType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Editor de técnicas ki (crear con slot = -1, editar con slot >= 0), sobre common_screen.
 *  - Botones: TextOnlyButton (texturizable por Juan).
 *  - Color: ColorPickerWidget (HSV + hex), embebido en el panel.
 *  - Columna derecha: PREVIEW de la entidad (Fase B: cuando exista el proyectil, aquí se
 *    renderiza en vivo con el color/tamaño elegidos; hoy dibuja un placeholder que ya
 *    reacciona al color y al tamaño).
 * Guardar solo activo con tipo desbloqueado y nombre no vacío; el servidor vuelve a validar.
 * Al cerrar vuelve al menú en la pestaña Técnicas Ki.
 */
public class TechniqueEditScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    private static final int BG_W = 256;
    private static final int BG_H = 256;

    private final Minecraft mc = Minecraft.getInstance();
    private final int slot; // -1 = crear

    private KiTechniqueType type;
    private int rgb; // 0xRRGGBB
    private int size;

    private EditBox nameBox;
    private ColorPickerWidget picker;
    private TextOnlyButton unlockButton;
    private TextOnlyButton saveButton;
    private boolean explosive;

    private int leftPos, topPos;

    public TechniqueEditScreen(int slot) {
        super(Component.translatable("screen.zenkai.technique.edit_title"));
        this.slot = slot;

        PlayerStatsAttachment att = att();
        KiTechnique existing = (att != null && slot >= 0) ? att.techniques().slot(slot) : null;
        if (existing != null) {
            type = existing.type();
            rgb = existing.rgb();
            size = existing.size();
        } else {
            type = KiTechniqueType.BLAST;
            rgb = type.defaultRgb;
            size = KiTechnique.MIN_SIZE;
        }
    }

    private PlayerStatsAttachment att() {
        return mc.player == null ? null : mc.player.getData(DataAttachments.PLAYER_STATS.get());
    }

    @Override
    protected void init() {
        leftPos = (this.width - BG_W) / 2;
        topPos = (this.height - BG_H) / 2;
        int x = leftPos + 16;
        int contentW = BG_W - 32;

        // Nombre
        String prevName = nameBox != null ? nameBox.getValue() : initialName();
        nameBox = new EditBox(this.font, x, topPos + 28, contentW, 16,
                Component.translatable("screen.zenkai.technique.name"));
        nameBox.setMaxLength(KiTechnique.MAX_NAME_LENGTH);
        nameBox.setValue(prevName);
        nameBox.setResponder(s -> refreshButtons());
        this.addRenderableWidget(nameBox);

        // Tipo (cicla) + desbloqueo
        this.addRenderableWidget(new TextOnlyButton(x, topPos + 50, contentW, 14,
                typeLabel(), () -> {
            KiTechniqueType[] all = KiTechniqueType.values();
            type = all[(type.ordinal() + 1) % all.length];
            rgb = type.defaultRgb;
            rebuildWidgets();
        }));

        unlockButton = new TextOnlyButton(x, topPos + 66, contentW, 12,
                Component.translatable("screen.zenkai.technique.unlock", type.tpCost),
                () -> PacketDistributor.sendToServer(TechniquePacket.unlock(type)));
        this.addRenderableWidget(unlockButton);

        // Color (izquierda) — 118x98. La columna derecha queda para el preview (Fase B).
        picker = new ColorPickerWidget(x, topPos + 84, 0xFF000000 | rgb,
                "Ki Color", argb -> rgb = argb & 0xFFFFFF);
        this.addRenderableWidget(picker);

        // Tamaño
        this.addRenderableWidget(new AbstractSliderButton(x, topPos + 192, contentW, 14,
                sizeLabel(size), (size - KiTechnique.MIN_SIZE)
                / (double) (KiTechnique.MAX_SIZE - KiTechnique.MIN_SIZE)) {
            @Override protected void updateMessage() {
                setMessage(sizeLabel(size));
            }
            @Override protected void applyValue() {
                size = KiTechnique.MIN_SIZE + (int) Math.round(
                        value * (KiTechnique.MAX_SIZE - KiTechnique.MIN_SIZE));
            }
        });

        //Explosivo
        this.addRenderableWidget(new TextOnlyButton(x, topPos + 210, contentW, 12,
                explosiveLabel(), () -> {
            explosive = !explosive;
            rebuildWidgets();
        }));


        // Guardar / Cancelar
        saveButton = new TextOnlyButton(x, topPos + 228, contentW / 2 - 4, 14,
                Component.translatable("screen.zenkai.technique.save"), () -> {
            PlayerStatsAttachment a = att();
            if (a != null) {
                String n = KiTechnique.sanitizeName(nameBox.getValue());
                if (slot < 0) {
                    a.techniques().addSlot(new KiTechnique(n, type, rgb, size, explosive));
                } else {
                    KiTechnique ex = a.techniques().slot(slot);
                    if (ex != null) ex.set(n, type, rgb, size, explosive);
                }
            }
            PacketDistributor.sendToServer(
                    TechniquePacket.save(slot, type, nameBox.getValue(), rgb, size, explosive));
            close();
        });
        this.addRenderableWidget(saveButton);

        this.addRenderableWidget(new TextOnlyButton(x + contentW / 2 + 4, topPos + 214,
                contentW / 2 - 4, 14, Component.translatable("gui.cancel"), this::close));

        refreshButtons();
    }

    private String initialName() {
        PlayerStatsAttachment att = att();
        KiTechnique existing = (att != null && slot >= 0) ? att.techniques().slot(slot) : null;
        return existing != null ? existing.name() : "";
    }

    private Component typeLabel() {
        return Component.translatable("screen.zenkai.technique.type")
                .append(": ")
                .append(Component.translatable(type.nameKey()));
    }

    private Component explosiveLabel() {
        return Component.translatable("screen.zenkai.technique.explosive")
                .append(": ")
                .append(Component.translatable(explosive ? "gui.yes" : "gui.no"));
    }

    private static Component sizeLabel(int s) {
        return Component.translatable("screen.zenkai.technique.size", s);
    }

    private void refreshButtons() {
        PlayerStatsAttachment att = att();
        boolean unlocked = att != null && att.techniques().isUnlocked(type);
        unlockButton.visible = !unlocked;
        unlockButton.active = !unlocked && att != null && att.getTP() >= type.tpCost;
        saveButton.active = unlocked && !KiTechnique.sanitizeName(nameBox.getValue()).isEmpty();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        refreshButtons(); // el desbloqueo llega por sync asíncrono

        g.drawCenteredString(this.font, this.title, leftPos + BG_W / 2, topPos + 12, 0xFFFFFFFF);

        // Columna derecha: preview de la entidad (Fase B). Placeholder que ya reacciona
        // al color y tamaño elegidos.
        int px = leftPos + 16 + ColorPickerWidget.TOTAL_W + 8;
        int pw = leftPos + BG_W - 16 - px;
        int py = topPos + 84;
        int ph = ColorPickerWidget.TOTAL_H;
        g.fill(px, py, px + pw, py + ph, 0x60000000);
        int dot = 6 + size * 3;
        int cx = px + pw / 2, cy = py + ph / 2;
        g.fill(cx - dot / 2, cy - dot / 2, cx + dot / 2, cy + dot / 2, 0xFF000000 | rgb);

        if (att() != null) {
            g.drawString(this.font, Component.literal("TP: " + Objects.requireNonNull(att()).getTP()),
                    leftPos + 16, topPos + 232, 0xFFFFD700, true);
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, leftPos, topPos, 0, 0, BG_W, BG_H);
    }

    private void close() {
        mc.setScreen(new KiTechniquesScreen());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}



