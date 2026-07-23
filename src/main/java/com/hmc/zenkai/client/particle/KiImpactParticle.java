package com.hmc.zenkai.client.particle;

import com.hmc.zenkai.content.particle.ZenkaiParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Destello de impacto: quieto, sin física, animado por edad sobre los 6 frames de
 * textures/particle/ki_impact_*.png. La textura es BLANCA; el tinte viene del RGB de las
 * opciones (color del aura resuelto en servidor).
 */
public class KiImpactParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected KiImpactParticle(ClientLevel level, double x, double y, double z,
                               ZenkaiParticleOptions opts, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.sprites = sprites;

        this.lifetime = 7;          // 6 frames + 1 tick de cola
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.friction = 1.0f;
        this.xd = this.yd = this.zd = 0.0;

        this.quadSize = 1.1f * opts.scale();
        this.rCol = opts.red();
        this.gCol = opts.green();
        this.bCol = opts.blue();

        // Giro aleatorio: dos golpes seguidos no dibujan la misma estrella.
        this.roll = this.oRoll = level.random.nextFloat() * Mth.TWO_PI; // ⚠ Mth.TWO_PI

        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<ZenkaiParticleOptions> {
        @Override
        public Particle createParticle(@NotNull ZenkaiParticleOptions opts, @NotNull ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new KiImpactParticle(level, x, y, z, opts, sprites);
        }
    }
}