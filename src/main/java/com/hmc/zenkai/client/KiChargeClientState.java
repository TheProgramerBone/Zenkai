package com.hmc.zenkai.client;

import com.hmc.zenkai.feature.technique.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quién está cargando qué, en el cliente. Lo llena KiChargeStatePacket y lo lee el renderer
 * de la bola de carga.
 * El progreso NO viaja: se apunta el tick en que llegó el aviso y se deriva restando contra
 * chargeTicks del tipo. Un desfase de red de un par de ticks no se nota en una bola que
 * crece durante uno o dos segundos.
 */
public final class KiChargeClientState {
    private KiChargeClientState() {}

    public record Charge(int rgb, int size, KiTechniqueType type,
                         TechniquePosition position, long startTick) {}

    private static final Map<Integer, Charge> ACTIVE = new ConcurrentHashMap<>();

    /** Ticks que la esfera sigue visible tras soltarse, encogiendo en su ÚLTIMA posición.
     *  El proyectil nace en el servidor, que no tiene huesos, así que sale del offset estático
     *  de TechniquePosition: en el Kamehameha eso es medio bloque de salto en un frame. Tres
     *  ticks de desvanecido tapan el corte sin que se lea como una segunda esfera. */
    public static final int FADE_TICKS = 3;

    /** Esfera apagándose. Congela el sitio y el tamaño que tenía al soltarse.
     *  Lleva el tipo de técnica por la misma razón que {@link Charge}: el renderer dibuja el
     *  mismo cuerpo con {@code KiVisual}, y sin el tipo el apagado caería siempre en la técnica
     *  por defecto en vez de conservar sus bandas y alfas propias. */
    public record Fade(int rgb, Vec3 origin, float radius, KiTechniqueType type, long startTick) {}

    private static final Map<Integer, Fade> FADING = new ConcurrentHashMap<>();

    /** Última posición y radio pintados, por jugador. Lo escribe el renderer cada frame: es el
     *  único que sabe si acabó anclando al hueso o al respaldo. */
    private record Last(Vec3 origin, float radius) {}
    private static final Map<Integer, Last> LAST = new ConcurrentHashMap<>();

    public static void rememberDrawn(int entityId, Vec3 origin, float radius) {
        LAST.put(entityId, new Last(origin, radius));
    }

    public static Fade fadeOf(Player p) { return FADING.get(p.getId()); }

    /** 1 → 0 a lo largo de FADE_TICKS. Fuera de rango, la entrada se retira. */
    public static float fadeAlpha(Fade f, long now, float partialTick) {
        float t = (now - f.startTick() + partialTick) / FADE_TICKS;
        return Math.max(0f, 1f - t);
    }

    public static void dropFade(int entityId) { FADING.remove(entityId); }

    public static void accept(KiChargeStatePacket pkt) {
        if (!pkt.charging()) {
            Charge ending = ACTIVE.remove(pkt.playerId());
            Last last = LAST.remove(pkt.playerId());
            if (last != null) {
                long t = Minecraft.getInstance().level == null
                        ? 0L : Minecraft.getInstance().level.getGameTime();
                // ending puede ser null si el paquete de fin llega sin que hubiera carga activa
                // registrada (reconexión a mitad de carga); el tipo por defecto es la misma red
                // de seguridad que usa KiVisual para un ordinal desconocido.
                KiTechniqueType type = ending != null ? ending.type() : KiTechniqueType.values()[0];
                FADING.put(pkt.playerId(), new Fade(pkt.rgb(), last.origin(), last.radius(), type, t));
            }
            return;
        }
        KiTechniqueType[] types = KiTechniqueType.values();
        int t = pkt.typeOrdinal();
        KiTechniqueType type = (t >= 0 && t < types.length) ? types[t] : types[0];

        long now = Minecraft.getInstance().level == null
                ? 0L : Minecraft.getInstance().level.getGameTime();

        // El origen se DERIVA del set, igual que en el servidor (KiTechnique.position()): las
        // dos puntas resuelven con TechniqueAnimSet, así que la bola y el proyectil no pueden
        // discrepar. BARRIER no tiene set y va por su constante.
        // Mismo criterio que KiTechnique.position(): lo que decide es si el tipo IMPONE
        // animación, no si es defensivo — la explosión no lo es y también tiene la suya.
        TechniquePosition pos = type.animOverride() != null
                ? TechniqueAnimSet.BARRIER_POSITION
                : TechniqueAnimSet.positionOf(Math.max(1, pkt.animSet()));

        ACTIVE.put(pkt.playerId(), new Charge(pkt.rgb(), pkt.size(), type, pos, now));
    }

    public static Charge of(Player p) { return ACTIVE.get(p.getId()); }

    /** 0..1. Se queda en 1 cuando ya está a tope: la bola deja de crecer, no desaparece. */
    public static float progress(Charge c, long now) {
        int max = Math.max(1, KiCombatServer.chargeTicksFor(c.type(), c.size()));
        return Math.min(1.0f, (now - c.startTick()) / (float) max);
    }

    /** Al cambiar de mundo/dimensión los ids de entidad dejan de valer. */
    public static void clear() { ACTIVE.clear(); FADING.clear(); LAST.clear(); }
}