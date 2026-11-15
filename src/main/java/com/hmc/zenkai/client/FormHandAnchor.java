package com.hmc.zenkai.client;

import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.RaceBodyResolver;
import com.hmc.zenkai.feature.race.ZenkaiFirstPersonBody;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Cuelga el objeto de la mano del PUÑO que se ve cuando el cuerpo de la forma tiene brazos propios (Oozaru y Súper
 * Oozaru), en vez de en la mano del jugador vanilla.
 *
 * EL PROBLEMA: ItemInHandLayer coloca el objeto con PlayerModel.translateToHand, es decir, en el brazo vanilla
 * (hombro en x ±5, y 22; objeto 10 px más abajo). El cuerpo del Oozaru es un geo de armadura con el hombro en
 * x ±11,25, y 27 y un brazo de tres huesos (brazo, antebrazo, mano) con el codo doblado: el puño queda unos 7,6 px de
 * modelo más fuera, más abajo y más adelante que el objeto (casi 1,5 bloques con la escala 3,0), y al balancear el
 * brazo la distancia crece porque cada uno gira alrededor de su propio hombro. Medido el 2026-11-13; el acorte del
 * antebrazo de gen_oozaru.py (2026-10-11) solo lo disimulaba.
 *
 * LA SOLUCIÓN: recorrer la cadena de huesos del geo hasta el hueso de la mano con las MISMAS operaciones que
 * GeoArmorRenderer (translate(0, 24/16, 0) + scale(-1,-1,1) y RenderUtil.prepMatrixForBone por hueso), copiando al
 * hueso armor*Arm la rotación y el offset del ModelPart vanilla tal como hace GeoArmorRenderer.applyBaseTransformations.
 * Así el objeto sigue al puño con las animaciones PAL sin calibrar nada a mano.
 *
 * Por qué se CALCULA y no se leen las matrices que GeckoLib guarda al pintar (GeoBone.setTrackingMatrices): la capa del
 * cuerpo racial (RaceSkinGeoArmorLayer) se añade al final de la lista de capas (RaceSkinRenderHooks) y corre DESPUÉS
 * de ItemInHandLayer, así que en ese momento la matriz guardada sería la del frame anterior o, peor, la de otro
 * jugador (el BakedGeoModel es compartido). Calcularla aquí no depende del orden de capas ni de que el cuerpo se
 * haya pintado.
 *
 * CONTRATO (opt-in por datos): solo se aplica a geos de la pechera racial que tienen huesos llamados "right_hand" /
 * "left_hand" colgando de armorRightArm / armorLeftArm. Hoy, solo oozaru_player.geo.json (tools/gen_oozaru.py). Un
 * cuerpo de forma nuevo con brazos que no coinciden con los vanilla solo tiene que nombrar así su hueso de la mano.
 */
public final class FormHandAnchor {
    private FormHandAnchor() {}

    /**
     * false = el objeto toma la POSICIÓN del puño pero la ORIENTACIÓN del brazo que mueve PAL (armor*Arm), como en un
     * jugador vanilla. true = también hereda los giros de reposo del brazo y el antebrazo del geo (el codo del Oozaru
     * dobla unos 24° hacia delante y la espada o la antorcha salían apuntando hacia arriba, capturas del 2026-11-13).
     */
    private static final boolean KEEP_HAND_TILT = false;

    /**
     * Sustituto de {@code translateToHand(arm, pose)}: deja la pila en un marco equivalente al del brazo vanilla, de
     * modo que las transformaciones que vienen después en ItemInHandLayer (rotar X -90, Y 180 y bajar 10 px) acaben
     * justo en el puño del geo. Devuelve false (sin tocar la pila) si el jugador no tiene un cuerpo con mano propia.
     */
    public static boolean translateToHand(LivingEntity entity, HumanoidModel<?> model, HumanoidArm arm, PoseStack pose) {
        if (!(entity instanceof Player player)) return false;
        // En la pasada de cuerpo de 1ª persona la mano que se ve es la del Saiyan (ZenkaiFirstPersonArmHooks): se queda
        // el comportamiento vanilla.
        if (ZenkaiFirstPersonBody.isBodyPass(player)) return false;
        // Sin cuerpo racial pintado (opción desactivada o sin raza) el jugador se ve vanilla: mano vanilla.
        if (!PlayerVisualAttachment.get(player).shouldRenderRaceSkin()
                || !PlayerStatsAttachment.get(player).isRaceChosen()) return false;

        BakedGeoModel geo = chestModel(player);
        if (geo == null) return false;
        boolean right = arm == HumanoidArm.RIGHT;
        GeoBone hand = geo.getBone(right ? "right_hand" : "left_hand").orElse(null);
        if (hand == null) return false;
        String armorName = right ? "armorRightArm" : "armorLeftArm";

        // Cadena raíz -> mano; tiene que pasar por armor*Arm (es el que recibe la pose de PAL).
        Deque<GeoBone> chain = new ArrayDeque<>();
        boolean viaArmor = false;
        for (GeoBone b = hand; b != null; b = b.getParent()) {
            chain.push(b);
            if (b.getName().equals(armorName)) viaArmor = true;
        }
        if (!viaArmor) return false;
        float[] grip = gripPoint(hand);
        if (grip == null) return false;

        ModelPart part = right ? model.rightArm : model.leftArm;
        // Espacio de GeckoLib (GeoArmorRenderer.actuallyRender).
        pose.pushPose();
        pose.translate(0, 24 / 16f, 0);
        pose.scale(-1, -1, 1);
        Matrix4f armFrame = null;
        Matrix3f armNormal = null;
        for (GeoBone b : chain) {
            if (b.getName().equals(armorName)) {
                // Lo que GeoArmorRenderer.applyBaseTransformations escribiría en el hueso (sin mutar el BakedGeoModel
                // compartido): rotación del ModelPart con X/Y invertidas y offset respecto al pivote vanilla.
                float px = right ? part.x + 5 : part.x - 5;
                prep(pose, b, px, 2 - part.y, part.z, -part.xRot, -part.yRot, part.zRot);
                armFrame = new Matrix4f(pose.last().pose());
                armNormal = new Matrix3f(pose.last().normal());
            } else {
                RenderUtil.prepMatrixForBone(pose, b);
            }
        }
        pose.translate(grip[0], grip[1], grip[2]);
        Matrix4f handFrame = new Matrix4f(pose.last().pose());
        Matrix3f handNormal = new Matrix3f(pose.last().normal());
        pose.popPose();
        // Marco final: el de la mano entero o, sin KEEP_HAND_TILT, los ejes del brazo de PAL llevados al punto de
        // agarre. (El escalado del jugador va en los ejes de los dos, así que el objeto sigue creciendo con la forma.)
        boolean tilt = KEEP_HAND_TILT || armFrame == null;
        pose.last().pose().set(tilt ? handFrame
                : armFrame.setTranslation(handFrame.m30(), handFrame.m31(), handFrame.m32()));
        // La matriz de normales va con la orientación elegida, o la luz del objeto saldría girada.
        pose.last().normal().set(tilt ? handNormal : armNormal);
        // De vuelta a ejes de ModelPart (girar 180° en Z = el scale(-1,-1,1) de antes sin invertir normales) y
        // retrocediendo el punto donde ItemInHandLayer suelta el objeto en un brazo vanilla: (-s/16, 10/16, -2/16) con
        // s = +1 derecho / -1 izquierdo (sale de su translate(±1/16, 0.125, -0.625) tras las dos rotaciones).
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        float s = right ? 1 : -1;
        pose.translate(s / 16f, -10 / 16f, 2 / 16f);
        return true;
    }

    /** prepMatrixForBone con posición y rotación dadas en vez de las guardadas en el hueso. */
    private static void prep(PoseStack pose, GeoBone b, float posX, float posY, float posZ,
                             float rotX, float rotY, float rotZ) {
        pose.translate(-posX / 16f, posY / 16f, posZ / 16f);
        pose.translate(b.getPivotX() / 16f, b.getPivotY() / 16f, b.getPivotZ() / 16f);
        RenderUtil.optionalRotateZYX(pose, rotZ, rotY, rotX);
        pose.scale(b.getScaleX(), b.getScaleY(), b.getScaleZ());
        pose.translate(-b.getPivotX() / 16f, -b.getPivotY() / 16f, -b.getPivotZ() / 16f);
    }

    /**
     * Punto de agarre en el espacio del hueso (coordenadas internas de GeckoLib, en bloques, con X invertida): centro
     * del puño en X e Y y su cara DELANTERA (-Z), igual que el objeto vanilla sale en la cara delantera del brazo.
     */
    private static float @Nullable [] gripPoint(GeoBone hand) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean any = false;
        for (GeoCube c : hand.getCubes()) {
            for (GeoQuad q : c.quads()) {
                if (q == null) continue;
                for (GeoVertex v : q.vertices()) {
                    Vector3f p = v.position();
                    minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
                    minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
                    minZ = Math.min(minZ, p.z());
                    any = true;
                }
            }
        }
        if (!any) return null;
        return new float[]{(minX + maxX) / 2f, (minY + maxY) / 2f, minZ};
    }

    /** Modelo horneado de la pechera racial que el render le pone al jugador (ya con el cuerpo de la forma). */
    private static @Nullable BakedGeoModel chestModel(Player player) {
        ItemStack chest = RaceBodyResolver.resolve(player, EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof GeoLayerArmorItem g)) return null;
        ResourceLocation m = g.resolveModel(chest);
        return m == null ? null : GeckoLibCache.getBakedModels().get(m);
    }
}
