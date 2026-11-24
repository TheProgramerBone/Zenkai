package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.internal.NeoForgeItemTagsProvider;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends ItemTagsProvider {

    /** Gafas de Create (compat opcional): van sobre los ojos, no cubren el cráneo ni se retiran de un cuerpo. */
    private static final ResourceLocation CREATE_GOGGLES = ResourceLocation.fromNamespaceAndPath("create", "goggles");

    public ModItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                              CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, Zenkai.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {

        // Menas del HFIL (HfilOres): mismas etiquetas de objeto que su mena vanilla (con toque de
        // seda se recogen como bloque; recetas y mods que piden "#c:ores/iron" las aceptan).
        ModBlocks.HFIL_ORES.values().forEach(m -> m.forEach((type, ore) -> {
            if (type.vanillaItemTag != null) tag(type.vanillaItemTag).add(ore.asItem());
            // Solo #c:ores/<mena>; ver la nota equivalente en ModBlockTagProvider.
            if (type.commonItemTag != null) tag(type.commonItemTag).add(ore.asItem());
        }));

        // ==========================================
        // 1. HERRAMIENTAS Y ARMADURAS
        // ==========================================


        // ==========================================
        // 2. ÍTEMS ESPECIALES Y COSMÉTICOS
        // ==========================================

        // Katchin de cualquier nivel (rework del 2026-11-23): material de las recetas de fabricación. La máquina solo
        // cuenta el ítem de la primera ranura que casa (sin mezcla de niveles).
        var ingots = tag(com.hmc.zenkai.feature.armor.KiInfusion.INGOTS).add(ModItems.KATCHIN_INGOT.get());
        ModItems.INFUSED_KATCHIN_INGOTS.values().forEach(i -> ingots.add(i.get()));
        var threads = tag(com.hmc.zenkai.feature.armor.KiInfusion.THREADS).add(ModItems.KATCHIN_THREAD.get());
        ModItems.INFUSED_KATCHIN_THREADS.values().forEach(i -> threads.add(i.get()));
        var cloths = tag(com.hmc.zenkai.feature.armor.KiInfusion.CLOTHS).add(ModItems.KATCHIN_CLOTH.get());
        ModItems.INFUSED_KATCHIN_CLOTHS.values().forEach(i -> cloths.add(i.get()));
        // Polvos del Triturador (Fase 5d): etiquetas comunes para que Mekanism, Create y otros mods los reconozcan.
        var dustKatchin = net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "dusts/katchin"));
        var dustCrystal = net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "dusts/energy_crystal"));
        tag(dustKatchin).add(ModItems.KATCHIN_DUST.get());
        tag(dustCrystal).add(ModItems.ENERGY_CRYSTAL_DUST.get());
        tag(net.neoforged.neoforge.common.Tags.Items.DUSTS).addTags(dustKatchin, dustCrystal);
        // Sets de Zenkai: su tope de puntos sale solo de la infusión (decisión del usuario 2026-11-23).
        tag(com.hmc.zenkai.feature.armor.KiInfusion.UNCAPPED).add(
                ModItems.SAIYAN_ARMOR_HELMET.get(), ModItems.SAIYAN_ARMOR_CHESTPLATE.get(),
                ModItems.SAIYAN_ARMOR_LEGGINGS.get(), ModItems.SAIYAN_ARMOR_BOOTS.get(),
                ModItems.NAMEK_CLOTHES_HELMET.get(), ModItems.NAMEK_CLOTHES_CHESTPLATE.get(),
                ModItems.NAMEK_CLOTHES_LEGGINGS.get(), ModItems.NAMEK_CLOTHES_BOOTS.get(),
                ModItems.TRAINING_GI_HELMET.get(), ModItems.TRAINING_GI_CHESTPLATE.get(),
                ModItems.TRAINING_GI_LEGGINGS.get(), ModItems.TRAINING_GI_BOOTS.get());

        tag(ItemTags.DYEABLE).add(
                ModItems.SCOUTER.get()
        );

        // Accesorios de cabeza que NO cubren el cráneo (ver HairGeoLayer): el scouter es un
        // visor sobre un ojo y el halo flota por encima, ninguno de los dos reemplaza el
        // cráneo como sí hacen los cuerpos raciales (que también son HELMET pero SON la
        // cabeza, no van sobre ella) — esos dos siguen ocultando el pelo sin necesidad de tag.
        tag(ModTags.Items.KEEPS_HAIR).add(
                ModItems.SCOUTER.get(),
                ModItems.HALO.get()
        ).addOptional(CREATE_GOGGLES);

        // El scouter se apoya en la oreja izquierda: la raza que la tenga (Namekiano) la oculta mientras lo lleva.
        tag(ModTags.Items.COVERS_EAR).add(
                ModItems.SCOUTER.get()
        );

        // «take_all» de un cuerpo nunca retira estos de quien los lleva (ver ArmorCompare).
        tag(ModTags.Items.NO_ARMOR_SWAP).add(
                ModItems.SCOUTER.get(),
                ModItems.HALO.get(),
                ModItems.WEIGHTED_STRAPS.get(),
                ModItems.WEIGHTED_CAPE.get()
        ).addOptional(CREATE_GOGGLES);

        // ==========================================
        // 3. SET DE MADERA DE AJISA Y VEGETACIÓN
        // ==========================================

        tag(ItemTags.LOGS_THAT_BURN).add(
                ModBlocks.AJISA_LOG.get().asItem(),
                ModBlocks.AJISA_WOOD.get().asItem(),
                ModBlocks.STRIPPED_AJISA_LOG.get().asItem(),
                ModBlocks.STRIPPED_AJISA_WOOD.get().asItem()
        );

        tag(ItemTags.PLANKS).add(ModBlocks.AJISA_PLANKS.get().asItem());
        tag(ItemTags.LEAVES).add(ModBlocks.AJISA_LEAVES.get().asItem());
        tag(ItemTags.SAPLINGS).add(ModBlocks.AJISA_SAPLING.get().asItem());
        tag(ItemTags.WOODEN_SLABS).add(ModBlocks.AJISA_SLAB.get().asItem());
        tag(ItemTags.WOODEN_STAIRS).add(ModBlocks.AJISA_STAIRS.get().asItem());
        tag(ItemTags.WOODEN_FENCES).add(ModBlocks.AJISA_FENCE.get().asItem());
        tag(ItemTags.FENCE_GATES).add(ModBlocks.AJISA_FENCE_GATE.get().asItem());
        tag(ItemTags.WOODEN_DOORS).add(ModBlocks.AJISA_DOOR.get().asItem());
        tag(ItemTags.WOODEN_TRAPDOORS).add(ModBlocks.AJISA_TRAPDOOR.get().asItem());
        tag(ItemTags.WOODEN_BUTTONS).add(ModBlocks.AJISA_BUTTON.get().asItem());
        tag(ItemTags.WOODEN_PRESSURE_PLATES).add(ModBlocks.AJISA_PRESSURE_PLATE.get().asItem());
        tag(ItemTags.SMALL_FLOWERS).add(ModBlocks.AJISA_FLOWER.get().asItem());
        tag(ItemTags.VILLAGER_PLANTABLE_SEEDS).add(ModItems.NAMEKIAN_HERB_SEEDS.get());

        // ==========================================
        // 4. BLOQUES DE CONSTRUCCIÓN (ÍTEMS)
        // ==========================================

        tag(Tags.Items.ORES_COAL).add(
                ModBlocks.NAMEKIAN_COAL_ORE.asItem()
        );

        tag(Tags.Items.ORES_IRON).add(
                ModBlocks.NAMEKIAN_IRON_ORE.asItem()
        );

        tag(Tags.Items.ORES_LAPIS).add(
                ModBlocks.NAMEKIAN_LAPIS_ORE.asItem()
        );

        tag(Tags.Items.ORES_REDSTONE).add(
                ModBlocks.NAMEKIAN_REDSTONE_ORE.asItem()
        );

        tag(Tags.Items.ORES_COPPER).add(
                ModBlocks.NAMEKIAN_COPPER_ORE.asItem()
        );

        tag(Tags.Items.ORES_GOLD).add(
                ModBlocks.NAMEKIAN_GOLD_ORE.asItem()
        );

        tag(Tags.Items.ORES_DIAMOND).add(
                ModBlocks.NAMEKIAN_DIAMOND_ORE.asItem()
        );

        tag(ItemTags.WALLS).add(
                ModBlocks.SACRED_STONE_WALL.get().asItem(),
                ModBlocks.POLISHED_SACRED_STONE_WALL.get().asItem(),
                ModBlocks.SACRED_STONE_BRICK_WALL.get().asItem(),
                ModBlocks.CUT_KATCHIN_WALL.get().asItem()
        );

        tag(ItemTags.STAIRS).add(
                ModBlocks.SACRED_STONE_STAIRS.get().asItem(),
                ModBlocks.POLISHED_SACRED_STONE_STAIRS.get().asItem(),
                ModBlocks.SACRED_STONE_BRICK_STAIRS.get().asItem(),
                ModBlocks.CUT_KATCHIN_STAIRS.get().asItem()
        );

        tag(ItemTags.SLABS).add(
                ModBlocks.SACRED_STONE_SLAB.get().asItem(),
                ModBlocks.POLISHED_SACRED_STONE_SLAB.get().asItem(),
                ModBlocks.SACRED_STONE_BRICK_SLAB.get().asItem(),
                ModBlocks.CUT_KATCHIN_SLAB.get().asItem()
        );

        // Piedras y arena del HFIL (2026-10-04): los mismos tags de objeto que sus tags de bloque
        // (ver ModBlockTagProvider 5b), copiados de ahí para que no se desincronicen.
        copy(Tags.Blocks.STONES, Tags.Items.STONES);
        copy(Tags.Blocks.SANDSTONE_BLOCKS, Tags.Items.SANDSTONE_BLOCKS);
        copy(BlockTags.SAND, ItemTags.SAND);
        // La roca calcinada y la pálida hacen de "cobblestone" para herramientas de piedra y
        // para los bloques que piden piedra (horno, destilador, etc.).
        tag(ItemTags.STONE_TOOL_MATERIALS).add(
                ModBlocks.HFIL_SCORCHED_STONE.asItem(),
                ModBlocks.HFIL_PALE_STONE.asItem()
        );
        tag(ItemTags.STONE_CRAFTING_MATERIALS).add(
                ModBlocks.HFIL_SCORCHED_STONE.asItem(),
                ModBlocks.HFIL_PALE_STONE.asItem()
        );

        for (var fam : ModBlocks.buildingFamilies()) {
            tag(ItemTags.STAIRS).add(fam.stairs().get().asItem());
            tag(ItemTags.SLABS).add(fam.slab().get().asItem());
            tag(ItemTags.WALLS).add(fam.wall().get().asItem());
        }

        // ==========================================
        // 5. TAGS PERSONALIZADOS DEL MOD (ZENKAI)
        // ==========================================

        tag(ModTags.Items.WORLDGEN_DEBRIS)
                .add(
                        Items.STICK,
                        Items.SHORT_GRASS,
                        Items.TALL_GRASS,
                        Items.DEAD_BUSH,
                        Items.PINK_PETALS
                )
                .addTag(ItemTags.SMALL_FLOWERS)
                .addTag(ItemTags.SAPLINGS)
                .addTag(ItemTags.LEAVES);

        tag(ModTags.Items.WORLDGEN_DEBRIS).add(
                Items.SHORT_GRASS,
                Items.TALL_GRASS,
                Items.DEAD_BUSH,
                Items.PINK_PETALS
        );

        tag(ModTags.Items.AJISA_LOGS).add(
                ModBlocks.AJISA_LOG.get().asItem(),
                ModBlocks.AJISA_WOOD.get().asItem(),
                ModBlocks.STRIPPED_AJISA_LOG.get().asItem(),
                ModBlocks.STRIPPED_AJISA_WOOD.get().asItem()
        );

        tag(ModTags.Items.DRAGON_BALLS_ITEM).add(
                ModBlocks.DRAGON_BALL_1.get().asItem(),
                ModBlocks.DRAGON_BALL_2.get().asItem(),
                ModBlocks.DRAGON_BALL_3.get().asItem(),
                ModBlocks.DRAGON_BALL_4.get().asItem(),
                ModBlocks.DRAGON_BALL_5.get().asItem(),
                ModBlocks.DRAGON_BALL_6.get().asItem(),
                ModBlocks.DRAGON_BALL_7.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_1.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_2.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_3.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_4.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_5.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_6.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_7.get().asItem()
        );

        // Deseo "Quiero más de...": las esferas, las siete fundidas y las piedras que las sueltan no se duplican.
        tag(ModTags.Items.WISH_BANNED)
                .addTag(ModTags.Items.DRAGON_BALLS_ITEM)
                .add(
                        ModItems.ALL_DRAGON_BALLS_ITEM.get(),
                        ModItems.NAMEK_ALL_DRAGON_BALLS_ITEM.get(),
                        ModBlocks.DRAGON_BALL_STONE.get().asItem(),
                        ModBlocks.NAMEK_DRAGON_BALL_STONE.get().asItem()
                );
    }
}