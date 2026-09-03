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
        //
        // A DIFERENCIA de IMMORTALITY de arriba, esta rama "else" es solo una red de seguridad,
        // NO el sitio que de verdad apaga Majin. ImmortalityEffect nunca escribe att.setImmortal
        // de vuelta (mira su propio archivo: solo cura, no toca el flag), así que para
        // inmortalidad SÍ basta con que este tick quite el efecto la próxima vez que corra.
        // MajinEffect SÍ tiene sync en la otra dirección (applyEffectTick pone el flag en true
        // en cuanto ve el efecto presente) — y ese tick de vainilla corre ANTES que este
        // (PlayerTickEvent.Post), dentro del MISMO tick de juego. Si algo solo apaga el flag y
        // espera a que ESTA rama quite el efecto en un tick posterior, el efecto sigue vivo
        // cuando corre el tick de vainilla anterior a este, `applyEffectTick` lo ve presente y
        // vuelve a poner el flag en true ANTES de que esta rama llegue a ejecutarse — el
        // comando/reset "no hacía nada" en la práctica. Cualquier código que active/desactive
        // Majin DEBE pasar por MajinEffect.setControlled(sp, value), que quita el
        // MobEffectInstance de forma SÍNCRONA en la misma llamada (ver su javadoc) — esta rama
        // solo cubre el caso de que el efecto se haya quitado por FUERA de ese camino (leche,
        // /effect clear) sin que nadie haya tocado el flag.
        if (c.visual().isMajinControlled()) {
            if (!c.p().hasEffect(ModEffects.MAJIN)) {
                c.p().addEffect(new MobEffectInstance(ModEffects.MAJIN,
                        MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
            }
        } else {
            c.p().removeEffect(ModEffects.MAJIN);
        }
    }
}