package com.hmc.zenkai.client;

import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.core.network.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Pose;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientZenkaiPalTick {

    /** Estado de animación por jugador (antes era global -> por eso solo animaba al local). */
    private static final class AnimState {
        boolean lastHeld = false;
        int chainTicks = 0;
        boolean flyPlaying = false;
        ZenkaiPalAnimations.FlyDir flyDir = null;
        int flyBoostState = 0; // 0 = crucero, 1 = intermedia, 2 = boost (loop)
        int flyBoostTicks = 0; // cuenta atrás de la intermedia antes de pasar al loop
    }

    private static final Map<UUID, AnimState> STATES = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        KeyBindings.handleClientTick();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Recorremos TODOS los jugadores visibles para reproducir la animación de cada uno
        // según SU estado de transformación (que ya llega sincronizado por SyncPlayerFormPacket).
        for (AbstractClientPlayer p : mc.level.players()) {
            tickPlayer(mc, p);
        }

        // Limpieza: descartar estados de jugadores que ya no están en el nivel.
        STATES.keySet().removeIf(uuid -> mc.level.getPlayerByUUID(uuid) == null);
    }

    private static void tickPlayer(Minecraft mc, AbstractClientPlayer p) {
        var form  = p.getData(DataAttachments.PLAYER_FORM.get());
        var stats = p.getData(DataAttachments.PLAYER_STATS.get());

        // Derribado: el jugador local recalcula su propia pose cada tick, así que si no la forzamos
        // aquí no se vería acostado a sí mismo (F5). A los demás ya les llega por DATA_POSE del server.
        if (stats.flags().isDowned()) {
            if (p == mc.player) {
                p.setPose(Pose.SWIMMING);
                p.setSwimming(true);
                mc.player.input.forwardImpulse = 0;
                mc.player.input.leftImpulse = 0;
                mc.player.input.jumping = false;
                mc.player.input.shiftKeyDown = false;
                mc.player.setSprinting(false);
            }
            return; // nada de animación de transformación mientras está derribado
        }

        AnimState st = STATES.computeIfAbsent(p.getUUID(), k -> new AnimState());

        // ── Animación de vuelo direccional + aceleración (jugador local) ──
        // Mientras vuela: animación según dirección. Al pulsar Control: intermedia (una vez) -> boost (loop).
        // Al soltar Control: vuelve a crucero. Al cambiar de dirección: crucero/boost de la nueva dirección.
        // Solo local por ahora (input sin sincronizar).
        if (p == mc.player) {
            boolean flying = !p.isCreative() && !p.isSpectator()
                    && stats.isFlyEnabled()
                    && p.getAbilities().flying;
            if (flying) {
                handleFlyAnim(mc, p, st);
            } else if (st.flyPlaying) {
                st.flyPlaying = false;
                st.flyDir = null;
                st.flyBoostState = 0;
                st.flyBoostTicks = 0;
                ZenkaiPalAnimations.stopFly(p);
            }
        }

        boolean heldNow = form.isTransformHeld();
        boolean canTransform = PlayerFormAttachment.canTransformFrom(stats.getRace(), form.getFormId());

        if (!canTransform) {
            if (st.lastHeld) {
                st.lastHeld = false;
                st.chainTicks = 0;
                ZenkaiPalAnimations.controller(p).stopTriggeredAnimation();
            }
            return;
        }

        // El bloqueo de input SOLO aplica al jugador local (los demás no tienen input local aquí).
        if (heldNow && p == mc.player) {
            mc.player.input.forwardImpulse = 0;
            mc.player.input.leftImpulse = 0;
            mc.player.input.jumping = false;
            mc.player.input.shiftKeyDown = false;
            mc.player.setSprinting(false);
        }

        if (heldNow && !st.lastHeld) {
            st.lastHeld = true;
            ZenkaiPalAnimations.playTransformStart(p);
            st.chainTicks = 10; // 0.5s
        }

        if (!heldNow && st.lastHeld) {
            st.lastHeld = false;
            st.chainTicks = 0;
            ZenkaiPalAnimations.controller(p).stopTriggeredAnimation();
        }

        if (heldNow && st.chainTicks > 0) {
            st.chainTicks--;
            if (st.chainTicks == 0) {
                ZenkaiPalAnimations.playTransformLoop(p);
            }
        }
    }

    /** Duración (ticks) de la animación intermedia antes de pasar al loop de boost. */
    // Estados de la animación de vuelo.
    private static final int CRUISE_START = 0, CRUISE_LOOP = 1, BOOST_START = 2, BOOST_LOOP = 3;
    // Duración (ticks) de cada INTERMEDIA antes de su loop. Ajústalas a la longitud real de tus animaciones.
    private static final int FLY_CRUISE_START_TICKS = 6; // ~0.3 s
    private static final int FLY_BOOST_START_TICKS  = 6; // ~0.3 s

    /** Máquina de estados de la animación de vuelo del jugador local (crucero y boost, cada uno start->loop). */
    private static void handleFlyAnim(Minecraft mc, AbstractClientPlayer p, AnimState st) {
        assert mc.player != null;
        ZenkaiPalAnimations.FlyDir dir = computeFlyDir(mc.player);
        boolean boosting = mc.options.keySprint.isDown();

        // Arranque de vuelo -> intermedia de crucero
        if (!st.flyPlaying) {
            st.flyPlaying = true;
            st.flyDir = dir;
            enterCruiseStart(p, st, dir);
            return;
        }

        boolean inBoost = (st.flyBoostState == BOOST_START || st.flyBoostState == BOOST_LOOP);

        // Cambio de dirección: loop de la nueva dirección en el modo actual (sin repetir intermedias).
        if (dir != st.flyDir) {
            st.flyDir = dir;
            if (inBoost) {
                ZenkaiPalAnimations.playFly(p, dir.boost);
                st.flyBoostState = BOOST_LOOP;
            } else {
                ZenkaiPalAnimations.playFly(p, dir.cruiseStart);
                st.flyBoostState = CRUISE_LOOP;
            }
            return;
        }

        // Misma dirección: transiciones de Control + avance de las intermedias.
        if (boosting && !inBoost) {
            // Entrar a boost -> intermedia de boost
            ZenkaiPalAnimations.playFly(p, dir.boostStart);
            st.flyBoostState = BOOST_START;
            st.flyBoostTicks = FLY_BOOST_START_TICKS;
        } else if (!boosting && inBoost) {
            // Salir de boost -> intermedia de crucero
            enterCruiseStart(p, st, dir);
        } else if (st.flyBoostState == CRUISE_START) {
            if (--st.flyBoostTicks <= 0) {
                ZenkaiPalAnimations.playFly(p, dir.cruiseStart);
                st.flyBoostState = CRUISE_LOOP;
            }
        } else if (st.flyBoostState == BOOST_START) {
            if (--st.flyBoostTicks <= 0) {
                ZenkaiPalAnimations.playFly(p, dir.boost);
                st.flyBoostState = BOOST_LOOP;
            }
        }
    }

    /** Dispara la intermedia de crucero y arma el timer hacia el loop de crucero. */
    private static void enterCruiseStart(AbstractClientPlayer p, AnimState st, ZenkaiPalAnimations.FlyDir dir) {
        ZenkaiPalAnimations.playFly(p, dir.cruise);
        st.flyBoostState = CRUISE_START;
        st.flyBoostTicks = FLY_CRUISE_START_TICKS;
    }

    /** Dirección de vuelo según el input del jugador local (adelante/atrás/lados/vertical + diagonales). */
    private static ZenkaiPalAnimations.FlyDir computeFlyDir(net.minecraft.client.player.LocalPlayer p) {
        var in = p.input;
        boolean f = in.forwardImpulse >  0.1f, b = in.forwardImpulse < -0.1f;
        boolean l = in.leftImpulse    >  0.1f, r = in.leftImpulse    < -0.1f;
        if (f && l) return ZenkaiPalAnimations.FlyDir.FORWARD_LEFT;
        if (f && r) return ZenkaiPalAnimations.FlyDir.FORWARD_RIGHT;
        if (b && l) return ZenkaiPalAnimations.FlyDir.BACK_LEFT;
        if (b && r) return ZenkaiPalAnimations.FlyDir.BACK_RIGHT;
        if (f) return ZenkaiPalAnimations.FlyDir.FORWARD;
        if (b) return ZenkaiPalAnimations.FlyDir.BACK;
        if (l) return ZenkaiPalAnimations.FlyDir.LEFT;
        if (r) return ZenkaiPalAnimations.FlyDir.RIGHT;
        if (in.jumping)      return ZenkaiPalAnimations.FlyDir.UP;
        if (in.shiftKeyDown) return ZenkaiPalAnimations.FlyDir.DOWN;
        return ZenkaiPalAnimations.FlyDir.IDLE;
    }
}