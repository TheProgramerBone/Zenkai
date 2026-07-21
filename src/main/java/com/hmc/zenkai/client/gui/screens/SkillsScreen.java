package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.core.network.feature.ZenkaiAttributes;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.skills.SkillDef;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña Habilidades: SOLO las que el jugador ya tiene (el nivel 1 lo dan maestros o
 * /zenkai skill give). Muestra nivel actual, coste del siguiente y un botón + para subirlo.
 * El botón se resuelve por hit-testing en mouseClicked, no con widgets: la lista llega por
 * sync y cambia sola, así no hay que reconstruir nada al comprar.
 */
public class SkillsScreen extends ZenkaiMenuScreen {

    private static final int ROW_H = 34;
    private static final int PLUS_SIZE = 12; // tamaño de PlusIconButton

    private final List<String> rowIds = new ArrayList<>();
    private final List<PlusIconButton> plusButtons = new ArrayList<>();

    public SkillsScreen() {
        super(Component.translatable(ZenkaiTab.SKILLS.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.SKILLS; }

    @Override
    protected void initContent() {
        rowIds.clear();
        plusButtons.clear();
        PlayerStatsAttachment st = stats();
        if (st == null) return;

        rowIds.addAll(st.skills().all());
        for (int i = 0; i < rowIds.size(); i++) {
            final String id = rowIds.get(i);
            PlusIconButton b = new PlusIconButton(plusX(), rowTop(i) + 2, () -> buy(id));
            plusButtons.add(b);
            addRenderableWidget(b);
        }
    }

    private void buy(String id) {
        PlayerStatsAttachment st = stats();
        SkillDef def = SkillDef.get(id);
        if (st == null || def == null || !def.purchasable()) return;
        int lvl = st.skills().level(id);
        if (lvl >= def.maxLevel() || !canAfford(st, def, lvl)) return;
        PacketDistributor.sendToServer(new SkillBuyPacket(id));
    }

    /** Attachment fresco: tras comprar, el server sincroniza y esto refleja el cambio solo. */
    private PlayerStatsAttachment stats() {
        return mc.player == null ? att : mc.player.getData(DataAttachments.PLAYER_STATS.get());
    }

    private int plusX() { return panelLeft + BG_W - 16 - PLUS_SIZE; }
    private int rowTop(int index) { return panelTop + 58 + index * ROW_H; }

    /** ¿Se puede pagar el siguiente nivel de esta habilidad? */
    private static boolean canAfford(PlayerStatsAttachment st, SkillDef def, int currentLevel) {
        return st.getTP() >= def.tpCost()
                && st.getAttribute(ZenkaiAttributes.MIND) >= def.mindReqFor(currentLevel + 1);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(this.font, this.title, panelLeft + 16, panelTop + 24, 0xFFFFFFFF, true);

        PlayerStatsAttachment st = stats();
        if (st == null) return;

        g.drawString(this.font,
                Component.translatable("screen.zenkai.skills.resources",
                        st.getTP(), st.getAttribute(ZenkaiAttributes.MIND)),
                panelLeft + 16, panelTop + 40, 0xFFFFD966, true);

        List<String> ids = new ArrayList<>(st.skills().all());
        if (ids.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("screen.zenkai.skills.empty"),
                    panelLeft + BG_W / 2, panelTop + BG_H / 2 - 4, 0xFFAAAAAA);
            return;
        }

        // Llegó una habilidad nueva por sync: hay que crear su botón.
        if (!rowIds.equals(ids)) {
            rebuildWidgets();
            return;
        }

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            SkillDef def = SkillDef.get(id);
            int y = rowTop(i);
            int lvl = st.skills().level(id);
            PlusIconButton plus = plusButtons.get(i);

            Component name = def != null
                    ? Component.translatable(def.nameKey())
                    : Component.literal(id); // huérfana: el registro cambió, se muestra el id
            if (def != null && def.maxLevel() > 1) {
                name = Component.literal("").append(name).append(
                        Component.literal("  " + lvl + "/" + def.maxLevel()));
            }
            g.drawString(this.font, name, panelLeft + 16, y, 0xFF7CFC7C, true);

            if (def == null) {
                plus.visible = false;
                continue;
            }
            g.drawString(this.font, Component.translatable(def.descKey()),
                    panelLeft + 16, y + 11, 0xFFAAAAAA, true);

            boolean canLevel = def.purchasable() && lvl < def.maxLevel();
            plus.visible = canLevel;
            plus.active = canLevel && canAfford(st, def, lvl);

            if (!canLevel) {
                if (lvl >= def.maxLevel()) {
                    g.drawString(this.font, Component.translatable("screen.zenkai.skills.maxed"),
                            panelLeft + 16, y + 22, 0xFF7CFC7C, true);
                }
                continue;
            }
            g.drawString(this.font,
                    Component.translatable("screen.zenkai.skills.cost",
                            def.tpCost(), def.mindReqFor(lvl + 1)),
                    panelLeft + 16, y + 22, plus.active ? 0xFFFFD966 : 0xFFCC6666, true);
        }
    }
}