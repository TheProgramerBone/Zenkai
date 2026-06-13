package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.CommonAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class GeoLayerArmorItem extends ArmorItem implements GeoItem {

    private final ResourceLocation modelPath;
    private final ResourceLocation texturePath;
    private final ResourceLocation animationPath;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GeoLayerArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties,
                             String modelPath, String texturePath, String animationPath) {
        super(material, type, properties);
        this.modelPath     = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, modelPath);
        this.texturePath   = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, texturePath);
        this.animationPath = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, animationPath);
    }

    public ResourceLocation getModelPath()     { return modelPath; }
    public ResourceLocation getTexturePath()   { return texturePath; }
    public ResourceLocation getAnimationPath() { return animationPath; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        CommonAnimations.genericIdleController(this);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoLayerArmorRenderer renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
                    @Nullable T livingEntity, ItemStack itemStack,
                    @Nullable EquipmentSlot equipmentSlot, @Nullable HumanoidModel<T> original) {
                if (this.renderer == null)
                    this.renderer = new GeoLayerArmorRenderer(GeoLayerArmorItem.this);
                return this.renderer;
            }
        });
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}