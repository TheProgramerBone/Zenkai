package com.hmc.db_renewed.entity.race;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.network.stats.PlayerStatsAttachment;
import com.hmc.db_renewed.network.stats.Race;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

@EventBusSubscriber(modid = DragonBlockRenewed.MOD_ID, value = Dist.CLIENT)
public final class RaceSkinRenderHooks {

    private RaceSkinRenderHooks() {}

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;

            // 1) Capturar y remover armor layer vanilla
            List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> layers = getLayers(renderer);
            if (layers == null) continue;

            List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> removedArmor = new ArrayList<>();
            layers.removeIf(l -> {
                if (l instanceof HumanoidArmorLayer<?, ?, ?>) {
                    removedArmor.add(l);
                    return true;
                }
                return false;
            });

            // 2) Agregar nuestra layer de raza (debajo)
            renderer.addLayer(new RaceSkinGeoArmorLayer(
                    renderer,
                    event.getEntityModels(),
                    Minecraft.getInstance().getModelManager()
            ));

            // 3) Re-agregar armor layer vanilla (encima)
            for (var armorLayer : removedArmor) {
                renderer.addLayer(armorLayer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> getLayers(
            PlayerRenderer renderer
    ) {
        try {
            Field f = LivingEntityRenderer.class.getDeclaredField("layers");
            f.setAccessible(true);
            return (List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>>) f.get(renderer);
        } catch (Throwable t) {
            return null;
        }
    }
}