package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Cola de Saiyan: mirror de HairResolver, pero para un overlay independiente en vez del
 * pelo. Dos condiciones INDEPENDIENTES:
 *   - PlayerStatsAttachment.hasTail() — ¿tiene cola? Servicio de Kami (quitar/hacer crecer),
 *     por defecto true (ver PlayerStateFlags.hasTail). Gameplay: también es una de las tres
 *     condiciones de Oozaru (ver OozaruConditions).
 *   - PlayerVisualAttachment.getTailStyleId() — "loose"/"waist". Capricho cosmético SIN
 *     costo, elegible desde la rueda (mantener X) una vez que hay cola (ver WheelMenu).
 *
 * Color: a diferencia del pelo, la cola NUNCA sigue una elección del jugador — cada forma tiene
 * SIEMPRE su propio tono fijo, base incluida. Por eso aquí SIEMPRE se pone un DYED_COLOR en el
 * stack (nunca se deja "sin tinte"), a diferencia de HairResolver que solo lo pone cuando la
 * forma activa lo declara explícitamente. Prioridad (de más a menos específico):
 *   1. FormDef.tailRgb() de la forma activa, si lo declara (escotilla de escape explícita).
 *   2. FormDef.hairRgb() de la forma activa, si lo declara — por defecto la cola CONCUERDA con
 *      el pelo de la transformación (dorado en SSJ, azul en SSJ Blue, etc.), sin tener que
 *      duplicar el valor en cada JSON de forma con tail_rgb.
 *   3. DEFAULT_TAIL_RGB — color natural, sin forma activa (o una que no toca el pelo tampoco).
 */
public final class TailResolver {

    private TailResolver() {}

    /** Color natural de la cola sin transformar (ninguna forma activa, o la activa no declara
     *  tail_rgb) — indicado por el usuario. */
    public static final int DEFAULT_TAIL_RGB = 0x8B4512;

    public static ItemStack resolveTail(Player player) {
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(player);
        if (!stats.isRaceChosen() || stats.getRace() != Race.SAIYAN) return ItemStack.EMPTY;
        if (!stats.hasTail()) return ItemStack.EMPTY;

        // Oozaru/Super Oozaru YA incluyen la cola en su propio modelo de mono gigante (aunque
        // hoy ese modelo todavía sea el cuerpo humano/saiyan escalado, ver geo-models-pendientes)
        // — este overlay independiente se apagaría, no la duplicaría.
        PlayerFormAttachment form = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        ResourceLocation formId = form.getFormId();
        if (FormIds.OOZARU.equals(formId) || FormIds.SUPER_OOZARU.equals(formId)) return ItemStack.EMPTY;

        PlayerVisualAttachment vis = PlayerVisualAttachment.get(player);
        var item = "waist".equals(vis.getTailStyleId()) ? ModItems.TAIL_WAIST : ModItems.TAIL_LOOSE;
        ItemStack stack = item.get().getDefaultInstance();

        // Sistema general "sin modelo GeckoLib -> vuelve al cuerpo por defecto" (ver
        // RaceSkinSlots.backedOrEmpty): los ítems de cola están registrados desde ya, pero su
        // .geo.json/textura pueden no existir todavía mientras se modelan con calma.
        GeoLayerArmorItem geo = (GeoLayerArmorItem) stack.getItem();
        if (!RaceTextureUtil.resourceExists(geo.getModelPath())) return ItemStack.EMPTY;

        FormDef def = FormRegistry.get(formId);
        int rgb = DEFAULT_TAIL_RGB;
        if (def != null) {
            if (def.tintsTail()) rgb = def.tailRgb();
            else if (def.tintsHair()) rgb = def.hairRgb();
        }
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, false));

        return stack;
    }
}
