package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.content.entity.otherworld.AngelEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

/**
 * Pinta la ARMADURA del ángel igual que la de cualquier otra entidad con casco: el ítem de la
 * ranura de casco (el halo del mod, {@code ModItems.HALO}, que AngelEntity se pone solo) se dibuja
 * encima del hueso `head` con la capa de armadura que ya trae GeckoLib. Así el aro hereda sin
 * código propio todo lo que le pase a la cabeza (cabeceo, alabeo, balanceo del cuerpo), exactamente
 * como un casco de diamante sobre un zombi. Sustituye a la antigua AngelHaloLayer, que redibujaba el
 * .geo.json del halo a mano y se quedaba clavado al origen del modelo.
 */
public class AngelArmorLayer extends ItemArmorGeoLayer<AngelEntity> {

    public AngelArmorLayer(GeoRenderer<AngelEntity> renderer) {
        super(renderer);
    }

    /** Solo el hueso de la cabeza lleva armadura (el ángel solo se equipa un casco). */
    @Override
    protected @Nullable ItemStack getArmorItemForBone(GeoBone bone, AngelEntity animatable) {
        return "head".equals(bone.getName()) ? this.helmetStack : null;
    }

    /**
     * GeckoLib 4.8.4 aplica la rotación del hueso DOS veces a la pieza de armadura: renderForBone
     * se llama dentro de RenderUtil.prepMatrixForBone (el PoseStack ya lleva la rotación del hueso
     * `head` alrededor de su pivote) y, además, prepModelPartForRender copia esa misma rotación al
     * ModelPart, que GeoArmorRenderer.applyBaseTransformations pasa al hueso `bipedHead` del halo.
     * Los dos giran alrededor del mismo pivote (0, 24, 0) -- los dos F = scale(-1,-1,1) y el
     * translate(0, 24/16, 0) de GeoArmorRenderer.actuallyRender se anulan --, así que el aro giraba
     * el DOBLE que la cabeza: con la animación `angel.play` (cabeceo -6 a -10 grados, alabeo 0 a 3)
     * quedaba inclinado 12 a 20 grados hacia delante. Se deja la posición y la escala y se anula la
     * rotación del ModelPart: el aro hereda el giro solo del PoseStack, igual que la cabeza.
     */
    @Override
    protected void prepModelPartForRender(PoseStack poseStack, GeoBone bone, ModelPart sourcePart) {
        super.prepModelPartForRender(poseStack, bone, sourcePart);
        sourcePart.xRot = 0f;
        sourcePart.yRot = 0f;
        sourcePart.zRot = 0f;
    }

    @Override
    protected @NotNull ModelPart getModelPartForBone(GeoBone bone, EquipmentSlot slot, ItemStack stack,
                                                     AngelEntity animatable, HumanoidModel<?> baseModel) {
        return baseModel.head;
    }
}
