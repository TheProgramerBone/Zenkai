package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.event.CombatZenkaiHooks;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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
    private static final Map<UUID, Item> LAST_MAINHAND = new ConcurrentHashMap<>();

    private static final ResourceLocation COMBAT_ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "combat_attack_speed");

    public static void update(ServerPlayer sp, boolean active) {
        byte style = (byte) PlayerStatsAttachment.get(sp).getStyle().ordinal();
        if (active) {
            ACTIVE.put(sp.getUUID(), style);
            // Tutorial de la tecla X: dispara en cada activación (vanilla deduplica el toast
            // una vez obtenido), no solo la primera vez, igual que technique()/wish().
            ZenkaiTriggers.MILESTONE.get().trigger(sp, ZenkaiTriggers.Kinds.COMBAT_STANCE);
        } else {
            ACTIVE.remove(sp.getUUID());
        }
        if (!active) KiCombatServer.applyBlocking(sp, false);
        applyAttackSpeed(sp, active);
        if (active) LAST_MAINHAND.put(sp.getUUID(), sp.getMainHandItem().getItem());
            else        LAST_MAINHAND.remove(sp.getUUID());
        PacketDistributor.sendToPlayersTrackingEntity(sp,
                new CombatModeSyncPacket(sp.getId(), active, style));
    }

    /** ¿Está este jugador en modo combate? (consulta del pipeline de daño). */
    public static boolean isActive(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    /**
     * Pone o quita el modificador de attack_speed, tratándolo como un TECHO y no como un
     * valor absoluto.
     * OJO CON EL CÁLCULO: se mide contra getValue() (total ya CON los modificadores del arma
     * equipada), no contra getBaseValue(). Con la base, nuestro -2.4 se sumaba al -2.4 propio
     * de una espada y el total caía a -0.8; ATTACK_SPEED tiene mínimo 0.0, así que se saneaba
     * a 0, el delay pasaba a ser 1/0 = infinito y getAttackStrengthScale devolvía 0 para
     * siempre: el indicador no cargaba nunca y cada golpe pegaba al 20%.
     * Quitamos primero el nuestro para que getValue() sea la lectura limpia del arma.
     * Si el arma YA es más lenta que el objetivo no tocamos nada: el modo combate marca un
     * suelo de lentitud, no obliga a cualquier arma a golpear igual.
     * Transient a propósito — no se persiste, así que un crash en pleno combate no deja a
     * nadie golpeando lento para siempre.
     */
    private static void applyAttackSpeed(ServerPlayer sp, boolean active) {
        AttributeInstance attr = sp.getAttribute(Attributes.ATTACK_SPEED);
        if (attr == null) return;
        attr.removeModifier(COMBAT_ATTACK_SPEED_ID);   // idempotente: re-aplicar es seguro
        if (!active) return;
        double target = ServerConfig.combatAttackSpeed();
        double current = attr.getValue();   // sin el nuestro, CON el del arma
        double delta = target - current;
        if (delta >= 0.0) return;   // ya es igual de lento o más: nada que hacer
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

    /**
    * Recalcula el techo de attack_speed cuando cambia el arma. Va en el TICK y no en
    * LivingEquipmentChangeEvent a propósito: ese evento se dispara mientras el intercambio
    * de equipo se está procesando, así que getValue() todavía no incluye los modificadores
    * del item nuevo. Leíamos 4.0 (como si fuera mano vacía), aplicábamos -2.4, y después
    * entraba el -2.4 propio de la espada: total -0.8, saneado a 0, delay infinito y el
    * indicador sin cargar nunca. En PlayerTickEvent.Post el equipamiento del tick ya está
    * resuelto, así que la lectura siempre es la buena.
    * Solo toca el atributo cuando el item CAMBIA (o cuando detecta el estado imposible),
    * no cada tick: quitar y volver a poner el modificador marca el atributo como sucio y
    * dispara un packet de sync por tick.
    */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!isActive(sp.getUUID())) return;

        Item now = sp.getMainHandItem().getItem();
        boolean changed = now != LAST_MAINHAND.get(sp.getUUID());
        if (!changed && !isAttackSpeedBroken(sp)) return;

        LAST_MAINHAND.put(sp.getUUID(), now);
        applyAttackSpeed(sp, true);
    }

    /** Red de seguridad: attack_speed 0 es un estado IMPOSIBLE (delay infinito, el golpe no
     *  carga jamás). Si algo por lo que no hemos pasado lo deja así — otro mod, un slot que
     *  no vigilamos, un curio — lo reparamos en el siguiente tick en vez de dejar al jugador
     *  pegando al 20% sin saber por qué. */
            private static boolean isAttackSpeedBroken(ServerPlayer sp) {
                AttributeInstance attr = sp.getAttribute(Attributes.ATTACK_SPEED);
                return attr != null && attr.getValue() <= 0.01;
            }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) clear(sp);
    }

    /** Morir corta el modo combate en el acto, sin esperar al respawn: el atacante que lo
     *  mató no debería ver el golpe de gracia con el cooldown de golpe todavía rebajado. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) clear(sp);
    }

    /** Red de seguridad al entrar: un cierre anómalo del servidor a mitad de combate no debe
     *  dejar al jugador con el attack_speed rebajado desde antes de reconectar. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) clear(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            clear(sp);
            // La escala de golpe capturada, la causa de muerte pendiente y el proc de Black
            // Flash sin consumir se guardan por UUID: sin esto queda una entrada por cada
            // jugador que haya entrado al servidor alguna vez.
            CombatZenkaiHooks.forgetAttackScale(sp.getUUID());
            DeathCauseTracker.forget(sp.getUUID());
            BlackFlash.forget(sp.getUUID());
        }
    }

    private static void clear(ServerPlayer sp) {
        KiCombatServer.applyBlocking(sp, false);
        applyAttackSpeed(sp, false);
        LAST_MAINHAND.remove(sp.getUUID());
        if (ACTIVE.remove(sp.getUUID()) != null) {
            PacketDistributor.sendToPlayersTrackingEntity(sp,
                    new CombatModeSyncPacket(sp.getId(), false, (byte) 0));
        }
    }
}