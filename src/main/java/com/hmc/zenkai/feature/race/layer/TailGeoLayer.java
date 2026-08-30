package com.hmc.zenkai.feature.race.layer;

import com.hmc.zenkai.feature.race.TailResolver;
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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Cola de Saiyan: mismo truco exacto que HairGeoLayer (backup-inyecta-render-restaura sobre
 * un HumanoidArmorLayer propio), solo que la ranura vehículo es LEGGINGS (índice 1) en vez de
 * HEAD — la geometría real la pone el .geo.json de TAIL_LOOSE/TAIL_WAIST, la ranura vanilla
 * es pura excusa para que HumanoidArmorLayer invoque al renderer de GeckoLib (ver
 * ModItems.TAIL_LOOSE). A diferencia del pelo, no hay candado de "ropa real tapa la cola":
 * unas piernas reales equipadas no ocultan la cola (ver TailResolver si hiciera falta añadirlo).
 */
public class TailGeoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private final HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>,
            HumanoidModel<AbstractClientPlayer>> armorLayer;

    public TailGeoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
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
                       @NotNull AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack tail = TailResolver.resolveTail(player);
        if (tail.isEmpty()) return;

        var inv = player.getInventory();

        // Backup
        ItemStack oldHead  = inv.getArmor(3);
        ItemStack oldChest = inv.getArmor(2);
        ItemStack oldLegs  = inv.getArmor(1);
        ItemStack oldFeet  = inv.getArmor(0);

        // Inyectar solo la cola en LEGS, limpiar el resto
        inv.armor.set(3, ItemStack.EMPTY);
        inv.armor.set(2, ItemStack.EMPTY);
        inv.armor.set(1, tail);
        inv.armor.set(0, ItemStack.EMPTY);

        armorLayer.render(poseStack, buffer, packedLight, player,
                limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);

        // Restore
        inv.armor.set(3, oldHead);
        inv.armor.set(2, oldChest);
        inv.armor.set(1, oldLegs);
        inv.armor.set(0, oldFeet);
    }
}
