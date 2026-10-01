package com.hmc.zenkai.client.aura.compose.component;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.compose.AuraBodyKind;
import com.hmc.zenkai.client.aura.compose.AuraBodyMesh;
import com.hmc.zenkai.client.aura.compose.AuraBodyParams;
import com.hmc.zenkai.client.aura.compose.AuraBodyParams.Key;
import com.hmc.zenkai.client.aura.compose.AuraComposer;
import com.hmc.zenkai.client.aura.compose.AuraFrame;
import com.hmc.zenkai.client.aura.compose.AuraStyleManager;
import com.hmc.zenkai.client.aura.flame.AuraFlameColors;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CÁSCARA SECUNDARIA: otro volumen de cuerpo, alrededor (o dentro) del principal, con su propio
 * programa, forma, color y PULSO a otra frecuencia. Es la "segunda capa" que diferencia una aura
 * salvaje (Beast: alas laterales enormes en V que laten más despacio que la llama de dentro,
 * aura_10) o la columna blanda que rodea a Ultra Instinct (aura_12). Parte de los parámetros
 * FINALES del cuerpo del frame (poder incluido) y los moldea:
 * <pre>
 * { "type": "shell", "kind": "flame", "scale": 1.35, "amp": 1.8, "alpha": 0.45, "color": "outer",
 *   "pulse": "sine", "pulse_hz": 0.9, "pulse_amp": 0.06, "body_set": { "side_up_dir": 0.1 }, "body": { ... } }
 * </pre>
 * Se dibuja DETRÁS del cuerpo principal (la cola ordena por distancia y esta va un pelo más
 * lejos). Desde lejos (LOD lejano) no se dibuja: la silueta la lleva el cuerpo principal.
 */
public final class ShellComponent implements AuraComponent {

    public static final String TYPE = "shell";

    public enum Pulse { NONE, SINE, HEARTBEAT }

    public record Config(@Nullable AuraBodyKind kind, float scale, float amp, float alpha, TintSpec color,
                         Pulse pulse, float pulseHz, float pulseAmp, Map<Key, Float> bodySet, Map<Key, Float> bodyMul)
            implements AuraComponentConfig {

        private static final Set<String> KEYS = Set.of("kind", "scale", "amp", "alpha", "color", "pulse",
                "pulse_hz", "pulse_amp", "body_set", "body");

        @Override public String type() { return TYPE; }

        @Override public AuraComponent create() { return new ShellComponent(this); }

        public static Config parse(JsonObject o) {
            String unknown = AuraComponentTypes.unknownKeys(o, KEYS);
            if (unknown != null) Zenkai.LOGGER.warn("[Zenkai] Cáscara de aura: claves desconocidas ignoradas: {}", unknown);
            AuraBodyKind kind = null;
            Pulse pulse;
            try {
                if (o.has("kind")) kind = AuraBodyKind.valueOf(o.get("kind").getAsString().toUpperCase(Locale.ROOT));
                pulse = Pulse.valueOf(AuraComponentTypes.s(o, "pulse", "sine").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new JsonParseException("kind (flame, soft, dense, flow) o pulse (none, sine, heartbeat) desconocido");
            }
            if (kind == AuraBodyKind.FLOW) throw new JsonParseException("una cáscara no puede ser flow (usa el componente rim)");
            return new Config(kind,
                    AuraComponentTypes.f(o, "scale", 1.3f),
                    AuraComponentTypes.f(o, "amp", 1.5f),
                    AuraComponentTypes.f(o, "alpha", 0.5f),
                    TintSpec.parse(AuraComponentTypes.s(o, "color", "outer")),
                    pulse,
                    AuraComponentTypes.f(o, "pulse_hz", 0.9f),
                    AuraComponentTypes.f(o, "pulse_amp", 0.05f),
                    AuraStyleManager.keyMap("shell", o, "body_set"),
                    AuraStyleManager.keyMap("shell", o, "body"));
        }
    }

    private final Config cfg;

    private ShellComponent(Config cfg) { this.cfg = cfg; }

    @Override public String type() { return TYPE; }

    @Override public boolean alive() { return false; }

    @Override public void update(AuraFrame f) { }

    /** Latido: seno suave, o doble golpe de corazón (Kaioken, auras inestables). */
    static float pulse(Pulse kind, float hz, float amp, float seconds) {
        return switch (kind) {
            case NONE -> 1f;
            case SINE -> 1f + amp * Mth.sin(seconds * hz * Mth.TWO_PI);
            case HEARTBEAT -> {
                float ph = (seconds * hz) % 1f;
                // Dos golpes cortos (lub-dub) y reposo.
                float beat = Math.max(Mth.clamp(1f - Math.abs(ph - 0.08f) / 0.06f, 0f, 1f),
                        0.6f * Mth.clamp(1f - Math.abs(ph - 0.26f) / 0.06f, 0f, 1f));
                yield 1f + amp * beat;
            }
        };
    }

    /** Programa de la cáscara: el pedido, o el del cuerpo (una cáscara nunca es FLOW). */
    public static AuraBodyKind kindOf(Config cfg, AuraBodyKind bodyKind) {
        return cfg.kind() != null ? cfg.kind() : (bodyKind == AuraBodyKind.FLOW ? AuraBodyKind.SOFT : bodyKind);
    }

    /** Parámetros de la cáscara a partir de los FINALES del cuerpo. Lo usa también la vista previa. */
    public static AuraBodyParams paramsOf(Config cfg, AuraBodyParams body, float seconds) {
        AuraBodyParams p = body.copy();
        float k = cfg.scale() * pulse(cfg.pulse(), cfg.pulseHz(), cfg.pulseAmp(), seconds);
        for (Key key : Key.values()) if (key.dimensional) p.mul(key, k);
        // El centro (cadera) se queda: la cáscara crece alrededor del cuerpo, no hacia arriba.
        p.put(Key.CENTER_Y, body.get(Key.CENTER_Y));
        p.mul(Key.AMP, cfg.amp());
        p.mul(Key.ALPHA_CENTER, cfg.alpha() * 0.5f);
        p.mul(Key.ALPHA_EDGE, cfg.alpha());
        for (Map.Entry<Key, Float> e : cfg.bodySet().entrySet()) p.set(e.getKey(), e.getValue());
        for (Map.Entry<Key, Float> e : cfg.bodyMul().entrySet()) p.mul(e.getKey(), e.getValue());
        p.put(Key.PEAKS, Math.max(1f, Math.round(p.get(Key.PEAKS))));
        return p;
    }

    @Override
    public void submit(AuraFrame f, PoseStack pose) {
        if (f.body == null || f.lod == AuraBodyMesh.LOD_FAR || f.growth <= 0.01f) return;
        AuraBodyParams p = paramsOf(cfg, f.body, f.seconds);
        float[] edge = cfg.color().of(f).clone();
        float[] center = AuraFlameColors.heatedCenter(edge, p.get(Key.CORE_WHITE));
        AuraComposer.submitBodyLayer(f, pose, kindOf(cfg, f.bodyKind), p, center, edge, f.extras, 1.0005f);
    }
}
