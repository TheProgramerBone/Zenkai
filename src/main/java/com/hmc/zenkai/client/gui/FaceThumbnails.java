package com.hmc.zenkai.client.gui;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.RaceBodyResolver;
import com.hmc.zenkai.feature.race.face.FaceCatalog;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.FaceLayer;
import com.hmc.zenkai.feature.race.face.FacePartDef;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.feature.race.layer.RaceLayerDiscovery;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hmc.zenkai.feature.race.RaceTextureUtil.resourceExists;

/**
 * Miniatura 2D de la cara de un jugador, compuesta EN VIVO con las mismas texturas y colores que
 * usa el render 3D: la región de cara (16×16 texels, x16-31/y16-31 de la textura de 256) de la
 * cabeza de su raza/forma + sus capas de color de cuerpo + cada capa de cada rasgo.
 *
 * Sin assets de miniatura que mantener: un rasgo nuevo del catálogo sale en la GUI con su
 * miniatura correcta solo por existir, y con los colores del jugador (decisión C8,
 * .claude/pendiente/sistema-visual-personajes-diseno.md).
 *
 * El rasgo de UNA categoría puede sustituirse ({@code overrideCategory}/{@code overrideId}) para
 * enseñar una opción de la lista en contexto: con los demás rasgos que ya lleva el jugador.
 *
 * Límite conocido: es la cara PLANA, sin la luz de entidad ni la arista del cubo; el preview 3D
 * de la pantalla es el que enseña el resultado real.
 */
public final class FaceThumbnails {
    private FaceThumbnails() {}

    /** Lado de la cara en texels y tamaño de referencia de las texturas de cabeza (cara-doc §4). */
    private static final int FACE_TEXELS = 16, TEX_SIZE = 256;
    /** Origen de la cara de casi todas las cabezas (uv [0,0]). NO de la arcosiana: ver faceOrigin. */
    private static final int[] DEFAULT_FACE_ORIGIN = {16, 16};
    private static final Map<ResourceLocation, int[]> FACE_ORIGIN_CACHE = new ConcurrentHashMap<>();

    /** Lado en píxeles de GUI de una miniatura a esta escala (16 texels × escala). */
    public static int size(int scale) { return FACE_TEXELS * scale; }

    /** Llamar al recargar recursos (los modelos de GeckoLib pueden cambiar). */
    public static void clearCache() { FACE_ORIGIN_CACHE.clear(); }

    public static void draw(GuiGraphics g, Player player, @Nullable FaceCategory overrideCategory,
                            @Nullable ResourceLocation overrideId, int x, int y, int scale) {
        PlayerVisualAttachment vis = PlayerVisualAttachment.get(player);
        ItemStack head = RaceBodyResolver.resolve(player, EquipmentSlot.HEAD);
        GeoLayerArmorItem geo = (head.getItem() instanceof GeoLayerArmorItem gi
                && resourceExists(gi.getModelPath())) ? gi : null;
        int[] origin = geo != null ? faceOrigin(geo.getModelPath()) : DEFAULT_FACE_ORIGIN;
        drawBase(g, player, vis, head, geo, origin, x, y, scale);

        FormDef form = FormDef.get(player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId());
        Race race = PlayerStatsAttachment.get(player).getRace();
        // Pelo plano ("texture"): su flequillo cae sobre la cara, igual que en FaceOverlayGeoLayer.
        if (!com.hmc.zenkai.feature.race.HairCode.isCustom(vis.getHairStyleId())) {
            var hair = com.hmc.zenkai.feature.race.hair.HairCatalog.effectiveStyle(vis.getHairStyle(),
                    form == null ? null : form.id(), race);
            if (hair != null && hair.representation() == com.hmc.zenkai.feature.race.hair.HairStyleDef.Representation.TEXTURE
                    && resourceExists(hair.texture())) {
                blitFace(g, hair.texture(), com.hmc.zenkai.feature.race.hair.HairCatalog.tintRgb(
                        hair, vis.getHairColorRgb(), form), origin, x, y, scale);
            }
        }
        for (FaceCategory c : FaceCategory.values()) {
            ResourceLocation id = (c == overrideCategory) ? overrideId : vis.getFacePart(c);
            FacePartDef def = FaceCatalog.get(id);
            if (def == null) continue;
            // Las capas de la raza ya vienen pintadas en la cara de SU modelo (race_layers).
            for (FaceLayer layer : def.layersFor(race)) {
                if (!resourceExists(layer.texture())) continue;
                blitFace(g, layer.texture(), layer.resolveRgb(vis, form), origin, x, y, scale);
            }
        }
    }

    /**
     * Esquina (u, v), en una textura de 256, de la cara FRONTAL de la cabeza de ese modelo, leída
     * del modelo horneado de GeckoLib (quad NORTH del primer cubo del hueso "head", que
     * BakedModelFactory construye en (u+d, v+d)). No se puede suponer (16,16): la cabeza
     * arcosiana usa uv [0,16], su cara está en (16,48) y en (16,16) está el frente del CASCO —
     * las miniaturas de Arcosiano enseñaban el casco morado en vez de la cara (2026-10-01).
     */
    private static int[] faceOrigin(ResourceLocation modelPath) {
        int[] cached = FACE_ORIGIN_CACHE.get(modelPath);
        if (cached != null) return cached;
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(modelPath);
        // Modelo aún no cargado: respaldo SIN cachear, para no quedarse con (16,16) hasta la
        // siguiente recarga de recursos.
        if (model == null) return DEFAULT_FACE_ORIGIN;
        return FACE_ORIGIN_CACHE.computeIfAbsent(modelPath, path -> {
            GeoBone bone = model.getBone("head").orElse(null);
            if (bone == null || bone.getCubes().isEmpty()) return DEFAULT_FACE_ORIGIN;
            for (GeoQuad quad : bone.getCubes().get(0).quads()) {
                if (quad == null || quad.direction() != Direction.NORTH) continue;
                float u = Float.MAX_VALUE, v = Float.MAX_VALUE;
                for (GeoVertex vertex : quad.vertices()) {
                    u = Math.min(u, vertex.texU());
                    v = Math.min(v, vertex.texV());
                }
                return new int[] {Math.round(u * TEX_SIZE), Math.round(v * TEX_SIZE)};
            }
            return DEFAULT_FACE_ORIGIN;
        });
    }

    /** Cara base: la textura de cabeza de la raza (o forma) con su tinte, o la skin vanilla. */
    private static void drawBase(GuiGraphics g, Player player, PlayerVisualAttachment vis, ItemStack head,
                                 @Nullable GeoLayerArmorItem geo, int[] origin, int x, int y, int scale) {
        int px = size(scale);
        if (!vis.shouldRenderRaceSkin() && player instanceof AbstractClientPlayer acp) {
            PlayerFaceRenderer.draw(g, acp.getSkin(), x, y, px);
            return;
        }
        if (geo == null) {
            g.fill(x, y, x + px, y + px, 0xFF000000 | vis.getSkinColorRgb());
            return;
        }
        // Mismo color que GeoLayerArmorRenderer.getRenderColor para el pase base.
        int rgb;
        if (head.has(DataComponents.DYED_COLOR)) rgb = DyedItemColor.getOrDefault(head, 0xFFFFFF);
        else rgb = switch (geo.getColorChannel()) {
            case SKIN -> vis.getSkinColorRgb();
            case HAIR -> vis.getHairColorRgb();
            default   -> 0xFFFFFF;
        };
        blitFace(g, geo.resolveTexture(head, vis.getSkinPreset()), rgb, origin, x, y, scale);
        // Capas de color de cuerpo (detalle Namek, marcas Arcosianas...), como BodyTintGeoLayer.
        if (geo.hasBodyTint()) {
            for (RaceLayerDiscovery.Layer layer : RaceLayerDiscovery.layersFor(geo)) {
                if (layer.index() == 0) continue;
                blitFace(g, layer.texture(), layer.argb(player), origin, x, y, scale);
            }
        }
    }

    /** Recorta la región de cara (en {@code origin}) de una textura y la pinta multiplicada por rgb. */
    private static void blitFace(GuiGraphics g, ResourceLocation tex, int rgb, int[] origin,
                                 int x, int y, int scale) {
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        // UV normalizadas por TEX_SIZE: vale para cualquier PNG del mismo aspecto (256, 512...).
        g.blit(tex, x, y, size(scale), size(scale), origin[0], origin[1], FACE_TEXELS, FACE_TEXELS, TEX_SIZE, TEX_SIZE);
        g.setColor(1f, 1f, 1f, 1f);
    }
}
