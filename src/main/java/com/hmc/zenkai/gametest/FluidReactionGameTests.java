package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Interacciones de los fluidos del mod (0.7.0, .claude/docs/interacciones-de-fluidos.md). Las de fluido
 * contra fluido se resuelven en el onPlace del segundo bloque, así que se comprueban en el acto; las de
 * bloques sólidos usan la regla inmediata (hormigón en polvo) para no depender del azar.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidReactionGameTests {
    private FluidReactionGameTests() {}

    private static final BlockPos A = new BlockPos(3, 2, 3);
    private static final BlockPos B = new BlockPos(4, 2, 3);

    private static void expect(GameTestHelper helper, BlockPos pos, Block block, String what) {
        helper.assertTrue(helper.getBlockState(pos).is(block), what + "; hay " + helper.getBlockState(pos).getBlock());
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void boilingEvaporatesVanillaWater(GameTestHelper helper) {
        helper.setBlock(A, Blocks.WATER.defaultBlockState());
        helper.setBlock(B, ModBlocks.HFIL_BOILING_WATER_BLOCK.get().defaultBlockState());
        expect(helper, A, Blocks.AIR, "El agua vanilla junto al agua hirviendo se evapora");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void boilingSolidifiesLavaSource(GameTestHelper helper) {
        helper.setBlock(A, Blocks.LAVA.defaultBlockState());
        helper.setBlock(B, ModBlocks.HFIL_BOILING_WATER_BLOCK.get().defaultBlockState());
        expect(helper, A, Blocks.SMOOTH_STONE, "Una fuente de lava junto al agua hirviendo pasa a piedra lisa");
        expect(helper, B, ModBlocks.HFIL_BOILING_WATER_BLOCK.get(), "El agua hirviendo se queda");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void healingTurnsLavaSourceIntoDirt(GameTestHelper helper) {
        helper.setBlock(A, Blocks.LAVA.defaultBlockState());
        helper.setBlock(B, ModBlocks.HEALING_WATER_BLOCK.get().defaultBlockState());
        expect(helper, A, Blocks.DIRT, "Una fuente de lava junto al agua curativa pasa a tierra");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void kiEvaporatesInBoilingWater(GameTestHelper helper) {
        helper.setBlock(A, ModBlocks.HFIL_BOILING_WATER_BLOCK.get().defaultBlockState());
        helper.setBlock(B, ModBlocks.KI_BASE_BLOCK.get().defaultBlockState());
        expect(helper, B, Blocks.AIR, "El ki junto al agua hirviendo se evapora");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void kiBaseOnLavaBecomesGravel(GameTestHelper helper) {
        helper.setBlock(A, Blocks.LAVA.defaultBlockState());
        helper.setBlock(B, ModBlocks.KI_BASE_BLOCK.get().defaultBlockState());
        expect(helper, B, Blocks.GRAVEL, "El ki Base sobre lava deja grava");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void kiOnWaterBecomesGlassAndMixedBecomesTuff(GameTestHelper helper) {
        helper.setBlock(A, Blocks.WATER.defaultBlockState());
        helper.setBlock(B, ModBlocks.KI_SUPER_BLOCK.get().defaultBlockState());
        expect(helper, B, Blocks.GLASS, "El ki junto al agua deja cristal");
        BlockPos c = new BlockPos(8, 2, 8), d = new BlockPos(9, 2, 8);
        helper.setBlock(c, ModBlocks.KI_BASE_BLOCK.get().defaultBlockState());
        helper.setBlock(d, ModBlocks.KI_LEGENDARY_BLOCK.get().defaultBlockState());
        expect(helper, d, Blocks.TUFF, "Dos ki distintos dejan toba");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void healingTurnsConcretePowderIntoConcrete(GameTestHelper helper) {
        helper.setBlock(B.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(B, Blocks.WHITE_CONCRETE_POWDER.defaultBlockState());
        helper.setBlock(A, ModBlocks.HEALING_WATER_BLOCK.get().defaultBlockState());
        helper.runAfterDelay(60, () -> {
            expect(helper, B, Blocks.WHITE_CONCRETE, "El hormigón en polvo junto al agua curativa fragua");
            helper.succeed();
        });
    }
}
