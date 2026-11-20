package com.hmc.zenkai.client.render_and_model_entities;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.content.blockentity.TransportBlockEntity;
import com.hmc.zenkai.content.block.TransportGeometry;
import com.hmc.zenkai.feature.transport.Look;
import com.hmc.zenkai.content.item.WrenchItem;
import com.hmc.zenkai.feature.sideconfig.SideConfigurable;
import com.hmc.zenkai.feature.transport.SideMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.Tags;
import org.joml.Matrix4f;

/**
 * Sombreado de puertos al llevar la llave (0.7.0), como el Configurator de Mekanism: sobre el bloque
 * APUNTADO, cada cara se tiñe con el color de su modo en el canal que la llave toca (Normal acero, Empujar
 * cian, Tirar verde, Desconectada rojo; la cara del eje del motor no se pinta). Así se ve qué hace cada
 * puerto antes de hacer clic. Los colores son los del marco de la cruz (SideConfigWidget.frameColor).
 *
 * Tanques y motor: la cara entera, con 1/16 de margen. Cables y tuberías: solo el cuadrado del brazo en el
 * borde del bloque, que es donde está el puerto de verdad. Lo lleva la llave de Zenkai y cualquier ítem
 * del tag c:tools/wrench (en esas, el canal es el único del bloque o el primero).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class WrenchOverlay {
    private WrenchOverlay() {}

    private static final net.neoforged.neoforge.common.ItemAbility CONFIGURE =
            net.neoforged.neoforge.common.ItemAbility.get("wrench_configure");
    private static final float ALPHA = 0.38f;
    private static final float LIFT = 0.003f;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;
        ItemStack wrench = held(mc);
        if (wrench.isEmpty() || WrenchItem.rotating(wrench)) return;   // en modo Rotar no hay puertos que enseñar
        // Configurator de Mekanism: solo en sus modos de energía y fluidos (en los demás no es cosa nuestra).
        if (!(wrench.getItem() instanceof WrenchItem) && wrench.canPerformAction(CONFIGURE)) {
            WrenchItem.Mode mek = WrenchItem.mekanismMode(wrench);
            if (mek != WrenchItem.Mode.ENERGY && mek != WrenchItem.Mode.FLUID) return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockEntity(pos) instanceof SideConfigurable cfg) || cfg.sideChannels().isEmpty()) return;
        SideConfigurable.Channel channel = wrench.getItem() instanceof WrenchItem || wrench.canPerformAction(CONFIGURE)
                ? WrenchItem.channelFor(wrench, cfg) : cfg.sideChannels().get(0);
        boolean cable = cfg instanceof TransportBlockEntity t && t.isCableBlock();

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        ps.pushPose();
        ps.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = ps.last().pose();
        for (Direction d : Direction.values()) {
            if (cfg.sideLocked(d)) continue;
            int argb = color(cfg.sideMode(channel, d));
            if (!cable) {
                face(vc, m, d, 1f / 16f, 15f / 16f, 1f, argb);
                continue;
            }
            // Cable: el cuadrado del brazo. Con brazo, en el borde del bloque (su extremo); sin brazo, pegado
            // a la cara del NÚCLEO. Antes se pintaba siempre en el borde y las caras sin brazo dejaban
            // paneles flotando alrededor del cable (captura del usuario, 2026-11-17).
            float lo = (float) (TransportGeometry.CORE_MIN / 16.0), hi = (float) (TransportGeometry.CORE_MAX / 16.0);
            Look look = ((TransportBlockEntity) cfg).look(d);
            boolean arm = look == Look.NORMAL || look == Look.PUSH || look == Look.PULL;
            float plane = arm ? 1f : (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? hi : 1f - lo);
            face(vc, m, d, lo, hi, plane, argb);
        }
        buffers.endBatch(RenderType.debugQuads());
        ps.popPose();
    }

    private static ItemStack held(Minecraft mc) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = mc.player.getItemInHand(h);
            if (s.getItem() instanceof WrenchItem || s.is(Tags.Items.TOOLS_WRENCH) || s.canPerformAction(CONFIGURE)) return s;
        }
        return ItemStack.EMPTY;
    }

    private static int color(SideMode m) {
        return switch (m) {
            case NORMAL -> ZenkaiTechPalette.STEEL_HI;
            case PUSH -> ZenkaiTechPalette.CYAN_HI;
            case PULL -> ZenkaiTechPalette.LED_GREEN_MID;
            case NONE -> ZenkaiTechPalette.LED_RED;
        };
    }

    /**
     * Un cuadrado [lo, hi] en el plano de la cara d, levantado LIFT hacia fuera. {@code depth} es a qué
     * distancia del centro está ese plano, medida desde el lado de la cara: 1 = el borde del bloque.
     */
    private static void face(VertexConsumer vc, Matrix4f m, Direction d, float lo, float hi, float depth, int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float out = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? depth + LIFT : 1f - depth - LIFT;
        float[][] v = switch (d.getAxis()) {
            case X -> new float[][]{{out, lo, lo}, {out, hi, lo}, {out, hi, hi}, {out, lo, hi}};
            case Y -> new float[][]{{lo, out, lo}, {lo, out, hi}, {hi, out, hi}, {hi, out, lo}};
            case Z -> new float[][]{{lo, lo, out}, {lo, hi, out}, {hi, hi, out}, {hi, lo, out}};
        };
        for (float[] p : v) vc.addVertex(m, p[0], p[1], p[2]).setColor(r, g, b, ALPHA);
    }
}
