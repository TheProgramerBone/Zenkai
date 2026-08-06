package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Tienda de un maestro. Pantalla PROPIA, no una pestaña de ZenkaiMenuScreen: es apaisada
 * (el maestro ocupa el tercio izquierdo) y no comparte navegación con el menú del jugador.
 * Solo se listan las habilidades cuyo campo "master" es este maestro. Estado por fila:
 *   - nivel 0 y puedes pagar  -> coste en blanco, clic compra
 *   - nivel 0 y no puedes     -> coste en ROJO, clic no hace nada
 *   - nivel >= 1              -> gris, "Aprendida" (el maestro solo da el nivel 1; los
 *                                siguientes se suben con TP desde la pantalla de skills)
 * El panel es dibujado, no una textura: así no bloquea a que exista el asset. Cambiar a
 * blit() es una línea cuando tengas el fondo.
 */
public class MasterScreen extends Screen {

    private static final int BG_W = 320;
    private static final int BG_H = 180;

    /** Ancho del panel del maestro (izquierda). El resto es la lista. */
    private static final int PORTRAIT_W = 108;
    private static final int PADDING    = 10;
    private static final int ROW_H      = 22;
    /** Separación bajo el título antes de la primera fila. */
    private static final int CONTENT_TOP = 30;

    private static final int COL_BG      = 0xF0100D14;
    private static final int COL_BORDER  = 0xFF3B3550;
    private static final int COL_PANEL   = 0x40000000;
    private static final int COL_TITLE   = 0xFFFFD966;
    private static final int COL_NAME    = 0xFFFFFFFF;
    private static final int COL_LEARNED = 0xFF808080;
    private static final int COL_COST_OK = 0xFFB9E36C;
    private static final int COL_COST_NO = 0xFFE05A5A;
    private static final int COL_HINT    = 0xFF9A93AD;

    private final String masterId;
    private final int entityId;

    private final List<SkillDef> rows = new ArrayList<>();
    private int scroll = 0;

    private int left, top;

    public MasterScreen(String masterId, int entityId) {
        super(Component.translatable("master.zenkai." + masterId));
        this.masterId = masterId;
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        left = (this.width - BG_W) / 2;
        top  = (this.height - BG_H) / 2;

        rows.clear();
        rows.addAll(SkillDef.taughtBy(masterId));
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    // ── Geometría ────────────────────────────────────────────────────────────

    private int listLeft()   { return left + PORTRAIT_W + PADDING; }
    private int listRight()  { return left + BG_W - PADDING; }
    private int listTop()    { return top + CONTENT_TOP; }
    private int listHeight() { return BG_H - CONTENT_TOP - PADDING; }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }
    private int maxScroll()  { return Math.max(0, rows.size() - visibleRows()); }
    private int rowTop(int i){ return listTop() + (i - scroll) * ROW_H; }

    private boolean onScreen(int i) {
        int rel = i - scroll;
        return rel >= 0 && rel < visibleRows();
    }

    // ── Estado de una fila ───────────────────────────────────────────────────

    private PlayerStatsAttachment stats() {
        return minecraft == null || minecraft.player == null ? null
                : minecraft.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
    }

    /** ¿Le alcanza para el nivel 1? Mismo criterio que valida el servidor: TP y MIND libre. */
    private boolean canAfford(PlayerStatsAttachment st, SkillDef def) {
        if (st == null) return false;
        return st.getTP() >= def.tpCost() && st.mindFree() >= def.mindReqFor(1);
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        g.fill(left, top, left + BG_W, top + BG_H, COL_BG);
        g.renderOutline(left, top, BG_W, BG_H, COL_BORDER);
        g.fill(left + PADDING / 2, top + PADDING / 2,
                left + PORTRAIT_W, top + BG_H - PADDING / 2, COL_PANEL);

        renderMaster(g, mouseX, mouseY);

        g.drawString(this.font, this.title, listLeft(), top + PADDING, COL_TITLE, true);

        PlayerStatsAttachment st = stats();
        if (st != null) {
            Component tp = Component.translatable("screen.zenkai.master.tp", st.getTP(), st.mindFree());
            g.drawString(this.font, tp, listLeft(), top + PADDING + 11, COL_HINT, true);
        }

        if (rows.isEmpty()) {
            g.drawString(this.font, Component.translatable("screen.zenkai.master.nothing"),
                    listLeft(), listTop(), COL_HINT, true);
        }

        for (int i = 0; i < rows.size(); i++) {
            if (!onScreen(i)) continue;
            renderRow(g, st, rows.get(i), rowTop(i), mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphics g, PlayerStatsAttachment st, SkillDef def, int y,
                           int mouseX, int mouseY) {
        boolean learned = st != null && st.skills().level(def.id()) > 0;
        boolean hovered = !learned && mouseX >= listLeft() && mouseX <= listRight()
                && mouseY >= y && mouseY < y + ROW_H;

        if (hovered) g.fill(listLeft() - 2, y - 1, listRight(), y + ROW_H - 3, 0x30FFFFFF);

        Component name = Component.translatable(def.nameKey());
        g.drawString(this.font, name, listLeft(), y + 2,
                learned ? COL_LEARNED : COL_NAME, true);

        Component right;
        int color;
        if (learned) {
            right = Component.translatable("screen.zenkai.master.learned")
                    .withStyle(ChatFormatting.GRAY);
            color = COL_LEARNED;
        } else {
            boolean afford = canAfford(st, def);
            right = Component.translatable("screen.zenkai.master.cost",
                    def.tpCost(), def.mindReqFor(1));
            color = afford ? COL_COST_OK : COL_COST_NO;
        }
        g.drawString(this.font, right, listRight() - this.font.width(right), y + 2, color, true);

        // Descripción en pequeño bajo el nombre, recortada al ancho disponible.
        Component desc = Component.translatable(def.descKey());
        String cut = this.font.plainSubstrByWidth(desc.getString(), listRight() - listLeft());
        g.drawString(this.font, cut, listLeft(), y + 12, COL_HINT, false);
    }

    /**
     * El maestro real, rotando hacia el ratón. Si la entidad ya no está cargada (te alejaste,
     * el chunk se descargó) el panel se queda vacío en vez de petar: la pantalla sigue siendo
     * usable y el servidor rechazará la compra por distancia de todas formas.
     */
    private void renderMaster(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.level == null) return;
        Entity e = minecraft.level.getEntity(entityId);
        if (!(e instanceof LivingEntity le)) return;

        int cx = left + PORTRAIT_W / 2;
        int cyTop = top + PADDING;
        int cyBot = top + BG_H - PADDING;

        InventoryScreen.renderEntityInInventoryFollowsMouse(          // ⚠ firma
                g,
                left + PADDING / 2, cyTop, left + PORTRAIT_W, cyBot,
                26,                    // escala; ajústala al alto real del geo
                0.0625F,
                mouseX, mouseY,
                le);

        Component name = le.getDisplayName();
        g.drawCenteredString(this.font, name, cx, cyBot - 10, COL_NAME);
    }

    // ── Entrada ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            PlayerStatsAttachment st = stats();
            for (int i = 0; i < rows.size(); i++) {
                if (!onScreen(i)) continue;
                int y = rowTop(i);
                if (mouseX < listLeft() - 2 || mouseX > listRight()) continue;
                if (mouseY < y - 1 || mouseY >= y + ROW_H - 3) continue;

                SkillDef def = rows.get(i);
                if (st != null && st.skills().level(def.id()) > 0) return true;  // ya aprendida
                if (!canAfford(st, def)) return true;                            // sin fondos

                PacketDistributor.sendToServer(new SkillBuyPacket(def.id(), masterId));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (maxScroll() > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}