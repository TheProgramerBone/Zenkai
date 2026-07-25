package com.hmc.zenkai.feature.mastery;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.SyncPlayerFormPacket;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
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

    /** Ritmo base de maestría de kaioken. Más alto que el de formas porque los escalones
     *  altos se usan en ráfagas de segundos, no en sesiones largas. */
    private static final double KAIOKEN_MASTERY_PER_MINUTE = 3.0;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        PlayerFormAttachment form = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        KaiokenTier tier = form.getKaioken();
        boolean transformed = !FormIds.BASE.equals(form.getFormId());
        if (!transformed && !tier.isOn()) return;   // ni forma ni kaioken: nada que acumular

        long t = sp.level().getGameTime();
        if (t % ACCRUE_EVERY == 0) {
            if (transformed) {
                form.addFormMastery(form.getFormId(),
                        (float) (CommonConfig.formMasteryPerMinute() / 60.0));
            }
            // Capas independientes: transformado CON kaioken entrena las dos a la vez.
            if (tier.isOn()) {
                form.addKaiokenMastery(tier,
                        (float) (KAIOKEN_MASTERY_PER_MINUTE / 60.0 * kaiokenGainMul(tier)));
            }
        }
        if (t % SYNC_EVERY == 0) {
            PacketDistributor.sendToPlayer(sp, SyncPlayerFormPacket.from(sp));
        }
    }

    /** Los escalones altos duran segundos, así que rinden más por segundo activo; si no,
     *  x20 sería inalcanzable. Raíz y no lineal para que no se dispare (x4 = referencia 1.0). */
    private static double kaiokenGainMul(KaiokenTier tier) {
        return Math.sqrt(tier.drainPctPerSecond() / 2.5);
    }
}