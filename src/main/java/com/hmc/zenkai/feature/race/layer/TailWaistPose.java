package com.hmc.zenkai.feature.race.layer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.cache.object.GeoBone;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Pose de la cola en la CINTURA (petición del usuario, 2026-10-11: un solo ítem de cola; antes había
 * un ítem y un modelo "tail_waist" aparte). La pose vive como clip "misc.waist" en
 * animations/tail_loose.animation.json, generado por tools/gen_tail_loose.py, pero NO lo reproduce
 * un controlador: el estado de animación de un GeoLayerArmorItem es UNO, compartido por todos los
 * jugadores, así que un controlador que eligiera el clip según el estilo de cada jugador haría saltar
 * la cola de uno a la pose del otro. En su lugar GeoLayerArmorModel.setCustomAnimations la aplica en
 * cada render a los jugadores con la cola en la cintura (TailGeoLayer fija {@link #weight}).
 *
 * Conversión, igual que GeckoLib 4.8.4 (BakedModelFactory y BakedAnimationsAdapter, comprobado en
 * sus fuentes): X e Y del archivo van negadas y Z tal cual, en radianes, y la rotación final de un
 * hueso es la de reposo (initialSnapshot) más la de la animación.
 *
 * Cambio de estilo animado (2026-10-12): la cola se enrolla y se desenrolla en {@link #WRAP_TICKS}
 * ticks, de la base a la punta, mezclando la pose del idle con la de la cintura (peso por jugador,
 * {@link #weightFor}). Antes el cambio era un salto.
 *
 * Agachado (decisión del usuario, 2026-10-12): la cola pasa a SUELTA mientras el jugador está
 * agachado (TailGeoLayer) y vuelve a la cintura al levantarse, con la misma animación. El torso
 * vanilla se inclina 0,5 rad al agacharse y el anillo, colgado de él, se metía en las piernas; se
 * probó a compensarlo (girar y desplazar tail_base) y el usuario prefirió lo simple.
 */
public final class TailWaistPose {
    private TailWaistPose() {}

    private static final ResourceLocation ANIM =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "animations/tail_loose.animation.json");
    private static final String CLIP = "misc.waist";
    private static final String TIP = "tail_tip";
    /** Vaivén de la punta: grados y segundos por ciclo (el resto de la cadena queda quieta). */
    private static final float TIP_SWAY_DEG = 6f, TIP_SWAY_PERIOD_S = 4f;

    /** Huesos de la cadena, de la base a la punta (orden del enrollado). */
    private static final String[] CHAIN = {"tail_base", "tail_01", "tail_02", "tail_03", "tail_04",
            "tail_05", "tail_06", "tail_07", "tail_tip"};
    /** Duración de enrollar/desenrollar, en ticks. */
    private static final float WRAP_TICKS = 14f;
    /** Desfase de la onda: con 0,6 la punta empieza cuando la base va por el 60%. */
    private static final float WRAP_SPREAD = 0.6f;

    /**
     * Peso de la cintura del jugador que se pinta AHORA (0 = suelta, 1 = en la cintura). Lo fija
     * TailGeoLayer alrededor de su render (cliente, un solo hilo de render: mismo patrón que
     * wearerHasRealHeadwear).
     */
    static float weight = 0f;

    private static final class State {
        float w = Float.NaN;
        float lastTime;
    }

    private static final Map<UUID, State> STATES = new WeakHashMap<>();

    /** Rotación de la animación por hueso, ya convertida a radianes con los signos de GeckoLib. */
    private static Map<String, float[]> pose;

    private static Map<String, float[]> pose() {
        if (pose != null) return pose;
        Map<String, float[]> out = new HashMap<>();
        var res = Minecraft.getInstance().getResourceManager().getResource(ANIM);
        if (res.isPresent()) {
            try (BufferedReader r = res.get().openAsReader()) {
                JsonObject bones = JsonParser.parseReader(r).getAsJsonObject()
                        .getAsJsonObject("animations").getAsJsonObject(CLIP).getAsJsonObject("bones");
                for (var e : bones.entrySet()) {
                    JsonObject rot = e.getValue().getAsJsonObject().getAsJsonObject("rotation");
                    var v = rot.entrySet().iterator().next().getValue().getAsJsonObject().getAsJsonArray("vector");
                    out.put(e.getKey(), new float[] {
                            (float) Math.toRadians(-v.get(0).getAsDouble()),
                            (float) Math.toRadians(-v.get(1).getAsDouble()),
                            (float) Math.toRadians(v.get(2).getAsDouble())});
                }
            } catch (Exception ex) {
                Zenkai.LOGGER.warn("[Zenkai] No se pudo leer la pose de la cola en la cintura: {}", ex.toString());
            }
        }
        pose = out;
        return pose;
    }

    /** Invalida la caché (recarga de recursos). */
    public static void clear() { pose = null; }

    /** Peso de la cintura para este jugador, avanzando hacia su estilo actual a ritmo fijo. */
    public static float weightFor(Player player, boolean waist, float partialTick) {
        State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        float now = player.tickCount + partialTick;
        float target = waist ? 1f : 0f;
        if (Float.isNaN(s.w)) {
            s.w = target; // primera vez que se ve: sin animación
        } else {
            float step = Mth.clamp(now - s.lastTime, 0f, 5f) / WRAP_TICKS;
            s.w = s.w < target ? Math.min(target, s.w + step) : Math.max(target, s.w - step);
        }
        s.lastTime = now;
        return s.w;
    }

    /**
     * Pone la pose de la cintura en los huesos de la cola, mezclada con la del idle según
     * {@link #weight}: cada eslabón tiene su tramo de la onda (la base se enrolla primero).
     */
    static void apply(GeoLayerArmorModel model, double animationTick) {
        // La posición de tail_base se fija en ABSOLUTO cada render (desde su reposo): sumarla a la
        // del fotograma anterior la haría derivar.
        GeoBone base = model.getBone("tail_base").orElse(null);
        if (base != null) {
            var r = base.getInitialSnapshot();
            base.updatePosition(r.getOffsetX(), r.getOffsetY(), r.getOffsetZ());
        }
        if (weight <= 0f) return;
        Map<String, float[]> pose = pose();
        for (int i = 0; i < CHAIN.length; i++) {
            float[] a = pose.get(CHAIN[i]);
            GeoBone bone = model.getBone(CHAIN[i]).orElse(null);
            if (a == null || bone == null) continue;
            float k = link(i);
            if (k <= 0f) continue;
            var rest = bone.getInitialSnapshot();
            float sway = 0f;
            if (TIP.equals(CHAIN[i])) {
                double t = animationTick / 20.0;
                // Y negada, como cualquier rotación Y del archivo (ver javadoc de la clase)
                sway = -Mth.DEG_TO_RAD * TIP_SWAY_DEG * (float) Math.sin(2 * Math.PI * t / TIP_SWAY_PERIOD_S);
            }
            // Lo que puso el idle -> la pose de la cintura.
            bone.setRotX(Mth.lerp(k, bone.getRotX(), rest.getRotX() + a[0]));
            bone.setRotY(Mth.lerp(k, bone.getRotY(), rest.getRotY() + a[1] + sway));
            bone.setRotZ(Mth.lerp(k, bone.getRotZ(), rest.getRotZ() + a[2]));
        }
    }

    /** Peso de un eslabón dentro de la onda de enrollado (suavizado). */
    private static float link(int i) {
        float start = WRAP_SPREAD * i / (CHAIN.length - 1);
        float k = Mth.clamp(weight * (1f + WRAP_SPREAD) - start, 0f, 1f);
        return k * k * (3f - 2f * k);
    }
}
