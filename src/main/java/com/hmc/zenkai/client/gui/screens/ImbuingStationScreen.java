package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.client.gui.menu.ImbuingStationMenu;
import com.hmc.zenkai.content.blockentity.ImbuingStationBlockEntity;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.armor.AttributeTemplates;
import com.hmc.zenkai.feature.armor.ImbuedAttributes;
import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de la Estación de imbuido (2026-11-23: las pestañas Atributos e Infundir de la antigua máquina de fabricación,
 * que el usuario partió en dos). Común: barra de FE, tanque de Ki (coste de imbuir e infundir) y la columna de cuatro
 * mejoras a la derecha. Pestañas en la cabecera.
 *   ATRIBUTOS: pieza y sangre, botón Imbuir y a la derecha la lectura: puntos usados/tope con su barra, lo imbuido de la
 *             pieza (dos columnas) y el coste de esta sangre.
 *   INFUNDIR: pieza y lingotes infundidos, botón Infundir y a la derecha nivel, lingotes, coste, tope y durabilidad que
 *             tendrá.
 * Fondos armor_fabricator_attributes/_infuse.png (tools/gen_armor_fabricator_screen.py; reutilizados tal cual); geometría
 * == ese script e ImbuingStationMenu. El coste se calcula aquí con la MISMA función que el servidor
 * (ImbuingStationBlockEntity.preview) y la config sincronizada. Cifras por ZenkaiNumbers (regla de CLAUDE.md).
 *
 * El panel oscuro de Atributos solo tiene {@link #INFO_W} px de texto: cada línea se parte (font.split) o se recorta, y
 * en las filas «etiqueta … cifra» la etiqueta cede ante la cifra. Antes «Imbued in the piece» y «Insert a piece and a
 * blood» se salían del panel (captura del usuario 2026-11-22).
 */
public class ImbuingStationScreen extends KiMachineScreen<ImbuingStationMenu> {

    private static final ResourceLocation BG_ATTR =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/armor_fabricator_attributes.png");
    private static final ResourceLocation BG_INFUSE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/armor_fabricator_infuse.png");

    // ── Layout (== tools/gen_armor_fabricator_screen.py) ─────────────────────
    // Dos pestañas, en los dos huecos de la derecha de la cabecera (los que ocupaban Atributos e Infundir).
    private static final int TAB_Y = 3, TAB_H = 14, TAB_W = 46, TAB1_X = 150, TAB2_X = 198;
    private static final int ATTR_INFO_X = 128, ATTR_INFO_Y = 36, INFO_LH = 10;
    private static final int INFO_W = INFO_RIGHT - ATTR_INFO_X;
    /** Primera fila del bloque de coste (Atributos): debajo de la barra de tope, los atributos en dos columnas y la pista. */
    private static final int ATTR_COST_Y = 104;
    /** Última fila que cabe dentro del panel oscuro (pozo hasta y=136, == BAR_Y + BAR_H). */
    private static final int INFO_BOTTOM = 136;
    /** Barra de puntos usados/tope bajo la cabecera de Atributos. */
    private static final int CAP_BAR_Y = 47, CAP_BAR_H = 3;
    private static final int IMBUE_X = 62, IMBUE_Y = 90, IMBUE_W = 56, IMBUE_H = 18;
    /** Barra de carga de imbuir: centrada en el hueco vertical entre la ranura de la pieza (pozo hasta y=53) y la de la
     *  sangre (pozo desde y=63), con el ancho del ítem (x 66..82). Pequeña a propósito (pedido del usuario 2026-11-22;
     *  antes iba a la derecha de las ranuras). */
    public static final int CHARGE_X = 66, CHARGE_Y = 56, CHARGE_W = 16, CHARGE_H = 4;

    private static final String[] UPGRADE_KEYS = {
            "screen.zenkai.ki_chamber.slot.tolerance", "screen.zenkai.ki_chamber.slot.speed",
            "screen.zenkai.ki_chamber.slot.storage", "screen.zenkai.armor_fabricator.slot.efficiency",
            "screen.zenkai.armor_fabricator.slot.battery" };

    private TechButton tabAttr, tabInfuse, imbueButton, infuseButton;

    public ImbuingStationScreen(ImbuingStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override protected String tankTipKey() { return "screen.zenkai.armor_fabricator.tank.tip"; }
    @Override protected String noToleranceKey() { return "screen.zenkai.armor_fabricator.tank.no_tolerance"; }

    @Override
    protected ResourceLocation background() {
        return menu.tab() == ImbuingStationMenu.TAB_INFUSE ? BG_INFUSE : BG_ATTR;
    }

    @Override
    protected void init() {
        super.init();
        tabAttr = new TechButton(leftPos + TAB1_X, topPos + TAB_Y, TAB_W, TAB_H,
                Component.translatable("screen.zenkai.armor_fabricator.tab.attributes"), TechButton.Kind.SECONDARY,
                () -> switchTab(ImbuingStationMenu.TAB_ATTR));
        tabInfuse = new TechButton(leftPos + TAB2_X, topPos + TAB_Y, TAB_W, TAB_H,
                Component.translatable("screen.zenkai.armor_fabricator.tab.infuse"), TechButton.Kind.SECONDARY,
                () -> switchTab(ImbuingStationMenu.TAB_INFUSE));
        addRenderableWidget(tabAttr);
        addRenderableWidget(tabInfuse);
        imbueButton = new TechButton(leftPos + IMBUE_X, topPos + IMBUE_Y, IMBUE_W, IMBUE_H,
                Component.translatable("screen.zenkai.armor_fabricator.imbue"), TechButton.Kind.PRIMARY,
                () -> press(ImbuingStationMenu.BTN_IMBUE));
        addRenderableWidget(imbueButton);
        infuseButton = new TechButton(leftPos + IMBUE_X, topPos + IMBUE_Y, IMBUE_W, IMBUE_H,
                Component.translatable("screen.zenkai.armor_fabricator.infuse"), TechButton.Kind.PRIMARY,
                () -> press(ImbuingStationMenu.BTN_INFUSE));
        addRenderableWidget(infuseButton);
        refresh();
    }

    private void switchTab(int tab) {
        menu.setTab(tab);   // en cliente a mano: el botón vanilla solo llega al servidor
        press(tab == ImbuingStationMenu.TAB_INFUSE ? ImbuingStationMenu.BTN_TAB_INFUSE : ImbuingStationMenu.BTN_TAB_ATTR);
        refresh();
    }

    private void press(int id) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        boolean attr = menu.tab() == ImbuingStationMenu.TAB_ATTR;
        boolean infuse = !attr;
        imbueButton.visible = attr;
        infuseButton.visible = infuse;
        ImbuingStationBlockEntity.InfusePreview ip = infusePreview();
        infuseButton.active = !menu.infusing() && ip != null && ip.ok();
        boolean template = hasTemplate();
        imbueButton.setMessage(Component.translatable(template ? "screen.zenkai.armor_fabricator.template.apply"
                : "screen.zenkai.armor_fabricator.imbue"));
        AttributeTemplates.Plan plan = templatePlan();
        ImbuingStationBlockEntity.ImbuePreview pv = preview();
        imbueButton.active = !menu.imbuing() && (template ? plan != null && plan.ok() : pv != null && !pv.overCap());
        tabAttr.active = !attr;
        tabInfuse.active = !infuse;
    }

    private boolean hasTemplate() { return !menu.machineStack(ImbuingStationBlockEntity.SLOT_TEMPLATE).isEmpty(); }

    @Nullable
    private AttributeTemplates.Plan templatePlan() {
        return ImbuingStationBlockEntity.templatePlan(minecraft == null ? null : minecraft.level,
                menu.machineStack(ImbuingStationBlockEntity.SLOT_TEMPLATE),
                menu.machineStack(ImbuingStationBlockEntity.SLOT_ARMOR),
                menu.machineStack(ImbuingStationBlockEntity.SLOT_TARGET));
    }

    /** Coste que enseña el bloque inferior de Atributos: el de la plantilla si hay una, si no el de la sangre. */
    private record Cost(long fe, long mb, @Nullable KiEnergyType fluid,
                        java.util.function.Function<KiEnergyType, Long> mbWith) {}

    @Nullable
    private Cost cost() {
        if (hasTemplate()) {
            AttributeTemplates.Plan p = templatePlan();
            if (p == null || !p.ok()) return null;
            return new Cost(p.fe(), menu.fluidCost(p.mb()), p.fluid(), t -> menu.fluidCost(p.mbWith(t)));
        }
        ImbuingStationBlockEntity.ImbuePreview p = preview();
        if (p == null) return null;
        return new Cost(p.fe(), menu.fluidCost(p.mb()), p.fluid(), t -> menu.fluidCost(p.mbWith(t)));
    }

    @Nullable
    private ImbuingStationBlockEntity.ImbuePreview preview() {
        return ImbuingStationBlockEntity.preview(minecraft == null ? null : minecraft.level,
                menu.machineStack(ImbuingStationBlockEntity.SLOT_ARMOR),
                menu.machineStack(ImbuingStationBlockEntity.SLOT_BLOOD));
    }

    @Nullable
    private ImbuingStationBlockEntity.InfusePreview infusePreview() {
        return ImbuingStationBlockEntity.infusePreview(menu.machineStack(ImbuingStationBlockEntity.SLOT_INFUSE_PIECE),
                menu.machineStack(ImbuingStationBlockEntity.SLOT_INFUSE_INGOTS));
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawHeader(g, menu.isWorking());
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);
        drawMachineBars(g, menu.isWorking());
        if (menu.tab() == ImbuingStationMenu.TAB_INFUSE) renderInfuse(g);
        else renderAttributes(g);
    }

    private void renderAttributes(GuiGraphics g) {
        drawBar(g, CHARGE_X, CHARGE_Y, CHARGE_W, CHARGE_H, menu.imbueFraction());
        int x = ATTR_INFO_X, y = ATTR_INFO_Y;
        ItemStack piece = menu.machineStack(ImbuingStationBlockEntity.SLOT_ARMOR);
        // Cabecera con los puntos usados / tope de la pieza y su barra (rework del 2026-11-23).
        boolean hasPiece = ImbuingStationBlockEntity.isArmorPiece(piece);
        int used = hasPiece ? KiInfusion.usedPoints(piece) : 0, cap = hasPiece ? KiInfusion.pieceCap(piece) : 0;
        if (hasPiece) {
            infoRow(g, x, y, "screen.zenkai.armor_fabricator.imbued",
                    Component.literal(ZenkaiNumbers.screen((long) used) + "/" + ZenkaiNumbers.screen((long) cap)),
                    used >= cap ? ZenkaiTechPalette.DENIED_ON_SCREEN : ZenkaiTechPalette.TEXT_ON_SCREEN);
            drawBar(g, x, CAP_BAR_Y, INFO_W, CAP_BAR_H, cap <= 0 ? 1f : Math.min(1f, used / (float) cap));
        } else {
            PanelText.onDark(g, font, PanelText.fit(font, Component.translatable("screen.zenkai.armor_fabricator.imbued"), INFO_W),
                    x, y, ZenkaiTechPalette.DIM_ON_SCREEN);
        }
        y = CAP_BAR_Y + CAP_BAR_H + 2;
        // Lo imbuido en DOS columnas: con la barra y la pista no caben cinco filas.
        ImbuedAttributes imb = piece.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE);
        // Dos columnas solo si el texto más ancho cabe dos veces; si no («+100 ESP» en es_mx) una sola, sin recortar nada.
        int widest = 0;
        for (ZenkaiAttributes a : ImbuedAttributes.IMBUABLE) {
            if (imb.get(a) > 0) widest = Math.max(widest, font.width(attrGain(a, imb.get(a))));
        }
        int cols = 2 * widest + 4 <= INFO_W ? 2 : 1;
        int col = 0, colW = INFO_W - widest;   // la 2.ª columna pegada al borde derecho del panel
        boolean any = false;
        for (ZenkaiAttributes a : ImbuedAttributes.IMBUABLE) {
            int v = imb.get(a);
            if (v <= 0) continue;
            any = true;
            PanelText.onDark(g, font, attrGain(a, v), x + (cols == 2 ? col * colW : 0), y, ZenkaiTechPalette.OK_ON_SCREEN);
            if (++col == cols) { col = 0; y += INFO_LH; }
        }
        if (col != 0) y += INFO_LH;
        if (!any) {
            PanelText.onDark(g, font, piece.isEmpty() ? Component.literal("—")
                    : Component.translatable("screen.zenkai.armor_fabricator.nothing"), x, y, ZenkaiTechPalette.DIM_ON_SCREEN);
            y += INFO_LH;
        }
        // Con la sangre puesta pasándose del tope, el aviso rojo va JUSTO debajo de lo imbuido y sustituye a la pista gris
        // (dice lo mismo): antes se pintaba en ATTR_COST_Y bajo la pista y en es_mx se salía del panel (captura 2026-11-23).
        ImbuingStationBlockEntity.ImbuePreview pv = hasTemplate() ? null : preview();
        if (pv != null && pv.overCap()) {
            for (var line : font.split(Component.translatable("screen.zenkai.armor_fabricator.imbue.over_cap"), INFO_W)) {
                if (y + INFO_LH > INFO_BOTTOM) break;
                g.drawString(font, line, x, y, ZenkaiTechPalette.DENIED_ON_SCREEN, true);
                y += INFO_LH;
            }
            return;
        }
        // «Infunde la pieza para subir el tope» mientras infundir aún lo subiría.
        if (hasPiece && KiInfusion.capRaisable(piece)) {
            for (var line : font.split(Component.translatable("screen.zenkai.armor_fabricator.cap.raise"), INFO_W)) {
                if (y + INFO_LH > ATTR_COST_Y) break;
                g.drawString(font, line, x, y, ZenkaiTechPalette.DIM_ON_SCREEN, true);
                y += INFO_LH;
            }
        }

        y = ATTR_COST_Y;
        Cost c = cost();
        if (c == null) {
            // Sin coste que enseñar: el motivo de la plantilla si hay una, si no la pista de qué poner.
            AttributeTemplates.Plan plan = templatePlan();
            Component hint = plan != null ? Component.translatable(plan.problem().langKey())
                    : Component.translatable(hasTemplate() ? "screen.zenkai.armor_fabricator.template.no_piece"
                    : "screen.zenkai.armor_fabricator.imbue.hint");
            int color = plan != null && plan.problem() != AttributeTemplates.Problem.NO_PIECE
                    ? ZenkaiTechPalette.DENIED_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN;
            for (var line : font.split(hint, INFO_W)) {
                g.drawString(font, line, x, y, color, true);
                y += INFO_LH;
            }
            return;
        }
        if (hasTemplate()) {
            AttributeTemplates.Kind kind = AttributeTemplates.kindOf(menu.machineStack(ImbuingStationBlockEntity.SLOT_TEMPLATE));
            infoRow(g, x, y, "screen.zenkai.armor_fabricator.template.action",
                    Component.translatable("screen.zenkai.armor_fabricator.template.kind." + kind.id()),
                    ZenkaiTechPalette.OK_ON_SCREEN);
        } else {
            ImbuingStationBlockEntity.ImbuePreview p = preview();
            infoRow(g, x, y, "screen.zenkai.armor_fabricator.imbue.gain", attrGain(p.attribute(), p.points()),
                    ZenkaiTechPalette.OK_ON_SCREEN);
        }
        y += INFO_LH;
        boolean feOk = menu.energy() >= c.fe();
        infoRow(g, x, y, "screen.zenkai.armor_fabricator.cost",
                Component.literal(ZenkaiNumbers.screen(c.fe()) + " FE"),
                feOk ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        y += INFO_LH;
        if (c.fluid() == null) return;   // reiniciar y transferir no gastan fluido
        // Con un tipo válido en el tanque (el pedido o uno superior) se muestra ESE tipo y lo que gasta; si no, el pedido.
        KiEnergyType inTank = menu.tankType();
        long mb = c.mbWith().apply(inTank);
        KiEnergyType shown = mb >= 0 ? inTank : c.fluid();
        long shownMb = mb >= 0 ? mb : c.mb();
        boolean fluidOk = mb >= 0 && menu.tankAmount() >= mb;
        // El color del tipo va en la ETIQUETA: el Ki Divino es rojo y en la cifra se confundía con «falta» (captura 2026-11-22).
        infoRow(g, x, y, shown.langKey(), shown.argb(), Component.literal(ZenkaiNumbers.screen(shownMb) + " mB"),
                fluidOk ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
    }

    /** Pestaña Infundir: estado y, con lingotes, nivel, lingotes, FE, fluido, tope y durabilidad que tendrá la pieza. */
    private void renderInfuse(GuiGraphics g) {
        drawBar(g, CHARGE_X, CHARGE_Y, CHARGE_W, CHARGE_H, menu.infuseFraction());
        int x = ATTR_INFO_X, y = ATTR_INFO_Y;
        ItemStack piece = menu.machineStack(ImbuingStationBlockEntity.SLOT_INFUSE_PIECE);
        ImbuingStationBlockEntity.InfusePreview p = infusePreview();
        KiEnergyType inTank = menu.tankType();
        Component status;
        int color;
        if (p == null) {
            status = Component.translatable("screen.zenkai.armor_fabricator.infuse.hint");
            color = ZenkaiTechPalette.DIM_ON_SCREEN;
        } else if (menu.infusing()) {
            status = Component.translatable("screen.zenkai.armor_fabricator.infuse.working");
            color = ZenkaiTechPalette.OK_ON_SCREEN;
        } else if (!p.ok()) {
            status = Component.translatable(p.problem().langKey());
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else if (!menu.tolerates(p.level())) {
            status = Component.translatable("screen.zenkai.armor_fabricator.imbue.no_tolerance");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else {
            status = Component.translatable("screen.zenkai.armor_fabricator.infuse.ready");
            color = ZenkaiTechPalette.OK_ON_SCREEN;
        }
        for (var line : font.split(status, INFO_W)) {
            g.drawString(font, line, x, y, color, true);
            y += INFO_LH;
        }
        y += 2;
        if (p == null || p.level() == null) return;
        KiEnergyType lvl = p.level();
        infoRow(g, x, y, lvl.langKey(), lvl.argb(),
                Component.literal(ZenkaiNumbers.screen((long) p.have()) + " / " + ZenkaiNumbers.screen((long) p.ingots())),
                p.have() >= p.ingots() ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        y += INFO_LH;
        infoRow(g, x, y, "screen.zenkai.armor_fabricator.cost", Component.literal(ZenkaiNumbers.screen(p.fe()) + " FE"),
                menu.energy() >= p.fe() ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        y += INFO_LH;
        boolean fluidOk = inTank == lvl && menu.tankAmount() >= menu.fluidCost(p.mb());
        infoRow(g, x, y, "screen.zenkai.armor_fabricator.infuse.fluid", lvl.argb(),
                Component.literal(ZenkaiNumbers.screen(menu.fluidCost(p.mb())) + " mB"),
                fluidOk ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        y += INFO_LH + 2;
        // Lo que gana la pieza: tope de puntos (con la regla del más bajo) y durabilidad.
        ItemStack after = KiInfusion.applyToArmor(piece, lvl);
        infoRow(g, x, y, "screen.zenkai.armor_fabricator.infuse.cap",
                Component.literal(ZenkaiNumbers.screen((long) KiInfusion.pieceCap(piece)) + " → "
                        + ZenkaiNumbers.screen((long) KiInfusion.pieceCap(after))), ZenkaiTechPalette.OK_ON_SCREEN);
        y += INFO_LH;
        if (piece.getMaxDamage() > 0) {
            infoRow(g, x, y, "screen.zenkai.armor_fabricator.infuse.durability",
                    Component.literal(ZenkaiNumbers.screen((long) piece.getMaxDamage()) + " → "
                            + ZenkaiNumbers.screen((long) after.getMaxDamage())), ZenkaiTechPalette.OK_ON_SCREEN);
        }
    }

    /** «+5 FUE» sin el espacio inicial que lleva la clave del tooltip. */
    private static Component attrGain(ZenkaiAttributes a, int points) {
        return Component.literal(Component.translatable("tooltip.zenkai.imbued." + key(a),
                ZenkaiNumbers.screen((long) points)).getString().trim());
    }

    private static String key(ZenkaiAttributes a) {
        return switch (a) {
            case STRENGTH -> "str";
            case CONSTITUTION -> "con";
            case DEXTERITY -> "dex";
            case WILLPOWER -> "wil";
            case SPIRIT -> "spi";
            case MIND -> "mnd";
        };
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        int lmx = localMouseX(mouseX), lmy = localMouseY(mouseY);
        if (machineTooltip(g, mouseX, mouseY)) return;
        if (menu.tab() == ImbuingStationMenu.TAB_ATTR && kiTooltip(g, mouseX, mouseY, lmx, lmy)) return;
    }

    /** Tooltip de la fila de Ki de Atributos: tipo pedido y lo que gasta cada tipo que vale. */
    private boolean kiTooltip(GuiGraphics g, int mouseX, int mouseY, int lmx, int lmy) {
        int rowY = ATTR_COST_Y + 2 * INFO_LH;
        if (lmx < ATTR_INFO_X || lmx >= INFO_RIGHT || lmy < rowY - 1 || lmy >= rowY + INFO_LH - 1) return false;
        Cost p = cost();
        if (p == null || p.fluid() == null) return false;
        KiEnergyType req = p.fluid();
        List<TipLine> lines = new ArrayList<>();
        lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.ki.requires",
                Component.translatable(req.langKey()).withStyle(st -> st.withColor(req.argb() & 0xFFFFFF)))
                .withStyle(ChatFormatting.BOLD)));
        // Pieza infundida: el tipo sale de su infusión y el coste lleva su descuento (Fase 5c).
        ItemStack payer = menu.machineStack(hasTemplate() && AttributeTemplates.kindOf(
                menu.machineStack(ImbuingStationBlockEntity.SLOT_TEMPLATE)) == AttributeTemplates.Kind.COPY
                ? ImbuingStationBlockEntity.SLOT_TARGET : ImbuingStationBlockEntity.SLOT_ARMOR);
        KiEnergyType inf = KiInfusion.of(payer);
        if (inf != null) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.ki.infused",
                    ZenkaiNumbers.fmt2(KiInfusion.imbueCostMult(inf))).withStyle(ChatFormatting.GRAY)));
        }
        for (KiEnergyType t : KiEnergyType.values()) {
            long mb = p.mbWith().apply(t);
            if (mb < 0) continue;
            lines.add(TipLine.of(Component.translatable(t.langKey()).withStyle(st -> st.withColor(t.argb() & 0xFFFFFF))
                    .append(Component.literal(": " + ZenkaiNumbers.screen(mb) + " mB").withStyle(ChatFormatting.GRAY))));
        }
        lines.add(TipLine.of(Component.translatable("screen.zenkai.armor_fabricator.ki.higher").withStyle(ChatFormatting.GRAY)));
        drawTooltip(g, mouseX, mouseY, lines);
        return true;
    }


    @Override
    protected @Nullable ResourceLocation workGhost(int idx) {
        if (idx == ImbuingStationBlockEntity.SLOT_ARMOR || idx == ImbuingStationBlockEntity.SLOT_TARGET
                || idx == ImbuingStationBlockEntity.SLOT_INFUSE_PIECE) return ResourceLocation.withDefaultNamespace("textures/item/empty_armor_slot_chestplate.png");
        if (idx == ImbuingStationBlockEntity.SLOT_BLOOD) return ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "textures/slot/empty_blood.png");
        if (idx == ImbuingStationBlockEntity.SLOT_TEMPLATE) return ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "textures/slot/empty_template.png");
        if (idx == ImbuingStationBlockEntity.SLOT_INFUSE_INGOTS) return ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "textures/slot/empty_ingot.png");
        return null;
    }

    @Override
    protected @Nullable String slotKey(int idx) {
        if (idx < 0) return null;
        if (idx == ImbuingStationBlockEntity.SLOT_ARMOR) return "screen.zenkai.armor_fabricator.slot.armor";
        if (idx == ImbuingStationBlockEntity.SLOT_BLOOD) return "screen.zenkai.armor_fabricator.slot.blood";
        if (idx == ImbuingStationBlockEntity.SLOT_TEMPLATE) return "screen.zenkai.armor_fabricator.slot.template";
        if (idx == ImbuingStationBlockEntity.SLOT_TARGET) return "screen.zenkai.armor_fabricator.slot.target";
        if (idx == ImbuingStationBlockEntity.SLOT_INFUSE_PIECE) return "screen.zenkai.armor_fabricator.slot.infuse_piece";
        if (idx == ImbuingStationBlockEntity.SLOT_INFUSE_INGOTS) return "screen.zenkai.armor_fabricator.slot.infuse_ingots";
        return null;
    }
}
