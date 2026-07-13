package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.core.network.feature.ZenkaiAttributes;
import com.hmc.zenkai.core.network.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.core.skills.SkillDef;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * Pestaña Habilidades: lista de SkillDef con compra (validada en servidor).
 * Botones TextOnlyButton; dropshadow activo.
 */
public class SkillsScreen extends ZenkaiMenuScreen {

    private static final int ROW_H = 30;

    public SkillsScreen() {
        super(Component.translatable(ZenkaiTab.SKILLS.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.SKILLS; }

    @Override
    protected void initContent() {
        int y = panelTop + 58;
        for (SkillDef def : SkillDef.all()) {
            boolean owned = att != null && att.skills().has(def.id());
            if (!owned) {
                boolean canBuy = att != null
                        && att.getAttribute(ZenkaiAttributes.MIND) >= def.mindReq()
                        && att.getTP() >= def.tpCost();
                TextOnlyButton buy = new TextOnlyButton(panelLeft + BG_W - 66, y, 54, 16,
                        Component.translatable("screen.zenkai.skills.buy"),
                        () -> {
                            PacketDistributor.sendToServer(new SkillBuyPacket(def.id()));
                            this.rebuildWidgets();
                        });
                buy.setTooltip(Tooltip.create(Component.translatable(def.descKey())));
                buy.active = canBuy;
                this.addRenderableWidget(buy);
            }
            y += ROW_H;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(this.font, this.title, panelLeft + 16, panelTop + 24, 0xFFFFFFFF, true);
        if (att != null) {
            g.drawString(this.font, Component.literal("TP: " + att.getTP()),
                    panelLeft + 16, panelTop + 38, 0xFFFFD700, true);
        }
        if (SkillDef.all().isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("screen.zenkai.skills.empty"),
                    panelLeft + BG_W / 2, panelTop + BG_H / 2 - 4, 0xFFAAAAAA);
            return;
        }
        int y = panelTop + 58;
        for (SkillDef def : SkillDef.all()) {
            boolean owned = att != null && att.skills().has(def.id());
            boolean mindOk = att != null && att.getAttribute(ZenkaiAttributes.MIND) >= def.mindReq();
            g.drawString(this.font, Component.translatable(def.nameKey()),
                    panelLeft + 16, y, owned ? 0xFF7CFC7C : 0xFFFFFFFF, true);
            if (owned) {
                g.drawString(this.font, Component.translatable("screen.zenkai.skills.owned"),
                        panelLeft + 16, y + 11, 0xFF7CFC7C, true);
            } else {
                g.drawString(this.font, Component.translatable("screen.zenkai.skills.cost",
                                def.tpCost(), def.mindReq()),
                        panelLeft + 16, y + 11, mindOk ? 0xFFAAAAAA : 0xFFFF5555, true);
            }
            y += ROW_H;
        }
    }
}