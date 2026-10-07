package com.hmc.zenkai.client.gui;

import com.hmc.zenkai.client.FaceRemapTextures;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.RaceBodyResolver;
import com.hmc.zenkai.feature.race.face.FaceCatalog;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.FaceLayer;
import com.hmc.zenkai.feature.race.face.FacePartDef;
import com.hmc.zenkai.feature.race.face.FaceRegion;
import com.hmc.zenkai.feature.race.face.RaceFaceTraits;
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
import org.jetbrains.annotations.Nullable;

import static com.hmc.zenkai.feature.race.RaceTextureUtil.resourceExists;

/**
 * Miniatura 2D de la cara de un jugador, compuesta EN VIVO con las mismas texturas y colores que
 * usa el render 3D: la región de cara de la cabeza de su raza/forma (FaceRegions; la canónica
 * es x16-31/y16-31 de la textura de 256) + sus capas de color de cuerpo + cada capa de cada rasgo.
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

    /** Lado de la cara en texels. */
    private static final int FACE_TEXELS = FaceRegion.FACE;

    /** Lado en píxeles de GUI de una miniatura a esta escala (16 texels × escala). */
    public static int size(int scale) { return FACE_TEXELS * scale; }

    public static void draw(GuiGraphics g, Player player, @Nullable FaceCategory overrideCategory,
                            @Nullable ResourceLocation overrideId, int x, int y, int scale) {
        PlayerVisualAttachment vis = PlayerVisualAttachment.get(player);
        ItemStack head = RaceBodyResolver.resolve(player, EquipmentSlot.HEAD);
        GeoLayerArmorItem geo = (head.getItem() instanceof GeoLayerArmorItem gi
                && resourceExists(gi.getModelPath())) ? gi : null;
        // La base sale de la región de cara de SU modelo (FaceRegions; la arcosiana está en
        // (16,48)). Los rasgos se dibujan siempre desde la cara canónica de su PNG: es lo mismo
        // que el render 3D recoloca a esa región, sin generar texturas para la GUI.
        FaceRegion region = geo != null ? FaceRemapTextures.regionFor(geo.getModelPath()) : FaceRegion.CANONICAL;
        drawBase(g, player, vis, head, geo, region, x, y, scale);

        FormDef form = FormDef.get(player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId());
        Race race = PlayerStatsAttachment.get(player).getRace();
        // Pelo plano ("texture"): su flequillo cae sobre la cara, igual que en FaceOverlayGeoLayer.
        if (!com.hmc.zenkai.feature.race.HairCode.isCustom(vis.getHairStyleId())) {
            var hair = com.hmc.zenkai.feature.race.hair.HairCatalog.effectiveStyle(vis.getHairStyle(),
                    form == null ? null : form.id(), race);
            if (hair != null && hair.representation() == com.hmc.zenkai.feature.race.hair.HairStyleDef.Representation.TEXTURE
                    && resourceExists(hair.texture())) {
                blitFace(g, hair.texture(), com.hmc.zenkai.feature.race.hair.HairCatalog.tintRgb(
                        hair, vis.getHairColorRgb(), form), FaceRegion.CANONICAL, x, y, scale);
            }
        }
        for (FaceCategory c : FaceCategory.values()) {
            ResourceLocation id = (c == overrideCategory) ? overrideId : vis.getFacePart(c);
            FacePartDef def = FaceCatalog.get(id);
            if (def == null) continue;
            // Variantes por raza (race_layers) también en la cara canónica.
            for (FaceLayer layer : def.layersFor(race)) {
                if (!RaceFaceTraits.shows(race, c, layer) || !resourceExists(layer.texture())) continue;
                // Mismo color que FaceOverlayGeoLayer (trazo de ojos con brow_rgb, boca Namek oscura).
                int rgb = layer.resolveRgb(vis, form, -1, c == FaceCategory.EYES);
                if (layer.color() == FaceLayer.Color.SKIN) rgb = RaceFaceTraits.shadeSkin(race, rgb);
                blitFace(g, layer.texture(), rgb, FaceRegion.CANONICAL, x, y, scale);
            }
        }
    }

    /** Cara base: la textura de cabeza de la raza (o forma) con su tinte, o la skin vanilla. */
    private static void drawBase(GuiGraphics g, Player player, PlayerVisualAttachment vis, ItemStack head,
                                 @Nullable GeoLayerArmorItem geo, FaceRegion region, int x, int y, int scale) {
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
        blitFace(g, geo.resolveTexture(head), rgb, region, x, y, scale);
        // Capas de color de cuerpo (detalle Namek, marcas Arcosianas...), como BodyTintGeoLayer.
        if (geo.hasBodyTint()) {
            for (RaceLayerDiscovery.Layer layer : RaceLayerDiscovery.layersFor(geo)) {
                if (layer.index() == 0) continue;
                blitFace(g, layer.texture(), layer.argb(player), region, x, y, scale);
            }
        }
    }

    /**
     * Recorta una región de cara de una textura, la estira al cuadro de la miniatura (una región
     * que no sea de 16×16 se escala igual que el render 3D escala los rasgos a ella) y la pinta
     * multiplicada por rgb.
     */
    private static void blitFace(GuiGraphics g, ResourceLocation tex, int rgb, FaceRegion r,
                                 int x, int y, int scale) {
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        // UV normalizadas por texSize: vale para cualquier PNG del mismo aspecto (256, 512...).
        g.blit(tex, x, y, size(scale), size(scale), r.x0(), r.y0(), r.width(), r.height(), r.texSize(), r.texSize());
        g.setColor(1f, 1f, 1f, 1f);
    }
}
