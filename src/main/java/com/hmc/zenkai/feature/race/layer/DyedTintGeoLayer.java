package com.hmc.zenkai.feature.race.layer;

import com.hmc.zenkai.feature.race.RaceRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import static com.hmc.zenkai.feature.race.RaceTextureUtil.deriveMask;
import static com.hmc.zenkai.feature.race.RaceTextureUtil.resourceExists;

/**
 * Capa de TINTE POR TINTES VANILLA para GeoLayerArmorItems (p. ej. el scouter).
 * Mismo mecanismo que BodyTintGeoLayer (Namek), pero el color NO viene de la visual del jugador
 * sino del componente DYED_COLOR del ItemStack puesto (tintes de Minecraft: craftear con tinte /
 * lavar en caldero — requiere el item en el tag #minecraft:dyeable).
 * Convención de textura: <base>_tint.png (máscara en ESCALA DE GRISES en las zonas teñibles,
 * transparente en el resto), derivada de la textura base del item, igual que _detail/_lines del
 * Namek. Si la máscara no existe, la pasada se omite (la base se ve sin teñir).
 * Sin tinte aplicado, usa el color por defecto del item (item.getDyeTintDefault()).
 * ⚠ API GeckoLib 4.8.4 — verificar: getCurrentStack() del GeoArmorRenderer (portador del stack).
 */
public class DyedTintGeoLayer extends GeoRenderLayer<GeoLayerArmorItem> {

    public DyedTintGeoLayer(GeoRenderer<GeoLayerArmorItem> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, GeoLayerArmorItem animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {

        if (!animatable.hasDyeTint()) return;

        ItemStack stack = ((GeoLayerArmorRenderer) this.getRenderer()).getCurrentStack();

        // Preset 0: tinte por tintes vanilla y presets de raza no se combinan en ningún item
        // de hoy. Si algún día lo hacen, aquí hay que sacar el preset del portador.
        ResourceLocation tint = deriveMask(animatable.resolveTexture(stack, 0), "_tint");
        if (!resourceExists(tint)) return;
        int rgb = (stack != null && stack.has(DataComponents.DYED_COLOR))
                ? DyedItemColor.getOrDefault(stack, animatable.getDyeTintDefault())
                : animatable.getDyeTintDefault();
        int alpha = animatable.getDyeTintAlpha();
        int argb = (alpha << 24) | (rgb & 0xFFFFFF);

        // Translúcido solo si de verdad hace falta: el cutout es más barato y es lo correcto
        // para lo que no sea cristal.
        RenderType rt = alpha >= 255
                ? RaceRenderTypes.viewOffset(tint)
                : RaceRenderTypes.translucent(tint);

        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, rt,
                bufferSource.getBuffer(rt), partialTick, packedLight, packedOverlay, argb);
    }
}