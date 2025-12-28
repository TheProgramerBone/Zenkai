package com.hmc.db_renewed.core.network.feature.race.hairs;

import com.hmc.db_renewed.content.item.ModItems;
import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.player.PlayerFormAttachment;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.core.network.feature.player.PlayerVisualAttachment;
import com.hmc.db_renewed.core.network.feature.race.forms.FormIds;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class HairResolver {

    private HairResolver() {}

    public static ItemStack resolveHairHead(Player player) {
        Race race = PlayerStatsAttachment.get(player).getRace();
        if (race != Race.SAIYAN) return ItemStack.EMPTY;

        PlayerFormAttachment form = player.getData(DataAttachments.PLAYER_FORM.get());
        PlayerVisualAttachment vis = player.getData(DataAttachments.PLAYER_VISUAL.get());

        ResourceLocation formId = form.getFormId();
        String hairStyle = vis.getHairStyleId(); // "hair1", "hair2", etc.

        // -----------------------
        // BASE
        // -----------------------
        if (FormIds.BASE.equals(formId)) {
            if ("hair1".equalsIgnoreCase(hairStyle)) {
                return ModItems.HAIR_1.get().getDefaultInstance();
            }
            // Futuro: hair2/hair3...
            return ModItems.HAIR_1.get().getDefaultInstance();
        }

        // -----------------------
        // SSJ1
        // -----------------------
        if (FormIds.SSJ1.equals(formId)) {
            if ("hair1".equalsIgnoreCase(hairStyle)) {
                return ModItems.SSJ1_HAIR1.get().getDefaultInstance();
            }
            // Futuro: ssj1_hair2/...
            return ModItems.SSJ1_HAIR1.get().getDefaultInstance();
        }

        // Si estás en otras formas (kaioken, mystic, etc.)
        // Decide tu política:
        // - Opción A: mantener pelo base (lo más común)
        // - Opción B: no renderizar pelo si no está definido
        //
        // Aquí dejo Opción A (mantener pelo base) porque es lo que suele esperarse en Saiyan.
        if ("hair1".equalsIgnoreCase(hairStyle)) {
            return ModItems.HAIR_1.get().getDefaultInstance();
        }
        return ModItems.HAIR_1.get().getDefaultInstance();
    }
}