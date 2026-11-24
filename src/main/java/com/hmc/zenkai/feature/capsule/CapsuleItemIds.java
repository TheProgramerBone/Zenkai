package com.hmc.zenkai.feature.capsule;

import com.hmc.zenkai.content.item.CapsuleItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Reconoce el id de una cápsula escrito como texto en un NBT (ver {@link CapsuleScan#containsCapsule}). Primero un filtro
 * barato por prefijo y luego el registro, para no confundir otro ítem que empiece igual.
 */
final class CapsuleItemIds {
    private CapsuleItemIds() {}

    private static final String PREFIX = "zenkai:capsule_";

    static boolean isCapsuleId(String s) {
        if (!s.startsWith(PREFIX)) return false;
        ResourceLocation rl = ResourceLocation.tryParse(s);
        return rl != null && BuiltInRegistries.ITEM.get(rl) instanceof CapsuleItem;
    }
}
