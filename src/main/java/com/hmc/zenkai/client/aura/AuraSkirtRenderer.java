package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.aura.AuraProfile;
import com.hmc.zenkai.feature.aura.AuraSkirt;
import com.hmc.zenkai.feature.aura.AuraSkirts;
import com.hmc.zenkai.feature.aura.AuraTuning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/**
 * La masa del aura: los faldones de planos-silueta, cada uno con su núcleo.
 *
 * ES DELIBERADAMENTE TONTO. Recibe un Plan con resuelto — faldones ya aligerados
 * por el LOD, colores ya decididos, alpha ya compensado, cadencia ya derivada — y su
 * único trabajo es convertir eso en vértices. No sabe qué es un saiyan, ni un Kaioken,
 * ni cuánto Power Level tiene nadie.
 *
 * EL NÚCLEO YA NO ES UN CONO APARTE. Antes se dibujaba una tercera gota escalada a 0.70;
 * ahora cada plano emite su masa y, justo encima, el cuadrante de núcleo de la MISMA
 * hoja. El núcleo tiene por tanto la forma exacta de su propia llama en vez de ser una
 * silueta más pequeña, y sale más barato: 74 quads en vez de los 111 de los tres conos.
 *
 * DOS RELOJES, A PROPÓSITO:
 *  - STEPPED (jitter, espejado, frame de hoja): salta al ritmo de frameTicks, que es lo
 *    que da la cadencia de animación en dos del anime.
 *  - CONTINUO (pulso): segundos reales. Es la respiración, y con Kaioken el latido.
 * Mezclarlos fue una decisión de dirección: la llama salta, el volumen respira.
 */
public final class AuraSkirtRenderer {
    private AuraSkirtRenderer() {}

    private static final ResourceLocation[] SHEET =
            new ResourceLocation[AuraTuning.SHEET_FRAMES];

    static {
        for (int i = 0; i < AuraTuning.SHEET_FRAMES; i++) {
            SHEET[i] = ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID, "textures/entity/aura_flame_" + i + ".png");
        }
    }

    /**
     * @param pose    ya trasladado al jugador y con el tilt de vuelo aplicado
     * @param seconds tiempo continuo, para el pulso
     * @param ticks   gameTime + partialTick, para el reloj stepped
     * @param toCamX  dirección jugador→cámara en XZ, o NaN para no atenuar el frente
     */
    public static void render(PoseStack pose, MultiBufferSource buffers,
                              AuraSkirts.Plan plan, double ticks, float seconds,
                              int seed, float toCamX, float toCamZ) {
        if (plan.isEmpty()) return;

        AuraProfile p = plan.profile();
        float frameTicks = p.frameTicks();
        int frame = (int) ((ticks / frameTicks + seed) % AuraTuning.SHEET_FRAMES);
        if (frame < 0) frame += AuraTuning.SHEET_FRAMES;

        VertexConsumer vc = buffers.getBuffer(ModAuraRenderType.energy(SHEET[frame]));

        // Tamaño con la respiración ya aplicada. Kaioken es lo que hace que se note.
        float scale = AuraSkirts.AURA_SCALE * p.pulsedSize(seconds);
        int stepIdx = (int) Math.floor(ticks / frameTicks);

        if (plan.hasOuter()) {
            // Capa envolvente: mismo perfil, más grande, seed desfasado para que no
            // respire igual que la interior. Una gota, dos colores.
            cone(pose, vc, plan, plan.outerColor(), scale * AuraTuning.OUTER_SCALE_MUL,
                    AuraTuning.OUTER_ALPHA_MUL, stepIdx, seed + 7, toCamX, toCamZ);
        }
        cone(pose, vc, plan, plan.innerColor(), scale, 1f, stepIdx, seed, toCamX, toCamZ);
    }

    private static void cone(PoseStack pose, VertexConsumer vc, AuraSkirts.Plan plan,
                             int rgb, float scale, float alphaMul,
                             int stepIdx, int seed, float toCamX, float toCamZ) {
        float r = AuraQuads.red(rgb), g = AuraQuads.green(rgb), b = AuraQuads.blue(rgb);
        boolean core = plan.hasCore();
        float coreA = plan.profile().core();
        int coreRgb = plan.coreColor();
        float cr = AuraQuads.red(coreRgb), cg = AuraQuads.green(coreRgb),
                cb = AuraQuads.blue(coreRgb);
        boolean fade = !Float.isNaN(toCamX);

        int si = 0;
        for (AuraSkirt s : plan.skirts()) {
            float w = s.width() * scale;
            float step = 360f / s.count();
            float baseAlpha = plan.alphaOf(s) * alphaMul;
            float u0 = AuraQuads.cellU(s.tex());
            float vMass = AuraQuads.cellV(s.tex());
            float vCore = AuraQuads.coreCellV(s.tex());

            for (int i = 0; i < s.count(); i++) {
                // Jitter por plano: re-rueda cada 2 steps, con fase desfasada por plano
                // (i*7 + si*13) para que las llamas no salten a coro.
                int jStep = (stepIdx + i * 7 + si * 13) >> 1;
                float wobble = AuraQuads.hash01(i, si, seed, jStep);
                float h = s.height() * scale * (1f - s.jitter() * wobble);
                boolean mirror = AuraQuads.hash01(i + 31, si, seed, jStep) > 0.5f;
                float ang = s.offsetDeg() + i * step;

                float a = baseAlpha;
                if (fade) a *= frontFade(ang, toCamX, toCamZ);

                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(ang));
                pose.translate(0f, s.yStart() * scale, s.baseR() * scale);
                pose.mulPose(Axis.XP.rotationDegrees(s.tiltDeg()));

                AuraQuads.plane(vc, pose.last(), w, h, u0, vMass, mirror, 0f, r, g, b, a);
                if (core) {
                    AuraQuads.plane(vc, pose.last(), w, h, u0, vCore, mirror,
                            AuraTuning.CORE_Z_OFFSET, cr, cg, cb, a * coreA);
                }
                pose.popPose();
            }
            si++;
        }
    }

    /** Atenuación del plano según lo de frente que esté a la cámara. */
    private static float frontFade(float angDeg, float toCamX, float toCamZ) {
        float rad = (float) Math.toRadians(angDeg);
        float dot = (float) (Math.sin(rad) * toCamX + Math.cos(rad) * toCamZ);
        float u = AuraTuning.clamp01(
                (dot - AuraTuning.FRONT_DOT_START)
                        / (AuraTuning.FRONT_DOT_FULL - AuraTuning.FRONT_DOT_START));
        float smooth = u * u * (3f - 2f * u);
        return 1f - (1f - AuraTuning.FRONT_FADE_MIN) * smooth;
    }

    /** Hoja del frame dado. La usan wisps y sparks para compartir RenderType. */
    public static ResourceLocation sheet(int frame) {
        return SHEET[Math.floorMod(frame, AuraTuning.SHEET_FRAMES)];
    }
}