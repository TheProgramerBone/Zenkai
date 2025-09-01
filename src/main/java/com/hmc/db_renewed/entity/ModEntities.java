package com.hmc.db_renewed.entity;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.entity.namekian.NamekianEntity;
import com.hmc.db_renewed.entity.namekian.NamekianWarriorEntity;
import com.hmc.db_renewed.entity.saiyan_pod.SpacePodEntity;
import com.hmc.db_renewed.entity.shenlong.ShenLongEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DragonBlockRenewed.MOD_ID);

    public static final Supplier<EntityType<SpacePodEntity>> SPACE_POD =
            ENTITY_TYPES.register("space_pod",() -> EntityType.Builder.of(SpacePodEntity::new, MobCategory.CREATURE)
                    .sized(2.45f,2.45f).build("space_pod"));

    public static final Supplier<EntityType<ShenLongEntity>> SHENLONG =
            ENTITY_TYPES.register("shenlong",() -> EntityType.Builder.of(ShenLongEntity::new, MobCategory.MISC)
                    .sized(5f,15f).build("shenlong"));

    public static final Supplier<EntityType<NamekianWarriorEntity>> NAMEKIAN_WARRIOR =
            ENTITY_TYPES.register("namekian_warrior",() -> EntityType.Builder.of(NamekianWarriorEntity::new, MobCategory.CREATURE)
                    .sized(1,2).build("namekian_warrior"));

    public static final Supplier<EntityType<NamekianEntity>> NAMEKIAN =
            ENTITY_TYPES.register("namekian",() -> EntityType.Builder.of(NamekianEntity::new, MobCategory.CREATURE)
                    .sized(1,2).build("namekian"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}