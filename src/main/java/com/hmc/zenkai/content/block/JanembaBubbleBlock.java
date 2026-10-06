package com.hmc.zenkai.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Gelatina de las esferas flotantes del Janemba Rift (referencia de Fusion Reborn,
 * .claude/pendiente/referencia_janemba.png): burbujas translúcidas, brillantes y de muchos colores.
 *
 * POR QUÉ un bloque propio y no cristal tintado (la primera versión): el cristal vanilla dibuja el
 * marco de cada bloque, así que una esfera de cristal se leía como un montón de cajas de vidrio.
 * Esta gelatina no tiene marco y, como el cristal, no dibuja la cara que toca a otro bloque igual
 * ({@link TransparentBlock}): la cáscara entera se ve como UNA superficie lisa.
 *
 * Un solo bloque y una sola textura en gris: el color sale del tinte del cliente (ver
 * client/world/JanembaBubbleColors) según {@link #COLOR}. {@link #SHINE} aclara el tinte hacia el
 * blanco: la feature lo pone en un parche de la parte de arriba de cada esfera, que hace de reflejo.
 *
 * Tintable (pedido del usuario, 2026-10-05): un tinte vanilla sobre el bloque lo cambia de color, y
 * 8 gelatinas + 1 tinte en la mesa dan 8 de ese color. El color viaja en el ítem con el componente
 * vanilla BLOCK_STATE: BlockItem lo aplica solo al colocar, el toque de seda lo copia al soltarlo
 * (CopyBlockState en la loot table) y el clic central lo conserva ({@link #getCloneItemStack}).
 */
public class JanembaBubbleBlock extends TransparentBlock {

    /**
     * Los 16 colores de tinte. Los 7 primeros son los de la generación y conservan su nombre de la
     * primera versión (cambiarlo rompería los bloques ya generados); sus tonos son los de la
     * referencia, más saturados que el tinte vanilla.
     */
    public enum Color implements StringRepresentable {
        RED("red", 0xE8324A, DyeColor.RED),
        PINK("pink", 0xFF5FAE, DyeColor.PINK),
        ORANGE("orange", 0xFF8A2A, DyeColor.ORANGE),
        YELLOW("yellow", 0xFFD23A, DyeColor.YELLOW),
        GREEN("green", 0x3FBF5A, DyeColor.GREEN),
        BLUE("blue", 0x3E91E8, DyeColor.BLUE),
        VIOLET("violet", 0x9A4DE0, DyeColor.PURPLE),
        WHITE("white", 0xF4F0FF, DyeColor.WHITE),
        LIGHT_GRAY("light_gray", 0xB8B4C4, DyeColor.LIGHT_GRAY),
        GRAY("gray", 0x6E6A7A, DyeColor.GRAY),
        BLACK("black", 0x2A2632, DyeColor.BLACK),
        BROWN("brown", 0x9A6234, DyeColor.BROWN),
        CYAN("cyan", 0x2CC8C8, DyeColor.CYAN),
        LIGHT_BLUE("light_blue", 0x7FD0FF, DyeColor.LIGHT_BLUE),
        LIME("lime", 0x9BEF3A, DyeColor.LIME),
        MAGENTA("magenta", 0xE04AD8, DyeColor.MAGENTA);

        private final String name;
        public final int rgb;
        public final DyeColor dye;

        Color(String name, int rgb, DyeColor dye) {
            this.name = name;
            this.rgb = rgb;
            this.dye = dye;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public static Color of(DyeColor dye) {
            for (Color c : values()) if (c.dye == dye) return c;
            return PINK;
        }
    }

    /** Colores que usa la generación: la paleta de esferas de la referencia. */
    public static final List<Color> WORLDGEN_COLORS = List.of(
            Color.RED, Color.PINK, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.BLUE, Color.VIOLET);

    public static final EnumProperty<Color> COLOR = EnumProperty.create("color", Color.class);
    public static final BooleanProperty SHINE = BooleanProperty.create("shine");

    /** Cuánto se acerca al blanco el reflejo (0 = el color, 1 = blanco). */
    private static final float SHINE_MIX = 0.55f;

    public JanembaBubbleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLOR, Color.PINK).setValue(SHINE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, SHINE);
    }

    /** Tinte RGB de un estado: el color, o el color aclarado si es reflejo. */
    public static int tint(BlockState state) {
        return tint(state.getValue(COLOR), state.getValue(SHINE));
    }

    public static int tint(Color color, boolean shine) {
        int rgb = color.rgb;
        if (!shine) return rgb;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (int) ((255 - r) * SHINE_MIX);
        g += (int) ((255 - g) * SHINE_MIX);
        b += (int) ((255 - b) * SHINE_MIX);
        return (r << 16) | (g << 8) | b;
    }

    /** Color guardado en un ítem de gelatina (sin componente: el rosa por defecto). */
    public static Color colorOf(ItemStack stack) {
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        if (props == null) return Color.PINK;
        Color c = props.get(COLOR);
        return c == null ? Color.PINK : c;
    }

    /** Ítem de gelatina de un color: el componente BLOCK_STATE que BlockItem aplica al colocar. */
    public static ItemStack stackOf(Color color, int count) {
        ItemStack stack = new ItemStack(com.hmc.zenkai.registry.ModBlocks.HFIL_JANEMBA_BUBBLE.get(), count);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(COLOR, color));
        return stack;
    }

    /** Tinte vanilla sobre el bloque: cambia su color (el reflejo se conserva). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dyeItem)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        Color color = Color.of(dyeItem.getDyeColor());
        if (state.getValue(COLOR) == color) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(COLOR, color), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Clic central: el ítem con el color del bloque, no la gelatina rosa por defecto. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return stackOf(state.getValue(COLOR), 1);
    }
}
