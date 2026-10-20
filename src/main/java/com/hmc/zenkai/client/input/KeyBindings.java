package com.hmc.zenkai.client.input;

import com.hmc.zenkai.client.ClientZenkaiPalTick;
import com.hmc.zenkai.client.CombatModeClientState;
import com.hmc.zenkai.client.LockOnClientState;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxDebugMode;
import com.hmc.zenkai.client.overlay.ScouterClientState;
import com.hmc.zenkai.client.overlay.SenseKiClientState;
import com.hmc.zenkai.client.gui.screens.RaceSelectionScreen;
import com.hmc.zenkai.client.gui.screens.StatsScreen;
import com.hmc.zenkai.client.gui.screens.StyleSelectionScreen;
import com.hmc.zenkai.client.gui.wheel.WheelMenu;
import com.hmc.zenkai.client.gui.wheel.WheelScreen;
import com.hmc.zenkai.feature.mastery.MasteryEffects;
import com.hmc.zenkai.feature.ki.KiChargePacket;
import com.hmc.zenkai.feature.ki.OverdriveChargePacket;
import com.hmc.zenkai.feature.ki.PowerPercentPacket;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.stats.TransformHoldPacket;
import com.hmc.zenkai.feature.teleport.InstantTransmissionConfirmPacket;
import com.hmc.zenkai.feature.teleport.InstantTransmissionHoldPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import com.hmc.zenkai.config.ClientConfig;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {

    private KeyBindings() {}

    public static KeyMapping OPEN_STATS;
    public static KeyMapping CHARGE_KI;
    public static KeyMapping SENSE_KI;
    public static KeyMapping TURBO;
    public static KeyMapping COMBAT_MODE;
    public static KeyMapping LOCK_ON;
    /** Cambiar de objetivo fijado: siguiente (a la derecha) y anterior (a la izquierda). */
    public static KeyMapping LOCK_ON_NEXT;
    public static KeyMapping LOCK_ON_PREV;
    /** Y: abre la ventana de parry sin necesidad de bloquear (ver ParryServer.tryActivate). */
    public static KeyMapping PARRY;

    /**
     * Bloquear. Por defecto el botón DERECHO del ratón (como siempre), pero reasignable en
     * Controles: a otra tecla, o sin asignar para no bloquear nunca. Contexto del modo combate: no
     * marca conflicto con «Usar objeto» de vanilla aunque compartan botón. Cuando comparte el botón
     * de usar, en modo combate y con las manos válidas se cancela la interacción vanilla (ver
     * CombatModeClientState.onInteraction).
     */
    public static KeyMapping BLOCK;

    /**
     * Teclas de los 9 slots de técnicas del modo combate (por defecto 1-9, como siempre). Son
     * reasignables en Controles. Usan ZenkaiKeyContexts.COMBAT_MODE, así que no chocan con la
     * hotbar vanilla aunque compartan tecla. El overlay muestra el nombre de la tecla asignada.
     */
    public static final KeyMapping[] COMBAT_SLOTS = new KeyMapping[9];

    private static final int WHEEL_HOLD_TICKS = 6; // igual que el umbral de B
    private static int xHoldTicks = 0;

    /** TAB: mantener pulsado + CLIC DERECHO = blink de Transmisión Instantánea (ver
     *  handleMouseButton — soltar TAB solo, sin clic derecho, ya NO teletransporta, pedido
     *  explícito del usuario tras probar Dragon Block C, para evitar el blink accidental de
     *  quien solo toca la tecla). Mantener quieto 2s SÍ sigue siendo solo la tecla: arma el
     *  menú de planetas (Fase 2) y soltar lo abre sin necesitar clic derecho. Deliberadamente
     *  coexiste con el binding vanilla de la lista de jugadores (key.playerlist) en la misma
     *  tecla física — decisión del usuario, ver .claude/pendiente/instant-transmission-pendiente.md. */
    public static KeyMapping INSTANT_TRANSMISSION;
    private static boolean lastInstantTransmissionSent = false;

    /** Z: baja el % de poder en escalones (Ki Control). */
    public static KeyMapping POWER_DOWN;

    /** B: toque = destransformar, sostenido = transformar (la máquina de hold es del servidor). */
    public static KeyMapping FORM;

    private static boolean REGISTERED = false;

    private static boolean lastChargeSent = false;
    private static boolean lastForceSent = false;
    private static boolean lastTransformSent = false;

    /** Soltar FORM antes de esto = toque (destransformar). Más largo = intento de transformación. */
    private static final int FORM_TAP_MAX_TICKS = 6;
    private static int formHeldTicks = 0;

    // Vuelo: SIN keybind propio. Reemplaza al viejo G (chocaba con el inventario de Curios) y
    // a un intento posterior de detectar el doble salto a mano aquí mismo — ese intento
    // competía por el mismo gesto que el doble salto NATIVO de vanilla necesita para
    // despegar de verdad, y llegaba un tick tarde. Ahora `FlightSystem.tick()` deja
    // `abilities.mayfly` en true en cuanto el jugador TIENE la capacidad (skill Fly, no
    // sobrecargado) — sin ningún "modo" intermedio — y el propio doble salto de vanilla
    // hace el resto, igual que en creativo. Ver CLAUDE.md, sección de vuelo.

    // Llamado SOLO desde tu evento RegisterKeyMappingsEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        if (REGISTERED) return;
        REGISTERED = true;

        OPEN_STATS = new KeyMapping(
                "key.zenkai.open_stats",
                GLFW.GLFW_KEY_V,
                "key.categories.zenkai"
        );
        event.register(OPEN_STATS);

        // C = cargar KI (se mantiene)
        CHARGE_KI = new KeyMapping(
                "key.zenkai.charge_ki",
                GLFW.GLFW_KEY_C,
                "key.categories.zenkai"
        );
        event.register(CHARGE_KI);

        // Z = bajar el % de poder
        POWER_DOWN = new KeyMapping(
                "key.zenkai.power_down",
                GLFW.GLFW_KEY_Z,
                "key.categories.zenkai"
        );
        event.register(POWER_DOWN);

        // B = transformar (hold) / destransformar (tap)
        FORM = new KeyMapping(
                "key.zenkai.form",
                GLFW.GLFW_KEY_H,
                "key.categories.zenkai"
        );
        event.register(FORM);

        SENSE_KI = new KeyMapping("key.zenkai.sense_ki", GLFW.GLFW_KEY_F4, "key.categories.zenkai");
        event.register(SENSE_KI);

        TURBO = new KeyMapping(
                "key.zenkai.turbo",
                GLFW.GLFW_KEY_R,
                "key.categories.zenkai"
        );
        event.register(TURBO);

        COMBAT_MODE = new KeyMapping(
                "key.zenkai.combat_mode",
                GLFW.GLFW_KEY_X,
                "key.categories.zenkai"
        );
        event.register(COMBAT_MODE);

        LOCK_ON = new KeyMapping(
                "key.zenkai.lock_on",
                GLFW.GLFW_KEY_LEFT_ALT,
                "key.categories.zenkai"
        );
        event.register(LOCK_ON);

        LOCK_ON_NEXT = new KeyMapping(
                "key.zenkai.lock_on_next",
                GLFW.GLFW_KEY_PERIOD,
                "key.categories.zenkai"
        );
        event.register(LOCK_ON_NEXT);

        LOCK_ON_PREV = new KeyMapping(
                "key.zenkai.lock_on_prev",
                GLFW.GLFW_KEY_COMMA,
                "key.categories.zenkai"
        );
        event.register(LOCK_ON_PREV);

        INSTANT_TRANSMISSION = new KeyMapping(
                "key.zenkai.instant_transmission",
                GLFW.GLFW_KEY_TAB,
                "key.categories.zenkai"
        );
        event.register(INSTANT_TRANSMISSION);

        // G es cómoda (junto a F/R/C, al alcance de la mano izquierda) pero es la tecla de Curios por
        // defecto. Con Curios instalado el parry nace en B (cómoda con el pulgar, bajo la barra
        // espaciadora) para no chocar; sin él, en G. Es solo el valor por defecto: se reasigna en
        // Controles como cualquier otra.
        // Parry y bloqueo comparten el contexto del modo combate: pueden estar en el MISMO botón que
        // «Usar objeto» sin avisos de conflicto, y SÍ se avisan entre sí (o con los slots) si el
        // jugador los pone en la misma tecla.
        PARRY = new KeyMapping(
                "key.zenkai.parry",
                ZenkaiKeyContexts.COMBAT_MODE,
                InputConstants.Type.KEYSYM,
                com.hmc.zenkai.compat.CuriosCompat.isLoaded() ? GLFW.GLFW_KEY_B : GLFW.GLFW_KEY_G,
                "key.categories.zenkai"
        );
        event.register(PARRY);

        BLOCK = new KeyMapping(
                "key.zenkai.block",
                ZenkaiKeyContexts.COMBAT_MODE,
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                "key.categories.zenkai"
        );
        event.register(BLOCK);

        for (int i = 0; i < COMBAT_SLOTS.length; i++) {
            COMBAT_SLOTS[i] = new KeyMapping(
                    "key.zenkai.combat_slot_" + (i + 1),
                    ZenkaiKeyContexts.COMBAT_MODE,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_1 + i,
                    "key.categories.zenkai");
            event.register(COMBAT_SLOTS[i]);
        }

    }

    /**
     * Esto lo sigues llamando desde tu InputEvent.Key (como lo tenías antes).
     * Aquí solo manejamos acciones tipo "consumeClick".
     */
    public static void handleKeyInput(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerStatsAttachment stats = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());

        // OPEN_STATS (V)
        if (OPEN_STATS != null && OPEN_STATS.consumeClick()) {
            if (!stats.isRaceChosen()) {
                mc.setScreen(new RaceSelectionScreen()); // nuevo “RaceScreen real”
            } else if (!stats.isStyleChosen()) {
                mc.setScreen(new StyleSelectionScreen(null,null,null)); // ya tiene raza, falta estilo
            } else {
                mc.setScreen(new StatsScreen());
            }
            return;
        }

        // (El PARRY se sondea en CombatModeClientState.onClientTickPre, no aquí: InputEvent.Key no
        // se dispara con botones del ratón y el parry puede reasignarse a uno.)

        if (SENSE_KI != null && SENSE_KI.consumeClick()) {
            SenseKiClientState.onKeyPress(mc);
        }

    }

    /**
     * Clic derecho + TAB sostenido = confirma el blink de Transmisión Instantánea (ver el
     * javadoc de INSTANT_TRANSMISSION). Se engancha a MouseButton.Pre (ANTES de que vanilla
     * procese el clic) para poder CANCELARLO: sin esto, el mismo clic también dispararía una
     * interacción normal por debajo (abrir un cofre, comer, colocar un bloque) mientras dura
     * este gesto, que es justo lo que se quiere evitar sosteniendo una tecla poco común. Solo
     * consume el PRESS (no el release, que no nos interesa) y solo mientras el jugador tiene la
     * skill de verdad — sin eso, sostener TAB (que en este mod también es la tecla de la lista
     * de jugadores) no debe robarle el clic derecho a nadie que no haya comprado la habilidad.
     * La animación de salida (el brazo baja solo) se predice AQUÍ MISMO, en el instante del
     * clic — ver ClientZenkaiPalTick.onInstantTransmissionConfirmClicked — sin esperar a que el
     * servidor confirme que el blink de verdad se ejecutó.
     */
    public static void handleMouseButton(InputEvent.MouseButton.Pre e) {
        if (e.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || e.getAction() != InputConstants.PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (INSTANT_TRANSMISSION == null || !INSTANT_TRANSMISSION.isDown()) return;
        if (SkillEffects.instantTransmissionLevel(mc.player) <= 0) return;

        e.setCanceled(true);
        PacketDistributor.sendToServer(new InstantTransmissionConfirmPacket());
        ClientZenkaiPalTick.onInstantTransmissionConfirmClicked();
    }

    /**
     * IMPORTANTÍSIMO:
     * Llama esto UNA VEZ por tick desde tu ClientTickEvent.Post (donde ya haces PAL o client logic).
     * Ejemplo en tu handler:
     * public static void onClientTick(ClientTickEvent.Post e) { KeyBindings.handleClientTick(); }
     */
    public static void handleClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var player = mc.player;
        SenseKiClientState.tick(mc);
        ScouterClientState.tick(mc);
        CombatModeClientState.tick(mc);
        PlayerStatsAttachment stats = player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        stats.setStatMultiplier(MasteryEffects.formStatFactor(player));
        // Espejo cliente de las pesas: weightLoad y weightFactor son derivados de servidor y
        // NO viajan en el sync de stats. Sin esto, el menú y el scouter mostrarían el PL sin
        // penalizar mientras el servidor aplica la penalización de verdad.
        // Va DESPUÉS de setStatMultiplier: computeLoad pide el PL limpio, que lo lleva dentro.
        double weightLoad = WeightSystem.computeLoad(player);
        stats.setWeightLoad(weightLoad);
        stats.setWeightFactor(WeightSystem.statFactor(weightLoad));

        boolean hasRace = stats.isRaceChosen();

        if (LOCK_ON != null) {
            while (LOCK_ON.consumeClick()) LockOnClientState.press(mc);
            // Modo "mantener": al levantar la tecla se suelta. Con un screen abierto isDown() es
            // false aunque la tecla siga física, y no debe soltar el lock al abrir el inventario.
            if (mc.screen == null && ClientConfig.lockOnHold() && !LOCK_ON.isDown()) {
                LockOnClientState.release(mc);
            }
        }
        if (LOCK_ON_NEXT != null) {
            while (LOCK_ON_NEXT.consumeClick()) LockOnClientState.cycle(mc, +1);
        }
        if (LOCK_ON_PREV != null) {
            while (LOCK_ON_PREV.consumeClick()) LockOnClientState.cycle(mc, -1);
        }
        LockOnClientState.tick(mc);

        // Gate: sin raza, cortar y vaciar colas. Sin el drenaje, los clicks se acumulan
        // y se disparan en conjunto de golpe la próxima vez que alguien los consuma.
        if (!hasRace) {
            stopChargeIfNeeded();
            stopForceIfNeeded();
            stopTransformHoldIfNeeded();
            stopInstantTransmissionIfNeeded();
            drainClicks(CHARGE_KI);
            drainClicks(POWER_DOWN);
            drainClicks(FORM);
            drainClicks(COMBAT_MODE);
            drainClicks(INSTANT_TRANSMISSION);
            formHeldTicks = 0;
            return;
        }

        // ── B: sostenido = transformar, toque = volver a base ──────────────
        boolean formDown = false;
        if (FORM != null) {
            drainClicks(FORM); // el estado lo lleva formHeldTicks, no la cola
            formDown = FORM.isDown();

            if (formDown) {
                formHeldTicks++;
            } else if (formHeldTicks > 0) {
                // Toque corto = destransformar. Un hold largo abortado no hace nada:
                // el servidor ya descartó el progreso al dejar de recibir transformHeld.
                if (formHeldTicks <= FORM_TAP_MAX_TICKS) {
                    PacketDistributor.sendToServer(new TransformHoldPacket(
                            TransformHoldPacket.Action.DETRANSFORM, true));
                }
                formHeldTicks = 0;
            }

            if (formDown != lastTransformSent) {
                lastTransformSent = formDown;

                // feedback local
                var form = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
                form.setTransformHeld(formDown);

                PacketDistributor.sendToServer(new TransformHoldPacket(
                        TransformHoldPacket.Action.TRANSFORM_HOLD, formDown));
            }
        }

        // ── Z: bajar el % de poder, escalón a escalón ──────────────────────
        if (POWER_DOWN != null) {
            while (POWER_DOWN.consumeClick()) {
                PacketDistributor.sendToServer(new PowerPercentPacket());
            }
        }

        // ── C: cargar ki (el % de poder sube solo). Shift+C, ya al 100%, fuerza por encima. ──
        if (formDown) {
            // Transformando no se carga ki: respeta la animación de transformación.
            stopChargeIfNeeded();
            stopForceIfNeeded();
            drainClicks(CHARGE_KI);
        } else if (CHARGE_KI != null) {
            drainClicks(CHARGE_KI); // se lee por isDown(): hay que vaciar la cola igual
            boolean now = CHARGE_KI.isDown();
            if (now != lastChargeSent) {
                lastChargeSent = now;
                PacketDistributor.sendToServer(new KiChargePacket(now));
            }

            // Shift se lee del estado de la ventana, no de un KeyMapping aparte: una sola tecla
            // en el menú de controles y el modificador funciona con la que el jugador rebindee
            // (mismo criterio que SenseKiClientState usa para Shift+F4).
            boolean wantForce = now && Screen.hasShiftDown();
            if (wantForce != lastForceSent) {
                lastForceSent = wantForce;
                PacketDistributor.sendToServer(new OverdriveChargePacket(wantForce));
            }
        }

        // TAB: solo el flanco viaja al servidor (mismo criterio que B/FORM). El progreso real
        // (quieto/no quieto, blink al soltar, armar el menú) es servidor-autoritativo: aquí no
        // se cuentan ticks ni se decide nada, solo se refleja si la tecla está pulsada o no.
        if (INSTANT_TRANSMISSION != null) {
            drainClicks(INSTANT_TRANSMISSION);
            boolean itDown = INSTANT_TRANSMISSION.isDown();
            if (itDown != lastInstantTransmissionSent) {
                lastInstantTransmissionSent = itDown;
                PacketDistributor.sendToServer(new InstantTransmissionHoldPacket(itDown));
            }
        }

        // X: toque = modo combate · mantenido = rueda. El toggle pasa a dispararse al SOLTAR,
        // porque hasta entonces no sabemos si era toque o mantenido.
        drainClicks(COMBAT_MODE);
        boolean xDown = COMBAT_MODE.isDown();
        if (xDown) {
            xHoldTicks++;
            if (xHoldTicks == WHEEL_HOLD_TICKS && mc.screen == null && mc.player != null) {
                mc.setScreen(new WheelScreen(WheelMenu.build(mc.player)));
            }
        } else {
            if (xHoldTicks > 0 && xHoldTicks < WHEEL_HOLD_TICKS) {
                CombatModeClientState.toggle(mc);
            }
            xHoldTicks = 0;
        }
    }

    /** Vacía la cola de clicks de una tecla que se lee por isDown(). Sin esto se acumulan
     *  y se disparan de golpe cuando alguien las consuma (era el bug del % de poder). */
    private static void drainClicks(KeyMapping key) {
        if (key == null) return;
        while (key.consumeClick()) { /* descartar */ }
    }

    private static void stopChargeIfNeeded() {
        if (lastChargeSent) {
            lastChargeSent = false;
            PacketDistributor.sendToServer(new KiChargePacket(false));
        }
    }

    private static void stopForceIfNeeded() {
        if (lastForceSent) {
            lastForceSent = false;
            PacketDistributor.sendToServer(new OverdriveChargePacket(false));
        }
    }

    private static void stopInstantTransmissionIfNeeded() {
        if (lastInstantTransmissionSent) {
            lastInstantTransmissionSent = false;
            PacketDistributor.sendToServer(new InstantTransmissionHoldPacket(false));
        }
    }

    private static void stopTransformHoldIfNeeded() {
        if (lastTransformSent) {
            lastTransformSent = false;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var form = mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
                form.setTransformHeld(false);
            }

            PacketDistributor.sendToServer(new TransformHoldPacket(
                    TransformHoldPacket.Action.TRANSFORM_HOLD, false
            ));
        }
    }
}
