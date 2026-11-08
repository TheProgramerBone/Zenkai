package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.api.event.FormChangedEvent;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * «Primera transformación de cada forma», sea cual sea la vía (Rage, entrenamiento, ritual, compra): marca la forma como vista
 * ({@code long_cinematic_seen}) y la anota en la {@link RageChronicle}. La primera vez es la que usará la secuencia larga; las
 * siguientes, la corta. Va en {@link FormChangedEvent} con prioridad mínima y sin recibir cancelados: si un addon veta el
 * cambio, no cuenta como primera vez.
 * Si la vía es Rage, {@link RageSystem} ya escribe su propia entrada AWAKENING (con la marca de cinemática), así que aquí solo se
 * marca la forma para no duplicar la historia.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class FirstTransformations {
    private FirstTransformations() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFormChanged(FormChangedEvent e) {
        if (!(e.player() instanceof ServerPlayer sp) || FormIds.BASE.equals(e.to())) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        boolean first = att.flags().markLongCinematicSeen(e.to().toString());
        if (!first) return;
        // Escena: invulnerable y sin acciones mientras dura la secuencia larga (ver AwakeningProtection).
        AwakeningProtection.start(sp);
        // La secuencia larga la ve quien se transforma y quien lo mira. Va ANTES de que el cambio llegue por el sync de forma.
        // Es solo presentación: un fallo al enviarla (jugador sin canal negociado, sin conexión) no debe abortar el cambio de forma
        // ni dejar sin anotar la entrada de la crónica, que van después.
        try {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                    new LongTransformPacket(sp.getUUID(), e.to()));
        } catch (RuntimeException ignored) {
            // sin aviso, la transformación se ve con la secuencia corta
        }
        if (Boolean.TRUE.equals(RageSystem.AWAKENING_IN_PROGRESS.get())) return;   // la entrada de la crónica la escribe RageSystem
        RageChronicle.get(sp).add(new RageChronicle.Entry("", RageChronicle.FORM_FIRST, e.to().toString(),
                RageResult.RECORDED.name(), "other", "", sp.level().getGameTime(), -1f, -1f, true, false, false));
    }
}
