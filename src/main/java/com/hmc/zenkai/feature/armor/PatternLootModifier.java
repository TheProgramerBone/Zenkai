package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.Zenkai;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Mete un patrón de armadura en cofres (Fase 5b de la 0.7.0): los patrones raros de personajes «se encuentran en cofres»
 * (loot de estructuras de Zenkai y de las vanilla). Modificador global y no edición de loot tables: no pisa las de
 * vanilla ni las de otros mods, y cada patrón nuevo es UN JSON en {@code data/<ns>/loot_modifiers/} más su entrada en
 * {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}, con las condiciones (qué cofres) en el propio JSON.
 * <pre>{ "type": "zenkai:add_armor_pattern", "conditions": [ ... ], "pattern": "zenkai:training_gi", "chance": 0.08 }</pre>
 */
public final class PatternLootModifier extends LootModifier {

    public static final MapCodec<PatternLootModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).and(i.group(
            ResourceLocation.CODEC.fieldOf("pattern").forGetter(m -> m.pattern),
            Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1f).forGetter(m -> m.chance)
    )).apply(i, PatternLootModifier::new));

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Zenkai.MOD_ID);
    public static final Supplier<MapCodec<PatternLootModifier>> ADD_ARMOR_PATTERN =
            SERIALIZERS.register("add_armor_pattern", () -> CODEC);

    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }

    private final ResourceLocation pattern;
    private final float chance;

    public PatternLootModifier(LootItemCondition[] conditions, ResourceLocation pattern, float chance) {
        super(conditions);
        this.pattern = pattern;
        this.chance = chance;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(@NotNull ObjectArrayList<ItemStack> loot, @NotNull LootContext ctx) {
        if (ctx.getRandom().nextFloat() < chance) loot.add(ArmorSets.pattern(pattern));
        return loot;
    }

    @Override
    public @NotNull MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
