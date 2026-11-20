package com.hmc.zenkai.content.item;

import com.hmc.zenkai.feature.sideconfig.SideConfigurable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * Llave de Zenkai (0.7.0), al estilo del Configurator de Mekanism (decidido con el usuario el
 * 2026-11-17, tras probar una pantalla de caras en cruz y descartarla para la llave). Tres MODOS que se
 * alternan con agachado + clic derecho al aire:
 *   ENERGÍA / FLUIDO → clic derecho en una cara cambia ese puerto en ese canal (en tanques y cables manda
 *                      su único canal; el motor tiene los dos);
 *   ROTAR            → clic derecho gira el bloque (el Motor de Ki lo gira Create).
 * En cualquier modo, agachado + clic derecho en un bloque lo desmonta.
 * Mientras se lleva en un modo de configurar, WrenchOverlay sombrea las caras con el color de su modo.
 *
 * Los clics sobre bloques NO van aquí sino en WrenchEvents (RightClickBlock a prioridad máxima): Create
 * trata cualquier ítem del tag c:tools/wrench como su llave sobre sus bloques y giraba el motor antes de
 * que llegara cualquier otra cosa (el usuario vio que la llave no cambiaba puertos del motor).
 */
public class WrenchItem extends Item {

    /** Cada modo con su color, para distinguirlo de un vistazo en la action bar y el tooltip (petición del
     *  usuario, 2026-11-17): energía en verde (las barras de FE), fluido en aguamarina, rotar en dorado. */
    public enum Mode {
        ENERGY(ChatFormatting.GREEN), FLUID(ChatFormatting.AQUA), ROTATE(ChatFormatting.GOLD);

        private final ChatFormatting color;
        Mode(ChatFormatting color) { this.color = color; }

        public String langKey() { return "item.zenkai.wrench.mode." + name().toLowerCase(Locale.ROOT); }
        /** Nombre del modo ya coloreado. */
        public Component label() { return Component.translatable(langKey()).withStyle(color, ChatFormatting.BOLD); }
    }

    private static final String MODE_KEY = "Mode";

    public WrenchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Modo elegido (Energía por defecto). Va en CUSTOM_DATA: un solo entero, no merece componente propio. */
    public static Mode mode(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        int i = data == null ? 0 : data.copyTag().getInt(MODE_KEY);
        return Mode.values()[Math.floorMod(i, Mode.values().length)];
    }

    public static boolean rotating(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem && mode(stack) == Mode.ROTATE;
    }

    /** Id del componente donde el Configurator de Mekanism guarda su modo (se lee por nombre: sin dependencia). */
    private static final net.minecraft.resources.ResourceLocation MEK_MODE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mekanism", "configurator_mode");

    /**
     * El modo del Configurator de Mekanism traducido a los de esta llave: Configurar energía → ENERGY,
     * Configurar fluidos → FLUID, Rotar → ROTATE. Cualquier otro (objetos, químicos, calor, vacío, llave) o un
     * ítem que no sea el Configurator: null, y Zenkai no toca el clic. Sin el componente el Configurator está
     * en su modo por defecto (objetos), así que tampoco es nuestro.
     */
    @SuppressWarnings("unchecked")
    @org.jetbrains.annotations.Nullable
    public static Mode mekanismMode(ItemStack stack) {
        var type = (net.minecraft.core.component.DataComponentType<Object>)
                net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(MEK_MODE);
        if (type == null) return null;
        Object v = stack.get(type);
        if (v == null) return null;
        String name = v instanceof net.minecraft.util.StringRepresentable sr ? sr.getSerializedName() : v.toString();
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "configurate_energy" -> Mode.ENERGY;
            case "configurate_fluids" -> Mode.FLUID;
            case "rotate" -> Mode.ROTATE;
            default -> null;
        };
    }

    /** El canal que la llave toca en este bloque: el del modo si lo tiene, si no el único que tenga. */
    public static SideConfigurable.Channel channelFor(ItemStack stack, SideConfigurable cfg) {
        Mode m = stack.getItem() instanceof WrenchItem ? mode(stack) : mekanismMode(stack);
        SideConfigurable.Channel wanted = m == Mode.FLUID ? SideConfigurable.Channel.FLUID : SideConfigurable.Channel.ENERGY;
        return cfg.sideChannels().contains(wanted) ? wanted : cfg.sideChannels().get(0);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE_KEY, next.ordinal()));
            player.displayClientMessage(Component.translatable("item.zenkai.wrench.mode", next.label()), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.6f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zenkai.wrench.mode", mode(stack).label()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zenkai.wrench.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zenkai.wrench.tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zenkai.wrench.tip3").withStyle(ChatFormatting.GRAY));
    }
}
