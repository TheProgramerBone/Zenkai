package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.event.CombatZenkaiHooks;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lado SERVIDOR del modo combate (mismo patrón que FlyAnimServerState):
 * guarda el último estado por jugador, lo difunde a los trackers, se lo manda a quien
 * empieza a trackear, y limpia (avisando) en respawn/logout.
 * También es quien engancha el COOLDOWN DE GOLPE de vanilla. El sistema (el ticker de
 * Player, el escalado cuadrático del daño y el indicador de la mira) cuelga del atributo
 * minecraft:attack_speed del JUGADOR, no del item que lleve: por eso no hace falta un arma
 * custom ni un mixin, basta con bajarle el atributo mientras el modo está activo.
 * El valor tiene que quedar por DEBAJO de 4.0 o Gui.renderCrosshair esconde el indicador
 * (exige getCurrentItemAttackStrengthDelay() > 5 ticks, y 20/4.0 = 5 justos).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class CombatModeServerState {
    private CombatModeServerState() {}

    private static final Map<UUID, Byte> ACTIVE = new ConcurrentHashMap<>(); // valor = estilo

    private static final ResourceLocation COMBAT_ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "combat_attack_speed");

    public static void update(ServerPlayer sp, boolean active) {
        byte style = (byte) PlayerStatsAttachment.get(sp).getStyle().ordinal();
        if (active) {
            ACTIVE.put(sp.getUUID(), style);
        } else {
            ACTIVE.remove(sp.getUUID());
        }
        if (!active) KiCombatServer.setBlocking(sp, false);
        applyAttackSpeed(sp, active);
        PacketDistributor.sendToPlayersTrackingEntity(sp,
                new CombatModeSyncPacket(sp.getId(), active, style));
    }

    /** ¿Está este jugador en modo combate? (consulta del pipeline de daño). */
    public static boolean isActive(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    /**
     * Pone o quita el modificador de attack_speed.
     * El importe se calcula contra getBaseValue() en vez de asumir el 4.0 de vanilla: así
     * sigue dando el valor objetivo aunque otro mod cambie la base del jugador.
     * Transient a propósito — no se persiste, así que un crash en pleno combate no deja a
     * nadie golpeando lento para siempre.
     */
    private static void applyAttackSpeed(ServerPlayer sp, boolean active) {
        AttributeInstance attr = sp.getAttribute(Attributes.ATTACK_SPEED);
        if (attr == null) return;
        attr.removeModifier(COMBAT_ATTACK_SPEED_ID);   // idempotente: re-aplicar es seguro
        if (!active) return;
        double target = CommonConfig.combatAttackSpeed();
        double delta = target - attr.getBaseValue();
        if (delta >= 0.0) return;   // no tiene sentido ACELERAR: sin cooldown no hay indicador
        attr.addTransientModifier(new AttributeModifier(
                COMBAT_ATTACK_SPEED_ID, delta, AttributeModifier.Operation.ADD_VALUE));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getTarget() instanceof ServerPlayer target)) return;
        if (!(e.getEntity() instanceof ServerPlayer tracker)) return;
        Byte style = ACTIVE.get(target.getUUID());
        if (style != null) {
            PacketDistributor.sendToPlayer(tracker,
                    new CombatModeSyncPacket(target.getId(), true, style));
        }
    }

    /**
     * Cambiar de dimensión reconstruye la entidad del jugador, y con ella el AttributeMap:
     * el modificador transitorio se pierde pero ACTIVE sigue puesto. Sin esto, cruzar un
     * portal en modo combate devolvía al jugador a los 5 ticks de recarga sin avisar.
     */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && isActive(sp.getUUID())) {
            applyAttackSpeed(sp, true);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) clear(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            clear(sp);
            // La escala de golpe capturada se guarda por UUID: sin esto queda una entrada
            // por cada jugador que haya entrado al servidor alguna vez.
            CombatZenkaiHooks.forgetAttackScale(sp.getUUID());
        }
    }

    private static void clear(ServerPlayer sp) {
        KiCombatServer.setBlocking(sp, false);
        applyAttackSpeed(sp, false);
        if (ACTIVE.remove(sp.getUUID()) != null) {
            PacketDistributor.sendToPlayersTrackingEntity(sp,
                    new CombatModeSyncPacket(sp.getId(), false, (byte) 0));
        }
    }
}