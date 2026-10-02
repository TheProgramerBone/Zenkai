package com.hmc.zenkai.client.particle;

import com.hmc.zenkai.client.aura.compose.AuraComposeShaders;
import com.hmc.zenkai.client.aura.compose.component.LightningPath;
import com.hmc.zenkai.client.aura.compose.component.Ribbons;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.content.particle.ZenkaiParticleOptions;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Arco eléctrico de las técnicas de ki (filo de Death Ball, arcos de carga de Final Flash, efecto
 * ARC de los datapacks): un RAYO como los del aura de SSJ2 y Beast, no un sprite.
 * <p>
 * Antes era un icono pixel-art de 16x16 ({@code ki_arc_*.png}) ampliado con filtro nearest, con
 * los escalones del píxel a la vista. Ahora es la MISMA polilínea fractal que usa el aura
 * ({@link LightningPath}) dibujada con la MISMA cinta ({@link Ribbons}, render type
 * {@code AuraComposeShaders.ribbons()}) en dos capas: un halo ancho del color de la técnica y un
 * núcleo fino casi blanco. Crepita: cada tick se regenera con otra semilla conservando los
 * extremos, así que vibra en el sitio en vez de saltar.
 * <p>
 * No dibuja en el búfer de partículas: ENCOLA en {@link KiVfxFrameQueue}, igual que el resto del
 * VFX de ki, para entrar en el bloom y en el orden de atrás hacia delante. Por eso por la ruta de
 * partículas no se dibuja nada visible (solo un cuádruple transparente, ver {@link #render}) y la
 * cola reproduce el rayo un instante después, en el mismo frame.
 * <p>
 * Dirección: la velocidad que recibe del emisor es la normal HACIA FUERA de la superficie donde
 * nace. El rayo no sale en recto hacia fuera sino reptando: mezcla esa normal con una tangente
 * aleatoria, que es como se ven los arcos en deathball_1-4. Sin velocidad (efecto de datapack) la
 * dirección es aleatoria. No se mueve nunca: vive pocos ticks y se apaga.
 */
public class KiArcParticle extends Particle {

    private static final int DEPTH = 4;
    private static final int PTS = LightningPath.points(DEPTH);
    private static final float ROUGHNESS = 0.28f;

    /** Tipo de render que no dibuja nada por sí mismo: ver la cabecera de la clase. */
    private static final ParticleRenderType QUEUED = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(@NotNull Tesselator tesselator, @NotNull TextureManager textures) {
            // El shader de posición+color y mezcla normal: lo único que necesita el cuádruple vacío
            // de render() para no dibujar nada ni dejar estado raro para el siguiente grupo.
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }

        @Override
        public String toString() { return "zenkai:ki_arc_queued"; }
    };

    private final float[] path = new float[PTS * 3];
    /** Extremo del rayo respecto al origen, para regenerar la polilínea con la misma forma general. */
    private final float ex, ey, ez;
    private long seed;
    private final float cr, cg, cb, kr, kg, kb;
    private final float width;

    protected KiArcParticle(ClientLevel level, double x, double y, double z,
                            double xd, double yd, double zd, ZenkaiParticleOptions opts) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.xd = this.yd = this.zd = 0.0;
        this.hasPhysics = false;
        this.lifetime = 3 + level.random.nextInt(3);

        float scale = opts.scale();
        float len = 0.5f + 1.4f * scale;
        this.width = 0.03f + 0.045f * scale;

        Vec3 out = new Vec3(xd, yd, zd);
        if (out.lengthSqr() < 1.0e-8) out = randomUnit(level);
        out = out.normalize();
        // Tangente a la superficie: perpendicular a la normal, con rumbo aleatorio.
        Vec3 t = out.cross(randomUnit(level));
        if (t.lengthSqr() < 1.0e-6) t = out.cross(new Vec3(0, 1, 0));
        t = t.normalize();
        Vec3 dir = out.scale(0.35).add(t.scale(0.9)).normalize().scale(len);
        this.ex = (float) dir.x;
        this.ey = (float) dir.y;
        this.ez = (float) dir.z;

        // Color: el de la técnica para el halo; el núcleo, llevado casi a blanco (el rayo de anime
        // tiene el centro blanco y el color en el halo, ver ElectricityComponent).
        this.cr = opts.red();
        this.cg = opts.green();
        this.cb = opts.blue();
        this.kr = Mth.lerp(0.75f, cr, 1f);
        this.kg = Mth.lerp(0.75f, cg, 1f);
        this.kb = Mth.lerp(0.75f, cb, 1f);

        this.seed = level.random.nextLong();
        rebuild();
        // La caja de la partícula decide si se dibuja (frustum): el rayo se extiende len desde el origen.
        setBoundingBox(new AABB(x, y, z, x, y, z).inflate(len + 0.25));
    }

    private static Vec3 randomUnit(ClientLevel level) {
        double theta = level.random.nextDouble() * Math.PI * 2.0;
        double z = level.random.nextDouble() * 2.0 - 1.0;
        double planar = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new Vec3(planar * Math.cos(theta), z, planar * Math.sin(theta));
    }

    private void rebuild() {
        LightningPath.generate(path, DEPTH, 0f, 0f, 0f, ex, ey, ez, ROUGHNESS, seed);
    }

    @Override
    public void tick() {
        if (age++ >= lifetime) {
            remove();
            return;
        }
        // Crepita: otra semilla, mismos extremos.
        seed = LightningPath.next(seed);
        rebuild();
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, Camera camera, float partialTicks) {
        // Un cuádruple degenerado y transparente, SIEMPRE y antes de cualquier return: el motor
        // de partículas construye el búfer de este tipo al acabar el grupo, y un búfer vacío no
        // debe poder llegar ahí (según la versión devuelve null o lanza). No pinta nada visible.
        for (int i = 0; i < 4; i++) buffer.addVertex(0f, 0f, 0f).setColor(0, 0, 0, 0);

        // Parpadeo del rayo de anime: sale a pleno y se apaga en la última mitad de su vida.
        float t = Mth.clamp((age + partialTicks) / lifetime, 0f, 1f);
        float a = t < 0.5f ? 1f : 1f - (t - 0.5f) * 2f;
        if (a <= 0.02f) return;

        Vec3 cam = camera.getPosition();
        final float ox = (float) (x - cam.x), oy = (float) (y - cam.y), oz = (float) (z - cam.z);
        final float alpha = a;
        KiVfxFrameQueue.submit(new PoseStack(), new Vec3(ox, oy, oz), (ps, buf) -> {
            VertexConsumer vc = buf.getBuffer(AuraComposeShaders.ribbons());
            Matrix4f m = ps.last().pose();
            Ribbons.strip(vc, m, path, PTS, ox, oy, oz, width * 3.2f, cr, cg, cb, alpha * 0.45f);
            Ribbons.strip(vc, m, path, PTS, ox, oy, oz, width, kr, kg, kb, alpha);
        });
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return QUEUED;
    }

    public static class Provider implements ParticleProvider<ZenkaiParticleOptions> {
        @Override
        public Particle createParticle(@NotNull ZenkaiParticleOptions opts, @NotNull ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new KiArcParticle(level, x, y, z, xd, yd, zd, opts);
        }
    }
}
