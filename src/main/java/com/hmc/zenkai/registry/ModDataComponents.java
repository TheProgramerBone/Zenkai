package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.sense.ScouterUpgrades;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

import java.util.function.Supplier;

/**
 * Data components del mod.
 * RADAR_UPGRADE: LEGACY, sin uso. Era la mejora del scouter para buscar esferas del dragón,
 * aplicada en mesa de herrería (plantilla + scouter + radar del dragón). Esa receta se
 * eliminó a propósito: el modo RADAR ahora depende SOLO de ScouterUpgrade.DRAGON_RADAR,
 * comprado en el banco de scouter (ver ScouterAreaScanPacket.handleRadar). El componente
 * sigue registrado únicamente para no romper stacks guardados de antes del cambio; ningún
 * código nuevo debe leerlo ni escribirlo.
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

    /** FE guardada en una batería (EnergyBatteryItem). Ausente = 0. Es el componente que lee
     *  la ComponentEnergyStorage de NeoForge, así que cualquier cargador de otro mod lo rellena. */
    public static final Supplier<DataComponentType<Integer>> STORED_ENERGY =
            COMPONENTS.register("stored_energy", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Fluido que lleva un tanque de transporte roto (TransportBlockItem). Ausente = vacío. */
    public static final Supplier<DataComponentType<SimpleFluidContent>> TRANSPORT_FLUID =
            COMPONENTS.register("transport_fluid", () -> DataComponentType.<SimpleFluidContent>builder()
                    .persistent(SimpleFluidContent.CODEC)
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC)
                    .build());

    /** Modos de las seis caras (ordinal de SideMode, orden de Direction.get3DDataValue) de un tanque
     *  de transporte roto. Ausente = todas en Normal. */
    public static final Supplier<DataComponentType<List<Integer>>> TRANSPORT_MODES =
            COMPONENTS.register("transport_modes", () -> DataComponentType.<List<Integer>>builder()
                    .persistent(Codec.INT.listOf())
                    .networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()))
                    .build());

    /** Scouter reventado. El stack NO se destruye: conserva nivel y tinte para la reparación. */
    public static final Supplier<DataComponentType<Boolean>> SCOUTER_BROKEN =
            COMPONENTS.register("scouter_broken", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /**
     * Peinado (id de HairCatalog) del item de pelo genérico zenkai:hair. Lo pone HairResolver en
     * un stack VIRTUAL que solo existe durante el render (nunca en un inventario real), pero se
     * registra persistente/sincronizable por si un stack así acabara serializado (creativo,
     * comandos): un componente sin codec rompería el guardado.
     */
    public static final Supplier<DataComponentType<net.minecraft.resources.ResourceLocation>> HAIR_STYLE =
            COMPONENTS.register("hair_style", () -> DataComponentType.<net.minecraft.resources.ResourceLocation>builder()
                    .persistent(net.minecraft.resources.ResourceLocation.CODEC)
                    .networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC)
                    .build());

    /**
     * Cuánto se ven los colores propios de los mechones en un pelo por mechones (StrandColors), en
     * pasos de HairResolver.COLOR_STEPS: 0 o ausente = entero del color de pelo; el máximo = los
     * colores del código. Lo pone HairResolver en el mismo stack virtual (interruptor del jugador,
     * forma que tiñe el pelo y su transición).
     */
    public static final Supplier<DataComponentType<Integer>> HAIR_STRAND_COLORS =
            COMPONENTS.register("hair_strand_colors", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * Atributos imbuidos en una pieza de armadura (Fase 5a de la 0.7.0). Ausente = ImbuedAttributes.NONE. Vale en
     * cualquier ítem (vanilla o de otro mod): los suma ArmorAttributeBonus solo si la pieza está en una ranura de armadura.
     */
    public static final Supplier<DataComponentType<com.hmc.zenkai.feature.armor.ImbuedAttributes>> IMBUED =
            COMPONENTS.register("imbued", () -> DataComponentType.<com.hmc.zenkai.feature.armor.ImbuedAttributes>builder()
                    .persistent(com.hmc.zenkai.feature.armor.ImbuedAttributes.CODEC)
                    .networkSynchronized(com.hmc.zenkai.feature.armor.ImbuedAttributes.STREAM_CODEC)
                    .build());

    /** Set de armadura que diseña un patrón (Fase 5b): id de las recetas zenkai:armor_fabrication. Ausente = patrón en blanco. */
    public static final Supplier<DataComponentType<net.minecraft.resources.ResourceLocation>> ARMOR_PATTERN =
            COMPONENTS.register("armor_pattern", () -> DataComponentType.<net.minecraft.resources.ResourceLocation>builder()
                    .persistent(net.minecraft.resources.ResourceLocation.CODEC)
                    .networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC)
                    .build());

    /** Calidad de una sangre (Fase 5b, P2): puntos que sube al imbuir, 1 (mobs vanilla), 2 (mobs del mod) o 5 (jefes).
     *  Ausente = 1. */
    public static final Supplier<DataComponentType<Integer>> BLOOD_QUALITY =
            COMPONENTS.register("blood_quality", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * Tipo de Ki con que se infundió el material (Fase 5c de la 0.7.0, KiInfusion): lo pone la infusora en el lingote y
     * lo heredan hilo, tela y la armadura fabricada. Ausente = sin infundir. Se guarda por nombre («base», «divine»…)
     * para que reordenar el enum no cambie los saves; por red viaja el ordinal.
     */
    public static final Supplier<DataComponentType<com.hmc.zenkai.feature.kichamber.KiEnergyType>> KI_INFUSION =
            COMPONENTS.register("ki_infusion", () -> DataComponentType.<com.hmc.zenkai.feature.kichamber.KiEnergyType>builder()
                    .persistent(com.hmc.zenkai.feature.armor.ArmorImbuingRecipe.KI_CODEC)
                    .networkSynchronized(net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs.enumCodec(
                            com.hmc.zenkai.feature.kichamber.KiEnergyType.class))
                    .build());

    /** Contenido de una cápsula Hoi-Poi llena (Fase 6): id del archivo y resumen. Ausente = vacía. Ver CapsuleContent. */
    public static final Supplier<DataComponentType<com.hmc.zenkai.feature.capsule.CapsuleContent>> CAPSULE_CONTENT =
            COMPONENTS.register("capsule_content", () -> DataComponentType.<com.hmc.zenkai.feature.capsule.CapsuleContent>builder()
                    .persistent(com.hmc.zenkai.feature.capsule.CapsuleContent.CODEC)
                    .networkSynchronized(com.hmc.zenkai.feature.capsule.CapsuleContent.STREAM_CODEC)
                    .build());

    /**
     * Id de vínculo de una cápsula (vacía o llena) para el control remoto: lo pone el control la primera vez que la vincula
     * y viaja con el stack por captura y pegado, así el control la reconoce aunque haya subido o bajado un bloque.
     */
    public static final Supplier<DataComponentType<java.util.UUID>> CAPSULE_LINK =
            COMPONENTS.register("capsule_link", () -> DataComponentType.<java.util.UUID>builder()
                    .persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                    .build());

    /** Vínculos del control remoto de cápsulas: dueño, lista y seleccionada. Ver CapsuleRemoteLinks. */
    public static final Supplier<DataComponentType<com.hmc.zenkai.feature.capsule.CapsuleRemoteLinks>> CAPSULE_REMOTE =
            COMPONENTS.register("capsule_remote", () -> DataComponentType.<com.hmc.zenkai.feature.capsule.CapsuleRemoteLinks>builder()
                    .persistent(com.hmc.zenkai.feature.capsule.CapsuleRemoteLinks.CODEC)
                    .networkSynchronized(com.hmc.zenkai.feature.capsule.CapsuleRemoteLinks.STREAM_CODEC)
                    .build());

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}