package com.hmc.zenkai.feature.crusher;

import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Receta del Triturador (Fase 5d de la 0.7.0). Formato a imagen del {@code create:crushing} (salidas con probabilidad) con
 * la cantidad de entrada del {@code mekanism:enriching} (3 crudos → 4 polvos): así las cifras de las dos referencias se
 * copian tal cual (diseño en pendientes-0.7.0, «Triturador»).
 * <pre>{ "type": "zenkai:crushing", "ingredient": { "tag": "c:ores/katchin" }, "count": 1, "time": 200, "energy": 4000,
 *   "results": [ { "id": "zenkai:katchin_dust", "count": 2 },
 *                { "id": "zenkai:katchin_dust", "chance": 0.25 } ],
 *   "infusion_aware": false }</pre>
 * {@code infusion_aware}: para reciclar armaduras. Si la pieza triturada está infundida, cada lingote/hilo/tela de katchin
 * normal de las salidas sale del NIVEL de la pieza (un peto Divino devuelve lingotes Divinos). Lo imbuido se pierde.
 * {@code energy} es el FE TOTAL de la receta, repartido en {@code time} ticks (como la fabricación).
 */
public record CrushingRecipe(Ingredient ingredient, int count, List<Output> results, int time, int energy,
                             boolean infusionAware) implements Recipe<CrushingRecipe.Input> {

    /** Una salida: el stack y su probabilidad (1 = siempre). */
    public record Output(ItemStack stack, float chance) {
        /** Plano como el de Create: {@code { "id": ..., "count": 2, "chance": 0.25 }} (sin componentes: no hacen falta). */
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(o -> o.stack().getItemHolder()),
                Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(o -> o.stack().getCount()),
                Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1f).forGetter(Output::chance)
        ).apply(i, (item, count, chance) -> new Output(new ItemStack(item, count), chance)));

        public static final StreamCodec<RegistryFriendlyByteBuf, Output> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Output::stack, ByteBufCodecs.FLOAT, Output::chance, Output::new);

        public boolean guaranteed() { return chance >= 1f; }
    }

    public record Input(ItemStack stack) implements RecipeInput {
        @Override public @NotNull ItemStack getItem(int index) { return stack; }
        @Override public int size() { return 1; }
    }

    public static final MapCodec<CrushingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CrushingRecipe::ingredient),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(CrushingRecipe::count),
            Output.CODEC.listOf(1, 4).fieldOf("results").forGetter(CrushingRecipe::results),
            Codec.intRange(1, 72_000).optionalFieldOf("time", 200).forGetter(CrushingRecipe::time),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("energy", 4_000).forGetter(CrushingRecipe::energy),
            Codec.BOOL.optionalFieldOf("infusion_aware", false).forGetter(CrushingRecipe::infusionAware)
    ).apply(i, CrushingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrushingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.ingredient);
                ByteBufCodecs.VAR_INT.encode(buf, r.count);
                Output.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.results);
                ByteBufCodecs.VAR_INT.encode(buf, r.time);
                ByteBufCodecs.VAR_INT.encode(buf, r.energy);
                ByteBufCodecs.BOOL.encode(buf, r.infusionAware);
            },
            buf -> new CrushingRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    Output.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

    /** FE por tick de trabajo, hacia arriba (la receta nunca sale más barata de lo que dice). */
    public int energyPerTick() { return (int) Math.ceil(energy / (double) time); }

    /**
     * Lo que sale esta vez: las salidas seguras siempre y las de probabilidad según {@code random}; con
     * {@code infusion_aware}, el katchin normal pasa al nivel de la pieza de entrada.
     */
    public List<ItemStack> roll(ItemStack input, RandomSource random) {
        KiEnergyType level = infusionAware ? KiInfusion.of(input) : null;
        List<ItemStack> out = new ArrayList<>();
        for (Output o : results) {
            if (!o.guaranteed() && random.nextFloat() >= o.chance()) continue;
            out.add(leveled(o.stack(), level));
        }
        return out;
    }

    /** Todas las salidas posibles a la vez (para comprobar que caben antes de empezar). */
    public List<ItemStack> worstCase(ItemStack input) {
        KiEnergyType level = infusionAware ? KiInfusion.of(input) : null;
        List<ItemStack> out = new ArrayList<>();
        for (Output o : results) out.add(leveled(o.stack(), level));
        return out;
    }

    private static ItemStack leveled(ItemStack s, @Nullable KiEnergyType level) {
        if (level == null) return s.copy();
        if (s.is(ModItems.KATCHIN_INGOT.get())) return new ItemStack(KiInfusion.ingot(level), s.getCount());
        if (s.is(ModItems.KATCHIN_THREAD.get())) return new ItemStack(KiInfusion.thread(level), s.getCount());
        if (s.is(ModItems.KATCHIN_CLOTH.get())) return new ItemStack(KiInfusion.cloth(level), s.getCount());
        return s.copy();
    }

    @Override public boolean matches(@NotNull Input in, @NotNull Level level) {
        return ingredient.test(in.stack()) && in.stack().getCount() >= count;
    }
    @Override public @NotNull ItemStack assemble(@NotNull Input in, HolderLookup.@NotNull Provider regs) {
        return results.get(0).stack().copy();
    }
    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider regs) { return results.get(0).stack(); }
    @Override public boolean isSpecial() { return true; }
    @Override public @NotNull RecipeSerializer<?> getSerializer() { return ModRecipes.CRUSHING_SERIALIZER.get(); }
    @Override public @NotNull RecipeType<?> getType() { return ModRecipes.CRUSHING.get(); }

    public static final class Serializer implements RecipeSerializer<CrushingRecipe> {
        @Override public @NotNull MapCodec<CrushingRecipe> codec() { return CODEC; }
        @Override public @NotNull StreamCodec<RegistryFriendlyByteBuf, CrushingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
