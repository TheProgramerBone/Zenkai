package com.hmc.zenkai.feature.transport;

import com.hmc.zenkai.content.blockentity.TransportBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tanques multibloque al estilo Create: tanques del mismo tipo pegados forman PRISMAS de base cuadrada
 * (1x1, 2x2 o 3x3) y cualquier altura, con UN búfer cuya capacidad es la suma de sus bloques. El
 * búfer y las cuotas por bloque son los de {@link TransportNetwork} (la misma clase que la red de
 * cables): un bloque roto se lleva su cuota en el ítem y el resto la conserva.
 *
 * REPARTO, determinista: se toma el componente de tanques unidos (caras que no estén Desconectadas,
 * solo chunks cargados), se disuelven sus grupos (las cuotas quedan escritas) y se recorre de abajo
 * arriba (y, luego x, luego z). Cada bloque libre es la esquina mínima de un prisma: se prueba base
 * 3, 2 y 1, cada una crece hacia arriba mientras la capa siguiente esté entera, libre, unida y con un
 * fluido compatible, y se queda el de mayor volumen. Como el resultado depende solo del componente,
 * reconstruir sin cambios da los mismos grupos.
 *
 * Un tanque con una cara Desconectada hacia su vecino no se une a él: así se separan dos tanques
 * pegados (como los cables). Dos fluidos distintos tampoco se unen.
 */
public final class TankFormation {
    private TankFormation() {}

    public static final int MAX_WIDTH = 3;
    /** Tope del componente que se recorre de una vez: una pared de miles de tanques no congela el tick. */
    private static final int MAX_COMPONENT = 4096;

    /** Forma los grupos del componente del tanque {@code start}. Solo servidor. */
    public static void build(TransportBlockEntity start) {
        Level level = start.getLevel();
        if (level == null) return;

        // 1. Componente de tanques unidos por sus modos.
        Map<BlockPos, TransportBlockEntity> comp = new HashMap<>();
        ArrayDeque<TransportBlockEntity> queue = new ArrayDeque<>();
        comp.put(start.getBlockPos(), start);
        queue.add(start);
        while (!queue.isEmpty() && comp.size() < MAX_COMPONENT) {
            TransportBlockEntity cur = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.getBlockPos().relative(d);
                if (comp.containsKey(np) || !level.isLoaded(np)) continue;
                if (level.getBlockEntity(np) instanceof TransportBlockEntity o && joins(cur, o, d)) {
                    comp.put(np, o);
                    queue.add(o);
                }
            }
        }

        // 2. Disolver sus grupos: las cuotas quedan escritas en cada bloque antes de repartir.
        for (TransportBlockEntity be : comp.values()) be.dissolveNetwork();

        // 3. Reparto en prismas, de abajo arriba.
        List<BlockPos> order = new ArrayList<>(comp.keySet());
        order.sort(Comparator.comparingInt((BlockPos q) -> q.getY()).thenComparingInt(q -> q.getX()).thenComparingInt(q -> q.getZ()));
        Set<BlockPos> used = new HashSet<>();
        for (BlockPos p : order) {
            if (used.contains(p)) continue;
            int bestW = 1, bestH = 1;
            long bestVol = 0;
            for (int w = MAX_WIDTH; w >= 1; w--) {
                int h = height(comp, used, p, w, start);
                if (h > 0 && (long) w * w * h > bestVol) {
                    bestVol = (long) w * w * h;
                    bestW = w;
                    bestH = h;
                }
            }
            List<TransportBlockEntity> members = new ArrayList<>();
            for (int y = 0; y < bestH; y++) {
                for (int x = 0; x < bestW; x++) {
                    for (int z = 0; z < bestW; z++) {
                        BlockPos q = p.offset(x, y, z);
                        used.add(q);
                        members.add(comp.get(q));
                    }
                }
            }
            create(members);
        }
    }

    /** Crea el grupo con las cuotas de sus bloques y se lo asigna. */
    private static void create(List<TransportBlockEntity> members) {
        TransportNetwork.Accumulator acc = members.get(0).newAccumulator();
        long capacity = 0;
        for (TransportBlockEntity m : members) {
            acc.admit(m);
            capacity += m.capacityLong();
        }
        TransportNetwork net = acc.create(members, capacity);
        for (TransportBlockEntity m : members) m.attachNetwork(net);
    }

    /**
     * Altura del prisma de base {@code w} con esquina mínima {@code p}, o 0 si ni la primera capa
     * cabe. Cada capa debe estar entera en el componente, libre, unida por dentro y a la de abajo, y
     * el fluido de cada bloque compatible con el resto del prisma.
     */
    private static int height(Map<BlockPos, TransportBlockEntity> comp, Set<BlockPos> used, BlockPos p, int w,
                              TransportBlockEntity start) {
        TransportNetwork.Accumulator acc = start.newAccumulator();   // de prueba: se descarta
        int h = 0;
        while (true) {
            BlockPos base = p.above(h);
            if (!layerOk(comp, used, base, w, h > 0, acc)) return h;
            h++;
        }
    }

    private static boolean layerOk(Map<BlockPos, TransportBlockEntity> comp, Set<BlockPos> used, BlockPos base, int w,
                                   boolean checkBelow, TransportNetwork.Accumulator acc) {
        // Primero que la capa exista entera y libre, SIN tocar el acumulador.
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                BlockPos q = base.offset(x, 0, z);
                TransportBlockEntity be = comp.get(q);
                if (be == null || used.contains(q)) return false;
                if (x + 1 < w && !joins(be, comp.get(q.east()), Direction.EAST)) return false;
                if (z + 1 < w && !joins(be, comp.get(q.south()), Direction.SOUTH)) return false;
                if (checkBelow && !joins(be, comp.get(q.below()), Direction.DOWN)) return false;
                if (!acc.accepts(be)) return false;
            }
        }
        // Después se suman (el acumulador fija el fluido del prisma con el primero que lleve).
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                if (!acc.admit(comp.get(base.offset(x, 0, z)))) return false;
            }
        }
        return true;
    }

    /** ¿Dos tanques vecinos (b en la dirección d de a) se unen? Mismo tipo y ninguna cara Desconectada. */
    private static boolean joins(TransportBlockEntity a, TransportBlockEntity b, Direction d) {
        return b != null && !b.isRemoved() && !a.isCableBlock() && !b.isCableBlock() && a.kind() == b.kind()
                && a.mode(d) != SideMode.NONE && b.mode(d.getOpposite()) != SideMode.NONE;
    }
}
