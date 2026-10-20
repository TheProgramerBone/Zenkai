package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.feature.sense.SenseKiDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Una presencia percibida por el Sentir Ki, tal como la recuerda el cliente entre escaneos.
 *
 * El servidor manda una foto cada 10 ticks; esta clase la convierte en algo que se puede dibujar
 * cada frame sin mentir:
 *  - POSICIÓN: si el cliente tiene la entidad cargada usa la suya (interpolada, exacta: vanilla ya
 *    se la manda). Si no (fuera del rango de seguimiento, el nivel 5 llega a 140 bloques), interpola
 *    entre las dos últimas fotos del servidor durante un intervalo de escaneo y se queda quieta.
 *  - CADUCIDAD: lo que deja de venir no desaparece de golpe; se desvanece en {@link #FADE_OUT_NANOS}
 *    con la posición congelada y después se borra.
 *  - FASE: cada presencia late con su propia fase, derivada del id, sobre el tiempo de juego. Nada
 *    aleatorio por frame.
 */
public final class SenseKiPresence {

    /** Intervalo de escaneo en nanosegundos (10 ticks a 20 TPS). */
    static final long SCAN_NANOS = 500_000_000L;
    static final long FADE_IN_NANOS = 300_000_000L;
    static final long FADE_OUT_NANOS = 450_000_000L;
    /** Una subida de fuerza o el inicio de una fijación cuentan como "reciente" este tiempo. */
    static final long RECENT_NANOS = 3_000_000_000L;

    public enum Tier {
        /** Núcleo, dos ondas y cualquier dato que autorice el nivel. */
        FULL,
        /** Núcleo, una onda y la vida; sin arcos de ki/stamina ni iconos de estado. */
        MEDIUM,
        /** Eco estático con un pulso breve; sin datos. */
        SIMPLE,
        /** Agregada a una zona común (saturación); no se dibuja por separado. */
        CLUSTERED
    }

    final int id;
    SenseKiDataPacket.Entry entry;
    private double px, py, pz;
    private double cx, cy, cz;
    private long updatedNanos;
    final long firstSeenNanos;
    /** Instante en que dejó de venir en los escaneos, o -1 si sigue viniendo. */
    long lostNanos = -1;
    /** Instante en que empezó a fijarte (bit FLAG_TARGETING_YOU), o -1. */
    long threatSinceNanos = -1;
    /** Instante de la última subida de categoría de fuerza, o -1. */
    long strengthRoseNanos = -1;
    /** 0..1, estable por id: desfasa los latidos para que no pulse todo a la vez. */
    final float phase;

    Tier tier = Tier.SIMPLE;

    SenseKiPresence(SenseKiDataPacket.Entry e, long now) {
        this.id = e.entityId();
        this.entry = e;
        this.px = this.cx = e.x();
        this.py = this.cy = e.y();
        this.pz = this.cz = e.z();
        this.updatedNanos = now;
        this.firstSeenNanos = now;
        this.phase = (Mth.murmurHash3Mixer(id) & 0xFFFF) / 65536f;
        if (e.has(SenseKiDataPacket.FLAG_TARGETING_YOU)) threatSinceNanos = now;
    }

    void update(SenseKiDataPacket.Entry e, long now) {
        // La posición de partida de la nueva interpolación es donde se dibujaba ahora mismo.
        double t = lerpT(now);
        px = Mth.lerp(t, px, cx);
        py = Mth.lerp(t, py, cy);
        pz = Mth.lerp(t, pz, cz);
        cx = e.x(); cy = e.y(); cz = e.z();
        updatedNanos = now;

        if (e.strength() > entry.strength()) strengthRoseNanos = now;
        boolean threat = e.has(SenseKiDataPacket.FLAG_TARGETING_YOU);
        if (threat && threatSinceNanos < 0) threatSinceNanos = now;
        else if (!threat) threatSinceNanos = -1;

        entry = e;
        lostNanos = -1;
    }

    private double lerpT(long now) {
        return Mth.clamp((now - updatedNanos) / (double) SCAN_NANOS, 0.0, 1.0);
    }

    boolean expired(long now) {
        return lostNanos >= 0 && now - lostNanos >= FADE_OUT_NANOS;
    }

    /** Opacidad por entrada/salida (0..1). */
    float lifeAlpha(long now) {
        float in = Mth.clamp((now - firstSeenNanos) / (float) FADE_IN_NANOS, 0f, 1f);
        float out = lostNanos < 0 ? 1f : 1f - Mth.clamp((now - lostNanos) / (float) FADE_OUT_NANOS, 0f, 1f);
        return in * out;
    }

    boolean recentlyRose(long now) {
        return strengthRoseNanos >= 0 && now - strengthRoseNanos < RECENT_NANOS;
    }

    /** Segundos desde que empezó a fijarte, o -1 si no te fija. */
    float threatSeconds(long now) {
        return threatSinceNanos < 0 ? -1f : (now - threatSinceNanos) / 1.0e9f;
    }

    /**
     * Punto de anclaje del eco (centro del cuerpo) para este frame.
     * Una presencia perdida queda congelada en su última posición conocida.
     */
    Vec3 anchor(Minecraft mc, float partial, long now) {
        if (lostNanos < 0 && mc.level != null && mc.level.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
            return le.getPosition(partial).add(0, le.getBbHeight() * ANCHOR_HEIGHT, 0);
        }
        double t = lerpT(now);
        return new Vec3(Mth.lerp(t, px, cx), Mth.lerp(t, py, cy) + entry.height() * ANCHOR_HEIGHT, Mth.lerp(t, pz, cz));
    }

    /** Posición de los pies sin interpolar, para ordenar y agrupar (no hace falta precisión de frame). */
    double feetX() { return cx; }
    double feetY() { return cy; }
    double feetZ() { return cz; }

    /** Fracción de la altura del hitbox a la que se ancla el eco: el pecho, no la cabeza. */
    private static final double ANCHOR_HEIGHT = 0.55;
}
