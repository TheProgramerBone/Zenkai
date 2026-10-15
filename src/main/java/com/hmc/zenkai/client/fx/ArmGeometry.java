package com.hmc.zenkai.client.fx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.feature.race.RaceBodyResolver;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Medidas del PUÑO de un jugador, sacadas del modelo que de verdad se ve: el geo racial que lleva puesto (pechera
 * de raza/forma) o, sin él, el brazo vanilla (ancho o fino según la skin). Todas en píxeles de modelo, relativas
 * al pivote del hombro, con el mismo signo de X local que usa el hueso del brazo. Las usa FistEnergyFx para que la
 * caja termine justo en el puño en cualquier raza y forma (Majin con brazos cortos, Oozaru enorme, brazo fino...).
 */
public final class ArmGeometry {
    private ArmGeometry() {}

    /** cx: centro del puño en X; tip: distancia del pivote al extremo del brazo; hx/hz: medio ancho y medio fondo;
     *  hand: largo de la caja, ya limitado a un puño. */
    public record Arm(float cx, float tip, float hx, float hz, float hand, float pivX, float pivY) {}

    /** Pivote del hombro de los modelos vanilla (geo): lo que no coincide hay que corregirlo (el Oozaru lo tiene
     *  más fuera y más arriba). X es -5/+5 según el brazo, Y 22. */
    public static final float VANILLA_PIV_Y = 22f;
    public static float vanillaPivX(boolean right) { return right ? -5f : 5f; }

    private static final float MAX_HAND = 4.5f;
    private static final Map<ResourceLocation, Arm[]> CACHE = new HashMap<>();

    /** Medidas del brazo derecho o izquierdo de este jugador. */
    public static Arm of(Player p, boolean right) {
        ResourceLocation geo = racialGeo(p);
        if (geo != null) {
            Arm[] a = CACHE.computeIfAbsent(geo, ArmGeometry::load);
            Arm m = a[right ? 0 : 1];
            if (m != null) return m;
        }
        boolean slim = p instanceof AbstractClientPlayer ap && ap.getSkin().model() == PlayerSkin.Model.SLIM;
        float sign = right ? -1f : 1f;
        float pivX = vanillaPivX(right);
        return slim ? new Arm(sign * 0.5f, 10f, 1.5f, 2f, 4f, pivX, VANILLA_PIV_Y)
                : new Arm(sign * 1f, 10f, 2f, 2f, 4f, pivX, VANILLA_PIV_Y);
    }

    /** Geo racial del jugador: el de la primera pieza de armadura de raza que lleve (pechera primero). */
    private static ResourceLocation racialGeo(Player p) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            // El cuerpo racial es armadura VIRTUAL (no está en las ranuras): se pide al mismo resolvedor que el
            // render, que ya aplica el cuerpo propio de la forma (Oozaru, Freezer...).
            ItemStack st = RaceBodyResolver.resolve(p, s);
            if (st.getItem() instanceof GeoLayerArmorItem g) {
                ResourceLocation m = g.resolveModel(st);
                if (m != null && m.getPath().contains("/races/")) return m;
            }
        }
        return null;
    }

    private static Arm[] load(ResourceLocation geo) {
        Arm[] out = new Arm[2];
        try (BufferedReader r = Minecraft.getInstance().getResourceManager().openAsReader(geo)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonArray bones = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            out[0] = measure(bones, "bipedRightArm");
            out[1] = measure(bones, "bipedLeftArm");
        } catch (Exception e) {
            // Sin lectura: cada brazo cae a las medidas vanilla.
        }
        return out;
    }

    /** Recorre el hueso raíz del brazo y sus descendientes; el puño es el cubo que llega más abajo. */
    private static Arm measure(JsonArray bones, String root) {
        Map<String, String> parent = new HashMap<>();
        for (JsonElement e : bones) {
            JsonObject b = e.getAsJsonObject();
            parent.put(b.get("name").getAsString(), b.has("parent") ? b.get("parent").getAsString() : null);
        }
        float pivX = 0, pivY = 0;
        boolean havePivot = false;
        float lowest = Float.MAX_VALUE, cx = 0, hx = 0, hz = 0, height = 0;
        boolean any = false;
        for (JsonElement e : bones) {
            JsonObject b = e.getAsJsonObject();
            String name = b.get("name").getAsString();
            if (name.equals(root)) {
                JsonArray pv = b.getAsJsonArray("pivot");
                pivX = pv.get(0).getAsFloat();
                pivY = pv.get(1).getAsFloat();
                havePivot = true;
            }
            if (!descends(parent, name, root) || !b.has("cubes")) continue;
            for (JsonElement ce : b.getAsJsonArray("cubes")) {
                JsonObject c = ce.getAsJsonObject();
                JsonArray o = c.getAsJsonArray("origin"), s = c.getAsJsonArray("size");
                float minY = o.get(1).getAsFloat();
                if (minY < lowest) {
                    lowest = minY;
                    cx = o.get(0).getAsFloat() + s.get(0).getAsFloat() / 2f;
                    hx = s.get(0).getAsFloat() / 2f;
                    hz = s.get(2).getAsFloat() / 2f;
                    height = s.get(1).getAsFloat();
                    any = true;
                }
            }
        }
        if (!any || !havePivot) return null;
        // Margen de 0,3 px por lado para que la caja abrace el puño sin quedarse dentro de la malla.
        return new Arm(cx - pivX, pivY - lowest, hx + 0.3f, hz + 0.3f, Math.min(height, MAX_HAND), pivX, pivY);
    }

    private static boolean descends(Map<String, String> parent, String name, String root) {
        for (String n = name; n != null; n = parent.get(n)) if (n.equals(root)) return true;
        return false;
    }
}
