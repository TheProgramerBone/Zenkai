package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.client.LockOnClientState;
import com.hmc.zenkai.feature.combat.SenseKiMode;
import com.hmc.zenkai.feature.sense.SenseKiDataPacket;
import com.hmc.zenkai.feature.sense.SenseKiScanPacket;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Estado CLIENTE del sentir el ki:
 *  - Modo actual (ciclo F4; ver SenseKiMode). Cada cambio se anuncia en la actionbar.
 *  - Presencias percibidas (SenseKiPresence), alimentadas por las respuestas del servidor, que es
 *    la ÚNICA fuente: el cliente no busca entidades por su cuenta para "descubrir" ocultas.
 *  - Selección visual: qué presencias se dibujan y con qué calidad (presupuesto por prioridad). Se
 *    recalcula con cada respuesta y cada {@link #SELECT_INTERVAL} ticks, nunca por frame.
 *  - Tick: cada SCAN_INTERVAL ticks con modo != OFF manda un SenseKiScanPacket.
 * Gate del F4: si el jugador lleva un scouter FUNCIONAL, F4 es del scouter; si no lleva, o el
 * que lleva está reventado, F4 cicla el sentir el ki.
 * NIVELES: SkillEffects.senseLevel(player) manda para poder encenderlo; lo que se VE lo decide el
 * nivel con el que el servidor filtró cada respuesta ({@link #level()}).
 */
public final class SenseKiClientState {
    private SenseKiClientState() {}

    private static final int SCAN_INTERVAL = 10; //Ticks
    private static final int SELECT_INTERVAL = 5; //Ticks

    // ── Presupuesto visual (dirección de arte «Resonancia», §4) ──────────────
    /** Con este total o menos, todas a plena calidad. */
    private static final int ALL_FULL_UP_TO = 6;
    /** Hasta este total: el objetivo pleno, el resto núcleo + una onda. */
    private static final int MEDIUM_UP_TO = 12;
    /** Con más de MEDIUM_UP_TO: cuántas (además del objetivo) siguen con una onda. */
    private static final int MEDIUM_BUDGET = 5;
    /** Por encima de esto se agrupan las de menor prioridad. */
    private static final int MAX_INDIVIDUAL = 20;
    /** Lado de la celda (bloques) con la que se agrupan presencias próximas. */
    private static final double CLUSTER_CELL = 12.0;
    /** Distancia (bloques) a la que una presencia hostil cuenta como "cercana" para la prioridad. */
    private static final double HOSTILE_NEAR = 24.0;
    /** Alineamiento a partir del cual (hacia abajo) se lee como hostil. */
    private static final int HOSTILE_ALIGNMENT = -20;

    private static SenseKiMode mode = SenseKiMode.OFF;
    private static int tickCounter = 0;
    private static int selectCounter = 0;
    private static int level = 0;

    /** entityId -> presencia (incluidas las que se están desvaneciendo). */
    private static final Map<Integer, SenseKiPresence> PRESENCES = new HashMap<>();
    /** Presencias que se dibujan por separado, de MAYOR a menor prioridad. */
    private static final List<SenseKiPresence> SELECTED = new ArrayList<>();
    /** Zonas agregadas por saturación. */
    private static final List<Cluster> CLUSTERS = new ArrayList<>();

    /** Varias presencias de baja prioridad dibujadas como un solo eco. */
    public record Cluster(double x, double y, double z, int count, int maxStrength) {}

    @Nullable private static SenseKiDataPacket.TargetReading reading = null;
    private static long readingTick = Long.MIN_VALUE;

    /** Instante (System.nanoTime) en que se encendió el sentido, para el fundido de entrada. */
    private static long enabledNanos = Long.MIN_VALUE;
    private static final float FADE_IN_SECONDS = 0.6f;

    /** 0..1: opacidad global durante el fundido de entrada. 1 pasado el fundido. */
    public static float fadeIn() {
        if (enabledNanos == Long.MIN_VALUE) return 1f;
        float t = (System.nanoTime() - enabledNanos) / 1.0e9f / FADE_IN_SECONDS;
        return t >= 1f ? 1f : Math.max(0f, t * t * (3f - 2f * t));
    }

    public static SenseKiMode mode() { return mode; }

    /** Nivel con el que el servidor filtró la última respuesta (0 si no hay ninguna). */
    public static int level() { return level; }

    /** Presencias seleccionadas para dibujar, de mayor a menor prioridad. No modificar. */
    public static List<SenseKiPresence> selected() { return SELECTED; }

    public static List<Cluster> clusters() { return CLUSTERS; }

    /** Datos del eco de una entidad, o null si no se percibe (o ya se está desvaneciendo). */
    @Nullable
    public static SenseKiDataPacket.Entry entry(int entityId) {
        SenseKiPresence p = PRESENCES.get(entityId);
        return p == null || p.lostNanos >= 0 ? null : p.entry;
    }

    public static boolean isSensed(int entityId) { return entry(entityId) != null; }

    /** ¿Lleva un scouter en la cabeza? (delegado al estado del scouter). */
    public static boolean isScouterEquipped(Minecraft mc) {
        return ScouterClientState.isScouterEquipped(mc);
    }

    /** Apaga el sentir el ki desde fuera (el scouter lo fuerza al encenderse: excluyentes). */
    public static void forceOff() {
        mode = SenseKiMode.OFF;
        clear();
    }

    private static void clear() {
        PRESENCES.clear();
        SELECTED.clear();
        CLUSTERS.clear();
        reading = null;
        level = 0;
    }

    /** Nivel de la habilidad Ki Sense (0 = sin habilidad: solo barras y PL). */
    public static int senseKiLevel() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : SkillEffects.senseLevel(mc.player);
    }

    /** Pulsación de F4 (desde KeyBindings). Shift invierte el sentido del ciclo. */
    public static void onKeyPress(Minecraft mc) {
        if (mc.player == null) return;

        // Shift se lee del estado de la ventana, no de un KeyMapping aparte: una sola tecla
        // en el menú de controles y el modificador funciona con la que el jugador rebindee.
        boolean backwards = Screen.hasShiftDown();

        if (isScouterUsable(mc)) {
            ScouterClientState.cycle(mc, backwards);
            return;
        }
        // Sin la habilidad no se siente nada. El scouter es la vía alternativa: por eso
        // el gate va DESPUÉS del desvío al scouter, no antes.
        if (SkillEffects.senseLevel(mc.player) <= 0) {
            forceOff();
            mc.player.displayClientMessage(
                    Component.translatable("messages.zenkai.sense_ki.locked")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        SenseKiMode before = mode;
        mode = backwards ? mode.prev() : mode.next();
        if (mode == SenseKiMode.OFF) clear();
        else select(mc); // el filtro cambió: la selección también
        // Encender: fundido de entrada y un tono discreto. Pasar entre modos activos no repite ni el uno ni el otro.
        if (before == SenseKiMode.OFF && mode != SenseKiMode.OFF) {
            enabledNanos = System.nanoTime();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 0.35f));
        } else if (before != SenseKiMode.OFF && mode == SenseKiMode.OFF) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 0.25f));
        }

        mc.player.displayClientMessage(
                Component.translatable(mode.translationKey()).withStyle(ChatFormatting.AQUA),
                true); // actionbar
    }

    /** Llamar 1 vez por tick de cliente (desde KeyBindings.handleClientTick). */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            clear();
            mode = SenseKiMode.OFF;
            return;
        }
        if (mode == SenseKiMode.OFF) return;

        // Perdió la habilidad (respec, /zenkai skill revoke): se apaga solo.
        if (SkillEffects.senseLevel(mc.player) <= 0) {
            SenseKiWarnings.forget();
            forceOff();
            return;
        }

        if (++tickCounter >= SCAN_INTERVAL) {
            tickCounter = 0;
            PacketDistributor.sendToServer(new SenseKiScanPacket());
        }
        if (++selectCounter >= SELECT_INTERVAL) {
            selectCounter = 0;
            select(mc);
        }
    }

    /** Respuesta del servidor: actualiza las presencias; lo que ya no viene empieza a desvanecerse. */
    public static void onData(int newLevel, List<SenseKiDataPacket.Entry> entries,
                              @Nullable SenseKiDataPacket.TargetReading newReading) {
        Minecraft mc = Minecraft.getInstance();
        // Los avisos van ANTES del filtro y del refresco: un peligro no se filtra.
        SenseKiWarnings.onScan(mc, entries);
        if (mode == SenseKiMode.OFF) return; // respuesta rezagada tras apagarlo

        long now = System.nanoTime();
        level = newLevel;
        for (SenseKiPresence p : PRESENCES.values()) p.lostNanos = p.lostNanos < 0 ? -2 : p.lostNanos;
        for (SenseKiDataPacket.Entry e : entries) {
            SenseKiPresence p = PRESENCES.get(e.entityId());
            if (p == null) PRESENCES.put(e.entityId(), new SenseKiPresence(e, now));
            else p.update(e, now); // deja lostNanos en -1
        }
        // Las que siguen marcadas (-2) no vinieron en esta foto: empiezan a caducar. Incluido el
        // objetivo fijado: si el servidor ya no lo manda, su lectura no puede quedarse congelada.
        for (SenseKiPresence p : PRESENCES.values()) if (p.lostNanos == -2) p.lostNanos = now;

        reading = newReading;
        readingTick = newReading == null ? Long.MIN_VALUE : now;
        select(mc);
    }

    /** Última lectura exacta del objetivo fijado (solo nivel 5), o null. */
    @Nullable
    public static SenseKiDataPacket.TargetReading reading() { return reading; }

    /** Instante (System.nanoTime) en que llegó la lectura, para interpolar la vida en el panel. */
    public static long readingNanos() { return readingTick; }

    /** Filtro por modo (cliente: es una preferencia de visualización, no un gate de datos). */
    public static boolean passesFilter(SenseKiDataPacket.Entry e, Minecraft mc) {
        if (mc.player == null) return false;
        return switch (mode) {
            case OFF     -> false;
            case ALL     -> true;
            case PLAYERS -> e.isPlayer();
            case MOBS    -> !e.isPlayer();
            // Sin lock activo, targetId() es -1 y no pasa nadie: la pantalla queda limpia,
            // que es justo la señal de "no estás fijando a nadie".
            case LOCKED  -> e.entityId() == LockOnClientState.targetId();
        };
    }

    // ── Selección ────────────────────────────────────────────────────────────

    // Pesos de prioridad (§4 «Criterios de prioridad»). Escalones grandes para que cada criterio
    // domine a los siguientes; la distancia solo desempata dentro de un mismo escalón.
    private static final double W_LOCKED = 1.0e7;
    private static final double W_TARGETING_YOU = 1.0e6;
    private static final double W_BOSS = 1.0e5;
    private static final double W_HOSTILE_NEAR = 1.0e4;
    private static final double W_KI_ACTIVE = 1.0e3;

    private static final List<SenseKiPresence> SCRATCH = new ArrayList<>();
    private static final Map<Long, double[]> CLUSTER_ACC = new HashMap<>();

    /**
     * Ordena por una PUNTUACIÓN explícita (un solo sort, sin cadenas de reversed()) y reparte el
     * presupuesto: objetivo fijado siempre pleno; el resto según el total percibido. Se llama con
     * cada respuesta y cada pocos ticks, nunca desde el render.
     */
    static void select(Minecraft mc) {
        SELECTED.clear();
        CLUSTERS.clear();
        if (mc.player == null) return;
        long now = System.nanoTime();

        Iterator<SenseKiPresence> it = PRESENCES.values().iterator();
        while (it.hasNext()) if (it.next().expired(now)) it.remove();

        SCRATCH.clear();
        for (SenseKiPresence p : PRESENCES.values()) {
            if (passesFilter(p.entry, mc)) SCRATCH.add(p);
        }
        if (SCRATCH.isEmpty()) return;

        int lockedId = LockOnClientState.targetId();
        double ex = mc.player.getX(), ey = mc.player.getY(), ez = mc.player.getZ();
        SCRATCH.sort(Comparator.comparingDouble(p -> -score(p, lockedId, ex, ey, ez, now)));

        int total = SCRATCH.size();
        int mediumLeft = MEDIUM_BUDGET;
        for (int i = 0; i < total; i++) {
            SenseKiPresence p = SCRATCH.get(i);
            boolean locked = p.id == lockedId;
            SenseKiPresence.Tier tier;
            if (locked || total <= ALL_FULL_UP_TO) {
                tier = SenseKiPresence.Tier.FULL;
            } else if (total <= MEDIUM_UP_TO) {
                tier = SenseKiPresence.Tier.MEDIUM;
            } else if (mediumLeft > 0) {
                mediumLeft--;
                tier = SenseKiPresence.Tier.MEDIUM;
            } else if (SELECTED.size() < MAX_INDIVIDUAL) {
                tier = SenseKiPresence.Tier.SIMPLE;
            } else {
                tier = SenseKiPresence.Tier.CLUSTERED;
            }
            p.tier = tier;
            if (tier == SenseKiPresence.Tier.CLUSTERED) addToCluster(p);
            else SELECTED.add(p);
        }
        for (double[] acc : CLUSTER_ACC.values()) {
            int n = (int) acc[3];
            CLUSTERS.add(new Cluster(acc[0] / n, acc[1] / n, acc[2] / n, n, (int) acc[4]));
        }
        CLUSTER_ACC.clear();
        SCRATCH.clear();
    }

    private static double score(SenseKiPresence p, int lockedId, double ex, double ey, double ez, long now) {
        SenseKiDataPacket.Entry e = p.entry;
        double dx = p.feetX() - ex, dy = p.feetY() - ey, dz = p.feetZ() - ez;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double s = -dist;
        if (p.id == lockedId) s += W_LOCKED;
        if (e.has(SenseKiDataPacket.FLAG_TARGETING_YOU)) s += W_TARGETING_YOU;
        if (e.has(SenseKiDataPacket.FLAG_BOSS)) s += W_BOSS;
        boolean hostile = (e.hasAlignment() && e.alignment() <= HOSTILE_ALIGNMENT)
                || e.has(SenseKiDataPacket.FLAG_IN_COMBAT);
        if (hostile && dist <= HOSTILE_NEAR) s += W_HOSTILE_NEAR;
        if (e.has(SenseKiDataPacket.FLAG_CHARGING) || e.has(SenseKiDataPacket.FLAG_TRANSFORMED)
                || p.recentlyRose(now)) s += W_KI_ACTIVE;
        // Lo que se está desvaneciendo cede su sitio a lo que sigue presente.
        if (p.lostNanos >= 0) s -= W_KI_ACTIVE * 0.5;
        return s;
    }

    private static void addToCluster(SenseKiPresence p) {
        long kx = (long) Math.floor(p.feetX() / CLUSTER_CELL);
        long ky = (long) Math.floor(p.feetY() / CLUSTER_CELL);
        long kz = (long) Math.floor(p.feetZ() / CLUSTER_CELL);
        long key = (kx & 0x1FFFFF) | ((ky & 0x1FFFFF) << 21) | ((kz & 0x1FFFFF) << 42);
        double[] acc = CLUSTER_ACC.computeIfAbsent(key, k -> new double[5]);
        acc[0] += p.feetX();
        acc[1] += p.feetY() + p.entry.height() * 0.5;
        acc[2] += p.feetZ();
        acc[3] += 1;
        acc[4] = Math.max(acc[4], p.entry.strength());
    }

    /** ¿Lleva un scouter que FUNCIONA? Es lo que decide de quién es la tecla F4.
     *  Uno roto NO cuenta: un aparato muerto no debe bloquearte el sentido. */
    public static boolean isScouterUsable(Minecraft mc) {
        return ScouterClientState.isScouterUsable(mc);
    }
}
