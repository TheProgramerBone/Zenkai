package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.client.aura.compose.AuraBodyParams.Key;
import com.hmc.zenkai.client.aura.compose.component.AuraComponentConfig;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * ESTILO de un {@code aura_type}: la variante concreta de una familia, en datos. Se carga de
 * {@code assets/<ns>/zenkai_aura_styles/<aura_type>.json} ({@link AuraStyleManager}): es una
 * decisión del CLIENTE sobre cómo se ve el aura, así que va en assets (un resource pack puede
 * cambiarla y F3+T la recarga), no en el datapack. La firma de poder-neutral de la forma
 * ({@code data/<ns>/zenkai_aura_signatures/}) sigue en data y sigue alimentando el perfil.
 *
 * @param auraType   el {@code aura_type} de la forma al que responde.
 * @param family     familia ({@link AuraFamilies}).
 * @param bodyMul    multiplicadores sobre el cuerpo ya moldeado por la familia (1 = sin cambio).
 * @param bodySet    valores ABSOLUTOS (las claves dimensionales se escalan por poder, ver
 *                   {@link AuraBodyParams}).
 * @param inner      capa interior; null = la de la familia.
 * @param components componentes; null = los de la familia. {@code extra} se añaden a esos.
 * @param ground     respuesta de suelo; null = la de la familia.
 * @param transform  secuencia de transformación; null = la de la familia.
 * @param trail      estela de vuelo; null = la de la familia.
 */
public record AuraStyle(String auraType, AuraFamily family, Map<Key, Float> bodyMul, Map<Key, Float> bodySet,
                        @Nullable AuraInnerSpec inner, @Nullable List<AuraComponentConfig> components,
                        List<AuraComponentConfig> extra, @Nullable String ground, @Nullable String transform,
                        @Nullable AuraTrailSpec trail) {

    /** Estilo desnudo de una familia: lo que se usa para un aura_type sin JSON y para
     *  {@code /zenkaiaura family}. */
    public static AuraStyle bare(String auraType, AuraFamily family) {
        return new AuraStyle(auraType, family, Map.of(), Map.of(), null, null, List.of(), null, null, null);
    }

    public AuraInnerSpec effectiveInner() { return inner != null ? inner : family.inner(); }

    public AuraTrailSpec effectiveTrail() { return trail != null ? trail : family.trail(); }

    public List<AuraComponentConfig> effectiveComponents() {
        List<AuraComponentConfig> base = components != null ? components : family.components();
        if (extra.isEmpty()) return base;
        java.util.ArrayList<AuraComponentConfig> all = new java.util.ArrayList<>(base);
        all.addAll(extra);
        return List.copyOf(all);
    }

    /** Aplica familia y estilo sobre los parámetros de partida (la llama calibrada con el perfil). */
    public void shape(AuraBodyParams b) {
        family.shape().accept(b);
        for (Map.Entry<Key, Float> e : bodySet.entrySet()) b.set(e.getKey(), e.getValue());
        for (Map.Entry<Key, Float> e : bodyMul.entrySet()) b.mul(e.getKey(), e.getValue());
        // Picos/lóbulos SIEMPRE enteros: uno fraccionario deja un salto en la espalda (la costura
        // de atan, ver aura_flame_shell.vsh).
        b.put(Key.PEAKS, Math.max(1f, Math.round(b.get(Key.PEAKS))));
    }
}
