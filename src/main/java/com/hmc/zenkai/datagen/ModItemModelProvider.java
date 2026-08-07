package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.LinkedHashMap;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Zenkai.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.SENZU_BEAN.get());
        basicItem(ModItems.BASIC_CIRCUIT.get());
        basicItem(ModItems.ADVANCED_CIRCUIT.get());
        basicItem(ModItems.ELITE_CIRCUIT.get());
        basicItem(ModItems.SPACE_POD_ITEM.get());
        basicItem(ModItems.KINTOUN_ITEM.get());
        basicItem(ModItems.SHADOW_KINTOUN_ITEM.get());
        basicItem(ModItems.HALO.get());
        basicItem(ModItems.SCOUTER_RADAR_UPGRADE.get());
        basicItem(ModItems.NAMEKIAN_HERB.get());
        basicItem(ModItems.NAMEKIAN_HERB_SEEDS.get());
        basicItem(ModItems.HEALING_WATER_BOTTLE.get());
        basicItem(ModItems.RAW_KATCHIN.get());
        basicItem(ModItems.DIRTY_RAW_KATCHIN.get());
        basicItem(ModItems.KATCHIN_INGOT.get());

        // El item de la puerta es una textura plana propia, no el modelo de bloque.
        basicItem(ModBlocks.AJISA_DOOR.get().asItem());

        withExistingParent("ajisa_fence", mcLoc("block/fence_inventory"))
                .texture("texture", modLoc("block/ajisa_planks"));
        withExistingParent("ajisa_button", mcLoc("block/button_inventory"))
                .texture("texture", modLoc("block/ajisa_planks"));
        withExistingParent("ajisa_log", modLoc("block/ajisa_log"));
        withExistingParent("stripped_ajisa_log", modLoc("block/stripped_ajisa_log"));
        withExistingParent("ajisa_wood", modLoc("block/ajisa_wood"));
        withExistingParent("stripped_ajisa_wood", modLoc("block/stripped_ajisa_wood"));
        withExistingParent("ajisa_stairs", modLoc("block/ajisa_stairs"));
        withExistingParent("ajisa_slab", modLoc("block/ajisa_slab"));
        withExistingParent("ajisa_fence_gate", modLoc("block/ajisa_fence_gate"));
        withExistingParent("ajisa_trapdoor", modLoc("block/ajisa_trapdoor_bottom"));
        withExistingParent("ajisa_pressure_plate", modLoc("block/ajisa_pressure_plate"));

        withExistingParent(ModItems.NAMEKIAN_SPAWN_EGG.getId().getPath(),mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.WARRIOR_SPAWN_EGG.getId().getPath(),mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.YEMMA_SPAWN_EGG.getId().getPath(),mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.SAIBAMAN_SPAWN_EGG.getId().getPath(),mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.SHENLONG_SPAWN_EGG.getId().getPath(),mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.ISAAC_SPAWN_EGG.getId().getPath(),mcLoc("item/template_spawn_egg"));

        basicItem(ModItems.NAMEK_CRYSTAL.get());
        basicItem(ModItems.ENERGY_CRYSTAL.get());
        basicItem(ModItems.SACRED_STONE.get());

        // Escaleras y losas heredan del modelo de bloque; los muros necesitan el modelo
        // "inventory" de vainilla, porque el de bloque son piezas sueltas sin forma completa.
        withExistingParent("sacred_stone_stairs", modLoc("block/sacred_stone_stairs"));
        withExistingParent("sacred_stone_slab", modLoc("block/sacred_stone_slab"));
        withExistingParent("polished_sacred_stone_stairs", modLoc("block/polished_sacred_stone_stairs"));
        withExistingParent("polished_sacred_stone_slab", modLoc("block/polished_sacred_stone_slab"));
        withExistingParent("sacred_stone_brick_stairs", modLoc("block/sacred_stone_brick_stairs"));
        withExistingParent("sacred_stone_brick_slab", modLoc("block/sacred_stone_brick_slab"));
        withExistingParent("cut_katchin_stairs", modLoc("block/cut_katchin_stairs"));
        withExistingParent("cut_katchin_slab", modLoc("block/cut_katchin_slab"));
        withExistingParent("cut_katchin_wall", mcLoc("block/wall_inventory"))
                .texture("wall", modLoc("block/cut_katchin"));
        withExistingParent("katchin_pillar", modLoc("block/katchin_pillar"));

        withExistingParent("sacred_stone_wall", mcLoc("block/wall_inventory"))
                .texture("wall", modLoc("block/sacred_stone_block"));
        withExistingParent("polished_sacred_stone_wall", mcLoc("block/wall_inventory"))
                .texture("wall", modLoc("block/polished_sacred_stone"));
        withExistingParent("sacred_stone_brick_wall", mcLoc("block/wall_inventory"))
                .texture("wall", modLoc("block/sacred_stone_bricks"));
        for (var fam : ModBlocks.STRUCTURAL_CONCRETE_FAMILIES) {
            withExistingParent(fam.name() + "_stairs", modLoc("block/" + fam.name() + "_stairs"));
            withExistingParent(fam.name() + "_slab",   modLoc("block/" + fam.name() + "_slab"));
            // El muro no tiene modelo de inventario propio: se monta con la textura base.
            withExistingParent(fam.name() + "_wall", mcLoc("block/wall_inventory"))
                    .texture("wall", modLoc("block/" + fam.name()));
        }
        registerRaceSkinModels();
    }

    private void trimmedArmorItem(DeferredItem<ArmorItem> itemDeferredItem) {
        final String MOD_ID = Zenkai.MOD_ID; // Change this to your mod id

        ArmorItem armorItem = itemDeferredItem.get();
        trimMaterials.forEach((trimMaterial, value) -> {
            float trimValue = value;

            String armorType = switch (armorItem.getEquipmentSlot()) {
                case HEAD -> "helmet";
                case CHEST -> "chestplate";
                case LEGS -> "leggings";
                case FEET -> "boots";
                default -> "";
            };

            String armorItemPath = armorItem.toString();
            String trimPath = "trims/items/" + armorType + "_trim_" + trimMaterial.location().getPath();
            String currentTrimName = armorItemPath + "_" + trimMaterial.location().getPath() + "_trim";
            ResourceLocation armorItemResLoc = ResourceLocation.parse(armorItemPath);
            ResourceLocation trimResLoc = ResourceLocation.parse(trimPath); // minecraft namespace
            ResourceLocation trimNameResLoc = ResourceLocation.parse(currentTrimName);
            existingFileHelper.trackGenerated(trimResLoc, PackType.CLIENT_RESOURCES, ".png", "textures");

            getBuilder(currentTrimName)
                    .parent(new ModelFile.UncheckedModelFile("item/generated"))
                    .texture("layer0", armorItemResLoc.getNamespace() + ":item/" + armorItemResLoc.getPath())
                    .texture("layer1", trimResLoc);

            this.withExistingParent(itemDeferredItem.getId().getPath(),
                            mcLoc("item/generated"))
                    .override()
                    .model(new ModelFile.UncheckedModelFile(trimNameResLoc.getNamespace() + ":item/" + trimNameResLoc.getPath()))
                    .predicate(mcLoc("trim_type"), trimValue).end()
                    .texture("layer0",
                            ResourceLocation.fromNamespaceAndPath(MOD_ID,
                                    "item/" + itemDeferredItem.getId().getPath()));
        });
    }

    private void handheldItem(DeferredItem<?> item) {
        withExistingParent(item.getId().getPath(),
                ResourceLocation.parse("item/handheld")).texture("layer0",
                ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "item/" + item.getId().getPath()));
    }

    private static final LinkedHashMap<ResourceKey<TrimMaterial>, Float> trimMaterials = new LinkedHashMap<>();
    static {
        trimMaterials.put(TrimMaterials.QUARTZ, 0.1F);
        trimMaterials.put(TrimMaterials.IRON, 0.2F);
        trimMaterials.put(TrimMaterials.NETHERITE, 0.3F);
        trimMaterials.put(TrimMaterials.REDSTONE, 0.4F);
        trimMaterials.put(TrimMaterials.COPPER, 0.5F);
        trimMaterials.put(TrimMaterials.GOLD, 0.6F);
        trimMaterials.put(TrimMaterials.EMERALD, 0.7F);
        trimMaterials.put(TrimMaterials.DIAMOND, 0.8F);
        trimMaterials.put(TrimMaterials.LAPIS, 0.9F);
        trimMaterials.put(TrimMaterials.AMETHYST, 1.0F);
    }

    /**
     * Modelos de inventario de los geo items (skins de raza, pelo, guadaña de ki).
     * GeckoLib los dibuja puestos en el jugador, pero en el inventario Minecraft sigue
     * pidiendo un modelo de item normal; sin él salen como cubo morado y llenan el log de
     * "Unable to load model".
     * Las tres razas comparten cuatro siluetas genéricas: en el inventario estas piezas son
     * un marcador de posición, no un objeto que el jugador vaya a comparar entre sí. Las
     * variantes _colorable y _female tampoco se distinguen ahí, porque lo que cambian es el
     * tinte y el modelo geo en el mundo.
     */
    private void registerRaceSkinModels() {
        flatGroup("race_helmet",
                "human_race_helmet", "human_race_helmet_colorable",
                "human_race_helmet_female", "human_race_helmet_colorable_female",
                "namekian_race_helmet", "namekian_race_helmet_colorable",
                "arcosian_race_helmet");

        flatGroup("race_chestplate",
                "human_race_chestplate", "human_race_chestplate_colorable",
                "human_race_chestplate_female", "human_race_chestplate_colorable_female",
                "namekian_race_chestplate", "namekian_race_chestplate_colorable",
                "arcosian_race_chestplate");

        flatGroup("race_leggings",
                "human_race_leggings", "human_race_leggings_colorable",
                "human_race_leggings_female", "human_race_leggings_colorable_female",
                "namekian_race_leggings", "namekian_race_leggings_colorable",
                "arcosian_race_leggings");

        flatGroup("race_boots",
                "human_race_boots", "human_race_boots_colorable",
                "human_race_boots_female", "human_race_boots_colorable_female",
                "namekian_race_boots", "namekian_race_boots_colorable",
                "arcosian_race_boots");

        flatGroup("hair_1");
        flatGroup("ssj_hair1");
        flatGroup("ki_scythe");
    }

    private void flatGroup(String texture, String... names) {
        ResourceLocation tex = modLoc("item/" + texture);
        for (String name : names) {
            withExistingParent(name, mcLoc("item/generated")).texture("layer0", tex);
        }
    }
}