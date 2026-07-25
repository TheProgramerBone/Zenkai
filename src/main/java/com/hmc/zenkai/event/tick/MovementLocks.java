package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Dueño de los tres modificadores de MOVEMENT_SPEED del mod y de la mecánica de LOCK.
 * Tener los ids aquí evita que dos sistemas creen modificadores con el mismo nombre
 * o se pisen el del otro.
 */
public final class MovementLocks {
    private MovementLocks() {}

    /** Multiplicador de velocidad por stats (lo escribe GroundMovementSystem). */
    public static final ResourceLocation MOVE_MOD_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "speed_mult");

    static final ResourceLocation TRANSFORM_LOCK_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "transform_lock");

    static final ResourceLocation DOWNED_LOCK_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "downed_lock");

    /** Quita el multiplicador de velocidad por stats (no toca los locks). */
    public static void clearSpeedMult(Player p) {
        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveAttr != null) moveAttr.removeModifier(MOVE_MOD_ID);
    }

    /** Lock de transformación: inmoviliza mientras dura la animación. */
    public static void transform(Player p, boolean lock) {
        apply(p, lock, TRANSFORM_LOCK_ID);
    }

    /**
     * Lock de derribado: mismo anclaje que el de transformación pero con su propio id, para
     * inmovilizar al jugador mientras está acostado. El daño SÍ le llega (no es invulnerable).
     */
    public static void downed(Player p, boolean lock) {
        apply(p, lock, DOWNED_LOCK_ID);
    }

    /**
     * LOCK real (servidor): corta input (xxa/zza/jump), quita sprint, ancla X/Z al tick
     * anterior (xo/zo) y corta la delta horizontal.
     */
    private static void apply(Player p, boolean lock, ResourceLocation id) {
        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);

        if (!lock) {
            if (moveAttr != null && moveAttr.getModifier(id) != null) {
                moveAttr.removeModifier(id);
            }
            return;
        }

        if (moveAttr != null && moveAttr.getModifier(id) == null) {
            moveAttr.addTransientModifier(new AttributeModifier(
                    id, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        p.setSprinting(false);
        p.xxa = 0.0F;
        p.zza = 0.0F;
        p.setJumping(false);
        p.setPos(p.xo, p.getY(), p.zo);   // ancla horizontal (server-only)
        var v = p.getDeltaMovement();
        p.setDeltaMovement(0.0, v.y, 0.0); // corta inercia horizontal
        p.hurtMarked = true;
    }
}