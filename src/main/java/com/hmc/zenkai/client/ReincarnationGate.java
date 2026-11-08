package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.RaceSelectionScreen;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Una reencarnación a medias no puede quedarse a medias: mientras el servidor diga que el jugador está reencarnando (flag
 * {@code reincarnating}, que viaja con los stats y se guarda en disco), si no hay ninguna pantalla abierta se vuelve a abrir la
 * elección de raza. Así Cancelar, Escape o salir y volver a entrar no dejan al jugador sin raza y sin forma de elegirla.
 *
 * El flag solo lo apaga el servidor al confirmar el estilo ({@code ChooseStylePacket}); la pantalla de estilo lo apaga también
 * en local y pide {@link #suppress} unos ticks para que un sync viejo no reabra la pantalla justo al confirmar.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ReincarnationGate {
    private ReincarnationGate() {}

    private static int suppressTicks = 0;

    public static void suppress(int ticks) { suppressTicks = Math.max(suppressTicks, ticks); }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
                && mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).isReincarnating();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        if (suppressTicks > 0) { suppressTicks--; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        PlayerStatsAttachment st = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        if (st.isReincarnating()) mc.setScreen(new RaceSelectionScreen());
    }
}
