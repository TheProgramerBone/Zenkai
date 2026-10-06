package com.hmc.zenkai.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.function.Supplier;

/**
 * Menas propias del HFIL: las 7 menas vanilla + el Katchin, una variante por cada piedra del
 * HFIL (24 bloques, en ModBlocks.HFIL_ORES). Petición del usuario (2026-10-02): las menas vanilla del HFIL
 * se veían como parches de piedra gris del Overworld sobre la roca del bioma. Mismo
 * comportamiento que la vanilla (drops, fortuna, experiencia, herramienta, tags), distinto
 * fondo. Texturas: tools/gen_hfil_ores.py. Las generan en worldgen las features hfil_*_ore de
 * ModConfiguredFeatures, que sustituyen a BiomeDefaultFeatures.addDefaultOres en el HFIL.
 */
public final class HfilOres {
    private HfilOres() {}

    /**
     * Piedra del HFIL sobre la que se asienta la mena. {@code host} es PEREZOSO a propósito:
     * ModBlocks construye HFIL_ORES recorriendo este enum durante su propia inicialización; si
     * el enum guardara los campos de ModBlocks directamente y alguien lo tocara antes que a
     * ModBlocks, esos campos llegarían a null (inicialización circular de clases).
     */
    public enum Stone {
        /** Relleno del subsuelo entero del Otherworld (default_block). */
        SCORCHED("scorched", () -> ModBlocks.HFIL_SCORCHED_STONE.get(), MapColor.NETHER),
        /** Suelo blanco de needle_wastes (~6 bloques de profundidad). */
        PALE("pale", () -> ModBlocks.HFIL_PALE_STONE.get(), MapColor.SNOW),
        /** Capa sólida bajo la arena de cinder_dunes. */
        CINDER("cinder", () -> ModBlocks.HFIL_CINDER_SANDSTONE.get(), MapColor.COLOR_BROWN);

        public final String key;
        public final Supplier<Block> host;
        public final MapColor mapColor;

        Stone(String key, Supplier<Block> host, MapColor mapColor) {
            this.key = key;
            this.host = host;
            this.mapColor = mapColor;
        }
    }

    /** Mena vanilla de la que es variante, con lo que hace falta para replicar su comportamiento. */
    public enum Type {
        COAL("coal", () -> Blocks.COAL_ORE, UniformInt.of(0, 2), () -> Items.COAL, 0.1f,
                BlockTags.COAL_ORES, ItemTags.COAL_ORES, Tags.Blocks.ORES_COAL, Tags.Items.ORES_COAL, null),
        IRON("iron", () -> Blocks.IRON_ORE, ConstantInt.of(0), () -> Items.IRON_INGOT, 0.7f,
                BlockTags.IRON_ORES, ItemTags.IRON_ORES, Tags.Blocks.ORES_IRON, Tags.Items.ORES_IRON, BlockTags.NEEDS_STONE_TOOL),
        COPPER("copper", () -> Blocks.COPPER_ORE, ConstantInt.of(0), () -> Items.COPPER_INGOT, 0.7f,
                BlockTags.COPPER_ORES, ItemTags.COPPER_ORES, Tags.Blocks.ORES_COPPER, Tags.Items.ORES_COPPER, BlockTags.NEEDS_STONE_TOOL),
        GOLD("gold", () -> Blocks.GOLD_ORE, ConstantInt.of(0), () -> Items.GOLD_INGOT, 1.0f,
                BlockTags.GOLD_ORES, ItemTags.GOLD_ORES, Tags.Blocks.ORES_GOLD, Tags.Items.ORES_GOLD, BlockTags.NEEDS_IRON_TOOL),
        REDSTONE("redstone", () -> Blocks.REDSTONE_ORE, null, () -> Items.REDSTONE, 0.7f,
                BlockTags.REDSTONE_ORES, ItemTags.REDSTONE_ORES, Tags.Blocks.ORES_REDSTONE, Tags.Items.ORES_REDSTONE, BlockTags.NEEDS_IRON_TOOL),
        LAPIS("lapis", () -> Blocks.LAPIS_ORE, UniformInt.of(2, 5), () -> Items.LAPIS_LAZULI, 0.2f,
                BlockTags.LAPIS_ORES, ItemTags.LAPIS_ORES, Tags.Blocks.ORES_LAPIS, Tags.Items.ORES_LAPIS, BlockTags.NEEDS_STONE_TOOL),
        DIAMOND("diamond", () -> Blocks.DIAMOND_ORE, UniformInt.of(3, 7), () -> Items.DIAMOND, 1.0f,
                BlockTags.DIAMOND_ORES, ItemTags.DIAMOND_ORES, Tags.Blocks.ORES_DIAMOND, Tags.Items.ORES_DIAMOND, BlockTags.NEEDS_IRON_TOOL),
        /**
         * Katchin (petición del usuario, 2026-10-02): el mineral propio del mod también cambia de
         * fondo según la piedra. Copia las propiedades de KATCHIN_ORE (dureza 30, sonido de
         * netherita), no las de una vanilla. Sin fundición directa (como KATCHIN_ORE: se funde el
         * katchin crudo), sin etiqueta vanilla, y su #c:ores/katchin está ESCRITO A MANO en
         * data/c/tags/{block,item}/ores/katchin.json (generarlo chocaría con ese archivo).
         */
        KATCHIN("katchin", () -> ModBlocks.KATCHIN_ORE.get(), UniformInt.of(3, 7), null, 0f,
                null, null, null, null, BlockTags.NEEDS_DIAMOND_TOOL, ModTags.Blocks.KI_INDESTRUCTIBLE);

        public final String key;
        /** La mena de la que es variante: se copian sus propiedades (dureza, sonido, luz de la
         *  redstone...). Perezosa: KATCHIN apunta a un bloque del propio mod. */
        public final Supplier<Block> vanilla;
        /** Experiencia al romperla; null = la gestiona la propia clase (RedStoneOreBlock). */
        public final IntProvider xp;
        /** Resultado de fundirla (horno/alto horno), igual que la vanilla; null = no se funde. */
        public final Supplier<Item> smeltResult;
        public final float smeltXp;
        /** Etiquetas de la mena original; null = no tiene (o, en las comunes, van escritas a mano). */
        public final TagKey<Block> vanillaBlockTag;
        public final TagKey<Item> vanillaItemTag;
        public final TagKey<Block> commonBlockTag;
        public final TagKey<Item> commonItemTag;
        /** Nivel de herramienta exigido; null = cualquier pico (carbón). */
        public final TagKey<Block> toolTag;
        /** Otras etiquetas de bloque de la mena original (p. ej. la del Katchin, indestructible por ki). */
        public final java.util.List<TagKey<Block>> extraBlockTags;

        @SafeVarargs
        Type(String key, Supplier<Block> vanilla, IntProvider xp, Supplier<Item> smeltResult, float smeltXp,
             TagKey<Block> vanillaBlockTag, TagKey<Item> vanillaItemTag, TagKey<Block> commonBlockTag,
             TagKey<Item> commonItemTag, TagKey<Block> toolTag, TagKey<Block>... extraBlockTags) {
            this.extraBlockTags = java.util.List.of(extraBlockTags);
            this.key = key;
            this.vanilla = vanilla;
            this.xp = xp;
            this.smeltResult = smeltResult;
            this.smeltXp = smeltXp;
            this.vanillaBlockTag = vanillaBlockTag;
            this.vanillaItemTag = vanillaItemTag;
            this.commonBlockTag = commonBlockTag;
            this.commonItemTag = commonItemTag;
            this.toolTag = toolTag;
        }
    }

    /** Id del bloque: hfil_&lt;piedra&gt;_&lt;mena&gt;_ore (también el nombre de su textura). */
    public static String id(Stone stone, Type type) {
        return "hfil_" + stone.key + "_" + type.key + "_ore";
    }

    public static DeferredBlock<Block> get(Stone stone, Type type) {
        return ModBlocks.HFIL_ORES.get(stone).get(type);
    }

    /** Piedra del HFIL que corresponde a un bloque de suelo (la arena de ceniza cuenta como
     *  ceniza), o null si no es ninguna (nieve, hielo, bloques ajenos). */
    public static Stone stoneOf(net.minecraft.world.level.block.state.BlockState ground) {
        for (Stone s : Stone.values()) if (ground.is(s.host.get())) return s;
        if (ground.is(ModBlocks.HFIL_CINDER_SAND.get())) return Stone.CINDER;
        return null;
    }
}
