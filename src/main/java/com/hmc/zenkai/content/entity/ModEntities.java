package com.hmc.zenkai.content.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.misc.IsaacEntity;
import com.hmc.zenkai.content.entity.ki_attacks.KiBlastEntity;
import com.hmc.zenkai.content.entity.misc.ShadowKintounEntity;
import com.hmc.zenkai.content.entity.namek.NamekianEntity;
import com.hmc.zenkai.content.entity.namek.NamekianWarriorEntity;
import com.hmc.zenkai.content.entity.misc.KintounEntity;
import com.hmc.zenkai.content.entity.otherworld.YemmaEntity;
import com.hmc.zenkai.content.entity.misc.SpacePodEntity;
import com.hmc.zenkai.content.entity.overworld.SaibamanEntity;
import com.hmc.zenkai.content.entity.overworld.ShenLongEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Zenkai.MOD_ID);

    public static final Supplier<EntityType<SpacePodEntity>> SPACE_POD =
            ENTITY_TYPES.register("space_pod",() -> EntityType.Builder.of(SpacePodEntity::new, MobCategory.CREATURE)
                    .sized(2.45f,2.5f).build("space_pod"));

    public static final Supplier<EntityType<KintounEntity>> KINTOUN =
            ENTITY_TYPES.register("kintoun",() -> EntityType.Builder.of(KintounEntity::new, MobCategory.CREATURE)
                    .sized(2.3f,0.8f).build("kintoun"));

    public static final Supplier<EntityType<ShadowKintounEntity>> SHADOW_KINTOUN =
            ENTITY_TYPES.register("kintoun_shadow",() -> EntityType.Builder.of(ShadowKintounEntity::new, MobCategory.CREATURE)
                    .sized(2.3f,0.8f).build("kintoun_shadow"));

    public static final Supplier<EntityType<ShenLongEntity>> SHENLONG =
            ENTITY_TYPES.register("shenlong",() -> EntityType.Builder.of(ShenLongEntity::new, MobCategory.MISC)
                    .sized(5f,15f).build("shenlong"));

    public static final Supplier<EntityType<NamekianWarriorEntity>> NAMEKIAN_WARRIOR =
            ENTITY_TYPES.register("namekian_warrior",() -> EntityType.Builder.of(NamekianWarriorEntity::new, MobCategory.CREATURE)
                    .sized(1,2).build("namekian_warrior"));

    public static final Supplier<EntityType<SaibamanEntity>> SAIBAMAN =
            ENTITY_TYPES.register("saibaman",() -> EntityType.Builder.of(SaibamanEntity::new, MobCategory.CREATURE)
                    .sized(1,1.5f).build("saibaman"));

    public static final Supplier<EntityType<NamekianEntity>> NAMEKIAN =
            ENTITY_TYPES.register("namekian",() -> EntityType.Builder.of(NamekianEntity::new, MobCategory.CREATURE)
                    .sized(1,2).build("namekian"));

    public static final Supplier<EntityType<IsaacEntity>> ISAAC =
            ENTITY_TYPES.register("isaac", () -> EntityType.Builder.of(IsaacEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).build("isaac"));

    public static final Supplier<EntityType<KiBlastEntity>> KI_BLAST =
            ENTITY_TYPES.register("ki_blast",()-> EntityType.Builder.of(KiBlastEntity::new,MobCategory.MISC)
                    .sized(1,1).build("ki_blast"));

    public static final Supplier<EntityType<YemmaEntity>> YEMMA =
            ENTITY_TYPES.register("yemma", () -> EntityType.Builder.of(YemmaEntity::new, MobCategory.MISC)
                    .sized(4, 10.0f).build("yemma"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}