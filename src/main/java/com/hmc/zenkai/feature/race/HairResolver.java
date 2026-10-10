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
 * el propio peinado ("form_overrides"); todo se pinta con UN item genérico (zenkai:hair). La
 * decisión de qué peinado se ve es la función pura HairCatalog.effectiveStyle (con gametest).
 */
public final class HairResolver {

    private HairResolver() {}

    public static ItemStack resolveHairHead(Player player) {
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(player);
        if (!stats.isRaceChosen()) return ItemStack.EMPTY; // sin personaje creado aún → sin pelo

        PlayerFormAttachment form = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        // Oozaru/Super Oozaru: el mono gigante trae su propia cresta de pelo en el modelo
        // (geo/races/oozaru_player.geo.json); el peinado del jugador saldría flotando sobre ella.
        // Mismo corte que TailResolver y FaceOverlayGeoLayer.
        ResourceLocation formId = form.getFormId();
        if (FormIds.OOZARU.equals(formId) || FormIds.SUPER_OOZARU.equals(formId)) return ItemStack.EMPTY;

        PlayerVisualAttachment vis = player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        // Pelo procedimental (HairCode, aparcado): sin renderer todavía → calvo, sin romper.
        if (HairCode.isCustom(vis.getHairStyleId())) return ItemStack.EMPTY;

        FormDef def = FormRegistry.get(form.getFormId());

        // Escotilla de escape heredada: la forma fuerza un ITEM concreto para este peinado
        // ("hair_items" en su JSON). Ninguna forma la usa hoy; se mantiene para no romper un
        // datapack que sí lo haga. Lo normal es form_overrides en el JSON del peinado.
        if (def != null) {
            ItemStack forced = itemFrom(def.hairItem(vis.getHairStyleId()));
            if (!forced.isEmpty()) return tinted(forced, def);
        }

        HairStyleDef style = HairCatalog.effectiveStyle(vis.getHairStyle(),
                def == null ? null : def.id(), stats.getRace());
        if (style == null || style.representation() != HairStyleDef.Representation.GEO) return ItemStack.EMPTY;
        // Un .geo.json ausente haría lanzar a GeckoLib ("Unable to find model"): calvo en su lugar.
        if (style.model() == null || !RaceTextureUtil.resourceExists(style.model())) return ItemStack.EMPTY;

        ItemStack stack = HairStacks.of(style.id());
        // El color se fija en el stack (DYED_COLOR gana al canal HAIR en GeoLayerArmorRenderer)
        // salvo el caso normal "color de pelo del jugador sin forma que lo tiña".
        int rgb = HairCatalog.tintRgb(style, vis.getHairColorRgb(), def);
        boolean followsPlayer = style.color() == com.hmc.zenkai.feature.race.face.FaceLayer.Color.HAIR
                && (def == null || !def.tintsHair());
        if (!followsPlayer) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, false));
        return stack;
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
