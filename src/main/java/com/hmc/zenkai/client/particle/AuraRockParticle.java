package com.hmc.zenkai.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Trozo de suelo que sale volando al cargar ki o transformarse (AuraChargeGroundFx). Es el
 * "toque Minecraft" frente a DragonMine Z, que usa sprites planos de roca: aquí es un CUBITO 3D
 * con un recorte de 4x4 píxeles del sprite del bloque que se pisa (tierra, piedra, arena...), así
 * que se lee como un pedazo real de ese bloque y gira en los tres ejes.
 * <p>
 * Movimiento con los números de partida de la RockParticle de DMZ: sube rápido, frena, flota y
 * cae despacio, con giro constante; encoge en los últimos 10 ticks en vez de desaparecer de golpe.
 * <p>
 * Sombreado por cara según hacia dónde mira la cara YA girada (arriba claro, abajo oscuro), como
 * el sombreado direccional de los bloques vanilla: sin él el cubito se ve plano.
 */
public class AuraRockParticle extends Particle {

    /** Lado del recorte del sprite, en fracción de la textura (4 de 16 píxeles). */
    private static final float WINDOW = 0.25f;
    private static final int SHRINK_TICKS = 10;

    private final TextureAtlasSprite sprite;
    private final float u0, u1, v0, v1;
    private final float halfSize;
    private final float spinX, spinY, spinZ;
    private float rotX, rotY, rotZ, oRotX, oRotY, oRotZ;

    protected AuraRockParticle(ClientLevel level, double x, double y, double z,
                               double xd, double yd, double zd, BlockState state, BlockPos pos,
                               boolean levitate) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.sprite = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(state);

        float uo = level.random.nextInt(4) * WINDOW;
        float vo = level.random.nextInt(4) * WINDOW;
        this.u0 = sprite.getU(uo);
        this.u1 = sprite.getU(uo + WINDOW);
        this.v0 = sprite.getV(vo);
        this.v1 = sprite.getV(vo + WINDOW);

        this.lifetime = 40 + level.random.nextInt(41);
        this.gravity = 0.12f;
        this.friction = 0.94f;
        this.hasPhysics = true;
        // Trozos más grandes (antes 0.05-0.13): junto al polvo de DMZ los cubitos se perdían.
        this.halfSize = 0.07f + level.random.nextFloat() * 0.11f;
        if (levitate) {
            // Presión del aura (Beast, aura_9/aura_10): el trozo sube despacio y se queda
            // flotando alrededor en vez de caer — gravedad levemente negativa, mucho rozamiento,
            // sin colisión para que no se quede enganchado en el borde de un bloque.
            this.lifetime = 60 + level.random.nextInt(50);
            this.gravity = -0.012f;
            this.friction = 0.9f;
            this.hasPhysics = false;
        }

        this.spinX = (level.random.nextFloat() - 0.5f) * 0.3f;
        this.spinY = (level.random.nextFloat() - 0.5f) * 0.3f;
        this.spinZ = (level.random.nextFloat() - 0.5f) * 0.3f;
        this.rotX = this.oRotX = level.random.nextFloat() * Mth.TWO_PI;
        this.rotY = this.oRotY = level.random.nextFloat() * Mth.TWO_PI;
        this.rotZ = this.oRotZ = level.random.nextFloat() * Mth.TWO_PI;

        // Mismo tinte que TerrainParticle: hojas, hierba alta... llevan color de bioma. El bloque
        // de hierba no (su icono de partícula es la tierra, sin tinte).
        if (!state.is(Blocks.GRASS_BLOCK)) {
            int c = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
            if (c != -1) {
                this.rCol = ((c >> 16) & 0xFF) / 255f;
                this.gCol = ((c >> 8) & 0xFF) / 255f;
                this.bCol = (c & 0xFF) / 255f;
            }
        }
    }

    @Override
    public void tick() {
        oRotX = rotX;
        oRotY = rotY;
        oRotZ = rotZ;
        super.tick();
        // Rozando el suelo deja de girar: un trozo que da vueltas apoyado se lee raro.
        float spinMul = onGround ? 0.2f : 1f;
        rotX += spinX * spinMul;
        rotY += spinY * spinMul;
        rotZ += spinZ * spinMul;
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);

        float left = lifetime - (age + partialTicks);
        float s = halfSize * Mth.clamp(left / SHRINK_TICKS, 0f, 1f);
        if (s <= 0.001f) return;

        Quaternionf rot = new Quaternionf().rotationXYZ(
                Mth.lerp(partialTicks, oRotX, rotX),
                Mth.lerp(partialTicks, oRotY, rotY),
                Mth.lerp(partialTicks, oRotZ, rotZ));
        int light = getLightColor(partialTicks);

        // Seis caras: normal + dos ejes del plano. Cada cara se emite con las dos vueltas de
        // winding: así no depende del estado de culling del render de partículas.
        face(buffer, rot, px, py, pz, s, light, 0, 1, 0, 1, 0, 0, 0, 0, 1);
        face(buffer, rot, px, py, pz, s, light, 0, -1, 0, 1, 0, 0, 0, 0, 1);
        face(buffer, rot, px, py, pz, s, light, 1, 0, 0, 0, 1, 0, 0, 0, 1);
        face(buffer, rot, px, py, pz, s, light, -1, 0, 0, 0, 1, 0, 0, 0, 1);
        face(buffer, rot, px, py, pz, s, light, 0, 0, 1, 1, 0, 0, 0, 1, 0);
        face(buffer, rot, px, py, pz, s, light, 0, 0, -1, 1, 0, 0, 0, 1, 0);
    }

    private void face(VertexConsumer buffer, Quaternionf rot, float px, float py, float pz, float s, int light,
                      float nx, float ny, float nz, float ax, float ay, float az, float bx, float by, float bz) {
        Vector3f n = rot.transform(new Vector3f(nx, ny, nz));
        // Sombreado direccional tipo bloque vanilla: 1.0 mirando arriba, 0.5 mirando abajo.
        float shade = 0.75f + 0.25f * n.y;
        float r = rCol * shade, g = gCol * shade, b = bCol * shade;

        Vector3f c = new Vector3f(nx, ny, nz).mul(s);
        Vector3f a = new Vector3f(ax, ay, az).mul(s);
        Vector3f bb = new Vector3f(bx, by, bz).mul(s);
        Vector3f[] corners = {
                new Vector3f(c).sub(a).sub(bb),
                new Vector3f(c).add(a).sub(bb),
                new Vector3f(c).add(a).add(bb),
                new Vector3f(c).sub(a).add(bb)};
        float[] us = {u0, u1, u1, u0};
        float[] vs = {v1, v1, v0, v0};
        for (Vector3f v : corners) rot.transform(v);

        for (int i = 0; i < 4; i++) vertex(buffer, corners[i], px, py, pz, us[i], vs[i], r, g, b, light);
        for (int i = 3; i >= 0; i--) vertex(buffer, corners[i], px, py, pz, us[i], vs[i], r, g, b, light);
    }

    private void vertex(VertexConsumer buffer, Vector3f v, float px, float py, float pz,
                        float u, float vv, float r, float g, float b, int light) {
        buffer.addVertex(px + v.x, py + v.y, pz + v.z).setUv(u, vv).setColor(r, g, b, alpha).setLight(light);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    public static class Provider implements ParticleProvider<BlockParticleOption> {
        /** true = la variante que levita (ModParticles.AURA_ROCK_FLOAT). */
        private final boolean levitate;

        public Provider(boolean levitate) { this.levitate = levitate; }

        @Override
        public Particle createParticle(@NotNull BlockParticleOption opts, @NotNull ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            BlockState state = opts.getState();
            if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) return null;
            return new AuraRockParticle(level, x, y, z, xd, yd, zd, state, BlockPos.containing(x, y, z), levitate);
        }
    }
}
