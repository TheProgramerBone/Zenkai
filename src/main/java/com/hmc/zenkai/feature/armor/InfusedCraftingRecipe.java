package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Receta de mesa que respeta la infusión de Ki del katchin (Fase 5c de la 0.7.0). Envuelve una receta normal
 * ({@code base}, con o sin forma) y solo cambia qué pasa con el componente {@code zenkai:ki_infusion}:
 * <ul>
 *   <li>{@code inherit}: el resultado lleva el tipo MÁS BAJO de los portadores de la rejilla (KiInfusion.Lowest); si
 *       alguno no estaba infundido, sale sin infundir. Lingote → hilo y hilo → tela.</li>
 *   <li>{@code reject}: la receta no casa si hay algún portador infundido. Lingotes → bloque: el bloque colocado no
 *       guarda componentes, así que la infusión se perdería sin aviso al ponerlo y quitarlo.</li>
 * </ul>
 * Hace falta porque las recetas vanilla aceptan el ítem con cualquier componente y devuelven el resultado limpio.
 * <pre>{ "type": "zenkai:infused_crafting", "mode": "inherit", "base": { "type": "minecraft:crafting_shapeless", ... } }</pre>
 */
public record InfusedCraftingRecipe(CraftingRecipe base, Mode mode) implements CraftingRecipe {

    public enum Mode implements StringRepresentable {
        INHERIT, REJECT;
        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        @Override public @NotNull String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    private static final Codec<CraftingRecipe> BASE_CODEC = Recipe.CODEC.comapFlatMap(
            r -> r instanceof CraftingRecipe c && !(r instanceof InfusedCraftingRecipe)
                    ? DataResult.success(c)
                    : DataResult.error(() -> "zenkai:infused_crafting necesita una receta de mesa como base"),
            c -> c);

    public static final MapCodec<InfusedCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BASE_CODEC.fieldOf("base").forGetter(InfusedCraftingRecipe::base),
            Mode.CODEC.optionalFieldOf("mode", Mode.INHERIT).forGetter(InfusedCraftingRecipe::mode)
    ).apply(i, InfusedCraftingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, InfusedCraftingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> { Recipe.STREAM_CODEC.encode(buf, r.base); buf.writeEnum(r.mode); },
            buf -> new InfusedCraftingRecipe((CraftingRecipe) Recipe.STREAM_CODEC.decode(buf), buf.readEnum(Mode.class)));

    @Override
    public boolean matches(@NotNull CraftingInput in, @NotNull Level level) {
        if (!base.matches(in, level)) return false;
        if (mode == Mode.REJECT) {
            for (ItemStack s : in.items()) if (KiInfusion.of(s) != null) return false;
        }
        return true;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingInput in, HolderLookup.@NotNull Provider regs) {
        ItemStack out = base.assemble(in, regs);
        if (mode != Mode.INHERIT || out.isEmpty()) return out;
        KiInfusion.Lowest lowest = new KiInfusion.Lowest();
        for (ItemStack s : in.items()) if (s.is(KiInfusion.CARRIERS)) lowest.add(s);
        KiEnergyType t = lowest.result();
        return t == null ? out : KiInfusion.infused(out, t);
    }

    @Override public boolean canCraftInDimensions(int w, int h) { return base.canCraftInDimensions(w, h); }
    @Override public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider regs) { return base.getResultItem(regs); }
    @Override public @NotNull NonNullList<Ingredient> getIngredients() { return base.getIngredients(); }
    @Override public @NotNull NonNullList<ItemStack> getRemainingItems(@NotNull CraftingInput in) { return base.getRemainingItems(in); }
    @Override public @NotNull CraftingBookCategory category() { return base.category(); }
    @Override public @NotNull String getGroup() { return base.getGroup(); }
    @Override public boolean showNotification() { return base.showNotification(); }
    @Override public boolean isSpecial() { return base.isSpecial(); }
    @Override public @NotNull RecipeSerializer<?> getSerializer() { return ModRecipes.INFUSED_CRAFTING_SERIALIZER.get(); }

    public static final class Serializer implements RecipeSerializer<InfusedCraftingRecipe> {
        @Override public @NotNull MapCodec<InfusedCraftingRecipe> codec() { return CODEC; }
        @Override public @NotNull StreamCodec<RegistryFriendlyByteBuf, InfusedCraftingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
