package com.hmc.zenkai.client.render_and_model_entities;

import com.hmc.zenkai.content.item.ZenkaiArmorItem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * Renderer de las armaduras de Zenkai ({@link ZenkaiArmorItem}): el de GeckoLib tal cual, que ya copia la pose del modelo
 * vanilla a los huesos armor* y deja visibles solo los de la ranura. Recorte (cutout) en vez del sólido por defecto: la
 * textura deja transparente la piel que la hoja de referencia muestra (antebrazos de Piccolo, cuello…), y sin recorte esos
 * texeles saldrían negros.
 */
public class ZenkaiArmorRenderer extends GeoArmorRenderer<ZenkaiArmorItem> {

    public ZenkaiArmorRenderer() {
        super(new Model());
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
