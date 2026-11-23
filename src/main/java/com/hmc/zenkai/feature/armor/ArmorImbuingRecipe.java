package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Qué tipo de Ki pide imbuir una pieza (0.7.0, decisión del usuario 2026-11-22: recetas JSON por pieza).
 * <pre>{ "type": "zenkai:armor_imbuing", "piece": { "tag": "zenkai:imbue/super" }, "ki": "super" }</pre>
 * Una pieza SIN receta toma el tipo AUTOMÁTICO de {@link #autoTier} (2026-11-22, decisión del usuario: cualquier
 * armadura de cualquier mod entra sola, por armadura + dureza comparadas con la vanilla del mismo tipo de pieza). Una
 * receta manda sobre lo automático, también para BAJAR el tipo (la ropa de tela del mod pide Base aunque su dureza la
 * subiría). Si varias recetas casan con la misma pieza gana la del tipo más alto (orden fijo, sin depender del orden de
 * carga de los datapacks).
 *
 * Tipo superior al pedido (orden Base &lt; Super &lt; Divino &lt; Legendario, el del enum): vale, y gasta la MITAD de
 * fluido por cada tipo de más ({@link #scaledMb}); uno inferior no vale. La FE y la sangre no cambian.
 * Solo dice el tipo: la cantidad sale de {@link ImbueCost} en ArmorFabricatorBlockEntity.preview.
 * La infusora (5c) podrá sobreescribir esto con el tipo con que se infundió el material.
 */
public record ArmorImbuingRecipe(Ingredient piece, KiEnergyType ki) implements Recipe<ArmorImbuingRecipe.Input> {

    public record Input(ItemStack piece) implements RecipeInput {
        @Override public @NotNull ItemStack getItem(int index) { return piece; }
        @Override public int size() { return 1; }
    }

    public static final Codec<KiEnergyType> KI_CODEC = Codec.STRING.comapFlatMap(s -> {
        for (KiEnergyType t : KiEnergyType.values()) {
            if (t.name().toLowerCase(Locale.ROOT).equals(s)) return com.mojang.serialization.DataResult.success(t);
        }
        return com.mojang.serialization.DataResult.error(() -> "Tipo de Ki desconocido: " + s);
    }, t -> t.name().toLowerCase(Locale.ROOT));

    public static final MapCodec<ArmorImbuingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("piece").forGetter(ArmorImbuingRecipe::piece),
            KI_CODEC.fieldOf("ki").forGetter(ArmorImbuingRecipe::ki)
    ).apply(i, ArmorImbuingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorImbuingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> { Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.piece); buf.writeEnum(r.ki); },
            buf -> new ArmorImbuingRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buf), buf.readEnum(KiEnergyType.class)));

    /** Tipo que pide la pieza: el más alto de las recetas que casan, o el automático. Vale en cliente (recetas sincronizadas). */
    public static KiEnergyType requiredFor(@Nullable Level level, ItemStack piece) {
        KiEnergyType explicit = explicitFor(level, piece);
        return explicit != null ? explicit : autoTier(piece);
    }

    /** Tipo de las recetas que casan con la pieza (el más alto), o null si ninguna. */
    @Nullable
    public static KiEnergyType explicitFor(@Nullable Level level, ItemStack piece) {
        if (level == null || piece.isEmpty()) return null;
        KiEnergyType best = null;
        for (RecipeHolder<ArmorImbuingRecipe> h : level.getRecipeManager().getAllRecipesFor(ModRecipes.ARMOR_IMBUING.get())) {
            ArmorImbuingRecipe r = h.value();
            if ((best == null || r.ki.ordinal() > best.ordinal()) && r.piece.test(piece)) best = r.ki;
        }
        return best;
    }

    // Puntuación vanilla (armadura + dureza) por tipo de pieza: casco, peto, pantalones, botas.
    private static final double[] IRON = {2, 6, 5, 2};
    private static final double[] DIAMOND = {5, 10, 8, 5};
    private static final double[] NETHERITE = {6, 11, 9, 6};

    /**
     * Tipo automático de una pieza de CUALQUIER mod: puntuación = armadura + dureza que da en su ranura (sumando los
     * modificadores ADD_VALUE de sus atributos, sin lo imbuido), comparada con la vanilla del mismo tipo de pieza (un
     * casco con cascos). Hasta hierro → Base; hasta diamante → Súper; hasta netherite → Divino; por encima → Legendario.
     * La defensa sola no basta: diamante y netherite dan los mismos 3/8/6/3 y solo los separa la dureza.
     */
    public static KiEnergyType autoTier(ItemStack piece) {
        int idx = pieceIndex(piece);
        if (idx < 0) return KiEnergyType.BASE;
        double score = score(piece);
        final double eps = 1e-6;
        if (score <= IRON[idx] + eps) return KiEnergyType.BASE;
        if (score <= DIAMOND[idx] + eps) return KiEnergyType.SUPER;
        if (score <= NETHERITE[idx] + eps) return KiEnergyType.DIVINE;
        return KiEnergyType.LEGENDARY;
    }

    /** Armadura + dureza de la pieza en su ranura. */
    public static double score(ItemStack piece) {
        net.minecraft.world.item.Equipable eq = net.minecraft.world.item.Equipable.get(piece);
        if (eq == null) return 0;
        double[] sum = {0};
        piece.forEachModifier(eq.getEquipmentSlot(), (attr, mod) -> {
            if (mod.operation() != net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) return;
            if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
                    || attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)) sum[0] += mod.amount();
        });
        return sum[0];
    }

    /** 0 casco, 1 peto, 2 pantalones, 3 botas; -1 si no va en una ranura de armadura. */
    private static int pieceIndex(ItemStack piece) {
        net.minecraft.world.item.Equipable eq = net.minecraft.world.item.Equipable.get(piece);
        if (eq == null) return -1;
        return switch (eq.getEquipmentSlot()) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> -1;
        };
    }

    /**
     * mB que se gastan con el tipo {@code used} cuando la receta pide {@code required} y la cuenta base es {@code mb};
     * -1 si el tipo no vale (inferior al pedido o sin fluido). Mitad por cada tipo de más, redondeando hacia arriba y
     * nunca por debajo de 1: un tipo superior siempre sale más barato, pero nunca gratis.
     */
    public static long scaledMb(long mb, KiEnergyType required, @Nullable KiEnergyType used) {
        if (used == null || used.ordinal() < required.ordinal()) return -1;
        int steps = used.ordinal() - required.ordinal();
        return Math.max(1, (mb + (1L << steps) - 1) >> steps);
    }

    @Override public boolean matches(@NotNull Input in, @NotNull Level level) { return piece.test(in.piece()); }
    @Override public @NotNull ItemStack assemble(@NotNull Input in, @NotNull HolderLookup.Provider regs) { return in.piece().copy(); }
    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public @NotNull ItemStack getResultItem(@NotNull HolderLookup.Provider regs) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public @NotNull RecipeSerializer<?> getSerializer() { return ModRecipes.ARMOR_IMBUING_SERIALIZER.get(); }
    @Override public @NotNull RecipeType<?> getType() { return ModRecipes.ARMOR_IMBUING.get(); }

    public static final class Serializer implements RecipeSerializer<ArmorImbuingRecipe> {
        @Override public @NotNull MapCodec<ArmorImbuingRecipe> codec() { return CODEC; }
        @Override public @NotNull StreamCodec<RegistryFriendlyByteBuf, ArmorImbuingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
