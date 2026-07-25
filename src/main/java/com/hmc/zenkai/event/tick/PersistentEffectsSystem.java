package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;

/** Efectos que deben existir mientras exista el flag, pase lo que pase. */
public final class PersistentEffectsSystem {
    private PersistentEffectsSystem() {}

    public static void tick(TickCtx c) {
        if (c.att().isImmortal()) {
            c.p().addEffect(new MobEffectInstance(ModEffects.IMMORTALITY,
                    MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
        } else {
            c.p().removeEffect(ModEffects.IMMORTALITY);
        }

        // Marca Majin: persistente como la inmortalidad. Si el flag está puesto, el efecto
        // se aplica aunque lo quiten con leche o /effect clear; solo la muerte lo borra.
        if (c.visual().isMajinControlled() && !c.p().hasEffect(ModEffects.MAJIN)) {
            c.p().addEffect(new MobEffectInstance(ModEffects.MAJIN,
                    MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
        }
    }
}