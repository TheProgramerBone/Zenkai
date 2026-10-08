package com.hmc.zenkai.client.clash;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CombatModeClientState;
import com.hmc.zenkai.client.action.ActionStateClient;
import com.hmc.zenkai.content.entity.technique.ClashOrbEntity;
import com.hmc.zenkai.feature.technique.clash.ClashJoinPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Unirse a un choque de haces ajeno (fase 2 del choque por equipos). SIN tecla nueva, pedido
 * explícito del usuario: con la mira sobre un luchador al que puedes ayudar aparece sobre la mira
 * el icono de una mano con «Clic derecho para ayudar» — mismo lenguaje que el icono de la
 * Transmisión Instantánea al pulsar TAB (InstantTransmissionCrosshairOverlay) — y el clic derecho
 * («Usar objeto», que sí se puede reasignar en Controles) manda ClashJoinPacket en vez de usar el
 * objeto de la mano.
 *
 * Además, una MANO SOBRE LA CABEZA (encima del nametag) de cada luchador al que puedes ayudar,
 * para encontrarlo sin tener que apuntar (pedido del usuario: «como cuando aparece un nametag»):
 * tenue si estás más lejos que clash.join_range, a color completo si ya puedes unirte, y algo más
 * grande sobre el que apuntas. Se pinta en el pase del mundo (RenderLevelStageEvent) y no en
 * RenderLivingEvent porque los NPC GeckoLib (la Sombra) no pasan por LivingEntityRenderer.
 *
 * Saber si alguien está en un choque sale de ClashOrbEntity, que se sincroniza a cualquiera que
 * esté cerca (su roster de ids por lado, si admite gente, el tamaño máximo y la distancia). Es
 * solo una PISTA para enseñar el aviso: el servidor (ClashManager.join) lo valida todo de nuevo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ClashJoinClient {
    private ClashJoinClient() {}

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/clash_hud.png");
    /** == tools/gen_clash_hud.py, «mano». */
    private static final int U_HAND = 184, V_HAND = 0, HAND = 16;
    /** Alcance del raycast que busca al luchador apuntado (la distancia real la decide la esfera). */
    private static final double PICK_RANGE = 16.0;
    /** Radio en que se buscan esferas de choque alrededor del luchador apuntado. */
    private static final double ORB_SEARCH = 48.0;

    /** Id de entidad del luchador al que se puede ayudar ahora mismo, o -1. */
    private static int target = -1;

    /** Luchadores a los que puedes ayudar (id -> ya estás a distancia de unirte), para la mano
     *  sobre la cabeza. Se recalcula cada tick. */
    private static final java.util.Map<Integer, Boolean> JOINABLE = new java.util.HashMap<>();
    /** Hasta dónde se enseña la mano sobre la cabeza. */
    private static final double SHOW_RANGE = 24.0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        target = findTarget();
        collectJoinable();
    }

    private static void collectJoinable() {
        JOINABLE.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (ClashClientState.active() || ActionStateClient.local().inClash()) return;
        for (ClashOrbEntity orb : mc.level.getEntitiesOfClass(ClashOrbEntity.class,
                mc.player.getBoundingBox().inflate(SHOW_RANGE + ORB_SEARCH))) {
            if (!orb.joinOpen()) continue;
            for (int side = 0; side < 2; side++) {
                if (orb.sideSize(side) >= orb.teamMax()) continue;
                for (int id : orb.sideIds(side)) {
                    Entity e = mc.level.getEntity(id);
                    if (e == null || !e.isAlive() || e == mc.player) continue;
                    double d = mc.player.distanceTo(e);
                    if (d > SHOW_RANGE) continue;
                    JOINABLE.put(id, d <= orb.joinRange());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(net.neoforged.neoforge.client.event.RenderLevelStageEvent e) {
        if (e.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (JOINABLE.isEmpty() || mc.level == null || mc.options.hideGui) return;
        var cam = e.getCamera();
        var camPos = cam.getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        var pose = e.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var type = net.minecraft.client.renderer.RenderType.text(TEX);
        var vc = buffers.getBuffer(type);
        float u0 = U_HAND / 256f, u1 = (U_HAND + HAND) / 256f, v0 = V_HAND / 64f, v1 = (V_HAND + HAND) / 64f;
        for (var en : JOINABLE.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            if (ent == null) continue;
            boolean aimed = en.getKey() == target;
            // Encima del nametag (que va a altura + 0,5) y con un leve vaivén para que se vea viva.
            double bob = Math.sin((mc.level.getGameTime() + pt) * 0.15) * 0.06;
            var pos = ent.getPosition(pt).add(0, ent.getBbHeight() + 0.95 + bob, 0).subtract(camPos);
            pose.pushPose();
            pose.translate(pos.x, pos.y, pos.z);
            pose.mulPose(cam.rotation());
            float sc = 0.025f * (aimed ? 1.35f : 1f);
            pose.scale(sc, -sc, sc);
            var m = pose.last().pose();
            int a = en.getValue() ? 255 : 110;
            vc.addVertex(m, -8f, -16f, 0f).setColor(255, 255, 255, a).setUv(u0, v0).setLight(0xF000F0);
            vc.addVertex(m, -8f, 0f, 0f).setColor(255, 255, 255, a).setUv(u0, v1).setLight(0xF000F0);
            vc.addVertex(m, 8f, 0f, 0f).setColor(255, 255, 255, a).setUv(u1, v1).setLight(0xF000F0);
            vc.addVertex(m, 8f, -16f, 0f).setColor(255, 255, 255, a).setUv(u1, v0).setLight(0xF000F0);
            pose.popPose();
        }
        buffers.endBatch(type);
    }

    private static int findTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return -1;
        if (ClashClientState.active() || ActionStateClient.local().inClash()) return -1;
        // Raycast propio: mc.hitResult solo llega a la distancia de interacción vanilla (~3
        // bloques) y clash.join_range es de 6 por defecto. Un bloque delante lo tapa.
        var eye = mc.player.getEyePosition();
        var end = eye.add(mc.player.getViewVector(1f).scale(PICK_RANGE));
        if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            end = mc.hitResult.getLocation();
        }
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                mc.player, eye, end, mc.player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator(), eye.distanceToSqr(end));
        if (hit == null) return -1;
        Entity e = hit.getEntity();
        for (ClashOrbEntity orb : mc.level.getEntitiesOfClass(ClashOrbEntity.class,
                new AABB(e.blockPosition()).inflate(ORB_SEARCH))) {
            int side = orb.sideOf(e.getId());
            if (side < 0) continue;
            if (!orb.joinOpen() || orb.sideSize(side) >= orb.teamMax()) return -1;
            if (mc.player.distanceTo(e) > orb.joinRange()) return -1;
            return e.getId();
        }
        return -1;
    }

    /** Clic derecho con un luchador apuntado: unirse en vez de usar el objeto. */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem() || target < 0) return;
        PacketDistributor.sendToServer(new ClashJoinPacket(target, CombatModeClientState.selected()));
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (target < 0 || mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        // A escala ×2 fija, como el HUD del choque (ver ClashBarOverlay, HUD_SCALE).
        double real = mc.getWindow().getGuiScale();
        float k = (float) (Math.min(2.0, real) / real);
        g.pose().pushPose();
        g.pose().scale(k, k, 1f);
        int w = Math.round(g.guiWidth() / k), h = Math.round(g.guiHeight() / k);
        RenderSystem.enableBlend();
        int x = w / 2 - HAND / 2, y = h / 2 - HAND - 10;
        g.blit(TEX, x, y, U_HAND, V_HAND, HAND, HAND, 256, 64);
        Component hint = Component.translatable("zenkai.clash.help");
        int tw = mc.font.width(hint);
        int ty = h / 2 + 12;
        g.fill(w / 2 - tw / 2 - 3, ty - 2, w / 2 + tw / 2 + 3, ty + 9, 0x900C0C10);
        g.drawCenteredString(mc.font, hint, w / 2, ty, 0xFFFFD84A);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }
}
