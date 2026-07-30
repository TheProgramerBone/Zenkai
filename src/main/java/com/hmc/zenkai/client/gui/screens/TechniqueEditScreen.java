package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.TechniqueAnimSets;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.technique.TechniqueAssets;
import com.hmc.zenkai.feature.technique.TechniquePacket;
import com.hmc.zenkai.feature.technique.TechniquePosition;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import com.hmc.zenkai.feature.technique.KiTechnique;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor de técnicas ki (crear con slot = -1, editar con slot >= 0), sobre common_screen.
 *
 * DOS PESTAÑAS dentro del MISMO Screen (excepción a "un Screen por pestaña"): el borrador a
 * medio editar tiene que sobrevivir al cambio de pestaña, y partirlo en dos pantallas
 * obligaría a sacar ese estado fuera para nada.
 *   - COMBAT: tipo, efecto, tamaño, posición de salida + las previews de números.
 *   - STYLE:  color, sonido de carga, sonido de disparo, set de animación + preview.
 *
 * El nombre y los botones viven fuera de las pestañas: aplican a las dos.
 * Guardar/Cancelar van FUERA del panel; la X de arriba cierra. Borrar NO comparte fila con
 * Guardar: es una papelera dentro del panel, en la esquina inferior izquierda, y necesita
 * dos pulsaciones.
 *
 * El ColorPickerWidget se abre FUERA del panel (a la derecha, o a la izquierda si no cabe),
 * igual que en StyleSelectionScreen y AppearanceScreen. Antes se abría encima del bloque de
 * preview y obligaba a esconder media pestaña mientras estaba abierto.
 */
public class TechniqueEditScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_wide.png");
    private static final ResourceLocation TEX_X =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x.png");
    private static final ResourceLocation TEX_X_HL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x_highlight.png");
    private static final ResourceLocation TEX_TRASH =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_trash.png");
    private static final ResourceLocation TEX_TRASH_HL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_trash_highlight.png");
    private static final ResourceLocation PREVIEW_BALL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_ball.png");
    private static final ResourceLocation PREVIEW_TRAIL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_trail.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    private static final int MARGIN = 16;
    private static final int ROW_H = 18;
    private static final int ARROW_W = 12;

    private static final int BTN_W = 60;
    private static final int BTN_H = 25;
    private static final int X_SIZE = 12;

    private static final int Y_NAME  = ScreenTitle.CONTENT_TOP;
    private static final int Y_TABS  = Y_NAME + 20;
    private static final int Y_ROWS  = Y_TABS + 22;
    private static final int Y_BLOCK = 148;   // previews de combate
    private static final int Y_PREVIEW = 150; // caja de animación (STYLE); la fila 4 acaba en 142
    private static final int PREVIEW_H = 58;
    private static final int Y_UNLOCK = 214;
    private static final int Y_BUTTONS = BG_H + 4; // FUERA del panel
    private static final int TRASH_SIZE = 16;  // 32px de textura / 2 → reducción limpia

    private enum Tab { COMBAT, STYLE }

    private final Minecraft mc = Minecraft.getInstance();
    private final int slot; // -1 = crear

    // ── Borrador ─────────────────────────────────────────────────────────────
    private KiTechniqueType type;
    private int rgb;
    private int size;
    private boolean explosive;
    private TechniquePosition position;
    private int chargeIdx;   // índice en soundList(true), 0 = ninguno
    private int releaseIdx;
    private int animSet;

    private Tab tab = Tab.COMBAT;
    private boolean deleteArmed = false;

    private EditBox nameBox;
    private TextOnlyButton unlockButton;
    private TextOnlyButton saveButton;
    @Nullable private ColorPickerWidget picker = null;
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
            position = existing.position();
            chargeIdx = indexOf(soundList(true), existing.chargeSound());
            releaseIdx = indexOf(soundList(false), existing.releaseSound());
            animSet = TechniqueAnimSets.clamp(existing.animSet());
        } else {
            type = KiTechniqueType.BLAST;
            rgb = type.defaultRgb();
            size = KiTechnique.MIN_SIZE;
            explosive = false;
            position = TechniquePosition.RIGHT_HAND;
            chargeIdx = 0;
            releaseIdx = 0;
            animSet = 1;
        }
    }

    private PlayerStatsAttachment att() {
        return mc.player == null ? null : mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
    }

    /** Lista de sonidos con un hueco null al principio: "sin sonido" es una opción válida. */
    private static List<ResourceLocation> soundList(boolean charge) {
        List<ResourceLocation> out = new ArrayList<>();
        out.add(null);
        out.addAll(charge ? TechniqueAssets.chargeSounds() : TechniqueAssets.releaseSounds());
        return out;
    }

    private static int indexOf(List<ResourceLocation> list, ResourceLocation id) {
        int i = list.indexOf(id);
        return i < 0 ? 0 : i;
    }

    // ── Construcción ─────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // rebuildWidgets() vacía la lista de hijos: la referencia al picker queda colgando.
        // Se anota si estaba abierto para reabrirlo al final y que ciclar un sonido no lo cierre.
        boolean reopenPicker = picker != null;
        picker = null;

        leftPos = (this.width - BG_W) / 2;
        topPos = (this.height - BG_H) / 2;
        int x = leftPos + MARGIN;
        int contentW = BG_W - MARGIN * 2;

        // ── Nombre (común a las dos pestañas) ──
        String prevName = nameBox != null ? nameBox.getValue() : initialName();
        nameBox = new EditBox(this.font, x + 44, topPos + Y_NAME, contentW - 44, 14,
                Component.translatable("screen.zenkai.technique.name"));
        nameBox.setMaxLength(KiTechnique.MAX_NAME_LENGTH);
        nameBox.setValue(prevName);
        // Ya no hay responder que valide: el nombre vacío es legal y se resuelve al tipo.
        addRenderableWidget(nameBox);

        // ── X de cerrar, esquina superior derecha del panel ──
        addRenderableWidget(new TextOnlyButton(
                leftPos + BG_W - MARGIN - X_SIZE, topPos + 10, X_SIZE, X_SIZE,
                Component.empty(), TEX_X, TEX_X_HL, this::close));

        // ── Pestañas ──
        int tabW = contentW / 2 - 2;
        addRenderableWidget(new TextOnlyButton(x, topPos + Y_TABS, tabW, 14,
                tabLabel(Tab.COMBAT), () -> switchTab(Tab.COMBAT)));
        addRenderableWidget(new TextOnlyButton(x + tabW + 4, topPos + Y_TABS, tabW, 14,
                tabLabel(Tab.STYLE), () -> switchTab(Tab.STYLE)));

        if (tab == Tab.COMBAT) initCombatTab(x, contentW);
        else initStyleTab(x, contentW);

        // ── Unlock (solo si el tipo está bloqueado) ──
        unlockButton = new TextOnlyButton(x, topPos + Y_UNLOCK, contentW, 12,
                type.mindReq() > 0
                        ? Component.translatable("screen.zenkai.technique.unlock_mnd",
                        type.tpCost(), type.mindReq())
                        : Component.translatable("screen.zenkai.technique.unlock", type.tpCost()),
                () -> PacketDistributor.sendToServer(TechniquePacket.unlock(type)));
        addRenderableWidget(unlockButton);

        initBottomBar();
        refreshButtons();

        if (reopenPicker && tab == Tab.STYLE) openPicker();
    }

    private void initCombatTab(int x, int contentW) {
        int y = topPos + Y_ROWS;

        cyclerRow(x, y, contentW,
                Component.translatable("screen.zenkai.technique.type")
                        .append(": ").append(Component.translatable(type.nameKey())),
                dir -> {
                    KiTechniqueType[] all = KiTechniqueType.values();
                    KiTechniqueType next = type;
                    for (int i = 0; i < all.length; i++) {
                        next = all[Math.floorMod(next.ordinal() + dir, all.length)];
                        if (next.enabled()) break;
                    }
                    if (!next.enabled()) return;
                    type = next;
                    rgb = type.defaultRgb();
                    rebuildWidgets();
                });
        y += ROW_H;

        cyclerRow(x, y, contentW,
                Component.translatable("screen.zenkai.technique.effect").append(": ")
                        .append(Component.translatable(explosive
                                ? "screen.zenkai.technique.effect.explosive"
                                : "screen.zenkai.technique.effect.none")),
                dir -> { explosive = !explosive; rebuildWidgets(); });
        y += ROW_H;

        cyclerRow(x, y, contentW,
                Component.translatable("screen.zenkai.technique.size", size),
                dir -> {
                    int span = KiTechnique.MAX_SIZE - KiTechnique.MIN_SIZE + 1;
                    size = KiTechnique.MIN_SIZE
                            + Math.floorMod(size - KiTechnique.MIN_SIZE + dir, span);
                    rebuildWidgets();
                });
        y += ROW_H;

        cyclerRow(x, y, contentW,
                Component.translatable("screen.zenkai.technique.position").append(": ")
                        .append(Component.translatable(position.langKey())),
                dir -> {
                    TechniquePosition[] all = TechniquePosition.values();
                    position = all[Math.floorMod(position.ordinal() + dir, all.length)];
                    rebuildWidgets();
                });
    }

    private void initStyleTab(int x, int contentW) {
        int y = topPos + Y_ROWS;

        // Color: botón a la izquierda, swatch + icono a la derecha. El picker se abre fuera.
        addRenderableWidget(new TextOnlyButton(x, y, 60, 14,
                Component.translatable("screen.zenkai.technique.color"), this::togglePicker));
        y += ROW_H;

        List<ResourceLocation> charges = soundList(true);
        cyclerRow(x, y, contentW,
                Component.translatable("screen.zenkai.technique.sound_charge").append(": ")
                        .append(soundLabel(charges.get(Math.floorMod(chargeIdx, charges.size())))),
                dir -> { chargeIdx = Math.floorMod(chargeIdx + dir, charges.size()); rebuildWidgets(); });
        y += ROW_H;

        List<ResourceLocation> releases = soundList(false);
        cyclerRow(x, y, contentW,
                Component.translatable("screen.zenkai.technique.sound_release").append(": ")
                        .append(soundLabel(releases.get(Math.floorMod(releaseIdx, releases.size())))),
                dir -> { releaseIdx = Math.floorMod(releaseIdx + dir, releases.size()); rebuildWidgets(); });
        y += ROW_H;

        List<Integer> sets = TechniqueAnimSets.available();
        cyclerRow(x, y, contentW,
                Component.translatable("screen.zenkai.technique.anim", animSet),
                dir -> {
                    int i = sets.indexOf(animSet);
                    if (i < 0) i = 0;
                    animSet = sets.get(Math.floorMod(i + dir, sets.size()));
                    rebuildWidgets();
                });
    }

    /** Cancelar | papelera | Guardar, FUERA del panel. La papelera comparte fila con Save,
     *  así que necesita DOS pulsaciones: un clic suelto no puede borrar nada. */
    private void initBottomBar() {
        int y = topPos + Y_BUTTONS;

        addRenderableWidget(new TextOnlyButton(
                leftPos + MARGIN, y, BTN_W, BTN_H,
                Component.translatable("gui.cancel"), TEX_BTN, null, this::close));

        if (slot >= 0) {
            addRenderableWidget(new TextOnlyButton(
                    leftPos + (BG_W - TRASH_SIZE) / 2, y + (BTN_H - TRASH_SIZE) / 2,
                    TRASH_SIZE, TRASH_SIZE,
                    Component.empty(), TEX_TRASH, TEX_TRASH_HL, this::confirmDelete));
        }

        saveButton = new TextOnlyButton(
                leftPos + BG_W - MARGIN - BTN_W, y, BTN_W, BTN_H,
                Component.translatable("screen.zenkai.technique.save"), TEX_BTN, null, this::save);
        addRenderableWidget(saveButton);
    }

    /** Doble pulsación: el primer clic arma, el segundo borra. Sin pantalla de confirmación
     *  para no destruir el borrador que vive en este Screen. */
    private void confirmDelete() {
        if (!deleteArmed) { deleteArmed = true; return; }
        PlayerStatsAttachment a = att();
        if (a != null) a.techniques().removeSlot(slot); // optimista
        PacketDistributor.sendToServer(TechniquePacket.delete(slot));
        close();
    }

    private void save() {
        List<ResourceLocation> charges = soundList(true);
        List<ResourceLocation> releases = soundList(false);
        ResourceLocation cs = charges.get(Math.floorMod(chargeIdx, charges.size()));
        ResourceLocation rs = releases.get(Math.floorMod(releaseIdx, releases.size()));
        String n = KiTechnique.sanitizeName(nameBox.getValue());

        PlayerStatsAttachment a = att();
        if (a != null) { // actualización optimista: el servidor revalida y resincroniza
            if (slot < 0) {
                a.techniques().addSlot(new KiTechnique(n, type, rgb, size, explosive,
                        position, cs, rs, animSet));
            } else {
                KiTechnique ex = a.techniques().slot(slot);
                if (ex != null) ex.set(n, type, rgb, size, explosive, position, cs, rs, animSet);
            }
        }
        PacketDistributor.sendToServer(TechniquePacket.save(
                slot, type, nameBox.getValue(), rgb, size, explosive, position, cs, rs, animSet));
        close();
    }

    private void switchTab(Tab t) {
        if (tab == t) return;
        tab = t;
        closePicker();
        rebuildWidgets();
    }

    // ── Color picker, fuera del panel ────────────────────────────────────────

    private void togglePicker() {
        if (picker != null) { closePicker(); return; }
        openPicker();
    }

    /** A la derecha del panel; si se sale de pantalla, a la izquierda. Nunca encima del panel:
     *  así el preview y las filas siguen visibles mientras se elige el color. */
    private void openPicker() {
        closePicker();
        int px = leftPos + BG_W + 8;
        if (px + ColorPickerWidget.TOTAL_W > this.width - 4)
            px = leftPos - ColorPickerWidget.TOTAL_W - 8;
        picker = new ColorPickerWidget(px, topPos + Y_ROWS, 0xFF000000 | rgb,
                "Ki Color", argb -> rgb = argb & 0xFFFFFF);
        addRenderableWidget(picker);
    }

    private void closePicker() {
        if (picker != null) { removeWidget(picker); picker = null; }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker != null) {
            boolean inside = mx >= picker.getX() && mx < picker.getX() + ColorPickerWidget.TOTAL_W
                    && my >= picker.getY() && my < picker.getY() + ColorPickerWidget.TOTAL_H;
            if (!inside) closePicker();
        }
        return super.mouseClicked(mx, my, button);
    }

    private Component tabLabel(Tab t) {
        Component c = Component.translatable(t == Tab.COMBAT
                ? "screen.zenkai.technique.tab.combat"
                : "screen.zenkai.technique.tab.style");
        return tab == t ? Component.literal("[ ").append(c).append(" ]") : c;
    }

    private Component soundLabel(ResourceLocation id) {
        return id == null
                ? Component.translatable("screen.zenkai.technique.sound.none")
                : Component.literal(id.getPath());
    }

    /** Fila: [<] etiqueta centrada [>]. dir = -1 / +1. */
    private void cyclerRow(int x, int y, int w, Component label,
                           java.util.function.IntConsumer onCycle) {
        addRenderableWidget(new ArrowIconButton(x, y, ArrowIconButton.Dir.LEFT,
                () -> onCycle.accept(-1)));
        addRenderableWidget(new TextOnlyButton(x + ARROW_W + 2, y, w - (ARROW_W + 2) * 2, 14,
                label, () -> onCycle.accept(1)));
        addRenderableWidget(new ArrowIconButton(x + w - ARROW_W, y, ArrowIconButton.Dir.RIGHT,
                () -> onCycle.accept(1)));
    }

    private String initialName() {
        PlayerStatsAttachment att = att();
        KiTechnique existing = (att != null && slot >= 0) ? att.techniques().slot(slot) : null;
        return existing != null ? existing.name() : "";
    }

    private void refreshButtons() {
        PlayerStatsAttachment att = att();
        boolean unlocked = att != null && att.techniques().isUnlocked(type);
        unlockButton.visible = !unlocked && type.enabled();
        unlockButton.active = !unlocked && type.enabled() && att != null
                && att.getTP() >= type.tpCost()
                && att.getAttribute(ZenkaiAttributes.MIND)
                >= type.mindReq();
        // El nombre vacío YA NO bloquea: se guarda vacío y se muestra el nombre del tipo.
        saveButton.active = unlocked && type.enabled();
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        refreshButtons(); // el desbloqueo llega por sync asíncrono

        ScreenTitle.drawAbovePanel(g, this.font, this.title, leftPos + BG_W / 2, topPos);
        g.drawString(this.font, Component.translatable("screen.zenkai.technique.name").append(":"),
                leftPos + MARGIN, topPos + Y_NAME + 3, 0xFFFFFFFF, true);

        if (tab == Tab.COMBAT) renderCombatTab(g, partialTick);
        else renderStyleTab(g, mouseX, mouseY);

        if (deleteArmed && slot >= 0) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.zenkai.technique.delete_confirm"),
                    leftPos + BG_W / 2, topPos + Y_BUTTONS - 11, 0xFFFF5555);
        }
    }

    private void renderCombatTab(GuiGraphics g, float partialTick) {
        PlayerStatsAttachment att = att();
        if (att == null) return;

        double kiPower = att.computeKiPowerFinal();
        double dmg = KiCombatServer.computeDamage(kiPower, type, size) * Math.max(1, type.count());
        int cost = KiCombatServer.computeCost(att, type, size,
                explosive && !type.defensive());

        int iy = topPos + Y_BLOCK;
        info(g, iy, "screen.zenkai.technique.speed", speedLabel());
        info(g, iy += 11, "screen.zenkai.technique.damage", Component.literal(fmt(dmg)));
        info(g, iy += 11, "screen.zenkai.technique.cost", Component.literal(String.valueOf(cost)));
        info(g, iy += 11, "screen.zenkai.technique.casttime",
                Component.literal(fmt(type.chargeTicks() / 20.0) + " sec"));
        info(g, iy += 11, "screen.zenkai.technique.cooldown",
                Component.literal(fmt(type.cooldownTicks() / 20.0) + " sec"));

        g.drawString(this.font, Component.literal("TP: " + att.getTP()),
                leftPos + MARGIN, topPos + Y_UNLOCK - 14, 0xFFFFD700, true);

        renderTechniquePreview(g, partialTick);
    }

    private void renderStyleTab(GuiGraphics g, int mouseX, int mouseY) {
        // Swatch del color + icono, a la derecha de la fila Color.
        int sy = topPos + Y_ROWS;
        int right = leftPos + BG_W - MARGIN;
        KiTechnique previewTech = new KiTechnique(" ", type, rgb, size, explosive);
        g.fill(right - 40, sy + 1, right - 26, sy + 13, 0xFF000000);
        g.fill(right - 39, sy + 2, right - 27, sy + 12, 0xFF000000 | rgb);
        TechniqueIcons.draw(g, right - 18, sy - 3, previewTech);

        // El preview ya NO se esconde con el picker abierto: el picker está fuera del panel.
        // MAQUETADO: hoy pinta el modelo quieto; falta lanzar la animación PAL en LOCAL
        // (sin packet) para que el resto de jugadores no la vea.
        int cx = leftPos + BG_W / 2;
        int top = topPos + Y_PREVIEW;
        g.fill(cx - 44, top, cx + 44, top + PREVIEW_H, 0x40000000);
        if (mc.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, cx - 40, top + 2, cx + 40, top + PREVIEW_H - 2,
                    24, 0.0625f, (float) mouseX, (float) mouseY, mc.player);
        }
    }

    private void info(GuiGraphics g, int y, String key, Component value) {
        g.drawString(this.font, Component.translatable(key).append(": ").append(value),
                leftPos + MARGIN, y, 0xFFCCCCCC, true);
    }

    private Component speedLabel() {
        String k = type.defensive() ? "screen.zenkai.technique.speed.none"
                : type.speed() <= 0.9f ? "screen.zenkai.technique.speed.slow"
                : type.speed() <= 1.4f ? "screen.zenkai.technique.speed.average"
                : "screen.zenkai.technique.speed.fast";
        return Component.translatable(k);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, v == Math.floor(v) ? "%.0f" : "%.1f", v);
    }

    /** Preview en vivo: bola tintada con pulso + estela desvanecida (SPIRAL ondula),
     *  BARRIER translúcida, BURST con sus bolitas. Solo GUI: sin entidad. */
    private void renderTechniquePreview(GuiGraphics g, float partialTick) {
        float t = (mc.player != null ? mc.player.tickCount : 0) + partialTick;
        float cr = ((rgb >> 16) & 0xFF) / 255f;
        float cg = ((rgb >> 8) & 0xFF) / 255f;
        float cb = (rgb & 0xFF) / 255f;

        int cx = leftPos + 208;
        int cy = topPos + Y_BLOCK + 26;
        boolean spiral = type == KiTechniqueType.SPIRAL;
        if (spiral) cy += (int) (Math.sin(t * 0.35) * 3);

        RenderSystem.enableBlend();

        if (type.hasTrail()) {
            int trailLen = switch (type) { case LAZER -> 57; case WAVE -> 42; default -> 48; };
            int trailW = (int) Math.max(6, (10 + 2 * size) * type.trailWidth());
            int seg = trailLen / 3;
            float[] alphas = {0.65f, 0.4f, 0.18f};
            for (int i = 0; i < 3; i++) {
                float segCx = cx - seg * (i + 0.5f);
                float segCy = cy + (spiral ? (float) Math.sin(t * 0.35 - (i + 1) * 1.1) * 3 : 0);
                RenderSystem.setShaderColor(cr, cg, cb, alphas[i]);
                g.pose().pushPose();
                g.pose().translate(segCx, segCy, 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees(90));
                g.blit(PREVIEW_TRAIL, -trailW / 2, -seg / 2, trailW, seg, 0f, 0f, 32, 64, 32, 64);
                g.pose().popPose();
            }
        }

        float pulse = 1f + 0.06f * (float) Math.sin(t * 0.3);
        boolean barrier = type == KiTechniqueType.BARRIER;
        RenderSystem.setShaderColor(cr, cg, cb, barrier ? 0.45f : 1f);
        if (type == KiTechniqueType.BURST) {
            int d = (int) ((10 + 2 * size) * pulse);
            int[][] off = {{0, 0}, {-13, -9}, {-9, 10}};
            for (int[] o : off) {
                g.blit(PREVIEW_BALL, cx + o[0] - d / 2, cy + o[1] - d / 2, d, d, 0f, 0f, 64, 64, 64, 64);
            }
        } else {
            int base = barrier ? 34 + 2 * size : 16 + 4 * size;
            int d = (int) (base * pulse);
            g.blit(PREVIEW_BALL, cx - d / 2, cy - d / 2, d, d, 0f, 0f, 64, 64, 64, 64);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
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