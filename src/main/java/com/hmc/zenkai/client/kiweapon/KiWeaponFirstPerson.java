package com.hmc.zenkai.client.kiweapon;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.kiweapon.KiWeaponState;
import com.hmc.zenkai.feature.race.ZenkaiFirstPersonArmHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Arma de ki en 1ª persona. Con la mano principal vacía vanilla solo dibuja el brazo
 * (RenderArmEvent), así que el arma se ancla a ESE brazo: mismo marco que
 * {@link ZenkaiFirstPersonArmHooks} (que a veces lo sustituye por el de la raza, con el hombro en
 * OFF_*) más una calibración propia, ajustable en juego con {@code /zenkaiweapon}.
 *
 * Prioridad ALTA: ZenkaiFirstPersonArmHooks CANCELA este evento cuando sustituye el brazo, y un
 * evento cancelado no llega a los suscriptores normales. Pintar el arma antes no estorba: abre y
 * cierra su propio pushPose.
 * Cuando PAL dibuja el cuerpo entero la mano vanilla ya está cancelada y el arma la pone
 * {@link KiWeaponLayer}; aquí se salta para no dibujarla dos veces.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiWeaponFirstPerson {
    private KiWeaponFirstPerson() {}

    /** Qué ruta dibujó el arma por última vez en 1ª persona (diagnóstico de /zenkaiweapon get). */
    public static String lastPath = "ninguna";

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderArm(RenderArmEvent e) {
        Player player = e.getPlayer();
        if (KiWeaponState.variantForArm(player, e.getArm()) == null) return;
        if (ZenkaiFirstPersonArmHooks.palFullBodyActive(player)) { lastPath = "PAL cuerpo entero (KiWeaponLayer)"; return; }

        PoseStack pose = e.getPoseStack();
        boolean left = e.getArm() == HumanoidArm.LEFT;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);

        // Con el brazo de la raza sustituido lo dibuja ZenkaiFirstPersonArmHooks al terminar el
        // brazo, con la matriz real de su hueso (drawOnRaceArm): aquí no hay hombro que adivinar.
        if (ZenkaiFirstPersonArmHooks.raceArmReplaced(player)) { lastPath = "brazo de raza (matriz del hueso)"; return; }
        lastPath = "brazo vanilla (pivote de hombro)";

        pose.pushPose();
        // Brazo vanilla: su pivote de hombro, igual que translateToHand con el brazo a 0.
        pose.translate((left ? 5.0F : -5.0F) / 16.0F, 2.0F / 16.0F, 0.0F);
        itemFrame(pose, left);
        KiWeaponVfx.drawNow(pose, e.getMultiBufferSource(), player, partial, e.getArm());
        pose.popPose();
    }

    /** Marco de ítem de vanilla (igual que KiWeaponLayer), desde el pivote del hombro. */
    private static void itemFrame(PoseStack pose, boolean left) {
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
    }

    /**
     * Arma sobre el brazo de la raza. {@code bonePose}/{@code boneNormal} son la matriz del hueso
     * del brazo en el espacio local del geo ya HORNEADO (px/16, Y hacia arriba, X negada respecto al .geo.json: hombro derecho en
     * (+5, 22, 0)); se pasa a un marco tipo vanilla (Y hacia abajo, origen en el hombro) y de ahí al marco de ítem.
     */
    public static void drawOnRaceArm(Player player, HumanoidArm arm, Matrix4f bonePose, Matrix3f boneNormal,
                                     MultiBufferSource buffers) {
        if (KiWeaponState.variantForArm(player, arm) == null) return;
        boolean left = arm == HumanoidArm.LEFT;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);

        PoseStack pose = new PoseStack();
        pose.last().pose().set(bonePose);
        pose.last().normal().set(boneNormal);
        // GeckoLib NIEGA la X de pivotes y cubos al hornear el geo (BakedModelFactory): el hombro del
        // brazo derecho (-5 en el .geo.json) vive en +5 en el espacio del hueso, y el izquierdo en -5.
        // Para volver al marco vanilla (Y hacia abajo, brazo derecho en -X) hay que invertir X e Y.
        pose.translate((left ? -5.0F : 5.0F) / 16.0F, 22.0F / 16.0F, 0.0F);
        pose.scale(-1.0F, -1.0F, 1.0F);
        itemFrame(pose, left);
        KiWeaponVfx.drawNow(pose, buffers, player, partial, arm);
    }

    /** Los ids de entidad se reutilizan entre mundos: sin esto un arma "fantasma" podría
     *  aparecer en el siguiente servidor. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        KiWeaponState.clientClear();
        KiWeaponTrail.clear();
    }
}
