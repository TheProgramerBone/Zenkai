package com.hmc.zenkai.content.item;

import com.hmc.zenkai.client.render_and_model_entities.item.ScouterBenchItemRenderer;
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
 * Ítem del banco de scouter. Existe SOLO para poder enseñar el modelo 3D real: el bloque se
 * dibuja con un GeoBlockRenderer y su blockstate apunta a un modelo sin caras, así que un
 * BlockItem normal no tiene nada que renderizar — hasta ahora el ítem salía como textura
 * faltante en inventario, mano y marco.
 *
 * SIN CONTROLADORES A PROPÓSITO. La animación "working" pertenece al bloque colocado, que es
 * el único que tiene un blockstate del que leer WORKING. Un ítem en la mano no está
 * trabajando, y un ítem que parpadease en el inventario sería ruido. Registrar el controlador
 * es obligatorio porque GeoItem lo exige, pero no añade ninguno.
 */
public class ScouterBenchBlockItem extends BlockItem implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ScouterBenchBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Vacío a propósito: ver comentario de clase.
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private ScouterBenchItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (this.renderer == null) this.renderer = new ScouterBenchItemRenderer();
                return this.renderer;
            }
        });
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}