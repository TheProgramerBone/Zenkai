package com.hmc.zenkai.event;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.EffectImmune;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** Niega cualquier efecto de poción a las entidades marcadas con {@link EffectImmune}. */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class EffectImmunityHook {
    private EffectImmunityHook() {}

    @SubscribeEvent
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof EffectImmune) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
