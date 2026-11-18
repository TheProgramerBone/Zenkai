package com.hmc.zenkai.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * ÚNICO punto de entrada desde fuera de {@code compat/create/}. Los registros del mod (bloque, tipo de
 * block entity) existen SIEMPRE —el registro no admite condiciones y debe coincidir entre cliente y
 * servidor—, pero lo que extiende clases de Create solo se construye con Create cargado.
 *
 * Esta clase NO puede nombrar {@link KiMotorBlock} ni {@link KiMotorBlockEntity}: el verificador de la
 * JVM carga los tipos que se asignan a otro (un {@code return new KiMotorBlock(..)} como {@code Block})
 * y eso cargaría la superclase de Create y daría NoClassDefFoundError sin Create. Todo eso vive en
 * {@link KiMotorLoader}, que solo se toca tras {@link #loaded()}.
 */
public final class KiMotorFactory {
    private KiMotorFactory() {}

    public static boolean loaded() {
        return ModList.get().isLoaded("create");
    }

    /** Bloque real con Create; sin él, un bloque inerte que no sale en el creativo ni tiene receta. */
    public static Block block(Block.Properties properties) {
        return loaded() ? KiMotorLoader.block(properties) : new KiMotorInertBlock(properties);
    }

    public static BlockEntity blockEntity(BlockPos pos, BlockState state) {
        return KiMotorLoader.blockEntity(pos, state);
    }

    /** Capabilities de FE y fluido por cara; solo se llama con Create cargado. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        KiMotorLoader.registerCapabilities(event);
    }
}
