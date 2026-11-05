package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.feature.player.death.BodyGear;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.armortrim.ArmorTrim;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.HashSet;
import java.util.Set;

/**
 * Dibuja sobre un cuerpo tumbado la armadura puesta y los objetos de las manos. No hay un LivingEntity detrás (el cuerpo es
 * una Entity y el modelo se pinta en su pose de reposo), así que no sirve {@code HumanoidArmorLayer}: aquí se repite su
 * lógica (capas del material, tinte, ribete y brillo) con los modelos de armadura de vanilla.
 *
 * Alcance: armaduras al estilo vanilla (las del propio vanilla y las de mods que usan capas de {@code ArmorMaterial}). Las
 * armaduras con modelo propio (GeckoLib) no se dibujan: no hay entidad que pasarles.
 * La mano principal es la DERECHA; el objeto se coloca como en {@code ItemInHandLayer}.
 */
final class BodyGearRenderer {
    private BodyGearRenderer() {}

    static void render(PoseStack ps, MultiBufferSource buffers, int light, BodyGear gear, PlayerModel<LivingEntity> body,
                       PlayerBodyRenderer.Models models, Entity wearer) {
        armor(ps, buffers, light, gear.feet(), EquipmentSlot.FEET, models, wearer);
        armor(ps, buffers, light, gear.legs(), EquipmentSlot.LEGS, models, wearer);
        armor(ps, buffers, light, gear.chest(), EquipmentSlot.CHEST, models, wearer);
        armor(ps, buffers, light, gear.head(), EquipmentSlot.HEAD, models, wearer);
        hand(ps, buffers, light, gear.hand(), HumanoidArm.RIGHT, body);
        hand(ps, buffers, light, gear.off(), HumanoidArm.LEFT, body);
    }

    /** Armaduras GeckoLib que fallaron al dibujarse sobre un cuerpo: no se vuelve a intentar (se avisó una vez en el log). */
    private static final Set<net.minecraft.world.item.Item> GEO_BROKEN = new HashSet<>();

    /**
     * Armadura GECKOLIB (scouter, pesas, y la de cualquier mod con {@code GeoItem}). {@code GeoArmorRenderer.prepForRender}
     * pide una Entity cualquiera, no un LivingEntity, así que se le pasa el que "lleva" la pieza (el propio cuerpo; quien lo
     * carga si va en brazos). GeckoLib copia las rotaciones del modelo base a sus huesos, y ese modelo base está en la misma pose
     * tumbada que el cuerpo. Un mod que asuma LivingEntity/Player puede lanzar excepción: se captura por pieza, se avisa UNA vez y
     * esa pieza deja de dibujarse (nunca tumba el cliente).
     * @return true si la pieza era GeckoLib y ya está tratada (dibujada o descartada); false para seguir por el camino de vanilla.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean geoArmor(PoseStack ps, MultiBufferSource buffers, int light, ItemStack stack, EquipmentSlot slot,
                                    PlayerBodyRenderer.Models models, Entity wearer) {
        if (!(stack.getItem() instanceof GeoItem) || wearer == null) return false;
        if (GEO_BROKEN.contains(stack.getItem())) return true;
        try {
            GeoRenderProvider provider = GeoRenderProvider.of(stack);
            HumanoidModel<LivingEntity> base = models.outer();
            base.young = false;
            base.setAllVisible(true);
            HumanoidModel<?> m = provider.getGeoArmorRenderer((LivingEntity) null, stack, slot, base);
            if (!(m instanceof GeoArmorRenderer renderer)) return false;      // no es un renderer GeckoLib de armadura: camino vanilla
            renderer.prepForRender(wearer, stack, slot, base, buffers, 0f, 0f, 0f, 0f, 0f);
            ResourceLocation tex = renderer.getTextureLocation((software.bernie.geckolib.animatable.GeoAnimatable) stack.getItem());
            renderer.renderToBuffer(ps, buffers.getBuffer(RenderType.armorCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY, -1);
            return true;
        } catch (Throwable t) {
            GEO_BROKEN.add(stack.getItem());
            com.hmc.zenkai.Zenkai.LOGGER.warn("Armadura GeckoLib {} no se pudo dibujar sobre un cuerpo; se omite", stack.getItem(), t);
            return true;
        }
    }

    private static void armor(PoseStack ps, MultiBufferSource buffers, int light, ItemStack stack, EquipmentSlot slot,
                              PlayerBodyRenderer.Models models, Entity wearer) {
        if (!(stack.getItem() instanceof ArmorItem item) || item.getEquipmentSlot() != slot) return;
        if (geoArmor(ps, buffers, light, stack, slot, models, wearer)) return;
        boolean inner = slot == EquipmentSlot.LEGS;
        HumanoidModel<LivingEntity> m = inner ? models.inner() : models.outer();
        m.young = false;
        m.setAllVisible(false);
        switch (slot) {
            case HEAD -> { m.head.visible = true; m.hat.visible = true; }
            case CHEST -> { m.body.visible = true; m.rightArm.visible = true; m.leftArm.visible = true; }
            case LEGS -> { m.body.visible = true; m.rightLeg.visible = true; m.leftLeg.visible = true; }
            default -> { m.rightLeg.visible = true; m.leftLeg.visible = true; }
        }

        ArmorMaterial material = item.getMaterial().value();
        int dye = stack.is(ItemTags.DYEABLE) ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -6265536)) : -1;
        for (ArmorMaterial.Layer layer : material.layers()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.armorCutoutNoCull(layer.texture(inner)));
            m.renderToBuffer(ps, vc, light, OverlayTexture.NO_OVERLAY, layer.dyeable() ? dye : -1);
        }
        ArmorTrim trim = stack.get(DataComponents.TRIM);
        if (trim != null) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET)
                    .getSprite(inner ? trim.innerTexture(item.getMaterial()) : trim.outerTexture(item.getMaterial()));
            VertexConsumer vc = sprite.wrap(buffers.getBuffer(Sheets.armorTrimsSheet(trim.pattern().value().decal())));
            m.renderToBuffer(ps, vc, light, OverlayTexture.NO_OVERLAY, -1);
        }
        if (stack.hasFoil()) m.renderToBuffer(ps, buffers.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY, -1);
    }

    private static void hand(PoseStack ps, MultiBufferSource buffers, int light, ItemStack stack, HumanoidArm arm,
                             PlayerModel<LivingEntity> body) {
        if (stack.isEmpty()) return;
        boolean left = arm == HumanoidArm.LEFT;
        ps.pushPose();
        body.translateToHand(arm, ps);
        ps.mulPose(Axis.XP.rotationDegrees(-90.0f));
        ps.mulPose(Axis.YP.rotationDegrees(180.0f));
        ps.translate((left ? -1 : 1) / 16.0f, 0.125f, -0.625f);
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(stack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                light, OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, 0);
        ps.popPose();
    }
}
