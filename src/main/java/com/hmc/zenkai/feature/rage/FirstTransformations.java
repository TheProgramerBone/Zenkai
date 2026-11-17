package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.api.event.FormChangedEvent;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    /** Duración de la escena hasta el estallido (= AwakeningScene.BURST_TICKS; aquí no se importa código de cliente). */
    public static final int LONG_SCENE_TICKS = 80;
    /** Duración total de la escena (= AwakeningScene.LENGTH_TICKS). */
    public static final int LONG_SCENE_TOTAL_TICKS = 126;
    private static final int ANNOUNCE_TTL_TICKS = 20 * 30;

    private record Announced(ResourceLocation form, long at) {}
    /** Secuencias largas ya avisadas AL EMPEZAR la carga (tecla de transformar): al completarse no se avisa otra vez. */
    private static final Map<UUID, Announced> ANNOUNCED = new ConcurrentHashMap<>();

    /**
     * La carga de transformación de {@code target} acaba de empezar (el jugador pulsó la tecla). Si va a ser larga (primera vez de la
     * forma o «siempre larga»), se avisa YA: la escena arranca al pulsar, en vez de la pose normal de carga, y no tras completarse.
     */
    public static void announceAtHoldStart(ServerPlayer sp, ResourceLocation target, int normalHoldTicks) {
        if (FormIds.BASE.equals(target)) return;
        boolean first = !PlayerStatsAttachment.get(sp).flags().hasSeenLongCinematic(target.toString());
        // «Siempre la larga» es solo presentación: anunciarla al pulsar fija la carga en LONG_SCENE_TICKS, y si la carga normal de esa
        // forma es MÁS larga (Potencial Desatado: 100 sin maestría) la opción la acortaría, una ventaja de combate. En ese caso no se
        // anuncia aquí y la escena sale al completarse la carga normal (onFormChanged), como antes de arrancar al pulsar.
        boolean wants = first || (AlwaysLongPrefPacket.wants(sp.getUUID()) && normalHoldTicks <= LONG_SCENE_TICKS);
        if (!wants) return;
        ANNOUNCED.put(sp.getUUID(), new Announced(target, sp.level().getGameTime()));
        try {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                    new LongTransformPacket(sp.getUUID(), target, "", first));
        } catch (RuntimeException ignored) {
            ANNOUNCED.remove(sp.getUUID());   // sin aviso se verá la corta
        }
    }

    /** ¿Esta carga ya anunció su secuencia larga? Entonces dura como mucho lo que la escena, para que el cambio caiga en el estallido. */
    public static boolean longAnnounced(ServerPlayer sp, ResourceLocation target) {
        Announced a = ANNOUNCED.get(sp.getUUID());
        return a != null && a.form().equals(target) && sp.level().getGameTime() - a.at() <= ANNOUNCE_TTL_TICKS;
    }

    public static void forget(UUID id) { ANNOUNCED.remove(id); INTERRUPTED.remove(id); }

    /** Jugadores que cortaron la presentación ANTES de que existiera su entrada de crónica (carga con la tecla): gameTime del aviso. */
    private static final Map<UUID, Long> INTERRUPTED = new ConcurrentHashMap<>();

    /**
     * El cliente avisó de que cortó la presentación y la entrada aún no existe. Solo se apunta si de verdad hay una secuencia
     * larga anunciada para él (ver {@link LongTransformInterruptedPacket}); la recoge {@link #onFormChanged} al escribir la entrada.
     */
    static void noteInterrupted(ServerPlayer sp) {
        Announced a = ANNOUNCED.get(sp.getUUID());
        long now = sp.level().getGameTime();
        if (a == null || now - a.at() > ANNOUNCE_TTL_TICKS) return;
        INTERRUPTED.put(sp.getUUID(), now);
    }

    /** ¿Cortó la presentación de la carga que acaba de completarse? Consume la marca. */
    private static boolean consumeInterrupted(ServerPlayer sp) {
        Long at = INTERRUPTED.remove(sp.getUUID());
        return at != null && sp.level().getGameTime() - at <= LongTransformInterruptedPacket.WINDOW_TICKS;
    }

    /**
     * La carga se cortó sin completarse (soltó la tecla, perdió la condición, fue vetada): si había una secuencia larga avisada, se
     * cancela en los clientes. Barato (un acceso al mapa), así que se llama cada tick en que no hay carga en marcha.
     */
    public static void cancelIfAnnounced(ServerPlayer sp) {
        INTERRUPTED.remove(sp.getUUID());   // sin transformación no hay entrada que marcar
        if (ANNOUNCED.remove(sp.getUUID()) == null) return;
        try {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                    new LongTransformCancelPacket(sp.getUUID()));
        } catch (RuntimeException ignored) {
            // solo presentación
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFormChanged(FormChangedEvent e) {
        if (!(e.player() instanceof ServerPlayer sp) || FormIds.BASE.equals(e.to())) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        boolean alreadyAnnounced = longAnnounced(sp, e.to());
        ANNOUNCED.remove(sp.getUUID());
        boolean interrupted = consumeInterrupted(sp);
        boolean first = att.flags().markLongCinematicSeen(e.to().toString());
        if (!first) {
            // «Siempre la larga» (opción del propio jugador): el aviso se manda igual, SIN la protección de la escena ni la crónica.
            if (!alreadyAnnounced && AlwaysLongPrefPacket.wants(sp.getUUID())) sendLong(sp, e, false);
            return;
        }
        // Escena: invulnerable y sin acciones mientras dura la secuencia larga (ver AwakeningProtection).
        // Despertar por una MUERTE: la escena arranca ahora (es automático) y la protección la cubre entera. Cualquier otra primera vez
        // (p. ej. entrenar con la tecla): los ticks de siempre al completarse la carga.
        RageTrigger why = RageSystem.AWAKENING_TRIGGER.get();
        boolean byDeath = why == RageTrigger.ALLY_DEATH || why == RageTrigger.ALLY_DIES_WHILE_DOWNED || why == RageTrigger.PET_DEATH;
        AwakeningProtection.start(sp, byDeath ? LONG_SCENE_TOTAL_TICKS : 0);
        // La secuencia larga la ve quien se transforma y quien lo mira. Va ANTES de que el cambio llegue por el sync de forma.
        // Es solo presentación: un fallo al enviarla (jugador sin canal negociado, sin conexión) no debe abortar el cambio de forma
        // ni dejar sin anotar la entrada de la crónica, que van después.
        if (!alreadyAnnounced) sendLong(sp, e, true);
        if (Boolean.TRUE.equals(RageSystem.AWAKENING_IN_PROGRESS.get())) return;   // la entrada de la crónica la escribe RageSystem
        RageChronicle.get(sp).add(new RageChronicle.Entry("", RageChronicle.FORM_FIRST, e.to().toString(),
                RageResult.RECORDED.name(), "other", "", sp.level().getGameTime(), -1f, -1f, true, interrupted, false));
    }

    /** Avisa de la secuencia larga a quien se transforma y a quienes lo ven. Solo presentación: un fallo se traga (sin aviso, se ve la corta). */
    private static void sendLong(net.minecraft.server.level.ServerPlayer sp, FormChangedEvent e, boolean first) {
        try {
            RageTrigger cause = RageSystem.AWAKENING_TRIGGER.get();   // null si la vía no es Rage: la escena usa su variante neutra
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                    new LongTransformPacket(sp.getUUID(), e.to(), cause == null ? "" : cause.name(), first));
        } catch (RuntimeException ignored) {
            // sin aviso, la transformación se ve con la secuencia corta
        }
    }
}
