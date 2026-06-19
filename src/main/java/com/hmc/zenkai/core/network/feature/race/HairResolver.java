package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.content.item.ModItems;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.core.network.feature.forms.FormIds;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class HairResolver {

    private HairResolver() {}

    public static ItemStack resolveHairHead(Player player) {
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(player);
        if (!stats.isRaceChosen()) return ItemStack.EMPTY; // sin personaje creado aún → sin pelo
        Race race = stats.getRace();
        if (race != Race.SAIYAN && race != Race.HUMAN) return ItemStack.EMPTY;

        PlayerFormAttachment form = player.getData(DataAttachments.PLAYER_FORM.get());
        PlayerVisualAttachment vis = player.getData(DataAttachments.PLAYER_VISUAL.get());

        ResourceLocation formId = form.getFormId();
        String hairStyle = vis.getHairStyleId(); // "hair0", "hair1", ...

        // 1) hair0 = calvo
        if (hairStyle == null || hairStyle.isEmpty()
                || "hair0".equalsIgnoreCase(hairStyle)
                || "bald".equalsIgnoreCase(hairStyle)) {
            return ItemStack.EMPTY;
        }

        // 2) Solo soportamos hair1 por ahora
        boolean isHair1 = "hair1".equalsIgnoreCase(hairStyle);
        if (!isHair1) return ItemStack.EMPTY;

        // 3) Resolver por forma
        if (FormIds.SSJ1.equals(formId)) {
            return ModItems.SSJ1_HAIR1.get().getDefaultInstance();
        }

        // BASE (y cualquier otra forma que no cambie hair aún)
        return ModItems.HAIR_1.get().getDefaultInstance();
    }
}