package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
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

import java.util.ArrayList;
import java.util.List;

public final class  HairResolver {

    private HairResolver() {}

    /** Grupo raíz de la cadena Saiyan: si el pelo propio de una forma SSJ no existe todavía
     *  para el peinado elegido, cae aquí antes de caer al pelo base sin transformar. */
    private static final String SSJ_ROOT_GROUP = "ssj1";

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

        // 1) ¿La forma activa sobrescribe el modelo de pelo para este peinado? Lo dice su JSON
        //    (escotilla de escape explícita, poco frecuente ahora que existe la cadena por
        //    convención del paso 2, pero un datapack puede seguir forzando un item concreto).
        FormDef def = FormRegistry.get(form.getFormId());
        ItemStack stack = (def == null) ? ItemStack.EMPTY : itemFrom(def.hairItem(hairStyle));

        // 2) Sin override explícito: cadena de convención por nombre "<grupo>_hair<peinado>"
        //    (ver ssjHairChain). Así un pelo nuevo (geo+textura con el nombre correcto) se
        //    aplica solo con crearlo, sin tocar hair_items en ningún JSON de forma.
        if (stack.isEmpty() && def != null) {
            for (ResourceLocation candidate : ssjHairChain(def, hairStyle)) {
                stack = itemFrom(candidate);
                if (!stack.isEmpty()) break;
            }
        }

        // 3) Sin nada de lo anterior: pelo base sin transformar. Solo hair1 por ahora.
        if (stack.isEmpty() && "hair1".equalsIgnoreCase(hairStyle)) {
            stack = ModItems.HAIR_1.get().getDefaultInstance();
        }
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // 4) Tinte de la forma: con el pelo en escala de grises, un modelo sirve para cualquiera.
        //    Se tiñe TAMBIÉN el pelo base, que es justo la gracia: SSJ1 puede reusar hair1
        //    y limitarse a declarar hair_rgb, sin necesitar un item propio.
        if (def != null && def.tintsHair()) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(def.hairRgb(), false));
        }
        return stack;
    }

    /**
     * Cadena de candidatos por convención de nombre "&lt;grupo&gt;_hair&lt;peinado&gt;" para el
     * pelo de una forma SSJ (ssj1/ssj2/ssj3/ssj4...). Cada tramo puede tener su propio pelo
     * distinto del de ssj1 (ese es el punto: "el pelo del ssj1 es distinto al del ssj2, ssj3 y
     * ssj4"), pero mientras el suyo propio no exista para el peinado elegido, cae al de ssj1
     * ANTES de caer al pelo base sin transformar — ver el paso 3 en resolveHairHead. Ejemplo
     * con forma=ssj2, peinado="hair2": ssj2_hair2 -> ssj2_hair1 -> ssj1_hair2 -> ssj1_hair1.
     * Devuelve lista vacía para cualquier forma fuera de la cadena SSJ (razas/formas que hoy no
     * declaran pelo propio por tramo, no las bloquea este chequeo).
     */
    private static List<ResourceLocation> ssjHairChain(FormDef def, String hairStyle) {
        String ownGroup = ssjGroupOf(def);
        if (ownGroup == null) return List.of();
        int n = hairNumber(hairStyle);
        List<ResourceLocation> out = new ArrayList<>(4);
        out.add(groupHairId(ownGroup, n));
        if (n != 1) out.add(groupHairId(ownGroup, 1));
        if (!ownGroup.equals(SSJ_ROOT_GROUP)) {
            out.add(groupHairId(SSJ_ROOT_GROUP, n));
            if (n != 1) out.add(groupHairId(SSJ_ROOT_GROUP, 1));
        }
        return out;
    }

    /** Grupo propio de pelo de una forma: el path de su id ("ssj1", "ssj2"...) si pertenece a
     *  la cadena Saiyan, null en cualquier otro caso (esas razas/formas no tienen grupo). */
    private static String ssjGroupOf(FormDef def) {
        String path = def.id().getPath();
        return path.startsWith("ssj") ? path : null;
    }

    /** Número de peinado ("hair2" -> 2). Cualquier cosa sin dígitos (o el propio "hair1") cae
     *  en 1, el peinado por defecto. */
    private static int hairNumber(String hairStyle) {
        String digits = hairStyle.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 1;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static ResourceLocation groupHairId(String group, int n) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, group + "_hair" + n);
    }

    /** Resuelve un id de item del datapack. Vacío si no existe: un JSON con una errata no
     *  debe tumbar el render, solo dejar el visual sin aplicar. Misma red de seguridad si el
     *  ítem SÍ existe pero su .geo.json todavía no (ver RaceSkinSlots.backedOrEmpty). */
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