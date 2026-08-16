package com.hmc.zenkai.feature.generator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Un combustible del generador. Vive en datapack:
 *   data/&lt;ns&gt;/zenkai_generator_fuels/&lt;id&gt;.json
 * y se sincroniza al cliente con GeneratorFuelSyncPacket (la pantalla necesita saber cuántos
 * FE/tick va a dar un ítem ANTES de meterlo).
 *
 * JSON, con "item" O "tag" (uno de los dos, no ambos):
 * <pre>
 * { "item": "minecraft:coal",  "fe_per_tick": 8,  "ticks": 200  }
 * { "tag":  "c:coals",         "fe_per_tick": 8,  "ticks": 200  }
 * </pre>
 *
 * NO SE USA getBurnTime DE VANILLA. Un carbón de horno son 1600 ticks, y a cualquier FE/tick
 * razonable eso convierte un stack en varios millones de FE: el catálogo entero del banco de
 * scouter cuesta ~175.000. Los tiempos de aquí son propios y están calibrados contra ese
 * número, no contra la cocción de vanilla. Un ítem quemable que no tenga entrada aquí NO
 * sirve como combustible, y es a propósito: prefiero que falte un material a que un mod
 * cualquiera meta un quemable de 20.000 ticks y rompa la economía sin avisar.
 *
 * @param fePerTick FE producidos cada tick mientras arde. Es la POTENCIA.
 * @param ticks     cuánto arde una unidad. Potencia x ticks = total del ítem.
 */
public record GeneratorFuel(int fePerTick, int ticks) {

    /** Total de FE que rinde una unidad. Es lo que enseña el tooltip. */
    public int totalFe() { return fePerTick * ticks; }

    public static final StreamCodec<FriendlyByteBuf, GeneratorFuel> STREAM_CODEC =
            StreamCodec.of(
                    (buf, f) -> {
                        buf.writeVarInt(f.fePerTick());
                        buf.writeVarInt(f.ticks());
                    },
                    buf -> new GeneratorFuel(buf.readVarInt(), buf.readVarInt()));

    /**
     * Entrada tal cual viene del JSON: todavía sin resolver si apunta a un ítem o a un tag.
     * Se guarda así y no como ítem ya resuelto porque los tags no están disponibles cuando se
     * cargan los datapacks, y resolverlos en ese momento daría listas vacías en silencio.
     *
     * @param key    id del ítem o del tag
     * @param isTag  true si key es un tag
     */
    public record Entry(ResourceLocation key, boolean isTag, GeneratorFuel fuel) {

        public static final StreamCodec<FriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.of(
                        (buf, e) -> {
                            buf.writeResourceLocation(e.key());
                            buf.writeBoolean(e.isTag());
                            GeneratorFuel.STREAM_CODEC.encode(buf, e.fuel());
                        },
                        buf -> new Entry(buf.readResourceLocation(), buf.readBoolean(),
                                GeneratorFuel.STREAM_CODEC.decode(buf)));
    }
}