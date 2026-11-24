package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Receta de la Infusora de Ki (Fase 5d de la 0.7.0, 2026-11-23; pedido del usuario: «con recetas por datapack así como en
 * el Mekanism»). Formato calcado del {@code mekanism:metallurgic_infusing} (ítem + químico → ítem), con el fluido de Ki en
 * el papel del químico y la FE y el tiempo por receta (en Mekanism los fija la máquina):
 * <pre>{ "type": "zenkai:ki_infusion",
 *   "item_input":  { "item": "zenkai:katchin_ingot", "count": 1 },
 *   "fluid_input": { "ki": "divine", "amount": 250 },
 *   "output":      { "id": "zenkai:ki_divine_katchin_ingot", "count": 1 },
 *   "energy": 10000, "time": 100 }</pre>
 * {@code item_input} admite {@code "tag"} en vez de {@code "item"} (es un {@link SizedIngredient} plano, como el de
 * Mekanism). {@code energy} es el FE TOTAL, repartido en {@code time} ticks; el fluido se cobra al terminar.
 *
 * La regla «un lingote ya infundido sube directo al nivel del fluido» (decisión del usuario del rework) vive ahora en los
 * DATOS: hay una receta por cada lingote de nivel inferior (tools/gen_ki_infusion_recipes.py). Un datapack puede quitar
 * esas, añadir otras (bloques, hilos, ítems de otros mods) o cambiar costes sin tocar código.
 *
 * Ojo: el id del tipo coincide con el del data component {@code zenkai:ki_infusion} de la armadura; son registros
 * distintos y no chocan.
 */
public record KiInfusionRecipe(SizedIngredient input, KiEnergyType ki, int fluidAmount, ItemStack output, int energy,
                               int time) implements Recipe<KiInfusionRecipe.Input> {

    /** Lo que hay en la máquina: el stack de la entrada y el tipo de Ki del tanque (null = vacío). */
    public record Input(ItemStack stack, @Nullable KiEnergyType ki) implements RecipeInput {
        @Override public @NotNull ItemStack getItem(int index) { return stack; }
        @Override public int size() { return 1; }
    }

    /** {@code { "ki": "divine", "amount": 250 }}: el «chemical_input» de Mekanism. */
    private record FluidInput(KiEnergyType ki, int amount) {
        static final Codec<FluidInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                ArmorImbuingRecipe.KI_CODEC.fieldOf("ki").forGetter(FluidInput::ki),
                Codec.intRange(1, 1_000_000).fieldOf("amount").forGetter(FluidInput::amount)
        ).apply(i, FluidInput::new));
    }

    public static final MapCodec<KiInfusionRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SizedIngredient.FLAT_CODEC.fieldOf("item_input").forGetter(KiInfusionRecipe::input),
            FluidInput.CODEC.fieldOf("fluid_input").forGetter(r -> new FluidInput(r.ki, r.fluidAmount)),
            ItemStack.STRICT_CODEC.fieldOf("output").forGetter(KiInfusionRecipe::output),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("energy", KiInfusion.FE_PER_ITEM).forGetter(KiInfusionRecipe::energy),
            Codec.intRange(1, 72_000).optionalFieldOf("time", KiInfusion.TICKS).forGetter(KiInfusionRecipe::time)
    ).apply(i, (in, fl, out, fe, t) -> new KiInfusionRecipe(in, fl.ki(), fl.amount(), out, fe, t)));

    public static final StreamCodec<RegistryFriendlyByteBuf, KiInfusionRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                SizedIngredient.STREAM_CODEC.encode(buf, r.input);
                buf.writeEnum(r.ki);
                ByteBufCodecs.VAR_INT.encode(buf, r.fluidAmount);
                ItemStack.STREAM_CODEC.encode(buf, r.output);
                ByteBufCodecs.VAR_INT.encode(buf, r.energy);
                ByteBufCodecs.VAR_INT.encode(buf, r.time);
            },
            buf -> new KiInfusionRecipe(SizedIngredient.STREAM_CODEC.decode(buf), buf.readEnum(KiEnergyType.class),
                    ByteBufCodecs.VAR_INT.decode(buf), ItemStack.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)));

    /** FE por tick de trabajo, hacia arriba (la receta nunca sale más barata de lo que dice). */
    public int energyPerTick() { return (int) Math.ceil(energy / (double) time); }

    /** ¿Ese stack es la entrada de esta receta (sin mirar el fluido ni la cantidad)? */
    public boolean acceptsItem(ItemStack stack) { return input.ingredient().test(stack); }

    @Override public boolean matches(@NotNull Input in, @NotNull Level level) {
        return in.ki() == ki && input.test(in.stack());
    }
    @Override public @NotNull ItemStack assemble(@NotNull Input in, HolderLookup.@NotNull Provider regs) { return output.copy(); }
    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider regs) { return output; }
    @Override public boolean isSpecial() { return true; }
    @Override public @NotNull RecipeSerializer<?> getSerializer() { return ModRecipes.KI_INFUSION_SERIALIZER.get(); }
    @Override public @NotNull RecipeType<?> getType() { return ModRecipes.KI_INFUSION.get(); }

    // ── Búsquedas (las comparten máquina, menú, pantalla y gametests) ────────

    /** Receta para esa entrada con ese tipo de Ki en el tanque. */
    public static Optional<RecipeHolder<KiInfusionRecipe>> find(RecipeManager rm, ItemStack stack, @Nullable KiEnergyType ki,
                                                                Level level) {
        if (stack.isEmpty() || ki == null) return Optional.empty();
        return rm.getRecipeFor(ModRecipes.KI_INFUSION.get(), new Input(stack, ki), level);
    }

    /** ¿Alguna receta acepta ese ítem (con cualquier fluido)? Filtro de tolvas, shift-clic y estado «Pon un ítem». */
    public static boolean isInput(RecipeManager rm, ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (RecipeHolder<KiInfusionRecipe> h : rm.getAllRecipesFor(ModRecipes.KI_INFUSION.get())) {
            if (h.value().acceptsItem(stack)) return true;
        }
        return false;
    }

    public static final class Serializer implements RecipeSerializer<KiInfusionRecipe> {
        @Override public @NotNull MapCodec<KiInfusionRecipe> codec() { return CODEC; }
        @Override public @NotNull StreamCodec<RegistryFriendlyByteBuf, KiInfusionRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
