package com.hmc.zenkai.content.item;

import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.registry.ModArmorMaterials;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Pesa de entrenamiento. Geo estática (sin animación), 0 de armadura e irrompible: el
 * material RACE_ARMOR_MATERIAL da 0 protección y no se llama a durability(), así que no
 * tiene barra de daño.
 *
 * El peso vive en el DataComponent WEIGHT_TONS del propio stack -> sobrevive a morir, a
 * guardar y a intercambiar, y cada pesa se ajusta por separado.
 *
 * Shift + clic derecho abre la pantalla de ajuste (cliente). Sin shift, se comporta como
 * armadura normal y se equipa.
 */
public class WeightArmorItem extends GeoLayerArmorItem {

    /** Escalón de los botones +/- de la pantalla, en toneladas. */
    public static final double STEP_TONS = 10.0;

    private final double minTons;
    private final double maxTons;

    public WeightArmorItem(String modelPath, String texturePath, double minTons, double maxTons) {
        super(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                new Item.Properties(), modelPath, texturePath, "");
        this.minTons = minTons;
        this.maxTons = maxTons;
        this.channel(ColorChannel.NONE);
    }

    public double minTons() { return minTons; }
    public double maxTons() { return maxTons; }

    /** Peso guardado en el stack; si no tiene componente todavía, el mínimo de la pesa. */
    public double getTons(ItemStack stack) {
        Double v = stack.get(ModDataComponents.WEIGHT_TONS.get());
        return v == null ? minTons : clampTons(v);
    }

    public void setTons(ItemStack stack, double tons) {
        stack.set(ModDataComponents.WEIGHT_TONS.get(), clampTons(tons));
    }

    /** Clampa al rango de ESTA pesa y redondea a 2 decimales (el campo de la GUI admite 2). */
    public double clampTons(double tons) {
        double v = Math.max(minTons, Math.min(maxTons, tons));
        return Math.round(v * 100.0) / 100.0;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level,
                                                           @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return super.use(level, player, hand);

        // La clase cliente solo se toca dentro de isClientSide: en servidor dedicado nunca
        // se carga (mismo truco que CuriosCompat.Impl).
        if (level.isClientSide()) {
            com.hmc.zenkai.client.gui.screens.WeightScreen.open(hand, this, getTons(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx,
                                @NotNull List<Component> lines, @NotNull TooltipFlag flag) {
        lines.add(Component.translatable("item.zenkai.weight.tooltip.current",
                fmt(getTons(stack))).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("item.zenkai.weight.tooltip.range",
                fmt(minTons), fmt(maxTons)).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("item.zenkai.weight.tooltip.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, ctx, lines, flag);
    }

    public static String fmt(double tons) {
        return String.format(Locale.ROOT, "%.2f", tons);
    }
}