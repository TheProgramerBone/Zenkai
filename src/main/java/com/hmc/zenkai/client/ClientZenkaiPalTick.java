package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.action.ActionStateClient;
import com.hmc.zenkai.event.ZenkaiPalAnimations;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.feature.action.ActionPhase;
import com.hmc.zenkai.feature.action.ActionType;
import com.hmc.zenkai.feature.ki.FlyBoostPacket;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
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
        // Vuelo: cuatro animaciones, un estado y un temporizador. Ya no hay dirección.
        int flyState = FLY_OFF;
        int flyTimer = 0;          // cuenta atrás de start/stop hacia su siguiente estado
        boolean blockPlaying = false;
        boolean combatPlaying = false;
        int combatStyle = -1;      // ordinal del Style con el que se posó
        int combatStartTicks = 0;  // cuenta atrás del start antes del loop
        // Ki: se reacciona a CAMBIOS de fase, no se re-dispara cada tick.
        ActionPhase kiPhase = null;
        int kiSet = -1;
        // Subir ki: start -> loop, y solo quieto.
        boolean chargeKiPlaying = false;
        int chargeKiStartTicks = 0;
        // Físicas: solo remotos. El jugador local anima por predicción, no por sync.
        long physSeenStart = -1L;
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
        var fl = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).flags();
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
        ZenkaiPalAnimations.applyFirstPersonPolicy(mc.player);
        for (AbstractClientPlayer p : mc.level.players()) {
            tickPlayer(mc, p);
        }

        STATES.keySet().removeIf(uuid -> mc.level.getPlayerByUUID(uuid) == null);
        com.hmc.zenkai.client.fly.FlightController.prune(mc.level);
        ActionStateClient.prune(mc.level);
        ClientFlyAnimState.prune(mc.level);
    }

    private static void tickPlayer(Minecraft mc, AbstractClientPlayer p) {
        var form  = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        PlayerStatsAttachment stats = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());

        AnimState st = STATES.computeIfAbsent(p.getUUID(), k -> new AnimState());

        // Derribado: forzamos la pose acostada del jugador local (los demás la reciben por DATA_POSE).
        if (stats.flags().isDowned()) {
            tickCombatIdle(p, st, -1);       // corta la pose ofensiva si estaba activa
            tickChargeKi(p, st, false);      // y la de subir ki
            com.hmc.zenkai.client.fly.FlightController.tick(p, false, false);
            driveFly(p, st, false, false);   // y aterriza la animación de vuelo
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

        // ── Animación de vuelo ──
        // Ya no se resuelve dirección: la postura es una sola y la orientación la pondrá el
        // FlightController. Boost = Ctrl + adelante, que es lo único que sigue siendo binario.
        if (p == mc.player) {
            boolean flying = !p.isCreative() && !p.isSpectator()
                    && stats.isFlyEnabled()
                    && p.getAbilities().flying;
            boolean boosting = flying
                    && mc.options.keySprint.isDown()
                    && mc.player.input.forwardImpulse > 0.1f;
            applyLocalBoost(p, boosting);
            ClientFlyAnimState.sendIfChanged(flying, boosting);
            com.hmc.zenkai.client.fly.FlightController.tick(p, flying, boosting);
            driveFly(p, st, flying, boosting);
        } else {
            ClientFlyAnimState.Remote rs = ClientFlyAnimState.get(p.getId());
            boolean rFlying = rs != null && rs.flying();
            boolean rBoosting = rs != null && rs.boosting();
            com.hmc.zenkai.client.fly.FlightController.tick(p, rFlying, rBoosting);
            driveFly(p, st, rFlying, rBoosting);
        }

        boolean heldNow = form.isTransformHeld();
        boolean canTransform = PlayerFormAttachment.canTransformFrom(p, stats.getRace(), form.getFormId());

        if (!canTransform) {
            // Sin transformación disponible (p. ej. humano/namekiano en base): solo limpiar
            // la anim de transformación. NO retornar: antes este return se tragaba las
            // animaciones de pose de combate y defensa de esas razas.
            if (st.lastHeld) {
                st.lastHeld = false;
                st.chainTicks = 0;
                ZenkaiPalAnimations.stopTransform(p);
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
                ZenkaiPalAnimations.stopTransform(p);
            }

            if (heldNow && st.chainTicks > 0) {
                st.chainTicks--;
                if (st.chainTicks == 0) {
                    ZenkaiPalAnimations.playTransformLoop(p);
                }
            }
        }

        // ── Quieto en tierra: condición compartida por la pose ofensiva y por subir ki ──
        boolean still = p.onGround() && p.walkAnimation.speed() < 0.05f && !p.isSwimming();

        // ── Subir ki (tecla C) ──
        // Cargar en movimiento SÍ está permitido; lo que no se reproduce es la pose, porque
        // una pose sostenida sobre un jugador andando da resultados raros.
        // Va ANTES de la pose ofensiva y tiene prioridad: comparten COMBAT_LAYER, así que si
        // se evaluaran al revés se pisarían cuando estás agachado en modo combate pulsando C.
        boolean chargeKi = stats.isChargingKi() && still;
        tickChargeKi(p, st, chargeKi);

        // ── Pose ofensiva del modo combate (SHIFT + quieto en tierra; start -> loop, por estilo) ──
        // Si se mueve con shift pulsado, walkAnimation.speed() sube -> se cancela sola.
        int combatStyleOrd = -1;
        boolean sneaking = (p == mc.player) ? p.isShiftKeyDown() : p.isCrouching();
        if (still && sneaking && !chargeKi) {
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

        // ── Técnicas de ki: local y remotos por igual, desde el estado sincronizado ──
        tickKiAnim(p, st);
        if (p != mc.player) tickPhysAnim(p, st);

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

    // ── Vuelo ────────────────────────────────────────────────────────────────
    /**
     * CUATRO animaciones, no 19. La animación aporta la POSTURA; la orientación (pitch, yaw,
     * roll, inclinación por aceleración) la calcula el código.
     * El enum FlyDir con sus 11 direcciones × 4 variantes se retiró a propósito: cada
     * combinación nueva de WASD+Ctrl+Espacio pedía otra animación, y los saltos entre ellas
     * eran el problema que este rediseño existe para resolver.
     */
    private static final ResourceLocation FLY_START =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.fly_start");
    private static final ResourceLocation FLY_CRUISE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.fly");
    private static final ResourceLocation FLY_BOOST =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.fly_boost");
    private static final ResourceLocation FLY_STOP =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.fly_stop");

    private static final int FLY_OFF = 0, FLY_STARTING = 1, FLY_CRUISING = 2,
            FLY_BOOSTING = 3, FLY_STOPPING = 4;

    /** Longitud de los one-shot. Ajústalas a la duración real de fly_start / fly_stop.
     *  Con el fundido de ZenkaiTransitions.FLY (6 ticks) incluido dentro de esta cuenta,
     *  si el start se corta a sí mismo, sube estos números. */
    private static final int FLY_START_TICKS = 8;
    private static final int FLY_STOP_TICKS  = 8;

    /**
     * Máquina de estados del vuelo, COMÚN a local y remotos:
     *   OFF → fly_start → fly ⇄ fly_boost → fly_stop → OFF
     */
    private static void driveFly(AbstractClientPlayer p, AnimState st,
                                 boolean flying, boolean boosting) {
        if (!flying) {
            if (st.flyState == FLY_OFF) return;
            if (st.flyState != FLY_STOPPING) {
                ZenkaiPalAnimations.playFly(p, FLY_STOP);
                st.flyState = FLY_STOPPING;
                st.flyTimer = FLY_STOP_TICKS;
                return;
            }
            if (--st.flyTimer <= 0) {
                ZenkaiPalAnimations.stopFly(p);
                st.flyState = FLY_OFF;
            }
            return;
        }

        switch (st.flyState) {
            case FLY_OFF, FLY_STOPPING -> {
                // Volver a despegar durante el aterrizaje corta el fly_stop: es lo correcto,
                // el jugador ya está en el aire otra vez.
                ZenkaiPalAnimations.playFly(p, FLY_START);
                st.flyState = FLY_STARTING;
                st.flyTimer = FLY_START_TICKS;
            }
            case FLY_STARTING -> {
                if (--st.flyTimer <= 0) enterCruiseOrBoost(p, st, boosting);
            }
            case FLY_CRUISING -> {
                if (boosting) enterCruiseOrBoost(p, st, true);
            }
            case FLY_BOOSTING -> {
                if (!boosting) enterCruiseOrBoost(p, st, false);
            }
            default -> { }
        }
    }

    private static void enterCruiseOrBoost(AbstractClientPlayer p, AnimState st, boolean boosting) {
        ZenkaiPalAnimations.playFly(p, boosting ? FLY_BOOST : FLY_CRUISE);
        st.flyState = boosting ? FLY_BOOSTING : FLY_CRUISING;
        st.flyTimer = 0;
    }

    /** Estado de vuelo para otros sistemas (la inclinación del aura). Ya no hay dirección:
     *  la orientación se deriva de la mirada y del movimiento real. */
    public record FlyPose(boolean flying, boolean boosting) {}

    public static FlyPose flyPoseOf(java.util.UUID playerId) {
        AnimState st = STATES.get(playerId);
        if (st == null || st.flyState == FLY_OFF) return new FlyPose(false, false);
        return new FlyPose(true, st.flyState == FLY_BOOSTING);
    }

    // ── Pose ofensiva del modo combate ───────────────────────────────────────
    /** Duración (ticks) del start antes del loop. Ajústala a tus animaciones. */
    private static final int COMBAT_START_TICKS = 6; // ~0.3 s

    /** styleOrd < 0 = sin pose (fuera de modo combate / derribado / sin estilo / subiendo ki). */
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

    /**
     * Animación de técnica de ki. Funciona igual para el jugador local y los remotos porque
     * ambos leen de ActionStateClient: no hay que inferir nada ni mandar paquetes aparte.
     * El animSet viaja en el canal `visual` del estado, que es la única forma que tiene un
     * observador de saber qué set eligió el otro jugador en su editor.
     */
    private static void tickKiAnim(AbstractClientPlayer p, AnimState st) {
        var action = ActionStateClient.of(p.getId());
        ActionPhase phase = (action.type() == ActionType.KI_TECHNIQUE) ? action.phase() : null;

        if (phase == st.kiPhase) return; // sin cambio: no re-disparar
        ActionPhase prev = st.kiPhase;
        st.kiPhase = phase;

        if (phase == null) {
            ZenkaiPalAnimations.stopKi(p);
            st.kiSet = -1;
            return;
        }

        // visual == 0 → técnica defensiva: animación ÚNICA, sin par charge/release. Se lanza
        // al entrar al estado y no se vuelve a disparar al cambiar de fase, o el paso
        // CHARGING → RELEASING la reiniciaría a media reproducción.
        if (action.visual() == 0) {
            st.kiSet = 0;
            if (prev == null) ZenkaiPalAnimations.playKiBarrier(p);
            return;
        }

        st.kiSet = TechniqueAnimSets.clamp(action.visual());
        switch (phase) {
            case CHARGING     -> ZenkaiPalAnimations.playKiCharge(p, st.kiSet);
            case OVERCHARGING -> ZenkaiPalAnimations.playKiOvercharge(p, st.kiSet);
            case RELEASING    -> ZenkaiPalAnimations.playKiRelease(p, st.kiSet);
            default           -> ZenkaiPalAnimations.stopKi(p);
        }
    }

    /**
     * Subir ki (tecla C). El llamante decide si toca (flag + quieto); aquí solo se encadena
     * start -> loop y se corta. Comparte COMBAT_LAYER con la pose ofensiva, que se suprime
     * mientras esto esté activo.
     */
    private static void tickChargeKi(AbstractClientPlayer p, AnimState st, boolean want) {
        if (want && !st.chargeKiPlaying) {
            st.chargeKiPlaying = true;
            st.chargeKiStartTicks = COMBAT_START_TICKS;
            ZenkaiPalAnimations.playChargeKiStart(p);
        } else if (!want && st.chargeKiPlaying) {
            st.chargeKiPlaying = false;
            st.chargeKiStartTicks = 0;
            ZenkaiPalAnimations.stopCombatIdle(p);
        } else if (st.chargeKiPlaying && st.chargeKiStartTicks > 0
                && --st.chargeKiStartTicks == 0) {
            ZenkaiPalAnimations.playChargeKiLoop(p);
        }
    }

    /**
     * Animación de técnica física de un jugador REMOTO, desde el estado sincronizado.
     * El jugador LOCAL no pasa por aquí a propósito: anima por predicción en el instante del
     * input (CombatModeClientState), sin esperar el round-trip. Si entrara, el ActionState de
     * vuelta relanzaría la animación a mitad y se vería un tirón. Un rechazo del servidor lo
     * atiende onRejected, que corta la predicción.
     * Se dispara por CAMBIO de startTick, no por fase: dos barrages seguidos son dos estados
     * PHYSICAL/ACTIVE distintos y ambos deben animarse.
     */
    private static void tickPhysAnim(AbstractClientPlayer p, AnimState st) {
        var action = ActionStateClient.of(p.getId());

        if (action.type() != ActionType.PHYSICAL) {
            st.physSeenStart = -1L;
            return;
        }
        if (action.startTick() == st.physSeenStart) return; // ya animado

        st.physSeenStart = action.startTick();
        var t = com.hmc.zenkai.feature.technique.PhysicalTechnique.byOrdinal(action.payload());
        if (t != null) ZenkaiPalAnimations.playPhysical(p, t);
    }
}