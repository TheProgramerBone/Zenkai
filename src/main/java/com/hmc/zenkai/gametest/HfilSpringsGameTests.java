package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.HfilGeyserBlock;
import com.hmc.zenkai.registry.ModBiomes;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModConfiguredFeatures;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Predicate;

/**
 * Mojones y pozas de Beppu del HFIL (propuestas C y A, 2026-10-02).
 *
 * POR QUÉ HACE FALTA el test de orden: runGameTestServer NO carga la dimensión del Otherworld
 * (ver .claude/docs/worldgen.md), así que un "Feature order cycle found" entre los biomas del
 * HFIL no saltaría en ningún test ni en el build — solo al entrar al HFIL en juego, como crash.
 * Aquí se ejecuta el MISMO FeatureSorter que usa el generador, sobre los 4 biomas reales del
 * biome_source de otherworld.json.
 *
 * El resto coloca cada feature de verdad en el nivel del gametest, sobre una losa de piedra
 * preparada por el propio test (independiente de cómo sea el suelo del mundo de pruebas).
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HfilSpringsGameTests {
    private HfilSpringsGameTests() {}

    private static final int PAD = 7;

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void otherworldFeatureOrderHasNoCycle(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        List<ResourceKey<Biome>> keys = List.of(ModBiomes.OTHERWORLD, ModBiomes.HFIL_NEEDLE_WASTES,
                ModBiomes.HFIL_BLOOD_SHORE, ModBiomes.HFIL_CINDER_DUNES, ModBiomes.HFIL_FROZEN_LAKE,
                ModBiomes.HFIL_JANEMBA_RIFT);
        List<Holder<Biome>> holders = keys.stream().<Holder<Biome>>map(biomes::getHolderOrThrow).toList();
        try {
            FeatureSorter.buildFeaturesPerStep(holders,
                    h -> h.value().getGenerationSettings().features(), true);
        } catch (IllegalStateException e) {
            helper.fail("Ciclo de orden de features en el Otherworld: " + e.getMessage());
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void geyserPlacesAndLaunches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareSlab(helper, Blocks.STONE.defaultBlockState());
        place(helper, ModConfiguredFeatures.HFIL_GEYSER_KEY, ground.above(), 1L);

        BlockPos vent = ground;
        BlockState state = level.getBlockState(vent);
        helper.assertTrue(state.is(ModBlocks.HFIL_GEYSER.get()), "la boca del géiser queda a ras de suelo");
        helper.assertTrue(level.getBlockTicks().hasScheduledTick(vent, ModBlocks.HFIL_GEYSER.get()),
                "la feature programa el ciclo de erupción (onPlace no corre en worldgen)");
        // Regla del usuario: hecho del suelo del bioma, nunca de un bloque ajeno.
        helper.assertTrue(state.getValue(HfilGeyserBlock.GROUND) == HfilGeyserBlock.Ground.SCORCHED,
                "sobre un suelo cualquiera, la variante por defecto (piedra calcinada)");
        helper.assertTrue(count(level, ground, s -> s.is(ModBlocks.HFIL_PALE_STONE.get())) == 0,
                "ni un bloque de piedra pálida fuera del suelo blanco");

        Pig pig = EntityType.PIG.create(level);
        helper.assertTrue(pig != null, "cerdo de prueba");
        pig.moveTo(vent.getX() + 0.5, vent.getY() + 1.0, vent.getZ() + 0.5);
        level.addFreshEntity(pig);

        RandomSource random = RandomSource.create(2L);
        state.tick(level, vent, random);                       // reposo → arranque
        state = level.getBlockState(vent);
        helper.assertTrue(state.getValue(HfilGeyserBlock.STAGE) == 1, "arranca la erupción");
        state.tick(level, vent, random);                       // primer paso: empuja
        helper.assertTrue(pig.getDeltaMovement().y >= 1.0, "la erupción lanza hacia arriba lo que está encima");
        pig.discard();

        // Sobre el suelo blanco de needle_wastes: variante pálida y cono de piedra pálida.
        BlockPos pale = prepareSlab(helper, ModBlocks.HFIL_PALE_STONE.get().defaultBlockState());
        place(helper, ModConfiguredFeatures.HFIL_GEYSER_KEY, pale.above(), 1L);
        helper.assertTrue(level.getBlockState(pale).getValue(HfilGeyserBlock.GROUND) == HfilGeyserBlock.Ground.PALE,
                "sobre piedra pálida, la boca es la variante pálida");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void boilingPoolPlacesAndCanBeBucketed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareSlab(helper, ModBlocks.HFIL_SCORCHED_STONE.get().defaultBlockState());
        place(helper, ModConfiguredFeatures.HFIL_BOILING_POOL_KEY, ground.above(), 3L);

        Block water = ModBlocks.HFIL_BOILING_WATER_BLOCK.get();
        helper.assertTrue(count(level, ground, s -> s.is(water)) > 0, "la poza se llena de agua hirviendo");
        helper.assertTrue(count(level, ground, s -> s.is(ModBlocks.HFIL_PALE_STONE.get())) == 0,
                "la orilla es el suelo del bioma: sin piedra pálida sobre piedra calcinada");

        BlockPos center = ground;
        helper.assertTrue(level.getBlockState(center).is(water), "el centro de la poza es agua hirviendo");
        ItemStack picked = ModBlocks.HFIL_BOILING_WATER_BLOCK.get()
                .pickupBlock(null, level, center, level.getBlockState(center));
        helper.assertTrue(picked.is(ModItems.HFIL_BOILING_WATER_BUCKET.get()),
                "un cubo vacío la recoge y devuelve el cubo de agua hirviendo");
        helper.succeed();
    }

    /**
     * Bug con captura (2026-10-02): sobre terreno con un escalón, la poza dejaba bloques de agua
     * sueltos un bloque por encima del resto (cada columna se llenaba hasta SU suelo). Ahora la
     * lámina es única: ni un bloque de agua por encima del suelo del centro.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void boilingPoolIsLevelOnSteppedGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareSlab(helper, ModBlocks.HFIL_PALE_STONE.get().defaultBlockState());
        // Media losa (x > 0) un bloque más alta: el escalón de la captura.
        for (int dx = 1; dx <= PAD; dx++) {
            for (int dz = -PAD; dz <= PAD; dz++) {
                level.setBlock(ground.offset(dx, 1, dz), ModBlocks.HFIL_PALE_STONE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        place(helper, ModConfiguredFeatures.HFIL_BOILING_POOL_KEY, ground.above(), 11L);

        Block water = ModBlocks.HFIL_BOILING_WATER_BLOCK.get();
        int maxY = Integer.MIN_VALUE;
        for (int dx = -PAD; dx <= PAD; dx++) {
            for (int dz = -PAD; dz <= PAD; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    if (level.getBlockState(ground.offset(dx, dy, dz)).is(water)) maxY = Math.max(maxY, ground.getY() + dy);
                }
            }
        }
        helper.assertTrue(maxY != Integer.MIN_VALUE, "la poza se genera con un escalón de 1 bloque");
        helper.assertTrue(maxY == ground.getY(), "lámina única: ningún bloque de agua por encima del suelo del centro (maxY="
                + maxY + ", suelo=" + ground.getY() + ")");
        helper.succeed();
    }

    /**
     * Terrazas de Pamukkale (propuesta B): sobre llano forman una colina escalonada con agua en
     * varios niveles, hecha del bloque de suelo, y NINGÚN bloque de agua queda junto a aire (ni
     * a los lados ni debajo) — si no, en juego el escalón se vaciaría sobre el siguiente.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void terracesStepAndHoldWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int pad = 20;
        BlockPos ground = prepareSlab(helper, ModBlocks.HFIL_PALE_STONE.get().defaultBlockState(), pad,
                new BlockPos(1, 40, 1));
        place(helper, ModConfiguredFeatures.HFIL_TERRACES_KEY, ground.above(), 13L);

        java.util.Set<Integer> levels = new java.util.HashSet<>();
        int waterCount = 0;
        for (int dx = -pad; dx <= pad; dx++) {
            for (int dz = -pad; dz <= pad; dz++) {
                for (int dy = -2; dy <= 6; dy++) {
                    BlockPos p = ground.offset(dx, dy, dz);
                    if (!level.getBlockState(p).is(Blocks.WATER)) continue;
                    waterCount++;
                    levels.add(p.getY());
                    for (BlockPos n : new BlockPos[]{p.below(), p.north(), p.south(), p.east(), p.west()}) {
                        if (level.getBlockState(n).isAir()) {
                            helper.fail("agua junto a aire en " + n.toShortString() + ": el escalón se derramaría");
                            return;
                        }
                    }
                }
            }
        }
        helper.assertTrue(waterCount > 0, "las terrazas llevan pozas de agua");
        helper.assertTrue(levels.size() >= 3, "al menos 3 escalones de agua a distinta altura (hay " + levels.size() + ")");
        helper.succeed();
    }

    /**
     * Menas del HFIL (petición del usuario, 2026-10-02): una veta que atraviesa piedra pálida es
     * de hierro PÁLIDO, la que atraviesa piedra calcinada es de hierro CALCINADO, y en ninguna
     * sale la mena vanilla con fondo de piedra gris.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void hfilOresMatchTheirStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (com.hmc.zenkai.registry.HfilOres.Stone stone : com.hmc.zenkai.registry.HfilOres.Stone.values()) {
            BlockPos ground = prepareSlab(helper, stone.host.get().defaultBlockState());
            // Origen dentro de la losa (4 de grueso): una veta de tamaño 9 cabe casi entera.
            place(helper, ModConfiguredFeatures.HFIL_ORE_IRON, ground.below(2), 17L);
            Block own = com.hmc.zenkai.registry.HfilOres.get(stone, com.hmc.zenkai.registry.HfilOres.Type.IRON).get();
            helper.assertTrue(count(level, ground, s -> s.is(own)) > 0,
                    "veta en piedra " + stone.key + " → mena de hierro " + stone.key);
            helper.assertTrue(count(level, ground, s -> s.is(Blocks.IRON_ORE) || s.is(Blocks.DEEPSLATE_IRON_ORE)) == 0,
                    "ni una mena de hierro vanilla en piedra " + stone.key);
        }
        helper.succeed();
    }

    /** Katchin (2026-10-02): la veta del HFIL y el afloramiento de superficie usan la variante
     *  de su piedra, nunca el katchin_ore de fondo gris. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void katchinMatchesItsStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pale = com.hmc.zenkai.registry.HfilOres.get(com.hmc.zenkai.registry.HfilOres.Stone.PALE,
                com.hmc.zenkai.registry.HfilOres.Type.KATCHIN).get();
        BlockPos ground = prepareSlab(helper, ModBlocks.HFIL_PALE_STONE.get().defaultBlockState());
        place(helper, ModConfiguredFeatures.KATCHIN_ORE_OW_KEY, ground.below(2), 23L);
        helper.assertTrue(count(level, ground, s -> s.is(pale)) > 0, "veta de katchin en piedra pálida → katchin pálido");

        BlockPos ground2 = prepareSlab(helper, ModBlocks.HFIL_PALE_STONE.get().defaultBlockState());
        place(helper, ModConfiguredFeatures.HFIL_ORE_BOULDER_KEY, ground2.above(), 29L);
        helper.assertTrue(count(level, ground2.above(), s -> s.is(pale)) > 0, "afloramiento sobre piedra pálida → katchin pálido");
        helper.assertTrue(count(level, ground2, s -> s.is(ModBlocks.KATCHIN_ORE.get())) == 0, "ni un katchin_ore de fondo gris");
        helper.succeed();
    }

    /** E (Osorezan): costra de azufre sustituyendo el suelo y al menos una fumarola. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void sulfurFieldPlaces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareSlab(helper, ModBlocks.HFIL_CINDER_SANDSTONE.get().defaultBlockState());
        place(helper, ModConfiguredFeatures.HFIL_SULFUR_FIELD_KEY, ground.above(), 31L);
        helper.assertTrue(count(level, ground, s -> s.is(ModBlocks.HFIL_SULFUR_CRUST.get())) > 0, "costra de azufre en el suelo");
        helper.assertTrue(count(level, ground, s -> s.is(ModBlocks.HFIL_FUMAROLE.get())) > 0, "al menos una fumarola");
        helper.succeed();
    }

    /** F (Darvaza): cuenco excavado con magma y fuego en el fondo, por debajo del suelo. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void gasCraterBurns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareSlab(helper, ModBlocks.HFIL_CINDER_SANDSTONE.get().defaultBlockState(), 10,
                new BlockPos(1, 60, 1));
        // Losa más gruesa: el cuenco baja hasta 6 bloques.
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) for (int dy = -8; dy <= -4; dy++)
            level.setBlock(ground.offset(dx, dy, dz), ModBlocks.HFIL_CINDER_SANDSTONE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        place(helper, ModConfiguredFeatures.HFIL_GAS_CRATER_KEY, ground.above(), 37L);
        int magma = 0, fire = 0;
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) for (int dy = -8; dy <= 0; dy++) {
            BlockState s = level.getBlockState(ground.offset(dx, dy, dz));
            if (s.is(Blocks.MAGMA_BLOCK)) magma++;
            if (s.is(Blocks.FIRE)) fire++;
        }
        helper.assertTrue(magma > 0 && fire > 0, "fondo de magma con fuego (magma=" + magma + ", fuego=" + fire + ")");
        helper.assertTrue(level.getBlockState(ground).isAir(), "el centro del cráter está excavado");
        helper.succeed();
    }

    /** G (kama-yude): olla con sangre hirviendo (columnas de burbujas) contenida, y hogueras. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void oniCauldronHoldsBoilingBlood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareSlab(helper, ModBlocks.HFIL_SCORCHED_STONE.get().defaultBlockState());
        place(helper, ModConfiguredFeatures.HFIL_ONI_CAULDRON_KEY, ground.above(), 41L);
        int bubbles = 0;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = 0; dy <= 6; dy++) {
            BlockPos p = ground.offset(dx, dy, dz);
            if (level.getFluidState(p).isEmpty()) continue;
            if (level.getBlockState(p).is(Blocks.BUBBLE_COLUMN)) bubbles++;
            for (BlockPos n : new BlockPos[]{p.north(), p.south(), p.east(), p.west(), p.below()}) {
                if (level.getBlockState(n).isAir()) {
                    helper.fail("la sangre de la olla toca aire en " + n.toShortString());
                    return;
                }
            }
        }
        helper.assertTrue(bubbles > 0, "la sangre hierve (columnas de burbujas)");
        helper.assertTrue(count(level, ground.above(), s -> s.is(Blocks.CAMPFIRE)) > 0, "hogueras bajo la olla");
        helper.succeed();
    }

    /**
     * Ogros (propuesta I): el puesto de guardia sobrevive a guardar/cargar (vanilla NO persiste
     * Mob.restrictTo; sin el NBT propio, el ogro olvidaría su caldero al recargar el chunk), y el
     * caldero genera su pareja de guardias, uno rojo y uno azul.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void ogresGuardTheirCauldron(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos post = helper.absolutePos(new BlockPos(1, 1, 1));
        var red = com.hmc.zenkai.registry.ModEntities.RED_OGRE.get().create(level);
        helper.assertTrue(red != null, "se crea un ogro rojo");
        red.guard(post);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        red.saveWithoutId(tag);
        var copy = com.hmc.zenkai.registry.ModEntities.RED_OGRE.get().create(level);
        copy.load(tag);
        helper.assertTrue(copy.hasRestriction() && copy.getRestrictCenter().equals(post),
                "el puesto de guardia se conserva al recargar");
        helper.assertTrue(red.isAlliedTo(com.hmc.zenkai.registry.ModEntities.BLUE_OGRE.get().create(level)),
                "rojos y azules son aliados");

        BlockPos ground = prepareSlab(helper, ModBlocks.HFIL_SCORCHED_STONE.get().defaultBlockState());
        place(helper, ModConfiguredFeatures.HFIL_ONI_CAULDRON_KEY, ground.above(), 43L);
        var area = new net.minecraft.world.phys.AABB(ground).inflate(PAD + 2);
        var ogres = level.getEntitiesOfClass(com.hmc.zenkai.content.entity.otherworld.OgreEntity.class, area);
        boolean hasRed = ogres.stream().anyMatch(o -> o.getType() == com.hmc.zenkai.registry.ModEntities.RED_OGRE.get());
        boolean hasBlue = ogres.stream().anyMatch(o -> o.getType() == com.hmc.zenkai.registry.ModEntities.BLUE_OGRE.get());
        boolean allGuard = ogres.stream().allMatch(o -> o.hasRestriction() && o.isPersistenceRequired());
        ogres.forEach(net.minecraft.world.entity.Entity::discard);
        helper.assertTrue(hasRed && hasBlue, "el caldero genera un ogro rojo y uno azul (hay " + ogres.size() + ")");
        helper.assertTrue(allGuard, "los guardias están atados al caldero y no desaparecen");
        helper.succeed();
    }

    /**
     * H (Dragon Ball Super), como ESTRUCTURA: la pieza dibuja el capullo con la cabeza morada, dos
     * ángeles con halo, un tocadiscos y el disco en un marco. OJO: este test corre en un
     * ServerLevel ya cargado, donde los block entities SÍ tienen nivel — no habría cazado el crash
     * real (JukeboxBlockEntity con level null en un ProtoChunk). Esa trampa se evita en el código
     * (nada de API de block entity en worldgen), no se puede probar aquí.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void friezaCocoonPlaces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareSlab(helper, ModBlocks.HFIL_PALE_STONE.get().defaultBlockState(), 8,
                new BlockPos(1, 80, 1));
        var piece = new com.hmc.zenkai.worldgen.structure.FriezaCocoonPiece(ground.above(), true);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(47L), piece.getBoundingBox(), new net.minecraft.world.level.ChunkPos(ground), ground);
        int wool = 0, purple = 0, logs = 0, leaves = 0, rods = 0, grass = 0;
        for (int dx = -13; dx <= 13; dx++) for (int dz = -13; dz <= 13; dz++) for (int dy = -3; dy <= 21; dy++) {
            BlockState s = level.getBlockState(ground.offset(dx, dy, dz));
            if (s.is(Blocks.WHITE_WOOL)) wool++;
            if (s.is(Blocks.PURPLE_CONCRETE)) purple++;
            if (s.is(Blocks.CHERRY_LOG) || s.is(Blocks.CHERRY_WOOD)) logs++;
            if (s.is(Blocks.CHERRY_LEAVES)) leaves++;
            if (s.is(Blocks.END_ROD)) rods++;
            if (s.is(Blocks.GRASS_BLOCK)) grass++;
        }
        var area = new net.minecraft.world.phys.AABB(ground).inflate(14);
        var angels = level.getEntitiesOfClass(com.hmc.zenkai.content.entity.otherworld.AngelEntity.class, area);
        int angelCount = angels.size();
        angels.forEach(net.minecraft.world.entity.Entity::discard);
        helper.assertTrue(wool > 40 && purple == 3, "capullo de seda con la cara de Freezer (morado=" + purple + ")");
        helper.assertTrue(logs > 40 && leaves > 150, "árbol de cerezo con copa (troncos=" + logs + ", hojas=" + leaves + ")");
        helper.assertTrue(rods == 2, "hilo de seda entre la rama y el capullo (" + rods + ")");
        helper.assertTrue(grass > 30, "prado bajo la copa (" + grass + ")");
        helper.assertTrue(angelCount == 2, "dos ángeles dando vueltas (hay " + angelCount + ")");
        helper.succeed();
    }

    /** Lago helado (propuesta D): se genera, es de sangre congelada y está a una sola altura. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void frozenLakeIsLevelBloodIce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareSlab(helper, Blocks.SNOW_BLOCK.defaultBlockState());
        place(helper, ModConfiguredFeatures.HFIL_FROZEN_LAKE_KEY, ground.above(), 19L);
        Block ice = ModBlocks.HFIL_BLOOD_ICE.get();
        int maxY = Integer.MIN_VALUE;
        for (int dx = -PAD; dx <= PAD; dx++) {
            for (int dz = -PAD; dz <= PAD; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    if (level.getBlockState(ground.offset(dx, dy, dz)).is(ice)) maxY = Math.max(maxY, ground.getY() + dy);
                }
            }
        }
        helper.assertTrue(maxY != Integer.MIN_VALUE, "el lago es de sangre congelada");
        helper.assertTrue(maxY == ground.getY(), "lámina única: hielo a ras del suelo (maxY=" + maxY + ")");
        helper.succeed();
    }

    /**
     * Janemba Rift (rehecho el 2026-10-05): cada formación se coloca sobre una losa de carne, con
     * sus bloques propios y sin ninguno de lava, fuego, obsidiana o magma (la referencia de Fusion
     * Reborn no tiene nada de eso). Losa grande y alta: las formas se recentran en su chunk y
     * alcanzan ~20 bloques. Cada forma va en su propia losa limpia para medir solo la suya.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty", timeoutTicks = 200)
    public static void janembaRiftFeaturesPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState flesh = ModBlocks.HFIL_CORRUPTED_FLESH.get().defaultBlockState();
        Block stone = ModBlocks.HFIL_VOID_STONE.get();
        Block blue = ModBlocks.HFIL_JANEMBA_BLUE_MASS.get();
        Block bubble = ModBlocks.HFIL_JANEMBA_BUBBLE.get();
        BlockPos rel = new BlockPos(1, 150, 1);
        int pad = 24;

        // El centro de la losa debe ser el centro de un chunk: las formas grandes se recentran ahí.
        BlockPos ground = chunkCenteredSlab(helper, flesh, pad, rel, 40);
        place(helper, ModConfiguredFeatures.JANEMBA_MOUND_KEY, ground.above(), 3L);
        helper.assertTrue(countBox(level, ground, pad, 1, 8, s -> s.is(stone) || s.is(flesh.getBlock())) > 20,
                "el montículo levanta el suelo");

        ground = chunkCenteredSlab(helper, flesh, pad, rel, 40);
        place(helper, ModConfiguredFeatures.JANEMBA_SPIRE_KEY, ground.above(), 5L);
        helper.assertTrue(countBox(level, ground, pad, 12, 30, s -> s.is(stone) || s.is(blue)) > 10,
                "la aguja sube más de 12 bloques");

        ground = chunkCenteredSlab(helper, flesh, pad, rel, 40);
        place(helper, ModConfiguredFeatures.JANEMBA_RIDGE_KEY, ground.above(), 7L);
        helper.assertTrue(countBox(level, ground, pad, 4, 20, s -> s.is(stone)) > 6,
                "la cresta tiene pinchos por encima del lomo");
        // Regresión (2026-10-05): la cresta trepaba por su propio lomo y salía como un poste
        // diagonal de decenas de bloques. Sobre llano, nada por encima de lomo + pincho más alto.
        helper.assertTrue(countBox(level, ground, pad, 21, 40, s -> s.is(stone) || s.is(flesh.getBlock())) == 0,
                "la cresta no trepa por sí misma (nada por encima de 20 sobre llano)");

        ground = chunkCenteredSlab(helper, flesh, pad, rel, 40);
        place(helper, ModConfiguredFeatures.JANEMBA_DOME_KEY, ground.above(), 9L);
        helper.assertTrue(countBox(level, ground, pad, 1, 30, s -> s.is(blue)) > 60,
                "la masa azul es de masa azul (no hormigón)");

        ground = chunkCenteredSlab(helper, flesh, pad, rel, 40);
        place(helper, ModConfiguredFeatures.JANEMBA_RIB_KEY, ground.above(), 11L);
        helper.assertTrue(countBox(level, ground, pad, 3, 24, s -> s.is(stone) || s.is(flesh.getBlock())) > 30,
                "el arco se levanta sobre el suelo");

        ground = chunkCenteredSlab(helper, flesh, pad, rel, 70);
        boolean orbs = false;
        for (long seed = 13L; seed < 23L && !orbs; seed++) {
            var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                    .getOrThrow(ModConfiguredFeatures.JANEMBA_ORB_KEY);
            orbs = feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), ground.above());
        }
        helper.assertTrue(orbs, "las esferas se colocan sobre suelo plano");
        int shells = countBox(level, ground, pad, -3, 70, s -> s.is(bubble));
        int shine = countBox(level, ground, pad, -3, 70, s -> s.is(bubble) && s.getValue(com.hmc.zenkai.content.block.JanembaBubbleBlock.SHINE));
        helper.assertTrue(shells > 8, "esferas de gelatina (" + shells + ")");
        helper.assertTrue(shine < shells, "el reflejo es un parche, no la esfera entera");

        ground = chunkCenteredSlab(helper, flesh, pad, rel, 40);
        place(helper, ModConfiguredFeatures.JANEMBA_NUB_KEY, ground.above(), 17L);
        helper.assertTrue(countBox(level, ground, pad, 1, 4, s -> s.is(stone) || s.is(flesh.getBlock()) || s.is(bubble)) > 0,
                "protuberancias sobre el suelo");

        helper.assertTrue(countBox(level, ground, pad, -3, 70, s -> s.is(Blocks.LAVA) || s.is(Blocks.FIRE)
                || s.is(Blocks.OBSIDIAN) || s.is(Blocks.MAGMA_BLOCK)) == 0,
                "nada de estética Nether");
        helper.succeed();
    }

    /**
     * Gelatina tintable (2026-10-05): un tinte sobre el bloque cambia su color y gasta el tinte, y
     * el color viaja en el ítem (componente BLOCK_STATE) de ida y vuelta: clic central → ítem del
     * color → al aplicarlo a un estado, ese color.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void janembaBubbleIsDyeable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var C = com.hmc.zenkai.content.block.JanembaBubbleBlock.Color.class;
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        Block bubble = ModBlocks.HFIL_JANEMBA_BUBBLE.get();
        level.setBlock(pos, bubble.defaultBlockState(), Block.UPDATE_CLIENTS);

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack dye = new ItemStack(Items.CYAN_DYE, 2);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, dye);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.UP, pos, false);
        level.getBlockState(pos).useItemOn(dye, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        var dyed = level.getBlockState(pos).getValue(com.hmc.zenkai.content.block.JanembaBubbleBlock.COLOR);
        helper.assertTrue(dyed == com.hmc.zenkai.content.block.JanembaBubbleBlock.Color.CYAN,
                "el tinte cian vuelve cian la gelatina (es " + dyed + ")");
        helper.assertTrue(dye.getCount() == 1, "se gasta un tinte");

        ItemStack picked = bubble.getCloneItemStack(level.getBlockState(pos), null, level, pos, null);
        helper.assertTrue(com.hmc.zenkai.content.block.JanembaBubbleBlock.colorOf(picked)
                        == com.hmc.zenkai.content.block.JanembaBubbleBlock.Color.CYAN,
                "el ítem conserva el color");
        var props = picked.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
        helper.assertTrue(props != null && props.apply(bubble.defaultBlockState())
                        .getValue(com.hmc.zenkai.content.block.JanembaBubbleBlock.COLOR)
                        == com.hmc.zenkai.content.block.JanembaBubbleBlock.Color.CYAN,
                "al colocar el ítem, el bloque sale de su color");
        helper.assertTrue(C.getEnumConstants().length == 16, "un color por cada tinte vanilla");
        helper.succeed();
    }

    /** Losa con el centro en el centro de un chunk (ver JanembaShapes.centered), con aire encima
     *  hasta {@code clear} bloques. Alturas propias (150, 225) para no pisar las losas de los
     *  gametests vecinos, que se colocan muy juntos con la plantilla "empty". */
    private static BlockPos chunkCenteredSlab(GameTestHelper helper, BlockState groundBlock, int pad, BlockPos rel, int clear) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(rel);
        BlockPos center = new BlockPos((abs.getX() & ~15) + 8, abs.getY(), (abs.getZ() & ~15) + 8);
        for (int dx = -pad; dx <= pad; dx++) {
            for (int dz = -pad; dz <= pad; dz++) {
                for (int dy = -3; dy <= 0; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), groundBlock, Block.UPDATE_CLIENTS);
                }
                for (int dy = 1; dy <= clear; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return center;
    }

    /** Cuántos bloques cumplen la condición en el prisma de la losa entre dy0 y dy1 (incluidos). */
    private static int countBox(ServerLevel level, BlockPos center, int pad, int dy0, int dy1, Predicate<BlockState> test) {
        int n = 0;
        for (int dx = -pad; dx <= pad; dx++) {
            for (int dz = -pad; dz <= pad; dz++) {
                for (int dy = dy0; dy <= dy1; dy++) {
                    if (test.test(level.getBlockState(center.offset(dx, dy, dz)))) n++;
                }
            }
        }
        return n;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void mudPotAndCairnsPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareSlab(helper, Blocks.STONE.defaultBlockState());
        place(helper, ModConfiguredFeatures.HFIL_MUD_POT_KEY, ground.above(), 5L);
        helper.assertTrue(count(level, ground, s -> s.is(ModBlocks.HFIL_BOILING_MUD.get())) > 0,
                "la charca de barro sustituye el suelo");

        BlockPos ground2 = prepareSlab(helper, Blocks.STONE.defaultBlockState());
        place(helper, ModConfiguredFeatures.HFIL_CAIRNS_KEY, ground2.above(), 7L);
        helper.assertTrue(count(level, ground2.above(), s -> s.is(Blocks.COBBLED_DEEPSLATE_WALL)
                        || s.is(Blocks.COBBLESTONE_WALL)) > 0,
                "al menos un mojón apilado sobre el suelo");
        helper.succeed();
    }

    /**
     * Losa de (2·PAD+1)² del bloque de suelo dado, con aire encima, centrada en el test. Se
     * rehace cada vez (también la segunda vez dentro del mismo test: deja el suelo limpio para
     * otra feature). Devuelve el bloque central de la losa.
     */
    private static BlockPos prepareSlab(GameTestHelper helper, BlockState groundBlock) {
        return prepareSlab(helper, groundBlock, PAD, new BlockPos(1, 1, 1));
    }

    /** Losa más grande, con su centro relativo propio: una losa ancha a la altura normal pisaría
     *  la zona de los gametests vecinos, así que se sube al aire. */
    private static BlockPos prepareSlab(GameTestHelper helper, BlockState groundBlock, int pad, BlockPos relCenter) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(relCenter);
        for (int dx = -pad; dx <= pad; dx++) {
            for (int dz = -pad; dz <= pad; dz++) {
                for (int dy = -3; dy <= 0; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), groundBlock, Block.UPDATE_CLIENTS);
                }
                for (int dy = 1; dy <= 8; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return center;
    }

    private static void place(GameTestHelper helper, ResourceKey<ConfiguredFeature<?, ?>> key, BlockPos origin, long seed) {
        ServerLevel level = helper.getLevel();
        ConfiguredFeature<?, ?> feature = level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(key);
        boolean placed = feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), origin);
        helper.assertTrue(placed, "la feature " + key.location() + " se coloca sobre suelo plano");
    }

    /** Cuántos bloques de la losa (y de 3 bloques por encima/debajo) cumplen la condición. */
    private static int count(ServerLevel level, BlockPos center, Predicate<BlockState> test) {
        int n = 0;
        for (int dx = -PAD; dx <= PAD; dx++) {
            for (int dz = -PAD; dz <= PAD; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    if (test.test(level.getBlockState(center.offset(dx, dy, dz)))) n++;
                }
            }
        }
        return n;
    }
}
