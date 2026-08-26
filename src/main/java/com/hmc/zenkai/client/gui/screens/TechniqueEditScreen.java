package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.KiChargeRenderer;
import com.hmc.zenkai.client.TechniqueAnimSets;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiBodyRenderer;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiMeshFactory;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiShape;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiVisual;
import com.hmc.zenkai.feature.player.MindBudget;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.technique.*;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor de técnicas ki (crear con slot = -1, editar con slot >= 0), sobre common_screen.
 * DOS PESTAÑAS dentro del MISMO Screen (excepción a "un Screen por pestaña"): el borrador a
 * medio editar tiene que sobrevivir al cambio de pestaña, y partirlo en dos pantallas
 * obligaría a sacar ese estado fuera para nada.
 *   - COMBAT: tipo, efecto, tamaño + las previews de números.
 *   - STYLE:  color, sonido de carga, sonido de disparo, set de animación + preview.
 * El nombre y los botones viven fuera de las pestañas: aplican a las dos.
 * Guardar/Cancelar van FUERA del panel; la X de arriba cierra. Borrar NO comparte fila con
 * Guardar: es una papelera dentro del panel, en la esquina inferior izquierda, y necesita
 * dos pulsaciones.
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
    private TechniqueEffect effect;
    private int chargeIdx;   // índice en soundList(true), 0 = ninguno
    private int releaseIdx;
    private int animSet;
    /** Bucle de animación del preview. Local, sin paquete: nadie más lo ve. */
    private final TechniqueAnimPreview animPreview = new TechniqueAnimPreview();

    private Tab tab = Tab.COMBAT;
    private boolean deleteArmed = false;

    private EditBox nameBox;
    private PanelButton unlockButton;
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
            effect = existing.effect();
            chargeIdx = indexOf(soundList(true), existing.chargeSound());
            releaseIdx = indexOf(soundList(false), existing.releaseSound());
            animSet = TechniqueAnimSets.clamp(existing.animSet());
        } else {
            type = KiTechniqueType.BLAST;
            rgb = type.defaultRgb();
            size = KiTechnique.MIN_SIZE;
            effect = TechniqueEffect.NONE;
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
        return Math.max(i, 0);
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
        nameBox = new EditBox(this.font, x + 35, topPos + Y_NAME, contentW - 60, 14,
                Component.translatable("screen.zenkai.technique.name"));
        nameBox.setMaxLength(KiTechnique.MAX_NAME_LENGTH);
        nameBox.setValue(prevName);
        addRenderableWidget(nameBox);

        // ── X de cerrar, esquina superior derecha del panel ──
        addRenderableWidget(new TextOnlyButton(
                leftPos + BG_W - MARGIN - X_SIZE, topPos + ScreenTitle.CONTENT_TOP, X_SIZE, X_SIZE,
                Component.empty(), TEX_X, TEX_X_HL, this::close));

        // ── Pestañas ──
        // .onPanel(): sin esto quedan con los colores por defecto de TextOnlyButton (blanco +
        // sombra), pensados para fondo oscuro — exactamente el "borrón sobre beige" que
        // ZenkaiPalette y PanelText documentan y que el resto de pantallas (Stats, Config) evita
        // llamando a esta función. Es lo que hacía que el editor se viera plano frente a Stats.
        int tabW = contentW / 2 - 2;
        addRenderableWidget(new TextOnlyButton(x, topPos + Y_TABS, tabW, 14,
                tabLabel(Tab.COMBAT), () -> switchTab(Tab.COMBAT)).onPanel());
        addRenderableWidget(new TextOnlyButton(x + tabW + 4, topPos + Y_TABS, tabW, 14,
                tabLabel(Tab.STYLE), () -> switchTab(Tab.STYLE)).onPanel());

        if (tab == Tab.COMBAT) initCombatTab(x, contentW);
        else initStyleTab(x, contentW);

        // ── Unlock (solo si el tipo está bloqueado) ──
        unlockButton = new PanelButton(
                x + (contentW - PanelButton.W) / 2, topPos + Y_UNLOCK,
                PanelButton.W, PanelButton.H,
                type.mindReq() > 0
                        ? Component.translatable("screen.zenkai.technique.unlock_mnd",
                        type.tpCost(), type.mindReq())
                        : Component.translatable("screen.zenkai.technique.unlock", type.tpCost()),
                PanelButton.Kind.PRIMARY,
                () -> PacketDistributor.sendToServer(TechniquePacket.unlock(type)));
        addRenderableWidget(unlockButton);

        initBottomBar();
        refreshButtons();

        if (reopenPicker && tab == Tab.STYLE) openPicker();
    }

    private void initCombatTab(int x, int contentW) {
        int y = topPos + Y_ROWS;

        // El valor va en negrita y en dorado (VALUE_ON_PANEL), como el nombre de la forma en
        // Stats: sin esto "Type: Blast" era una sola línea plana y el ojo no distinguía la
        // etiqueta de lo que de verdad se está eligiendo.
        Component typeLabel = Component.translatable("screen.zenkai.technique.type").append(": ")
                .append(Component.translatable(type.nameKey())
                        .withStyle(s -> s.withBold(true)
                                .withColor(TextColor.fromRgb(
                                        ZenkaiPalette.VALUE_ON_PANEL & 0xFFFFFF))));
        Component typeTooltip = Component.translatable(type.descKey());
        if (slot >= 0) {
            // Editando una técnica ya creada: el tipo NO se puede tocar aquí. Decide daño,
            // coste, animación y qué efectos admite, así que reasignarlo a mitad de vida es más
            // "borrar y crear otra" que "editar" — y el servidor lo rechaza igual
            // (TechniquePacket.handleSave ignora el tipo del paquete en modo edición), así que
            // dejar las flechas activas solo enseñaría un cambio que luego no se guarda. Sin
            // flechas y sin acción es el mismo lenguaje que ya usa este editor para "este campo
            // está fijo" (ver la fila de animación en initStyleTab cuando el tipo la impone).
            TextOnlyButton lockedType = new TextOnlyButton(x + ARROW_W + 2, y,
                    contentW - (ARROW_W + 2) * 2, 14, typeLabel, () -> {}).onPanel();
            lockedType.setTooltip(Tooltip.create(typeTooltip));
            addRenderableWidget(lockedType);
        } else {
            cyclerRow(x, y, contentW, typeLabel, typeTooltip,
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
        }
        y += ROW_H;

        // Ciruela + negrita solo cuando hay un efecto elegido: mismo trato que SPECIAL_ON_PANEL
        // le da a la forma en Stats ("estado especial"). En NONE se queda apagado a propósito,
        // para que la fila misma diga "no hay nada especial aquí" sin necesidad de leer el texto.
        int effectColor = effect == TechniqueEffect.NONE
                ? ZenkaiPalette.MUTED_ON_PANEL : ZenkaiPalette.SPECIAL_ON_PANEL;
        cyclerRow(x, y, contentW,
                Component.translatable("screen.zenkai.technique.effect").append(": ")
                        .append(Component.translatable(effect.langKey())
                                .withStyle(s -> s.withBold(effect != TechniqueEffect.NONE)
                                        .withColor(TextColor.fromRgb(effectColor & 0xFFFFFF)))),
                Component.translatable(effect.descKey()),
                dir -> {
                    // Salta los efectos que este tipo no admite: ciclar por opciones que el
                    // set() va a revertir a NONE es enseñar una elección que no existe.
                    TechniqueEffect[] all = TechniqueEffect.values();
                    TechniqueEffect next = effect;
                    for (int i = 0; i < all.length; i++) {
                        next = all[Math.floorMod(next.ordinal() + dir, all.length)];
                        if (type.allowsEffect(next)) break;
                    }
                    if (type.allowsEffect(next)) effect = next;
                    rebuildWidgets();
                });
        y += ROW_H;

        cyclerRow(x, y, contentW,
                Component.translatable("screen.zenkai.technique.size", size),
                dir -> {
                    int span = KiTechnique.MAX_SIZE - KiTechnique.MIN_SIZE + 1;
                    size = KiTechnique.MIN_SIZE
                            + Math.floorMod(size - KiTechnique.MIN_SIZE + dir, span);
                    rebuildWidgets();
                });
    }

    private void initStyleTab(int x, int contentW) {
        int y = topPos + Y_ROWS;

        // Color: botón a la izquierda, swatch + icono a la derecha. El picker se abre fuera.
        addRenderableWidget(new TextOnlyButton(x, y, 60, 14,
                Component.translatable("screen.zenkai.technique.color"), this::togglePicker)
                .onPanel());
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

        // El ORIGEN va en la misma fila que el set, no en una línea aparte: son el mismo dato.
        // Desde que la posición dejó de ser elegible es una consecuencia del set, y enseñarla
        // justo donde se cicla hace visible esa relación — cambias de animación y ves cambiar
        // de dónde sale. Además no hay hueco: entre esta fila (acaba en 142) y la caja del
        // preview (Y_PREVIEW = 150) quedan 8 px, y por debajo de la caja está Y_UNLOCK.
        // BARRIER no tiene set (su visual es 0), así que su origen sale de la constante.
        // El tipo puede IMPONER su animación (barrera, explosión). En ese caso no hay nada que
        // ciclar: en vez de una fila muerta con flechas que no hacen nada, se enseña el nombre
        // de la técnica y su origen como texto plano. Ocultarla dejaría al jugador sin
        // saber por qué esa técnica no tiene la opción que sí tienen las demás.
        TechniqueAnimOverride ov = type.animOverride();
        Component animLabel = ov != null
                ? Component.translatable("screen.zenkai.technique.anim",
                        Component.translatable(type.nameKey()))
                .append(" · ")
                .append(Component.translatable(
                        TechniqueAnimSet.BARRIER_POSITION.langKey()))
                : Component.translatable("screen.zenkai.technique.anim",
                        Component.translatable(TechniqueAnimSet.byNumber(animSet).langKey()))
                .append(" · ")
                .append(Component.translatable(
                        TechniqueAnimSet.positionOf(animSet).langKey()));

        // El botón central mide contentW - (ARROW_W + 2) * 2. Con nombres largos el texto se
        // sale de la fila, así que se recorta con el mismo criterio que el resto del mod.
        Component fitted = PanelText.fit(this.font, animLabel, contentW - (ARROW_W + 2) * 2 - 6);

        if (ov != null) {
            // Sin flechas y sin acción: la fila queda como etiqueta.
            addRenderableWidget(new TextOnlyButton(x + ARROW_W + 2, y,
                    contentW - (ARROW_W + 2) * 2, 14, fitted, () -> {}).onPanel());
        } else {
            cyclerRow(x, y, contentW, fitted,
                    dir -> {
                        int i = sets.indexOf(animSet);
                        if (i < 0) i = 0;
                        animSet = sets.get(Math.floorMod(i + dir, sets.size()));
                        rebuildWidgets();
                    });
        }
    }

    /** Cancelar | papelera | Guardar, FUERA del panel. */
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

    private void confirmDelete() {
        if (!deleteArmed) { deleteArmed = true; return; }
        PlayerStatsAttachment a = att();
        if (a != null) a.techniques().removeSlot(slot);
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
        if (a != null) {
            if (slot < 0) {
                a.techniques().addSlot(new KiTechnique(n, type, rgb, size, effect,
                        cs, rs, animSet));
            } else {
                KiTechnique ex = a.techniques().slot(slot);
                if (ex != null) ex.set(n, type, rgb, size, effect, cs, rs, animSet);
            }
        }
        PacketDistributor.sendToServer(TechniquePacket.save(
                slot, type, nameBox.getValue(), rgb, size, effect, cs, rs, animSet));
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

    private void cyclerRow(int x, int y, int w, Component label,
                           java.util.function.IntConsumer onCycle) {
        cyclerRow(x, y, w, label, null, onCycle);
    }

    /** Igual, con un tooltip opcional (qué hace el valor actual) puesto en las tres piezas de
     *  la fila — flechas incluidas, para que no haga falta acertar el pelo de texto exacto
     *  para que aparezca. */
    private void cyclerRow(int x, int y, int w, Component label, @Nullable Component tooltip,
                           java.util.function.IntConsumer onCycle) {
        ArrowIconButton left = new ArrowIconButton(x, y, ArrowIconButton.Dir.LEFT,
                () -> onCycle.accept(-1));
        TextOnlyButton mid = new TextOnlyButton(x + ARROW_W + 2, y, w - (ARROW_W + 2) * 2, 14,
                label, () -> onCycle.accept(1)).onPanel();
        ArrowIconButton right = new ArrowIconButton(x + w - ARROW_W, y, ArrowIconButton.Dir.RIGHT,
                () -> onCycle.accept(1));
        if (tooltip != null) {
            Tooltip t = Tooltip.create(tooltip);
            left.setTooltip(t);
            mid.setTooltip(t);
            right.setTooltip(t);
        }
        addRenderableWidget(left);
        addRenderableWidget(mid);
        addRenderableWidget(right);
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
                && MindBudget.canUnlock(att, type);
        saveButton.active = unlocked && type.enabled();
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        refreshButtons();

        ScreenTitle.drawAbovePanel(g, this.font, this.title, leftPos + BG_W / 2, topPos);
        PanelText.onPanel(g, this.font,
                Component.translatable("screen.zenkai.technique.name").append(":"),
                leftPos + MARGIN, topPos + Y_NAME + 3, ZenkaiPalette.LABEL_ON_PANEL);

        if (tab == Tab.COMBAT) renderCombatTab(g, partialTick);
        else renderStyleTab(g, mouseX, mouseY);

        if (deleteArmed && slot >= 0) {
            PanelText.centeredOnDark(g, this.font,
                    Component.translatable("screen.zenkai.technique.delete_confirm"),
                    leftPos + BG_W / 2, topPos + Y_BUTTONS - 11, ZenkaiPalette.ERROR);
        }
    }

    private void renderCombatTab(GuiGraphics g, float partialTick) {
        PlayerStatsAttachment att = att();
        if (att == null) return;

        double kiPower = att.computeKiPowerFinal();
        // BARRIER no hace daño (damage_mult 0.0 en su datapack): "Damage: 0" no le dice nada
        // útil al jugador. Muestra su vida/absorción real en el mismo hueco — mismo patrón que
        // speedLabel() ya usa (type.defensive(), no una comparación literal al tipo).
        boolean isBarrier = type.defensive();
        double dmg = isBarrier ? 0
                : KiCombatServer.computeDamage(kiPower, type, size) * Math.max(1, type.count());
        // 1.0 = referencia a carga normal (100%, sin sobrecarga), mismo criterio que dmg de
        // arriba (computeDamage sin multiplicar por ratio): el editor enseña el número base,
        // no una sobrecarga que el jugador aún no ha elegido.
        double life = isBarrier ? KiCombatServer.barrierPool(kiPower, size, 1.0) : 0;
        int cost = KiCombatServer.computeCost(att, type, size,
                effect);

        int iy = topPos + Y_BLOCK;
        info(g, iy, "screen.zenkai.technique.speed", speedLabel());
        info(g, iy += 11,
                isBarrier ? "screen.zenkai.technique.life" : "screen.zenkai.technique.damage",
                Component.literal(fmt(isBarrier ? life : dmg)));
        info(g, iy += 11, "screen.zenkai.technique.cost", Component.literal(String.valueOf(cost)));
        info(g, iy += 11, "screen.zenkai.technique.casttime",
                Component.literal(fmt(KiCombatServer.chargeTicksFor(type, size) / 20.0) + " sec"));
        info(g, iy += 11, "screen.zenkai.technique.cooldown",
                Component.literal(fmt(KiCombatServer.cooldownTicksFor(type, size) / 20.0) + " sec"));

        PanelText.rightOnPanel(g, this.font,
                Component.translatable("screen.zenkai.technique.tp", att.getTP()),
                leftPos + BG_W - MARGIN, topPos + Y_UNLOCK - 14, ZenkaiPalette.TP_ON_PANEL);

        renderTechniquePreview(g, partialTick);
    }

    private void renderStyleTab(GuiGraphics g, int mouseX, int mouseY) {
        int sy = topPos + Y_ROWS;
        int right = leftPos + BG_W - MARGIN;
        KiTechnique previewTech = new KiTechnique(" ", type, rgb, size, effect);
        g.fill(right - 40, sy + 1, right - 26, sy + 13, ZenkaiPalette.BORDER_IN);
        g.fill(right - 39, sy + 2, right - 27, sy + 12, 0xFF000000 | rgb);
        TechniqueIcons.draw(g, right - 18, sy - 3, previewTech);

        int cx = leftPos + BG_W / 2;
        int top = topPos + Y_PREVIEW;
        g.fill(cx - 45, top - 1, cx + 45, top + PREVIEW_H + 1, ZenkaiPalette.BORDER_IN);
        g.fill(cx - 44, top, cx + 44, top + PREVIEW_H, ZenkaiPalette.BEIGE_DEEP);
        if (mc.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, cx - 40, top + 2, cx + 40, top + PREVIEW_H - 2,
                    24, 0.0625f, (float) mouseX, (float) mouseY, mc.player);
        }
    }

    /** Etiqueta marrón + valor destacado: mismo par que drawField() en StatsScreen (Race, Style,
     *  TP...), en vez de dos tonos de marrón casi indistinguibles entre sí. */
    private void info(GuiGraphics g, int y, String key, Component value) {
        Component label = Component.translatable(key).append(": ");
        PanelText.onPanel(g, this.font, label, leftPos + MARGIN, y, ZenkaiPalette.LABEL_ON_PANEL);
        PanelText.onPanel(g, this.font, value,
                leftPos + MARGIN + this.font.width(label), y, ZenkaiPalette.VALUE_ON_PANEL);
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

    /**
     * Cuerpo REAL de KiBodyRenderer, el mismo que ves cargando en el mundo (KiChargeRenderer):
     * esfera con fresnel de tres bandas si el shader está disponible, cáscara + núcleo si no.
     * ANTES esto era un blit a mano de ki_ball.png con pulso hecho aquí a pelo y una estela de
     * franjas — el sistema VIEJO, anterior a KiBodyRenderer/KiMeshFactory/KiVisual. La malla es
     * la esfera desnuda de la carga (KiMeshFactory.chargeSphere()) para casi cualquier tipo —
     * cargando, la energía todavía no tiene forma — salvo DISK, que es la única excepción
     * también en KiChargeRenderer: un disco ya se reconoce como disco desde que se condensa, así
     * que aquí también usa su malla real y el mismo ladeo (ver DISK_CANT_DEGREES en
     * KiChargeRenderer) para que la vista previa no mienta sobre lo que se ve al cargar.
     * SIN proyección propia: el PoseStack de GuiGraphics ya sirve para geometría 3D dentro de la
     * GUI — es el mismo truco que usa InventoryScreen.renderEntityInInventory (translate + scale,
     * sin cámara real), y el shader de ki no depende de nada del mundo (ver ki_fresnel.json: solo
     * ModelViewMat/ProjMat que la propia GuiGraphics ya sube).
     */
    private void renderTechniquePreview(GuiGraphics g, float partialTick) {
        float t = (mc.player != null ? mc.player.tickCount : 0) + partialTick;
        float cr = ((rgb >> 16) & 0xFF) / 255f;
        float cg = ((rgb >> 8) & 0xFF) / 255f;
        float cb = (rgb & 0xFF) / 255f;

        int cx = leftPos + 208;
        int cy = topPos + Y_BLOCK + 26;

        // Mismo pulso que antes (respiración suave), no el de KiChargeRenderer: aquí no hay un
        // progreso de carga que animar, es una foto fija de "así se ve esta técnica".
        float pulse = 1f + 0.06f * (float) Math.sin(t * 0.3);
        float diameterPx = (16 + 4 * size) * pulse;

        KiVisual visual = KiVisual.of(type);
        // DISK: misma excepción que KiChargeRenderer (ver su DISK_CANT_DEGREES). Sin una
        // dirección real que mirar (esto es un icono fijo, no hay mundo ni cámara), el ladeo se
        // aplica sobre el eje fijo de la GUI y se deja girar despacio con `t` — no hay marco de
        // referencia (suelo, trayectoria) contra el que pueda leerse "mal orientado" aquí.
        boolean asDisk = visual.shape() == KiShape.DISK;

        g.pose().pushPose();
        g.pose().translate(cx, cy, 100.0);
        // Z invertida, igual que renderEntityInInventory: mantiene la misma quiralidad que usa
        // el resto del juego para geometría 3D metida en la GUI. La esfera es simétrica así que
        // no CAMBIA su silueta, pero el fresnel lee normales contra la cámara y conviene no
        // improvisar un espacio distinto al que ya está probado.
        g.pose().scale(1f, 1f, -1f);
        if (asDisk) {
            g.pose().mulPose(Axis.XP.rotationDegrees(KiChargeRenderer.DISK_CANT_DEGREES));
            g.pose().mulPose(Axis.ZP.rotationDegrees(t * 8f));
        }
        KiBodyRenderer.render(g.bufferSource(), visual,
                asDisk ? KiMeshFactory.get(visual) : KiMeshFactory.chargeSphere(),
                g.pose(), diameterPx, cr, cg, cb);
        g.flush();
        g.pose().popPose();
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

    @Override
    public void tick() {
        super.tick();
        animPreview.tick(animSet, type.animOverride());
    }

    @Override
    public void removed() {
        super.removed();
        animPreview.stop();
    }
}