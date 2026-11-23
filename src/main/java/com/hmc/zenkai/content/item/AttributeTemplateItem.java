package com.hmc.zenkai.content.item;

import com.hmc.zenkai.feature.armor.AttributeTemplates;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Plantilla de atributos (Fase 5c de la 0.7.0): un ítem por acción (reiniciar, copiar, transferir, aumentar). Se usa
 * en la pestaña de Atributos de la máquina de fabricación y se gasta; la lógica vive en AttributeTemplates.
 * Solo se halla en cofres; no se fabrica desde cero: se duplica en la mesa (como la plantilla de netherita) o en la
 * pestaña Fabricar de la máquina.
 */
public class AttributeTemplateItem extends Item {
    private final AttributeTemplates.Kind kind;

    public AttributeTemplateItem(AttributeTemplates.Kind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    public AttributeTemplates.Kind kind() { return kind; }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx, @NotNull List<Component> lines,
                                @NotNull TooltipFlag flag) {
        lines.add(Component.translatable("item.zenkai." + kind.id() + "_template.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.zenkai.attribute_template.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
