package com.hmc.zenkai.feature.kichamber;

import com.hmc.zenkai.Zenkai;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Qué pasa cuando un fluido de Ki derramado toca agua, lava u otro ki (0.7.0). La tabla la eligió
 * el usuario (2026-11-17) de una propuesta: bloques RAROS o de difícil acceso, nunca minerales, para
 * que el ki dé un uso decorativo sin volverse una granja de recursos.
 *
 *   ki           + agua               + lava
 *   Base (azul)  hielo azul           bloque de amatista
 *   Súper (oro)  piedra luminosa      piedra negra dorada
 *   Divino       luz de hongo         obsidiana llorona
 *   Legendario   linterna marina      bloque de sculk
 *
 * Dos ki distintos que se tocan se neutralizan («chispazo»: el fizz de lava y agua) y dejan:
 * Base + Súper → bloque de cuarzo; cualquiera + Divino → bloque de purpur; cualquiera + Legendario
 * → prismarina oscura. Divino + Legendario manda el Legendario (prismarina oscura).
 *
 * FluidInteractionRegistry convierte el bloque del fluido QUE SE ACTUALIZA (al colocarse o cuando
 * un vecino cambia), así que basta registrar la pareja desde el lado del ki: si es el agua la que
 * llega, el ki vecino recibe el aviso y se solidifica él.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)   // FMLCommonSetupEvent es de bus de mod: NeoForge 21.1 lo deduce solo
public final class KiFluidInteractions {
    private KiFluidInteractions() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(KiFluidInteractions::register);
    }

    private static void register() {
        for (KiEnergyType ki : KiEnergyType.values()) {
            FluidType self = ki.fluid().type().get();
            FluidInteractionRegistry.addInteraction(self,
                    new FluidInteractionRegistry.InteractionInformation(NeoForgeMod.WATER_TYPE.value(), withWater(ki)));
            FluidInteractionRegistry.addInteraction(self,
                    new FluidInteractionRegistry.InteractionInformation(NeoForgeMod.LAVA_TYPE.value(), withLava(ki)));
            for (KiEnergyType other : KiEnergyType.values()) {
                if (other == ki) continue;
                FluidInteractionRegistry.addInteraction(self,
                        new FluidInteractionRegistry.InteractionInformation(other.fluid().type().get(), mixed(ki, other)));
            }
        }
    }

    public static BlockState withWater(KiEnergyType ki) {
        return switch (ki) {
            case BASE -> Blocks.BLUE_ICE.defaultBlockState();
            case SUPER -> Blocks.GLOWSTONE.defaultBlockState();
            case DIVINE -> Blocks.SHROOMLIGHT.defaultBlockState();
            case LEGENDARY -> Blocks.SEA_LANTERN.defaultBlockState();
        };
    }

    public static BlockState withLava(KiEnergyType ki) {
        return switch (ki) {
            case BASE -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case SUPER -> Blocks.GILDED_BLACKSTONE.defaultBlockState();
            case DIVINE -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
            case LEGENDARY -> Blocks.SCULK.defaultBlockState();
        };
    }

    /** Simétrica: el resultado no depende de cuál de los dos se actualiza. */
    public static BlockState mixed(KiEnergyType a, KiEnergyType b) {
        if (a == KiEnergyType.LEGENDARY || b == KiEnergyType.LEGENDARY) return Blocks.DARK_PRISMARINE.defaultBlockState();
        if (a == KiEnergyType.DIVINE || b == KiEnergyType.DIVINE) return Blocks.PURPUR_BLOCK.defaultBlockState();
        return Blocks.QUARTZ_BLOCK.defaultBlockState();   // Base + Súper
    }
}
