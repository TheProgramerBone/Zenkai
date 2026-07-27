package com.hmc.zenkai.client;

import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.content.item.special.ScouterItem;
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

/**
 * Dibuja el scouter cuando está en el slot de Curios. Mismo truco que HaloGeoLayer: el
 * pipeline de GeckoLib solo se activa desde HumanoidArmorLayer, que lee getItemBySlot(HEAD),
 * así que se inyecta el stack ahí un instante y se restaura.
 * Se pasa el STACK REAL de Curios, no un getDefaultInstance(): el tinte vive en el
 * DYED_COLOR del stack y con una instancia nueva el scouter saldría siempre verde.
 */
public class ScouterGeoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private final HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>,
            HumanoidModel<AbstractClientPlayer>> armorLayer;

    public ScouterGeoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                           EntityModelSet models, ModelManager modelManager) {
        super(parent);
        HumanoidModel<AbstractClientPlayer> inner =
                new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        HumanoidModel<AbstractClientPlayer> outer =
                new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        this.armorLayer = new HumanoidArmorLayer<>(parent, inner, outer, modelManager);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight,
                       @NotNull AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {

        if (player.isInvisible()) return;
        // Si ya lo lleva como casco, la capa de armadura normal ya lo pinta: no duplicar.
        if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ScouterItem) return;

        ItemStack scouter = CuriosCompat.findEquipped(player, "scouter");
        if (!(scouter.getItem() instanceof ScouterItem)) return;

        var inv = player.getInventory();
        ItemStack oldHead  = inv.getArmor(3);
        ItemStack oldChest = inv.getArmor(2);
        ItemStack oldLegs  = inv.getArmor(1);
        ItemStack oldFeet  = inv.getArmor(0);

        inv.armor.set(3, scouter);
        inv.armor.set(2, ItemStack.EMPTY);
        inv.armor.set(1, ItemStack.EMPTY);
        inv.armor.set(0, ItemStack.EMPTY);

        armorLayer.render(poseStack, buffer, packedLight, player,
                limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);

        inv.armor.set(3, oldHead);
        inv.armor.set(2, oldChest);
        inv.armor.set(1, oldLegs);
        inv.armor.set(0, oldFeet);
    }
}