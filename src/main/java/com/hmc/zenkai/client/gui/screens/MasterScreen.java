package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
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

    // ═══ PALETA UNIFICADA ═══
    //
    // Esta pantalla tenía paleta propia —fondo 0xF0100D14, borde 0xFF3B3550, pistas
    // 0xFF9A93AD— que no aparecía en ningún otro sitio del mod: los maestros parecían de otro
    // mod. Era deuda visual, no una decisión. Ahora el fondo oscuro se mantiene (esta pantalla
    // sí es un diálogo sobre el mundo, no un panel beige) pero los colores de ESTADO salen de
    // la paleta común, que es lo que hace que "no te llega el TP" se vea igual aquí que en la
    // pestaña de habilidades.
    //
    // Fondo y marco: el mismo lenguaje de tres anillos del popup lateral de la ficha.
    private static final int COL_BG      = ZenkaiPalette.POPUP_BG;
    private static final int COL_PANEL   = ZenkaiPalette.BAR_BG_DARK;

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

        // Marco de tres anillos, como el popup lateral de la ficha y el retrato del jugador:
        // es lo que ata visualmente esta pantalla al resto del mod sin renunciar al fondo
        // oscuro, que aquí sí hace falta porque flota sobre el mundo.
        g.fill(left - 2, top - 2, left + BG_W + 2, top + BG_H + 2, ZenkaiPalette.BORDER_IN);
        g.fill(left - 1, top - 1, left + BG_W + 1, top + BG_H + 1, ZenkaiPalette.BORDER_MID);
        g.fill(left, top, left + BG_W, top + BG_H, COL_BG);
        g.fill(left + PADDING / 2, top + PADDING / 2,
                left + PORTRAIT_W, top + BG_H - PADDING / 2, COL_PANEL);

        renderMaster(g, mouseX, mouseY);

        PanelText.onDark(g, this.font, ScreenTitle.styled(this.title), listLeft(), top + PADDING,
                ZenkaiPalette.GOLD);

        PlayerStatsAttachment st = stats();
        if (st != null) {
            // TP y MIND separados y con SUS colores de rol, igual que en SkillsScreen: aquí
            // salían los dos en el mismo gris malva y había que leerlos para distinguirlos.
            Component tp = Component.translatable("screen.zenkai.skills.tp", st.getTP());
            PanelText.onDark(g, this.font, tp, listLeft(), top + PADDING + 11,
                    ZenkaiPalette.TP_ON_DARK);
            int free = st.mindFree();
            PanelText.onDark(g, this.font,
                    Component.translatable("screen.zenkai.skills.mind",
                            free, st.getAttribute(com.hmc.zenkai.feature.ZenkaiAttributes.MIND)),
                    listLeft() + this.font.width(tp) + 10, top + PADDING + 11,
                    free < 0 ? ZenkaiPalette.ERROR : ZenkaiPalette.MIND_ON_DARK);
        }

        if (rows.isEmpty()) {
            PanelText.onDark(g, this.font, Component.translatable("screen.zenkai.master.nothing"),
                    listLeft(), listTop(), ZenkaiPalette.TEXT_OFF);
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

        if (hovered) {
            g.fill(listLeft() - 2, y - 1, listRight(), y + ROW_H - 3, ZenkaiPalette.HOVER_VEIL);
        }

        Component name = Component.translatable(def.nameKey());
        // Aprendida = apagada; disponible = verde de "algo tuyo", el MISMO que usa la pestaña
        // de habilidades para lo mismo.
        PanelText.onDark(g, this.font, name, listLeft(), y + 2,
                learned ? ZenkaiPalette.TEXT_OFF : ZenkaiPalette.OK);

        Component right;
        int color;
        if (learned) {
            right = Component.translatable("screen.zenkai.master.learned");
            color = ZenkaiPalette.TEXT_OFF;
        } else {
            right = Component.translatable("screen.zenkai.master.cost",
                    def.tpCost(), def.mindReqFor(1));
            color = canAfford(st, def) ? ZenkaiPalette.TP_ON_DARK : ZenkaiPalette.DENIED;
        }
        PanelText.rightOnDark(g, this.font, right, listRight(), y + 2, color);

        // Descripción en pequeño bajo el nombre, recortada al ancho disponible.
        Component desc = PanelText.fit(this.font, Component.translatable(def.descKey()),
                listRight() - listLeft());
        PanelText.onDark(g, this.font, desc, listLeft(), y + 12, ZenkaiPalette.TEXT_DIM);
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

        PanelText.centeredOnDark(g, this.font, le.getDisplayName(), cx, cyBot - 10,
                ZenkaiPalette.TEXT);
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