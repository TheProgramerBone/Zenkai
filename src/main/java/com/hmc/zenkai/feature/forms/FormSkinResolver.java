package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.race.RaceTextureUtil;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FormSkinResolver {

    private FormSkinResolver() {}

    /**
     * Devuelve SOLO overrides del BODY por forma.
     * Si la forma NO cambia body en ese slot, retorna ItemStack.EMPTY.
     * Nota: Aquí NO deberías meter pelo/halo/aura. Solo "body" (cuerpo gecko por forma),
     * especialmente para razas tipo Arcosian (Freezer) donde cambia el modelo.
     */
    public static ItemStack resolveBodyOverride(Player player, EquipmentSlot slot) {
        PlayerFormAttachment form = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        FormDef def = FormRegistry.get(form.getFormId());
        if (def == null) return ItemStack.EMPTY;

        // "head" / "chest" / "legs" / "feet" en el JSON de la forma.
        String key = switch (slot) {
            case HEAD  -> "head";
            case CHEST -> "chest";
            case LEGS  -> "legs";
            case FEET  -> "feet";
            default    -> null;
        };
        if (key == null) return ItemStack.EMPTY;

        ResourceLocation id = def.bodyItem(key);
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return ItemStack.EMPTY;

        // Sistema general "sin modelo GeckoLib -> vuelve al cuerpo por defecto" (ver
        // RaceSkinSlots.backedOrEmpty): el ítem puede estar registrado (código ya escrito
        // para una forma futura) sin que su .geo.json exista todavía en disco. Sin este
        // chequeo, un datapack que apuntara body_items a un ítem así crashearía el render en
        // vez de caer al cuerpo de la raza como si la forma no lo sobrescribiera.
        if (item instanceof GeoLayerArmorItem geo && !RaceTextureUtil.resourceExists(geo.getModelPath())) {
            return ItemStack.EMPTY;
        }
        return item.getDefaultInstance();
    }
}
