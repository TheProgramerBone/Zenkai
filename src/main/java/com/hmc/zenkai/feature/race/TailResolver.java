package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Cola de Saiyan: mirror de HairResolver, pero para un overlay independiente en vez del
 * pelo. Dos condiciones INDEPENDIENTES:
 *   - PlayerStatsAttachment.hasTail() — ¿tiene cola? Servicio de Kami (quitar/hacer crecer),
 *     por defecto true (ver PlayerStateFlags.hasTail). Gameplay: también es una de las tres
 *     condiciones de Oozaru (ver OozaruConditions).
 *   - PlayerVisualAttachment.getTailStyleId() — "loose"/"waist". Capricho cosmético SIN
 *     costo, elegible desde la rueda (mantener X) una vez que hay cola (ver WheelMenu).
 */
public final class TailResolver {

    private TailResolver() {}

    public static ItemStack resolveTail(Player player) {
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(player);
        if (!stats.isRaceChosen() || stats.getRace() != Race.SAIYAN) return ItemStack.EMPTY;
        if (!stats.hasTail()) return ItemStack.EMPTY;

        PlayerVisualAttachment vis = PlayerVisualAttachment.get(player);
        var item = "waist".equals(vis.getTailStyleId()) ? ModItems.TAIL_WAIST : ModItems.TAIL_LOOSE;
        ItemStack stack = item.get().getDefaultInstance();

        // Sistema general "sin modelo GeckoLib -> vuelve al cuerpo por defecto" (ver
        // RaceSkinSlots.backedOrEmpty): los ítems de cola están registrados desde ya, pero su
        // .geo.json/textura pueden no existir todavía mientras se modelan con calma.
        GeoLayerArmorItem geo = (GeoLayerArmorItem) stack.getItem();
        return RaceTextureUtil.resourceExists(geo.getModelPath()) ? stack : ItemStack.EMPTY;
    }
}
