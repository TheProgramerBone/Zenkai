package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorModel;
import com.hmc.zenkai.feature.race.layer.RaceLayerDiscovery;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Brazo en 1ª persona usando el MISMO modelo del cuerpo (reusado): oculta cada uno de los huesos
 * menos el del brazo y lo renderiza en la pose que da RenderArmEvent. Así hereda automáticamente
 * cualquier cambio de modelo (transformaciones, género, etc.) sin assets extra.
 * Para razas con tinte multicapa (bodyTint: Namek, y futuras Majin/Arcosian) pinta además las
 * pasadas _detail y _lines sobre el brazo, igual que BodyTintGeoLayer hace en 3ª persona — si no,
 * el brazo se vería "hueco" donde la textura base es transparente.
 * ⚠ API de GeckoLib 4.8.4 a verificar al compilar:
 *   · GeoObjectRenderer (constructor + firma de render(...))
 *   · GeoModel.getBakedModel(...) / BakedGeoModel.topLevelBones()
 *   · GeoBone.setHidden(boolean) / setChildrenHidden(boolean)
 *   · Color.ofOpaque(rgb)  (mismo factory que GeoLayerArmorRenderer)
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiFirstPersonArmHooks {

    private ZenkaiFirstPersonArmHooks() {}

    // ── Brazo DERECHO — calibración original, MUTABLE en runtime vía /zenkaiarmright. Se deja
    // intacta a propósito como copia de seguridad: es la única calibración que existió hasta
    // ahora y todo lo de abajo se deriva de ella, nunca al revés. ──
    public static float OFF_X = -1.55f;
    public static float OFF_Y = 0.65f;
    public static float OFF_Z = -0.5f;
    public static float SCALE = 1.0f;
    public static float ROT_X = 0.0f;
    public static float ROT_Y = 0.0f;
    public static float ROT_Z = 0.0f;

    // Copia INMUTABLE de los valores de arriba, solo para /zenkaiarmright reset — si alguien
    // "juega" con los campos mutables en una sesión de calibración y pierde la cuenta de por
    // dónde iba, esto es el punto de vuelta conocido-bueno. Nunca se lee en el render, solo en
    // resetRight().
    private static final float ORIGINAL_OFF_X = -1.55f;
    private static final float ORIGINAL_OFF_Y = 0.65f;
    private static final float ORIGINAL_OFF_Z = -0.5f;
    private static final float ORIGINAL_SCALE = 1.0f;
    private static final float ORIGINAL_ROT_X = 0.0f;
    private static final float ORIGINAL_ROT_Y = 0.0f;
    private static final float ORIGINAL_ROT_Z = 0.0f;

    // ── Brazo IZQUIERDO — espejo del derecho, MUTABLE en runtime vía /zenkaiarmleft. ──
    // El brazo izquierdo NUNCA usó estas constantes hasta ahora: renderArm() reutilizaba el
    // set de arriba para los dos brazos, solo invirtiendo pivotX (-5 vs 5, el pivote real de
    // bipedRightArm/bipedLeftArm en *_player.geo.json). Eso deja el codo bien anclado pero la
    // pose (offset + rotación de 3 ejes, calibrada a ojo SOLO para el brazo derecho con
    // /zenkaiarmright) sale mal en el izquierdo, porque un offset/rotación no se copia igual
    // entre dos huesos que son espejo geométrico el uno del otro.
    // Verificado contra ".claude/mods de referencia/firstperson.geo.json" (un rig de mano en
    // 1ª persona de otro mod, ajeno a este): sus huesos "rightarm"/"leftarm" SÍ son el mismo
    // espejo que bipedRightArm/bipedLeftArm de aquí (pivot X negado, Y/Z de pivot iguales;
    // rotation X igual, Y/Z negados) — mismo patrón que un espejo respecto al plano X=0.
    // Las cifras de ese geo no son reutilizables tal cual (es un rig propio con otro origen y
    // otra escala), pero confirman la fórmula de espejo para Y/Z de posición y para las tres
    // rotaciones — Y y Z de offset SÍ salieron correctos calibrando en juego (0.65 / -0.5,
    // idénticos al espejo). El offset X, en cambio, NO: la fórmula predice -OFF_X = 1.55, pero
    // calibrado en juego (2026-09-01) el valor que de verdad alinea el brazo izquierdo es 0.6 —
    // mismo signo, magnitud muy distinta. La malla del brazo (bipedLeftArm) no es un espejo
    // perfecto de bipedRightArm solo en ese eje (alguna asimetría del propio modelo/mirror UV
    // que la fórmula de pivote no captura), así que LEFT_OFF_X queda con el valor EMPÍRICO, no
    // el derivado — no "corregir" esto de vuelta a -OFF_X sin recalibrar en juego.
    public static float LEFT_OFF_X = 0.6f;
    public static float LEFT_OFF_Y = OFF_Y;
    public static float LEFT_OFF_Z = OFF_Z;
    public static float LEFT_SCALE = SCALE;
    public static float LEFT_ROT_X = ROT_X;
    public static float LEFT_ROT_Y = -ROT_Y;
    public static float LEFT_ROT_Z = -ROT_Z;

    // Copia INMUTABLE de la calibración izquierda CONFIRMADA EN JUEGO (2026-09-01), no de la
    // fórmula de espejo (que para X predice 1.55, no 0.6 — ver comentario arriba). Punto de
    // vuelta de /zenkaiarmleft reset.
    private static final float ORIGINAL_LEFT_OFF_X = 0.6f;
    private static final float ORIGINAL_LEFT_OFF_Y = 0.65f;
    private static final float ORIGINAL_LEFT_OFF_Z = -0.5f;
    private static final float ORIGINAL_LEFT_SCALE = 1.0f;
    private static final float ORIGINAL_LEFT_ROT_X = 0.0f;
    private static final float ORIGINAL_LEFT_ROT_Y = 0.0f;
    private static final float ORIGINAL_LEFT_ROT_Z = 0.0f;

    /** Restaura el brazo DERECHO a su calibración original de fábrica. */
    public static void resetRight() {
        OFF_X = ORIGINAL_OFF_X;
        OFF_Y = ORIGINAL_OFF_Y;
        OFF_Z = ORIGINAL_OFF_Z;
        SCALE = ORIGINAL_SCALE;
        ROT_X = ORIGINAL_ROT_X;
        ROT_Y = ORIGINAL_ROT_Y;
        ROT_Z = ORIGINAL_ROT_Z;
    }

    /** Restaura el brazo IZQUIERDO a su calibración original de fábrica (la confirmada en
     *  juego, no la de la fórmula de espejo — ver {@link #ORIGINAL_LEFT_OFF_X}). */
    public static void resetLeft() {
        LEFT_OFF_X = ORIGINAL_LEFT_OFF_X;
        LEFT_OFF_Y = ORIGINAL_LEFT_OFF_Y;
        LEFT_OFF_Z = ORIGINAL_LEFT_OFF_Z;
        LEFT_SCALE = ORIGINAL_LEFT_SCALE;
        LEFT_ROT_X = ORIGINAL_LEFT_ROT_X;
        LEFT_ROT_Y = ORIGINAL_LEFT_ROT_Y;
        LEFT_ROT_Z = ORIGINAL_LEFT_ROT_Z;
    }

    private static final String RIGHT_ARM_BONE = "bipedRightArm";
    private static final String LEFT_ARM_BONE  = "bipedLeftArm";

    private static com.hmc.zenkai.feature.race.ZenkaiFirstPersonArmHooks.ArmRenderer RENDERER; // cache perezoso (en hilo de render)

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent e) {
        // Mientras PAL tiene una animación en THIRD_PERSON_MODEL, el cuerpo entero ya se dibuja:
        // la mano vanilla sobra. Se CANCELA — hacer solo `return` la dejaba pasar, que es lo que
        // metía el brazo + manga de la skin vanilla en cámara.
        // isFirstPersonPass() no sirve aquí: es false durante renderHandsWithItems. Hay que
        // preguntarle al gestor de animación de PAL, que es lo que consulta el propio PAL en su
        // ItemInHandRendererMixin.
        var mgr = ((com.zigythebird.playeranim.accessors.IAnimatedPlayer) e.getPlayer())
                .playerAnimLib$getAnimManager();
        if (mgr != null && mgr.isActive()
                && mgr.getFirstPersonMode()
                == com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode.THIRD_PERSON_MODEL) {
            e.setCanceled(true);
            return;
        }

        Player player = e.getPlayer();
        var visual = player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        var stats  = player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        if (!visual.shouldRenderRaceSkin() || !stats.isRaceChosen()) return;

        ItemStack body = RaceSkinSlots.getVirtualRaceArmor(player, EquipmentSlot.CHEST);
        if (!(body.getItem() instanceof GeoLayerArmorItem item)) return;

        e.setCanceled(true); // ocultar el brazo vanilla

        if (RENDERER == null) RENDERER = new ArmRenderer();
        RENDERER.renderArm(item, player, e.getArm(), e.getPoseStack(),
                e.getMultiBufferSource(), e.getPackedLight());
    }

    /** GeoObjectRenderer que pinta SOLO el brazo del cuerpo, tintado según el canal del item. */
    private static final class ArmRenderer extends GeoObjectRenderer<GeoLayerArmorItem> {
        private Player current;
        /** Si != null, getRenderColor devuelve este color (para las pasadas detail/lines). */
        private Integer passColor = null;

        ArmRenderer() { super(new GeoLayerArmorModel()); }

        void renderArm(GeoLayerArmorItem item, Player player, HumanoidArm arm,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
            this.current = player;

            GeoModel<GeoLayerArmorItem> model = getGeoModel();
            BakedGeoModel baked = model.getBakedModel(model.getModelResource(item));
            String armRoot = (arm == HumanoidArm.RIGHT) ? RIGHT_ARM_BONE : LEFT_ARM_BONE;

            try {
                for (GeoBone b : baked.topLevelBones()) {
                    boolean isArm = b.getName().equals(armRoot);
                    setBranchHidden(b, !isArm);
                    // Los GeoBone del modelo son compartidos y mutables: un render previo (3ª persona)
                    // pudo dejar el brazo animado por el idle. Lo devolvemos a su pose de bind ANTES de
                    // pintar, y como el render fuerza isReRender=true (no corre handleAnimations), se queda
                    // quieto igual que el brazo vanilla. Fin del vaivén.
                    if (isArm) resetBranchToBind(b);
                }

                ResourceLocation baseTex = model.getTextureResource(item);
                RenderType rt = RenderType.entityTranslucent(baseTex);

                boolean isRight = (arm == HumanoidArm.RIGHT);
                float pivotX = isRight ? -5f : 5f;
                float pivotY = 22f;
                // Cada brazo usa SU PROPIA calibración (derecha original / izquierda espejo,
                // ver comentario de los campos arriba) — nunca la misma para los dos.
                float offX = isRight ? OFF_X : LEFT_OFF_X;
                float offY = isRight ? OFF_Y : LEFT_OFF_Y;
                float offZ = isRight ? OFF_Z : LEFT_OFF_Z;
                float rotX = isRight ? ROT_X : LEFT_ROT_X;
                float rotY = isRight ? ROT_Y : LEFT_ROT_Y;
                float rotZ = isRight ? ROT_Z : LEFT_ROT_Z;
                float scale = isRight ? SCALE : LEFT_SCALE;

                poseStack.pushPose();
                poseStack.translate(offX, offY, offZ);
                poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
                poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
                poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ));
                poseStack.scale(scale, scale, scale);
                poseStack.scale(1f, -1f, 1f);
                poseStack.translate(-pivotX / 16f, -pivotY / 16f, 0f);

                // Pasada base (piel): tinte por el canal del item (SKIN).
                passColor = null;
                render(poseStack, item, buffers, rt, buffers.getBuffer(rt), light, OverlayTexture.NO_OVERLAY);

                // Pasadas de tinte de cuerpo (detalle + líneas) para razas multicolor.
                if (item.hasBodyTint()) {
                    for (RaceLayerDiscovery.Layer layer : RaceLayerDiscovery.layersFor(item)) {
                        if (layer.index() == 0) continue;
                        passColor = layer.argb(player) & 0xFFFFFF;
                        RenderType rtL = RaceRenderTypes.viewOffset(layer.texture());
                        render(poseStack, item, buffers, rtL, buffers.getBuffer(rtL), light, OverlayTexture.NO_OVERLAY);
                    }
                    passColor = null;
                }

                poseStack.popPose();
            } finally {
                for (GeoBone b : baked.topLevelBones()) {
                    setBranchHidden(b, false);
                }
                this.passColor = null;
                this.current = null;
            }
        }

        /** Oculta/muestra un hueso y su descendencia entera (no solo un nivel). */
        private static void setBranchHidden(GeoBone bone, boolean hidden) {
            bone.setHidden(hidden);
            bone.setChildrenHidden(hidden);
            for (GeoBone child : bone.getChildBones()) {
                setBranchHidden(child, hidden);
            }
        }

        /** Devuelve el hueso y su descendencia a la pose inicial (bind), anulando cualquier animación previa. */
        private static void resetBranchToBind(GeoBone bone) {
            BoneSnapshot s = bone.getInitialSnapshot();
            if (s != null) {
                bone.setRotX(s.getRotX());     bone.setRotY(s.getRotY());     bone.setRotZ(s.getRotZ());
                bone.setPosX(s.getOffsetX());  bone.setPosY(s.getOffsetY());  bone.setPosZ(s.getOffsetZ());
                bone.setScaleX(s.getScaleX()); bone.setScaleY(s.getScaleY()); bone.setScaleZ(s.getScaleZ());
            }
            for (GeoBone child : bone.getChildBones()) {
                resetBranchToBind(child);
            }
        }

        /**
         * Fuerza isReRender=true SIEMPRE: así {@code GeoObjectRenderer.actuallyRender} NO llama a
         * handleAnimations y el modelo se pinta tal cual quedó tras {@link #resetBranchToBind} (bind pose).
         * Es lo que mantiene el brazo estático (como el vanilla) en vez de reproducir el idle.
         */
        @Override
        public void actuallyRender(PoseStack poseStack, GeoLayerArmorItem animatable, BakedGeoModel model,
                                   RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                   boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                    true, partialTick, packedLight, packedOverlay, colour);
        }

        @Override
        public Color getRenderColor(GeoLayerArmorItem animatable, float partialTick, int packedLight) {
            if (passColor != null) return Color.ofOpaque(passColor & 0xFFFFFF);
            if (current == null) return Color.WHITE;
            GeoLayerArmorItem.ColorChannel ch = animatable.getColorChannel();
            if (ch == GeoLayerArmorItem.ColorChannel.NONE) return Color.WHITE;
            var visual = current.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
            int rgb = switch (ch) {
                case SKIN   -> visual.getSkinColorRgb();
                case HAIR   -> visual.getHairColorRgb();
                default     -> 0xFFFFFF;
            };
            return Color.ofOpaque(rgb);
        }
    }
}