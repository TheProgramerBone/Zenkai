package com.hmc.db_renewed.block.entity.client;

import com.hmc.db_renewed.block.entity.AllDragonBallsEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;

public class AllDragonBallsRenderer extends GeoBlockRenderer<AllDragonBallsEntity> {
    public AllDragonBallsRenderer(Context context) {
        super(new AllDragonBallsModel());
    }
}
