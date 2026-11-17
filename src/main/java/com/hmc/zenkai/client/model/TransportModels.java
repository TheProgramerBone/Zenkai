package com.hmc.zenkai.client.model;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.TransportBlock;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.CompositeModel;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

/** Sustituye el modelo compuesto horneado de cada bloque de transporte por su envoltorio dinámico. */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class TransportModels {
    private TransportModels() {}

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        List<DeferredBlock<Block>> blocks = List.of(
                ModBlocks.ENERGY_CABLE, ModBlocks.FLUID_PIPE, ModBlocks.ENERGY_TANK, ModBlocks.FLUID_TANK);
        for (DeferredBlock<Block> b : blocks) {
            // Un solo blockstate: su clave de modelo es la del estado por defecto.
            ModelResourceLocation key = BlockModelShaper.stateToModelLocation(b.get().defaultBlockState());
            BakedModel baked = event.getModels().get(key);
            if (baked instanceof CompositeModel.Baked composite) {
                event.getModels().put(key, new TransportBakedModel(composite, ((TransportBlock) b.get()).isCable()));
            }
        }
    }
}
