package com.hmc.zenkai.client.rage;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.compose.LongTransformMode;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Retrasa EN EL CLIENTE el cambio de forma que se ve durante una secuencia larga: la forma (cuerpo, pelo, cara, aura) debe transicionar
 * CONFORME avanza la escena, en el estallido, y no estar ya puesta cuando empieza. El servidor no se entera ni lo necesita: aplica el
 * cambio en el acto como siempre (estadísticas, desbloqueos, protección); esto es presentación pura.
 * <p>
 * Funcionamiento: el aviso de secuencia larga ({@code LongTransformPacket}) llega ANTES que el sync de forma, y lo ARMA ({@link #arm}).
 * Cuando llega el {@code SyncPlayerFormPacket} con una forma distinta, {@link #afterLoad} deja puesta la forma ANTERIOR y apunta la
 * de destino, que se aplica a los {@link #DELAY_TICKS} del aviso (el estallido de la escena). Mientras tanto, otros sync de forma del
 * mismo jugador (maestría, etc.) siguen cargándose pero no pisan la forma mostrada, y el aura se pinta ya con la forma de destino
 * ({@code PlayerFormAttachment.auraDef}). Sin aviso, o con el modo en «Apagada», no se retrasa nada.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ClientFormDelay {
    private ClientFormDelay() {}

    /** Ticks hasta aplicar la forma: el estallido de la escena (AwakeningScene.BURST_TICKS). */
    public static final int DELAY_TICKS = AwakeningScene.BURST_TICKS;
    /** Un aviso armado que no recibe su sync en este margen se descarta. */
    private static final int ARM_WINDOW_TICKS = 100;

    private static final class Deferred {
        ResourceLocation shown, target;
        long applyAt;
    }

    private static final Map<UUID, Long> ARMED = new HashMap<>();
    private static final Map<Integer, Deferred> DEFERRED = new HashMap<>();

    /** El aviso de secuencia larga de este jugador acaba de llegar. */
    public static void arm(UUID player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientConfig.longTransformMode() == LongTransformMode.OFF) return;
        ARMED.put(player, mc.level.getGameTime());
    }

    /** Se llama justo después de cargar un sync de forma; {@code before} es la forma que había antes de cargarlo. */
    public static void afterLoad(Player p, PlayerFormAttachment att, ResourceLocation before) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ResourceLocation incoming = att.getFormId();
        Deferred d = DEFERRED.get(p.getId());
        if (d != null) {
            if (!incoming.equals(d.shown)) d.target = incoming;   // un cambio nuevo a mitad de escena sustituye al destino
            att.setFormId(d.shown);
            att.setAuraFormOverride(d.target);
            return;
        }
        Long armedAt = ARMED.get(p.getUUID());
        // Un sync que NO cambia la forma (maestría, progreso del hold, estado de stun…) puede llegar entre el aviso y el sync que sí
        // la cambia: NO debe gastar el aviso, o el cambio llegaba sin retraso y la forma se veía puesta ANTES de la secuencia larga.
        if (armedAt == null || incoming.equals(before) || FormIds.BASE.equals(incoming)) return;
        ARMED.remove(p.getUUID());
        long now = mc.level.getGameTime();
        if (now - armedAt > ARM_WINDOW_TICKS || armedAt + DELAY_TICKS <= now) return;
        Deferred nd = new Deferred();
        nd.shown = before;
        nd.target = incoming;
        nd.applyAt = armedAt + DELAY_TICKS;
        DEFERRED.put(p.getId(), nd);
        att.setFormId(before);
        att.setAuraFormOverride(incoming);   // el aura ya es la de la forma de destino
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        long now = mc.level.getGameTime();
        ARMED.values().removeIf(t -> now - t > ARM_WINDOW_TICKS * 2L);
        for (Iterator<Map.Entry<Integer, Deferred>> it = DEFERRED.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (!(mc.level.getEntity(en.getKey()) instanceof Player p)) { it.remove(); continue; }
            if (now < en.getValue().applyAt) continue;
            var att = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
            att.setFormId(en.getValue().target);
            att.setAuraFormOverride(null);
            it.remove();
        }
    }

    /** Secuencia cancelada: no queda aviso armado ni cambio retrasado de este jugador. */
    public static void cancel(UUID player) {
        ARMED.remove(player);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getPlayerByUUID(player) instanceof Player p)) return;
        Deferred d = DEFERRED.remove(p.getId());
        if (d == null) return;
        var att = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        att.setFormId(d.target);   // el servidor ya aplicó la forma: se muestra la real
        att.setAuraFormOverride(null);
    }

    public static void clear() {
        ARMED.clear();
        DEFERRED.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}
