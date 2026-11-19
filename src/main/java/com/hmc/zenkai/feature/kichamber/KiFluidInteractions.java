package com.hmc.zenkai.feature.kichamber;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.hfil.HfilWaterInteractions;
import com.hmc.zenkai.registry.ModFluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry.InteractionInformation;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Qué pasa cuando un fluido de Ki derramado toca agua, lava, otro ki o los fluidos de Namek y del HFIL.
 * La tabla completa y su justificación: {@code .claude/docs/interacciones-de-fluidos.md}.
 *
 * REGLA DE DISEÑO (2026-11-17, tras la crítica habitual de «contenido que le quita la exclusividad a
 * los biomas y dimensiones»): el resultado NUNCA es un bloque exclusivo de un bioma o dimensión, ni
 * un bloque propio del HFIL o de Namek, ni un bloque teñido (el ki no sustituye al tinte).
 *
 *   ki + agua           → cristal (los cuatro)
 *   ki + lava           → Base grava · Súper andesita · Divino diorita · Legendario granito
 *   ki + agua curativa  → arcilla
 *   ki + agua hirviendo → el ki se EVAPORA (sin bloque)
 *   ki + otro ki        → toba («descarga»: ambos se neutralizan; el que se actualiza queda sólido)
 *   ki + el mismo ki    → sin reacción
 *
 * El BLOQUE QUE SE VUELVE SÓLIDO es siempre el ki (es el escaso y no se regenera: un cubo = un bloque);
 * el agua o la lava siguen donde estaban. FluidInteractionRegistry convierte el bloque del fluido QUE SE
 * ACTUALIZA, así que basta registrar la pareja desde el lado del ki: si es el otro fluido el que llega,
 * el ki vecino recibe el aviso y reacciona él.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)   // FMLCommonSetupEvent es de bus de mod: NeoForge 21.1 lo deduce solo
public final class KiFluidInteractions {
    private KiFluidInteractions() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(KiFluidInteractions::register);
    }

    private static void register() {
        FluidType boiling = ModFluids.HFIL_BOILING_WATER_TYPE.get();
        FluidType healing = ModFluids.HEALING_WATER_TYPE.get();
        for (KiEnergyType ki : KiEnergyType.values()) {
            FluidType self = ki.fluid().type().get();
            FluidInteractionRegistry.addInteraction(self,
                    new InteractionInformation(NeoForgeMod.WATER_TYPE.value(), withWater(ki)));
            FluidInteractionRegistry.addInteraction(self,
                    new InteractionInformation(NeoForgeMod.LAVA_TYPE.value(), withLava(ki)));
            FluidInteractionRegistry.addInteraction(self,
                    new InteractionInformation(healing, withHealing(ki)));
            // Agua hirviendo: el ki que se actualiza se evapora (sin bloque).
            FluidInteractionRegistry.addInteraction(self, new InteractionInformation(
                    (level, cur, rel, state) -> level.getFluidState(rel).getFluidType() == boiling,
                    (level, cur, rel, state) -> HfilWaterInteractions.evaporate(level, cur)));
            for (KiEnergyType other : KiEnergyType.values()) {
                if (other == ki) continue;
                FluidInteractionRegistry.addInteraction(self,
                        new InteractionInformation(other.fluid().type().get(), mixed(ki, other)));
            }
        }
    }

    public static BlockState withWater(KiEnergyType ki) {
        return Blocks.GLASS.defaultBlockState();
    }

    public static BlockState withLava(KiEnergyType ki) {
        return switch (ki) {
            case BASE -> Blocks.GRAVEL.defaultBlockState();
            case SUPER -> Blocks.ANDESITE.defaultBlockState();
            case DIVINE -> Blocks.DIORITE.defaultBlockState();
            case LEGENDARY -> Blocks.GRANITE.defaultBlockState();
        };
    }

    public static BlockState withHealing(KiEnergyType ki) {
        return Blocks.CLAY.defaultBlockState();
    }

    /** Descarga entre dos ki distintos: toba, sin color ni tipo. */
    public static BlockState mixed(KiEnergyType a, KiEnergyType b) {
        return Blocks.TUFF.defaultBlockState();
    }
}
