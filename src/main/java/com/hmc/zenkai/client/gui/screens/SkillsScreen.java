package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.core.skills.SkillDef;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Pestaña Habilidades: SOLO las que el jugador ya tiene (se va llenando conforme las
 * consigue vía maestros/comando o compras futuras). Sin catálogo ni botón de compra.
 * Dropshadow activo.
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
        // Sin widgets: la pestaña es solo lectura (las habilidades llegan por sync).
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(this.font, this.title, panelLeft + 16, panelTop + 24, 0xFFFFFFFF, true);

        if (att == null || att.skills().all().isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("screen.zenkai.skills.empty"),
                    panelLeft + BG_W / 2, panelTop + BG_H / 2 - 4, 0xFFAAAAAA);
            return;
        }

        int y = panelTop + 58;
        for (String id : att.skills().all()) {
            SkillDef def = SkillDef.get(id);
            Component name = def != null
                    ? Component.translatable(def.nameKey())
                    : Component.literal(id); // huérfana: registro cambió, se muestra el id
            g.drawString(this.font, name, panelLeft + 16, y, 0xFF7CFC7C, true);
            if (def != null) {
                g.drawString(this.font, Component.translatable(def.descKey()),
                        panelLeft + 16, y + 11, 0xFFAAAAAA, true);
            }
            y += ROW_H;
        }
    }
}