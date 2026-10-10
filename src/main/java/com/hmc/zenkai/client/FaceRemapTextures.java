package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.face.FaceRegion;
import com.hmc.zenkai.feature.race.face.FaceRegions;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recoloca un rasgo facial (pintado en la cara canónica (16,16)-(31,31) de un PNG de 256) a la
 * región de cara de un modelo concreto ({@link FaceRegions}). Genera UNA textura dinámica por
 * pareja (PNG, región) la primera vez que se pide y la reutiliza; con la región canónica
 * devuelve el PNG original sin generar nada (Human/Saiyan/Namek no pagan nada).
 *
 * Por qué una textura nueva y no tocar UVs: el rasgo se pinta re-renderizando el MISMO modelo
 * de la cabeza con otra textura (ver FaceOverlayGeoLayer); la UV la decide la geo y no se puede
 * desplazar por pasada sin un RenderType propio, que ya rompió Iris/Oculus una vez.
 *
 * Solo hilo de render (lo llaman las capas de render y las miniaturas de la GUI). La caché se
 * vacía en cada recarga de recursos (RaceTextureCacheReload).
 */
public final class FaceRemapTextures {
    private FaceRemapTextures() {}

    private record Key(ResourceLocation src, FaceRegion region) {}

    private static final Map<Key, ResourceLocation> REMAPPED = new HashMap<>();
    private static final Set<Key> MISSING = new HashSet<>();
    private static final List<ResourceLocation> REGISTERED = new ArrayList<>();
    private static final Map<ResourceLocation, FaceRegion> BAKED_REGION = new HashMap<>();
    private static int counter;

    /**
     * Color de los rasgos de cara "skin" (boca, nariz) en este modelo: el tono de piel del jugador
     * ({@code skinRgb}) salvo que {@link FaceRegions#skinFeatureLayer} diga que la cara está pintada con
     * otra capa (forma final arcosiana: capa 2 = blanco de la armadura), y entonces el color efectivo
     * de ESA capa (que ya incluye lo que imponga la forma, p. ej. Golden/Black).
     */
    public static int skinFeatureRgb(@Nullable com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem geo,
                                     net.minecraft.world.entity.player.Player player, int skinRgb) {
        if (geo == null) return skinRgb;
        int idx = FaceRegions.skinFeatureLayer(geo.getModelPath());
        if (idx <= 0) return skinRgb;
        // Primero lo que imponga la forma activa (Golden/Black no tienen capa: la textura ya es el color
        // final y solo declaran el color de boca/nariz en layer_colors).
        var form = com.hmc.zenkai.feature.forms.FormDef.get(player.getData(
                com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId());
        if (form != null && form.layerColor(idx) >= 0) return form.layerColor(idx);
        for (var layer : com.hmc.zenkai.feature.race.layer.RaceLayerDiscovery.layersFor(geo)) {
            if (layer.index() == idx) return layer.rgb(player);
        }
        return skinRgb;
    }

    /**
     * Región de cara de un modelo: la tabla manual manda; sin entrada, la esquina leída del
     * modelo horneado (16×16); sin modelo cargado, la canónica.
     */
    public static FaceRegion regionFor(@Nullable ResourceLocation geoPath) {
        FaceRegion manual = FaceRegions.manual(geoPath);
        if (manual != null) return manual;
        if (geoPath == null) return FaceRegion.CANONICAL;
        FaceRegion cached = BAKED_REGION.get(geoPath);
        if (cached != null) return cached;
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(geoPath);
        // Modelo aún no cargado: respaldo SIN cachear, para no quedarse con la canónica hasta la
        // siguiente recarga de recursos.
        if (model == null) return FaceRegion.CANONICAL;
        FaceRegion region = bakedRegion(model);
        BAKED_REGION.put(geoPath, region);
        return region;
    }

    /**
     * Esquina de la cara FRONTAL leída del modelo horneado: quad NORTH del primer cubo del hueso
     * "head", que BakedModelFactory construye en (u+d, v+d). Antes de la tabla manual esto era lo
     * único que sabía que la cara arcosiana está en (16,48) (miniaturas que enseñaban el casco).
     */
    private static FaceRegion bakedRegion(BakedGeoModel model) {
        GeoBone bone = model.getBone("head").orElse(null);
        if (bone == null || bone.getCubes().isEmpty()) return FaceRegion.CANONICAL;
        for (GeoQuad quad : bone.getCubes().get(0).quads()) {
            if (quad == null || quad.direction() != Direction.NORTH) continue;
            float u = Float.MAX_VALUE, v = Float.MAX_VALUE;
            for (GeoVertex vertex : quad.vertices()) {
                u = Math.min(u, vertex.texU());
                v = Math.min(v, vertex.texV());
            }
            int x = Math.round(u * FaceRegion.CANONICAL_TEX), y = Math.round(v * FaceRegion.CANONICAL_TEX);
            int last = FaceRegion.CANONICAL_TEX - FaceRegion.FACE;
            if (x < 0 || y < 0 || x > last || y > last) return FaceRegion.CANONICAL;
            return new FaceRegion(x, y, x + FaceRegion.FACE - 1, y + FaceRegion.FACE - 1);
        }
        return FaceRegion.CANONICAL;
    }

    /**
     * Textura del rasgo recolocada a esa región, o null si el PNG no existe en este cliente
     * (entonces la capa no se pinta, como antes). Región canónica → el propio {@code src}.
     */
    @Nullable
    public static ResourceLocation remap(ResourceLocation src, FaceRegion region) {
        if (region.isCanonical()) return src;
        Key key = new Key(src, region);
        ResourceLocation done = REMAPPED.get(key);
        if (done != null) return done;
        if (MISSING.contains(key)) return null;

        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(src);
        if (res.isEmpty()) {
            MISSING.add(key);
            return null;
        }
        NativeImage out;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            out = new NativeImage(region.texSize(), region.texSize(), true); // calloc: transparente
            // El PNG del rasgo puede no ser de 256 (un resource pack a 512): sus texels se buscan
            // en proporción, igual que las UV normalizadas de GeckoLib.
            float sx = img.getWidth() / (float) FaceRegion.CANONICAL_TEX;
            float sy = img.getHeight() / (float) FaceRegion.CANONICAL_TEX;
            for (int ty = region.y0(); ty <= region.y1(); ty++) {
                for (int tx = region.x0(); tx <= region.x1(); tx++) {
                    int[] s = region.sourceTexel(tx, ty);
                    out.setPixelRGBA(tx, ty, img.getPixelRGBA((int) (s[0] * sx), (int) (s[1] * sy)));
                }
            }
        } catch (Exception e) {
            Zenkai.LOGGER.warn("[Zenkai] No se pudo recolocar la cara {} a {}: {}", src, region, e.toString());
            MISSING.add(key);
            return null;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "face_remap/" + (counter++));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
        REGISTERED.add(id);
        REMAPPED.put(key, id);
        return id;
    }

    /** Recarga de recursos: los PNG o los modelos pueden haber cambiado. Libera las texturas. */
    public static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation id : REGISTERED) textures.release(id);
        REGISTERED.clear();
        REMAPPED.clear();
        MISSING.clear();
        BAKED_REGION.clear();
    }
}
