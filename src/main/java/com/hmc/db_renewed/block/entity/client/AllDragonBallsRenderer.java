package com.hmc.db_renewed.block.entity.client;

import com.hmc.db_renewed.block.entity.AllDragonBallsEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class AllDragonBallsRenderer extends GeoBlockRenderer<AllDragonBallsEntity> {
    public AllDragonBallsRenderer(BlockEntityRendererProvider.Context  context) {
        super(new AllDragonBallsModel());
    }
}
