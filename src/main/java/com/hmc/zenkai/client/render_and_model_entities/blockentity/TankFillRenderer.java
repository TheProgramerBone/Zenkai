package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.TransportBlock;
import com.hmc.zenkai.content.blockentity.TransportBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Nivel visible en el cristal de los tanques. El grupo multibloque manda a cada bloque cuánto de SU
 * altura está lleno (TransportBlockEntity#fillSteps, en escalones de 1/32: el contenido sube de abajo
 * arriba por el grupo entero, como en Create) y este renderer pinta ese tramo encima del cristal de
 * cada cara LATERAL exterior: el fluido con su textura y su tinte, la energía con unas celdas verdes
 * a brillo completo.
 *
 * El rectángulo de cristal sale de los mismos bordes que la textura conectada (CT_PROPERTY): donde un
 * lado es borde del grupo, el marco ocupa 7 px (de 32) y el cristal empieza en el 8.º; donde no, el
 * cristal llega al filo y empalma con el bloque vecino. Si gen_transport_textures.py cambia esas
 * medidas, hay que cambiar {@link #FRAME} con ellas.
 *
 * Los cables comparten tipo de block entity con los tanques: para ellos no hace nada.
 */
public class TankFillRenderer<T extends TransportBlockEntity> implements BlockEntityRenderer<T> {

    /** Píxeles (de 32) que ocupa el marco desde un lado que es borde del grupo, hasta el cristal. */
    private static final int FRAME = 7;
    /** Separación de la cara para no pelear en profundidad con el cristal. */
    private static final float EPS = 0.002f;
    private static final ResourceLocation ENERGY_FILL = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "block/energy_tank_fill");

    public TankFillRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(@NotNull T be, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
                       int light, int overlay) {
        if (be.isCableBlock() || be.fillSteps() <= 0) return;
        Level level = be.getLevel();
        if (level == null) return;

        TextureAtlasSprite sprite;
        int color;
        int useLight;          // -1 = la luz del bloque de delante de cada cara
        int passes = 1;
        if (be.kind() == TransportBlock.Kind.ENERGY) {
            sprite = atlas().apply(ENERGY_FILL);
            color = 0xFFFFFFFF;
            useLight = LightTexture.FULL_BRIGHT;   // la carga brilla, como la pantalla de una máquina
        } else {
            if (be.fillFluidId().isEmpty()) return;
            Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(be.fillFluidId()));
            FluidStack stack = new FluidStack(fluid, 1000);
            if (stack.isEmpty()) return;
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
            sprite = atlas().apply(ext.getStillTexture(stack));
            color = ext.getTintColor(stack);
            // OJO: el «light» que llega es el de la posición del tanque, que es un bloque opaco: vale 0 y el
            // agua salía negra (captura del 2026-11-17). Se usa la luz del aire delante de cada cara.
            useLight = fluid.getFluidType().getLightLevel(stack) > 0 ? LightTexture.FULL_BRIGHT : -1;
            // La textura del agua (y de muchos fluidos) es semitransparente: sobre el cristal oscuro casi no
            // se distinguía (captura del 2026-11-17). Dos pasadas le dan cuerpo sin tocar su color.
            passes = 2;
        }

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f m = pose.last().pose();
        BlockPos pos = be.getBlockPos();
        float top = be.fillSteps() / (float) TransportBlockEntity.FILL_STEPS;   // altura llena, 0..1 desde abajo

        for (Direction face : Direction.Plane.HORIZONTAL) {
            // Cara tapada por otro tanque del mismo tipo (por dentro del grupo o pegada a otro): no se ve.
            if (level.getBlockState(pos.relative(face)).getBlock() instanceof TransportBlock tb
                    && !tb.isCable() && tb.kind() == be.kind()) continue;
            // Una cara con placa de modo (Empujar/Tirar/Desconectada): la placa ocupa el centro (PLATE_MIN..
            // PLATE_MAX). Antes la cara se saltaba entera y, en un grupo cargado, ese bloque parecía vacío
            // (captura del usuario, 2026-11-17); ahora el nivel se pinta ALREDEDOR de la placa.
            boolean plate = be.look(face) != com.hmc.zenkai.feature.transport.Look.NORMAL;
            int bits = (be.ctBits() >> (4 * face.get3DDataValue())) & 15;
            // Rectángulo de cristal en coordenadas de la cara vista desde fuera (u izq->der, v abajo->arriba), 0..1.
            float u0 = (bits & TransportBlockEntity.CT_LEFT) != 0 ? (FRAME + 1) / 32f : 0f;
            float u1 = (bits & TransportBlockEntity.CT_RIGHT) != 0 ? (32 - FRAME) / 32f : 1f;
            float v0 = (bits & TransportBlockEntity.CT_BOTTOM) != 0 ? FRAME / 32f : 0f;
            float glassTop = (bits & TransportBlockEntity.CT_TOP) != 0 ? (32 - FRAME - 1) / 32f : 1f;
            float v1 = Math.min(top, glassTop);
            if (v1 <= v0 || u1 <= u0) continue;
            int faceLight = useLight >= 0 ? useLight
                    : net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos.relative(face));
            for (int p = 0; p < passes; p++) {
                float eps = EPS * (p + 1);
                if (!plate) {
                    quad(vc, m, face, u0, v0, u1, v1, sprite, color, faceLight, eps);
                    continue;
                }
                // Cristal menos el cuadrado de la placa: hasta cuatro tiras (abajo, arriba, izquierda, derecha).
                quadClip(vc, m, face, u0, v0, u1, Math.min(v1, PLATE_MIN), sprite, color, faceLight, eps);
                quadClip(vc, m, face, u0, Math.max(v0, PLATE_MAX), u1, v1, sprite, color, faceLight, eps);
                float mv0 = Math.max(v0, PLATE_MIN), mv1 = Math.min(v1, PLATE_MAX);
                quadClip(vc, m, face, u0, mv0, Math.min(u1, PLATE_MIN), mv1, sprite, color, faceLight, eps);
                quadClip(vc, m, face, Math.max(u0, PLATE_MAX), mv0, u1, mv1, sprite, color, faceLight, eps);
            }
        }
    }

    /** La placa de modo: 24 px centrados de 32 (textures/block/*_io_*.png, 4 px de margen transparente). */
    private static final float PLATE_MIN = 4 / 32f, PLATE_MAX = 28 / 32f;

    private static void quadClip(VertexConsumer vc, Matrix4f m, Direction face, float u0, float v0, float u1, float v1,
                                 TextureAtlasSprite sprite, int color, int light, float eps) {
        if (u1 > u0 && v1 > v0) quad(vc, m, face, u0, v0, u1, v1, sprite, color, light, eps);
    }

    private static java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas() {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
    }

    /**
     * Un rectángulo sobre la cara {@code face}, en coordenadas de la cara vista desde fuera, con la
     * misma orientación que la UV por defecto de los cubos (en la cara norte la u crece hacia el oeste).
     * Los vértices van en sentido antihorario vistos desde fuera, para el descarte de caras traseras.
     */
    private static void quad(VertexConsumer vc, Matrix4f m, Direction face, float u0, float v0, float u1, float v1,
                             TextureAtlasSprite sprite, int color, int light, float eps) {
        float[][] corners = { {u0, v0}, {u1, v0}, {u1, v1}, {u0, v1} };
        int a = (color >>> 24) & 255, r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        for (float[] c : corners) {
            float u = c[0], v = c[1];
            float x, y = v, z;
            switch (face) {
                case NORTH -> { x = 1 - u; z = -eps; }
                case SOUTH -> { x = u; z = 1 + eps; }
                case WEST -> { x = -eps; z = u; }
                default -> { x = 1 + eps; z = 1 - u; }   // EAST
            }
            vc.addVertex(m, x, y, z)
                    .setColor(r, g, b, a)
                    .setUv(sprite.getU(u), sprite.getV(1 - v))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(face.getStepX(), face.getStepY(), face.getStepZ());
        }
    }
}
