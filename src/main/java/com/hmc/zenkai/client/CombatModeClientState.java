package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.core.network.feature.combat.BlockingPacket;
import com.hmc.zenkai.core.network.feature.combat.CombatModePacket;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerTechniques;
import com.hmc.zenkai.core.network.feature.technique.KiFirePacket;
import com.hmc.zenkai.core.network.feature.technique.PhysicalFirePacket;
import com.hmc.zenkai.core.technique.KiTechnique;
import com.hmc.zenkai.core.technique.KiTechniqueType;
import com.hmc.zenkai.core.technique.PhysicalTechnique;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado CLIENTE del modo combate.
 *  - X: entra/sale. 1-9: mueven SOLO la selección del overlay (clicks consumidos en
 *    ClientTickEvent.Pre, antes de handleKeybinds). Rueda: solo hotbar vanilla.
 *  - CARGA (estilo DBC): R + click derecho SOSTENIDOS cargan la técnica del slot
 *    seleccionado (cast time por tipo); SOLTAR el click derecho dispara si la carga
 *    llegó al 25%; soltar R primero CANCELA. La barra central la dibuja
 *    TechniqueHotbarOverlay con chargeRatio().
 *  - DEFENSA: click derecho SIN R + manos vacías = bloquear (edge-triggered al server).
 *  - COOLDOWNS espejo: al disparar se anota localmente ready-at por slot (el servidor
 *    es autoritativo; esto es solo para pintar el overlay y no cargar en vano).
 *  - REMOTES / REMOTE_BLOCKING: estados de otros jugadores para las poses PAL.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class CombatModeClientState {
    private CombatModeClientState() {}

    /** Estado remoto: estilo con el que posar (ordinal de Style). */
    public record Remote(byte styleOrdinal) {}

    private static boolean active = false;
    private static int selected = 0; // posición del overlay (0..8)

    // Carga en curso
    private static int chargingSlot = -1;   // slot de técnica latcheado al iniciar
    /** Posición (0..8) cuya tecla sostiene la carga actual, o -1. */
    private static int chargingKey = -1;
    private static int chargeTicks = 0;

    // Cooldowns espejo (slot -> gameTime en que vuelve a estar listo)
    private static final Map<Integer, Long> READY_AT = new ConcurrentHashMap<>();

    // Bloqueo local (edge-trigger)
    private static boolean lastBlockingSent = false;

    /** Cooldowns cliente de físicas (gameTime listo), por ordinal. */
    private static final Map<Integer, Long> PHYS_READY_AT = new HashMap<>();
    private static boolean lastPhysCombo = false;

    // Estados remotos
    private static final Map<Integer, Remote> REMOTES = new ConcurrentHashMap<>();
    private static final Set<Integer> REMOTE_BLOCKING = ConcurrentHashMap.newKeySet();

    public static boolean isActive()  { return active; }
    public static int selected()      { return selected; }
    public static boolean isBlockingLocal() { return lastBlockingSent; }

    public static boolean isCharging() { return chargingSlot >= 0; }
    public static int chargingSlot()   { return chargingSlot; }

    /** 0..1 respecto al casttime del tipo cargándose (0 si no hay carga). */
    public static double chargeRatio(Minecraft mc) {
        if (chargingSlot < 0 || mc.player == null) return 0;
        KiTechnique t = PlayerStatsAttachment.get(mc.player).techniques().slot(chargingSlot);
        if (t == null) return 0;
        return Math.min(1.0, chargeTicks / (double) Math.max(1, t.type().chargeTicks));
    }

    /** Fracción de cooldown RESTANTE del slot (0 = listo), para pintar el overlay. */
    public static double cooldownFraction(Minecraft mc, int slot) {
        if (mc.level == null) return 0;
        Long ready = READY_AT.get(slot);
        if (ready == null) return 0;
        long now = mc.level.getGameTime();
        if (now >= ready) return 0;
        KiTechnique t = mc.player == null ? null
                : PlayerStatsAttachment.get(mc.player).techniques().slot(slot);
        int total = t == null ? 20 : Math.max(1, t.type().cooldownTicks);
        return Math.min(1.0, (ready - now) / (double) total);
    }

    public static Remote remote(int entityId) { return REMOTES.get(entityId); }
    public static boolean isBlockingRemote(int entityId) { return REMOTE_BLOCKING.contains(entityId); }

    /** Tecla X. */
    public static void toggle(Minecraft mc) {
        if (mc.player == null) return;
        if (!active && !PlayerStatsAttachment.get(mc.player).isRaceChosen()) return;
        active = !active;
        if (!active) cancelCharge();
        PacketDistributor.sendToServer(new CombatModePacket(active));
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean rightDown = mc.options.keyUse.isDown();
        boolean handsFree = mc.player.getMainHandItem().isEmpty()
                && mc.player.getOffhandItem().isEmpty();
        boolean inGame = mc.screen == null && mc.getConnection() != null;

        // ── Teclas 1-9 (consumidas ANTES de handleKeybinds; la hotbar vanilla no se mueve):
        //    primera pulsación = seleccionar; SEGUNDA pulsación sobre la seleccionada = actuar
        //    (física: ejecutar; ki: empezar carga sosteniendo la tecla). ──
        boolean pressedSelected = false;
        if (active && mc.screen == null) {
            var binds = PlayerStatsAttachment.get(mc.player).techniques();
            for (int i = 0; i < Math.min(mc.options.keyHotbarSlots.length,
                    PlayerTechniques.BIND_POSITIONS); i++) {
                while (mc.options.keyHotbarSlots[i].consumeClick()) {
                    if (binds.physicalBinding(i) != null) {
                        // FÍSICA: se ejecuta A LA PRIMERA pulsación
                        selected = i;
                        cancelCharge();
                        pressedSelected = true;
                    } else if (i != selected) {
                        selected = i;
                        cancelCharge(); // cambiar de casilla cancela la carga en curso
                    } else {
                        pressedSelected = true;
                    }
                }
            }
        }

        // ── Actuar sobre la casilla seleccionada ──
        if (pressedSelected && active && inGame && handsFree && mc.level != null) {
            var techniques = PlayerStatsAttachment.get(mc.player).techniques();
            PhysicalTechnique phys = techniques.physicalBinding(selected);
            if (phys != null) {
                // Física: instantánea (con cooldown local optimista + anim).
                long now = mc.level.getGameTime();
                if (PHYS_READY_AT.getOrDefault(phys.ordinal(), 0L) <= now) {
                    PacketDistributor.sendToServer(new PhysicalFirePacket(phys.ordinal()));
                    PHYS_READY_AT.put(phys.ordinal(), now + phys.cooldownTicks); // optimista
                    ZenkaiPalAnimations.playPhysical(mc.player, phys); // anim local (sync remoto: pendiente)
                }
            } else if (chargingSlot < 0) {
                int bound = techniques.binding(selected);
                if (bound >= 0 && cooldownFraction(mc, bound) <= 0) {
                    chargingSlot = bound;
                    chargingKey = selected;
                    chargeTicks = 0;
                }
            }
        }

        // ── Máquina de CARGA: se sostiene con la TECLA DE NÚMERO; soltar = disparar (≥25%). ──
        if (chargingSlot >= 0) {
            KiTechnique t = PlayerStatsAttachment.get(mc.player).techniques().slot(chargingSlot);
            boolean held = chargingKey >= 0
                    && chargingKey < mc.options.keyHotbarSlots.length
                    && mc.options.keyHotbarSlots[chargingKey].isDown();
            if (!active || !inGame || !handsFree || t == null) {
                cancelCharge();
            } else if (!held) {
                // Soltar el número: DISPARA si llegó al mínimo; si no, cancela.
                double ratio = chargeTicks / (double) Math.max(1, t.type().chargeTicks);
                if (ratio >= KiTechniqueType.MIN_CHARGE) {
                    PacketDistributor.sendToServer(new KiFirePacket(chargingSlot, chargeTicks));
                    if (mc.level != null) {
                        READY_AT.put(chargingSlot,
                                mc.level.getGameTime() + t.type().cooldownTicks);
                    }
                }
                cancelCharge();
            } else {
                chargeTicks = Math.min(chargeTicks + 1, t.type().chargeTicks);
            }
        }

        // ── DEFENSA: click derecho + manos vacías (edge-trigger al server) ──
        boolean blocking = active && inGame && rightDown
                && chargingSlot < 0 && handsFree;
        if (blocking != lastBlockingSent) {
            lastBlockingSent = blocking;
            if (mc.getConnection() != null) {
                PacketDistributor.sendToServer(new BlockingPacket(blocking));
            }
        }

        // Bloquear = no atacar: consume los clicks de ataque para que ni haga el swing
        // (el server además cancela AttackEntityEvent — guard en ambos lados).
        if (blocking) {
            while (mc.options.keyAttack.consumeClick()) { /* descartado */ }
        }
    }

    private static void cancelCharge() {
        chargingSlot = -1;
        chargingKey = -1;
        chargeTicks = 0;
    }

    /** Desde CombatModeSyncPacket. */
    public static void onSync(int entityId, boolean modeActive, byte styleOrdinal) {
        if (modeActive) {
            REMOTES.put(entityId, new Remote(styleOrdinal));
        } else {
            REMOTES.remove(entityId);
        }
    }

    /** Desde BlockingSyncPacket. */
    public static void onBlockingSync(int entityId, boolean blocking) {
        if (blocking) REMOTE_BLOCKING.add(entityId); else REMOTE_BLOCKING.remove(entityId);
    }

    /** Llamar 1 vez por tick de cliente (junto a los demás ticks de estado). */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        REMOTES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        REMOTE_BLOCKING.removeIf(id -> mc.level.getEntity(id) == null);
    }

    public static void reset() {
        active = false;
        selected = 0;
        cancelCharge();
        READY_AT.clear();
        lastBlockingSent = false;
        REMOTES.clear();
        REMOTE_BLOCKING.clear();
    }

    /** Fracción de cooldown restante de una física (0 = lista), para el overlay. */
    public static double physCooldownFraction(Minecraft mc, PhysicalTechnique t) {
        if (mc.level == null || t == null) return 0;
        long left = PHYS_READY_AT.getOrDefault(t.ordinal(), 0L) - mc.level.getGameTime();
        return left <= 0 ? 0 : Math.min(1.0, left / (double) Math.max(1, t.cooldownTicks));
    }
}