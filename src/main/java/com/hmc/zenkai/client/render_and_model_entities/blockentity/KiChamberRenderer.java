package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Renderer de la cámara de contención. El block entity vive en la mitad inferior pero el modelo
 * mide DOS bloques: sin ampliar la caja de render, mirar solo la mitad de arriba (con la de abajo
 * fuera de pantalla) haría desaparecer la cámara entera. Medio bloque de margen en horizontal:
 * las hojas de la puerta, abiertas, sobresalen por delante.
 */
public class KiChamberRenderer extends GeoBlockRenderer<KiContainmentChamberBlockEntity> {

    public KiChamberRenderer(Context context) {
        super(new KiChamberModel());
        addRenderLayer(new KiChamberGlowLayer(this));
        addRenderLayer(new KiChamberGlassLayer(this));   // el cristal, siempre el último
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull KiContainmentChamberBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 0.5, p.getY(), p.getZ() - 0.5, p.getX() + 1.5, p.getY() + 2.0, p.getZ() + 1.5);
    }
}
