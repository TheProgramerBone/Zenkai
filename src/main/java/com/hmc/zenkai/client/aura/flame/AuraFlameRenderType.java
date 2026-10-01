package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * Shader y RenderType de la LLAMA (aura_flame_shell): el cuerpo de las familias {@code flame},
 * {@code electric_flame} y {@code wild} del aura compuesta (client/aura/compose), que lo dibuja
 * desde un VBO entre {@code setupRenderState}/{@code clearRenderState}. AISLADO del legacy
 * ({@link com.hmc.zenkai.client.aura.ModAuraRenderType}): ninguno importa al otro.
 * <p>
 * Historia: nació para la "llama sola" estilo dbrebirth (2026-09-29), que tenía además una capa
 * interior sobre el PlayerModel con su propio shader (aura_flame.*). La llama sola y esa capa se
 * BORRARON el 2026-09-30, cuando el sistema compuesto pasó a cubrirlo entero; de aquí queda solo la
 * cáscara.
 * <p>
 * Esta malla ENVUELVE al jugador: el truco de cull FRONT del aro legacy lo taparía visto desde
 * dentro. Se usa el mecanismo de dbrebirth: {@code NO_CULL} + fresnel en el fragment shader que
 * atenúa la cara trasera por alfa.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraFlameRenderType extends RenderType {
    // Nunca se instancia: solo heredamos para llegar a los shards protegidos de RenderStateShard.
    private AuraFlameRenderType() { super("", null, null, 0, false, false, null, null); }

    private static final ResourceLocation SHELL_SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_flame_shell");

    private static ShaderInstance shellShader;

    /** Mismo patrón try/catch con fallback silencioso que ModAuraRenderType/KiVfxRenderTypes: un
     *  fallo de compilación (driver viejo) deja el shader en null en vez de crashear, y
     *  {@link #available()} es la pregunta que hace el despacho. */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHELL_SHADER_ID,
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    instance -> shellShader = instance);
        } catch (Exception ex) {
            shellShader = null;
            Zenkai.LOGGER.error("[Zenkai] El shader de aura_flame_shell NO cargó ({}). El aura nueva "
                    + "no se dibuja hasta que compile (se usa la clásica).", ex.toString());
        }
    }

    /** ¿Compiló el programa de la llama? Sin él, el despacho usa el aura clásica. */
    public static boolean available() { return shellShader != null; }

    /** Mezcla PREMULTIPLICADA, la misma que KiVfxRenderTypes (su shard es privado, se replica):
     *  el shader sale como vec4(c*a, a*(1-emit)). Con emit=0 cubre como un translúcido normal; con
     *  emit=1 suma luz. Es lo que permite la regla de núcleo caliente sin aditivo puro (el aditivo
     *  sobre el aura se rechazó en juego: las capas superpuestas lavan a blanco). */
    private static final TransparencyStateShard PREMULTIPLIED =
            new TransparencyStateShard("zenkai_aura_flame_premultiplied", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    /** Ruido tileable del aura (NoiseTex de dbrebirth), generado por tools/gen_aura_noise.py —
     *  único origen del PNG, no editarlo a mano. */
    private static final ResourceLocation NOISE_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/aura_noise.png");

    /** Textura = solo el ruido (la forma sale entera del shader). blur=true: el ruido es un campo
     *  continuo. Sin lightmap (energía, no se oscurece de noche) y sin escritura de depth. */
    private static final RenderType SHELL = RenderType.create(
            "zenkai_aura_flame_shell",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS, 4096,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shellShader))
                    .setTextureState(new TextureStateShard(NOISE_TEX, true, false))
                    .setTransparencyState(PREMULTIPLIED)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    public static RenderType shell() { return SHELL; }

    /** El programa de la llama, para el cuerpo FLAME del aura compuesta (AuraComposeShaders). */
    public static ShaderInstance shellShaderInstance() { return shellShader; }
}
