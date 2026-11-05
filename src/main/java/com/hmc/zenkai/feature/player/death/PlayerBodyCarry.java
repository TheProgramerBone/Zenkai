package com.hmc.zenkai.feature.player.death;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Llevar un cuerpo en brazos (estilo CarryOn): Shift + clic derecho sobre el cuerpo lo levanta (dueño, party o
 * cualquiera con el grifeo activo, según {@link PlayerBodyAccess}) y Shift + clic derecho en un bloque lo suelta.
 *
 * El cuerpo SALE del mundo mientras se lleva (su estado entero va al adjunto {@code CARRIED_BODY}) y vuelve a
 * crearse al soltarlo: no hay una entidad que arrastrar entre chunks, dimensiones ni desconexiones. Si el que lo
 * lleva muere o se desconecta, el cuerpo se suelta a sus pies (no se queda en el limbo de un jugador ausente).
 * Mientras se lleva, el jugador va lento: pesa.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PlayerBodyCarry {
    private PlayerBodyCarry() {}

    public static CarriedBody carried(ServerPlayer sp) {
        return sp.getData(ZenkaiDataAttachments.CARRIED_BODY.get());
    }

    public static void pickUp(ServerPlayer sp, PlayerBodyEntity body) {
        if (!com.hmc.zenkai.config.ServerConfig.PERMADEATH_CARRY_ENABLED.get()) {
            sp.displayClientMessage(Component.translatable("zenkai.body.carry_disabled"), true);
            return;
        }
        if (!carried(sp).isNone()) {
            sp.displayClientMessage(Component.translatable("zenkai.body.already_carrying"), true);
            return;
        }
        sp.setData(ZenkaiDataAttachments.CARRIED_BODY.get(), new CarriedBody(
                body.saveForCarry(), body.owner().orElse(CarriedBody.NIL), body.ownerName(), body.skinTextures(), body.scale(), body.gear()));
        body.discard();   // su remove() lo saca del índice
        sp.displayClientMessage(Component.translatable("zenkai.body.picked_up", body.ownerName()), true);
    }

    /** Crea de nuevo el cuerpo con su estado en esa posición. false (y avisa) si no cabe. */
    public static boolean place(ServerPlayer sp, double x, double y, double z, boolean force) {
        CarriedBody c = carried(sp);
        if (c.isNone() || !(sp.level() instanceof ServerLevel level)) return false;
        PlayerBodyEntity body = ModEntities.PLAYER_BODY.get().create(level);
        if (body == null) return false;
        body.loadFromCarry(c.nbt());
        body.moveTo(x, y, z, sp.getYRot(), 0f);
        AABB box = body.getBoundingBox();
        if (!force && !level.noCollision(body, box)) {
            sp.displayClientMessage(Component.translatable("zenkai.body.no_room"), true);
            return false;
        }
        if (!level.addFreshEntity(body)) return false;
        PlayerBodyRegistry.get(sp.server).put(new PlayerBodyRegistry.Entry(
                body.getUUID(), c.owner(), level.dimension(), body.blockPosition(), body.createdAt()));
        sp.removeData(ZenkaiDataAttachments.CARRIED_BODY.get());
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return true;
    }

    /**
     * ¿Tiene las manos ocupadas por un cuerpo? Lo leen el servidor y el cliente (el adjunto se sincroniza). Con
     * {@code permadeath.carry_restrictions} apagada se puede llevar un cuerpo sin ninguna restricción.
     */
    public static boolean restricted(net.minecraft.world.entity.player.Player p) {
        return !p.getData(ZenkaiDataAttachments.CARRIED_BODY.get()).isNone()
                && com.hmc.zenkai.config.ServerConfig.PERMADEATH_CARRY_RESTRICTIONS.get();
    }

    /** Aviso de «manos ocupadas» en la action bar, como mucho una vez por segundo para no saturar. */
    private static void busy(net.minecraft.world.entity.player.Player p) {
        if (p instanceof ServerPlayer sp && sp.tickCount % 20 == 0)
            sp.displayClientMessage(Component.translatable("zenkai.body.hands_busy"), true);
    }

    // ── Restricciones (las de CarryOn, adaptadas): con un cuerpo en brazos las manos están ocupadas ─────────────────
    // Atacar, picar, colocar, usar objetos e interactuar quedan bloqueados; las acciones de Zenkai (pelea, ki, transformar,
    // vuelo, transmisión) lo están en ActionRules / ActionResolver / FlightSystem / los paquetes de carga. Lo que CarryOn
    // bloquea y aquí NO hace falta: cambiar de casilla de la barra (los objetos ya no se pueden usar).

    @SubscribeEvent
    public static void onPlaceClick(PlayerInteractEvent.RightClickBlock e) {
        var p = e.getEntity();
        if (p.getData(ZenkaiDataAttachments.CARRIED_BODY.get()).isNone()) return;
        boolean place = p.isShiftKeyDown() && e.getHand() == InteractionHand.MAIN_HAND;
        if (!place && !restricted(p)) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (place && p instanceof ServerPlayer sp) {
            BlockPos at = e.getPos().relative(e.getFace());
            place(sp, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, false);
        } else if (!place) busy(p);
    }

    @SubscribeEvent
    public static void onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent e) {
        if (restricted(e.getEntity())) { e.setCanceled(true); busy(e.getEntity()); }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (restricted(e.getEntity())) { e.setCanceled(true); busy(e.getEntity()); }
    }

    @SubscribeEvent
    public static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e) {
        if (restricted(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreakSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed e) {
        if (restricted(e.getEntity())) e.setNewSpeed(0f);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem e) {
        if (restricted(e.getEntity())) { e.setCanceled(true); busy(e.getEntity()); }
    }

    @SubscribeEvent
    public static void onStartUsing(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start e) {
        if (e.getEntity() instanceof net.minecraft.world.entity.player.Player p && restricted(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        var p = e.getEntity();
        if (!restricted(p)) return;
        e.setCanceled(true);
        if (e.getTarget() instanceof PlayerBodyEntity && p instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("zenkai.body.already_carrying"), true);
        else busy(p);
    }

    /** Un cuerpo en brazos sin Shift no debe dejar usar el bloque ni el objeto de la mano por accidente: no hace falta
     *  bloquear nada más, pero sí mantener el peso (lentitud) mientras dure. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 0 || carried(sp).isNone()) return;
        int level = com.hmc.zenkai.config.ServerConfig.PERMADEATH_CARRY_SLOWNESS.get();
        if (level > 0) sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, level - 1, true, false, false));
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer sp && !carried(sp).isNone()) place(sp, sp.getX(), sp.getY(), sp.getZ(), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && !carried(sp).isNone()) place(sp, sp.getX(), sp.getY(), sp.getZ(), true);
    }
}
