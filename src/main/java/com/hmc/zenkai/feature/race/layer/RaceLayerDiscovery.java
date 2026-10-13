package com.hmc.zenkai.feature.race.layer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
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

import static com.hmc.zenkai.feature.race.RaceTextureUtil.deriveMask;
import static com.hmc.zenkai.feature.race.RaceTextureUtil.resourceExists;

/**
 * Descubre las capas de tinte numeradas de un item de cuerpo por convención, a partir de su
 * textura base: &lt;base&gt;_layer_0.png, _1.png, _2.png ... hasta el primer hueco. Las capas
 * totalmente transparentes se saltan (conservando la numeración de las demás).
 * Cada capa lleva opcionalmente un JSON hermano &lt;base&gt;_layer_&lt;n&gt;.json (campos en readConfig).
 * Por defecto una capa tiene su PROPIO color (elegible en la GUI, Namek/Arcosiano). Con
 * "follows" sigue al color de piel o de pelo con un tono propio (LayerTone): así el humano tiene
 * varias capas de piel que dependen de un solo color, y el pelo pintado sobre la cabeza.
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
                        String labelKey, int dy, int defaultRgb, int[] presets,
                        Follow follows, int toneRgb, int refRgb, boolean requiresHair) {

        /**
         * Etiqueta traducible: clave "layer.<namespace>.<base>.<n>" con fallback al
         * "label" literal del JSON. Añade la clave a en_us.json para traducirla.
         */
        public net.minecraft.network.chat.Component labelComponent() {
            return net.minecraft.network.chat.Component.translatableWithFallback(labelKey, label);
        }

        /**
         * ¿Se pinta esta capa sobre este item y este jugador? Una capa que sigue a la piel solo en
         * el cuerpo TEÑIBLE (el de "Default" ya trae sus tonos pintados); una que pide pelo, solo
         * si el jugador lleva peinado (pelo pintado sobre la cabeza, bajo el peinado 3D).
         */
        public boolean visibleFor(Player player, GeoLayerArmorItem item) {
            if (follows == Follow.SKIN && item.getColorChannel() != GeoLayerArmorItem.ColorChannel.SKIN) return false;
            if (requiresHair) {
                var visual = PlayerVisualAttachment.get(player);
                if (PlayerVisualAttachment.HAIR_NONE.equals(visual.getHairStyleId())) return false;
                var race = com.hmc.zenkai.feature.player.PlayerStatsAttachment.get(player).getRace();
                if (com.hmc.zenkai.feature.race.hair.HairCatalog.options(race).isEmpty()) return false;
            }
            return true;
        }

        /** Color RGB efectivo: override del jugador si existe, si no el default del JSON. */
        public int rgb(Player player) {
            if (follows != Follow.NONE) return LayerTone.apply(followedRgb(player), toneRgb, refRgb);
            // Una forma puede imponer el color de la capa (Golden/Black sobre el cuerpo de la forma final).
            var form = com.hmc.zenkai.feature.forms.FormDef.get(
                    player.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId());
            if (form != null && form.layerColor(index) >= 0) return form.layerColor(index);
            var att = PlayerVisualAttachment.get(player);
            if (att.hasLayerColor(index)) return att.getLayerColorRgb(index) & 0xFFFFFF;
            return defaultRgb & 0xFFFFFF;
        }

        /** ARGB opaco para el pase de render. */
        public int argb(Player player) { return 0xFF000000 | rgb(player); }

        /** Piel o pelo actuales, con lo que imponga la forma activa (layer_colors[0] / hair_rgb). */
        private int followedRgb(Player player) {
            var form = com.hmc.zenkai.feature.forms.FormDef.get(
                    player.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId());
            var att = PlayerVisualAttachment.get(player);
            if (follows == Follow.HAIR) {
                // Con el cambio de forma en curso, el mismo color que el pelo 3D (HairTransition).
                return com.hmc.zenkai.feature.race.HairResolver.currentHairRgb(player,
                        com.hmc.zenkai.client.hair.HairTransition.peek(player));
            }
            int formSkin = form == null ? -1 : form.layerColor(0);
            return formSkin >= 0 ? formSkin : att.getSkinColorRgb();
        }
    }

    /** A qué color sigue una capa: a ninguno (color propio), a la piel o al pelo. */
    public enum Follow { NONE, SKIN, HAIR }

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
            // Una capa SIN un solo píxel opaco no tiñe nada: no sale en la GUI ni gasta un pase de
            // render. Pasaba en la forma final del Arcosiano (Músculos, Detalle 2 y Cuernos vacíos:
            // el generador de capas las crea para cada forma y Freezer final no tiene cuernos), y
            // el jugador veía tres selectores que no hacían nada. Se SALTA sin cortar el bucle y
            // conservando su número: los colores guardados van por índice de capa.
            if (isFullyTransparent(tex)) continue;
            out.add(readConfig(base, tex, n));
        }
        return List.copyOf(out);
    }

    /** ¿No tiene ningún píxel con alfa? Un PNG ilegible cuenta como NO vacío (mejor un selector
     *  de más que esconder una capa válida). */
    private static boolean isFullyTransparent(ResourceLocation tex) {
        var res = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (res.isEmpty()) return false;
        try (var in = res.get().open();
             var img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if ((img.getPixelRGBA(x, y) >>> 24) != 0) return false;
                }
            }
            return true;
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("[Zenkai] No se pudo leer la capa {}: {}", tex, ex.toString());
            return false;
        }
    }

    /**
     * Lee &lt;base&gt;_layer_&lt;n&gt;.json:
     *   { "label":"Detail", "color":"F3ACB7", "presets":["F3ACB7","E75C72"] }
     *   - label:   nombre en la GUI (fallback "Layer n")
     *   - color:   color POR DEFECTO de la capa (hex). Se usa si el jugador no la ha tocado.
     *   - presets: paleta opcional para la GUI (hex). Vacío -> la GUI muestra solo swatch+picker.
     *   - follows: "skin" | "hair" -> la capa no tiene color propio: sigue a ese color con
     *              "tone" (su color cuando el seguido vale "ref"); ver LayerTone.
     *   - requires: "hair" -> solo si el jugador lleva peinado (pelo pintado sobre la cabeza).
     * Sin JSON: label "Layer n", default blanco, sin presets.
     */
    private static Layer readConfig(ResourceLocation base, ResourceLocation tex, int n) {
        String   label      = "Layer " + n;
        int      dy         = 0;      // desplazamiento vertical extra de su fila en la GUI (px)
        int      defaultRgb = 0xFFFFFF;
        int[]    presets    = EMPTY;
        Follow   follows    = Follow.NONE;
        int      toneRgb    = 0xFFFFFF;
        int      refRgb     = 0xFFFFFF;
        boolean  needsHair  = false;

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
                if (o.has("follows")) {
                    follows = Follow.valueOf(o.get("follows").getAsString().toUpperCase(java.util.Locale.ROOT));
                }
                if (o.has("tone")) toneRgb = parseHex(o.get("tone").getAsString());
                if (o.has("ref"))  refRgb  = parseHex(o.get("ref").getAsString());
                needsHair = o.has("requires") && "hair".equalsIgnoreCase(o.get("requires").getAsString());
            } catch (Exception ex) {
                Zenkai.LOGGER.warn("[Zenkai] Layer JSON inválido {}: {}", json, ex.toString());
            }
        }
        return new Layer(tex, n, label, labelKey, dy, defaultRgb, presets, follows, toneRgb, refRgb, needsHair);
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