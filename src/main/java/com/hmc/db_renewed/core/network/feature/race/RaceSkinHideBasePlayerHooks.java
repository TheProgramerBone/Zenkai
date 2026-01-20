package com.hmc.db_renewed.core.network.feature.race;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = DragonBlockRenewed.MOD_ID, value = Dist.CLIENT)
public final class RaceSkinHideBasePlayerHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("DBR-RaceSkin");

    private static final Map<UUID, Byte> OLD_PARTS = new HashMap<>();
    private static final Set<UUID> LOGGED = new HashSet<>();

    // Para saber si en este frame realmente tocamos algo (y así restaurar seguro)
    private static final Set<UUID> TOUCHED_THIS_FRAME = new HashSet<>();

    // Cache por reflexión (porque el campo es protected)
    private static final EntityDataAccessor<Byte> SKIN_PARTS = resolveSkinPartsAccessor();

    private RaceSkinHideBasePlayerHooks() {}

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Byte> resolveSkinPartsAccessor() {
        try {
            Field f = Player.class.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
            f.setAccessible(true);
            return (EntityDataAccessor<Byte>) f.get(null);
        } catch (Throwable t) {
            LOGGER.error("[DBR] No pude acceder al accessor de skin parts (DATA_PLAYER_MODE_CUSTOMISATION). " +
                    "Overlays pueden seguir apareciendo.", t);
            return null;
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre e) {
        AbstractClientPlayer player = (AbstractClientPlayer) e.getEntity();

        var stats  = player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = player.getData(DataAttachments.PLAYER_VISUAL.get());

        Race race = PlayerStatsAttachment.get(player).getRace();

        // Si es humano, nunca tocamos nada
        if (race == Race.HUMAN) return;

        // >>> FIX CLAVE <<<
        // Si el jugador está en "Vanilla Skin" (o en general NO quiere ocultar el cuerpo vanilla),
        // NO escondas el modelo base ni apagues overlays.
        if (!visual.shouldHideVanillaBody()) return;

        // Opcional: si ni siquiera vas a renderizar el race skin, no tiene sentido ocultar el vanilla.
        // (Esto te protege de estados raros.)
        if (!visual.shouldRenderRaceSkin()) return;

        TOUCHED_THIS_FRAME.add(player.getUUID());

        // 1) Apagar overlays desde el byte (si pudimos resolver el accessor)
        if (SKIN_PARTS != null) {
            byte old = player.getEntityData().get(SKIN_PARTS);
            OLD_PARTS.put(player.getUUID(), old);
            player.getEntityData().set(SKIN_PARTS, (byte) 0);

            if (LOGGED.add(player.getUUID())) {
                LOGGER.info("[DBR] Skin overlays OFF (skinParts=0) for player={} race={} old={}",
                        player.getName().getString(), race, old);
            }
        }

        // 2) Ocultar el modelo base (para que se vea la GeoArmor de raza)
        PlayerModel<AbstractClientPlayer> m = e.getRenderer().getModel();
        m.setAllVisible(false);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post e) {
        AbstractClientPlayer player = (AbstractClientPlayer) e.getEntity();

        // Solo restaurar si en Pre realmente tocamos algo
        if (!TOUCHED_THIS_FRAME.remove(player.getUUID())) return;

        // Restaurar skin parts
        if (SKIN_PARTS != null) {
            Byte old = OLD_PARTS.remove(player.getUUID());
            if (old != null) {
                player.getEntityData().set(SKIN_PARTS, old);
            }
        }

        // Restaurar visibilidad del modelo para no afectar otros renders
        PlayerModel<AbstractClientPlayer> m = e.getRenderer().getModel();
        m.setAllVisible(true);
    }
}
