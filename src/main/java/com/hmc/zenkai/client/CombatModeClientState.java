package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.combat.CombatModePacket;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerTechniques;
import com.hmc.zenkai.core.network.feature.technique.KiFirePacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado CLIENTE del modo combate (paso 5, overlay derecho estilo Cursed Fate).
 *
 *  - X: entra/sale (avisa al servidor; este re-difunde a los trackers para la pose).
 *  - En modo combate, las teclas 1-9 mueven EXCLUSIVAMENTE la selección del overlay:
 *    consumimos sus clicks en ClientTickEvent.Pre, ANTES de que Minecraft.handleKeybinds
 *    los procese, así la hotbar vanilla no cambia. La RUEDA no se toca: sigue moviendo
 *    solo la hotbar vanilla.
 *  - R: dispara lo asignado en la posición seleccionada.
 *  - REMOTES: modo combate de otros jugadores (entityId -> estilo), para la pose PAL.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class CombatModeClientState {
    private CombatModeClientState() {}

    /** Estado remoto: estilo con el que posar (ordinal de Style). */
    public record Remote(byte styleOrdinal) {}

    private static boolean active = false;
    private static int selected = 0; // posición del overlay (0..8)
    private static final Map<Integer, Remote> REMOTES = new ConcurrentHashMap<>();

    public static boolean isActive() { return active; }
    public static int selected()     { return selected; }

    public static Remote remote(int entityId) { return REMOTES.get(entityId); }

    /** Tecla X. */
    public static void toggle(Minecraft mc) {
        if (mc.player == null) return;
        if (!active && !PlayerStatsAttachment.get(mc.player).isRaceChosen()) return;
        active = !active;
        PacketDistributor.sendToServer(new CombatModePacket(active));
    }

    /** Tecla R: dispara lo asignado en la posición seleccionada del overlay. */
    public static void fireSelected(Minecraft mc) {
        if (!active || mc.player == null) return;
        int techSlot = PlayerStatsAttachment.get(mc.player).techniques().binding(selected);
        if (techSlot >= 0) {
            PacketDistributor.sendToServer(new KiFirePacket(techSlot));
        }
    }

    /**
     * Intercepción de las teclas de hotbar: en Pre del tick de cliente, ANTES de que
     * vanilla procese sus keybinds. Consumir el click aquí evita que cambie el ítem.
     */
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null || mc.screen != null) return;
        for (int i = 0; i < Math.min(mc.options.keyHotbarSlots.length,
                PlayerTechniques.BIND_POSITIONS); i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                selected = i;
            }
        }
    }

    /** Desde CombatModeSyncPacket. */
    public static void onSync(int entityId, boolean modeActive, byte styleOrdinal) {
        if (modeActive) {
            REMOTES.put(entityId, new Remote(styleOrdinal));
        } else {
            REMOTES.remove(entityId);
        }
    }

    /** Llamar 1 vez por tick de cliente (junto a los demás ticks de estado). */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        REMOTES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    public static void reset() {
        active = false;
        selected = 0;
        REMOTES.clear();
    }
}