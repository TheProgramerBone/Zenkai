package com.hmc.zenkai.event.tick;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Sin raza no hay features: apaga y corta el tick. */
public final class RaceGateSystem {
    private RaceGateSystem() {}

    /** @return true si hay que cortar el tick. */
    public static boolean handle(TickCtx c) {
        if (c.att().isRaceChosen()) return false;

        Player p = c.p();
        c.att().setChargingKi(false);
        c.form().resetAll();

        // Sin esto, tras /zenkai reset full el jugador conservaba mayfly y la velocidad
        // de vuelo vieja (este gate retornaba antes de la sección de vuelo).
        if (!p.isCreative() && !p.isSpectator()) {
            var ab = p.getAbilities();
            if (ab.mayfly || ab.flying) {
                ab.mayfly = false;
                ab.flying = false;
                ab.setFlyingSpeed(0.05F); // default vanilla
                p.onUpdateAbilities();
            }
        }
        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveAttr != null) {
            moveAttr.removeModifier(MovementLocks.MOVE_MOD_ID);
            moveAttr.removeModifier(MovementLocks.TRANSFORM_LOCK_ID);
        }
        return true;
    }
}