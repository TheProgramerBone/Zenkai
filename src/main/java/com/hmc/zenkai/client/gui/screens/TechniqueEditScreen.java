package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.network.feature.technique.TechniquePacket;
import com.hmc.zenkai.core.technique.KiCombatServer;
import com.hmc.zenkai.core.technique.KiTechnique;
import com.hmc.zenkai.core.technique.KiTechniqueType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Editor de técnicas ki estilo DBC (crear con slot = -1, editar con slot >= 0), sobre
 * common_screen. Filas con cicladores < >:
 *   Name | Type | Effect (No/Explosive) | Size | Color (abre ColorPickerWidget, patrón
 *   AppearanceScreen/StyleSelection).
 * Debajo, PREVIEWS de solo lectura calculadas con LAS MISMAS fórmulas del servidor
 * (KiCombatServer): Speed, Damage (con tu Ki Power actual), Energy Cost, Casttime,
 * Cooldown. Guardar exige tipo desbloqueado y nombre; el servidor revalida.
 */
public class TechniqueEditScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    private static final int BG_W = 256;
    private static final int BG_H = 256;
    private static final int ROW_H = 17;

    private final Minecraft mc = Minecraft.getInstance();
    private final int slot; // -1 = crear

    private KiTechniqueType type;
    private int rgb;
    private int size;
    private boolean explosive;

    private EditBox nameBox;
    private TextOnlyButton unlockButton;
    private TextOnlyButton saveButton;
    private boolean pickerOpen = false;

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
            explosive = existing.explosive();
        } else {
            type = KiTechniqueType.BLAST;
            rgb = type.defaultRgb;
            size = KiTechnique.MIN_SIZE;
            explosive = false;
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

        // ── Name ──
        String prevName = nameBox != null ? nameBox.getValue() : initialName();
        nameBox = new EditBox(this.font, x + 44, topPos + 26, contentW - 44, 14,
                Component.translatable("screen.zenkai.technique.name"));
        nameBox.setMaxLength(KiTechnique.MAX_NAME_LENGTH);
        nameBox.setValue(prevName);
        nameBox.setResponder(s -> refreshButtons());
        this.addRenderableWidget(nameBox);

        int y = topPos + 46;

        // ── Type ──
        cyclerRow(x, y, contentW,
                () -> Component.translatable("screen.zenkai.technique.type")
                        .append(": ").append(Component.translatable(type.nameKey())),
                dir -> {
                    KiTechniqueType[] all = KiTechniqueType.values();
                    type = all[Math.floorMod(type.ordinal() + dir, all.length)];
                    rgb = type.defaultRgb;
                    rebuildWidgets();
                });
        y += ROW_H;

        // ── Effect (No / Explosive) ──
        cyclerRow(x, y, contentW,
                () -> Component.translatable("screen.zenkai.technique.effect")
                        .append(": ")
                        .append(Component.translatable(explosive
                                ? "screen.zenkai.technique.effect.explosive"
                                : "screen.zenkai.technique.effect.none")),
                dir -> {
                    explosive = !explosive;
                    rebuildWidgets();
                });
        y += ROW_H;

        // ── Size ──
        cyclerRow(x, y, contentW,
                () -> Component.translatable("screen.zenkai.technique.size", size),
                dir -> {
                    int span = KiTechnique.MAX_SIZE - KiTechnique.MIN_SIZE + 1;
                    size = KiTechnique.MIN_SIZE
                            + Math.floorMod(size - KiTechnique.MIN_SIZE + dir, span);
                    rebuildWidgets();
                });
        y += ROW_H;

        // ── Color (abre/cierra el picker) ──
        this.addRenderableWidget(new TextOnlyButton(x, y, 90, 14,
                Component.translatable("screen.zenkai.technique.color"),
                () -> {
                    pickerOpen = !pickerOpen;
                    rebuildWidgets();
                }));
        if (pickerOpen) {
            this.addRenderableWidget(new ColorPickerWidget(
                    leftPos + BG_W - 16 - ColorPickerWidget.TOTAL_W, y + 16,
                    0xFF000000 | rgb, "Ki Color", argb -> rgb = argb & 0xFFFFFF));
        }
        y += ROW_H;

        // ── Unlock (solo si el tipo está bloqueado) ──
        unlockButton = new TextOnlyButton(x, topPos + 196, contentW, 12,
                Component.translatable("screen.zenkai.technique.unlock", type.tpCost),
                () -> PacketDistributor.sendToServer(TechniquePacket.unlock(type)));
        this.addRenderableWidget(unlockButton);

        // ── Save / Cancel ──
        saveButton = new TextOnlyButton(x, topPos + 214, contentW / 2 - 4, 14,
                Component.translatable("screen.zenkai.technique.save"), () -> {
            PlayerStatsAttachment a = att();
            if (a != null) {
                String n = KiTechnique.sanitizeName(nameBox.getValue());
                if (slot < 0) {
                    a.techniques().addSlot(new KiTechnique(n, type, rgb, size, explosive)); // optimista
                } else {
                    KiTechnique ex = a.techniques().slot(slot);
                    if (ex != null) ex.set(n, type, rgb, size, explosive);                  // optimista
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

    /** Fila DBC: [<] etiqueta centrada [>]. dir = -1 / +1. */
    private void cyclerRow(int x, int y, int w, java.util.function.Supplier<Component> label,
                           java.util.function.IntConsumer onCycle) {
        this.addRenderableWidget(new TextOnlyButton(x, y, 12, 14,
                Component.literal("<"), () -> onCycle.accept(-1)));
        this.addRenderableWidget(new TextOnlyButton(x + 14, y, w - 28, 14,
                label.get(), () -> onCycle.accept(1)));
        this.addRenderableWidget(new TextOnlyButton(x + w - 12, y, 12, 14,
                Component.literal(">"), () -> onCycle.accept(1)));
    }

    private String initialName() {
        PlayerStatsAttachment att = att();
        KiTechnique existing = (att != null && slot >= 0) ? att.techniques().slot(slot) : null;
        return existing != null ? existing.name() : "";
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
        g.drawString(this.font, Component.translatable("screen.zenkai.technique.name")
                .append(":"), leftPos + 16, topPos + 29, 0xFFFFFFFF, true);

        // Swatch + ícono junto al botón Color.
        KiTechnique previewTech = new KiTechnique(" ", type, rgb, size, explosive);
        int sy = topPos + 46 + ROW_H * 3;
        g.fill(leftPos + 110, sy + 1, leftPos + 124, sy + 13, 0xFF000000);
        g.fill(leftPos + 111, sy + 2, leftPos + 123, sy + 12, 0xFF000000 | rgb);
        TechniqueIcons.draw(g, leftPos + 130, sy - 3, previewTech);

        // ── Previews (mismas fórmulas del servidor) ──
        PlayerStatsAttachment att = att();
        if (att != null && !pickerOpen) {
            double kiPower = att.computeKiPowerFinal();
            double dmg = KiCombatServer.computeDamage(kiPower, type, size)
                    * Math.max(1, type.count);
            int cost = KiCombatServer.computeCost(att.getEnergyMax(), type, size,
                    explosive && !type.defensive);

            int iy = topPos + 118;
            info(g, iy, "screen.zenkai.technique.speed", speedLabel());
            info(g, iy += 12, "screen.zenkai.technique.damage",
                    Component.literal(fmt(dmg)));
            info(g, iy += 12, "screen.zenkai.technique.cost",
                    Component.literal(String.valueOf(cost)));
            info(g, iy += 12, "screen.zenkai.technique.casttime",
                    Component.literal(fmt(type.chargeTicks / 20.0) + " sec"));
            info(g, iy += 12, "screen.zenkai.technique.cooldown",
                    Component.literal(fmt(type.cooldownTicks / 20.0) + " sec"));

            g.drawString(this.font, Component.literal("TP: " + att.getTP()),
                    leftPos + 16, topPos + 182, 0xFFFFD700, true);
        }
    }

    private void info(GuiGraphics g, int y, String key, Component value) {
        g.drawString(this.font, Component.translatable(key).append(": ").append(value),
                leftPos + 16, y, 0xFFCCCCCC, true);
    }

    private Component speedLabel() {
        String k = type.defensive ? "screen.zenkai.technique.speed.none"
                : type.speed <= 0.9f ? "screen.zenkai.technique.speed.slow"
                : type.speed <= 1.4f ? "screen.zenkai.technique.speed.average"
                : "screen.zenkai.technique.speed.fast";
        return Component.translatable(k);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, v == Math.floor(v) ? "%.0f" : "%.1f", v);
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