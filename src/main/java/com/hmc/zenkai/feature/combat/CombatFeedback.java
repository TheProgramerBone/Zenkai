package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import com.hmc.zenkai.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Dos cosas que cuelgan del mismo sitio: el sonido del puñetazo y la entrada automática en
 * combate.
energy_generator * POR QUÉ NO ESTÁ DENTRO DE CombatZenkaiHooks: ahí vive el cálculo del daño, que es delicado
 * y está lleno de returns tempranos por motivos que no tienen nada que ver con el feedback
 * (i-frames, /kill, gamerules). Colgar el sonido de una de esas ramas lo haría desaparecer
 * en casos difíciles de razonar. Aquí son dos listeners independientes que no pueden romper
 * el pipeline de daño porque no lo tocan.
energy_generator * EL SONIDO VA EN AttackEntityEvent y no en el evento de daño: quiero el sonido del GOLPE,
 * no el de cualquier daño. Con LivingDamageEvent sonaría un puñetazo al caerse de una
 * escalera o al quemarse. Suena aunque el golpe acabe mitigado a cero, igual que en vanilla:
 * el jugador oye que ha conectado, y si no ha hecho daño ya lo dice la barra.
energy_generator * LA VARIACIÓN NO SE SORTEA AQUÍ. zenkai:hit declara cuatro archivos y zenkai:block tres en
 * sounds.json, y el motor elige uno por reproducción. Rotarlo en Java significaría mandar
 * varios SoundEvents distintos y que cada uno pudiera desincronizarse de los demás en volumen
 * o en subtítulo.
 * HIT vs BLOCK se decide leyendo KiCombatServer.isBlocking(target) directamente, no
 * e.isCanceled(): el evento también se cancela por motivos ajenos al bloqueo (barrera de
 * Shenlong en ModEvents.onAttackEntity) y el orden entre listeners de la misma prioridad
 * (este y CombatZenkaiHooks.onAttackWhileBlocking) no está garantizado.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class CombatFeedback {
    private CombatFeedback() {}

    /** Rango de tono. Sin esto los tres archivos se vuelven reconocibles en cuanto encadenas
     *  golpes, y una ráfaga suena a bucle de dos segundos. */
    private static final float PITCH_MIN = 0.92f;
    private static final float PITCH_MAX = 1.08f;

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;

        // El defensor bloqueando manda sobre cualquier otra razón de cancelación: si está
        // bloqueando, suena BLOCK aunque el evento esté cancelado (lo está, precisamente por
        // eso). Si está cancelado por OTRO motivo (barrera de Shenlong, etc.) no suena nada,
        // igual que antes de este cambio.
        boolean blocked = e.getTarget() instanceof ServerPlayer defSp
                && KiCombatServer.isBlocking(defSp);
        if (!blocked && e.isCanceled()) return;

        float pitch = PITCH_MIN + p.getRandom().nextFloat() * (PITCH_MAX - PITCH_MIN);
        SoundEvent sound = blocked ? ModSounds.BLOCK.get() : ModSounds.HIT.get();
        // null como jugador excluido: lo oye el mundo, incluido quien pega. Pasar sp
        // aquí es el error clásico — el atacante se queda sin el sonido de su propio golpe.
        p.level().playSound(null, e.getTarget().getX(), e.getTarget().getY(), e.getTarget().getZ(),
                sound, SoundSource.PLAYERS, 1.0f, pitch);
    }

    /**
     * Entrada en combate. En LOWEST para correr después de que CombatZenkaiHooks decida qué
     * pasa con el daño: si el golpe se anuló entero (i-frames, /kill), el evento ni llega
     * aquí con algo que contar.
energy_generator     * Cuenta el daño de cualquier origen y no solo el melee: un ki blast a distancia es
     * combate, y un jugador que huye de un esqueleto también lo es. Lo que NO cuenta es el
     * daño ambiental sin autor, porque quemarse en la lava no debería impedirte curarte.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre e) {
        if (e.getEntity().level().isClientSide()) return;

        LivingEntity victim = e.getEntity();
        var source = e.getSource().getEntity();

        Player attackerPlayer = (source instanceof Player pl) ? pl : null;
        Player victimPlayer   = (victim instanceof Player pl) ? pl : null;

        // Sin autor no hay combate: caída, ahogo, cactus.
        if (source == null && victimPlayer != null) return;

        InCombatState.markBoth(attackerPlayer, victimPlayer);
    }

    /** Reaparecer no es seguir peleando. Sin esto, morir en combate te deja el icono puesto
     *  y la curación penalizada durante los primeros segundos de vida nueva. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            InCombatState.clear(sp);
        }
    }

    /** Corte inmediato en el instante de morir, sin esperar al respawn (ver mismo razonamiento
     *  en CombatModeServerState.onDeath). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            InCombatState.clear(sp);
        }
    }

    /** Red de seguridad al entrar: inCombatUntil es un gameTime absoluto, así que en teoría
     *  expira solo, pero si el jugador reconecta muy rápido dentro de la ventana no debería
     *  aparecer "en combate" antes de haber hecho nada esta sesión. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            InCombatState.clear(sp);
        }
    }
}