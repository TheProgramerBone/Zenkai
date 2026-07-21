package com.hmc.zenkai.core.technique;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Números de una técnica. Viven en datapack:
 *   data/&lt;ns&gt;/zenkai_techniques/ki/&lt;id&gt;.json
 *   data/&lt;ns&gt;/zenkai_techniques/physical/&lt;id&gt;.json
 * y se sincronizan al cliente con TechniqueSyncPacket (ver TechniqueManager).
 *
 * El id es el nombre del enum en minúsculas. Los enums (KiTechniqueType /
 * PhysicalTechnique) siguen siendo la IDENTIDAD: nombre = clave NBT, ordinal = celda
 * del atlas de íconos, y la estela sigue codificada ahí (es visual, no balance).
 *
 * SIN JSON = TÉCNICA DESACTIVADA: no se puede desbloquear, ni guardar, ni disparar,
 * y no aparece en las pantallas. Los getters del enum devuelven valores neutros y
 * los costes devuelven Integer.MAX_VALUE para que nada sea "asequible" por accidente.
 *
 * Campos según kind (los que no aplican quedan a 0):
 *  - KI:       damage_mult, ki_cost_mult, charge_ticks, cooldown_ticks, speed, count,
 *              defensive, default_rgb
 *  - PHYSICAL: damage_mult, stamina_pct, cooldown_ticks, range
 * Comunes: tp_cost (coste de desbloqueo), mind_req (MND mínimo para desbloquear).
 */
public record TechniqueDef(String id, Kind kind, int tpCost, int mindReq,
                           double damageMult, double kiCostMult, double staminaPct,
                           int chargeTicks, int cooldownTicks, double speed,
                           int count, boolean defensive, int defaultRgb, double range) {

    public enum Kind {
        KI, PHYSICAL;

        /** Nombre de la subcarpeta del datapack. */
        public String folder() { return name().toLowerCase(Locale.ROOT); }

        public static Kind byFolder(String s) {
            for (Kind k : values()) if (k.folder().equals(s)) return k;
            return null;
        }
    }

    private static volatile Map<String, TechniqueDef> REGISTRY = Map.of();

    private static String key(Kind kind, String id) { return kind.name() + "/" + id; }

    /** Reemplaza el snapshot completo (reload del server o sync al cliente). */
    public static void replaceAll(Map<String, TechniqueDef> defs) {
        Map<String, TechniqueDef> m = new LinkedHashMap<>();
        for (TechniqueDef d : defs.values()) m.put(key(d.kind(), d.id()), d);
        REGISTRY = Collections.unmodifiableMap(m);
    }

    /** null si la técnica no está definida en ningún datapack (= desactivada). */
    public static TechniqueDef get(Kind kind, String id) { return REGISTRY.get(key(kind, id)); }

    public static java.util.Collection<TechniqueDef> all() { return REGISTRY.values(); }

    // StreamCodec manual: 14 campos, muy por encima de lo que cubre composite.
    public static final StreamCodec<FriendlyByteBuf, TechniqueDef> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeUtf(d.id());
                buf.writeVarInt(d.kind().ordinal());
                buf.writeVarInt(d.tpCost());
                buf.writeVarInt(d.mindReq());
                buf.writeDouble(d.damageMult());
                buf.writeDouble(d.kiCostMult());
                buf.writeDouble(d.staminaPct());
                buf.writeVarInt(d.chargeTicks());
                buf.writeVarInt(d.cooldownTicks());
                buf.writeDouble(d.speed());
                buf.writeVarInt(d.count());
                buf.writeBoolean(d.defensive());
                buf.writeInt(d.defaultRgb());
                buf.writeDouble(d.range());
            },
            buf -> {
                String id = buf.readUtf();
                int k = buf.readVarInt();
                Kind kind = (k >= 0 && k < Kind.values().length) ? Kind.values()[k] : Kind.KI;
                return new TechniqueDef(id, kind,
                        buf.readVarInt(), buf.readVarInt(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readVarInt(), buf.readVarInt(),
                        buf.readDouble(), buf.readVarInt(), buf.readBoolean(),
                        buf.readInt(), buf.readDouble());
            });
}