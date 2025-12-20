package com.hmc.db_renewed.entity.race;

import com.hmc.db_renewed.network.stats.PlayerStatsAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RaceSkinGeoArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> LOGGED = ConcurrentHashMap.newKeySet();

    private final HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>, HumanoidModel<AbstractClientPlayer>> armorLayer;

    public RaceSkinGeoArmorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
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
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {

        if (LOGGED.add(player.getUUID())) {
            LOGGER.info("[DBR] RaceSkinGeoArmorLayer activo para {} race={}",
                    player.getGameProfile().getName(),
                    PlayerStatsAttachment.get(player).getRace()
            );
        }

        // 4 stacks reales (para restaurar)
        var inv = player.getInventory();

        ItemStack oldHead  = inv.getArmor(3);
        ItemStack oldChest = inv.getArmor(2);
        ItemStack oldLegs  = inv.getArmor(1);
        ItemStack oldFeet  = inv.getArmor(0);

        // Inyectar virtual race armor (SIEMPRE, aunque tenga armadura real)
        inv.armor.set(3, RaceSkinSlots.getVirtualRaceArmor(player, EquipmentSlot.HEAD));
        inv.armor.set(2, RaceSkinSlots.getVirtualRaceArmor(player, EquipmentSlot.CHEST));
        inv.armor.set(1, RaceSkinSlots.getVirtualRaceArmor(player, EquipmentSlot.LEGS));
        inv.armor.set(0, RaceSkinSlots.getVirtualRaceArmor(player, EquipmentSlot.FEET));

        // Renderizar “como armadura” (aquí GeckoLib entra porque tu item implementa GeoItem)
        armorLayer.render(poseStack, buffer, packedLight, player,
                limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);

        // Restaurar stacks reales
        inv.armor.set(3, oldHead);
        inv.armor.set(2, oldChest);
        inv.armor.set(1, oldLegs);
        inv.armor.set(0, oldFeet);
    }
}