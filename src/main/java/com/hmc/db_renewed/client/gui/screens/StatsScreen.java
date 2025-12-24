package com.hmc.db_renewed.client.gui.screens;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.client.gui.button.PlusIconButton;
import com.hmc.db_renewed.core.config.StatsConfig;
import com.hmc.db_renewed.core.network.feature.Dbrattributes;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.core.network.feature.stats.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class StatsScreen extends Screen {

    private static final int PAD = 8;
    private final Minecraft mc = Minecraft.getInstance();
    private PlayerStatsAttachment att;

    // Fondo del menú de stats
    private static final ResourceLocation BG_TEX =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/gui/stats_bg.png");

    // Tamaño del panel (ajusta a tu textura)
    private static final int BG_W = 256;
    private static final int BG_H = 228;

    // posición calculada en init()
    private int panelLeft;
    private int panelTop;

    // Orden de atributos
    private static final List<Dbrattributes> ORDER = List.of(
            Dbrattributes.STRENGTH, Dbrattributes.DEXTERITY, Dbrattributes.CONSTITUTION,
            Dbrattributes.WILLPOWER, Dbrattributes.MIND, Dbrattributes.SPIRIT
    );

    // ================= TP MULTIPLIER =================

    private static final int[] TP_STEPS = {1, 10, 100, 1000};
    private int tpStepIndex = 0; // por defecto x1

    // área de texto "TPC: xN" para tooltip / posición
    private int tpcLabelX, tpcLabelY, tpcLabelW, tpcLabelH;

    private int getCurrentTpStep() {
        return TP_STEPS[tpStepIndex];
    }

    /** Avanza al siguiente multiplicador: x1 → x10 → x100 → x1000 → x1... */
    private void cycleTpStep() {
        tpStepIndex = (tpStepIndex + 1) % TP_STEPS.length;
    }

    // ================= AREAS PARA TOOLTIP DE ATRIBUTOS =================

    private record AttrArea(Dbrattributes attr, int x, int y, int w, int h) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<AttrArea> attrAreas = new ArrayList<>();

    public StatsScreen() {
        super(Component.translatable("screen.db_renewed.stats_screen.title"));
    }

    @Override
    protected void init() {
        if (mc.player != null) {
            att = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        }
        this.clearWidgets();

        this.panelLeft = (this.width  - BG_W) / 2;
        this.panelTop  = (this.height - BG_H) / 2;

        int x = panelLeft + 16;
        int y = panelTop  + 60;

        // Botón "+" por atributo (usa el multiplicador actual)
        for (Dbrattributes a : ORDER) {
            final String name = a.name();

            this.addRenderableWidget(new PlusIconButton(
                    x + 60,         // X
                    y + 15,        // Y
                    () -> spend(name, getCurrentTpStep())  // acción al click
            ));

            y += 18;
        }

        // --- Texto TPC + botón único de multiplicador de TP ---
        Font font = this.font;
        String maxText = "TPC: x1000";   // texto más largo posible
        tpcLabelX = panelLeft + 12;
        tpcLabelY = panelTop + BG_H - 30;
        tpcLabelW = font.width(maxText);
        tpcLabelH = font.lineHeight;

        int btnX = tpcLabelX + tpcLabelW + 4;
        int btnY = tpcLabelY - 2;

        PlusIconButton tpStepButton = new PlusIconButton(
                btnX + 6,
                btnY,
                this::cycleTpStep
        );
        this.addRenderableWidget(tpStepButton);
    }

    private void spend(String attrName, int points) {
        PacketDistributor.sendToServer(new SpendTpPacket(attrName, points));
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (mc.player == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        att = mc.player.getData(DataAttachments.PLAYER_STATS.get());

        super.render(g, mouseX, mouseY, partialTick);

        int left = panelLeft + 12;
        int top  = panelTop  + 16;

        Font font = this.font;

        g.drawString(font, this.title, left, top, 0xFFFFFF);

        int x = left;
        int y = top + 14;

        // ======= Cabecera Race / Style / TP =======
        g.drawString(font,
                Component.translatable("screen.db_renewed.stats_screen.race")
                        .append(Component.translatable(att.getRace().name()).withStyle(ChatFormatting.AQUA)),
                x, y, 0xFFFFFF);
        y += 10;
        g.drawString(font,
                Component.translatable("screen.db_renewed.stats_screen.style")
                        .append(Component.translatable(att.getStyle().name()).withStyle(ChatFormatting.AQUA)),
                x, y, 0xFFFFFF);
        y += 10;

        g.drawString(font,
                Component.translatable("screen.db_renewed.stats_screen.tp")
                        .append(Component.literal(String.valueOf(att.getTP())).withStyle(ChatFormatting.GOLD)),
                x, y, 0xFFFFFF);
        y += 12;

        // ======= Encabezados columnas =======
        g.drawString(font, Component.translatable("screen.db_renewed.stats_screen.attributes"), x, y, 0xFFD0D0);
        int statsXHeader = x + 130;
        g.drawString(font, Component.translatable("screen.db_renewed.stats_screen.stats"), statsXHeader, y, 0xFFD0D0);
        y += 10;

        // ======= Atributos (izquierda) =======
        attrAreas.clear();
        int ay = y;
        for (Dbrattributes a : ORDER) {
            int value = att.getAttribute(a);
            Component line = getAttributeLabel(a, value);

            int textX = x;
            int textY = ay + 5;
            g.drawString(font, line, textX, textY, 0xFFFFFF);

            int w = font.width(line);
            int h = font.lineHeight;
            attrAreas.add(new AttrArea(a, textX, textY, w, h));

            ay += 18;
        }

        int sx = statsXHeader;
        int sy = y;

        double melee   = att.computeMeleeFinal();
        double defense = att.computeDefenseFinal();
        double speed   = att.computeSpeedFinal();
        double fly     = att.computeFlyFinal();

        String body    = att.getBody() + "/" + att.getBodyMax();
        String stam    = att.getStamina() + "/" + att.getStaminaMax();
        String ki      = att.getEnergy() + "/" + att.getEnergyMax();

        double moveMult = Math.min(1.0 + (speed / 100.0) * StatsConfig.movementScaling(),
                StatsConfig.speedMultiplierCap());
        double flyMult  = Math.min(1.0 + (fly   / 100.0) * StatsConfig.flyScaling(),
                StatsConfig.flyMultiplierCap());

        int runningPct = (int) Math.round(moveMult * 100);
        int flyingPct  = (int) Math.round(flyMult  * 100);

        // 🔹 Nuevo: Ki Power final
        double kiPower = att.computeKiPowerFinal();

        List<Component> stats = new ArrayList<>();
        stats.add(Component.translatable("screen.db_renewed.stats_screen.stat.melee",
                String.format(java.util.Locale.ROOT, "%.1f", melee)));
        stats.add(Component.translatable("screen.db_renewed.stats_screen.stat.defense",
                String.format(java.util.Locale.ROOT, "%.1f", defense)));
        stats.add(Component.translatable("screen.db_renewed.stats_screen.stat.body", body));
        stats.add(Component.translatable("screen.db_renewed.stats_screen.stat.stamina", stam));
        stats.add(Component.translatable("screen.db_renewed.stats_screen.stat.ki", ki));

        stats.add(Component.translatable("screen.db_renewed.stats_screen.stat.ki_power",
                String.format(java.util.Locale.ROOT, "%.1f", kiPower)));

        stats.add(Component.translatable("screen.db_renewed.stats_screen.stat.running", runningPct));
        stats.add(Component.translatable("screen.db_renewed.stats_screen.stat.flying", flyingPct));

        for (Component c : stats) {
            g.drawString(font, c, sx, sy, 0xFFFFFF);
            sy += 10;
        }

        // ======= Texto TPC: xN =======
        Component tpcText = Component.translatable(
                "screen.db_renewed.stats_screen.tpx",
                getCurrentTpStep()
        );
        g.drawString(font, tpcText, tpcLabelX, tpcLabelY, 0xFFFFFF);

        // ======= Coste según el multiplicador actual (preview global) =======
        int cost = computeCurrentTpCost();
        int costY = tpcLabelY + font.lineHeight + 4; // un renglón debajo

        Component costText = Component.translatable(
                "screen.db_renewed.stats_screen.cost",
                cost
        );
        g.drawString(font, costText, tpcLabelX, costY, 0xFFFFFF);

        g.drawString(font,
                Component.translatable("screen.db_renewed.stats_screen.wip").withStyle(ChatFormatting.GRAY),
                panelLeft + 150,
                panelTop + BG_H - 12 - PAD,
                0xAAAAAA);

        // ======= Tooltips =======
        renderAttributeTooltip(g, mouseX, mouseY);
        renderTpStepTooltip(g, mouseX, mouseY);
    }

    /**
     * Calcula el coste de TP para el multiplicador actual,
     * tomando el coste mínimo entre todos los atributos disponibles.
     */
    private int computeCurrentTpCost() {
        if (att == null) return 0;
        int step = getCurrentTpStep();
        int best = Integer.MAX_VALUE;

        for (Dbrattributes a : ORDER) {
            int c = att.previewTpCost(a, step);
            if (c > 0 && c < best) {
                best = c;
            }
        }
        return (best == Integer.MAX_VALUE) ? 0 : best;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.blit(BG_TEX, panelLeft, panelTop, 0, 0, BG_W, BG_H);
    }

    /** Tooltip SOLO sobre el texto "TPC: xN", NO sobre el botón. */
    private void renderTpStepTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX >= tpcLabelX && mouseX < tpcLabelX + tpcLabelW &&
                mouseY >= tpcLabelY && mouseY < tpcLabelY + tpcLabelH) {

            Component tip = Component.translatable(
                    "screen.db_renewed.stats_screen.tp_des"
            );
            g.renderTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    /** Tooltip de ATRIBUTOS (STR/DEX/CON/WIL/SPI/MND) al pasar el mouse sobre el texto de la izquierda. */
    private void renderAttributeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (AttrArea area : attrAreas) {
            if (area.contains(mouseX, mouseY)) {
                Component tip = getAttributeDescription(area.attr(), att);
                g.renderTooltip(this.font, tip, mouseX, mouseY);
                break;
            }
        }
    }

    private Component getAttributeLabel(Dbrattributes attr, int value) {
        return switch (attr) {
            case STRENGTH     -> Component.translatable("attribute.db_renewed.str", value);
            case DEXTERITY    -> Component.translatable("attribute.db_renewed.dex", value);
            case CONSTITUTION -> Component.translatable("attribute.db_renewed.con", value);
            case WILLPOWER    -> Component.translatable("attribute.db_renewed.wil", value);
            case MIND         -> Component.translatable("attribute.db_renewed.mnd", value);
            case SPIRIT       -> Component.translatable("attribute.db_renewed.spi", value);
        };
    }

    /**
     * Texto de descripción por atributo (simplificado) con “+x por punto”,
     * usando los keys:
     *
     *  "tooltip.db_renewed.attr.str"
     *  "tooltip.db_renewed.attr.con"
     *  "tooltip.db_renewed.attr.dex"
     *  "tooltip.db_renewed.attr.wil"
     *  "tooltip.db_renewed.attr.spi"
     *  "tooltip.db_renewed.attr.mnd"
     */
    private Component getAttributeDescription(Dbrattributes attr, PlayerStatsAttachment att) {
        // Usamos SIEMPRE los multiplicadores de StatsConfig para evitar desincronizar
        double[] r = StatsConfig.raceMultipliers(att.getRace());   // [mSTR, mCON, mDEX, mWIL, mSPI, mMND]
        double[] s = StatsConfig.styleMultipliers(att.getStyle()); // [sSTR, sCON, sDEX, sWIL, sSPI, sMND]

        // Seguridad mínima por si algo raro pasa con la config
        double mSTR = (r.length > 0) ? r[0] : 1.0;
        double mCON = (r.length > 1) ? r[1] : 1.0;
        double mDEX = (r.length > 2) ? r[2] : 1.0;
        double mWIL = (r.length > 3) ? r[3] : 1.0;
        double mSPI = (r.length > 4) ? r[4] : 1.0;
        double mMND = (r.length > 5) ? r[5] : 1.0;

        double sSTR = (s.length > 0) ? s[0] : 1.0;
        double sCON = (s.length > 1) ? s[1] : 1.0;
        double sDEX = (s.length > 2) ? s[2] : 1.0;
        double sWIL = (s.length > 3) ? s[3] : 1.0;
        double sSPI = (s.length > 4) ? s[4] : 1.0;
        double sMND = (s.length > 5) ? s[5] : 1.0;

        double perPoint;
        String formatted;

        return switch (attr) {
            case STRENGTH -> {
                // 1 punto de STR → cuánto aporta al stat de melee
                perPoint = mSTR * sSTR;
                formatted = String.format(java.util.Locale.ROOT, "%.1f", perPoint);
                yield Component.translatable("tooltip.db_renewed.attr.str", formatted);
            }
            case CONSTITUTION -> {
                // CON en tu sistema afecta body/stamina, por eso sigues usando *2.0
                perPoint = mCON * sCON * 2.0;
                formatted = String.format(java.util.Locale.ROOT, "%.1f", perPoint);
                yield Component.translatable("tooltip.db_renewed.attr.con", formatted);
            }
            case DEXTERITY -> {
                // DEX → velocidad terrestre y de vuelo
                perPoint = mDEX * sDEX;
                formatted = String.format(java.util.Locale.ROOT, "%.1f", perPoint);
                yield Component.translatable("tooltip.db_renewed.attr.dex", formatted);
            }
            case WILLPOWER -> {
                // WIL → Ki Power
                perPoint = mWIL * sWIL;
                formatted = String.format(java.util.Locale.ROOT, "%.1f", perPoint);
                yield Component.translatable("tooltip.db_renewed.attr.wil", formatted);
            }
            case SPIRIT -> {
                // SPI → Ki Pool
                perPoint = mSPI * sSPI;
                formatted = String.format(java.util.Locale.ROOT, "%.1f", perPoint);
                yield Component.translatable("tooltip.db_renewed.attr.spi", formatted);
            }
            case MIND -> {
                // MND → de momento neutro, pero ya quedas sincronizado con config
                perPoint = mMND * sMND;
                formatted = String.format(java.util.Locale.ROOT, "%.1f", perPoint);
                yield Component.translatable("tooltip.db_renewed.attr.mnd", formatted);
            }
        };
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
