package com.hmc.zenkai.client;

import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.content.item.WeightArmorItem;
import com.hmc.zenkai.feature.weights.WeightSystem;
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
 * Dibuja la pesa que va en el slot de Curios. La del PECHO no pasa por aquí: esa la pinta
 * la HumanoidArmorLayer vanilla, que corre después de RaceSkinGeoArmorLayer y por tanto ya
 * la ve. Mismo truco que ScouterGeoLayer: se inyecta el stack en el slot un instante y se
 * restaura. Al registrarse DESPUÉS de la capa de raza, queda por encima del peto.
 */
public class WeightGeoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private final HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>,
            HumanoidModel<AbstractClientPlayer>> armorLayer;

    public WeightGeoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
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

        ItemStack curios = CuriosCompat.findEquipped(player, WeightSystem.CURIOS_SLOT);
        if (!(curios.getItem() instanceof WeightArmorItem)) return;
        // Si la MISMA pesa ya va en el pecho, la capa vanilla la pinta: no duplicar.
        if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() == curios.getItem()) return;

        var inv = player.getInventory();
        ItemStack oldHead  = inv.getArmor(3);
        ItemStack oldChest = inv.getArmor(2);
        ItemStack oldLegs  = inv.getArmor(1);
        ItemStack oldFeet  = inv.getArmor(0);

        inv.armor.set(3, ItemStack.EMPTY);
        inv.armor.set(2, curios);
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