package com.hmc.zenkai.feature.transport;

import com.hmc.zenkai.content.blockentity.TransportBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Red de cables (o de tuberías) conectados: UN búfer compartido cuya capacidad es la SUMA de la de
 * cada cable, como las redes de Mekanism (EnergyNetwork/FluidNetwork). Lo que entra por un extremo
 * está disponible en el otro el mismo tick; el límite por cara sigue siendo el caudal del cable.
 *
 * VIDA DE UNA RED (no se guarda: es transitoria y se reconstruye sola):
 *  - Se CONSTRUYE bajo demanda ({@link #build}) por relleno desde un cable sin red: recorre los cables
 *    vecinos del mismo tipo unidos por caras que no estén Desconectadas, solo por chunks cargados (sin
 *    esa comprobación, pedir el block entity de un chunk sin cargar lo cargaría). Si un cable alcanzado
 *    ya tenía otra red, esa se disuelve antes: así se FUSIONAN dos redes al unirse.
 *  - Cada cable guarda en NBT su CUOTA ({@link #syncShares}): el búfer repartido en proporción a su
 *    capacidad. Al construir se suman las cuotas; al disolver se reparte de nuevo. Así una red partida
 *    conserva lo que tenía cada trozo y el cable roto se lleva (y pierde) solo lo suyo, como en Mekanism.
 *  - Se DISUELVE ({@link #invalidate}) al quitar o descargar un cable, al cambiar el modo de una cara o
 *    cuando el repaso periódico ve un vecino unido que está en otra red.
 *  - Tickea UNA vez por tick: la mueve su primer miembro (el «líder»). Ojo: no se deduplica por
 *    getGameTime(), porque los gametests tickean a mano muchas veces dentro del mismo tick del mundo.
 *
 * Los tanques NO son miembros de una red de cables: son extremos y la red trata con ellos aplicando los
 * modos de las dos caras. Pero la MISMA clase sirve para el GRUPO de tanques (multibloque al estilo
 * Create, lo forma {@link TankFormation}): un búfer con la suma de sus bloques, sin red de cables.
 */
public abstract class TransportNetwork {

    /** Recoge cuotas durante el relleno y crea la red. Uno nuevo por construcción. */
    public interface Accumulator {
        /**
         * Mirada previa, SIN tocar nada: ¿podría unirse este cable con lo que lleva ahora (su red si la
         * tiene)? Se pregunta ANTES de disolver la red del vecino: si no, dos redes de fluidos distintos
         * se robarían por turnos la tubería vacía que las separa, disolviéndose la una a la otra.
         */
        default boolean accepts(TransportBlockEntity be) { return true; }

        /** ¿Puede unirse este cable? (las tuberías no mezclan fluidos). Suma su cuota si sí. */
        boolean admit(TransportBlockEntity be);

        TransportNetwork create(List<TransportBlockEntity> members, long capacity);
    }

    /** Ticks entre avisos de «chunk sin guardar» a los miembros mientras el búfer cambia. */
    private static final int SAVE_EVERY = 20;

    protected final List<TransportBlockEntity> members;
    protected final long capacity;
    /** ¿Grupo de TANQUES (multibloque, ver {@link TankFormation}) en vez de red de cables? */
    protected final boolean tankGroup;
    private final Set<BlockPos> positions = new HashSet<>();
    private boolean valid = true;
    private boolean sharesDirty;
    private boolean changedSinceSave;
    private int saveCooldown;

    protected TransportNetwork(List<TransportBlockEntity> members, long capacity) {
        this.members = members;
        this.capacity = capacity;
        this.tankGroup = !members.isEmpty() && !members.get(0).isCableBlock();
        for (TransportBlockEntity m : members) positions.add(m.getBlockPos());
    }

    public boolean isTankGroup() { return tankGroup; }

    /** ¿Hay un miembro en esa posición? */
    public boolean contains(BlockPos pos) { return positions.contains(pos); }

    public boolean isValid() { return valid; }
    public long capacity() { return capacity; }
    public int size() { return members.size(); }
    public boolean isLeader(TransportBlockEntity be) { return !members.isEmpty() && members.get(0) == be; }

    /** Llamar cada vez que el búfer cambia. */
    protected void markDirty() {
        sharesDirty = true;
        changedSinceSave = true;
    }

    /** Vuelca el búfer en las cuotas de los miembros si cambió desde la última vez. */
    public void syncShares() {
        if (!sharesDirty) return;
        sharesDirty = false;
        writeShares();
    }

    /** Fracción llena del búfer (0..1), para el nivel que enseña el cristal de un grupo de tanques. */
    protected abstract double fillFraction();

    /** Id del fluido que se ve en el cristal ("" en energía o vacío). */
    protected String displayFluidId() { return ""; }

    /** Ticks entre repasos del nivel visible (solo se manda al cliente si cambia el escalón). */
    private static final int FILL_EVERY = 10;
    private int fillCooldown;

    /**
     * Nivel del cristal de cada bloque de un grupo de tanques: el contenido sube DE ABAJO ARRIBA por
     * el grupo entero (como en Create), así que cada bloque recibe solo cuánto de SU altura está lleno,
     * en escalones de 1/32 (un píxel de la textura a 2x). Las cuotas reales siguen siendo proporcionales:
     * esto es solo lo que se ve.
     */
    public void updateFillDisplay() {
        if (!tankGroup || members.isEmpty()) return;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (TransportBlockEntity m : members) {
            minY = Math.min(minY, m.getBlockPos().getY());
            maxY = Math.max(maxY, m.getBlockPos().getY());
        }
        double filled = fillFraction() * (maxY - minY + 1);
        String fluid = displayFluidId();
        for (TransportBlockEntity m : members) {
            double local = Math.max(0, Math.min(1, filled - (m.getBlockPos().getY() - minY)));
            int steps = (int) Math.round(local * TransportBlockEntity.FILL_STEPS);
            if (steps == 0 && local > 0) steps = 1;   // un poco de contenido se ve aunque no llegue al escalón
            m.setFillDisplay(steps, fluid);
        }
    }

    /** Reparte el búfer en las cuotas de cada miembro, en proporción a su capacidad. */
    protected abstract void writeShares();

    /** Mueve el contenido con lo que rodea a la red: máquinas, tanques. */
    protected abstract void tickTransfer(Level level);

    public final void tick(Level level) {
        if (!valid) return;
        tickTransfer(level);
        if (tankGroup && valid && ++fillCooldown >= FILL_EVERY) {
            fillCooldown = 0;
            updateFillDisplay();
        }
        // Sin avisar al chunk, una red que solo se mueve no se guardaría al descargar. Cada 20 ticks
        // como mucho: avisar a mil cables en cada tick costaría más que el propio transporte.
        if (changedSinceSave && ++saveCooldown >= SAVE_EVERY) {
            saveCooldown = 0;
            changedSinceSave = false;
            for (TransportBlockEntity m : members) m.setChanged();
        }
    }

    /** Disuelve la red: escribe las cuotas y suelta a cada miembro (la reconstruyen bajo demanda). */
    public void invalidate() {
        if (!valid) return;
        valid = false;
        writeShares();
        sharesDirty = false;
        for (TransportBlockEntity m : members) m.detachNetwork(this);
    }

    /** Construye la red del cable {@code start} y se la asigna a cada miembro. Solo servidor. */
    public static void build(TransportBlockEntity start) {
        Level level = start.getLevel();
        if (level == null) return;
        Accumulator acc = start.newAccumulator();
        List<TransportBlockEntity> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<TransportBlockEntity> queue = new ArrayDeque<>();

        start.dissolveNetwork();
        acc.admit(start);
        seen.add(start.getBlockPos());
        found.add(start);
        queue.add(start);
        long capacity = 0;

        while (!queue.isEmpty()) {
            TransportBlockEntity cur = queue.poll();
            capacity += cur.capacityLong();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.getBlockPos().relative(d);
                if (seen.contains(np) || !level.isLoaded(np)) continue;
                if (!(level.getBlockEntity(np) instanceof TransportBlockEntity o) || o.isRemoved()) continue;
                if (!cur.joinsByModes(o, d)) continue;
                seen.add(np);
                if (!acc.accepts(o)) continue;
                o.dissolveNetwork();            // sus cuotas quedan al día antes de sumarlas
                if (!acc.admit(o)) continue;    // otro fluido: formará su propia red
                found.add(o);
                queue.add(o);
            }
        }

        TransportNetwork net = acc.create(found, capacity);
        for (TransportBlockEntity m : found) m.attachNetwork(net);
    }

    /** Reparto proporcional entero de {@code total} entre capacidades, sin perder el resto. */
    protected static long[] split(long total, List<TransportBlockEntity> members, long capacity) {
        long[] out = new long[members.size()];
        if (total <= 0 || capacity <= 0) return out;
        long given = 0;
        for (int i = 0; i < out.length; i++) {
            long cap = members.get(i).capacityLong();
            out[i] = (long) Math.floor((double) total * cap / capacity);
            out[i] = Math.min(out[i], cap);
            given += out[i];
        }
        long rest = total - given;
        for (int i = 0; i < out.length && rest > 0; i++) {
            long room = members.get(i).capacityLong() - out[i];
            long add = Math.min(room, rest);
            out[i] += add;
            rest -= add;
        }
        return out;
    }

    /**
     * Cuánto mover para igualar el PORCENTAJE de llenado entre la red (a de ca) y un tanque (b de cb).
     * Positivo: de la red al tanque. En double porque a·cb desborda un long con redes grandes.
     */
    protected static long equalizeAmount(long a, long ca, long b, long cb) {
        if (ca <= 0 || cb <= 0) return 0;
        double diff = (double) a * cb - (double) b * ca;
        return (long) (diff / (ca + cb));
    }
}
