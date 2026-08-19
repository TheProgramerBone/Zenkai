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

public final class ZenkaiPalAnimations {
    private ZenkaiPalAnimations() {}

    /**
     * Política de 1ª persona. Los brazos vanilla van APAGADOS cuando hay cuerpo racial.
     * PAL (PlayerModelMixin, inject at RETURN de setupAnim) apaga todas las partes del modelo
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

    /** Último valor aplicado al jugador local (evita reescribir las cinco capas cada tick). */
    private static Boolean lastFpPolicy = null;

    /**
     * ÚNICO sitio donde se decide la configuración FP. Se llama una vez por tick para el
     * jugador local; solo escribe cuando el estado cambia (elegir raza, activar/desactivar
     * la skin racial, cambiar de forma sin cuerpo propio).
     */
    public static void applyFirstPersonPolicy(AbstractClientPlayer player) {
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