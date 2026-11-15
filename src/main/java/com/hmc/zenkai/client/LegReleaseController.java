package com.hmc.zenkai.client;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Controlador de las capas cuya pose se SOSTIENE mientras el jugador puede seguir andando (técnicas de ki, bloqueo, pose de
 * combate): suelta las PIERNAS cuando el jugador se mueve y, si vuela, las cede por completo a la capa de vuelo.
 * <p>
 * Queja del usuario (2026-11-14): «se ve chistoso cuando está la animación de un ataque y el jugador comienza a caminar y se
 * quedan estáticas las piernas». Causa: PAL arranca cada hueso con la pose vanilla (que ya trae el paso de la caminata) y el
 * clip SUSTITUYE la rotación de las piernas, así que el jugador se deslizaba con las piernas clavadas en la postura. Aquí se
 * guarda lo que traía la pierna antes de la capa y, según cuánto se mueve el jugador, se devuelve eso en vez de lo del clip:
 * quieto conserva la postura entera; andando, las piernas dan los pasos de siempre y brazos, torso y cabeza siguen en la pose.
 * <p>
 * Mismo punto de extensión que {@code FlyAnimationController} (get3DTransformRaw), sin mixin y sin tocar los clips. Los golpes
 * físicos (PHYS_LAYER) quedan fuera a propósito: son cortos y mueven las piernas con intención (patada, dash). El resto de capas
 * (transformación, cargar un cuerpo, transmisión) tampoco lo usan, y el vuelo tampoco: sus piernas son las de su clip.
 * <p>
 * EN VUELO (corrección del usuario, 2026-11-14) las piernas NO vuelven a la caminata vanilla sino a lo que dejó la capa de vuelo (el valor
 * previo, de menor prioridad, que ya trae sus piernas), y siempre, también flotando quieto: sin esto, cargar una técnica volando dejaba las
 * piernas de la pose de ki clavadas, y soltarlas a la vanilla las ponía a caminar en el aire.
 * <p>
 * La medida del movimiento es la misma que usa el modelo vanilla para balancear las piernas ({@code walkAnimation.speed}), así
 * que vale igual para el jugador local y para los demás, y la mezcla entra y sale suave en vez de saltar.
 */
public class LegReleaseController extends PlayerAnimationController {

    /** Por debajo de esta velocidad de paso la pose manda entera; por encima de FULL, las piernas son del jugador. */
    private static final float START = 0.06f, FULL = 0.30f;

    public LegReleaseController(AbstractClientPlayer player, AnimationStateHandler handler) {
        super(player, handler);
    }

    @Override
    public PlayerAnimBone get3DTransformRaw(@NotNull PlayerAnimBone bone) {
        String name = bone.getName();
        if (!"right_leg".equals(name) && !"left_leg".equals(name)) return super.get3DTransformRaw(bone);

        float k = release();
        if (k <= 0f) return super.get3DTransformRaw(bone);

        float rx = bone.rotX, ry = bone.rotY, rz = bone.rotZ;
        float px = bone.positionX, py = bone.positionY, pz = bone.positionZ;
        bone = super.get3DTransformRaw(bone);
        bone.rotX = Mth.lerp(k, bone.rotX, rx);
        bone.rotY = Mth.lerp(k, bone.rotY, ry);
        bone.rotZ = Mth.lerp(k, bone.rotZ, rz);
        bone.positionX = Mth.lerp(k, bone.positionX, px);
        bone.positionY = Mth.lerp(k, bone.positionY, py);
        bone.positionZ = Mth.lerp(k, bone.positionZ, pz);
        return bone;
    }

    /** 0 = quieto (manda la pose), 1 = andando (mandan las piernas de la caminata). */
    private float release() {
        if (com.hmc.zenkai.client.ClientZenkaiPalTick.flyPoseOf(getPlayer().getUUID()).flying()) return 1f;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float speed = getPlayer().walkAnimation.speed(partial);
        float k = Mth.clamp((speed - START) / (FULL - START), 0f, 1f);
        return k * k * (3f - 2f * k);
    }
}
