package com.hmc.zenkai.client.aura.compose.component;

import com.google.gson.JsonObject;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.compose.AuraBodyKind;
import com.hmc.zenkai.client.aura.compose.AuraBodyParams;
import com.hmc.zenkai.client.aura.compose.AuraBodyParams.Key;
import com.hmc.zenkai.client.aura.compose.AuraComposer;
import com.hmc.zenkai.client.aura.compose.AuraFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.Set;

/**
 * CONTORNO de energía pegado al cuerpo: el borde luminoso que envuelve la silueta (aura_6,
 * aura_14). Sustituye, en el sistema compuesto, al rim legacy (AuraRimRenderer), que solo existía
 * para formas con {@code divine_tier}: aquí lo pide cualquier estilo. Se dibuja con el programa
 * FLOW (las cápsulas sobre la pose real) en modo "solo borde", así que sigue brazos y piernas y no
 * es un cascarón inflado del modelo (que tapaba al jugador o se partía en caras sueltas).
 * <pre>
 * { "type": "rim", "strength": 0.5, "width": 0.25, "pulse": "sine", "pulse_hz": 3.0, "pulse_amp": 0.3, "color": "hot" }
 * </pre>
 * Con Kaioken o carga extrema late más (AuraDynamics). En primera persona propia no se dibuja.
 */
public final class RimComponent implements AuraComponent {

    public static final String TYPE = "rim";

    public record Config(float strength, float width, ShellComponent.Pulse pulse, float pulseHz, float pulseAmp,
                         TintSpec color, float flecks) implements AuraComponentConfig {

        private static final Set<String> KEYS = Set.of("strength", "width", "pulse", "pulse_hz", "pulse_amp",
                "color", "flecks");

        @Override public String type() { return TYPE; }

        @Override public AuraComponent create() { return new RimComponent(this); }

        public static Config parse(JsonObject o) {
            String unknown = AuraComponentTypes.unknownKeys(o, KEYS);
            if (unknown != null) Zenkai.LOGGER.warn("[Zenkai] Contorno de aura: claves desconocidas ignoradas: {}", unknown);
            return new Config(
                    Mth.clamp(AuraComponentTypes.f(o, "strength", 0.45f), 0f, 1f),
                    Math.max(0f, AuraComponentTypes.f(o, "width", 0.25f)),
                    ShellComponent.Pulse.valueOf(AuraComponentTypes.s(o, "pulse", "sine").toUpperCase(Locale.ROOT)),
                    AuraComponentTypes.f(o, "pulse_hz", 1.5f),
                    AuraComponentTypes.f(o, "pulse_amp", 0.2f),
                    TintSpec.parse(AuraComponentTypes.s(o, "color", "hot")),
                    Mth.clamp(AuraComponentTypes.f(o, "flecks", 0f), 0f, 1f));
        }
    }

    private final Config cfg;

    private RimComponent(Config cfg) { this.cfg = cfg; }

    @Override public String type() { return TYPE; }

    @Override public boolean alive() { return false; }

    @Override public void update(AuraFrame f) { }

    @Override
    public void submit(AuraFrame f, PoseStack pose) {
        if (f.body == null || f.selfFirstPerson || f.growth <= 0.01f) return;
        AuraBodyParams p = f.body.copy();
        float beat = ShellComponent.pulse(cfg.pulse(), cfg.pulseHz() * f.dynamics.pulseRateFactor(),
                cfg.pulseAmp(), f.seconds);
        p.put(Key.INFLATE, cfg.width());
        p.put(Key.AMP, 0.02f * f.powerScale);
        p.put(Key.LICKS, 0f);
        p.put(Key.RIM_ONLY, 1f);
        p.put(Key.FLECKS, cfg.flecks());
        p.put(Key.ALPHA_EDGE, Mth.clamp(cfg.strength() * beat, 0f, 1f));
        p.put(Key.CEL_RIM, 0.55f);
        p.put(Key.FRESNEL_POWER, 1.5f);
        p.put(Key.FRESNEL_DIVIS, 0.8f);
        p.put(Key.BAND_SOFT, 0.05f);
        p.put(Key.EMIT_EDGE, 0.6f);
        p.put(Key.SPEED, 0.8f);
        float[] c = cfg.color().of(f);
        AuraComposer.submitBodyLayer(f, pose, AuraBodyKind.FLOW, p, c, c, f.extras, 0.999f);
    }
}
