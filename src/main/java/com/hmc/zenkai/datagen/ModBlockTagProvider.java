package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {

    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Zenkai.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {

        // ==========================================
        // 1. HERRAMIENTAS Y HERRAMIENTAS REQUERIDAS
        // ==========================================

        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                // Bloques base y de construcción
                ModBlocks.ROCKY_BLOCK.get(),
                ModBlocks.HFIL_SCORCHED_STONE.get(),
                ModBlocks.HFIL_SPIKE_ROCK.get(),
                ModBlocks.HFIL_CINDER_SANDSTONE.get(),
                ModBlocks.NAMEKIAN_STONE.get(),
                ModBlocks.NAMEKIAN_COBBLESTONE.get(),
                ModBlocks.SCOUTER_BENCH.get(),
                ModBlocks.ENERGY_GENERATOR.get(),

                // Minerales de Namek
                ModBlocks.NAMEKIAN_COAL_ORE.get(),
                ModBlocks.NAMEKIAN_IRON_ORE.get(),
                ModBlocks.NAMEKIAN_COPPER_ORE.get(),
                ModBlocks.NAMEKIAN_GOLD_ORE.get(),
                ModBlocks.NAMEKIAN_REDSTONE_ORE.get(),
                ModBlocks.NAMEKIAN_LAPIS_ORE.get(),
                ModBlocks.NAMEKIAN_DIAMOND_ORE.get(),

                // Cristal y Piedra Sagrada
                ModBlocks.NAMEK_CRYSTAL_ORE.get(),
                ModBlocks.ENERGY_CRYSTAL_ORE.get(),
                ModBlocks.SACRED_STONE_ORE.get(),
                ModBlocks.NAMEK_CRYSTAL_BLOCK.get(),
                ModBlocks.ENERGY_CRYSTAL_BLOCK.get(),
                ModBlocks.SACRED_STONE_BLOCK.get(),
                ModBlocks.SACRED_STONE_STAIRS.get(),
                ModBlocks.SACRED_STONE_SLAB.get(),
                ModBlocks.SACRED_STONE_WALL.get(),
                ModBlocks.POLISHED_SACRED_STONE.get(),
                ModBlocks.POLISHED_SACRED_STONE_STAIRS.get(),
                ModBlocks.POLISHED_SACRED_STONE_SLAB.get(),
                ModBlocks.POLISHED_SACRED_STONE_WALL.get(),
                ModBlocks.SACRED_STONE_BRICKS.get(),
                ModBlocks.SACRED_STONE_BRICK_STAIRS.get(),
                ModBlocks.SACRED_STONE_BRICK_SLAB.get(),
                ModBlocks.SACRED_STONE_BRICK_WALL.get(),
                ModBlocks.NAMEKIAN_LAMP.get()
        );

        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(
                ModBlocks.NAMEKIAN_DIRT.get(),
                ModBlocks.NAMEKIAN_DIRT_PATH.get(),
                ModBlocks.NAMEKIAN_GRASS_BLOCK.get(),
                ModBlocks.NAMEKIAN_SAND.get(),
                ModBlocks.NAMEKIAN_GRAVEL.get(),
                ModBlocks.HFIL_CINDER_SAND.get()
        );

        tag(BlockTags.MINEABLE_WITH_AXE).add(
                ModBlocks.AJISA_LOG.get(),
                ModBlocks.AJISA_WOOD.get(),
                ModBlocks.STRIPPED_AJISA_LOG.get(),
                ModBlocks.STRIPPED_AJISA_WOOD.get(),
                ModBlocks.AJISA_PLANKS.get(),
                ModBlocks.AJISA_SLAB.get(),
                ModBlocks.AJISA_STAIRS.get(),
                ModBlocks.AJISA_FENCE.get(),
                ModBlocks.AJISA_FENCE_GATE.get(),
                ModBlocks.AJISA_DOOR.get(),
                ModBlocks.AJISA_TRAPDOOR.get(),
                ModBlocks.AJISA_BUTTON.get(),
                ModBlocks.AJISA_PRESSURE_PLATE.get()
        );

        tag(BlockTags.MINEABLE_WITH_HOE).add(
                ModBlocks.AJISA_LEAVES.get()
        );

        // ==========================================
        // 2. NIVELES DE HERRAMIENTA (TIERS)
        // ==========================================

        tag(BlockTags.NEEDS_STONE_TOOL).add(
                ModBlocks.NAMEKIAN_IRON_ORE.get(),
                ModBlocks.NAMEKIAN_COPPER_ORE.get(),
                ModBlocks.NAMEKIAN_LAPIS_ORE.get(),
                ModBlocks.SACRED_STONE_ORE.get()
        );

        tag(BlockTags.NEEDS_IRON_TOOL).add(

                ModBlocks.NAMEKIAN_GOLD_ORE.get(),
                ModBlocks.NAMEKIAN_REDSTONE_ORE.get(),
                ModBlocks.NAMEKIAN_DIAMOND_ORE.get(),
                ModBlocks.NAMEK_CRYSTAL_ORE.get(),
                ModBlocks.NAMEK_CRYSTAL_BLOCK.get()
        );

        tag(BlockTags.NEEDS_DIAMOND_TOOL).add(
                ModBlocks.KATCHIN_ORE.get(),
                ModBlocks.DEEPSLATE_KATCHIN_ORE.get(),
                ModBlocks.KATCHIN_BLOCK.get(),
                ModBlocks.CUT_KATCHIN.get(),
                ModBlocks.CUT_KATCHIN_STAIRS.get(),
                ModBlocks.CUT_KATCHIN_SLAB.get(),
                ModBlocks.CUT_KATCHIN_WALL.get(),
                ModBlocks.KATCHIN_PILLAR.get(),
                ModBlocks.ENERGY_CRYSTAL_ORE.get(),
                ModBlocks.ENERGY_CRYSTAL_BLOCK.get()
        );

        // ==========================================
        // 3. SET DE MADERA DE AJISA Y VEGETACIÓN
        // ==========================================

        tag(BlockTags.LOGS_THAT_BURN).add(
                ModBlocks.AJISA_LOG.get(),
                ModBlocks.AJISA_WOOD.get(),
                ModBlocks.STRIPPED_AJISA_LOG.get(),
                ModBlocks.STRIPPED_AJISA_WOOD.get()
        );

        tag(BlockTags.PLANKS).add(ModBlocks.AJISA_PLANKS.get());
        tag(BlockTags.LEAVES).add(ModBlocks.AJISA_LEAVES.get());
        tag(BlockTags.SAPLINGS).add(ModBlocks.AJISA_SAPLING.get());
        tag(BlockTags.WOODEN_SLABS).add(ModBlocks.AJISA_SLAB.get());
        tag(BlockTags.WOODEN_STAIRS).add(ModBlocks.AJISA_STAIRS.get());
        tag(BlockTags.WOODEN_FENCES).add(ModBlocks.AJISA_FENCE.get());
        tag(BlockTags.FENCE_GATES).add(ModBlocks.AJISA_FENCE_GATE.get());
        tag(BlockTags.WOODEN_DOORS).add(ModBlocks.AJISA_DOOR.get());
        tag(BlockTags.WOODEN_TRAPDOORS).add(ModBlocks.AJISA_TRAPDOOR.get());
        tag(BlockTags.WOODEN_BUTTONS).add(ModBlocks.AJISA_BUTTON.get());
        tag(BlockTags.WOODEN_PRESSURE_PLATES).add(ModBlocks.AJISA_PRESSURE_PLATE.get());
        tag(BlockTags.SMALL_FLOWERS).add(ModBlocks.AJISA_FLOWER.get());

        // ==========================================
        // 4. BLOQUES DE CONSTRUCCIÓN Y ESTRUCTURALES
        // ==========================================

        tag(BlockTags.DIRT).add(
                ModBlocks.NAMEKIAN_DIRT.get(),
                ModBlocks.NAMEKIAN_GRASS_BLOCK.get()
        );

        tag(Tags.Blocks.SANDS).add(
                ModBlocks.NAMEKIAN_SAND.get(),
                ModBlocks.HFIL_CINDER_SAND.get()
        );

        tag(Tags.Blocks.GRAVELS).add(
                ModBlocks.NAMEKIAN_GRAVEL.get()
        );

        tag(Tags.Blocks.STONES).add(
                ModBlocks.NAMEKIAN_STONE.get()
        );

        tag(Tags.Blocks.COBBLESTONES).add(
                ModBlocks.NAMEKIAN_COBBLESTONE.get()
        );

        tag(BlockTags.WALLS).add(
                ModBlocks.SACRED_STONE_WALL.get(),
                ModBlocks.POLISHED_SACRED_STONE_WALL.get(),
                ModBlocks.SACRED_STONE_BRICK_WALL.get(),
                ModBlocks.CUT_KATCHIN_WALL.get()
        );

        tag(BlockTags.STAIRS).add(
                ModBlocks.SACRED_STONE_STAIRS.get(),
                ModBlocks.POLISHED_SACRED_STONE_STAIRS.get(),
                ModBlocks.SACRED_STONE_BRICK_STAIRS.get(),
                ModBlocks.CUT_KATCHIN_STAIRS.get()
        );

        tag(BlockTags.SLABS).add(
                ModBlocks.SACRED_STONE_SLAB.get(),
                ModBlocks.POLISHED_SACRED_STONE_SLAB.get(),
                ModBlocks.SACRED_STONE_BRICK_SLAB.get(),
                ModBlocks.CUT_KATCHIN_SLAB.get()
        );

        tag(BlockTags.SLABS).add(
                ModBlocks.SACRED_STONE_SLAB.get(),
                ModBlocks.POLISHED_SACRED_STONE_SLAB.get(),
                ModBlocks.SACRED_STONE_BRICK_SLAB.get()
        );

        tag(Tags.Blocks.ORES_COAL).add(
                ModBlocks.NAMEKIAN_COAL_ORE.get()
        );

        tag(Tags.Blocks.ORES_IRON).add(
                ModBlocks.NAMEKIAN_IRON_ORE.get()
        );

        tag(Tags.Blocks.ORES_LAPIS).add(
                ModBlocks.NAMEKIAN_LAPIS_ORE.get()
        );

        tag(Tags.Blocks.ORES_REDSTONE).add(
                ModBlocks.NAMEKIAN_REDSTONE_ORE.get()
        );

        tag(Tags.Blocks.ORES_COPPER).add(
                ModBlocks.NAMEKIAN_COPPER_ORE.get()
        );

        tag(Tags.Blocks.ORES_GOLD).add(
                ModBlocks.NAMEKIAN_GOLD_ORE.get()
        );

        tag(Tags.Blocks.ORES_DIAMOND).add(
                ModBlocks.NAMEKIAN_DIAMOND_ORE.get()
        );

        // Concreto estructural: pico obligatorio (lo pide requiresCorrectToolForDrops) y
        // tags de familia para que funcionen recetas, cantero y mods de construcción.
        for (var fam : ModBlocks.STRUCTURAL_CONCRETE_FAMILIES) {
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                    fam.block().get(), fam.stairs().get(), fam.slab().get(), fam.wall().get());
            tag(BlockTags.STAIRS).add(fam.stairs().get());
            tag(BlockTags.SLABS).add(fam.slab().get());
            tag(BlockTags.WALLS).add(fam.wall().get());
        }

        // ==========================================
        // 5. GENERACIÓN DE MUNDO Y ENTORNO
        // ==========================================

        tag(BlockTags.SCULK_REPLACEABLE).add(
                ModBlocks.ROCKY_BLOCK.get(),
                ModBlocks.NAMEKIAN_GRASS_BLOCK.get(),
                ModBlocks.NAMEKIAN_STONE.get(),
                ModBlocks.NAMEKIAN_COBBLESTONE.get(),
                ModBlocks.NAMEKIAN_DIRT.get()
        );

        tag(BlockTags.CROPS).add(
                ModBlocks.NAMEKIAN_HERB_CROP.get());

        tag(BlockTags.DEAD_BUSH_MAY_PLACE_ON).add(
                ModBlocks.ROCKY_BLOCK.get()
        );

        tag(BlockTags.OVERWORLD_CARVER_REPLACEABLES).add(
                ModBlocks.NAMEKIAN_STONE.get(),
                ModBlocks.NAMEKIAN_DIRT.get(),
                ModBlocks.NAMEKIAN_GRASS_BLOCK.get(),
                ModBlocks.NAMEKIAN_SAND.get(),
                ModBlocks.NAMEKIAN_GRAVEL.get()
        );

        // ==========================================
        // 6. TAGS PERSONALIZADOS DEL MOD (ZENKAI)
        // ==========================================

        tag(ModTags.Blocks.KI_INDESTRUCTIBLE).add(
                ModBlocks.KATCHIN_ORE.get(),
                ModBlocks.DEEPSLATE_KATCHIN_ORE.get(),
                ModBlocks.KATCHIN_BLOCK.get(),
                ModBlocks.CUT_KATCHIN.get(),
                ModBlocks.CUT_KATCHIN_STAIRS.get(),
                ModBlocks.CUT_KATCHIN_SLAB.get(),
                ModBlocks.CUT_KATCHIN_WALL.get(),
                ModBlocks.KATCHIN_PILLAR.get()
        );

        tag(ModTags.Blocks.NAMEKIAN_ORE_REPLACEABLES).add(
                ModBlocks.NAMEKIAN_STONE.get()
        );

        // CRÍTICO: HFIL_SCORCHED_STONE pasa a ser el default_block de otherworld_noise.json
        // (todo el relleno subterráneo del Otherworld, no solo una veneer de superficie como
        // ROCKY_BLOCK). Los ores vanilla del HFIL (BiomeDefaultFeatures.addDefaultOres) Y el
        // Katchin propio (KATCHIN_ORE_HFIL, ver ModConfiguredFeatures) buscan bloque objetivo
        // vía este mismo tag — sin esta entrada, NINGÚN ore del HFIL encontraría dónde
        // generarse, exactamente el bug ya documentado en CLAUDE.md (ronda 6, ahí fue solo
        // Katchin porque el default_block seguía siendo minecraft:stone; aquí sería TODOS).
        tag(BlockTags.STONE_ORE_REPLACEABLES).add(
                ModBlocks.HFIL_SCORCHED_STONE.get()
        );

        // IGUAL DE CRÍTICO y encontrado tarde (bug reportado en juego, "no hay cuevas, todo
        // está cubierto por scorched stone"): los carvers (cave/cave_extra_underground/canyon,
        // vía BiomeDefaultFeatures.addDefaultCarversAndLakes en ModBiomeGen.hfilBase) solo
        // pueden tallar aire en bloques listados en este tag — es el mismo mecanismo que
        // STONE_ORE_REPLACEABLES arriba pero para EXCAVAR en vez de para generar menas. Sin
        // esta entrada, un carver que se topa con HFIL_SCORCHED_STONE simplemente no lo toca:
        // el hueco de la cueva nunca se abre, aunque el carver "pase por ahí" con normalidad.
        tag(BlockTags.OVERWORLD_CARVER_REPLACEABLES).add(
                ModBlocks.HFIL_SCORCHED_STONE.get()
        );

        // HFIL_CINDER_SANDSTONE (Fase 4 del rework, hfil_cinder_dunes) SÍ lleva estos dos tags,
        // a diferencia de la decisión original — la capa sólida bajo la arena de las dunas
        // (~6-7 bloques, ver la surface_rule de otherworld_noise.json) es la MISMA profundidad
        // estructural que HFIL_SCORCHED_STONE en blood_shore, pero sin el tag no era ni minable
        // (ningún ore, ni vanilla ni Katchin, la reemplaza) ni excavable por carvers — una franja
        // "muerta" cerca de la superficie de cinder_dunes que no existía en los otros dos biomas.
        // HFIL_CINDER_SAND (la capa de arriba, la que cae de verdad) se queda FUERA de ambos
        // tags a propósito, igual que minecraft:red_sand tampoco los lleva en vainilla — solo la
        // roca sólida debajo debe ser minable/excavable, no la arena suelta.
        tag(BlockTags.STONE_ORE_REPLACEABLES).add(
                ModBlocks.HFIL_CINDER_SANDSTONE.get()
        );
        tag(BlockTags.OVERWORLD_CARVER_REPLACEABLES).add(
                ModBlocks.HFIL_CINDER_SANDSTONE.get()
        );

        tag(ModTags.Blocks.DRAGON_BALLS_BLOCK).add(
                ModBlocks.DRAGON_BALL_1.get(),
                ModBlocks.DRAGON_BALL_2.get(),
                ModBlocks.DRAGON_BALL_3.get(),
                ModBlocks.DRAGON_BALL_4.get(),
                ModBlocks.DRAGON_BALL_5.get(),
                ModBlocks.DRAGON_BALL_6.get(),
                ModBlocks.DRAGON_BALL_7.get(),
                ModBlocks.NAMEK_DRAGON_BALL_1.get(),
                ModBlocks.NAMEK_DRAGON_BALL_2.get(),
                ModBlocks.NAMEK_DRAGON_BALL_3.get(),
                ModBlocks.NAMEK_DRAGON_BALL_4.get(),
                ModBlocks.NAMEK_DRAGON_BALL_5.get(),
                ModBlocks.NAMEK_DRAGON_BALL_6.get(),
                ModBlocks.NAMEK_DRAGON_BALL_7.get(),
                ModBlocks.ALL_DRAGON_BALLS.get()
        );
    }
}