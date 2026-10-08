package com.hmc.zenkai.client.particle;

import com.hmc.zenkai.content.particle.ZenkaiParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import org.jetbrains.annotations.NotNull;

/**
 * Mota de energía: la MISMA partícula que END_ROD de vainilla (mismas texturas `glitter_*`, mismo
 * rozamiento, vida, deriva y fundido), pero con COLOR propio, que END_ROD no permite. Existe para
 * la estela de la Genki Dama: las criaturas pacíficas siguen usando END_ROD (blanca) y la energía
 * de los mobs hostiles de un jugador malvado usa esta, en rojo, con idéntico aspecto.
 * El tinte llega en ZenkaiParticleOptions (rgb); hereda la velocidad del packet.
 */
public class EnergyMoteParticle extends SimpleAnimatedParticle {

    protected EnergyMoteParticle(ClientLevel level, double x, double y, double z,
                                 double xd, double yd, double zd,
                                 ZenkaiParticleOptions opts, SpriteSet sprites) {
        super(level, x, y, z, sprites, -5.0E-4F);   // misma gravedad que EndRodParticle
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.quadSize *= 0.75F * opts.scale();
        this.lifetime = 60 + this.random.nextInt(12);
        // Tinte principal y un fundido a un tono más oscuro del mismo color (END_ROD funde a un
        // rosado fijo; aquí el fundido sigue el tinte para que la mota no se vuelva rosa).
        this.setColor(opts.rgb());
        int dim = ((int) (opts.red() * 0.45f * 255f) << 16)
                | ((int) (opts.green() * 0.45f * 255f) << 8)
                | (int) (opts.blue() * 0.45f * 255f);
        this.setFadeColor(dim);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void move(double dx, double dy, double dz) {
        this.setBoundingBox(this.getBoundingBox().move(dx, dy, dz));
        this.setLocationFromBoundingbox();
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880; // a pleno brillo, como END_ROD
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<ZenkaiParticleOptions> {
        @Override
        public Particle createParticle(@NotNull ZenkaiParticleOptions opts, @NotNull ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new EnergyMoteParticle(level, x, y, z, xd, yd, zd, opts, sprites);
        }
    }
}
