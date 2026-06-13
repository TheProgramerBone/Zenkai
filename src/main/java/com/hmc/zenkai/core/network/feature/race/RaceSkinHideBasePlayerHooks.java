package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
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

@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class RaceSkinHideBasePlayerHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-RaceSkin");

    private static final Map<UUID, Byte>      OLD_PARTS          = new HashMap<>();
    private static final Map<UUID, boolean[]> OLD_MODEL_PARTS    = new HashMap<>();
    private static final Set<UUID>            TOUCHED_THIS_FRAME = new HashSet<>();

    private static final EntityDataAccessor<Byte> SKIN_PARTS = resolveSkinPartsAccessor();

    private RaceSkinHideBasePlayerHooks() {}

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Byte> resolveSkinPartsAccessor() {
        try {
            Field f = Player.class.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
            f.setAccessible(true);
            return (EntityDataAccessor<Byte>) f.get(null);
        } catch (Throwable t) {
            LOGGER.error("[Zenkai] No pude acceder a DATA_PLAYER_MODE_CUSTOMISATION.", t);
            return null;
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre e) {
        AbstractClientPlayer player = (AbstractClientPlayer) e.getEntity();

        var stats  = player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = player.getData(DataAttachments.PLAYER_VISUAL.get());

        boolean customSkin = visual.shouldRenderRaceSkin();

        // Sin raza elegida o sin custom skin → vanilla completo, no tocamos nada
        if (!stats.isRaceChosen() || !customSkin) return;

        TOUCHED_THIS_FRAME.add(player.getUUID());
        PlayerModel<AbstractClientPlayer> model = e.getRenderer().getModel();

        // Ocultar skin parts (cape, etc.) y modelo base completo
        // — aplica para TODAS las razas cuando customSkin=true,
        //   incluyendo Human (su "Race Skin" reemplaza el modelo vanilla)
        if (SKIN_PARTS != null) {
            byte old = player.getEntityData().get(SKIN_PARTS);
            OLD_PARTS.put(player.getUUID(), old);
            player.getEntityData().set(SKIN_PARTS, (byte) 0);
        }
        model.setAllVisible(false);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post e) {
        AbstractClientPlayer player = (AbstractClientPlayer) e.getEntity();

        if (!TOUCHED_THIS_FRAME.remove(player.getUUID())) return;

        // Restaurar skin parts
        if (SKIN_PARTS != null) {
            Byte old = OLD_PARTS.remove(player.getUUID());
            if (old != null) player.getEntityData().set(SKIN_PARTS, old);
        }

        // Restaurar visibilidad completa del modelo
        OLD_MODEL_PARTS.remove(player.getUUID()); // limpiar por si quedó algo
        PlayerModel<AbstractClientPlayer> model = e.getRenderer().getModel();
        model.setAllVisible(true);
    }
}