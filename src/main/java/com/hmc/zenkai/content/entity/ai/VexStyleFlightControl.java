package com.hmc.zenkai.content.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Vuelo estilo Vex (vainilla): empuja velocidad DIRECTAMENTE hacia el punto deseado en vez de
 * leer Attributes.FLYING_SPEED/MOVEMENT_SPEED según mob.onGround() como hace el FlyingMoveControl
 * genérico de vainilla — ese daba tirones/sensación de caída real en juego (confirmado con la
 * Sombra). Copiado del patrón real de Vex.VexMoveControl (mismo constante 0.05 de suavizado), sin
 * la parte de girar hacia el objetivo: LookAtPlayerGoal/los goals de ataque ya gestionan la mirada.
 *
 * Extraído de ShadowCloneEntity.ShadowFlightMoveControl para que CUALQUIER ZenkaiDefaultMob con
 * "can_fly": true en su JSON lo reciba gratis (ver ZenkaiDefaultMob) — no solo la Sombra.
 *
 * No comprueba por sí mismo si el mob "tiene permiso" para volar ahora mismo (p. ej. la Sombra
 * sin la skill fly del dueño, ver ShadowCloneEntity#canFlyNow) — eso lo decide setNoGravity: sin
 * él, la gravedad tira hacia abajo mientras este control sigue empujando velocidad horizontal
 * hacia el objetivo, así que el mob simplemente camina/cae en esa dirección en vez de volar.
 */
public class VexStyleFlightControl extends MoveControl {
    public VexStyleFlightControl(Mob mob) { super(mob); }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) return;

        Vec3 delta = new Vec3(this.wantedX - mob.getX(), this.wantedY - mob.getY(), this.wantedZ - mob.getZ());
        double dist = delta.length();
        if (dist < mob.getBoundingBox().getSize()) {
            this.operation = Operation.WAIT;
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5));
            return;
        }
        mob.setDeltaMovement(mob.getDeltaMovement().add(delta.scale(this.speedModifier * 0.05 / dist)));
    }
}
