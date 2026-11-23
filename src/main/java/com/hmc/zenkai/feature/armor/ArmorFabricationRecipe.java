package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Receta de la máquina de fabricación (Fase 5b de la 0.7.0, modelo de DMZ sin pieza vanilla de base): patrón (set) +
 * pieza elegida en la máquina + {@code count} unidades de UN material repartidas por las 9 ranuras → la pieza.
 * Gasta {@code energy} FE repartidos a lo largo de {@code time} ticks. Una receta por pieza y set (4 por set).
 * <pre>{ "type": "zenkai:armor_fabrication", "pattern": "zenkai:saiyan_armor", "piece": "chestplate",
 *   "material": { "item": "zenkai:katchin_ingot" }, "count": 8, "energy": 40000, "time": 200,
 *   "result": { "id": "zenkai:saiyan_armor_chestplate" } }</pre>
 * isSpecial: no va al libro de recetas (no es de mesa) y así vanilla no avisa de categoría desconocida.
 */
public record ArmorFabricationRecipe(ResourceLocation pattern, ArmorItem.Type piece, Ingredient material, int count,
                                     int energy, int time, ItemStack result)
        implements Recipe<ArmorFabricationRecipe.Input> {

    /** Lo que mira la máquina: el set del patrón y la pieza elegida. El material se comprueba aparte (cuenta total). */
    public record Input(ResourceLocation pattern, ArmorItem.Type piece) implements RecipeInput {
        @Override public @NotNull ItemStack getItem(int index) { return ItemStack.EMPTY; }
        @Override public int size() { return 0; }
        @Override public boolean isEmpty() { return false; }
    }

    public static final MapCodec<ArmorFabricationRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("pattern").forGetter(ArmorFabricationRecipe::pattern),
            ArmorItem.Type.CODEC.fieldOf("piece").forGetter(ArmorFabricationRecipe::piece),
            Ingredient.CODEC_NONEMPTY.fieldOf("material").forGetter(ArmorFabricationRecipe::material),
            Codec.intRange(1, 64 * 9).fieldOf("count").forGetter(ArmorFabricationRecipe::count),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("energy", 20_000).forGetter(ArmorFabricationRecipe::energy),
            Codec.intRange(1, 72_000).optionalFieldOf("time", 200).forGetter(ArmorFabricationRecipe::time),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ArmorFabricationRecipe::result)
    ).apply(i, ArmorFabricationRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorFabricationRecipe> STREAM_CODEC =
            StreamCodec.of(ArmorFabricationRecipe::write, ArmorFabricationRecipe::read);

    private static void write(RegistryFriendlyByteBuf buf, ArmorFabricationRecipe r) {
        ResourceLocation.STREAM_CODEC.encode(buf, r.pattern);
        buf.writeEnum(r.piece);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.material);
        ByteBufCodecs.VAR_INT.encode(buf, r.count);
        ByteBufCodecs.VAR_INT.encode(buf, r.energy);
        ByteBufCodecs.VAR_INT.encode(buf, r.time);
        ItemStack.STREAM_CODEC.encode(buf, r.result);
    }

    private static ArmorFabricationRecipe read(RegistryFriendlyByteBuf buf) {
        return new ArmorFabricationRecipe(ResourceLocation.STREAM_CODEC.decode(buf), buf.readEnum(ArmorItem.Type.class),
                Ingredient.CONTENTS_STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ItemStack.STREAM_CODEC.decode(buf));
    }

    /** FE por tick de trabajo (redondeado hacia arriba: la receta nunca sale más barata de lo que dice). */
    public int energyPerTick() { return (int) Math.ceil(energy / (double) time); }

    @Override
    public boolean matches(@NotNull Input in, @NotNull Level level) {
        return pattern.equals(in.pattern()) && piece == in.piece();
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull Input in, @NotNull HolderLookup.Provider regs) { return result.copy(); }

    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public @NotNull ItemStack getResultItem(@NotNull HolderLookup.Provider regs) { return result; }
    @Override public boolean isSpecial() { return true; }
    @Override public @NotNull RecipeSerializer<?> getSerializer() { return ModRecipes.ARMOR_FABRICATION_SERIALIZER.get(); }
    @Override public @NotNull RecipeType<?> getType() { return ModRecipes.ARMOR_FABRICATION.get(); }

    public static final class Serializer implements RecipeSerializer<ArmorFabricationRecipe> {
        @Override public @NotNull MapCodec<ArmorFabricationRecipe> codec() { return CODEC; }
        @Override public @NotNull StreamCodec<RegistryFriendlyByteBuf, ArmorFabricationRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
