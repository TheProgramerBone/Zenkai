package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.worldgen.PersistentLeavesProcessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Tipos de StructureProcessor del mod.
 *
 * El tipo solo se usa para SERIALIZAR el procesador a JSON, y los nuestros se aplican desde
 * código. Aun así hay que registrarlo: getType() es abstracto en StructureProcessor y devolver
 * null revienta en cuanto algo intente escribir la plantilla.
 */
public class ModStructureProcessors {

    public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Zenkai.MOD_ID);

    public static final Supplier<StructureProcessorType<PersistentLeavesProcessor>> PERSISTENT_LEAVES =
            PROCESSORS.register("persistent_leaves", () -> () -> PersistentLeavesProcessor.CODEC);

    public static void register(IEventBus bus) {
        PROCESSORS.register(bus);
    }
}