package com.hmc.zenkai.core.network.feature.race;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hmc.zenkai.core.network.feature.race.RaceTextureUtil.deriveMask;
import static com.hmc.zenkai.core.network.feature.race.RaceTextureUtil.resourceExists;

/**
 * Descubre las capas de tinte numeradas de un item de cuerpo por convención, a partir de su
 * textura base: &lt;base&gt;_layer_0.png, _1.png, _2.png ... hasta el primer hueco.
 * Cada capa lleva opcionalmente un JSON hermano &lt;base&gt;_layer_&lt;n&gt;.json:
 *   { "channel": "detail" }   (skin | hair | detail | lines)
 *   { "color":   "FF8800" }   (hex fijo, ignora el canal)
 * Sin JSON -> canal "detail".
 * Añadir una capa a una raza = soltar el PNG (+ JSON opcional). Cero código.
 * El resultado se cachea por textura base (invalidado al recargar recursos / cambiar de mundo).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class RaceLayerDiscovery {
    private RaceLayerDiscovery() {}

    /**
     * Una capa resuelta. El COLOR ACTUAL vive en PlayerVisualAttachment.layerColors[index]
     * (sincronizado); si el jugador no la ha fijado se usa {@code defaultRgb} (del JSON).
     * {@code label} y {@code presets} los consume la GUI.
     */
    public record Layer(ResourceLocation texture, int index, String label,
                        String labelKey, int dy, int defaultRgb, int[] presets) {

        /**
         * Etiqueta traducible: clave "layer.<namespace>.<base>.<n>" con fallback al
         * "label" literal del JSON. Añade la clave a en_us.json para traducirla.
         */
        public net.minecraft.network.chat.Component labelComponent() {
            return net.minecraft.network.chat.Component.translatableWithFallback(labelKey, label);
        }

        /** Color RGB efectivo: override del jugador si existe, si no el default del JSON. */
        public int rgb(Player player) {
            var att = PlayerVisualAttachment.get(player);
            if (att.hasLayerColor(index)) return att.getLayerColorRgb(index) & 0xFFFFFF;
            return defaultRgb & 0xFFFFFF;
        }

        /** ARGB opaco para el pase de render. */
        public int argb(Player player) { return 0xFF000000 | rgb(player); }
    }

    private static final Map<ResourceLocation, List<Layer>> CACHE = new ConcurrentHashMap<>();

    /** Capas de este item (cacheadas). Vacío si no tiene ninguna. */
    public static List<Layer> layersFor(GeoLayerArmorItem item) {
        ResourceLocation base = normalizeBase(item.getTexturePath());
        return CACHE.computeIfAbsent(base, RaceLayerDiscovery::discover);
    }

    /**
     * Normaliza la textura del item a su "base de capas": quita la extensión, un sufijo
     * {@code _layer_<n>} y/o {@code _colorable}. Así el item base (namekian_player.png) y el
     * coloreable (namekian_player_layer_0.png) descubren EL MISMO conjunto de capas.
     * Sin esto, el coloreable derivaba namekian_player_layer_0_layer_0.png (inexistente) ->
     * 0 capas -> el cuerpo se pintaba solo con el pase base.
     */
    private static ResourceLocation normalizeBase(ResourceLocation tex) {
        String p = tex.getPath();
        int dot = p.lastIndexOf('.');
        String ext  = (dot >= 0) ? p.substring(dot) : ".png";
        String stem = (dot >= 0) ? p.substring(0, dot) : p;
        stem = stem.replaceFirst("_layer_\\d+$", "");
        if (stem.endsWith("_colorable")) stem = stem.substring(0, stem.length() - "_colorable".length());
        return ResourceLocation.fromNamespaceAndPath(tex.getNamespace(), stem + ext);
    }

    private static List<Layer> discover(ResourceLocation base) {
        List<Layer> out = new ArrayList<>();
        for (int n = 0; ; n++) {
            ResourceLocation tex = deriveMask(base, "_layer_" + n);
            if (!resourceExists(tex)) break; // primer hueco = fin
            out.add(readConfig(base, tex, n));
        }
        return List.copyOf(out);
    }

    /**
     * Lee &lt;base&gt;_layer_&lt;n&gt;.json:
     *   { "label":"Detail", "color":"F3ACB7", "presets":["F3ACB7","E75C72"] }
     *   - label:   nombre en la GUI (fallback "Layer n")
     *   - color:   color POR DEFECTO de la capa (hex). Se usa si el jugador no la ha tocado.
     *   - presets: paleta opcional para la GUI (hex). Vacío -> la GUI muestra solo swatch+picker.
     * Sin JSON: label "Layer n", default blanco, sin presets.
     */
    private static Layer readConfig(ResourceLocation base, ResourceLocation tex, int n) {
        String   label      = "Layer " + n;
        int      dy         = 0;      // desplazamiento vertical extra de su fila en la GUI (px)
        int      defaultRgb = 0xFFFFFF;
        int[]    presets    = EMPTY;

        // Clave de traducción: layer.<namespace>.<archivo base sin extensión>.<n>
        String basePath = base.getPath();
        int slash = basePath.lastIndexOf('/');
        int dot2  = basePath.lastIndexOf('.');
        String baseName = basePath.substring(slash + 1, dot2 > slash ? dot2 : basePath.length());
        String labelKey = "layer." + base.getNamespace() + "." + baseName + "." + n;

        ResourceLocation json = deriveMask(base, "_layer_" + n);
        json = ResourceLocation.fromNamespaceAndPath(json.getNamespace(),
                json.getPath().replaceFirst("\\.png$", ".json"));
        var opt = Minecraft.getInstance().getResourceManager().getResource(json);
        if (opt.isPresent()) {
            try (BufferedReader r = opt.get().openAsReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                if (o.has("label"))  label      = o.get("label").getAsString();
                if (o.has("dy"))     dy         = o.get("dy").getAsInt();
                if (o.has("color"))  defaultRgb = parseHex(o.get("color").getAsString());
                if (o.has("presets") && o.get("presets").isJsonArray()) {
                    var arr = o.getAsJsonArray("presets");
                    presets = new int[arr.size()];
                    for (int i = 0; i < arr.size(); i++) presets[i] = parseHex(arr.get(i).getAsString());
                }
            } catch (Exception ex) {
                Zenkai.LOGGER.warn("[Zenkai] Layer JSON inválido {}: {}", json, ex.toString());
            }
        }
        return new Layer(tex, n, label, labelKey, dy, defaultRgb, presets);
    }

    private static final int[] EMPTY = new int[0];

    private static int parseHex(String s) {
        return (int) (Long.parseLong(s.replace("#", "").trim(), 16) & 0xFFFFFF);
    }

    /** Invalida la caché (recarga de recursos o cambio de mundo). */
    public static void clear() { CACHE.clear(); }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) { clear(); }
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }


}