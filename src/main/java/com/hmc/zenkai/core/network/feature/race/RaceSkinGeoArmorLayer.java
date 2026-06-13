package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class RaceSkinGeoArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private final HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>,
            HumanoidModel<AbstractClientPlayer>> armorLayer;

    public RaceSkinGeoArmorLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            EntityModelSet models,
            ModelManager modelManager) {
        super(parent);
        HumanoidModel<AbstractClientPlayer> inner =
                new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        HumanoidModel<AbstractClientPlayer> outer =
                new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        this.armorLayer = new HumanoidArmorLayer<>(parent, inner, outer, modelManager);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {

        var stats  = player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = player.getData(DataAttachments.PLAYER_VISUAL.get());

        // Sin custom skin → no renderizar nada
        if (!visual.shouldRenderRaceSkin()) return;

        // Sin raza elegida → no renderizar nada
        if (!stats.isRaceChosen()) return;

        // Invisibilidad completa → no renderizar armadura racial
        if (player.isInvisible()) return;

        ItemStack head  = RaceBodyResolver.resolve(player, EquipmentSlot.HEAD);
        ItemStack chest = RaceBodyResolver.resolve(player, EquipmentSlot.CHEST);
        ItemStack legs  = RaceBodyResolver.resolve(player, EquipmentSlot.LEGS);
        ItemStack feet  = RaceBodyResolver.resolve(player, EquipmentSlot.FEET);

        if (head.isEmpty() && chest.isEmpty() && legs.isEmpty() && feet.isEmpty()) return;

        var inv = player.getInventory();

        ItemStack oldHead  = inv.getArmor(3);
        ItemStack oldChest = inv.getArmor(2);
        ItemStack oldLegs  = inv.getArmor(1);
        ItemStack oldFeet  = inv.getArmor(0);

        inv.armor.set(3, head);
        inv.armor.set(2, chest);
        inv.armor.set(1, legs);
        inv.armor.set(0, feet);

        armorLayer.render(poseStack, buffer, packedLight, player,
                limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);

        inv.armor.set(3, oldHead);
        inv.armor.set(2, oldChest);
        inv.armor.set(1, oldLegs);
        inv.armor.set(0, oldFeet);
    }
}