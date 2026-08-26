package com.hmc.zenkai.content.entity.technique;

import com.hmc.zenkai.registry.ModDamageTypes;
import com.hmc.zenkai.registry.ModGameRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Zona persistente del efecto LINGERING (TechniqueEffect): daño periódico en un radio, nace
 * donde impacta la técnica (KiProjectileEntity.detonate) y se apaga sola al agotar su duración.
 * PURAMENTE SERVIDOR: registrada en ModEntities con clientTrackingRange(0), así que nunca se
 * manda al cliente y no hace falta renderer — lo que se VE son las partículas y el sonido que
 * el propio servidor manda con sendParticles, exactamente igual que KiProjectileEntity.detonate
 * pinta su explosión sin que la entidad del proyectil necesite representarla.
 * Sin física, sin colisión, invulnerable: es un marcador de posición que solo ticka su propio
 * radio, no un objeto con el que el mundo pueda interactuar.
 */
public class KiLingeringZoneEntity extends Entity {

    /** Golpea cada medio segundo, no cada tick: un DoT que hurt() 20 veces por segundo es
     *  indistinguible de un golpe único gigante salvo por el log de combate. */
    private static final int HIT_INTERVAL = 10;
    private static final int PARTICLE_INTERVAL = 5;

    private double damagePerHit = 1.0;
    private double radius = 2.0;
    private int life = 60;
    private UUID ownerId;
    /** Solo para atribuir el mensaje de muerte (ver ModDamageTypes.KI_FIRE); NO se persiste,
     *  igual que el resto del estado de esta entidad — si vuelve de un guardado a mitad de vida
     *  simplemente pierde la atribución y el daño cae al genérico, nada crítico. */
    private LivingEntity owner;

    public KiLingeringZoneEntity(EntityType<? extends KiLingeringZoneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    /** Configuración al nacer (solo servidor). */
    public void configure(LivingEntity owner, double damagePerHit, double radius, int lifeTicks) {
        this.ownerId = owner != null ? owner.getUUID() : null;
        this.owner = owner;
        this.damagePerHit = damagePerHit;
        this.radius = radius;
        this.life = lifeTicks;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return; // nunca se ejecuta en cliente: no llega

        if (tickCount % PARTICLE_INTERVAL == 0) {
            sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.1, getZ(),
                    (int) Math.max(3, radius * 3), radius * 0.5, 0.15, radius * 0.5, 0.01);
        }

        // Mismo gate que el resto del daño ki (KiProjectileEntity.detonate): con la gamerule
        // apagada la zona sigue naciendo y se ve, pero no hace daño — igual que un impacto
        // directo o un área con la regla desactivada.
        if (tickCount % HIT_INTERVAL == 0 && ModGameRules.enableKiDamage(sl.getServer())) {
            for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(position(), radius * 2, radius * 2, radius * 2),
                    t -> t.isAlive() && !t.getUUID().equals(ownerId))) {
                if (position().distanceTo(target.position()) > radius) continue;
                // magic(): mismo criterio que el resto del mod para daño "verdadero" que no
                // hace falta atribuir a un atacante concreto (ver KiFirePacket.selfDamage).
                target.hurt(damageSources().source(ModDamageTypes.KI_FIRE, owner), (float) damagePerHit);
                // El fuego de verdad es lo que hace que "quema" se LEA como quemadura y no como
                // un tic de daño invisible — el overlay naranja y el sonido de fuego vienen
                // gratis con esto.
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), HIT_INTERVAL));
            }
        }

        if (--life <= 0) discard();
    }

    @Override protected void defineSynchedData(@NotNull SynchedEntityData.Builder builder) { }

    // Sin persistencia a propósito, igual que `pierced` en KiProjectileEntity: esta zona vive
    // unos segundos. Si un guardado/recarga cae justo en ese margen, la instancia que vuelve a
    // aparecer arranca con los valores por defecto del constructor en vez de los de configure()
    // — un caso raro y de impacto bajo (peor caso: unos segundos de más con radio/daño
    // modestos), no una zona que se quede viva para siempre ni un crash.
    @Override protected void readAdditionalSaveData(@NotNull CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(@NotNull CompoundTag tag) { }

    @Override public boolean isPickable() { return false; }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.@NotNull DamageSource source, float amount) {
        return false; // invulnerable ya lo cubre; explícito por si algo lo comprueba directo
    }
}
