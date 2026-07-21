package com.hmc.zenkai.content.item;

import com.hmc.zenkai.Zenkai;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Data components del mod.
 * RADAR_UPGRADE: mejora del scouter para buscar esferas del dragón. Se aplica en mesa de
 * herrería (plantilla + scouter + radar del dragón, ver recipe scouter_radar_upgrade.json)
 * y vive en el ItemStack -> sobrevive a morir/guardar/comerciar, y cada scouter se mejora
 * individualmente.
 */
public final class ModDataComponents {
    private ModDataComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Zenkai.MOD_ID);

    public static final Supplier<DataComponentType<Boolean>> RADAR_UPGRADE =
            COMPONENTS.register("radar_upgrade", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    public static final Supplier<DataComponentType<GlobalPos>> RADAR_TARGET =
            COMPONENTS.register("radar_target", () -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC)
                    .build());

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}