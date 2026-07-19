package com.hmc.zenkai.client;

import com.hmc.zenkai.client.ZenkaiPalAnimations.FlyDir;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.core.network.feature.ki.FlyBoostPacket;
import com.hmc.zenkai.core.network.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Pose;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientZenkaiPalTick {

    /** Estado de animación por jugador (antes era global -> por eso solo animaba al local). */
    private static final class AnimState {
        boolean lastHeld = false;
        int chainTicks = 0;
        boolean flyPlaying = false;
        FlyDir flyDir = null;
        int flyBoostState = 0; // ver constantes CRUISE_START/CRUISE_LOOP/BOOST_START/BOOST_LOOP
        int flyBoostTicks = 0; // cuenta atrás de la intermedia antes de pasar al loop
        boolean blockPlaying = false;
        boolean combatPlaying = false;
        int combatStyle = -1;      // ordinal del Style con el que se posó
        int combatStartTicks = 0;  // cuenta atrás del start antes del loop
    }

    private static final Map<UUID, AnimState> STATES = new HashMap<>();

    /** Último valor del bit de boost enviado al servidor (edge-trigger, solo jugador local). */
    private static boolean lastFlyBoostSent = false;

    /**
     * Aplica el estado de boost del jugador LOCAL:
     *  - Avisa al servidor (bit autoritativo) solo cuando cambia.
     *  - Fija el flag local para que BoostSizeHandler encoja hitbox + baje la cámara sin esperar
     *    el round-trip.
     *  - Llama refreshDimensions() SOLO en el cambio (fuera->dentro / dentro->fuera) para recalcular
     *    la caja y la altura de ojos (dispara EntityEvent.Size).
     * NO toca la pose -> no frena el vuelo ni inclina el modelo (eso lo hace la animación PAL).
     */
    private static void applyLocalBoost(AbstractClientPlayer p, boolean boosting) {
        if (boosting != lastFlyBoostSent) {
            lastFlyBoostSent = boosting;
            PacketDistributor.sendToServer(new FlyBoostPacket(boosting));
        }
        var fl = p.getData(DataAttachments.PLAYER_STATS.get()).flags();
        fl.setFlyBoosting(boosting);
        if (boosting != fl.isBoostSizeApplied()) {
            fl.setBoostSizeApplied(boosting);
            p.refreshDimensions();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        KeyBindings.handleClientTick();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        for (AbstractClientPlayer p : mc.level.players()) {
            tickPlayer(mc, p);
        }

        STATES.keySet().removeIf(uuid -> mc.level.getPlayerByUUID(uuid) == null);
        ClientFlyAnimState.prune(mc.level);
    }

    private static void tickPlayer(Minecraft mc, AbstractClientPlayer p) {
        var form  = p.getData(DataAttachments.PLAYER_FORM.get());
        var stats = p.getData(DataAttachments.PLAYER_STATS.get());

        // Derribado: forzamos la pose acostada del jugador local (los demás la reciben por DATA_POSE).
        AnimState st = STATES.computeIfAbsent(p.getUUID(), k -> new AnimState());

        // Derribado: forzamos la pose acostada del jugador local (los demás la reciben por DATA_POSE).
        if (stats.flags().isDowned()) {
            tickCombatIdle(p, st, -1); // corta la pose ofensiva si estaba activa
            if (p == mc.player) {
                applyLocalBoost(p, false); // por si se derriba en pleno boost (limpia hitbox/cámara)
                p.setPose(Pose.SWIMMING);
                p.setSwimming(true);
                mc.player.input.forwardImpulse = 0;
                mc.player.input.leftImpulse = 0;
                mc.player.input.jumping = false;
                mc.player.input.shiftKeyDown = false;
                mc.player.setSprinting(false);
            }
            return;
        }

        // ── Animación de vuelo direccional + aceleración ──
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
                applyLocalBoost(p, false); // deja de volar -> hitbox/cámara vuelven a normal
                ClientFlyAnimState.sendIfChanged(false, null, false); // avisa a los demás
                ZenkaiPalAnimations.stopFly(p);
            }
        } else {
            // Jugadores REMOTOS: mismo estado-máquina, alimentado por el estado sincronizado.
            ClientFlyAnimState.Remote rs = ClientFlyAnimState.get(p.getId());
            if (rs != null && rs.flying()) {
                driveFly(p, st, rs.dir(), rs.boosting());
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
            // Sin transformación disponible (p. ej. humano/namekiano en base): solo limpiar
            // la anim de transformación. NO retornar: antes este return se tragaba las
            // animaciones de pose de combate y defensa de esas razas.
            if (st.lastHeld) {
                st.lastHeld = false;
                st.chainTicks = 0;
                ZenkaiPalAnimations.controller(p).stopTriggeredAnimation();
            }
        } else {
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

        // ── Pose ofensiva del modo combate (SHIFT + quieto en tierra; start -> loop, por estilo) ──
        // Si se mueve con shift pulsado, walkAnimation.speed() sube -> se cancela sola.
        int combatStyleOrd = -1;
        boolean sneaking = (p == mc.player) ? p.isShiftKeyDown() : p.isCrouching();
        boolean combatStill = sneaking
                && p.onGround()
                && p.walkAnimation.speed() < 0.05f
                && !p.isSwimming();
        if (combatStill) {
            if (p == mc.player) {
                if (CombatModeClientState.isActive() && stats.isStyleChosen()) {
                    combatStyleOrd = stats.getStyle().ordinal();
                }
            } else {
                CombatModeClientState.Remote cr = CombatModeClientState.remote(p.getId());
                if (cr != null) combatStyleOrd = cr.styleOrdinal();
            }
        }
        tickCombatIdle(p, st, combatStyleOrd);

        // ── Animación de defensa (local: estado propio instantáneo; remotos: sync) ──
        boolean blockingNow = (p == mc.player)
                ? CombatModeClientState.isBlockingLocal()
                : CombatModeClientState.isBlockingRemote(p.getId());
        if (blockingNow && !st.blockPlaying) {
            st.blockPlaying = true;
            ZenkaiPalAnimations.playBlock(p);
        } else if (!blockingNow && st.blockPlaying) {
            st.blockPlaying = false;
            ZenkaiPalAnimations.stopBlock(p);
        }
    }

    // ── Estados de la animación de vuelo ─────────────────────────────────────
    private static final int CRUISE_START = 0, CRUISE_LOOP = 1, BOOST_START = 2, BOOST_LOOP = 3;
    // Duración (ticks) de cada START antes de su loop. Ajústalas a la longitud real de tus animaciones.
    private static final int FLY_CRUISE_START_TICKS = 6; // ~0.3 s
    private static final int FLY_BOOST_START_TICKS  = 6;

    /** Estado de vuelo para otros sistemas (p.ej. la inclinación del aura).
     *  dir = null cuando NO está en animación de vuelo. Funciona para el jugador
     *  local y los remotos (ambos alimentan la misma máquina de estados). */
    public record FlyPose(ZenkaiPalAnimations.FlyDir dir, boolean boosting) {}

    public static FlyPose flyPoseOf(java.util.UUID playerId) {
        AnimState st = STATES.get(playerId);
        if (st == null || !st.flyPlaying) return new FlyPose(null, false);
        boolean boosting = st.flyBoostState == BOOST_START || st.flyBoostState == BOOST_LOOP;
        return new FlyPose(st.flyDir, boosting);
    }// ~0.3 s

    /**
     * Máquina de estados de la animación de vuelo del jugador local.
     * Resolución (dirección, boost) según tus inputs:
     *   Shift+Control+W -> DOWN boost (down_boost)   ·  Espacio+Control+W -> UP boost (up_boost)
     *   Shift            -> DOWN (cualquier dir)      ·  W+Control -> FORWARD boost
     *   W -> FORWARD (cubre A/D)  ·  S -> BACK  ·  A -> LEFT  ·  D -> RIGHT  ·  Espacio -> UP  ·  nada -> IDLE
     * Todas las direcciones pasan por su START -> LOOP (idle no tiene start). En cambio de dirección
     * TAMBIÉN se respeta el start (era lo que se saltaba). El timer garantiza el paso start->loop.
     */
    private static void handleFlyAnim(Minecraft mc, AbstractClientPlayer p, AnimState st) {
        assert mc.player != null;
        var in = mc.player.input;
        boolean f = in.forwardImpulse >  0.1f, b = in.forwardImpulse < -0.1f;
        boolean l = in.leftImpulse    >  0.1f, r = in.leftImpulse    < -0.1f;
        boolean up = in.jumping, down = in.shiftKeyDown;
        boolean ctrl = mc.options.keySprint.isDown();

        FlyDir dir;
        boolean boosting;
        if (down && ctrl && f)   { dir = FlyDir.DOWN;    boosting = true;  } // down_boost: shift+control+W
        else if (up && ctrl && f){ dir = FlyDir.UP;      boosting = true;  } // up_boost: espacio+control+W
        else if (down)           { dir = FlyDir.DOWN;    boosting = false; } // shift solo (cualquier dir)
        else if (f && ctrl)      { dir = FlyDir.FORWARD; boosting = true;  } // forward_boost: W+control
        else if (f)              { dir = FlyDir.FORWARD; boosting = false; } // W (cubre A/D)
        else if (b)              { dir = FlyDir.BACK;    boosting = false; }
        else if (l)              { dir = FlyDir.LEFT;    boosting = false; }
        else if (r)              { dir = FlyDir.RIGHT;   boosting = false; }
        else if (up)             { dir = FlyDir.UP;      boosting = false; } // espacio solo
        else                     { dir = FlyDir.IDLE;    boosting = false; }

        // Hitbox + cámara "acostado" durante el boost. NO tocamos la pose (eso frenaba el vuelo):
        // el tamaño/altura-de-ojos se ajustan por EntityEvent.Size (BoostSizeHandler) según este flag.
        applyLocalBoost(p, boosting);

        // Publica el estado propio a los demás (solo si cambió).
        ClientFlyAnimState.sendIfChanged(true, dir, boosting);

        driveFly(p, st, dir, boosting);
    }

    /**
     * Estado-máquina de la animación de vuelo, COMÚN a local y remotos: entrada, cambio de
     * dirección, transiciones de boost y avance de los starts hacia sus loops.
     */
    private static void driveFly(AbstractClientPlayer p, AnimState st, FlyDir dir, boolean boosting) {
        boolean inBoost = (st.flyBoostState == BOOST_START || st.flyBoostState == BOOST_LOOP);

        // Arranque de vuelo
        if (!st.flyPlaying) {
            st.flyPlaying = true;
            st.flyDir = dir;
            enterDir(p, st, dir, boosting);
            return;
        }

        // Cambio de dirección: entra a la nueva dirección RESPETANDO su start (idle va directo al loop).
        if (dir != st.flyDir) {
            st.flyDir = dir;
            enterDir(p, st, dir, boosting);
            return;
        }

        // Misma dirección: transiciones de Control + avance de los starts.
        if (boosting && !inBoost) {
            enterBoostStart(p, st, dir);        // entrar a boost -> boost_start -> boost
        } else if (!boosting && inBoost) {
            enterDir(p, st, dir, false);        // salir de boost -> <dir>_start -> <dir>
        } else if (st.flyBoostState == CRUISE_START) {
            if (--st.flyBoostTicks <= 0) {
                ZenkaiPalAnimations.playFly(p, dir.cruise);
                st.flyBoostState = CRUISE_LOOP;
            }
        } else if (st.flyBoostState == BOOST_START) {
            if (--st.flyBoostTicks <= 0) {
                ZenkaiPalAnimations.playFly(p, dir.boost);
                st.flyBoostState = BOOST_LOOP;
            }
        }
    }

    /** Entra a una dirección: boost_start (si boost), o <dir>_start->loop; idle va directo (no tiene start). */
    private static void enterDir(AbstractClientPlayer p, AnimState st, FlyDir dir, boolean boosting) {
        if (boosting) {
            enterBoostStart(p, st, dir);
        } else if (dir == FlyDir.IDLE) {
            ZenkaiPalAnimations.playFly(p, dir.cruise); // idle no tiene start
            st.flyBoostState = CRUISE_LOOP;
        } else {
            enterCruiseStart(p, st, dir);
        }
    }

    /** Dispara el <dir>_boost_start y arma el timer hacia el loop de boost. */
    private static void enterBoostStart(AbstractClientPlayer p, AnimState st, FlyDir dir) {
        ZenkaiPalAnimations.playFly(p, dir.boostStart);
        st.flyBoostState = BOOST_START;
        st.flyBoostTicks = FLY_BOOST_START_TICKS;
    }

    /** Dispara el <dir>_start y arma el timer hacia el loop de crucero. */
    private static void enterCruiseStart(AbstractClientPlayer p, AnimState st, FlyDir dir) {
        ZenkaiPalAnimations.playFly(p, dir.cruiseStart);
        st.flyBoostState = CRUISE_START;
        st.flyBoostTicks = FLY_CRUISE_START_TICKS;
    }

    // ── Pose ofensiva del modo combate ───────────────────────────────────────
    /** Duración (ticks) del start antes del loop. Ajústala a tus animaciones. */
    private static final int COMBAT_START_TICKS = 6; // ~0.3 s

    /** styleOrd < 0 = sin pose (fuera de modo combate / derribado / sin estilo). */
    private static void tickCombatIdle(AbstractClientPlayer p, AnimState st, int styleOrd) {
        if (styleOrd < 0) {
            if (st.combatPlaying) {
                st.combatPlaying = false;
                st.combatStyle = -1;
                st.combatStartTicks = 0;
                ZenkaiPalAnimations.stopCombatIdle(p);
            }
            return;
        }
        if (!st.combatPlaying || st.combatStyle != styleOrd) {
            st.combatPlaying = true;
            st.combatStyle = styleOrd;
            st.combatStartTicks = COMBAT_START_TICKS;
            ZenkaiPalAnimations.playCombatIdleStart(p, styleOrd);
            return;
        }
        if (st.combatStartTicks > 0 && --st.combatStartTicks == 0) {
            ZenkaiPalAnimations.playCombatIdleLoop(p, styleOrd);
        }
    }
}