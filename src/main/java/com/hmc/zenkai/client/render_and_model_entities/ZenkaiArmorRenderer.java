package com.hmc.zenkai.client.render_and_model_entities;

import com.hmc.zenkai.content.item.ZenkaiArmorItem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * Renderer de las armaduras de Zenkai ({@link ZenkaiArmorItem}): el de GeckoLib tal cual, que ya copia la pose del modelo
 * vanilla a los huesos armor* y deja visibles solo los de la ranura. Recorte (cutout) en vez del sólido por defecto: la
 * textura deja transparente la piel que la hoja de referencia muestra (antebrazos de Piccolo, cuello…), y sin recorte esos
 * texeles saldrían negros.
 */
public class ZenkaiArmorRenderer extends GeoArmorRenderer<ZenkaiArmorItem> {

    /**
     * Hueso hijo de {@code armorBody} con la CINTURA del pantalón (de la cadera al ombligo). GeckoLib solo enseña los huesos
     * de las piernas en la ranura LEGS, así que sin esto el pantalón acababa en y = 12 y con solo el pantalón puesto
     * asomaba la ropa interior del cuerpo (usuario 2026-11-25, con SSJ4 y el pantalón de GT: «los pantalones cubren la ropa
     * interior y dejan el pecho libre; para eso es la pechera»). Al colgar del torso sigue su pose y no se parte entre
     * las dos piernas al andar.
     */
    static final String WAIST = "armorWaist";

    public ZenkaiArmorRenderer() {
        super(new Model());
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
        super.applyBoneVisibilityBySlot(currentSlot);
        GeoBone waist = getGeoModel().getBone(WAIST).orElse(null);
        if (waist == null || this.body == null) return;
        if (currentSlot == EquipmentSlot.LEGS && this.body.isHidden() && this.body.getChildBones().contains(waist)) {
            // el torso sigue oculto (sus cubos son de la pechera) pero deja pasar a su hijo cintura y a nadie más
            this.body.setChildrenHidden(false);
            for (GeoBone child : this.body.getChildBones()) child.setHidden(child != waist);
        } else {
            // con la pechera (o cualquier otra ranura) la cintura NO se dibuja: es del pantalón. Los demás hijos del
            // torso recuperan su visibilidad (el estado de los huesos dura de un render al siguiente)
            for (GeoBone child : this.body.getChildBones()) child.setHidden(child == waist);
        }
    }

    static final class Model extends GeoModel<ZenkaiArmorItem> {
        @Override
        public ResourceLocation getModelResource(ZenkaiArmorItem item) { return item.modelPath(); }

        @Override
        public ResourceLocation getTextureResource(ZenkaiArmorItem item) { return item.texturePath(); }

        @Override
        public ResourceLocation getAnimationResource(ZenkaiArmorItem item) {
            // nunca se carga: el ítem no registra controladores
            return ResourceLocation.fromNamespaceAndPath(item.set().getNamespace(),
                    "animations/armor/" + item.set().getPath() + ".animation.json");
        }

        @Override
        public @Nullable RenderType getRenderType(ZenkaiArmorItem item, ResourceLocation texture) {
            return RenderType.armorCutoutNoCull(texture);
        }
    }
}
