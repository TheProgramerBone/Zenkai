package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.screens.ZenkaiPanelScreen;
import com.hmc.zenkai.feature.wishes.ConfirmVillagerWishPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Deseo del aldeano bibliotecario con un libro encantado.
 * El problema real de esta pantalla no era estético: recorría el registro ENTERO de
 * encantamientos con dos flechas, una a una. Con vanilla ya son ~40 y con cualquier modpack se
 * pasa de 100 — llegar a "Fortuna" costaba cincuenta clics y no había forma de saber cuántos
 * quedaban. Ahora hay una caja de búsqueda que filtra por nombre traducido, las flechas se
 * mueven dentro del resultado filtrado y un contador dice dónde estás.
 * Se ordena por el nombre TRADUCIDO y no por el ResourceLocation: el jugador busca "Filo", no
 * "minecraft:sharpness", y con el orden por id los encantamientos del mismo mod salían juntos
 * pero alfabéticamente desordenados en pantalla.
 */
public class EnchantVillagerWishScreen extends ZenkaiPanelScreen {

    private static final int BOX_W = 150, BOX_H = 18;

    private List<Holder.Reference<Enchantment>> all = List.of();
    private List<Holder.Reference<Enchantment>> filtered = List.of();
    private int index = 0;

    private EditBox searchBox;
    private ArrowIconButton leftArrow, rightArrow;

    public EnchantVillagerWishScreen(Screen parent) {
        super(Component.translatable("screen.zenkai.wish.enchant_villager"), parent);
    }

    @Override protected int titleColor() { return ZenkaiPalette.SHENLONG; }

    @Override
    protected void initContent() {
        if (mc.level != null) {
            var reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            this.all = reg.listElements()
                    .sorted(Comparator.comparing(h -> h.value().description().getString()))
                    .toList();
        }
        this.filtered = all;

        int y = panelTop + CONTENT_TOP + 10;
        searchBox = new EditBox(this.font, centerX() - BOX_W / 2, y, BOX_W, BOX_H,
                Component.translatable("screen.zenkai.enchant.search"));
        searchBox.setHint(Component.translatable("screen.zenkai.enchant.search"));
        searchBox.setResponder(this::applyFilter);
        addRenderableWidget(searchBox);

        int arrowY = y + BOX_H + 26;
        leftArrow  = new ArrowIconButton(centerX() - 92, arrowY, ArrowIconButton.Dir.LEFT,  () -> cycle(-1));
        rightArrow = new ArrowIconButton(centerX() + 80, arrowY, ArrowIconButton.Dir.RIGHT, () -> cycle(1));
        addRenderableWidget(leftArrow);
        addRenderableWidget(rightArrow);
    }

    private void applyFilter(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        filtered = q.isEmpty() ? all : all.stream()
                .filter(h -> h.value().description().getString().toLowerCase(Locale.ROOT).contains(q)
                        || h.key().location().getPath().contains(q))
                .toList();
        index = 0;
    }

    private void cycle(int delta) {
        if (filtered.isEmpty()) return;
        index = (index + delta + filtered.size()) % filtered.size();
    }

    @Override protected boolean confirmEnabled() { return !filtered.isEmpty(); }

    @Override
    protected void onConfirm() {
        if (filtered.isEmpty()) return;
        ResourceLocation id = filtered.get(index).key().location();
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new ConfirmVillagerWishPayload(id));
        onClose();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean any = !filtered.isEmpty();
        if (leftArrow != null)  leftArrow.visible  = filtered.size() > 1;
        if (rightArrow != null) rightArrow.visible = filtered.size() > 1;

        int y = searchBox.getY() + BOX_H + 20;

        if (!any) {
            drawCenteredOnPanel(g, Component.translatable("screen.zenkai.no_enchantments"),
                    y, ZenkaiPalette.MUTED_ON_PANEL);
            return;
        }

        Holder.Reference<Enchantment> h = filtered.get(index);

        drawCenteredOnPanel(g, h.value().description(), y, ZenkaiPalette.LABEL_ON_PANEL);
        y += this.font.lineHeight + 6;
        drawCenteredOnPanel(g, Component.translatable("screen.zenkai.enchant.max_level",
                h.value().getMaxLevel()), y, ZenkaiPalette.BODY_ON_PANEL);
        y += this.font.lineHeight + 4;

        // El id completo, atenuado. Con varios mods hay nombres traducidos idénticos y esto es
        // lo único que distingue "Fortuna" de un mod de la de otro.
        drawCenteredOnPanel(g, Component.literal(h.key().location().toString()),
                y, ZenkaiPalette.MUTED_ON_PANEL);

        drawCenteredOnPanel(g,
                Component.translatable("screen.zenkai.enchant.index", index + 1, filtered.size()),
                contentBottom() - this.font.lineHeight - 4, ZenkaiPalette.MUTED_ON_PANEL);
    }
}