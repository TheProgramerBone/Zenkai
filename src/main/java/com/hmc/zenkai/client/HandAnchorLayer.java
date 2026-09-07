package com.hmc.zenkai.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * No dibuja nada: solo lee dónde han quedado los huesos después de que PAL los anime y lo deja
 * en PlayerHandTracker.
 * Va como render layer y no como evento porque necesita el PoseStack CON el modelo ya posado y
 * en espacio de entidad — es la misma técnica con la que el juego cuelga los items de la mano.
 * (RenderLivingEvent.Post NO vale como alternativa: NeoForge lo dispara DESPUÉS del popPose de
 * LivingEntityRenderer.render, o sea con la pila ya de vuelta en espacio de cámara y sin la
 * transformación de la entidad.)
 * UNIDADES: dentro del modelo se trabaja en 1/16 de bloque y con el eje Y INVERTIDO (por el
 * scale(-1,-1,1) de LivingEntityRenderer). Por eso "arriba" es negativo y la punta del brazo,
 * que mide 12, está a +10 desde su pivote — el mismo 0.625 que usa PlayerItemInHandLayer.
 *
 * POR QUÉ EXTIENDE PlayerItemInHandLayer SIN DIBUJAR NINGÚN ÍTEM
 * -------------------------------------------------------------
 * Es la ÚNICA forma de que esta capa sobreviva a la pasada de primera persona de PAL. Su
 * LivingEntityRendererMixin envuelve el bucle de capas con un filtro que, cuando el jugador
 * es el LOCAL y estamos en la pasada de 1ª persona, deja pasar exactamente dos clases:
 *   return layer instanceof PlayerItemInHandLayer || layer instanceof HumanoidArmorLayer;
 * Es una lista blanca por TIPO, sin API para añadirse a ella. Con la capa como RenderLayer
 * normal, en primera persona nunca corría: no había datos de hueso, KiChargeRenderer caía a
 * su respaldo de offset fijo y la esfera de carga se quedaba clavada delante de la cara
 * mientras en tercera persona seguía las manos. Heredar de la clase permitida (y NO llamar a
 * super.render(), así que sigue sin dibujar nada) la mete en la lista blanca.
 * Lo que NO es este truco: no depende del orden de pasadas. PAL renderiza el cuerpo de 1ª
 * persona DENTRO de LevelRenderer.renderLevel —falsea camera.isDetached() para que la entidad
 * local se dibuje como cualquier otra (ver su LevelRendererMixin)— así que cae de lleno en la
 * ventana AFTER_SKY..AFTER_PARTICLES en la que PlayerHandTracker.capturing() es cierto y en la
 * que KiChargeRenderer consume. Solo faltaba el permiso.
 * Si una versión futura de PAL cambia ese filtro, el síntoma será exactamente el mismo de
 * antes: la esfera correcta en tercera persona y pegada a la cara en primera.
 */
public class HandAnchorLayer extends PlayerItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Punta del brazo desde el pivote del hombro, en unidades de modelo. */
    private static final float ARM_TIP = 10.0F / 16.0F;

    /** Separación desde el centro de la cabeza hasta justo delante de la cara. */
    private static final float FACE_Z = -4.5F / 16.0F;

    /** Alturas dentro del hueso de la cabeza, que va de 0 (cuello) a -8 (coronilla) con el eje
     *  Y invertido. Ajusta estos tres si los dedos del Makankosappo no caen donde toca. */
    private static final float MOUTH_Y    = -2.5F / 16.0F;
    private static final float EYES_Y     = -4.5F / 16.0F;
    private static final float FOREHEAD_Y = -6.0F / 16.0F;

    public HandAnchorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                           ItemInHandRenderer itemInHandRenderer) {
        super(parent, itemInHandRenderer);
    }

    @Override
    public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffer, int light,
                       @NotNull AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        if (!PlayerHandTracker.capturing()) return;   // render de GUI: no envenenar la caché

        PlayerModel<AbstractClientPlayer> model = getParentModel();

        Vec3 right = anchor(pose, model.rightArm, 0.0F, ARM_TIP, 0.0F);
        Vec3 left  = anchor(pose, model.leftArm,  0.0F, ARM_TIP, 0.0F);

        Vec3 mouth    = anchor(pose, model.head, 0.0F, MOUTH_Y,    FACE_Z);
        Vec3 eyes     = anchor(pose, model.head, 0.0F, EYES_Y,     FACE_Z);
        Vec3 forehead = anchor(pose, model.head, 0.0F, FOREHEAD_Y, FACE_Z);

        PlayerHandTracker.put(player.getId(), right, left, mouth, forehead, eyes);
        // A PROPÓSITO sin super.render(): heredamos de PlayerItemInHandLayer solo para pasar su
        // filtro de 1ª persona (ver el javadoc de la clase), no para dibujar ítems — de eso ya
        // se encarga la capa real que PlayerRenderer trae de serie. Llamar a super pintaría el
        // objeto de la mano DOS veces.
    }

    /** Posición del punto (x,y,z) del hueso, en espacio relativo a la cámara. */
    private static Vec3 anchor(PoseStack pose, ModelPart part, float x, float y, float z) {
        pose.pushPose();
        part.translateAndRotate(pose);          // pivote + rotaciones YA escritas por PAL
        pose.translate(x, y, z);
        Vector3f p = pose.last().pose().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
        pose.popPose();
        return new Vec3(p.x(), p.y(), p.z());
    }
}