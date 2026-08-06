package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.sense.SenseKiDataPacket;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Avisos NO VISUALES del sentir el ki (nivel 5). Dos disparadores:
 *  1. AMENAZA — entra en rango algo con PL aparente >= 3x el tuyo.
 *  2. SUBIDA  — alguien duplica su PL aparente entre dos escaneos consecutivos. Suprimir el ki
 *     ya existe (powerPercent de Ki Control) y el PL que llega es el APARENTE, así que dejar de
 *     esconderse ES un salto de PL. No hay que detectar nada más.
 * IGNORAN EL FILTRO DE MODO a propósito. Las llamas son una preferencia de visualización; un
 * aviso es un peligro. Estar en modo MOBS no puede silenciar a un jugador que te triplica —
 * eso convertiría una opción de comodidad en una trampa mortal.
 * ANTI-SPAM en tres capas, porque un aviso que se repite deja de ser un aviso:
 *  - Por entidad: se dispara UNA vez y no se rearma hasta 60 s sin verla.
 *  - Global: 15 s mínimo entre dos avisos cualesquiera, vengan de quien vengan.
 *  - La subida lleva su propio enfriamiento corto por entidad (5 s), porque puede repetirse
 *    legítimamente mientras alguien va cargando.
 * Los registros NO se borran al apagar el sentido: si se borraran, apagar y encender F4
 * rearmaría entero y bastaría machacar la tecla para tener un martilleo constante.
 * NO se suscribe a eventos. La detección la llama SenseKiClientState.onData y la sacudida la
 * pide CameraShakeMixin desde el final de Camera.setup. Un @EventBusSubscriber sin ningún
 * @SubscribeEvent revienta el arranque en NeoForge.
 */
public final class SenseKiWarnings {
    private SenseKiWarnings() {}

    // ── Diales de detección ──────────────────────────────────────────────────
    private static final double THREAT_RATIO = 3.0;   // x tu PL para que entrar en rango avise
    private static final double SPIKE_RATIO  = 2.0;   // x su PL anterior para contar como salto
    /** Y además debe quedar cerca de ti: un pollo que sube de 8 a 16 no es una subida de poder. */
    private static final double SPIKE_RELEVANCE = 0.75;

    // ── Diales de anti-spam (en ticks) ───────────────────────────────────────
    private static final int REARM_TICKS  = 1200; // 60 s sin verla para que vuelva a avisar
    private static final int GLOBAL_COOL  = 300;  // 15 s entre dos avisos cualesquiera
    private static final int SPIKE_COOL   = 100;  // 5 s por entidad para el disparador de subida
    private static final int PURGE_TICKS  = 2400; // 120 s sin verla: se olvida el registro

    /**
     * Centinela de "nunca ocurrió". NO es Long.MIN_VALUE a propósito: sobre él se hace
     * aritmética (now - lastWarn), y restar el mínimo de un long DESBORDA y da la vuelta a un
     * negativo enorme, que siempre parece "hace nada" y bloquea el aviso PARA SIEMPRE — fire()
     * salía por el enfriamiento antes de poder asignar lastAnyWarn, así que no se curaba solo.
     * MIN_VALUE/4 está igual de lejos en el pasado y deja margen de sobra para no desbordar.
     */
    private static final long NEVER = Long.MIN_VALUE / 4;

    // ── Diales de la sacudida ────────────────────────────────────────────────
    private static final int SHAKE_TICKS = 60;      // ~1 s
    private static final double SHAKE_AMP = 0.55;   // bloques, a intensidad máxima
    private static final float INTENSITY_FLOOR = 0.25f;

    /** Lo que se recuerda de cada entidad vista. */
    private static final class Track {
        long lastPl;
        long lastSeen;
        long lastSpike = NEVER;
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static long lastAnyWarn = NEVER;

    // Sacudida en curso.
    private static long shakeStart = NEVER;
    private static float shakeIntensity = 0f;

    /** Olvida completo. Cambio de mundo o desconexión: los ids de entidad ya no significan nada. */
    public static void forget() {
        TRACKS.clear();
        lastAnyWarn = NEVER;
        shakeStart = NEVER;
        shakeIntensity = 0f;
    }

    /**
     * Se llama con CADA respuesta del escaneo, antes o después de refrescar la caché: este
     * sistema lleva su propio histórico y no depende del de SenseKiClientState.
     */
    public static void onScan(Minecraft mc, List<SenseKiDataPacket.Entry> entries) {
        if (mc.player == null || mc.level == null) return;
        if (!SkillEffects.senseShowsWarnings(mc.player)) return;

        long now = mc.level.getGameTime();
        long myPl = PlayerStatsAttachment.get(mc.player).getPowerLevel();

        for (SenseKiDataPacket.Entry en : entries) {
            if (en.entityId() == mc.player.getId()) continue;

            long pl = en.powerLevel();
            Track t = TRACKS.get(en.entityId());
            boolean isNew = (t == null) || (now - t.lastSeen > REARM_TICKS);

            if (t == null) {
                t = new Track();
                TRACKS.put(en.entityId(), t);
            }

            if (isNew) {
                // AMENAZA: solo al entrar. Que siga ahí no es noticia nueva.
                if (myPl > 0 && pl >= myPl * THREAT_RATIO) {
                    fire(mc, now, ratioIntensity(pl, myPl));
                }
            } else if (t.lastPl > 0
                    && pl >= t.lastPl * SPIKE_RATIO
                    && myPl > 0 && pl >= myPl * SPIKE_RELEVANCE
                    && now - t.lastSpike >= SPIKE_COOL) {
                // SUBIDA: alguien acaba de soltar lo que estaba escondiendo.
                t.lastSpike = now;
                fire(mc, now, ratioIntensity(pl, myPl));
            }

            t.lastPl = pl;
            t.lastSeen = now;
        }

        purge(now);
    }

    /** Intensidad 0.25..1: x1 tu PL apenas se nota, x8 o más satura. Logarítmica porque el PL
     *  crece por órdenes de magnitud. */
    private static float ratioIntensity(long pl, long myPl) {
        if (myPl <= 0) return 1f;
        double octaves = Math.log((double) pl / myPl) / Math.log(2);
        return Mth.clamp((float) (octaves / 3.0), INTENSITY_FLOOR, 1f); // 3 octavas = x8
    }

    /**
     * Dispara el aviso si el enfriamiento global lo permite. Mismo sonido para los dos
     * disparadores: el jugador no necesita saber CUÁL saltó, necesita mirar alrededor.
     */
    private static void fire(Minecraft mc, long now, float intensity) {
        if (now - lastAnyWarn < GLOBAL_COOL) return;
        lastAnyWarn = now;

        assert mc.player != null && mc.level != null;
        // ⚠ VERIFICAR 1.21.1: si SoundEvents.WARDEN_HEARTBEAT es Holder<SoundEvent>, añade .value()
        mc.level.playLocalSound(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                0.6f + 0.4f * intensity,
                1.05f - 0.25f * intensity,   // más grave cuanto peor la cosa
                false);

        if (ClientConfig.kiSenseCameraShake()) {
            shakeStart = now;
            shakeIntensity = intensity;
        }

        // TEMPORAL: quita este bloque cuando esté calibrado.
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "[sense] aviso · intensidad " + String.format("%.2f", intensity)
                        + " · sacudida " + ClientConfig.kiSenseCameraShake()), false);
    }

    private static void purge(long now) {
        Iterator<Map.Entry<Integer, Track>> it = TRACKS.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastSeen > PURGE_TICKS) it.remove();
        }
    }

    // ── Sacudida ─────────────────────────────────────────────────────────────

    /**
     * Desplazamiento de la sacudida para este fotograma, o null si no hay ninguna en curso.
     * Devuelve {frente, arriba, lateral} en las unidades que espera Camera.move.
     * Sin rotación a propósito: girarte la cámara te quita la puntería y marea, y esto debe
     * alarmar, no castigar.
     * Las tres frecuencias son deliberadamente inconmensurables entre sí: con frecuencias en
     * proporción simple el patrón se repite y se lee como una vibración mecánica en vez de como
     * un escalofrío.
     */
    public static float[] shakeOffset(float partialTick) {
        if (shakeStart == NEVER) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { shakeStart = NEVER; return null; }

        // La resta va en LONG y el partialTick se suma DESPUÉS. Convertir gameTime a float
        // antes de restar pierde el decimal en cuanto el mundo lleva unas horas.
        float elapsed = (mc.level.getGameTime() - shakeStart) + partialTick;
        if (elapsed < 0f || elapsed >= SHAKE_TICKS) {
            shakeStart = NEVER;
            return null;
        }

        // Decaimiento LINEAL: el cuadrático gastaba casi entero en los primeros 0,3 s.
        float decay = 1f - (elapsed / SHAKE_TICKS);
        float amp = (float) (SHAKE_AMP * shakeIntensity * decay);

        return new float[]{
                amp * Mth.sin(elapsed * 1.63f + 3.1f) * 0.4f,  // frente
                amp * Mth.sin(elapsed * 3.07f + 1.7f) * 0.8f,  // arriba
                amp * Mth.sin(elapsed * 2.31f)                 // lateral
        };
    }
}