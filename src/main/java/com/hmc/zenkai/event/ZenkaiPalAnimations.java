package com.hmc.zenkai.event;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
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

    /** Direcciones de vuelo. Cada una tiene 3 animaciones del PAL (créalas en player_animations/):
     *   cruise     = "fly.<dir>"              (crucero, sin Control)
     *   boostStart = "fly.<dir>_boost_start"  (INTERMEDIA: transición al pulsar Control, se ve una vez)
     *   boost      = "fly.<dir>_boost"         (boost a tope, loop)
     *  Las que no hagas, reapunta el campo a otra (p.ej. boostStart = cruise). */
    public enum FlyDir {
        IDLE("fly.idle"),
        FORWARD("fly.forward"),   BACK("fly.back"),
        LEFT("fly.left"),         RIGHT("fly.right"),
        UP("fly.up"),             DOWN("fly.down"),
        FORWARD_LEFT("fly.forward_left"),  FORWARD_RIGHT("fly.forward_right"),
        BACK_LEFT("fly.back_left"),        BACK_RIGHT("fly.back_right");

        public final ResourceLocation cruise;
        public final ResourceLocation cruiseStart;
        public final ResourceLocation boostStart;
        public final ResourceLocation boost;
        FlyDir(String base) {
            this.cruiseStart = rl(base + "_start");
            this.cruise = rl(base);
            this.boostStart = rl(base + "_boost_start");
            this.boost      = rl(base + "_boost");
        }
        private static ResourceLocation rl(String path) {
            return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
        }
    }

    public static PlayerAnimationController controller(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.TRANSFORM_LAYER);
    }

    public static PlayerAnimationController flyController(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.FLY_LAYER);
    }

    public static void playFly(AbstractClientPlayer player, ResourceLocation anim) {
        flyController(player).triggerAnimation(anim);
    }

    public static void stopFly(AbstractClientPlayer player) {
        flyController(player).stopTriggeredAnimation();
    }

    public static void playTransformStart(AbstractClientPlayer player) {
        controller(player).triggerAnimation(TRANSFORMATION_1);
    }

    public static void playTransformLoop(AbstractClientPlayer player) {
        controller(player).triggerAnimation(TRANSFORMATION_2);
    }

    public static PlayerAnimationController blockController(AbstractClientPlayer player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, ZenkaiPalLayers.BLOCK_LAYER);
    }

    public static void playBlock(AbstractClientPlayer player) {
        blockController(player).triggerAnimation(BLOCK_ANIM);
    }

    public static void stopBlock(AbstractClientPlayer player) {
        blockController(player).stopTriggeredAnimation();
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
        combatController(player).triggerAnimation(COMBAT_IDLE_START[styleOrdinal]);
    }

    public static void playCombatIdleLoop(AbstractClientPlayer player, int styleOrdinal) {
        if (styleOrdinal < 0 || styleOrdinal >= COMBAT_IDLE_LOOP.length) return;
        combatController(player).triggerAnimation(COMBAT_IDLE_LOOP[styleOrdinal]);
    }

    public static void stopCombatIdle(AbstractClientPlayer player) {
        combatController(player).stopTriggeredAnimation();
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

    public static void playPhysical(AbstractClientPlayer player,
                                    PhysicalTechnique t) {
        var c = (PlayerAnimationController) PlayerAnimationAccess
                .getPlayerAnimationLayer(player, ZenkaiPalLayers.PHYS_LAYER);
        if (c != null) c.triggerAnimation(PHYS_ANIMS[t.ordinal()]);
    }
}