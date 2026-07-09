package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;

/**
 * Ajuste de HITBOX + CÁMARA durante el boost de vuelo, SIN cambiar la pose (así no se frena el
 * vuelo ni se inclina el modelo). Se dispara al llamar refreshDimensions() en la transición de boost.
 *  - Hitbox: caja 0.6 x 0.6 horizontales (como "acostado"), autoritativa en servidor y visible al local.
 *  - Cámara 1ª persona: la altura de ojos va DENTRO de EntityDimensions (1.21), así que al fijarla aquí
 *    con withEyeHeight(...) baja la cámara al recalcularse. NO hacen falta mixins.
 * El flag lo pone: el servidor (FlyBoostPacket) y el cliente local (ClientZenkaiPalTick.applyLocalBoost).
 * Corre en ambos lados (game bus). CUIDADO: EntityEvent.Size también se dispara en el constructor de la
 * entidad; ahí el attachment por defecto trae flyBoosting=false, así que no se sobrescribe nada.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class BoostSizeHandler {
    private BoostSizeHandler() {}

    // Diales tuneables. BOOST_EYE controla qué tan baja queda la cámara en 1ª persona.
    private static final float BOOST_WIDTH  = 0.6f;
    private static final float BOOST_HEIGHT = 0.6f;
    private static final float BOOST_EYE    = 0.5f;

    @SubscribeEvent
    public static void onSize(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof Player p)) return;

        var att = p.getData(DataAttachments.PLAYER_STATS.get());
        if (att.flags().isFlyBoosting()) {
            // scalable() respeta el atributo de escala del jugador; withEyeHeight baja la cámara.
            e.setNewSize(EntityDimensions.scalable(BOOST_WIDTH, BOOST_HEIGHT).withEyeHeight(BOOST_EYE));
        }
    }
}