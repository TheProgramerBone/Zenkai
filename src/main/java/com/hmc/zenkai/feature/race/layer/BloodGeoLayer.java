package com.hmc.zenkai.feature.race.layer;

import com.hmc.zenkai.client.face.BloodTextures;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.race.RaceRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Sangre sobre el cuerpo según la vida que queda (2026-11-01): a 70 %, 45 % y 20 % aparecen más manchas (ver
 * {@link BloodTextures}). Una pasada más sobre el mismo modelo con una textura generada a partir de la del cuerpo, igual
 * que las capas de tinte; corre en cada pieza del cuerpo (cabeza, pecho, piernas y botas), y cada una pinta solo sus
 * huesos. Sangre roja en Humano, Saiyan y Majin; morada en Namekiano y Arcosiano.
 *
 * Se va PRIMERO que la cara en la lista de capas: los ojos y la boca quedan por encima de la sangre. Se apaga con la
 * opción de cliente {@code face.blood_effects} (no afecta a lo que ven los demás: cada cliente decide el suyo).
 */
public class BloodGeoLayer extends GeoRenderLayer<GeoLayerArmorItem> {

    public BloodGeoLayer(GeoRenderer<GeoLayerArmorItem> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, GeoLayerArmorItem animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        if (!ClientConfig.bloodEffects()) return;
        Entity wearer = ((GeoLayerArmorRenderer) this.getRenderer()).getCurrentEntity();
        if (!(wearer instanceof Player player)) return;
        float max = player.getMaxHealth();
        if (max <= 0f) return;
        int stage = BloodTextures.stageFor(player.getHealth() / max);
        if (stage == 0) return;

        Race race = PlayerStatsAttachment.get(player).getRace();
        boolean purple = race == Race.NAMEKIAN || race == Race.ARCOSIAN;
        ResourceLocation tex = BloodTextures.get(animatable.getTexturePath(), stage, purple);
        if (tex == null) return;

        RenderType rt = RaceRenderTypes.viewOffset(tex);
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, rt,
                bufferSource.getBuffer(rt), partialTick, packedLight, packedOverlay, 0xFFFFFFFF);
    }
}
