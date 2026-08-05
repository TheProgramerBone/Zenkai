package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.sense.ScouterUpgrades;
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

    /** Peso configurado de una pesa de entrenamiento, en toneladas. Vive en el stack para
     *  que cada pesa se ajuste por separado y el valor sobreviva a morir/guardar. */
    public static final Supplier<DataComponentType<Double>> WEIGHT_TONS =
            COMPONENTS.register("weight_tons", () -> DataComponentType.<Double>builder()
                    .persistent(Codec.DOUBLE)
                    .networkSynchronized(ByteBufCodecs.DOUBLE)
                    .build());

    /** Mejoras del scouter. Ausente = ScouterUpgrades.NONE: los scouters de mundos anteriores
     *  no se rompen al cargar, simplemente arrancan sin mejoras. */
    public static final Supplier<DataComponentType<ScouterUpgrades>> SCOUTER_UPGRADES =
            COMPONENTS.register("scouter_upgrades", () -> DataComponentType.<ScouterUpgrades>builder()
                    .persistent(ScouterUpgrades.CODEC)
                    .networkSynchronized(ScouterUpgrades.STREAM_CODEC)
                    .build());

    /** Scouter reventado. El stack NO se destruye: conserva nivel y tinte para la reparación. */
    public static final Supplier<DataComponentType<Boolean>> SCOUTER_BROKEN =
            COMPONENTS.register("scouter_broken", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}