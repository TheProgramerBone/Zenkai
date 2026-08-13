package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.feature.aura.AuraSkirts;
import com.hmc.zenkai.feature.aura.AuraTuning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Energía alrededor de los pies. Solo aparece cuando hay poder de sobra:
 * ground = presence² × release, así que un novato no la ve nunca y un endgame
 * suprimido tampoco. Es la señal de "esto está afectando al terreno".
 *
 * Deliberadamente NO es una circunferencia: son lengüetas tumbadas en anillo, con
 * radios y ángulos irregulares, para que el borde quede roto. Un anillo perfecto se
 * lee como un decal de videojuego, no como energía.
 *
 * Y GIRA. Sin rotación el anillo se ve plano por muy irregular que sea: la mirada lo
 * lee como una textura pegada al terreno. La velocidad la manda groundSpin del perfil,
 * que sube con la presencia — un endgame arrastra el suelo a 60 grados/segundo y un
 * novato apenas a 14. Las dos coronas giran en sentidos OPUESTOS: es lo que da la
 * sensación de que la energía está siendo removida y no simplemente rotada.
 *
 * No contamina la lógica del aura principal: lee ground() del perfil y nada más.
 */
public final class AuraGroundRenderer {
    private AuraGroundRenderer() {}

    /** Lengüetas del anillo con la intensidad al máximo. */
    private static final int MAX_TONGUES = 12;
    /** La corona interior gira más despacio y al revés que la exterior. */
    private static final float INNER_SPIN_MUL = -0.62f;
    private static final float INNER_RADIUS_MUL = 0.66f;
    private static final float RADIUS = 1.05f;
    private static final float ALPHA_MUL = 0.85f;
    /** Se levanta del suelo para no hacer z-fighting con el bloque. */
    private static final float Y_LIFT = 0.02f;

    public static void render(PoseStack pose, MultiBufferSource buffers,
                              AuraSkirts.Plan plan, AbstractClientPlayer p,
                              double ticks, float seconds, int seed) {
        float ground = plan.profile().ground();
        if (ground <= 0.01f || plan.isEmpty()) return;

        int count = Math.max(3, Math.round(MAX_TONGUES * ground));
        float frameTicks = plan.profile().frameTicks();
        int frame = (int) ((ticks / frameTicks + seed) % AuraTuning.SHEET_FRAMES);
        if (frame < 0) frame += AuraTuning.SHEET_FRAMES;

        VertexConsumer vc = buffers.getBuffer(
                ModAuraRenderType.energy(AuraSkirtRenderer.sheet(frame)));

        int rgb = plan.hasOuter() ? plan.outerColor() : plan.innerColor();
        float r = AuraQuads.red(rgb), g = AuraQuads.green(rgb), b = AuraQuads.blue(rgb);

        // El anillo respira con el mismo pulso que el aura: si hay Kaioken, late.
        float scale = plan.profile().pulsedSize(seconds);
        int stepIdx = (int) Math.floor(ticks / frameTicks);
        // El cuadrante del faldón bajo es el más ancho y plano: el que mejor tumba.
        float u0 = AuraQuads.cellU(3), v0 = AuraQuads.cellV(3);

        // Giro continuo, en segundos: es lo único del aura que NO va al ritmo stepped.
        // Un anillo que salta se lee como parpadeo; uno que gira, como energía.
        float spin = seconds * plan.profile().groundSpin();

        ring(pose, vc, plan, count, spin, scale, ground, 1f, stepIdx, seed, u0, v0, r, g, b);
        ring(pose, vc, plan, Math.max(3, count - 3), spin * INNER_SPIN_MUL,
                scale * INNER_RADIUS_MUL, ground * 0.85f, 0.8f, stepIdx, seed + 53,
                u0, v0, r, g, b);
    }

    private static void ring(PoseStack pose, VertexConsumer vc, AuraSkirts.Plan plan,
                             int count, float spin, float scale, float ground,
                             float sizeMul, int stepIdx, int seed,
                             float u0, float v0, float r, float g, float b) {
        float step = 360f / count;
        for (int i = 0; i < count; i++) {
            float wob = AuraQuads.hash01(i, 91, seed, stepIdx >> 1);
            float ang = i * step + wob * step * 0.6f + spin;
            float rad = RADIUS * scale * (0.75f + 0.45f * wob);
            float half = 0.42f * scale * sizeMul * (0.6f + 0.7f * wob)
                    * (0.5f + 0.5f * ground);
            float a = AuraSkirts.BASE_ALPHA * ALPHA_MUL * ground
                    * plan.profile().alpha() * (0.6f + 0.4f * wob);

            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(ang));
            pose.translate(0f, Y_LIFT, rad);
            AuraQuads.ground(vc, pose.last(), half, 0f, u0, v0, r, g, b, a);
            pose.popPose();
        }
    }
}