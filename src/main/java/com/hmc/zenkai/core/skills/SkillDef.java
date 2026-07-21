package com.hmc.zenkai.core.skills;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Definición de una habilidad. Viven en datapack (data/&lt; ns&gt; /zenkai_skills/&lt; id&gt; .json —
 * ver SkillManager) y se sincronizan al cliente con SkillSyncPacket.
 * Habilidades CON NIVELES: el maestro enseña el nivel 1, el resto se suben con TP.
 *  - tpCost: coste FIJO por cada nivel a partir del 1.
 *  - mindReq: lista con una entrada por nivel (índice 0 = nivel 1). Es la palanca para
 *    encarecer los niveles altos, ya que el TP es plano.
 *  - values: curvas con nombre, una entrada por nivel. Cada habilidad declara las que
 *    necesita (Fly usa ki_cost_mult y speed_mult; Ki Control ninguna, su fórmula es
 *    50 + 5*nivel) y el código las lee por nombre sin que este schema las conozca.
 *  - master: id del maestro que enseña el nivel 1. Null = nadie lo enseña (razas).
 * El EFECTO se implementa donde corresponda consultando
 * PlayerStatsAttachment#skills().level(id).
 * Claves de lang derivadas: skill.zenkai.&lt; id&gt; (nombre) y skill.zenkai.&lt; id&gt; .desc.
 */
public record SkillDef(String id, int tpCost, int maxLevel, List<Integer> mindReq,
                       boolean purchasable, String master, Map<String, List<Double>> values) {

    private static volatile Map<String, SkillDef> REGISTRY = Map.of();

    /** Reemplaza el snapshot completo (reload del server o sync al cliente). */
    public static void replaceAll(Map<String, SkillDef> defs) {
        REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(defs));
    }

    /** null si no existe. */
    public static SkillDef get(String id) { return REGISTRY.get(id); }

    /** En orden de carga (orden de la lista en la GUI). */
    public static Collection<SkillDef> all() { return REGISTRY.values(); }

    /** Las que enseña un maestro concreto, para su menú de compra. */
    public static List<SkillDef> taughtBy(String masterId) {
        List<SkillDef> out = new ArrayList<>();
        for (SkillDef d : REGISTRY.values()) {
            if (masterId.equals(d.master())) out.add(d);
        }
        return out;
    }

    /** MND necesario para alcanzar ese nivel. 0 si no está definido. */
    public int mindReqFor(int level) {
        int i = level - 1;
        return (i >= 0 && i < mindReq.size()) ? mindReq.get(i) : 0;
    }

    /** Valor de una curva en ese nivel; devuelve fallback si la curva o el nivel no existen. */
    public double value(String key, int level, double fallback) {
        List<Double> curve = values.get(key);
        if (curve == null) return fallback;
        int i = level - 1;
        return (i >= 0 && i < curve.size()) ? curve.get(i) : fallback;
    }

    public String nameKey() { return "skill.zenkai." + id; }
    public String descKey() { return nameKey() + ".desc"; }

    // StreamCodec manual: composite no llega a 7 campos y aquí hay listas y mapas.
    public static final StreamCodec<FriendlyByteBuf, SkillDef> STREAM_CODEC = StreamCodec.of(
            (buf, def) -> {
                buf.writeUtf(def.id());
                buf.writeVarInt(def.tpCost());
                buf.writeVarInt(def.maxLevel());
                buf.writeVarInt(def.mindReq().size());
                for (int m : def.mindReq()) buf.writeVarInt(m);
                buf.writeBoolean(def.purchasable());
                buf.writeUtf(def.master() == null ? "" : def.master());
                buf.writeVarInt(def.values().size());
                for (Map.Entry<String, List<Double>> e : def.values().entrySet()) {
                    buf.writeUtf(e.getKey());
                    buf.writeVarInt(e.getValue().size());
                    for (double d : e.getValue()) buf.writeDouble(d);
                }
            },
            buf -> {
                String id = buf.readUtf();
                int tp = buf.readVarInt();
                int max = buf.readVarInt();
                int n = buf.readVarInt();
                List<Integer> mind = new ArrayList<>(n);
                for (int i = 0; i < n; i++) mind.add(buf.readVarInt());
                boolean purch = buf.readBoolean();
                String master = buf.readUtf();
                int vn = buf.readVarInt();
                Map<String, List<Double>> vals = new LinkedHashMap<>();
                for (int i = 0; i < vn; i++) {
                    String k = buf.readUtf();
                    int ln = buf.readVarInt();
                    List<Double> curve = new ArrayList<>(ln);
                    for (int j = 0; j < ln; j++) curve.add(buf.readDouble());
                    vals.put(k, List.copyOf(curve));
                }
                return new SkillDef(id, tp, max, List.copyOf(mind), purch,
                        master.isEmpty() ? null : master,
                        Collections.unmodifiableMap(vals));
            });
}