package com.hmc.zenkai.client;

import com.hmc.zenkai.feature.technique.KiChargeStatePacket;
import com.hmc.zenkai.feature.technique.TechniquePosition;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

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

    public static void accept(KiChargeStatePacket pkt) {
        if (!pkt.charging()) {
            ACTIVE.remove(pkt.playerId());
            return;
        }
        KiTechniqueType[] types = KiTechniqueType.values();
        int t = pkt.typeOrdinal();
        KiTechniqueType type = (t >= 0 && t < types.length) ? types[t] : types[0];

        long now = Minecraft.getInstance().level == null
                ? 0L : Minecraft.getInstance().level.getGameTime();

        ACTIVE.put(pkt.playerId(), new Charge(pkt.rgb(), pkt.size(), type,
                TechniquePosition.byOrdinal(pkt.positionOrdinal()), now));
    }

    public static Charge of(Player p) { return ACTIVE.get(p.getId()); }

    /** 0..1. Se queda en 1 cuando ya está a tope: la bola deja de crecer, no desaparece. */
    public static float progress(Charge c, long now) {
        int max = Math.max(1, c.type().chargeTicks());
        return Math.min(1.0f, (now - c.startTick()) / (float) max);
    }

    /** Al cambiar de mundo/dimensión los ids de entidad dejan de valer. */
    public static void clear() { ACTIVE.clear(); }
}