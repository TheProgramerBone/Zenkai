package com.hmc.zenkai.feature.race.layer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Pose de la cola en la CINTURA (petición del usuario, 2026-10-11: un solo ítem de cola; antes había
 * un ítem y un modelo "tail_waist" aparte). La pose vive como clip "misc.waist" en
 * animations/tail_loose.animation.json, generado por tools/gen_tail_loose.py, pero NO lo reproduce
 * un controlador: el estado de animación de un GeoLayerArmorItem es UNO, compartido por todos los
 * jugadores, así que un controlador que eligiera el clip según el estilo de cada jugador haría saltar
 * la cola de uno a la pose del otro. En su lugar GeoLayerArmorModel.setCustomAnimations la aplica en
 * cada render a los jugadores con la cola en la cintura (TailGeoLayer marca {@link #active}).
 *
 * Conversión, igual que GeckoLib 4.8.4 (BakedModelFactory y BakedAnimationsAdapter, comprobado en
 * sus fuentes): X e Y del archivo van negadas y Z tal cual, en radianes, y la rotación final de un
 * hueso es la de reposo (initialSnapshot) más la de la animación.
 */
public final class TailWaistPose {
    private TailWaistPose() {}

    private static final ResourceLocation ANIM =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "animations/tail_loose.animation.json");
    private static final String CLIP = "misc.waist";
    private static final String TIP = "tail_tip";
    /** Vaivén de la punta: grados y segundos por ciclo (el resto de la cadena queda quieta). */
    private static final float TIP_SWAY_DEG = 6f, TIP_SWAY_PERIOD_S = 4f;

    /** ¿El jugador que se pinta AHORA lleva la cola en la cintura? Lo fija TailGeoLayer alrededor
     *  de su render (cliente, un solo hilo de render: mismo patrón que wearerHasRealHeadwear). */
    static boolean active = false;

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

    /** Pone la pose de la cintura en los huesos de la cola, encima de lo que haya puesto el idle. */
    static void apply(GeoLayerArmorModel model, double animationTick) {
        for (var e : pose().entrySet()) {
            GeoBone bone = model.getBone(e.getKey()).orElse(null);
            if (bone == null) continue;
            float[] a = e.getValue();
            var rest = bone.getInitialSnapshot();
            float sway = 0f;
            if (TIP.equals(e.getKey())) {
                double t = animationTick / 20.0;
                // Y negada, como cualquier rotación Y del archivo (ver javadoc de la clase)
                sway = -Mth.DEG_TO_RAD * TIP_SWAY_DEG * (float) Math.sin(2 * Math.PI * t / TIP_SWAY_PERIOD_S);
            }
            bone.setRotX(rest.getRotX() + a[0]);
            bone.setRotY(rest.getRotY() + a[1] + sway);
            bone.setRotZ(rest.getRotZ() + a[2]);
        }
    }
}
