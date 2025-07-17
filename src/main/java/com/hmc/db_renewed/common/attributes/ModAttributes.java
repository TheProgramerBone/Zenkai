package com.hmc.db_renewed.common.attributes;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.hmc.db_renewed.common.stats.StatAttributeApplier.applyAttribute;

public class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, "db_renewed");

    public static final DeferredHolder<Attribute, RangedAttribute> MAX_KI = ATTRIBUTES.register("max_ki",
            () -> new RangedAttribute("attribute.name.db_renewed.max_ki", 100.0D, 0.0D, 10000.0D));

    private static void applySpirit(Player player, int spirit) {
        double maxKi = 100 + (spirit - 10) * 5;
        applyAttribute(player, ModAttributes.MAX_KI.get(), maxKi);
    }

}