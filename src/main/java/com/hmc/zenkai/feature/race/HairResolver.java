package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.hair.HairCatalog;
import com.hmc.zenkai.feature.race.hair.HairStacks;
import com.hmc.zenkai.feature.race.hair.HairStyleDef;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Qué pelo 3D (representación "geo") se pinta sobre la cabeza: devuelve el stack virtual que
 * HairGeoLayer inyecta en la ranura HEAD, o vacío (calvo / peinado "texture", que pinta
 * FaceOverlayGeoLayer sobre la propia cabeza).
 *
 * S5 (2026-10-01): antes cada peinado era un item registrado y la variante de cada forma SSJ se
 * buscaba por convención de nombres ("ssj2_hair2" -> "ssj2_hair1" -> "ssj1_hair2" -> ...). Ahora
 * el peinado es un id del catálogo de datapack (HairCatalog) y la variante por forma la declara
 * el propio peinado ("form_overrides"); se pinta cada cosa con UN item genérico (zenkai:hair). La
 * decisión de qué peinado se ve es la función pura HairCatalog.effectiveStyle (con gametest).
 */
public final class HairResolver {

    private HairResolver() {}

    /**
     * Punto del paso entre dos formas (t = 0 -&gt; from, 1 -&gt; to). Lo calcula el cliente
     * (client.hair.HairTransition); fuera de una transición, from = to = la forma activa.
     */
    public record FormBlend(ResourceLocation from, ResourceLocation to, float t) {
        public static FormBlend settled(ResourceLocation form) {
            return new FormBlend(form, form, 1f);
        }

        public boolean blending() {
            return !from.equals(to) && t > 0f && t < 1f;
        }
    }

    /**
     * Pasos del color en un cambio de forma (y del peso de los colores propios de los mechones):
     * cada uno es una textura-rampa (HairRampTextures). 48: con 24 el usuario pidió más (2026-10-12).
     */
    public static final int COLOR_STEPS = 48;

    public static ItemStack resolveHairHead(Player player) {
        return resolveHairHead(player, FormBlend.settled(
                player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId()));
    }

    public static ItemStack resolveHairHead(Player player, FormBlend blend) {
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(player);
        if (!stats.isRaceChosen()) return ItemStack.EMPTY; // sin personaje creado aún → sin pelo

        PlayerFormAttachment form = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        // Oozaru/Super Oozaru: el mono gigante trae su propia cresta de pelo en el modelo
        // (geo/races/oozaru_player.geo.json); el peinado del jugador saldría flotando sobre ella.
        // Mismo corte que TailResolver y FaceOverlayGeoLayer.
        ResourceLocation formId = form.getFormId();
        if (isOozaru(formId)) return ItemStack.EMPTY;
        // Hacia o desde el Oozaru el cuerpo entero cambia de golpe: sin paso intermedio de pelo.
        if (isOozaru(blend.from()) || isOozaru(blend.to())) blend = FormBlend.settled(formId);

        PlayerVisualAttachment vis = player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        FormDef from = FormRegistry.get(blend.from());
        FormDef to = FormRegistry.get(blend.to());
        float t = blend.blending() ? blend.t() : 1f;
        if (!blend.blending()) from = to;
        // Lo que no se puede mezclar (otro modelo, un item forzado) cambia a mitad de camino.
        FormDef nearest = t < 0.5f ? from : to;

        // Peinado importado (biblioteca del jugador): el código viaja en su visual y este cliente
        // construye el modelo al vuelo (CustomHairModels), sin archivos. Ilegible o no permitido
        // en el mundo -> calvo, sin romper.
        if (HairCode.isCustom(vis.getHairStyleId())) {
            var entry = vis.activeCustomHair();
            if (entry == null || !HairCatalog.customAllowed(stats.getRace(), vis)) return ItemStack.EMPTY;
            ResourceLocation model = com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.model(
                    entry.code(), idOf(from), idOf(to), t);
            if (model == null) return ItemStack.EMPTY;
            ItemStack stack = HairStacks.of(model);
            // Mismo tinte que un peinado del catálogo con "color": "hair".
            int own = vis.getHairColorRgb() & 0xFFFFFF;
            dye(stack, tints(from) ? from.hairRgb() : own, tints(to) ? to.hairRgb() : own, t,
                    !tints(from) && !tints(to));
            strandColors(stack, vis, from, to, t);
            return stack;
        }

        // Escotilla de escape heredada: la forma fuerza un ITEM concreto para este peinado
        // ("hair_items" en su JSON). Ninguna forma la usa hoy; se mantiene para no romper un
        // datapack que sí lo haga. Lo normal es form_overrides en el JSON del peinado.
        if (nearest != null) {
            ItemStack forced = itemFrom(nearest.hairItem(vis.getHairStyleId()));
            if (!forced.isEmpty()) return tinted(forced, nearest);
        }

        HairStyleDef styleFrom = HairCatalog.effectiveStyle(vis.getHairStyle(), idOf(from), stats.getRace());
        HairStyleDef styleTo = HairCatalog.effectiveStyle(vis.getHairStyle(), idOf(to), stats.getRace());
        HairStyleDef style = t < 0.5f ? styleFrom : styleTo;
        if (style == null) return ItemStack.EMPTY;
        ItemStack stack;
        if (style.representation() == HairStyleDef.Representation.CODE) {
            // Peinado por código (los de DragonMineZ que trae el mod): mismo camino que un importado,
            // con la variante de la forma activa (la que el form_override no haya sustituido ya).
            // Si las dos formas usan el mismo código, sus variantes se mezclan paso a paso.
            boolean sameCode = styleFrom != null && styleTo != null && styleFrom.id().equals(styleTo.id());
            ResourceLocation model = sameCode
                    ? com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.model(style.code(), idOf(from), idOf(to), t)
                    : com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.model(style.code(), idOf(nearest));
            if (model == null) return ItemStack.EMPTY;
            stack = HairStacks.of(model);
            strandColors(stack, vis, from, to, t);
        } else {
            if (style.representation() != HairStyleDef.Representation.GEO) return ItemStack.EMPTY;
            // Un .geo.json ausente haría lanzar a GeckoLib ("Unable to find model"): calvo en su lugar.
            if (style.model() == null || !RaceTextureUtil.resourceExists(style.model())) return ItemStack.EMPTY;
            stack = HairStacks.of(style.id());
        }
        // El color se fija en el stack (DYED_COLOR gana al canal HAIR en GeoLayerArmorRenderer)
        // salvo el caso normal "color de pelo del jugador sin forma que lo tiña". En un cambio de
        // forma el color va de uno a otro aunque el modelo cambie de golpe.
        int playerRgb = vis.getHairColorRgb();
        HairStyleDef a = styleFrom != null ? styleFrom : style, b = styleTo != null ? styleTo : style;
        dye(stack, HairCatalog.tintRgb(a, playerRgb, from), HairCatalog.tintRgb(b, playerRgb, to), t,
                followsPlayer(a, from) && followsPlayer(b, to));
        return stack;
    }

    /**
     * Colores propios de los mechones (StrandColors) en el stack, según el interruptor del jugador.
     * Se mantienen en TODAS las formas (decisión del usuario, 2026-10-12: los mechones negros de un
     * peinado blanco siguen negros en SSJ); la forma solo tiñe los mechones sin color propio, porque
     * el color de pelo que reciben las columnas "de pelo" ya es el de la forma (dye). DMZ, en cambio,
     * pinta el peinado entero del color de la forma.
     */
    private static void strandColors(ItemStack stack, PlayerVisualAttachment vis, FormDef from, FormDef to, float t) {
        if (vis.isHairStrandColors()) {
            stack.set(com.hmc.zenkai.registry.ModDataComponents.HAIR_STRAND_COLORS.get(), COLOR_STEPS);
        }
    }

    /**
     * Color de pelo "efectivo" en este momento, con la transición de forma en curso: el que siguen
     * las capas pintadas de la cabeza ("follows": "hair", RaceLayerDiscovery). Fuera de una
     * transición: el hair_rgb de la forma si tiñe, si no el del jugador.
     */
    public static int currentHairRgb(Player player, FormBlend blend) {
        int own = player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).getHairColorRgb() & 0xFFFFFF;
        FormDef from = FormRegistry.get(blend.from()), to = FormRegistry.get(blend.to());
        int b = tints(to) ? to.hairRgb() & 0xFFFFFF : own;
        if (!blend.blending()) return b;
        int a = tints(from) ? from.hairRgb() & 0xFFFFFF : own;
        return com.hmc.zenkai.feature.race.hair.strand.HairRampColors.mix(a, b, blend.t());
    }

    private static boolean isOozaru(ResourceLocation id) {
        return FormIds.OOZARU.equals(id) || FormIds.SUPER_OOZARU.equals(id);
    }

    private static ResourceLocation idOf(FormDef def) {
        return def == null ? null : def.id();
    }

    private static boolean tints(FormDef def) {
        return def != null && def.tintsHair();
    }

    private static boolean followsPlayer(HairStyleDef style, FormDef def) {
        return style.color() == com.hmc.zenkai.feature.race.face.FaceLayer.Color.HAIR && !tints(def);
    }

    /** Tiñe el stack con la mezcla de dos colores (cuantizada), salvo que el pelo siga al jugador. */
    private static void dye(ItemStack stack, int rgbFrom, int rgbTo, float t, boolean followsPlayer) {
        if (followsPlayer) return;
        float q = Math.round(t * COLOR_STEPS) / (float) COLOR_STEPS;
        int rgb = com.hmc.zenkai.feature.race.hair.strand.HairRampColors.mix(rgbFrom, rgbTo, q);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb & 0xFFFFFF, false));
    }

    private static ItemStack tinted(ItemStack stack, FormDef def) {
        if (def.tintsHair()) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(def.hairRgb(), false));
        return stack;
    }

    /** Item de un datapack. Vacío si no existe o si su .geo.json falta (ver RaceSkinSlots.backedOrEmpty). */
    private static ItemStack itemFrom(ResourceLocation id) {
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return ItemStack.EMPTY;
        if (item instanceof GeoLayerArmorItem geo && !RaceTextureUtil.resourceExists(geo.getModelPath())) {
            return ItemStack.EMPTY;
        }
        return item.getDefaultInstance();
    }
}
