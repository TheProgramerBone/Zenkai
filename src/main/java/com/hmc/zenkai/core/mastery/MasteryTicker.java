package com.hmc.zenkai.core.mastery;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.forms.FormIds;
import com.hmc.zenkai.core.network.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.core.network.feature.player.SyncPlayerFormPacket;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Acumula maestría de FORMA en el servidor: mientras el jugador esté transformado
 * (formId != base), suma StatsConfig.formMasteryPerMinute() repartido por segundo.
 * La maestría de TÉCNICA se suma en el punto de uso (KiFirePacket / PhysicalCombatServer).
 * Sync: el mapa viaja dentro del NBT de PlayerFormAttachment; aquí se reenvía cada
 * SYNC_EVERY ticks mientras acumula (es dato de UI, no hace falta tick a tick).
 * ⚠ API a verificar al compilar: PlayerTickEvent.Post (NeoForge 1.21.1, paquete
 *   net.neoforged.neoforge.event.tick) — si tu mapping difiere, es el tick de jugador post.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class MasteryTicker {
    private MasteryTicker() {}

    private static final int ACCRUE_EVERY = 20;   // cada segundo
    private static final int SYNC_EVERY   = 200;  // cada 10 s

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        PlayerFormAttachment form = sp.getData(DataAttachments.PLAYER_FORM.get());
        if (FormIds.BASE.equals(form.getFormId())) return;

        long t = sp.level().getGameTime();
        if (t % ACCRUE_EVERY == 0) {
            float perSecond = (float) (StatsConfig.formMasteryPerMinute() / 60.0);
            form.addFormMastery(form.getFormId(), perSecond);
        }
        if (t % SYNC_EVERY == 0) {
            PacketDistributor.sendToPlayer(sp, SyncPlayerFormPacket.from(sp));
        }
    }
}