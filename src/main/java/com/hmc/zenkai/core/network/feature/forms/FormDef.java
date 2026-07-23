package com.hmc.zenkai.core.network.feature.forms;

import com.hmc.zenkai.core.network.feature.Race;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Datos de una transformación. Viven en datapack: data/&lt;ns&gt;/zenkai_forms/&lt;id&gt;.json
 * y se sincronizan al cliente con FormSyncPacket (ver FormManager).
 *
 * La MAESTRÍA interpola entre dos extremos declarados por la propia forma:
 *   stat_percent_untrained -> stat_percent_mastered   (sube con la maestría)
 *   ki_drain_untrained     -> ki_drain_mastered       (baja con la maestría)
 * Por eso NO hace falta un tope de maestría en config: el techo es el dato de cada forma.
 * Nombramos los extremos por la POSICIÓN en la maestría y no por su magnitud, porque el
 * drenaje baja: su "mínimo" es el de maestría máxima, y min/max se prestaba a confusión.
 *
 * La cadena de formas apunta HACIA ATRÁS (parent). Así añadir una rama nueva es crear un
 * archivo sin tocar el de la forma anterior, que es la gracia de los datapacks; la cadena
 * hacia delante se reconstruye al cargar (FormRegistry.nextFrom).
 */
public record FormDef(ResourceLocation id, EnumSet<Race> races, Kind kind,
                      ResourceLocation parent, int tpCost, int holdTicks,
                      double masteryGain,
                      double statPercentUntrained, double statPercentMastered,
                      double kiDrainUntrained, double kiDrainMastered) {

    public enum Kind {
        /** Innata por raza: no la enseña ningún maestro. */
        SUPER,
        /** Requiere maestro (Whis y compañía). */
        DIVINE,
        /** Cuelga de otra forma (parent); es su continuación. */
        EXTENSION;

        public static Kind byName(String s) {
            if (s == null) return SUPER;
            try { return valueOf(s.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return SUPER; }
        }
    }

    private static volatile Map<ResourceLocation, FormDef> REGISTRY = Map.of();

    public static void replaceAll(Map<ResourceLocation, FormDef> defs) {
        REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(defs));
    }

    /** null si esa forma no está definida en ningún datapack. */
    public static FormDef get(ResourceLocation id) { return id == null ? null : REGISTRY.get(id); }

    public static java.util.Collection<FormDef> all() { return REGISTRY.values(); }

    // ── Interpolación por maestría (0..100) ─────────────────────────────────

    private static double lerp(double a, double b, double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return a + (b - a) * c;
    }

    /** Fracción que SUMA al multiplicador total (1.5 = +150%), según la maestría. */
    public double statPercent(double mastery0to100) {
        return lerp(statPercentUntrained, statPercentMastered, mastery0to100 / 100.0);
    }

    /** Ki drenado por tick, según la maestría (baja al dominarla). */
    public double kiDrainPerTick(double mastery0to100) {
        return lerp(kiDrainUntrained, kiDrainMastered, mastery0to100 / 100.0);
    }

    public boolean allows(Race race) { return race != null && races.contains(race); }

    // ── StreamCodec manual (11 campos + lista de razas) ─────────────────────

    public static final StreamCodec<FriendlyByteBuf, FormDef> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeResourceLocation(d.id());
                buf.writeVarInt(d.races().size());
                for (Race r : d.races()) buf.writeVarInt(r.ordinal());
                buf.writeVarInt(d.kind().ordinal());
                buf.writeBoolean(d.parent() != null);
                if (d.parent() != null) buf.writeResourceLocation(d.parent());
                buf.writeVarInt(d.tpCost());
                buf.writeVarInt(d.holdTicks());
                buf.writeDouble(d.masteryGain());
                buf.writeDouble(d.statPercentUntrained());
                buf.writeDouble(d.statPercentMastered());
                buf.writeDouble(d.kiDrainUntrained());
                buf.writeDouble(d.kiDrainMastered());
            },
            buf -> {
                ResourceLocation id = buf.readResourceLocation();
                int n = buf.readVarInt();
                List<Race> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    int o = buf.readVarInt();
                    if (o >= 0 && o < Race.values().length) list.add(Race.values()[o]);
                }
                EnumSet<Race> races = list.isEmpty() ? EnumSet.noneOf(Race.class) : EnumSet.copyOf(list);
                int k = buf.readVarInt();
                Kind kind = (k >= 0 && k < Kind.values().length) ? Kind.values()[k] : Kind.SUPER;
                ResourceLocation parent = buf.readBoolean() ? buf.readResourceLocation() : null;
                return new FormDef(id, races, kind, parent,
                        buf.readVarInt(), buf.readVarInt(),
                        buf.readDouble(),
                        buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble());
            });
}