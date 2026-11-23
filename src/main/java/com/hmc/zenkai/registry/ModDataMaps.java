package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Créditos de modelos y texturas.
 * Va como DataMap y no como componente del item por tres razones: no ocupa NBT en cada
 * stack, se declara una vez por item en datagen, y un pack de recursos o un servidor puede
 * sobrescribirlo sin tocar el jar.
 * El codec acepta las dos formas:
 *   "zenkai:ki_scythe": "Juan"
 *   "zenkai:ki_scythe": { "author": "Juan", "detail": "modelo y textura" }
 * La segunda es opcional; si no pones detail, el tooltip solo muestra el autor.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ModDataMaps {
    private ModDataMaps() {}

    public record ModelCredit(String author, String detail) {

        private static final Codec<ModelCredit> FULL = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("author").forGetter(ModelCredit::author),
                Codec.STRING.optionalFieldOf("detail", "").forGetter(ModelCredit::detail)
        ).apply(i, ModelCredit::new));

        /** Permite escribir solo el nombre como cadena suelta. */
        public static final Codec<ModelCredit> CODEC =
                Codec.either(Codec.STRING, FULL).xmap(
                        e -> e.map(s -> new ModelCredit(s, ""), c -> c),
                        c -> c.detail().isEmpty()
                                ? com.mojang.datafixers.util.Either.left(c.author())
                                : com.mojang.datafixers.util.Either.right(c));
    }

    public static final DataMapType<net.minecraft.world.item.Item, ModelCredit> MODEL_CREDITS =
            DataMapType.builder(
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "model_credits"),
                    Registries.ITEM,
                    ModelCredit.CODEC).synced(ModelCredit.CODEC, false).build();

    /**
     * Fuente de sangre de un tipo de entidad (Fase 5b de la 0.7.0, P2): qué sangre suelta al morir a manos de un jugador,
     * de qué calidad (1 vanilla, 2 mobs del mod, 5 jefes) y con qué probabilidad. Data map y no tabla de loot para que un
     * addon o un datapack añada mobs con una línea, sin tocar sus loot tables. Lo lee feature/armor/BloodDrops.
     * Un mob puede soltar VARIAS (2026-11-22): el valor es una lista y cada entrada tira su probabilidad por separado.
     * Se acepta también un objeto suelto (formato de la primera versión) para no romper datapacks ya escritos.
     */
    public record BloodSource(net.minecraft.world.item.Item blood, int quality, float chance) {
        public static final Codec<BloodSource> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec().fieldOf("blood").forGetter(BloodSource::blood),
                Codec.intRange(1, 1000).optionalFieldOf("quality", 1).forGetter(BloodSource::quality),
                Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.1f).forGetter(BloodSource::chance)
        ).apply(i, BloodSource::new));
    }

    /** Lista de sangres, o una sola (formato viejo). */
    public static final Codec<java.util.List<BloodSource>> BLOOD_LIST_CODEC =
            Codec.withAlternative(BloodSource.CODEC.listOf(), BloodSource.CODEC, java.util.List::of);

    public static final DataMapType<net.minecraft.world.entity.EntityType<?>, java.util.List<BloodSource>> BLOOD_SOURCES =
            DataMapType.builder(
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "blood_sources"),
                    Registries.ENTITY_TYPE,
                    BLOOD_LIST_CODEC)
                    // Sincronizado (no obligatorio) para que la compat de JEI pueda listar qué mob suelta qué sangre
                    // en el cliente; sin esto el data map solo existe en el servidor.
                    .synced(BloodSource.CODEC.listOf(), false)
                    .build();

    @SubscribeEvent
    public static void onRegister(RegisterDataMapTypesEvent event) {
        event.register(MODEL_CREDITS);
        event.register(BLOOD_SOURCES);
    }
}