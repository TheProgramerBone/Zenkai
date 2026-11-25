package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.content.entity.technique.FalseMoonEntity;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * LUNA FALSA (técnica {@code PhysicalTechnique.FALSE_MOON}): crea una esfera flotante ({@link FalseMoonEntity}) y decide si un
 * ser que la mira cuenta como «viendo la luna llena». Es el SEGUNDO disparador del Oozaru, junto a la luna real
 * ({@link OozaruConditions}): ni dimensión, ni hora, ni fase, ni cielo abierto; solo importa que exista una luna falsa cerca y
 * que el ser la tenga delante. La condición de ser Saiyan con cola se sigue exigiendo en OozaruConditions (la técnica solo la
 * aprende un Saiyan, pero cualquier Saiyan con cola que mire la esfera se transforma, sea quien sea el lanzador).
 *
 * API sin jugador ({@link #isLookingAtFalseMoon(LivingEntity)}) para que un NPC Saiyan pueda usarla cuando exista su Oozaru.
 */
public final class FalseMoon {
    private FalseMoon() {}

    /** Distancia máxima, en bloques, a la que la esfera «se ve». */
    public static final double VISIBLE_RANGE = 96.0;
    /** Cuánto tiene que apuntar la mirada hacia ella: coseno del ángulo máximo (aprox. 30 grados). */
    private static final double LOOK_COS = 0.866;
    /** Altura de la luna sobre el lanzador y distancia horizontal por delante. */
    private static final double HOVER_HEIGHT = 18.0;
    private static final double HOVER_AHEAD = 22.0;

    /** ¿Puede lanzarla ahora? Solo un Saiyan. */
    public static boolean canCast(ServerPlayer sp) {
        return PlayerStatsAttachment.get(sp).getRace() == Race.SAIYAN;
    }

    /** Ejecución de la técnica (PhysicalCombatServer.execute, case FALSE_MOON): arroja la esfera y la deja subir y flotar. */
    public static void cast(ServerPlayer sp) {
        Vec3 look = sp.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 start = sp.getEyePosition().add(look.scale(0.8));
        Vec3 hover = sp.position().add(flat.scale(HOVER_AHEAD)).add(0, HOVER_HEIGHT, 0);
        FalseMoonEntity moon = new FalseMoonEntity(ModEntities.FALSE_MOON.get(), sp.level());
        moon.configure(hover, FalseMoonEntity.DEFAULT_LIFE_TICKS);
        moon.setPos(start.x, start.y, start.z);
        sp.level().addFreshEntity(moon);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    /** ¿Hay una luna falsa en el campo de visión de este ser? Misma dimensión, dentro del alcance y delante de su mirada. */
    public static boolean isLookingAtFalseMoon(LivingEntity e) {
        if (e.level().isClientSide()) return false;
        AABB box = e.getBoundingBox().inflate(VISIBLE_RANGE);
        List<FalseMoonEntity> moons = e.level().getEntitiesOfClass(FalseMoonEntity.class, box);
        if (moons.isEmpty()) return false;
        Vec3 eye = e.getEyePosition();
        Vec3 look = e.getLookAngle();
        for (FalseMoonEntity m : moons) {
            Vec3 to = m.position().add(0, m.getBbHeight() * 0.5, 0).subtract(eye);
            double d = to.length();
            if (d < 1.0E-3 || d > VISIBLE_RANGE) continue;
            if (look.dot(to.scale(1.0 / d)) >= LOOK_COS) return true;
        }
        return false;
    }
}
