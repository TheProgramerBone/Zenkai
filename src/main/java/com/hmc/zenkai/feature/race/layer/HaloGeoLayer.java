package com.hmc.zenkai.feature.race.layer;

import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
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
 * Aureola del otro mundo: se renderiza encima de la cabeza cuando el jugador
 * tiene el flag inOtherworld (sincronizado). Mismo truco que HairGeoLayer:
 * inyecta el item HALO en el slot HEAD, lo renderiza vía HumanoidArmorLayer
 * (que delega en el GeoArmorRenderer) y restaura los slots.
 *
 * El halo flota sobre la cabeza según su geo, así que convive con casco/pelo.
 */
public class HaloGeoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Píxeles de modelo que sube y se adelanta el halo sobre la cabeza del Oozaru (ver render). */
    private static final double OOZARU_HALO_RAISE = 4.0, OOZARU_HALO_FORWARD = 2.75;

    private final HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>,
            HumanoidModel<AbstractClientPlayer>> armorLayer;

    public HaloGeoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
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
        if (com.hmc.zenkai.feature.race.ZenkaiFirstPersonBody.hideHeadAttachments(player)) return;
        // Solo si está en el otro mundo (flag sincronizado vía PlayerStatsAttachment).
        if (!player.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).isInOtherworld()) return;
        if (player.isInvisible() && !GeoLayerArmorRenderer.isGhostPass()) return;

        ItemStack halo = ModItems.HALO.get().getDefaultInstance();

        var inv = player.getInventory();
        ItemStack oldHead  = inv.getArmor(3);
        ItemStack oldChest = inv.getArmor(2);
        ItemStack oldLegs  = inv.getArmor(1);
        ItemStack oldFeet  = inv.getArmor(0);

        // Solo el halo en HEAD; el resto vacío para no re-renderizar otra armadura aquí.
        inv.armor.set(3, halo);
        inv.armor.set(2, ItemStack.EMPTY);
        inv.armor.set(1, ItemStack.EMPTY);
        inv.armor.set(0, ItemStack.EMPTY);

        // El halo NO lleva el humo de silueta: se saca de la máscara (ver SilhouetteFlameRenderer.suspendCapture).
        com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.suspendCapture();
        // La cabeza del Oozaru (geo propio) sube a y=36 y está centrada en z=-2,75, mientras la humanoide vanilla
        // acaba en y=32 y se centra en 0: el aro del halo (y=35, centrado en 0) quedaba CLAVADO en el cráneo. Se
        // sube 4 px (36 - 32: el aro queda a los mismos 3 px de siempre sobre la cabeza) y se adelanta a su centro. El espacio
        // de las capas va con Y invertida (de ahí el signo) y el frente en -Z. La escala x3 la pone ya la pose.
        var form = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();
        boolean oozaru = com.hmc.zenkai.feature.forms.FormIds.OOZARU.equals(form)
                || com.hmc.zenkai.feature.forms.FormIds.SUPER_OOZARU.equals(form);
        poseStack.pushPose();
        if (oozaru) poseStack.translate(0.0, -OOZARU_HALO_RAISE / 16.0, -OOZARU_HALO_FORWARD / 16.0);
        armorLayer.render(poseStack, buffer, packedLight, player,
                limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        poseStack.popPose();

        com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.resumeCapture();
        inv.armor.set(3, oldHead);
        inv.armor.set(2, oldChest);
        inv.armor.set(1, oldLegs);
        inv.armor.set(0, oldFeet);
    }
}