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
 * Nube de polvo que levanta el aura al cargar ki o transformarse (AuraChargeGroundFx). Sale de los
 * pies hacia fuera, frena, crece y se deshace: los 4 frames de aura_dust_*.png van por EDAD (la
 * misma nube abriéndose), no al azar. Números de partida sacados de la DustParticle de
 * DragonMine Z (vida 40-60 ticks, frenado horizontal ~0.95 por tick), que es la referencia visual
 * que pidió el usuario (vídeo comparado el 2026-09-30).
 * <p>
 * El tinte (rgb de las opciones) es el MapColor del bloque pisado: en tierra sale marrón, en
 * arena beige, en nieve blanco. La textura es gris con su propio contorno oscuro, así el tinte
 * multiplica sin borrar el sombreado. Luz del mundo (no FULL_BRIGHT): es polvo, no energía, y de
 * noche se oscurece como cualquier partícula vanilla.
 */
public class AuraDustParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    /** Crecimiento por tick del tamaño: la nube se expande mientras se aleja. */
    private final float growth;

    protected AuraDustParticle(ClientLevel level, double x, double y, double z,
                               double xd, double yd, double zd,
                               ZenkaiParticleOptions opts, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;

        this.lifetime = 40 + level.random.nextInt(21);
        this.gravity = 0f;
        this.friction = 0.93f;
        this.hasPhysics = true;

        this.quadSize = 0.32f * opts.scale() * (0.8f + level.random.nextFloat() * 0.4f);
        this.growth = 1.012f + level.random.nextFloat() * 0.008f;
        this.roll = level.random.nextFloat() * (float) (Math.PI * 2);
        this.oRoll = this.roll;
        this.rCol = opts.red();
        this.gCol = opts.green();
        this.bCol = opts.blue();
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        setSpriteFromAge(sprites);
        quadSize *= growth;
        // Opaca la mayor parte de la vida y se apaga en el último 40%: una nube que se va
        // volviendo transparente desde el primer tick se lee como humo, no como polvo levantado.
        float life = (float) age / (float) lifetime;
        alpha = life < 0.6f ? 1f : Math.max(0f, 1f - (life - 0.6f) / 0.4f);
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
            return new AuraDustParticle(level, x, y, z, xd, yd, zd, opts, sprites);
        }
    }
}
