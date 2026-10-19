package com.hmc.zenkai.client.particle;

import com.hmc.zenkai.client.combat.StunClientState;
import com.hmc.zenkai.content.particle.ZenkaiParticleOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Estrella de aturdimiento: orbita despacio sobre la cabeza de una entidad (como el «confundido»
 * de Cobblemon). No es una partícula libre: se ata a la entidad por id y se recoloca cada tick,
 * y muere sola cuando el aturdimiento termina o la entidad desaparece.
 *
 * Los tres parámetros de movimiento de la fábrica se reutilizan como datos, porque el record de
 * opciones solo lleva color y escala: xd = id de la entidad, yd = fase inicial (rad), zd = ticks
 * que debe durar como máximo.
 */
public class StunStarParticle extends TextureSheetParticle {

    /** Radianes por tick: una vuelta cada ~3,5 s. Lento a propósito. */
    private static final double SPIN = 0.09;

    private final SpriteSet sprites;
    private final int entityId;
    private double angle;
    private final float baseSize;

    protected StunStarParticle(ClientLevel level, double x, double y, double z,
                               double entityId, double phase, double lifeTicks,
                               ZenkaiParticleOptions opts, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.sprites = sprites;
        setSprite(sprites.get(0, 3));
        this.entityId = (int) entityId;
        this.angle = phase;
        this.lifetime = Math.max(1, (int) lifeTicks);
        this.hasPhysics = false;
        this.gravity = 0f;
        this.baseSize = 0.11f * opts.scale();
        this.quadSize = baseSize;
        this.rCol = opts.red();
        this.gCol = opts.green();
        this.bCol = opts.blue();
        place(level.getEntity(this.entityId));
    }

    private void place(Entity e) {
        if (e == null) return;
        double r = e.getBbWidth() * 0.45 + 0.15;
        double bob = Math.sin(age * 0.15 + angle) * 0.03;
        this.setPos(e.getX() + Math.cos(angle) * r,
                e.getY() + e.getBbHeight() + 0.12 + bob,
                e.getZ() + Math.sin(angle) * r);
    }

    @Override
    public void tick() {
        this.xo = x;
        this.yo = y;
        this.zo = z;
        if (age++ >= lifetime || !StunClientState.isStunned(entityId)) { remove(); return; }
        Entity e = level.getEntity(entityId);
        if (e == null || !e.isAlive()) { remove(); return; }
        angle += SPIN;
        // Titileo: 4 fotogramas, uno cada 3 ticks, con un pulso suave del tamaño.
        setSprite(sprites.get((age / 3) % 4, 3));
        this.quadSize = baseSize * (1.0f + 0.08f * (float) Math.sin(age * 0.5 + angle));
        place(e);
        // Primera persona: las del propio jugador taparían la vista.
        var mc = Minecraft.getInstance();
        boolean hide = e == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
        this.alpha = hide ? 0f : 1f;
    }

    @Override
    protected int getLightColor(float partialTick) { return 0xF000F0; }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<ZenkaiParticleOptions> {
        @Override
        public Particle createParticle(@NotNull ZenkaiParticleOptions opts, @NotNull ClientLevel level,
                                       double x, double y, double z,
                                       double entityId, double phase, double lifeTicks) {
            return new StunStarParticle(level, x, y, z, entityId, phase, lifeTicks, opts, sprites);
        }
    }
}
