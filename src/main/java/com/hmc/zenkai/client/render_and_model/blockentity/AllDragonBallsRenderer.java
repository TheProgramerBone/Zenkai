package com.hmc.zenkai.client.render_and_model.blockentity;

import com.hmc.zenkai.content.blockentity.AllDragonBalls.AllDragonBallsEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;

public class AllDragonBallsRenderer extends GeoBlockRenderer<AllDragonBallsEntity> {
    public AllDragonBallsRenderer(Context context) {
        super(new AllDragonBallsModel());
    }
}
