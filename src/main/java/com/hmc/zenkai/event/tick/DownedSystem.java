package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.event.CombatZenkaiHooks; // ⚠ ajustar si CombatZenkaiHooks quedó en feature.combat
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.combat.DeathCauseTracker;
import com.hmc.zenkai.feature.combat.DownedDeathGuard;
import com.hmc.zenkai.feature.player.OtherworldManager;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Derribado y body a 0: los dos cortes de tick más agresivos. */
public final class DownedSystem {
    private DownedSystem() {}

    /**
     * Derribado: acostado e inmóvil 5 s. Si lo curan (body > 0) se levanta; si expira, muere de
     * verdad y LivingDeathEvent lo manda al otro mundo — salvo que YA esté en el otro mundo, en
     * cuyo caso expirar solo lo cura y reancla ahí (ver OtherworldManager.keepInOtherworld): no
     * hay una segunda muerte que dar.
     * @return true si hay que cortar el tick.
     */
    public static boolean handleDowned(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        if (!att.flags().isDowned()) return false;

        MovementLocks.downed(p, true);
        // Pose horizontal, se sincroniza vía DATA_POSE; el propio jugador la fuerza en el
        // cliente (ClientZenkaiPalTick) porque LocalPlayer la recalcula cada tick.
        p.setPose(Pose.SWIMMING);
        p.setSwimming(true);
        // Los mobs sueltan al derribado. Se repasa cada 10 ticks y no una sola vez al caer,
        // porque también hay que soltar a los que se acerquen durante los 5 segundos.
        if (p instanceof ServerPlayer downedSp && p.tickCount % 10 == 0) {
            releaseAttackers(downedSp);
        }

        if (att.getBody() > 0) {
            // Se levanta con el body que dejó la curación: senzu 100%, revive de aliado 20%.
            clearDowned(p, att);
            PlayerLifeCycle.syncIfServer(p);
        } else if (p.level().getGameTime() >= att.flags().getDownedUntil()) {
            // En el Otro Mundo no hay una segunda muerte que dar: ya está muerto. El derribado
            // termina en el mismo reseteo (cura + reancla en la entrada) que OtherworldManager
            // ya aplica a cualquier golpe que lo tumbe allí — antes esto se saltaba entero y el
            // combate/entrenamiento en el Otherworld no tenía ninguna consecuencia visible; con
            // el derribado normal de por medio, al menos hay pose, ventana y posibilidad de que
            // te curen antes de perder. Sin gastar totem: no hay muerte real de la que salvarse.
            if (p instanceof ServerPlayer sp && att.isInOtherworld()) {
                clearDowned(p, att);
                OtherworldManager.keepInOtherworld(sp);
                return true;
            }
            // Único punto de muerte REAL del mod (el daño normal nunca llega a matar: se
            // anula en CombatZenkaiHooks.applyToZenkaiVictim). Es también el único sitio donde
            // vanilla revisaría un Totem of Undying si la muerte pasara por hurt()/
            // actuallyHurt() — como no pasa, el chequeo se replica a mano aquí.
            if (p instanceof ServerPlayer sp && consumeTotem(sp)) {
                att.setBody(CombatZenkaiHooks.downedReviveBody(att));
                clearDowned(p, att);
                ZenkaiTriggers.MILESTONE.get().trigger(sp, ZenkaiTriggers.Kinds.REVIVED);
                PlayerLifeCycle.syncIfServer(p);
                return true;
            }
            clearDowned(p, att);
            if (p instanceof ServerPlayer sp) {
                // health=0 ANTES de die(): sin esto la muerte "no cuaja" (el cliente nunca ve
                // vida 0) y el respawn sincroniza -> jugador vivo con body 0 y sin viaje.
                // allowRealDeath: esta muerte es intencional, DownedDeathGuard no debe
                // convertirla otra vez en derribado (sería un bucle infinito).
                DownedDeathGuard.allowRealDeath(sp);
                sp.setHealth(0.0F);
                // Causa real del golpe que lo tumbó (ki, técnica física...), si CombatZenkaiHooks
                // llegó a guardarla; sin eso (ahogamiento, /kill fuera de este camino, etc.) cae
                // al genérico de siempre. Ver DeathCauseTracker.
                DamageSource cause = DeathCauseTracker.take(sp.getUUID());
                sp.die(cause != null ? cause : sp.damageSources().generic());
            }
        }
        return true;
    }

    /** Busca un Totem of Undying en mano principal u offhand (mismo orden que vanilla) y lo
     *  consume, replicando la animación (evento de entidad 35, la misma que usa
     *  LivingEntity.checkTotemDeathProtection) y el sonido de vanilla. No podemos invocar ese
     *  función protegida desde aquí, así que se reconstruye el efecto a mano. */
    private static boolean consumeTotem(ServerPlayer sp) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = sp.getItemInHand(hand);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                stack.shrink(1);
                sp.serverLevel().broadcastEntityEvent(sp, (byte) 35); // TOTEM_USE_ANIMATION
                sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                return true;
            }
        }
        return false;
    }

    /** Body a 0: derriba, o lo mantiene en el otro mundo. @return true si hay que cortar. */
    public static boolean handleBodyDepleted(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        if (att.getBody() > 0 || p.isDeadOrDying() || !(p instanceof ServerPlayer sp)) return false;

        if (att.isInOtherworld()) {
            OtherworldManager.keepInOtherworld(sp);
        } else {
            att.flags().setDowned(true);
            att.flags().setDownedUntil(p.level().getGameTime() + CombatZenkaiHooks.DOWNED_TICKS);
            PlayerLifeCycle.syncIfServer(p);
        }
        return true;
    }

    /** Sale del estado derribado: limpio flag, libera el lock y restaura la pose. */
    private static void clearDowned(Player p, PlayerStatsAttachment att) {
        att.flags().setDowned(false);
        att.flags().setDownedUntil(0L);
        MovementLocks.downed(p, false);
        p.setSwimming(false);
        p.setPose(Pose.STANDING); // updatePlayerPose recalculará la correcta al siguiente tick
    }

    /** Quita a este jugador de la mira de mob que lo tuviera fijado. */
    private static void releaseAttackers(ServerPlayer sp) {
        for (Mob mob : sp.serverLevel().getEntitiesOfClass(
                Mob.class, sp.getBoundingBox().inflate(48.0), m -> m.getTarget() == sp)) {
            mob.setTarget(null);
        }
    }
}