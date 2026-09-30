package com.hmc.zenkai.feature.skills;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Datos de un ESTADO SUPERPUESTO (overlay): una capacidad que se activa/desactiva como
 * interruptor (ver {@link SkillToggles}) y cuyo efecto puede depender de la Transformación
 * PRIMARIA activa en ese momento ({@code PlayerFormAttachment.getFormId()}), en vez de
 * reemplazarla — pensado para Ultra Instinct/Ultra Ego "técnica" (Base + UI, SSJ Blue + UI...),
 * distinto de su propia Transformación completa (que sigue siendo un {@link
 * com.hmc.zenkai.feature.forms.FormDef} normal, sin relación de código con esta clase — ver
 * el javadoc de {@code SkillToggles} para el porqué de la separación).
 *
 * {@code id} coincide con el id de un {@link SkillToggles.Toggle} — acoplamiento por STRING, no
 * por referencia dura: un {@code OverlayStateDef} sin `Toggle` correspondiente nunca se activa
 * (`SkillToggles.isOn` ya devuelve `false` para un id no registrado); un `Toggle` sin
 * `OverlayStateDef` cargado simplemente no aplica ningún efecto (interruptor visible y
 * funcional en la rueda, pero inerte) — ninguno de los dos casos crashea ni dejar estado
 * corrupto, ver {@link OverlayStateManager}.
 *
 * {@code formModifiers}: mapa ABIERTO, sin ningún canal fijado en Java. Clave externa = id de
 * forma completo ("zenkai:ssj_god") o el literal {@code "default"} (respaldo si la forma actual
 * no tiene entrada propia); clave interna = nombre de canal libre ("stat_percent",
 * "ki_drain_mult", cualquier otro que se decida más adelante). Vacío hoy en todos los usos
 * reales del mod — la infraestructura existe, el balance de UI/UE todavía no.
 */
public record OverlayStateDef(String id, int auraRgb, int priority,
                              Map<String, Map<String, Double>> formModifiers) {

    /** -1 = sin capa de aura propia (el overlay no cambia el aura). */
    public boolean hasAura() { return auraRgb >= 0; }

    /**
     * Valor de un canal para la forma primaria dada. Orden de búsqueda: entrada propia de esa
     * forma -> entrada "default" -> {@code fallback}. Ni el nombre del canal ni el significado
     * de "eficiencia" están fijados aquí — es una consulta genérica, quien llama decide qué
     * pedir y qué hacer con el número.
     */
    public double modifier(ResourceLocation formId, String channel, double fallback) {
        Map<String, Double> perForm = formId == null ? null : formModifiers.get(formId.toString());
        if (perForm != null && perForm.containsKey(channel)) return perForm.get(channel);
        Map<String, Double> def = formModifiers.get("default");
        if (def != null && def.containsKey(channel)) return def.get(channel);
        return fallback;
    }

    private static volatile Map<String, OverlayStateDef> REGISTRY = Map.of();

    public static void replaceAll(Map<String, OverlayStateDef> defs) {
        REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(defs));
    }

    /** null si no hay definición cargada para ese id (ver javadoc de clase: caso seguro). */
    public static OverlayStateDef get(String id) { return id == null ? null : REGISTRY.get(id); }

    public static Collection<OverlayStateDef> all() { return REGISTRY.values(); }

    // StreamCodec manual: mapas anidados, composite no llega.
    public static final StreamCodec<FriendlyByteBuf, OverlayStateDef> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeUtf(d.id());
                buf.writeInt(d.auraRgb());
                buf.writeVarInt(d.priority());
                buf.writeVarInt(d.formModifiers().size());
                for (var outer : d.formModifiers().entrySet()) {
                    buf.writeUtf(outer.getKey());
                    buf.writeVarInt(outer.getValue().size());
                    for (var inner : outer.getValue().entrySet()) {
                        buf.writeUtf(inner.getKey());
                        buf.writeDouble(inner.getValue());
                    }
                }
            },
            buf -> {
                String id = buf.readUtf();
                int auraRgb = buf.readInt();
                int priority = buf.readVarInt();
                int n = buf.readVarInt();
                Map<String, Map<String, Double>> mods = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    String outerKey = buf.readUtf();
                    int m = buf.readVarInt();
                    Map<String, Double> inner = new LinkedHashMap<>();
                    for (int j = 0; j < m; j++) inner.put(buf.readUtf(), buf.readDouble());
                    mods.put(outerKey, Collections.unmodifiableMap(inner));
                }
                return new OverlayStateDef(id, auraRgb, priority, Collections.unmodifiableMap(mods));
            });
}
