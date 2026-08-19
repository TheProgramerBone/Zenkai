package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.technique.TechniquePosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Posición en el mundo de las manos y la cabeza de cada jugador, tomada de los huesos ya
 * animados por PAL. La llena HandAnchorLayer durante el render de cada jugador y la consume
 * KiChargeRenderer.
 * POR QUÉ HACE FALTA CAPTURAR Y NO SE PUEDE LEER DIRECTAMENTE
 * ----------------------------------------------------------
 * PlayerRenderer y su PlayerModel son UNA instancia compartida por todos los jugadores: leer
 * model.rightArm desde RenderLevelStageEvent daría la pose del ÚLTIMO dibujado, así que con
 * dos jugadores cargando a la vez ambas esferas irían a la mano del mismo.
 * ESPACIO Y VIGENCIA
 * ------------------
 * Se guarda en coordenadas RELATIVAS A LA CÁMARA, que es el espacio en el que trabaja el
 * PoseStack tanto en el render de entidades como en AFTER_PARTICLES. Como las dos cosas pasan
 * en el mismo frame con la misma cámara, no hace falta convertir. Cada entrada lleva el número
 * de frame y solo vale para ese: un dato de un frame anterior sería una posición vieja con una
 * cámara que ya se movió.
 * LA COMPUERTA
 * ------------
 * El recuadro del editor de técnicas también renderiza al jugador, con un PoseStack de GUI.
 * Capturar ahí metería coordenadas de pantalla en la caché. capturing() solo es cierto entre
 * AFTER_SKY (antes de las entidades) y AFTER_PARTICLES (después de consumir), que es
 * exactamente la ventana del mundo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class PlayerHandTracker {

    private PlayerHandTracker() {}

    /** Tres puntos distintos en la cabeza, no uno. El Makankosappo (set 10) ancla en la FRENTE
     *  y un Masenko también; devolver el punto de la boca para las tres dejaba los dedos en la
     *  barbilla. Son tres multiplicaciones de matriz más por jugador y frame: nada. */
    public record Anchors(Vec3 rightHand, Vec3 leftHand,
                          Vec3 mouth, Vec3 forehead, Vec3 eyes, long frame) {

        public Vec3 resolve(TechniquePosition pos) {
            return switch (pos) {
                case RIGHT_HAND -> rightHand;
                case LEFT_HAND  -> leftHand;
                case BOTH_HANDS -> rightHand.add(leftHand).scale(0.5);
                case MOUTH      -> mouth;
                case FOREHEAD   -> forehead;
                case EYES       -> eyes;
            };
        }
    }

    private static final Map<Integer, Anchors> CACHE = new ConcurrentHashMap<>();

    private static boolean inLevelPass = false;
    private static long frame = 0L;

    public static boolean capturing() { return inLevelPass; }

    public static void put(int entityId, Vec3 right, Vec3 left,
                           Vec3 mouth, Vec3 forehead, Vec3 eyes) {
        if (!inLevelPass) return;
        CACHE.put(entityId, new Anchors(right, left, mouth, forehead, eyes, frame));
    }

    /** null si no hay dato de ESTE frame: primera persona (PAL filtra la capa), jugador fuera
     *  de pantalla, o culling. El llamante cae al respaldo de TechniquePosition. */
    @Nullable
    public static Anchors get(int entityId) {
        Anchors a = CACHE.get(entityId);
        return (a != null && a.frame() == frame) ? a : null;
    }

    public static void clear() { CACHE.clear(); }

    @SubscribeEvent
    public static void onLevelStage(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            frame++;
            inLevelPass = true;
            // Barrido barato: sin esto la caché crece con cada jugador que se va.
            if ((frame & 0xFF) == 0) {
                var level = Minecraft.getInstance().level;
                if (level != null) CACHE.keySet().removeIf(id -> level.getEntity(id) == null);
            }
        } else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            inLevelPass = false;
        }
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            renderer.addLayer(new HandAnchorLayer(renderer));
        }
    }
}