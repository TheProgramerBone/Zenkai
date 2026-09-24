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

    /** @param rgb2 segundo color (interior) o -1, ver KiVfxColors. */
    public record Charge(int rgb, int size, KiTechniqueType type,
                         TechniquePosition position, long startTick, int rgb2) {}

    private static final Map<Integer, Charge> ACTIVE = new ConcurrentHashMap<>();

    /** Ticks que la esfera sigue visible tras soltarse, encogiendo en su ÚLTIMA posición.
     *  El proyectil nace en el servidor, que no tiene huesos: desde 2026-09-24 sale del centro
     *  de esta misma esfera (pista de KiFirePacket, ver releaseOrigin), salvo en los sets cuyo
     *  release mueve las manos (Kamehameha, Galick Gun), que siguen usando el offset estático
     *  de TechniquePosition. Tres ticks de desvanecido tapan el corte que queda en esos casos
     *  sin que se lea como una segunda esfera. */
    public static final int FADE_TICKS = 3;

    /** Esfera apagándose. Congela el sitio y el tamaño que tenía al soltarse.
     *  Lleva el tipo de técnica por la misma razón que {@link Charge}: el renderer dibuja el
     *  mismo cuerpo con {@code KiVfxProfile}, y sin el tipo el apagado caería siempre en la técnica
     *  por defecto en vez de conservar sus bandas y alfas propias. */
    public record Fade(int rgb, Vec3 origin, float radius, KiTechniqueType type, long startTick, int rgb2) {}

    private static final Map<Integer, Fade> FADING = new ConcurrentHashMap<>();

    /** Última posición y radio pintados, por jugador. Lo escribe el renderer cada frame: es el
     *  único que sabe si acabó anclando al hueso o al respaldo. */
    private record Last(Vec3 origin, float radius) {}
    private static final Map<Integer, Last> LAST = new ConcurrentHashMap<>();

    public static void rememberDrawn(int entityId, Vec3 origin, float radius) {
        LAST.put(entityId, new Last(origin, radius));
    }

    /** Centro de la bola de carga dibujada este disparo, o null si no hay. Lo manda KiFirePacket
     *  como pista para que el proyectil nazca donde el jugador VE la energía (ver
     *  KiFirePacket.spawnCenter). Solo vale con una carga ACTIVA: LAST no se borra al soltar y,
     *  sin esa condición, un disparo sin bola pintada reutilizaría la posición de otro. */
    public static Vec3 releaseOrigin(Player p) {
        if (!ACTIVE.containsKey(p.getId())) return null;
        Last l = LAST.get(p.getId());
        return l == null ? null : l.origin();
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
                // de seguridad que usa KiVfxProfile para un ordinal desconocido.
                KiTechniqueType type = ending != null ? ending.type() : KiTechniqueType.values()[0];
                // El segundo color sale de la carga que termina: el paquete de fin puede no
                // traerlo (KiChargeServer.broadcastStop manda -1).
                int rgb2 = ending != null ? ending.rgb2() : pkt.rgb2();
                FADING.put(pkt.playerId(), new Fade(pkt.rgb(), last.origin(), last.radius(), type, t, rgb2));
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

        ACTIVE.put(pkt.playerId(), new Charge(pkt.rgb(), pkt.size(), type, pos, now, pkt.rgb2()));
    }

    public static Charge of(Player p) { return ACTIVE.get(p.getId()); }

    /** 0..1. Se queda en 1 cuando ya está a tope: la bola deja de crecer, no desaparece. */
    public static float progress(Charge c, long now) {
        int max = Math.max(1, KiCombatServer.chargeTicksFor(c.type(), c.size()));
        return Math.min(1.0f, (now - c.startTick()) / (float) max);
    }

    /** Olvida por completo a UN jugador (carga, desvanecido, última posición) — ver
     *  ClientVfxStateReset: al reaparecer el jugador conserva su id de entidad, así que sin esto
     *  una esfera a medio desvanecer seguiría anclada al sitio donde murió. */
    public static void forget(int entityId) {
        ACTIVE.remove(entityId);
        FADING.remove(entityId);
        LAST.remove(entityId);
    }

    /** Al cambiar de mundo/dimensión los ids de entidad dejan de valer. */
    public static void clear() { ACTIVE.clear(); FADING.clear(); LAST.clear(); }
}