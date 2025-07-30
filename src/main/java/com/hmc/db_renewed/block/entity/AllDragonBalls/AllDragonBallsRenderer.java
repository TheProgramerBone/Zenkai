package com.hmc.db_renewed.block.entity.AllDragonBalls;

import software.bernie.geckolib.renderer.GeoBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;

public class AllDragonBallsRenderer extends GeoBlockRenderer<AllDragonBallsEntity> {
    public AllDragonBallsRenderer(Context context) {
        super(new AllDragonBallsModel());
    }
}
