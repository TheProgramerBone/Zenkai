package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.ZenkaiPalLayers;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Pose de derribado PROPIA (pedido del usuario, 2026-11-14: «una animación custom para cuando el jugador está derribado, distinta a
 * la vanilla»). El derribado deja al jugador en {@code Pose.SWIMMING} (ver {@code DownedSystem} y {@code ClientZenkaiPalTick}), lo
 * que en vanilla anima la brazada de nado; este clip ({@code zenkai.downed}, de {@code tools/gen_downed_animation.py}) sustituye
 * brazos, piernas, torso y cabeza por la de un cuerpo herido, en la capa {@link ZenkaiPalLayers#DOWNED_LAYER}.
 * <p>
 * Se enciende y se apaga mirando la marca de derribado de CADA jugador visible ({@code PlayerStateFlags}, que llega sincronizada a
 * todos), no solo la del local; mismo patrón que {@link CarryAnimClient}. La orientación del cuerpo tumbado sigue siendo la de la
 * pose vanilla: el clip no toca la raíz. En primera persona propia la capa va en NONE (ver
 * {@code ZenkaiPalAnimations.applyFirstPersonModes}).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class DownedAnimClient {
    private DownedAnimClient() {}

    private static final ResourceLocation CLIP = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.downed");

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            var c = (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(p, ZenkaiPalLayers.DOWNED_LAYER);
            if (c == null) continue;
            boolean downed = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).flags().isDowned();
            boolean playing = CLIP.equals(ZenkaiTransitions.currentClip(c));
            if (downed && !playing) ZenkaiTransitions.play(c, CLIP, ZenkaiTransitions.TRANSFORM);
            else if (!downed && playing) ZenkaiTransitions.stop(c, ZenkaiTransitions.COMBAT);
        }
    }
}
