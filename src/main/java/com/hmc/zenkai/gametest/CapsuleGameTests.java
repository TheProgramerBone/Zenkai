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

    /**
     * Control remoto (2026-11-25): vincular, recoger a distancia (la llena se queda colocada UN BLOQUE MÁS ABAJO, donde
     * estaba el suelo guardado) y abrir a distancia (la vacía vuelve a su sitio, de pie sobre el suelo pegado), siempre con
     * el mismo id de vínculo. Suelo firme de piedra en y=0 bajo la zona para que la llena tenga dónde apoyarse.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty", timeoutTicks = 60)
    public static void remotePacksAndOpensKeepingTheCapsulePlaced(GameTestHelper h) {
        var level = h.getLevel();
        for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        h.setBlock(new BlockPos(8, 1, 8), Blocks.STONE);         // suelo bajo la cápsula
        h.setBlock(new BlockPos(7, 1, 7), Blocks.COBBLESTONE);
        h.setBlock(new BlockPos(9, 2, 9), Blocks.OAK_PLANKS);
        BlockPos capAbs = h.absolutePos(CAPSULE);
        h.assertTrue(com.hmc.zenkai.content.blockentity.CapsuleBlockEntity.place(level, capAbs,
                new ItemStack(ModItems.CAPSULE_COPPER.get())), "cápsula colocada");

        var fake = FakePlayerFactory.getMinecraft(level);
        fake.moveTo(capAbs.getX() + 3.5, capAbs.getY(), capAbs.getZ() + 3.5);
        ItemStack remote = new ItemStack(ModItems.CAPSULE_REMOTE.get());
        var be = (com.hmc.zenkai.content.blockentity.CapsuleBlockEntity) level.getBlockEntity(capAbs);
        com.hmc.zenkai.feature.capsule.CapsuleRemote.link(fake, remote, be);
        var links = com.hmc.zenkai.feature.capsule.CapsuleRemote.links(remote);
        h.assertTrue(links.links().size() == 1 && links.owner().isPresent(), "vinculada y con dueño");
        java.util.UUID id = links.links().get(0).id();

        com.hmc.zenkai.feature.capsule.CapsuleRemote.toggle(fake, remote);
        h.assertTrue(be.mode() == com.hmc.zenkai.content.blockentity.CapsuleBlockEntity.Mode.ARMED, "armada a distancia");

        // La captura corre en el tick del bloque múltiplo de 10.
        h.runAfterDelay(25, () -> {
            var packed = com.hmc.zenkai.feature.capsule.CapsuleRemote.find(level, capAbs, id);
            h.assertTrue(packed != null && packed.getBlockPos().equals(capAbs.below()) && packed.full(),
                    "llena y colocada un bloque más abajo: " + (packed == null ? "no está" : packed.getBlockPos()));
            h.assertBlockPresent(Blocks.AIR, new BlockPos(7, 1, 7));
            h.assertBlockPresent(Blocks.AIR, new BlockPos(9, 2, 9));

            com.hmc.zenkai.feature.capsule.CapsuleRemote.toggle(fake, remote);
            CapsuleRelease.finishAll();
            h.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(7, 1, 7));
            h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(9, 2, 9));
            h.assertBlockPresent(Blocks.STONE, new BlockPos(8, 1, 8));
            var opened = com.hmc.zenkai.feature.capsule.CapsuleRemote.find(level, capAbs, id);
            h.assertTrue(opened != null && opened.getBlockPos().equals(capAbs) && !opened.full(),
                    "vacía y de vuelta en su sitio: " + (opened == null ? "no está" : opened.getBlockPos()));
            h.assertTrue(com.hmc.zenkai.feature.capsule.CapsuleRemote.links(remote).links().get(0).pos().pos().equals(capAbs.below()),
                    "el control recordó la posición al recoger");
            h.succeed();
        });
    }

    /**
     * Vínculo permanente (usuario 2026-11-26): recogida como ítem conserva su id; colocada en OTRO sitio, el control la
     * encuentra por CapsuleLinkRegistry sin revincular; desvincularla le quita el id (vuelve a apilarse con las nuevas).
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void linkSurvivesPickupAndUnlinkClearsId(GameTestHelper h) {
        var level = h.getLevel();
        h.setBlock(new BlockPos(4, 0, 4), Blocks.STONE);
        h.setBlock(new BlockPos(12, 0, 12), Blocks.STONE);
        BlockPos a = h.absolutePos(new BlockPos(4, 1, 4)), b = h.absolutePos(new BlockPos(12, 1, 12));
        var Be = com.hmc.zenkai.content.blockentity.CapsuleBlockEntity.class;
        h.assertTrue(com.hmc.zenkai.content.blockentity.CapsuleBlockEntity.place(level, a,
                new ItemStack(ModItems.CAPSULE_COPPER.get())), "colocada en A");
        var fake = FakePlayerFactory.getMinecraft(level);
        fake.moveTo(a.getX() + 2.5, a.getY(), a.getZ() + 2.5);
        ItemStack remote = new ItemStack(ModItems.CAPSULE_REMOTE.get());
        com.hmc.zenkai.feature.capsule.CapsuleRemote.link(fake, remote, Be.cast(level.getBlockEntity(a)));
        java.util.UUID id = com.hmc.zenkai.feature.capsule.CapsuleRemote.links(remote).links().get(0).id();

        // Recogida (como al romperla) y puesta en B.
        ItemStack item = Be.cast(level.getBlockEntity(a)).takeCapsule();
        level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(id.equals(item.get(ModDataComponents.CAPSULE_LINK.get())), "como ítem conserva su id");
        h.assertTrue(com.hmc.zenkai.feature.capsule.CapsuleLinkRegistry.get(level.getServer()).where(id) == null,
                "en un inventario no figura en el registro");
        h.assertTrue(com.hmc.zenkai.content.blockentity.CapsuleBlockEntity.place(level, b, item), "colocada en B");
        var where = com.hmc.zenkai.feature.capsule.CapsuleLinkRegistry.get(level.getServer()).where(id);
        h.assertTrue(where != null && where.pos().equals(b), "el registro la sitúa en B");

        // El control la usa en B sin revincular (vacía y con algo en la zona: queda armada).
        h.setBlock(new BlockPos(13, 1, 12), Blocks.COBBLESTONE);
        com.hmc.zenkai.feature.capsule.CapsuleRemote.toggle(fake, remote);
        var atB = Be.cast(level.getBlockEntity(b));
        h.assertTrue(atB.mode() == com.hmc.zenkai.content.blockentity.CapsuleBlockEntity.Mode.ARMED, "armada en B a distancia");
        com.hmc.zenkai.feature.capsule.CapsuleRemote.toggle(fake, remote);   // cancelar

        // Desvincular con el control: fuera del control y sin id.
        com.hmc.zenkai.feature.capsule.CapsuleRemote.link(fake, remote, atB);
        h.assertTrue(com.hmc.zenkai.feature.capsule.CapsuleRemote.links(remote).links().isEmpty(), "fuera del control");
        ItemStack loose = atB.takeCapsule();
        h.assertTrue(!loose.has(ModDataComponents.CAPSULE_LINK.get()), "sin id tras desvincular");
        h.assertTrue(ItemStack.isSameItemSameComponents(loose, new ItemStack(ModItems.CAPSULE_COPPER.get())),
                "se apila con una nueva");
        level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    /**
     * Rotación al pegar (2026-11-26): 90° horario lleva la celda relativa (x, z) a (4 − z, x) en una zona de lado 5 y gira
     * los estados (escalera al norte → al este), como StructureTemplate.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void releaseRotatesBlocksAndStates(GameTestHelper h) {
        h.setBlock(new BlockPos(6, 1, 6), Blocks.COBBLESTONE);
        h.setBlock(new BlockPos(6, 1, 7), Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH));
        CapsuleCapture.Result r = capture(h);
        h.assertTrue(r.outcome() == CapsuleCapture.Outcome.OK, "captura: " + r.outcome());
        h.assertTrue(CapsuleRelease.release(h.getLevel(), h.absolutePos(CAPSULE.below()), r.content(),
                FakePlayerFactory.getMinecraft(h.getLevel()), null,
                net.minecraft.world.level.block.Rotation.CLOCKWISE_90, null) == CapsuleRelease.Outcome.OK, "pegado girado");
        CapsuleRelease.finishAll();
        h.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(10, 1, 6));   // (0,0) -> (4,0)
        h.assertBlockPresent(Blocks.AIR, new BlockPos(6, 1, 6));
        var stair = h.getBlockState(new BlockPos(9, 1, 6));                 // (0,1) -> (3,0)
        h.assertTrue(stair.is(Blocks.OAK_STAIRS)
                && stair.getValue(net.minecraft.world.level.block.StairBlock.FACING) == net.minecraft.core.Direction.EAST,
                "escalera girada al este: " + stair);
        h.succeed();
    }
}
