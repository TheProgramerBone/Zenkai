package com.hmc.db_renewed.item.custom;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;

public class ModFoodProperties {
    public static final FoodProperties SENZU_BEAN = new FoodProperties.Builder()
            .fast()
            .nutrition(20)
            .alwaysEdible()
            .saturationModifier(1.0f)
            .effect(()->new MobEffectInstance(MobEffects.HEAL,20,90),1.0f)
            .effect(()->new MobEffectInstance(MobEffects.SATURATION,20,255),1.0f)
            .build();
}
