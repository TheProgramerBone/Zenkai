package com.hmc.zenkai.feature.race.face;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * TABLA A MANO de dónde está la cara en la textura de cada modelo de cuerpo (pedido del usuario,
 * 2026-10-01: "plenamente en el código", sin datapack). Los rasgos faciales se dibujan una sola
 * vez en la cara canónica (16,16)-(31,31) y el render los recoloca a la región de la tabla.
 *
 * Motivo: la cabeza arcosiana usa uv [0,16] (su cara está en (16,48), en (16,16) está el frente
 * del casco) y cada forma de Freezer con geo propia puede volver a mover la cara. Antes había que
 * redibujar cada rasgo por raza (race_layers); ahora basta una línea aquí por modelo.
 *
 * Cómo rellenar una entrada para un modelo nuevo:
 *  1. Abrir la textura del cuerpo (la del pase base, p. ej. ..._layer_0.png) y localizar la cara
 *     frontal de la cabeza. Si dudas, pinta un píxel de color, renderiza y mira dónde sale.
 *  2. Apuntar el PRIMER y el ÚLTIMO píxel de la cara (esquinas inclusivas), en píxeles de esa
 *     textura. Si el PNG no es de 256, pasar su lado como quinto número.
 *  3. Si parte de la cara queda tapada por geometría (el casco arcosiano), se puede marcar solo
 *     la zona visible: los rasgos se escalan a ella (vecino más cercano). Para medir qué tapa:
 *     python .claude/workbench/tools/face_occlusion.py &lt;geo&gt;
 *
 * Sin entrada: el cliente lee la esquina de la cara del modelo horneado de GeckoLib (16×16 desde
 * el quad NORTH del primer cubo del hueso "head") y, si no puede, usa la canónica. La tabla
 * SIEMPRE manda sobre esa lectura automática.
 */
public final class FaceRegions {
    private FaceRegions() {}

    private static final Map<ResourceLocation, FaceRegion> MANUAL = new HashMap<>();

    /**
     * EXCEPCIONES de color: modelos cuya cara NO está pintada con el color de la piel (capa 0) sino con
     * el de otra capa tintable. Los rasgos de color "skin" (boca, nariz) toman el color de esa capa en
     * vez del tono de piel del jugador. Sin entrada = la capa 0, que es lo normal.
     */
    private static final Map<ResourceLocation, Integer> SKIN_FEATURE_LAYER = new HashMap<>();

    static {
        // Primera forma arcosiana: cabeza con uv [0,16] → cara en (16,48)-(31,63). Con el modelo
        // actual el casco tapa las filas 0-7 de la cara (ly 48-55): un rasgo en la mitad superior
        // queda oculto. Al rediseñar el modelo, ajustar aquí (p. ej. solo la franja visible).
        put("geo/races/arcosian_first_form_player.geo.json", 16, 48, 31, 63);

        // Segunda forma: quad NORTH de la cabeza en uv [22,16] (sobre 128) → (44,32)-(59,47) en el
        // PNG de 256. El casco tapa las filas 0-7, como en la primera forma.
        put("geo/races/arcosian_second_form_player.geo.json", 44, 32, 59, 47);

        // Tercera forma: quad NORTH de la cabeza en uv [0,28] (sobre 128) → (0,56)-(15,71) en el
        // PNG de 256. El casco tapa las filas 0-7.
        put("geo/races/arcosian_third_form_player.geo.json", 0, 56, 15, 71);

        // Forma final: quad NORTH de la cabeza en uv [0,14] (sobre 128) → (0,28)-(15,41) en el
        // PNG de 256. El casco (head#1) tapa las columnas 2-13 de las filas 0-6.
        put("geo/races/arcosian_final_form_player.geo.json", 0, 28, 15, 41);
        // La cara de la forma final está pintada con el blanco de la armadura (capa 2), no con la piel:
        // su boca y su nariz se tiñen con ese color.
        putSkinLayer("geo/races/arcosian_final_form_player.geo.json", 2);

        // Golden y Black Freezer: copias del modelo de la forma final (mismo geo, textura propia), así que
        // la cara está en el mismo sitio. Su cara no es piel: boca y nariz toman el color "4" que la forma
        // declara en layer_colors (no hay capa 4: la textura ya es el color final).
        put("geo/races/arcosian_golden_form_player.geo.json", 0, 28, 15, 41);
        put("geo/races/arcosian_black_form_player.geo.json", 0, 28, 15, 41);
        putSkinLayer("geo/races/arcosian_golden_form_player.geo.json", 4);
        putSkinLayer("geo/races/arcosian_black_form_player.geo.json", 4);

        // Formas con geo propia aún sin crear (rutas ya registradas en ModItems). Descomentar y
        // poner los números cuando exista cada modelo:
        // put("geo/forms/arcosian_fifth_form_player.geo.json",       x0, y0, x1, y1);
        // put("geo/forms/arcosian_golden_fifth_form_player.geo.json", x0, y0, x1, y1);
        // put("geo/forms/arcosian_black_fifth_form_player.geo.json",  x0, y0, x1, y1);
        // put("geo/forms/namek_orange_player.geo.json",              x0, y0, x1, y1);
        // put("geo/races/majin_player.geo.json",                     x0, y0, x1, y1);
    }

    private static void putSkinLayer(String geoPath, int layerIndex) {
        SKIN_FEATURE_LAYER.put(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, geoPath), layerIndex);
    }

    /** Capa tintable cuyo color pinta los rasgos "skin" de la cara de este modelo; 0 = la piel del jugador. */
    public static int skinFeatureLayer(@Nullable ResourceLocation geoPath) {
        return geoPath == null ? 0 : SKIN_FEATURE_LAYER.getOrDefault(geoPath, 0);
    }

    private static void put(String geoPath, int x0, int y0, int x1, int y1) {
        MANUAL.put(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, geoPath), new FaceRegion(x0, y0, x1, y1));
    }

    @SuppressWarnings("unused") // para modelos con textura que no sea de 256
    private static void put(String geoPath, int x0, int y0, int x1, int y1, int texSize) {
        MANUAL.put(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, geoPath), new FaceRegion(x0, y0, x1, y1, texSize));
    }

    /** La región escrita a mano para ese modelo, o null si no tiene entrada. */
    @Nullable
    public static FaceRegion manual(@Nullable ResourceLocation geoPath) {
        return geoPath == null ? null : MANUAL.get(geoPath);
    }
}
