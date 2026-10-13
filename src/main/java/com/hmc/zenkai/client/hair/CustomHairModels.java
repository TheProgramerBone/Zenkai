package com.hmc.zenkai.client.hair;

import com.google.gson.JsonObject;
import com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes;
import com.hmc.zenkai.feature.race.hair.strand.StrandGeoWriter;
import com.hmc.zenkai.feature.race.hair.strand.StrandSet;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.util.List;
import java.util.Map;

/**
 * Modelos GeckoLib de los peinados importados, construidos AL VUELO en cada cliente a partir del
 * código que viaja en el visual del jugador (nada de archivos: lo ve cualquiera que tenga el mod).
 *
 * Mismo camino que GeckoLibCache al cargar un .geo.json (StrandGeoWriter -&gt; JSON -&gt; Model -&gt;
 * GeometryTree -&gt; BakedGeoModel), y el resultado se mete en el propio mapa de GeckoLib justo antes
 * de que GeoModel.getBakedModel lo pida: GeoModel guarda el modelo activo y su procesador de
 * animación en campos privados, y reimplementar esa función para servirlo desde fuera no es
 * posible sin duplicarla. Tras F3+T GeckoLib cambia de mapa y los modelos se rehacen solos la
 * primera vez que se vuelvan a pedir.
 */
public final class CustomHairModels {
    private CustomHairModels() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-CustomHair");
    private static BakedGeoModel empty;

    /**
     * Pasos intermedios de un cambio de forma horneados hace poco (LRU). Una variante fija se queda
     * en el mapa de GeckoLib como cualquier modelo; los pasos se van sacando para que muchos
     * jugadores transformándose no lo llenen (hasta 23 por par de variantes y peinado).
     */
    private static final int MAX_BLENDS = 256;
    private static final Map<ResourceLocation, Boolean> BLENDS = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, Boolean> eldest) {
            if (size() <= MAX_BLENDS) return false;
            try {
                GeckoLibCache.getBakedModels().remove(eldest.getKey());
            } catch (UnsupportedOperationException ignored) {
                // mapa inmutable (antes de la primera carga): no había nada que sacar
            }
            return true;
        }
    };

    /** Garantiza que GeckoLib tenga el modelo de este id (o uno vacío = calvo si no se puede). */
    public static void ensure(ResourceLocation id) {
        Map<ResourceLocation, BakedGeoModel> models = GeckoLibCache.getBakedModels();
        if (CustomHairCodes.isBlend(id)) BLENDS.put(id, Boolean.TRUE); // también refresca su uso
        if (models.containsKey(id)) return;
        BakedGeoModel baked = bake(id);
        try {
            models.put(id, baked);
        } catch (UnsupportedOperationException e) {
            // Antes de la primera carga de recursos el mapa es inmutable: no hay nada que pintar aún.
        }
    }

    private static BakedGeoModel bake(ResourceLocation id) {
        StrandSet set = CustomHairCodes.strandsFor(id);
        if (set != null) {
            try {
                return fromJson(id, StrandGeoWriter.write(id.getPath().replace('/', '_'), set).geo());
            } catch (RuntimeException e) {
                LOGGER.warn("[Zenkai] No se pudo construir el peinado importado {}: {}", id, e.toString());
            }
        }
        return emptyModel(id);
    }

    private static BakedGeoModel fromJson(ResourceLocation id, JsonObject json) {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(json, Model.class);
        return BakedModelFactory.getForNamespace(id.getNamespace()).constructGeoModel(GeometryTree.fromModel(model));
    }

    /** Esqueleto sin cubos: el pelo no se ve, pero GeckoLib encuentra sus huesos y no lanza. */
    private static BakedGeoModel emptyModel(ResourceLocation id) {
        if (empty == null) {
            empty = fromJson(id, StrandGeoWriter.write("zenkai_empty_hair", new StrandSet(null, List.of())).geo());
        }
        return empty;
    }
}
