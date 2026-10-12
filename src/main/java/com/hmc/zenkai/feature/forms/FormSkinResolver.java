package com.hmc.zenkai.feature.forms;

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
     * Forma que la VISTA PREVIA del editor de colores (BodyColorsScreen) enseña en lugar de la
     * real, o null. Solo cliente y solo mientras dura ese render: la pantalla lo pone justo antes
     * de dibujar al jugador y lo quita justo después (try/finally), así que ningún otro render
     * lo ve. Existe porque el editor se usa en la forma base y el jugador no tenía forma de ver
     * cómo quedan sus colores en las demás formas del Arcosiano (petición del usuario, 2026-10-11).
     * FormIds.BASE = el cuerpo de la raza sin override.
     */
    @org.jetbrains.annotations.Nullable
    private static ResourceLocation previewFormId;

    public static void setPreviewForm(@org.jetbrains.annotations.Nullable ResourceLocation formId) {
        previewFormId = formId;
    }

    /**
     * Devuelve SOLO overrides del BODY por forma.
     * Si la forma NO cambia body en ese slot, retorna ItemStack.EMPTY.
     * Nota: Aquí NO deberías meter pelo/halo/aura. Solo "body" (cuerpo gecko por forma),
     * especialmente para razas tipo Arcosian (Freezer) donde cambia el modelo.
     */
    public static ItemStack resolveBodyOverride(Player player, EquipmentSlot slot) {
        ResourceLocation formId = previewFormId != null ? previewFormId
                : player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();
        FormDef def = FormRegistry.get(formId);
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
