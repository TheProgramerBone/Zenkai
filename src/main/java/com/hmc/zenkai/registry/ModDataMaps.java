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
                    ModelCredit.CODEC).synced(ModelCredit.CODEC, false).build();   // ⚠ API

    @SubscribeEvent
    public static void onRegister(RegisterDataMapTypesEvent event) {
        event.register(MODEL_CREDITS);
    }
}