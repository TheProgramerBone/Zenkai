package com.hmc.zenkai.client.combat;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Espejo cliente de StunState, por id de entidad (lo llena StunSyncPacket). Hace tres cosas:
 * anula el input del jugador local mientras está aturdido, alimenta el ActionContext que usan
 * las reglas de predicción, y dibuja unas estrellitas girando sobre la cabeza de cualquier
 * entidad aturdida (jugadores y mobs) para que se vea de un vistazo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class StunClientState {
    private StunClientState() {}

    /** Id de entidad -> tick de juego (cliente) en que termina el aturdimiento. */
    private static final Map<Integer, Long> UNTIL = new ConcurrentHashMap<>();

    private static final int STARS = 3;

    /** Id de entidad -> tick en que acaba la inmunidad posterior al aturdimiento (para el icono). */
    private static final Map<Integer, Long> IMMUNE_UNTIL = new ConcurrentHashMap<>();

    public static void accept(int entityId, int ticks, int immunityTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (ticks <= 0) {
            UNTIL.remove(entityId);
            // Corte anticipado (StunState.release): la inmunidad empieza ya.
            if (immunityTicks > 0) IMMUNE_UNTIL.put(entityId, level.getGameTime() + immunityTicks);
            return;
        }
        UNTIL.put(entityId, level.getGameTime() + ticks);
        if (immunityTicks > 0) IMMUNE_UNTIL.put(entityId, level.getGameTime() + ticks + immunityTicks);
        // Tres estrellas repartidas en círculo; cada una se ata a la entidad y vive lo que dure el stun
        // (ver StunStarParticle: xd = id, yd = fase, zd = ticks).
        var opts = com.hmc.zenkai.registry.ModParticles.stunStar(0xFFFFFF, 1.0f);
        for (int i = 0; i < STARS; i++) {
            level.addParticle(opts, true, 0, 0, 0, entityId, i * (Math.PI * 2.0 / STARS), ticks);
        }
    }

    /** Ticks que le quedan de inmunidad al aturdimiento (ya terminado el stun); 0 si no la tiene. */
    public static int immunityRemainingTicks(int entityId) {
        Long until = IMMUNE_UNTIL.get(entityId);
        ClientLevel level = Minecraft.getInstance().level;
        if (until == null || level == null) return 0;
        long left = until - level.getGameTime();
        if (left <= 0) { IMMUNE_UNTIL.remove(entityId); return 0; }
        return (int) left;
    }

    /** Ticks que le quedan al aturdimiento de esa entidad (0 si no lo está). */
    public static int remainingTicks(int entityId) {
        Long until = UNTIL.get(entityId);
        ClientLevel level = Minecraft.getInstance().level;
        if (until == null || level == null) return 0;
        return (int) Math.max(0L, until - level.getGameTime());
    }

    public static boolean isStunned(int entityId) {
        Long until = UNTIL.get(entityId);
        if (until == null) return false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || until <= level.getGameTime()) {
            UNTIL.remove(entityId);
            return false;
        }
        return true;
    }

    public static boolean localStunned() {
        var p = Minecraft.getInstance().player;
        return p != null && isStunned(p.getId());
    }

    /** Plantado en una Evasiva (Super Explosive Wave): el cuerpo es el epicentro inamovible, sin andar ni volar. */
    public static boolean localRooted() {
        var a = com.hmc.zenkai.client.action.ActionStateClient.local();
        return a.physBusy()
                && a.payload() == com.hmc.zenkai.feature.technique.PhysicalTechnique.EVADE.ordinal();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        UNTIL.clear();
        IMMUNE_UNTIL.clear();
        GuardClientState.reset();
    }

    /** Aturdido = ni andar, ni saltar, ni agacharse, ni subir o bajar volando. Mismo patrón que el choque de haces. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent e) {
        if (!localStunned() && !localRooted()) return;
        Input in = e.getInput();
        in.forwardImpulse = 0f;
        in.leftImpulse = 0f;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }
}
