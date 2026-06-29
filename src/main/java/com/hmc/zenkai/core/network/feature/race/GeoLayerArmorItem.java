package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.CommonAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class GeoLayerArmorItem extends ArmorItem implements GeoItem {

    /** Canal de color de PlayerVisualAttachment con el que se tiñe este modelo (vía getRenderColor). */
    public enum ColorChannel {
        NONE,   // sin tinte (blanco) — textura tal cual
        SKIN,   // skinColorRgb   → cuerpo/piel de raza
        HAIR,   // hairColorRgb   → pelos
        DETAIL  // detailColorRgb → detalles (p. ej. Arcosian)
    }

    private final ResourceLocation modelPath;
    private final ResourceLocation texturePath;
    private final ResourceLocation animationPath;
    private final boolean hasAnimation;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Lo activa ZenkaiFirstPersonArmHooks SOLO durante el render del brazo en 1ª persona.
    public static volatile boolean ARM_RENDER_PASS = false;
    private static double frozenArmTick = 0;
    private static boolean armFrozen    = false;

    // Tinte: por defecto NONE (sin tinte)
    private ColorChannel colorChannel = ColorChannel.NONE;
    // Texturas preset opcionales (para razas con presets pintados, p. ej. Namekian/Arcosian).
    // Si es null se usa siempre texturePath.
    @Nullable private ResourceLocation[] presetTextures = null;
    // Si true, el renderer añade los overlays de cara (ojos/boca/nariz). Solo para items de cuerpo.
    private boolean faceOverlays = false;
    // Si true, el renderer añade la capa de tinte de cuerpo (detalle + líneas) — razas multicolor (Namek).
    private boolean bodyTint = false;
    // Controladores de animación personalizados; si es null se usa el idle genérico (cuando hay animación)
    @Nullable private Consumer<AnimatableManager.ControllerRegistrar> controllerFactory = null;

    public GeoLayerArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties,
                             String modelPath, String texturePath, String animationPath) {
        super(material, type, properties);
        this.modelPath     = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, modelPath);
        this.texturePath   = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, texturePath);
        this.animationPath = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, animationPath);
        this.hasAnimation  = animationPath != null && !animationPath.isBlank();
    }

    // ── Configuración fluida (encadenable en el registro) ─────────────────────

    /** Asigna el canal de color para el tinte. Devuelve this para encadenar. */
    public GeoLayerArmorItem channel(ColorChannel channel) {
        this.colorChannel = channel;
        return this;
    }

    /** Define controladores de animación personalizados. Devuelve this para encadenar. */
    public GeoLayerArmorItem animations(Consumer<AnimatableManager.ControllerRegistrar> factory) {
        this.controllerFactory = factory;
        return this;
    }

    /**
     * Define texturas preset (rutas relativas a assets/zenkai/, p. ej. "textures/models/races/namekian_player_0.png").
     * El renderer elegirá una según el índice skinPreset del jugador. Devuelve this para encadenar.
     */
    public GeoLayerArmorItem presets(String... paths) {
        if (paths == null || paths.length == 0) { this.presetTextures = null; return this; }
        this.presetTextures = new ResourceLocation[paths.length];
        for (int i = 0; i < paths.length; i++)
            this.presetTextures[i] = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, paths[i]);
        return this;
    }

    /** Textura a usar para un índice de preset dado (si hay presets); si no, la textura única. */
    public ResourceLocation getTexture(int presetIndex) {
        if (presetTextures != null && presetTextures.length > 0) {
            int i = Math.floorMod(presetIndex, presetTextures.length);
            return presetTextures[i];
        }
        return texturePath;
    }

    /** Marca este item como "cuerpo" para que el renderer le añada los overlays de cara. */
    public GeoLayerArmorItem faceOverlays() {
        this.faceOverlays = true;
        return this;
    }

    public boolean hasFaceOverlays() { return faceOverlays; }

    /** Marca este item para que el renderer le añada la capa de tinte de cuerpo (detalle + líneas). */
    public GeoLayerArmorItem bodyTint() {
        this.bodyTint = true;
        return this;
    }

    public boolean hasBodyTint() { return bodyTint; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public ResourceLocation getModelPath()     { return modelPath; }
    public ResourceLocation getTexturePath()   { return texturePath; }
    public ResourceLocation getAnimationPath() { return animationPath; }
    public boolean          hasAnimation()     { return hasAnimation; }
    public ColorChannel     getColorChannel()  { return colorChannel; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (controllerFactory != null) {
            controllerFactory.accept(controllers);                       // animaciones personalizadas
        } else if (hasAnimation) {
            controllers.add(CommonAnimations.genericIdleController(this)); // idle por defecto (FIX: antes se descartaba)
        }
        // sin animación y sin factory → no se registra controlador (modelo estático, sin warnings)
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoLayerArmorRenderer renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
                    @Nullable T livingEntity, ItemStack itemStack,
                    @Nullable EquipmentSlot equipmentSlot, @Nullable HumanoidModel<T> original) {
                if (this.renderer == null)
                    this.renderer = new GeoLayerArmorRenderer(GeoLayerArmorItem.this);
                return this.renderer;
            }
        });
    }

    /**
     * Tiempo de animación. Por defecto avanza con el reloj real (RenderUtils.getCurrentTick()).
     * Durante el render del brazo en 1ª persona, si hay una pantalla abierta, lo CONGELA para que
     * el brazo no "flote" solo (la mano vanilla queda quieta con el inventario/menús abiertos).
     * No afecta a los cuerpos en 3ª persona (ARM_RENDER_PASS solo es true durante el brazo).
     */
    @Override
    public double getTick(Object itemStack) {
        double now = RenderUtil.getCurrentTick();
        if (ARM_RENDER_PASS) {
            if (Minecraft.getInstance().screen != null) {
                if (!armFrozen) { frozenArmTick = now; armFrozen = true; }
                return frozenArmTick;
            }
            armFrozen = false;
        }
        return now;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}