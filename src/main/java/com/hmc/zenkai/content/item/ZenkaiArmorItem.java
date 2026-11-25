package com.hmc.zenkai.content.item;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Pieza de una armadura de Dragon Ball de Zenkai (Fase 5b de la 0.7.0, arte definitivo 2026-11-25): modelo GeckoLib propio
 * por set ({@code geo/armor/<set>.geo.json} + {@code textures/armor/<set>.png}, ambos de {@code tools/gen_armaduras.py},
 * skill {@code armor-from-reference}). Un geo por set con los huesos de armadura de GeckoLib (armorHead, armorBody,
 * armorRightArm…); GeoArmorRenderer muestra solo los de la ranura de la pieza, así un mismo geo sirve a las tres o cuatro.
 *
 * Clase PROPIA y no {@code GeoLayerArmorItem} a propósito: media docena de sitios (mano en 1ª persona, colores del cuerpo,
 * selector de raza…) tratan cualquier {@code GeoLayerArmorItem} como pieza de raza o cuerpo virtual.
 */
public class ZenkaiArmorItem extends ArmorItem implements GeoItem {

    private final ResourceLocation set;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ZenkaiArmorItem(ResourceLocation set, Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        this.set = set;
    }

    /** Set al que pertenece la pieza (el mismo id que el patrón {@code zenkai:armor_pattern}). */
    public ResourceLocation set() { return set; }

    public ResourceLocation modelPath() {
        return ResourceLocation.fromNamespaceAndPath(set.getNamespace(), "geo/armor/" + set.getPath() + ".geo.json");
    }

    public ResourceLocation texturePath() {
        return ResourceLocation.fromNamespaceAndPath(set.getNamespace(), "textures/armor/" + set.getPath() + ".png");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // sin animaciones: el renderer copia la pose del modelo vanilla en cada hueso (la capa de Piccolo, cuando llegue,
        // tendrá su propio controlador)
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private com.hmc.zenkai.client.render_and_model_entities.ZenkaiArmorRenderer renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                                                                                @Nullable EquipmentSlot equipmentSlot,
                                                                                @Nullable HumanoidModel<T> original) {
                if (this.renderer == null)
                    this.renderer = new com.hmc.zenkai.client.render_and_model_entities.ZenkaiArmorRenderer();
                return this.renderer;
            }
        });
    }
}
