package com.hmc.zenkai.client.particle;

import com.hmc.zenkai.content.particle.ZenkaiParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import org.jetbrains.annotations.NotNull;

/**
 * Chispa que sale despedida del impacto: hereda la velocidad del packet, cae con gravedad
 * suave, rebota en el suelo y se apaga con alfa lineal. Frame aleatorio de ki_spark_*.png.
 */
public class KiSparkParticle extends TextureSheetParticle {

    protected KiSparkParticle(ClientLevel level, double x, double y, double z,
                              double xd, double yd, double zd,
                              ZenkaiParticleOptions opts, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        pickSprite(sprites);

        this.xd = xd;
        this.yd = yd;
        this.zd = zd;

        this.lifetime = 6 + level.random.nextInt(7);
        this.gravity = 0.35f;
        this.friction = 0.86f;
        this.hasPhysics = true;

        this.quadSize = 0.11f * opts.scale() * (0.7f + level.random.nextFloat() * 0.6f);
        this.rCol = opts.red();
        this.gCol = opts.green();
        this.bCol = opts.blue();
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = Math.max(0.0f, 1.0f - (float) this.age / (float) this.lifetime);
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
            return new KiSparkParticle(level, x, y, z, xd, yd, zd, opts, sprites);
        }
    }
}