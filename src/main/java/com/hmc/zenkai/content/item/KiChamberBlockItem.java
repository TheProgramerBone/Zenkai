package com.hmc.zenkai.content.item;

import com.hmc.zenkai.client.render_and_model_entities.item.KiChamberItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Ítem de la cámara de contención de ki. Existe por lo mismo que ScouterBenchBlockItem: el
 * bloque se dibuja con un GeoBlockRenderer y su blockstate apunta a un modelo sin caras, así que
 * un BlockItem normal no tendría nada que enseñar en inventario, mano o marco.
 * Sin controladores a propósito: la animación de carga es del bloque colocado.
 */
public class KiChamberBlockItem extends BlockItem implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public KiChamberBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Vacío a propósito: ver comentario de clase.
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private KiChamberItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (this.renderer == null) this.renderer = new KiChamberItemRenderer();
                return this.renderer;
            }
        });
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
