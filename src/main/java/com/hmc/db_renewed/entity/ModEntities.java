package com.hmc.db_renewed.entity;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.entity.saiyan_pod.SpacePodEntity;
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

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}