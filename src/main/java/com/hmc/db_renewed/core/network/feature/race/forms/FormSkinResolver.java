package com.hmc.db_renewed.core.network.feature.race.forms;

import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.player.PlayerFormAttachment;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class FormSkinResolver {

    private FormSkinResolver() {}

    /**
     * Devuelve SOLO overrides del BODY por forma.
     * Si la forma NO cambia body en ese slot, retorna ItemStack.EMPTY.
     *
     * Nota: Aquí NO deberías meter pelo/halo/aura. Solo "body" (cuerpo gecko por forma),
     * especialmente para razas tipo Arcosian (Freezer) donde cambia todo el modelo.
     */
    public static ItemStack resolveBodyOverride(Player player, EquipmentSlot slot) {
        Race race = PlayerStatsAttachment.get(player).getRace();
        PlayerFormAttachment form = player.getData(DataAttachments.PLAYER_FORM.get());

        // Ya es ResourceLocation, no se parsea
        ResourceLocation formId = form.getFormId();

        // ============================
        // ARCOSIAN / FREEZER (ejemplo)
        // ============================
        // Cuando tengas los items, descomentas y ajustas a tus ModItems reales.
        //
        // if (race == Race.ARCOSIAN) {
        //     if (FormIds.SECOND_FORM.equals(formId)) {
        //         return switch (slot) {
        //             case HEAD  -> ModItems.ARCOSIAN_SECOND_FORM_HELMET.get().getDefaultInstance();
        //             case CHEST -> ModItems.ARCOSIAN_SECOND_FORM_CHESTPLATE.get().getDefaultInstance();
        //             case LEGS  -> ModItems.ARCOSIAN_SECOND_FORM_LEGGINGS.get().getDefaultInstance();
        //             case FEET  -> ModItems.ARCOSIAN_SECOND_FORM_BOOTS.get().getDefaultInstance();
        //             default    -> ItemStack.EMPTY;
        //         };
        //     }
        // }

        // Saiyan SSJ1 NO cambia body (solo pelo), así que aquí siempre vacío.
        // Otras formas que sí cambien body pueden entrar aquí después.

        return ItemStack.EMPTY;
    }
}
