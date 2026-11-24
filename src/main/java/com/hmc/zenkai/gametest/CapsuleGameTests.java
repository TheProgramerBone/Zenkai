package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.capsule.CapsuleCapture;
import com.hmc.zenkai.feature.capsule.CapsuleContent;
import com.hmc.zenkai.feature.capsule.CapsuleRelease;
import com.hmc.zenkai.feature.capsule.CapsuleStorage;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fase 6 de la 0.7.0: cápsulas Hoi-Poi. Captura con inventario y mob, pegado en su sitio, lo prohibido se queda,
 * obstrucción sin gastar la cápsula, anti-duplicación (soltar dos veces la misma) y veto en el deseo de duplicar.
 *
 * Geometría: radio 2 (cobre). Cápsula «virtual» en CAPSULE (no hace falta el bloque: su posición se salta), así que la
 * zona va de (6,1,6) a (10,5,10) dentro de la plantilla vacía de 16x6x16. Para pegar en el mismo sitio el ancla es un
 * bloque más abajo (CapsuleZone: el suelo guardado va al nivel del ancla).
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CapsuleGameTests {
    private CapsuleGameTests() {}

    private static final BlockPos CAPSULE = new BlockPos(8, 2, 8);
    private static final int R = 2;

    private static CapsuleCapture.Result capture(GameTestHelper h) {
        return CapsuleCapture.capture(h.getLevel(), h.absolutePos(CAPSULE), R, FakePlayerFactory.getMinecraft(h.getLevel()));
    }

    private static CapsuleRelease.Outcome releaseInPlace(GameTestHelper h, CapsuleContent c) {
        return CapsuleRelease.release(h.getLevel(), h.absolutePos(CAPSULE.below()), c,
                FakePlayerFactory.getMinecraft(h.getLevel()), null);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void capturesAndReleasesWithInventoryAndMob(GameTestHelper h) {
        h.setBlock(new BlockPos(7, 1, 7), Blocks.COBBLESTONE);
        h.setBlock(new BlockPos(9, 1, 9), Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(new BlockPos(9, 1, 9))).setItem(0, new ItemStack(Items.DIAMOND, 5));
        h.setBlock(new BlockPos(6, 1, 9), Blocks.STONE);
        Cow cow = h.spawn(EntityType.COW, new BlockPos(7, 2, 9));
        cow.setNoAi(true);

        CapsuleCapture.Result r = capture(h);
        h.assertTrue(r.outcome() == CapsuleCapture.Outcome.OK, "captura: " + r.outcome());
        CapsuleContent c = r.content();
        h.assertTrue(c.blocks() == 3, "3 bloques guardados, no " + c.blocks());
        h.assertTrue(c.mobs() == 1, "1 mob guardado, no " + c.mobs());
        h.assertBlockPresent(Blocks.AIR, new BlockPos(7, 1, 7));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(9, 1, 9));
        h.assertTrue(!cow.isAlive() || cow.isRemoved(), "la vaca sale del mundo");
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                AABB.encapsulatingFullBlocks(h.absolutePos(new BlockPos(5, 0, 5)), h.absolutePos(new BlockPos(11, 6, 11))))
                .isEmpty(), "el cofre no suelta nada al quitarse");
        h.assertTrue(CapsuleStorage.exists(h.getLevel().getServer(), c.id()), "archivo escrito");

        h.assertTrue(releaseInPlace(h, c) == CapsuleRelease.Outcome.OK, "pegado");
        CapsuleRelease.finishAll();
        h.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(7, 1, 7));
        h.assertBlockPresent(Blocks.STONE, new BlockPos(6, 1, 9));
        var chest = (ChestBlockEntity) h.getBlockEntity(new BlockPos(9, 1, 9));
        h.assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 5, "el cofre conserva su contenido");
        h.assertTrue(!h.getLevel().getEntitiesOfClass(Cow.class, new AABB(h.absolutePos(new BlockPos(5, 0, 5))).inflate(8)).isEmpty(),
                "la vaca vuelve");
        h.assertTrue(!CapsuleStorage.exists(h.getLevel().getServer(), c.id()), "archivo borrado al terminar");

        // ANTI-DUPLICACIÓN: una copia de la misma cápsula ya no tiene nada que soltar.
        h.assertTrue(CapsuleRelease.release(h.getLevel(), h.absolutePos(new BlockPos(8, 3, 8)), c,
                FakePlayerFactory.getMinecraft(h.getLevel()), null) == CapsuleRelease.Outcome.MISSING, "segunda vez: vacía");
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void prohibitedStayBehind(GameTestHelper h) {
        h.setBlock(new BlockPos(7, 1, 7), Blocks.BEDROCK);
        h.setBlock(new BlockPos(9, 1, 9), Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(new BlockPos(9, 1, 9))).setItem(0, new ItemStack(ModItems.CAPSULE_IRON.get()));
        h.setBlock(new BlockPos(8, 1, 7), Blocks.OAK_PLANKS);

        CapsuleCapture.Result r = capture(h);
        h.assertTrue(r.outcome() == CapsuleCapture.Outcome.OK, "captura: " + r.outcome());
        h.assertTrue(r.content().blocks() == 1, "solo la madera, no " + r.content().blocks());
        h.assertTrue(r.skipped() == 2, "bedrock y el cofre con otra cápsula se quedan, no " + r.skipped());
        h.assertBlockPresent(Blocks.BEDROCK, new BlockPos(7, 1, 7));
        h.assertBlockPresent(Blocks.CHEST, new BlockPos(9, 1, 9));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(8, 1, 7));
        CapsuleStorage.delete(h.getLevel().getServer(), r.content().id());
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void obstructedReleaseKeepsCapsule(GameTestHelper h) {
        h.setBlock(new BlockPos(7, 1, 7), Blocks.COBBLESTONE);
        CapsuleCapture.Result r = capture(h);
        h.assertTrue(r.outcome() == CapsuleCapture.Outcome.OK, "captura: " + r.outcome());
        h.setBlock(new BlockPos(7, 1, 7), Blocks.STONE);   // algo estorba justo donde iría

        h.assertTrue(releaseInPlace(h, r.content()) == CapsuleRelease.Outcome.OBSTRUCTED, "obstruido");
        h.assertTrue(CapsuleStorage.exists(h.getLevel().getServer(), r.content().id()), "la cápsula no se gasta");
        h.assertBlockPresent(Blocks.STONE, new BlockPos(7, 1, 7));
        CapsuleStorage.delete(h.getLevel().getServer(), r.content().id());
        h.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void emptyZoneAndWishBan(GameTestHelper h) {
        h.assertTrue(capture(h).outcome() == CapsuleCapture.Outcome.NOTHING, "zona vacía: nada");
        ItemStack empty = new ItemStack(ModItems.CAPSULE_GOLD.get());
        ItemStack full = empty.copy();
        full.set(ModDataComponents.CAPSULE_CONTENT.get(), new CapsuleContent(java.util.UUID.randomUUID(), 1, 0, 5));
        h.assertTrue(!ServerConfig.isBanned(empty), "vacía: el deseo sí la duplica");
        h.assertTrue(ServerConfig.isBanned(full), "llena: vetada en el deseo");
        h.succeed();
    }
}
