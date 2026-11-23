package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Duplicado de una plantilla de atributos en la máquina de fabricación (Fase 5c de la 0.7.0; diseño: «menos materiales
 * pero más FE», como receta de la máquina y no con una máquina propia). La plantilla va en la ranura del patrón y SÍ se
 * gasta (a diferencia del patrón); el material, en las 9 ranuras; sale {@code result} (dos plantillas).
 * <pre>{ "type": "zenkai:template_duplication", "template": { "item": "zenkai:reset_template" },
 *   "material": { "item": "zenkai:katchin_ingot" }, "count": 2, "energy": 60000, "time": 200,
 *   "result": { "id": "zenkai:reset_template", "count": 2 } }</pre>
 */
public record TemplateDuplicationRecipe(Ingredient template, Ingredient material, int count, int energy, int time,
                                        ItemStack result) implements Recipe<TemplateDuplicationRecipe.Input> {

    public record Input(ItemStack template) implements RecipeInput {
        @Override public @NotNull ItemStack getItem(int index) { return template; }
        @Override public int size() { return 1; }
    }

    public static final MapCodec<TemplateDuplicationRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("template").forGetter(TemplateDuplicationRecipe::template),
            Ingredient.CODEC_NONEMPTY.fieldOf("material").forGetter(TemplateDuplicationRecipe::material),
            Codec.intRange(1, 64 * 9).fieldOf("count").forGetter(TemplateDuplicationRecipe::count),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("energy", 60_000).forGetter(TemplateDuplicationRecipe::energy),
            Codec.intRange(1, 72_000).optionalFieldOf("time", 200).forGetter(TemplateDuplicationRecipe::time),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(TemplateDuplicationRecipe::result)
    ).apply(i, TemplateDuplicationRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TemplateDuplicationRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.template);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.material);
                ByteBufCodecs.VAR_INT.encode(buf, r.count);
                ByteBufCodecs.VAR_INT.encode(buf, r.energy);
                ByteBufCodecs.VAR_INT.encode(buf, r.time);
                ItemStack.STREAM_CODEC.encode(buf, r.result);
            },
            buf -> new TemplateDuplicationRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                    Ingredient.CONTENTS_STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ItemStack.STREAM_CODEC.decode(buf)));

    /** FE por tick de trabajo, hacia arriba como la fabricación. */
    public int energyPerTick() { return (int) Math.ceil(energy / (double) time); }

    @Override public boolean matches(@NotNull Input in, @NotNull Level level) { return template.test(in.template()); }
    @Override public @NotNull ItemStack assemble(@NotNull Input in, HolderLookup.@NotNull Provider regs) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider regs) { return result; }
    @Override public boolean isSpecial() { return true; }
    @Override public @NotNull RecipeSerializer<?> getSerializer() { return ModRecipes.TEMPLATE_DUPLICATION_SERIALIZER.get(); }
    @Override public @NotNull RecipeType<?> getType() { return ModRecipes.TEMPLATE_DUPLICATION.get(); }

    public static final class Serializer implements RecipeSerializer<TemplateDuplicationRecipe> {
        @Override public @NotNull MapCodec<TemplateDuplicationRecipe> codec() { return CODEC; }
        @Override public @NotNull StreamCodec<RegistryFriendlyByteBuf, TemplateDuplicationRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
