package com.hmc.db_renewed.client.render_and_model.blockentity;

import com.hmc.db_renewed.content.blockentity.AllDragonBalls.AllDragonBallsEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;

public class AllDragonBallsRenderer extends GeoBlockRenderer<AllDragonBallsEntity> {
    public AllDragonBallsRenderer(Context context) {
        super(new AllDragonBallsModel());
    }
}
