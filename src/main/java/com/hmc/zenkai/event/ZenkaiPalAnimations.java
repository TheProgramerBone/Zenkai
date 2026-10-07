package com.hmc.zenkai.event;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.TechniqueAnimSets;
import com.hmc.zenkai.client.ZenkaiTransitions;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.feature.technique.TechniqueAnimOverride;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;

public final class ZenkaiPalAnimations {
    private ZenkaiPalAnimations() {}

    /**
     * Política de 1ª persona. Los brazos vanilla van APAGADOS cuando hay cuerpo racial.
     * PAL (PlayerModelMixin, inject at RETURN de setupAnim) apaga cada una de las partes del modelo
     * en la pasada FP y vuelve a encender rightArm/leftArm — y con ellos sus mangas de segunda
     * capa — según esta configuración. Corre DESPUÉS de cualquier evento o mixin nuestro, así
     * que la visibilidad de los brazos SOLO se controla desde aquí; intentar apagarlos con
     * setAllVisible() o cancelando renderToBuffer no sirve de nada.
     * Con cuerpo racial los brazos los aporta el chestplate geo (armorLeftArm/armorRightArm)
     * a través de HumanoidArmorLayer, que es una de las dos únicas render layers que PAL deja
     * pasar en la pasada FP. Sin cuerpo racial se dejan los brazos vanilla o el jugador se
     * queda manco.
     */
    private static final FirstPersonConfiguration FP_RACIAL = new FirstPersonConfiguration()
            .setShowLeftArm(true)
            .setShowRightArm(true)
            .setShowLeftItem(true)
            .setShowRightItem(true)
            .setShowArmor(true);

    private static final FirstPersonConfiguration FP_VANILLA = new FirstPersonConfiguration()
            .setShowLeftArm(true)
            .setShowRightArm(true)
            .setShowLeftItem(true)
            .setShowRightItem(true)
            .setShowArmor(true);

    public static PlayerAnimationController newFirstPersonController(AbstractClientPlayer player) {
        PlayerAnimationController c = new PlayerAnimationController(
                player, (controller, state, animSetter) -> PlayState.STOP);
        c.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
        c.setFirstPersonConfiguration(FP_VANILLA); // se corrige en el primer tick
        return c;
    }

    /**
     * La capa de vuelo usa un controlador propio que aplica la orientación dinámica encima de
     * la animación. Misma política de 1ª persona que las demás: applyFirstPersonPolicy itera
     * ZenkaiPalLayers.ALL, así que esta capa también recibe la configuración al cambiar.
     */
    public static PlayerAnimationController newFlightController(AbstractClientPlayer player) {
        var c = new com.hmc.zenkai.client.fly.FlyAnimationController(
                player, (controller, state, animSetter) -> PlayState.STOP);
        // FUERA de la 1ª persona, a diferencia de las otras cinco capas. La pose de vuelo pone
        // los brazos extendidos a la altura del pecho, y en boost el cuerpo se tumba 90°: en
        // primera persona eso es el torso cruzando el plano de cámara. Con NONE, volando se
        // ven las manos vanilla y el cuerpo sigue animándose para los demás.
        c.setFirstPersonMode(FirstPersonMode.NONE);
        c.setFirstPersonConfiguration(FP_VANILLA);
        return c;
    }

    /**
     * Clips que NUNCA se ven en 1ª persona (el resto de jugadores sí los ve). Para añadir otro,
     * basta con meter aquí su id: applyFirstPersonModes lo apaga solo mientras ese clip sea el
     * actual de su capa. Beast: la pose agresiva de forzar tapa media pantalla pegada a la cámara.
     */
    private static final java.util.Set<ResourceLocation> THIRD_PERSON_ONLY = new java.util.HashSet<>();

    // ── Swing de ataque vanilla sobre capas que mandan en los brazos ─────────────────────────
    /** Ajustes del swing (1.0 = idéntico a vanilla). Un solo sitio para calibrarlo a ojo. */
    private static final float SWING_PITCH_MUL = 1.0f;   // cuánto levanta/baja el brazo
    private static final float SWING_YAW_MUL   = 1.0f;   // cuánto lo cruza hacia dentro
    private static final float SWING_ROLL_MUL  = 1.0f;   // cuánto lo abre hacia fuera

    /**
     * El clic de ataque vanilla (swing) SÍ mueve el brazo que ataca aunque una capa PAL mande sobre
     * los brazos. Causa: PAL arranca cada hueso con la pose vanilla (que ya incluye el swing) pero
     * el clip SUSTITUYE la rotación del brazo (copyOtherBoneIfNotDisabled), así que el brazo se
     * quedaba quieto mientras el torso —que el clip no toca— sí giraba con el swing. Se SUMA
     * encima del brazo animado la misma rotación que HumanoidModel#setupAttackAnimation añade al
     * brazo que golpea (mano que ataca + brazo principal). Lo llama el controlador de la capa
     * desde get3DTransformRaw; hoy solo el de vuelo (FlyAnimationController).
     */
    public static void applyVanillaSwing(AbstractClientPlayer p, com.zigythebird.playeranimcore.bones.PlayerAnimBone bone) {
        boolean rightBone = "right_arm".equals(bone.getName());
        if (!rightBone && !"left_arm".equals(bone.getName())) return;

        float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float t = p.getAttackAnim(partial);
        if (t <= 0f) return;

        net.minecraft.world.entity.HumanoidArm arm = p.swingingArm == net.minecraft.world.InteractionHand.MAIN_HAND
                ? p.getMainArm() : p.getMainArm().getOpposite();
        if ((arm == net.minecraft.world.entity.HumanoidArm.RIGHT) != rightBone) return;

        // Copia de HumanoidModel#setupAttackAnimation (1.21.1).
        float twist = net.minecraft.util.Mth.sin(net.minecraft.util.Mth.sqrt(t) * net.minecraft.util.Mth.TWO_PI)
                * 0.2f * (arm == net.minecraft.world.entity.HumanoidArm.LEFT ? -1f : 1f);
        float f1 = 1f - t;
        f1 *= f1;
        f1 *= f1;
        f1 = 1f - f1;
        float f2 = net.minecraft.util.Mth.sin(f1 * net.minecraft.util.Mth.PI);
        float headX = p.getXRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        float f3 = net.minecraft.util.Mth.sin(t * net.minecraft.util.Mth.PI) * -(headX - 0.7f) * 0.75f;
        bone.rotX -= (f2 * 1.2f + f3) * SWING_PITCH_MUL;
        bone.rotY += twist * 2f * SWING_YAW_MUL;
        bone.rotZ += net.minecraft.util.Mth.sin(t * net.minecraft.util.Mth.PI) * -0.4f * SWING_ROLL_MUL;
    }

    /** Último modo aplicado por controlador (clave = el controlador mismo: al reaparecer o
     *  reconectar el jugador local es otra entidad con controladores nuevos, y un "ya aplicado"
     *  guardado solo como booleano se lo saltaría). */
    private static final java.util.Map<PlayerAnimationController, FirstPersonMode> LAST_FP_MODE =
            new java.util.WeakHashMap<>();

    /**
     * Modo de 1ª persona de cada capa, decidido por tick para el jugador local. FLY queda fuera:
     * va siempre en NONE (ver newFlightController). Una capa va en NONE (manos vanilla, sin
     * pasada corporal de PAL) cuando:
     *  - vuelo rápido (Ctrl) activo: la pasada corporal de PAL se rompe con el cuerpo tumbado, así
     *    que NINGUNA animación se ve en 1ª persona salvo las de la capa de vuelo, que ya es NONE;
     *    volar normal sí es seguro y no entra aquí;
     *  - su clip actual está en THIRD_PERSON_ONLY;
     *  - es la capa de ki y la opción de cliente "animaciones de ki en 1ª persona" está apagada.
     * Consecuencia conocida de NONE: sin pasada corporal no hay huesos de mano que leer, así que
     * la bola de carga cae a su posición de respaldo delante de la cámara (ver
     * .claude/docs/animaciones-y-modelos.md, "PAL filtra las capas...").
     */
    private static void applyFirstPersonModes(AbstractClientPlayer player) {
        boolean fastFlight = com.hmc.zenkai.client.ClientZenkaiPalTick.fastFlightMode;
        boolean kiFp = com.hmc.zenkai.config.ClientConfig.kiFirstPersonAnimations();
        for (ResourceLocation layer : ZenkaiPalLayers.ALL) {
            if (layer.equals(ZenkaiPalLayers.FLY_LAYER)) continue;
            var c = (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, layer);
            if (c == null) continue;
            ResourceLocation clip = ZenkaiTransitions.currentClip(c);
            boolean fp = !fastFlight && (clip == null || !THIRD_PERSON_ONLY.contains(clip));
            if (layer.equals(ZenkaiPalLayers.KI_LAYER)) fp &= kiFp;
            FirstPersonMode mode = fp ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
            if (LAST_FP_MODE.put(c, mode) != mode) c.setFirstPersonMode(mode);
        }
    }

    /** Último valor aplicado al jugador local (evita reescribir las cinco capas cada tick). */
    private static Boolean lastFpPolicy = null;

    /**
     * ÚNICO sitio donde se decide la configuración FP. Se llama una vez por tick para el
     * jugador local; solo escribe cuando el estado cambia (elegir raza, activar/desactivar
     * la skin racial, cambiar de forma sin cuerpo propio).
     */
    public static void applyFirstPersonPolicy(AbstractClientPlayer player) {
        applyFirstPersonModes(player);
        boolean racial = com.hmc.zenkai.feature.race.ZenkaiFirstPersonBody.hasRacialBody(player);
        if (lastFpPolicy != null && lastFpPolicy == racial) return;
        lastFpPolicy = racial;

        FirstPersonConfiguration cfg = racial ? FP_RACIAL : FP_VANILLA;
        for (ResourceLocation layer : ZenkaiPalLayers.ALL) {
            var c = (PlayerAnimationController) PlayerAnimationAccess
                    .getPlayerAnimationLayer(player, layer);
            if (c != null) c.setFirstPersonConfiguration(cfg);
        }
    }

    public static final ResourceLocation TRANSFORMATION_1 =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.transformation1");
    public static final ResourceLocation TRANSFORMATION_2 =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.transformation2");

    private static final ResourceLocation BLOCK_ANIM =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.block");


    public static PlayerAnimationController controller(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.TRANSFORM_LAYER);
    }

    public static PlayerAnimationController flyController(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.FLY_LAYER);
    }

    public static void playFly(AbstractClientPlayer player, ResourceLocation anim) {
        ZenkaiTransitions.play(flyController(player), anim, ZenkaiTransitions.FLY);
    }

    public static void stopFly(AbstractClientPlayer player) {
        ZenkaiTransitions.stop(flyController(player), ZenkaiTransitions.FLY);
    }

    public static void playTransformStart(AbstractClientPlayer player) {
        ZenkaiTransitions.play(controller(player), TRANSFORMATION_1, ZenkaiTransitions.TRANSFORM);
    }

    public static void playTransformLoop(AbstractClientPlayer player) {
        // start -> loop también funde: sin esto el salto al loop es un tirón visible.
        ZenkaiTransitions.play(controller(player), TRANSFORMATION_2, ZenkaiTransitions.TRANSFORM);
    }

    public static void stopTransform(AbstractClientPlayer player) {
        ZenkaiTransitions.stop(controller(player), ZenkaiTransitions.TRANSFORM);
    }

    public static PlayerAnimationController blockController(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.BLOCK_LAYER);
    }

    public static void playBlock(AbstractClientPlayer player) {
        ZenkaiTransitions.play(blockController(player), BLOCK_ANIM, ZenkaiTransitions.BLOCK_IN);
    }

    public static void stopBlock(AbstractClientPlayer player) {
        // El fundido de salida NO retrasa el input: dejar de defender ya se resolvió en
        // servidor cuando esto se ejecuta. Es solo la representación la que se toma 4 ticks.
        ZenkaiTransitions.stop(blockController(player), ZenkaiTransitions.BLOCK_OUT);
    }

    // ── Pose ofensiva del modo combate ───────────────────────────────────────
    /** Por estilo (orden = Style.ordinal()):
     *   start = "zenkai.combat_idle_<estilo>_start" (transición, se ve una vez)
     *   loop  = "zenkai.combat_idle_<estilo>"        (pose sostenida, loop) */
    private static final String[] COMBAT_STYLES = {"warrior", "martial_artist", "spiritualist"};
    private static final ResourceLocation[] COMBAT_IDLE_START = new ResourceLocation[COMBAT_STYLES.length];
    private static final ResourceLocation[] COMBAT_IDLE_LOOP  = new ResourceLocation[COMBAT_STYLES.length];
    static {
        for (int i = 0; i < COMBAT_STYLES.length; i++) {
            COMBAT_IDLE_START[i] = ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID, "zenkai.combat_idle_" + COMBAT_STYLES[i] + "_start");
            COMBAT_IDLE_LOOP[i] = ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID, "zenkai.combat_idle_" + COMBAT_STYLES[i]);
        }
    }

    public static PlayerAnimationController combatController(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.COMBAT_LAYER);
    }

    public static void playCombatIdleStart(AbstractClientPlayer player, int styleOrdinal) {
        if (styleOrdinal < 0 || styleOrdinal >= COMBAT_IDLE_START.length) return;
        ZenkaiTransitions.play(combatController(player), COMBAT_IDLE_START[styleOrdinal],
                ZenkaiTransitions.COMBAT);
    }

    public static void playCombatIdleLoop(AbstractClientPlayer player, int styleOrdinal) {
        if (styleOrdinal < 0 || styleOrdinal >= COMBAT_IDLE_LOOP.length) return;
        ZenkaiTransitions.play(combatController(player), COMBAT_IDLE_LOOP[styleOrdinal],
                ZenkaiTransitions.COMBAT);
    }

    public static void stopCombatIdle(AbstractClientPlayer player) {
        ZenkaiTransitions.stop(combatController(player), ZenkaiTransitions.COMBAT);
    }

    // ── Técnicas físicas: una animación one-shot por técnica (sin loop):
    //    "zenkai.phys_dash_punch", "zenkai.phys_heavy_blow", "zenkai.phys_barrage",
    //    "zenkai.phys_kiai" (orden = PhysicalTechnique.ordinal()). ──
    private static final ResourceLocation[] PHYS_ANIMS;
    static {
        var vals = PhysicalTechnique.values();
        PHYS_ANIMS = new ResourceLocation[vals.length];
        for (int i = 0; i < vals.length; i++) {
            PHYS_ANIMS[i] = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                    "zenkai.phys_" + vals[i].name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public static void playPhysical(AbstractClientPlayer player, PhysicalTechnique t) {
        var c = (PlayerAnimationController) PlayerAnimationAccess
                .getPlayerAnimationLayer(player, ZenkaiPalLayers.PHYS_LAYER);
        // 2 ticks: el golpe tiene que salir seco. Fundir más se siente como lag aunque el
        // daño se haya resuelto en el tick 0.
        ZenkaiTransitions.play(c, PHYS_ANIMS[t.ordinal()], ZenkaiTransitions.PHYS);
    }

    // ── Esquive automático de Ultra Instinct: dos clips one-shot, el servidor sortea cuál ──
    private static final ResourceLocation[] UI_DODGE_ANIMS = {
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.ui_dodge_1"),
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.ui_dodge_2")};
    /** Cuántas variantes de esquive existen (el servidor sortea 0..N-1, ver UiDodgeAnimPacket). */
    public static final int UI_DODGE_VARIANTS = UI_DODGE_ANIMS.length;

    public static void playUiDodge(AbstractClientPlayer player, int variant) {
        if (variant < 0 || variant >= UI_DODGE_ANIMS.length) return;
        var c = (PlayerAnimationController) PlayerAnimationAccess
                .getPlayerAnimationLayer(player, ZenkaiPalLayers.PHYS_LAYER);
        // Misma capa y fundido corto que las técnicas físicas: reactivo, tiene que salir seco.
        ZenkaiTransitions.play(c, UI_DODGE_ANIMS[variant], ZenkaiTransitions.PHYS);
        // Sin esperar al tick siguiente: si no, el primer tick del esquive aún se vería en 1ª persona.
        if (player == net.minecraft.client.Minecraft.getInstance().player) applyFirstPersonModes(player);
    }

    // ── Técnicas de ki ───────────────────────────────────────────────────────

    public static PlayerAnimationController kiController(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess
                .getPlayerAnimationLayer(player, ZenkaiPalLayers.KI_LAYER);
    }

    public static void playKiCharge(AbstractClientPlayer p, int animSet) {
        ZenkaiTransitions.play(kiController(p), TechniqueAnimSets.charge(animSet),
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void playKiOvercharge(AbstractClientPlayer p, int animSet) {
        // charge -> overcharge es un cambio de pose sostenida: funde como la carga.
        ZenkaiTransitions.play(kiController(p), TechniqueAnimSets.overcharge(animSet),
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void playKiRelease(AbstractClientPlayer p, int animSet) {
        ZenkaiTransitions.play(kiController(p), TechniqueAnimSets.release(animSet),
                ZenkaiTransitions.KI_RELEASE);
    }

    public static void playKiBarrier(AbstractClientPlayer p) {
        ZenkaiTransitions.play(kiController(p), TechniqueAnimSets.BARRIER,
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void stopKi(AbstractClientPlayer p) {
        ZenkaiTransitions.stop(kiController(p), ZenkaiTransitions.KI_CHARGE);
    }

    public static void playChargeKiStart(AbstractClientPlayer p) {
        ZenkaiTransitions.play(combatController(p), TechniqueAnimSets.KI_CHARGE_START,
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void playChargeKiLoop(AbstractClientPlayer p) {
        ZenkaiTransitions.play(combatController(p), TechniqueAnimSets.KI_CHARGE_LOOP,
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void playOverrideCharge(AbstractClientPlayer p, TechniqueAnimOverride ov) {
        ZenkaiTransitions.play(kiController(p), TechniqueAnimSets.overrideCharge(ov),
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void playOverrideOvercharge(AbstractClientPlayer p, TechniqueAnimOverride ov) {
        ZenkaiTransitions.play(kiController(p), TechniqueAnimSets.overrideOvercharge(ov),
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void playOverrideRelease(AbstractClientPlayer p, TechniqueAnimOverride ov) {
        ZenkaiTransitions.play(kiController(p), TechniqueAnimSets.overrideRelease(ov),
                ZenkaiTransitions.KI_RELEASE);
    }

    // ── Transmisión Instantánea (TAB sostenido) ─────────────────────────────

    /** Pose de carga, sostenida mientras dura el hold — tanto si termina en blink (toque
     *  rápido) como si llega a los 5s que arman el menú. Nombre de archivo/clip fijado en el
     *  plan de la Fase 1 antes de que existiera el .animation.json: ver
     *  .claude/pendiente/instant-transmission-pendiente.md. */
    private static final ResourceLocation INSTANT_TRANSMISSION_CHARGE =
                ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.instant_transmission_charge");

    /** Clip de salida (el brazo baja solo) cuando el blink SÍ se ejecutó — ver
     *  ClientZenkaiPalTick.onInstantTransmissionTeleported. Mismo patrón que el par charge/
     *  release de las técnicas de ki (TechniqueAnimSets): un clip SOSTENIDO (hold_on_last_frame)
     *  para la espera y un clip CORTO, de un solo disparo (sin loop), para el cierre — nunca un
     *  fade-to-null (ver la prohibición documentada en ZenkaiTransitions.stop). Archivo pendiente
     *  de crear en Blockbench por el usuario: sin él, ZenkaiTransitions.play devuelve false y el
     *  llamador cae al corte en seco de siempre — no revienta nada. */
    private static final ResourceLocation INSTANT_TRANSMISSION_RELEASE =
                ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.instant_transmission_release");

    public static PlayerAnimationController transmissionController(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess
                .getPlayerAnimationLayer(player, ZenkaiPalLayers.TRANSMISSION_LAYER);
    }

    public static void playInstantTransmissionCharge(AbstractClientPlayer p) {
        ZenkaiTransitions.play(transmissionController(p), INSTANT_TRANSMISSION_CHARGE,
                ZenkaiTransitions.KI_CHARGE);
    }

    /** @return true si el clip de salida existe y se lanzó — false si el asset todavía no
     *  existe, para que el llamador caiga a stopInstantTransmissionCharge (corte en seco) en vez
     *  de dejar la pose de carga congelada para siempre sin ningún camino de salida. */
    public static boolean playInstantTransmissionRelease(AbstractClientPlayer p) {
        return ZenkaiTransitions.play(transmissionController(p), INSTANT_TRANSMISSION_RELEASE,
                ZenkaiTransitions.KI_RELEASE);
    }

    /** Corte SIEMPRE en seco (nunca fade-to-null, ver ZenkaiTransitions.stop). Reservado para
     *  cuando el hold NO termina en un blink real: se cancela a medio camino, se arma el menú en
     *  vez de blinkear, o el clip de salida (arriba) todavía no existe — ver
     *  ClientZenkaiPalTick.onInstantTransmissionTeleported/onInstantTransmissionMenuOpened y el
     *  timeout IT_AWAIT_TIMEOUT_TICKS. Cuando SÍ hay blink real, usar
     *  playInstantTransmissionRelease en su lugar para que el brazo baje solo. */
    public static void stopInstantTransmissionCharge(AbstractClientPlayer p) {
        ZenkaiTransitions.stop(transmissionController(p), ZenkaiTransitions.KI_CHARGE);
    }

    static {
        // Esquives de Ultra Instinct: el cuerpo entero se mueve y en 1ª persona rompen la vista.
        Collections.addAll(THIRD_PERSON_ONLY, UI_DODGE_ANIMS);
    }

    // ── Capa de PREVIEW (editores) ───────────────────────────────────────────

    /**
     * Controlador de la capa de preview. FirstPersonMode.NONE, igual que la capa de vuelo y
     * por la misma razón: hay poses que no tienen sentido pegadas a la cámara. Aquí además
     * es el objetivo, no un efecto lateral — el preview del editor debe verse SOLO en el
     * modelo renderizado, nunca en las manos del jugador.
     * No comparte controlador con KI_LAYER a propósito: conmutar el modo de la capa real al
     * abrir y cerrar la pantalla deja estados inconsistentes si el cierre no llega.
     */
    public static PlayerAnimationController newPreviewController(AbstractClientPlayer player) {
        PlayerAnimationController c = new PlayerAnimationController(
                player, (controller, state, animSetter) -> PlayState.STOP);
        c.setFirstPersonMode(FirstPersonMode.NONE);
        c.setFirstPersonConfiguration(FP_VANILLA);
        return c;
    }

    private static PlayerAnimationController previewController(AbstractClientPlayer p) {
        return (PlayerAnimationController) PlayerAnimationAccess
                .getPlayerAnimationLayer(p, ZenkaiPalLayers.PREVIEW_LAYER);
    }

    /** Mismos clips y mismos fundidos que la capa de ki real: el preview tiene que enseñar
     *  lo que se va a ver en combate, no una versión suya. */
    public static void playPreviewCharge(AbstractClientPlayer p, int animSet) {
        ZenkaiTransitions.play(previewController(p), TechniqueAnimSets.charge(animSet),
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void playPreviewOvercharge(AbstractClientPlayer p, int animSet) {
        ZenkaiTransitions.play(previewController(p), TechniqueAnimSets.overcharge(animSet),
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void playPreviewRelease(AbstractClientPlayer p, int animSet) {
        ZenkaiTransitions.play(previewController(p), TechniqueAnimSets.release(animSet),
                ZenkaiTransitions.KI_RELEASE);
    }

    /** Clip único de la barrera. Se mantiene aparte porque no tiene par carga/disparo. */
    public static void playPreviewBarrier(AbstractClientPlayer p) {
        ZenkaiTransitions.play(previewController(p), TechniqueAnimSets.BARRIER,
                ZenkaiTransitions.KI_CHARGE);
    }

    // Anulaciones con las tres fases (explosión). Mismos clips y mismos fundidos que en
    // combate: el preview enseña lo que se va a ver, no una versión suya.

    public static void playPreviewOverrideCharge(AbstractClientPlayer p, TechniqueAnimOverride ov) {
        ZenkaiTransitions.play(previewController(p), TechniqueAnimSets.overrideCharge(ov),
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void playPreviewOverrideOvercharge(AbstractClientPlayer p, TechniqueAnimOverride ov) {
        ZenkaiTransitions.play(previewController(p), TechniqueAnimSets.overrideOvercharge(ov),
                ZenkaiTransitions.KI_CHARGE);
    }

    public static void playPreviewOverrideRelease(AbstractClientPlayer p, TechniqueAnimOverride ov) {
        ZenkaiTransitions.play(previewController(p), TechniqueAnimSets.overrideRelease(ov),
                ZenkaiTransitions.KI_RELEASE);
    }

    public static void stopPreview(AbstractClientPlayer p) {
        ZenkaiTransitions.stop(previewController(p), ZenkaiTransitions.KI_CHARGE);
    }
}