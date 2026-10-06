package com.hmc.zenkai.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Campo de mojones (pilas de piedras) del HFIL — propuesta C de
 * .claude/pendiente/worldgen-pendiente.md. Referencia: Sai no Kawara, la orilla del río Sanzu
 * del budismo japonés, donde las almas de los niños apilan torres de piedras que los oni
 * derriban. En Osorezan (Japón, la "puerta del inframundo") esa orilla existe de verdad,
 * cubierta de montoncitos de piedra. El infierno de Dragon Ball sale de ese mismo Jigoku (el
 * Blood Pond es su Chi no Ike), así que encaja sin inventar nada.
 *
 * Cada mojón es una columna de 1-3 muros de piedra (el poste fino del muro se lee como "piedra
 * apilada", un bloque entero se leería como pilar) coronada a veces por un botón de piedra (la
 * piedrecita de arriba). Mismo estilo ligero que HfilBonePileFeature: a mano, sondeo de suelo
 * LOCAL por columna (LocalGroundProbe), nunca encima de un líquido.
 */
public class HfilCairnFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_CAIRNS = 3;
    private static final int MAX_CAIRNS = 7;
    /** Radio del campo de mojones alrededor del origen. */
    private static final int FIELD_SPREAD = 4;
    private static final int GROUND_SEARCH_RADIUS = 3;
    private static final int MAX_HEIGHT = 3;
    /** Probabilidad de la piedrecita (botón) en lo alto. */
    private static final float TOP_STONE_CHANCE = 0.5f;
    /** Probabilidad de que un mojón sea de piedra clara en vez de pizarra oscura: la oscura
     *  destaca sobre el suelo blanco de needle_wastes, la clara sobre la roca roja. */
    private static final float LIGHT_STONE_CHANCE = 0.3f;

    public HfilCairnFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int count = MIN_CAIRNS + random.nextInt(MAX_CAIRNS - MIN_CAIRNS + 1);
        boolean placedAny = false;
        for (int i = 0; i < count; i++) {
            int x = origin.getX() + random.nextInt(FIELD_SPREAD * 2 + 1) - FIELD_SPREAD;
            int z = origin.getZ() + random.nextInt(FIELD_SPREAD * 2 + 1) - FIELD_SPREAD;
            int groundY = LocalGroundProbe.findGroundY(level, x, origin.getY(), z, GROUND_SEARCH_RADIUS);
            if (groundY == Integer.MIN_VALUE) continue;

            // findGroundY atraviesa líquidos: groundY puede ser el FONDO de un charco. Solo se
            // apila sobre suelo seco con aire encima.
            BlockPos base = new BlockPos(x, groundY, z);
            if (!level.getBlockState(base).isAir()) continue;

            placedAny |= placeCairn(level, random, base);
        }
        return placedAny;
    }

    private static boolean placeCairn(WorldGenLevel level, RandomSource random, BlockPos base) {
        BlockState wall = (random.nextFloat() < LIGHT_STONE_CHANCE
                ? Blocks.COBBLESTONE_WALL : Blocks.COBBLED_DEEPSLATE_WALL).defaultBlockState();
        int height = 1 + random.nextInt(MAX_HEIGHT);

        BlockPos.MutableBlockPos pos = base.mutable();
        for (int i = 0; i < height; i++) {
            if (!level.getBlockState(pos).isAir()) break;
            level.setBlock(pos, wall, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            pos.move(Direction.UP);
        }
        if (pos.getY() == base.getY()) return false;

        if (random.nextFloat() < TOP_STONE_CHANCE && level.getBlockState(pos).isAir()) {
            BlockState stone = Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(ButtonBlock.FACE, AttachFace.FLOOR)
                    .setValue(ButtonBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random));
            level.setBlock(pos, stone, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
        return true;
    }
}
