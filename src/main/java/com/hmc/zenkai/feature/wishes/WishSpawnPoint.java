package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.content.entity.overworld.ShenLongEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Dónde aparece lo que concede un deseo.
 * ÚNICO sitio donde se decide. El cálculo estaba copiado en el deseo de mascota y en el del
 * aldeano, y ahora lo necesita también el de revivir jugador: a la tercera copia, cambiar el
 * radio de búsqueda del dragón en un sitio y no en los otros dos deja de ser hipotético.
 * POR QUÉ SE BUSCA A SHENLONG Y NO SE USA AL JUGADOR SIN MÁS: lo concedido tiene que salir
 * de donde está la magia. Apareciendo junto al invocador, el efecto se lee como que lo trajo
 * él; apareciendo bajo el dragón, se lee como que lo trajo el deseo. El respaldo existe
 * porque Shenlong puede haberse ido ya cuando el jugador confirma en la pantalla.
 */
public final class WishSpawnPoint {
    private WishSpawnPoint() {}

    /** Radio de búsqueda del dragón. Generoso a propósito: Shenlong es enorme y su origen
     *  puede quedar lejos del jugador aunque lo tenga encima. */
    private static final double DRAGON_SEARCH = 48.0;

    /** Ocho direcciones alrededor del punto base, para buscar hueco donde soltar a alguien. */
    private static final double[][] RING = {
            {1.5, 0}, {-1.5, 0}, {0, 1.5}, {0, -1.5},
            {1.2, 1.2}, {-1.2, 1.2}, {1.2, -1.2}, {-1.2, -1.2}
    };

    /** Punto de origen del deseo: bajo Shenlong si sigue ahí, o junto al invocador. */
    public static Vec3 origin(ServerLevel level, ServerPlayer invoker) {
        ShenLongEntity dragon = level.getEntitiesOfClass(
                        ShenLongEntity.class, invoker.getBoundingBox().inflate(DRAGON_SEARCH))
                .stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(invoker)))
                .orElse(null);

        return dragon != null
                ? new Vec3(dragon.getX(), dragon.getY(), dragon.getZ())
                : new Vec3(invoker.getX() + 1.0, invoker.getY(), invoker.getZ() + 1.0);
    }

    /**
     * Sitio con los pies en el suelo para depositar a un JUGADOR, cerca del invocador.
         * No se usa el origen del dragón ni se suelta desde el aire como con las mascotas: a una
     * mascota se le pone slow falling y ya está, pero a un jugador que acaba de reaparecer
     * caerle encima de un techo o dentro de una pared le arruina el momento — y si el
     * invocador está en una cueva, +5 de altura es piedra maciza.
         * El invocador está de pie en un sitio que funciona, así que se prueba a su alrededor y se
     * usa el primer hueco libre. Si no hay ninguno (invocando desde un armario), se cae a su
     * misma posición: solaparse un instante es feo, pero quedarse atrapado en un bloque es peor.
     */
    public static Vec3 besidePlayer(ServerLevel level, ServerPlayer invoker) {
        for (double[] off : RING) {
            Vec3 candidate = new Vec3(invoker.getX() + off[0], invoker.getY(), invoker.getZ() + off[1]);
            if (level.noCollision(invoker.getBoundingBox().move(
                    candidate.x - invoker.getX(), 0, candidate.z - invoker.getZ()))) {
                return candidate;
            }
        }
        return invoker.position();
    }
}