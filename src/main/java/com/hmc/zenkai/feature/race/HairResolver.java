package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

public final class  HairResolver {

    private HairResolver() {}

    public static ItemStack resolveHairHead(Player player) {
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(player);
        if (!stats.isRaceChosen()) return ItemStack.EMPTY; // sin personaje creado aún → sin pelo
        Race race = stats.getRace();
        if (race != Race.SAIYAN && race != Race.HUMAN) return ItemStack.EMPTY;


        PlayerFormAttachment form = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        PlayerVisualAttachment vis = player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        String hairStyle = vis.getHairStyleId();
        // Pelo procedimental: lo pinta un RenderLayer propio, no un item de GeckoLib.
        // Hasta que exista, se devuelve vacío (calvo) en vez de romper.
        if (HairCode.isCustom(hairStyle)) return ItemStack.EMPTY;

        // hair0 = calvo
        if (hairStyle == null || hairStyle.isEmpty()
                || "hair0".equalsIgnoreCase(hairStyle)
                || "bald".equalsIgnoreCase(hairStyle)) {
            return ItemStack.EMPTY;
        }

        // 1) ¿La forma activa sobrescribe el modelo de pelo para este peinado? Lo dice su JSON.
        FormDef def = FormRegistry.get(form.getFormId());
        ItemStack stack = (def == null) ? ItemStack.EMPTY : itemFrom(def.hairItem(hairStyle));

        // 2) Sin override: pelo base. Solo hair1 por ahora.
        if (stack.isEmpty() && "hair1".equalsIgnoreCase(hairStyle)) {
            stack = ModItems.HAIR_1.get().getDefaultInstance();
        }
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // 3) Tinte de la forma: con el pelo en escala de grises, un modelo sirve para cualquiera.
        //    Se tiñe TAMBIÉN el pelo base, que es justo la gracia: SSJ1 puede reusar hair1
        //    y limitarse a declarar hair_rgb, sin necesitar un item propio.
        if (def != null && def.tintsHair()) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(def.hairRgb(), false));
        }
        return stack;
    }

    /** Resuelve un id de item del datapack. Vacío si no existe: un JSON con una errata no
     *  debe tumbar el render, solo dejar el visual sin aplicar. */
    private static ItemStack itemFrom(ResourceLocation id) {
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? ItemStack.EMPTY : item.getDefaultInstance();
    }
}