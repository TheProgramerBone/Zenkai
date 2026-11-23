package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.EnergyGeneratorBlock;
import com.hmc.zenkai.content.block.HfilGeyserBlock;
import com.hmc.zenkai.content.block.NamekianHerbCropBlock;
import com.hmc.zenkai.content.block.NamekianLampBlock;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, Zenkai.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        blockWithItem(ModBlocks.NAMEKIAN_DIRT);
        simpleBlockWithItem(ModBlocks.NAMEKIAN_DIRT_PATH.get(),
                models().withExistingParent("namekian_dirt_path", mcLoc("block/dirt_path"))
                        .texture("top",  modLoc("block/namekian_dirt_path_top"))
                        .texture("side", modLoc("block/namekian_dirt_path_side")));
        blockWithItem(ModBlocks.KATCHIN_ORE);
        blockWithItem(ModBlocks.DEEPSLATE_KATCHIN_ORE);
        blockWithItem(ModBlocks.KATCHIN_BLOCK);
        blockWithItem(ModBlocks.CUT_KATCHIN);
        stairsBlock((StairBlock) ModBlocks.CUT_KATCHIN_STAIRS.get(), modLoc("block/cut_katchin"));
        slabBlock((SlabBlock) ModBlocks.CUT_KATCHIN_SLAB.get(), modLoc("block/cut_katchin"), modLoc("block/cut_katchin"));
        wallBlock((WallBlock) ModBlocks.CUT_KATCHIN_WALL.get(), modLoc("block/cut_katchin"));
        axisBlock((RotatedPillarBlock) ModBlocks.KATCHIN_PILLAR.get(),
                modLoc("block/katchin_pillar"), modLoc("block/katchin_pillar_top"));
        blockWithItem(ModBlocks.NAMEKIAN_STONE);
        blockWithItem(ModBlocks.NAMEKIAN_COBBLESTONE);
        blockWithItem(ModBlocks.NAMEKIAN_SAND);
        blockWithItem(ModBlocks.NAMEKIAN_GRAVEL);
        blockWithItem(ModBlocks.NAMEKIAN_COAL_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_IRON_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_COPPER_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_GOLD_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_REDSTONE_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_LAPIS_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_DIAMOND_ORE);
        blockWithItem(ModBlocks.ROCKY_BLOCK);
        blockWithItem(ModBlocks.HFIL_SCORCHED_STONE);
        blockWithItem(ModBlocks.HFIL_PALE_STONE);
        blockWithItem(ModBlocks.HFIL_BOILING_MUD);
        blockWithItem(ModBlocks.HFIL_BLOOD_ICE);
        blockWithItem(ModBlocks.HFIL_SULFUR_CRUST);
        simpleBlockWithItem(ModBlocks.HFIL_FUMAROLE.get(), models().cubeBottomTop("hfil_fumarole",
                modLoc("block/hfil_sulfur_crust"), modLoc("block/hfil_sulfur_crust"),
                modLoc("block/hfil_fumarole_top")));
        // Menas del HFIL: textura = id (tools/gen_hfil_ores.py). La redstone tiene LIT, pero
        // como la vanilla usa el mismo modelo encendida y apagada.
        // Las de ceniza, de tres caras como su arenisca (si no, el canto del lateral sale arriba).
        ModBlocks.HFIL_ORES.forEach((stone, m) -> m.values().forEach(ore -> {
            if (stone != com.hmc.zenkai.registry.HfilOres.Stone.CINDER) { blockWithItem(ore); return; }
            String id = ore.getId().getPath();
            simpleBlockWithItem(ore.get(), models().cubeBottomTop(id, modLoc("block/" + id),
                    modLoc("block/" + id + "_bottom"), modLoc("block/" + id + "_top")));
        }));
        // Géiser: un modelo por suelo (GROUND), hecho del MISMO bloque que el bioma donde sale —
        // costados y base = ese bloque, arriba = ese bloque con la boca. Mismo modelo en las 16
        // etapas de STAGE: la erupción se ve por partículas, no por modelo.
        java.util.Map<HfilGeyserBlock.Ground, ModelFile> geyserModels = new java.util.EnumMap<>(HfilGeyserBlock.Ground.class);
        for (HfilGeyserBlock.Ground g : HfilGeyserBlock.Ground.values()) {
            String base = switch (g) {
                case SCORCHED -> "block/hfil_scorched_stone";
                case PALE -> "block/hfil_pale_stone";
                case CINDER -> "block/hfil_cinder_sand";
            };
            geyserModels.put(g, models().cubeBottomTop("hfil_geyser_" + g.getSerializedName(),
                    modLoc(base), modLoc(base), modLoc("block/hfil_geyser_top_" + g.getSerializedName())));
        }
        getVariantBuilder(ModBlocks.HFIL_GEYSER.get()).forAllStates(s -> ConfiguredModel.builder()
                .modelFile(geyserModels.get(s.getValue(HfilGeyserBlock.GROUND))).build());
        simpleBlockItem(ModBlocks.HFIL_GEYSER.get(), geyserModels.get(HfilGeyserBlock.Ground.SCORCHED));
        blockWithItem(ModBlocks.HFIL_SPIKE_ROCK);
        blockWithItem(ModBlocks.HFIL_CORRUPTED_FLESH);
        blockWithItem(ModBlocks.HFIL_VOID_STONE);
        blockWithItem(ModBlocks.HFIL_JANEMBA_BLUE_MASS);
        // Gelatina de las esferas: un cubo con tintindex 0 en las 6 caras (el modelo de las hojas
        // vanilla) y translúcido. Mismo modelo en los 14 estados: el color lo pone el tinte del
        // cliente (JanembaBubbleColors), no el modelo.
        simpleBlockWithItem(ModBlocks.HFIL_JANEMBA_BUBBLE.get(),
                models().withExistingParent("hfil_janemba_bubble", mcLoc("block/leaves"))
                        .texture("all", modLoc("block/hfil_janemba_bubble"))
                        .renderType("translucent"));
        blockWithItem(ModBlocks.HFIL_CINDER_SAND);
        // Tres caras como la arenisca vanilla: con cubeAll arriba y abajo salía el canto del lateral.
        simpleBlockWithItem(ModBlocks.HFIL_CINDER_SANDSTONE.get(), models().cubeBottomTop("hfil_cinder_sandstone",
                modLoc("block/hfil_cinder_sandstone"), modLoc("block/hfil_cinder_sandstone_bottom"),
                modLoc("block/hfil_cinder_sandstone_top")));
        blockWithItem(ModBlocks.HTC_BLOCK);
        blockWithItem(ModBlocks.HTC_PORTAL);
        for (var fam : ModBlocks.buildingFamilies()) {
            ResourceLocation tex = modLoc("block/" + fam.name());
            if (fam.block() == ModBlocks.CUT_HFIL_CINDER_SANDSTONE) {
                // Como cut_sandstone vanilla: lateral cortado, arriba y abajo la cara lisa de la arenisca.
                ResourceLocation end = modLoc("block/hfil_cinder_sandstone_top");
                simpleBlockWithItem(fam.block().get(), models().cubeColumn(fam.name(), tex, end));
                stairsBlock(fam.stairs().get(), tex, end, end);
                slabBlock(fam.slab().get(), modLoc("block/" + fam.name()), tex, end, end);
                wallBlock(fam.wall().get(), tex);
                continue;
            }
            blockWithItem(fam.block());
            stairsBlock(fam.stairs().get(), tex);
            slabBlock(fam.slab().get(), tex, tex);
            wallBlock(fam.wall().get(), tex);
        }
        blockWithItem(ModBlocks.OTHERWORLD_CLOUD);
        var energyOreModel = models().cubeAll("energy_crystal_ore", modLoc("block/energy_crystal_ore"));
        getVariantBuilder(ModBlocks.ENERGY_CRYSTAL_ORE.get())
                .forAllStates(s -> ConfiguredModel.builder().modelFile(energyOreModel).build());
        simpleBlockItem(ModBlocks.ENERGY_CRYSTAL_ORE.get(), energyOreModel);

        var lampOn  = models().cubeAll("namekian_lamp", modLoc("block/namekian_lamp"));
        var lampOff = models().cubeAll("namekian_lamp_off", modLoc("block/namekian_lamp_off"));
        getVariantBuilder(ModBlocks.NAMEKIAN_LAMP.get())
                .forAllStates(s -> ConfiguredModel.builder()
                        .modelFile(s.getValue(NamekianLampBlock.LIT) ? lampOn : lampOff)
                        .build());
        simpleBlockItem(ModBlocks.NAMEKIAN_LAMP.get(), lampOn);
        blockWithItem(ModBlocks.NPC_MARKER);
        makeCrop((CropBlock) ModBlocks.NAMEKIAN_HERB_CROP.get(), "namekian_herb_crop_stage", "namekian_herb_crop_stage");
        logBlock((RotatedPillarBlock) ModBlocks.AJISA_LOG.get());
        logBlock((RotatedPillarBlock) ModBlocks.STRIPPED_AJISA_LOG.get());
        axisBlock((RotatedPillarBlock) ModBlocks.AJISA_WOOD.get(),
                modLoc("block/ajisa_log"), modLoc("block/ajisa_log"));
        axisBlock((RotatedPillarBlock) ModBlocks.STRIPPED_AJISA_WOOD.get(),
                modLoc("block/stripped_ajisa_log"), modLoc("block/stripped_ajisa_log"));

        blockWithItem(ModBlocks.AJISA_PLANKS);

        simpleBlockWithItem(ModBlocks.AJISA_LEAVES.get(),
                models().cubeAll("ajisa_leaves", modLoc("block/ajisa_leaves")).renderType("cutout_mipped"));

        simpleBlock(ModBlocks.AJISA_SAPLING.get(),
                models().cross("ajisa_sapling", modLoc("block/ajisa_sapling")).renderType("cutout"));
        itemModels().getBuilder("ajisa_sapling")
                .parent(itemModels().getExistingFile(mcLoc("item/generated")))
                .texture("layer0", modLoc("block/ajisa_sapling"));
        simpleBlock(ModBlocks.AJISA_FLOWER.get(),
                models().cross("ajisa_flower", modLoc("block/ajisa_flower")).renderType("cutout"));
        itemModels().getBuilder("ajisa_flower")
                .parent(itemModels().getExistingFile(mcLoc("item/generated")))
                .texture("layer0", modLoc("block/ajisa_flower"));

        stairsBlock((StairBlock) ModBlocks.AJISA_STAIRS.get(), modLoc("block/ajisa_planks"));
        slabBlock((SlabBlock) ModBlocks.AJISA_SLAB.get(), modLoc("block/ajisa_planks"), modLoc("block/ajisa_planks"));
        fenceBlock((FenceBlock) ModBlocks.AJISA_FENCE.get(), modLoc("block/ajisa_planks"));
        fenceGateBlock((FenceGateBlock) ModBlocks.AJISA_FENCE_GATE.get(), modLoc("block/ajisa_planks"));
        doorBlockWithRenderType((DoorBlock) ModBlocks.AJISA_DOOR.get(),
                modLoc("block/ajisa_door_bottom"), modLoc("block/ajisa_door_top"), "cutout");
        trapdoorBlockWithRenderType((TrapDoorBlock) ModBlocks.AJISA_TRAPDOOR.get(),
                modLoc("block/ajisa_trapdoor"), true, "cutout");
        buttonBlock((ButtonBlock) ModBlocks.AJISA_BUTTON.get(), modLoc("block/ajisa_planks"));
        pressurePlateBlock((PressurePlateBlock) ModBlocks.AJISA_PRESSURE_PLATE.get(), modLoc("block/ajisa_planks"));

        blockWithItem(ModBlocks.NAMEK_CRYSTAL_ORE);
        blockWithItem(ModBlocks.SACRED_STONE_ORE);
        blockWithItem(ModBlocks.NAMEK_CRYSTAL_BLOCK);
        blockWithItem(ModBlocks.ENERGY_CRYSTAL_BLOCK);
        blockWithItem(ModBlocks.SACRED_STONE_BLOCK);
        blockWithItem(ModBlocks.POLISHED_SACRED_STONE);
        blockWithItem(ModBlocks.SACRED_STONE_BRICKS);

        stairsBlock((StairBlock) ModBlocks.SACRED_STONE_STAIRS.get(), modLoc("block/sacred_stone_block"));
        slabBlock((SlabBlock) ModBlocks.SACRED_STONE_SLAB.get(), modLoc("block/sacred_stone_block"), modLoc("block/sacred_stone_block"));
        wallBlock((WallBlock) ModBlocks.SACRED_STONE_WALL.get(), modLoc("block/sacred_stone_block"));

        stairsBlock((StairBlock) ModBlocks.POLISHED_SACRED_STONE_STAIRS.get(), modLoc("block/polished_sacred_stone"));
        slabBlock((SlabBlock) ModBlocks.POLISHED_SACRED_STONE_SLAB.get(), modLoc("block/polished_sacred_stone"), modLoc("block/polished_sacred_stone"));
        wallBlock((WallBlock) ModBlocks.POLISHED_SACRED_STONE_WALL.get(), modLoc("block/polished_sacred_stone"));

        stairsBlock((StairBlock) ModBlocks.SACRED_STONE_BRICK_STAIRS.get(), modLoc("block/sacred_stone_bricks"));
        slabBlock((SlabBlock) ModBlocks.SACRED_STONE_BRICK_SLAB.get(), modLoc("block/sacred_stone_bricks"), modLoc("block/sacred_stone_bricks"));
        wallBlock((WallBlock) ModBlocks.SACRED_STONE_BRICK_WALL.get(), modLoc("block/sacred_stone_bricks"));

        // Un modelo POR ESTADO y con nombre propio. Generar los dos con el mismo nombre hace
        // que el segundo sobrescriba al primero en disco y el bloque apagado acabe con la
        // textura encendida — sin error de compilación ni aviso en el log.
        horizontalBlock(ModBlocks.ENERGY_GENERATOR.get(), state ->
                state.getValue(EnergyGeneratorBlock.LIT)
                        ? models().orientable("energy_generator_on",
                        modLoc("block/energy_generator_side_on"),
                        modLoc("block/energy_generator_front"),
                        modLoc("block/energy_generator_top"))
                        : models().orientable("energy_generator",
                        modLoc("block/energy_generator_side"),
                        modLoc("block/energy_generator_front"),
                        modLoc("block/energy_generator_top")));

        // Máquina de fabricación (Fase 5b): cubo orientable con cara frontal encendida al fabricar.
        ModelFile fabOff = models().orientable("armor_fabricator", modLoc("block/armor_fabricator_side"),
                modLoc("block/armor_fabricator_front"), modLoc("block/armor_fabricator_top"));
        ModelFile fabOn = models().orientable("armor_fabricator_on", modLoc("block/armor_fabricator_side"),
                modLoc("block/armor_fabricator_front_on"), modLoc("block/armor_fabricator_top"));
        horizontalBlock(ModBlocks.ARMOR_FABRICATOR.get(),
                state -> state.getValue(com.hmc.zenkai.content.block.ArmorFabricatorBlock.LIT) ? fabOn : fabOff);
        simpleBlockItem(ModBlocks.ARMOR_FABRICATOR.get(), fabOff);

        // Cámara de gravedad: el modelo de pedestal es a mano (models/block/gravity_chamber*.json);
        // aquí solo la orientación y la pantalla encendida. El puerto es un cubo con una textura.
        horizontalBlock(ModBlocks.GRAVITY_CHAMBER.get(), state ->
                models().getExistingFile(modLoc(state.getValue(com.hmc.zenkai.content.block.GravityChamberBlock.ACTIVE)
                        ? "block/gravity_chamber_on" : "block/gravity_chamber")));
        simpleBlockWithItem(ModBlocks.GRAVITY_CHAMBER_PORT.get(),
                models().cubeAll("gravity_chamber_port", modLoc("block/gravity_chamber_port")));

        transportCable(ModBlocks.ENERGY_CABLE, "energy_cable");
        transportCable(ModBlocks.FLUID_PIPE, "fluid_pipe");
        transportTank(ModBlocks.ENERGY_TANK, "energy_tank");
        transportTank(ModBlocks.FLUID_TANK, "fluid_tank");

        // piece_connector: bloque de 6 direcciones (FACING incluye arriba/abajo, no solo
        // horizontal) — directionalBlock() es el helper de NeoForge para justo este caso:
        // calcula rotación X/Y por estado a partir de UN modelo donde la cara "arriba" del
        // modelo sin rotar es la que se toma como "frente" (a diferencia de horizontalBlock/
        // "orientable", donde el frente es la cara norte). Antes esto era un cubeAll con una
        // sola textura para las 6 caras — no se distinguía hacia dónde apuntaba el conector con
        // solo mirarlo, justo lo que hace falta ver al construir una pieza a mano.
        directionalBlock(ModBlocks.PIECE_CONNECTOR.get(), models().cubeBottomTop("piece_connector",
                modLoc("block/piece_connector_side"),
                modLoc("block/piece_connector_side"),
                modLoc("block/piece_connector_front")));

        // El fluido en sí lo pinta LiquidBlockRenderer (ver HealingWaterFluidClientExtensions),
        // NUNCA este modelo — pero BlockStateModelLoader igual pide un modelo para CADA valor
        // de LEVEL (0-15) de este bloque, y sin blockstate/modelo propios lanzaba "missing
        // model for variant" 16 veces en el log. Mismo patrón que minecraft:water: un modelo
        // sin "elements" (invisible) que solo declara la textura "particle", usada para las
        // partículas de goteo/salpicadura, no para el render del bloque.
        var healingWaterModel = models().getBuilder("healing_water")
                .texture("particle", mcLoc("block/water_still"));
        getVariantBuilder(ModBlocks.HEALING_WATER_BLOCK.get())
                .forAllStates(s -> ConfiguredModel.builder().modelFile(healingWaterModel).build());

        // Mismo caso que healing_water: modelo invisible solo con "particle".
        var boilingWaterModel = models().getBuilder("hfil_boiling_water")
                .texture("particle", mcLoc("block/water_still"));
        getVariantBuilder(ModBlocks.HFIL_BOILING_WATER_BLOCK.get())
                .forAllStates(s -> ConfiguredModel.builder().modelFile(boilingWaterModel).build());
    }

    // Atlas 16x16 de cada cable (tools/gen_transport_textures.py): núcleo con símbolo, placa del brazo,
    // punta de «empujar» y collar de «tirar».
    private static final float[] UV_CORE = {0, 0, 6, 6}, UV_PLATE = {6, 0, 12, 6},
            UV_PUSH = {12, 0, 16, 4}, UV_PULL = {12, 4, 16, 8};

    /** Una caja {x0,y0,z0,x1,y1,z1} (16avos) con la misma región de textura en cada cara, salvo la que se omita. */
    private static <T extends net.neoforged.neoforge.client.model.generators.ModelBuilder<T>> void box(
            T model, double[] b, net.minecraft.core.Direction skip, float[] uv) {
        var e = model.element().from((float) b[0], (float) b[1], (float) b[2]).to((float) b[3], (float) b[4], (float) b[5]);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (d == skip) continue;
            e.face(d).texture("#all").uvs(uv[0], uv[1], uv[2], uv[3]).end();
        }
        e.end();
    }

    /** Pieza suelta de un modelo compuesto, con la textura del bloque. */
    private net.neoforged.neoforge.client.model.generators.BlockModelBuilder part(ResourceLocation tex) {
        return models().nested().texture("particle", tex).texture("all", tex);
    }

    /**
     * Cable/tubería. Modelo COMPUESTO con una pieza por cada aspecto posible de cada cara: el núcleo
     * y, por cada una de las seis direcciones, el brazo normal, el de empujar (punta estrecha) y el
     * de tirar (collar ancho). Qué piezas se ven lo decide TransportBakedModel con los datos del
     * block entity, así el bloque tiene UN solo blockstate (como Mekanism). Las cajas salen de
     * TransportGeometry, las mismas que usan la forma y el clic.
     */
    private void transportCable(DeferredBlock<Block> block, String name) {
        ResourceLocation tex = modLoc("block/" + name);
        var loader = models().getBuilder(name).texture("particle", tex)
                .customLoader(net.neoforged.neoforge.client.model.generators.loaders.CompositeModelBuilder::begin);

        var core = part(tex);
        box(core, new double[] {5, 5, 5, 11, 11, 11}, null, UV_CORE);
        loader.child("core", core);

        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            var toCore = d.getOpposite();   // la cara del brazo pegada al núcleo no se ve
            var normal = part(tex);
            box(normal, com.hmc.zenkai.content.block.TransportGeometry.box(d, 0, 5, 5, 11), toCore, UV_PLATE);
            loader.child(d.getName() + "_normal", normal);

            var push = part(tex);
            box(push, com.hmc.zenkai.content.block.TransportGeometry.box(d, 2, 5, 5, 11), toCore, UV_PLATE);
            box(push, com.hmc.zenkai.content.block.TransportGeometry.box(d, 0, 2, 6, 10), toCore, UV_PUSH);
            loader.child(d.getName() + "_push", push);

            var pull = part(tex);
            box(pull, com.hmc.zenkai.content.block.TransportGeometry.box(d, 2, 5, 5, 11), toCore, UV_PLATE);
            box(pull, com.hmc.zenkai.content.block.TransportGeometry.box(d, 0, 2, 4, 12), null, UV_PULL);   // el collar enseña su cara interior
            loader.child(d.getName() + "_pull", pull);
        }
        simpleBlock(block.get(), loader.end());

        // Ítem: un tramo recto (el parent block/block aporta las transformaciones de GUI y mano).
        var item = itemModels().getBuilder(name)
                .parent(new ModelFile.UncheckedModelFile(mcLoc("block/block")))
                .texture("particle", tex).texture("all", tex);
        box(item, new double[] {5, 5, 5, 11, 11, 11}, null, UV_CORE);
        box(item, com.hmc.zenkai.content.block.TransportGeometry.box(net.minecraft.core.Direction.NORTH, 0, 5, 5, 11),
                net.minecraft.core.Direction.SOUTH, UV_PLATE);
        box(item, com.hmc.zenkai.content.block.TransportGeometry.box(net.minecraft.core.Direction.SOUTH, 0, 5, 5, 11),
                net.minecraft.core.Direction.NORTH, UV_PLATE);
    }

    /**
     * Tanque. Modelo COMPUESTO con TEXTURA CONECTADA: por cada cara, 16 piezas (una por variante de
     * bordes del grupo multibloque: izquierda=1, derecha=2, arriba=4, abajo=8, ver
     * TransportBlockEntity#CT_PROPERTY), cada una con su celda del atlas de 128x128 ({name}_side para
     * los lados, {name}_top para arriba y abajo; celda = bits % 4, bits / 4). Con cullface, así las caras
     * entre dos bloques del grupo no se dibujan. Encima, por cada cara y modo distinto de Normal, una
     * placa en RECORTE (textura con fondo transparente) 0,02 px por fuera: deja ver la cara conectada.
     * Qué piezas se ven lo decide TransportBakedModel con los datos del block entity.
     */
    private void transportTank(DeferredBlock<Block> block, String name) {
        ResourceLocation side = modLoc("block/" + name + "_side");
        ResourceLocation top = modLoc("block/" + name + "_top");
        ResourceLocation particle = modLoc("block/" + name + "_particle");   // el atlas entero daría trozos de marco

        var loader = models().getBuilder(name).texture("particle", particle)
                .customLoader(net.neoforged.neoforge.client.model.generators.loaders.CompositeModelBuilder::begin);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            ResourceLocation tex = d.getAxis() == net.minecraft.core.Direction.Axis.Y ? top : side;
            for (int bits = 0; bits < 16; bits++) {
                float u = (bits % 4) * 4f, v = (bits / 4) * 4f;
                var face = models().nested().texture("particle", particle).texture("all", tex);
                face.element().from(0, 0, 0).to(16, 16, 16)
                        .face(d).texture("#all").uvs(u, v, u + 4, v + 4).cullface(d).end().end();
                loader.child("face_" + d.getName() + "_" + bits, face);
            }
        }
        for (com.hmc.zenkai.feature.transport.Look look : new com.hmc.zenkai.feature.transport.Look[] {
                com.hmc.zenkai.feature.transport.Look.PUSH,
                com.hmc.zenkai.feature.transport.Look.PULL,
                com.hmc.zenkai.feature.transport.Look.NONE}) {
            ResourceLocation plateTex = modLoc("block/" + name + "_io_" + look.part());
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                float o = d.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE ? 16.02f : -0.02f;
                var plate = part(plateTex).texture("particle", particle).renderType("cutout");
                var e = switch (d.getAxis()) {
                    case X -> plate.element().from(o, 0, 0).to(o, 16, 16);
                    case Y -> plate.element().from(0, o, 0).to(16, o, 16);
                    case Z -> plate.element().from(0, 0, o).to(16, 16, o);
                };
                e.face(d).texture("#all").uvs(0, 0, 16, 16).end().end();
                loader.child(d.getName() + "_" + look.part(), plate);
            }
        }
        simpleBlock(block.get(), loader.end());

        // Ítem: el tanque suelto (celda 15 = los cuatro bordes en cada cara).
        var item = itemModels().getBuilder(name)
                .parent(new ModelFile.UncheckedModelFile(mcLoc("block/block")))
                .texture("particle", particle).texture("side", side).texture("top", top);
        var e = item.element().from(0, 0, 0).to(16, 16, 16);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            e.face(d).texture(d.getAxis() == net.minecraft.core.Direction.Axis.Y ? "#top" : "#side")
                    .uvs(12, 12, 16, 16).cullface(d).end();
        }
        e.end();
    }

    private void blockWithItem(DeferredBlock<?> deferredBlock) {
        simpleBlockWithItem(deferredBlock.get(), cubeAll(deferredBlock.get()));
    }

    private void makeCrop(CropBlock block, String modelName, String textureName) {
        getVariantBuilder(block).forAllStates(state -> {
            int age = state.getValue(NamekianHerbCropBlock.AGE);
            return ConfiguredModel.builder().modelFile(
                    models().crop(modelName + age, modLoc("block/" + textureName + age)).renderType("cutout")).build();
        });
    }
}
