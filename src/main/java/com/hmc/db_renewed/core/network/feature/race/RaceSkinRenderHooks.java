package com.hmc.db_renewed.core.network.feature.race;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.race.hairs.HairGeoLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = DragonBlockRenewed.MOD_ID, value = Dist.CLIENT)
public final class RaceSkinRenderHooks {

    private RaceSkinRenderHooks() {}

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        var mc = Minecraft.getInstance();

        for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;

            // 1) Capturar la lista real de layers y remover armor layer vanilla
            List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> layers = getLayers(renderer);
            if (layers == null) continue;

            List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> removedArmor = new ArrayList<>();

            // Remueve todas las HumanoidArmorLayer para reinsertarlas al final
            layers.removeIf(l -> {
                if (l instanceof HumanoidArmorLayer<?, ?, ?>) {
                    removedArmor.add(l);
                    return true;
                }
                return false;
            });

            // 2) Agregar layer de body racial (debajo de la armadura vanilla)
            renderer.addLayer(new RaceSkinGeoArmorLayer(
                    renderer,
                    event.getEntityModels(),
                    mc.getModelManager()
            ));

            // 3) Agregar layer de pelo Saiyan (también debajo de la armadura vanilla)
            //    (Solo renderiza cuando aplica; en otros casos hace return)
            renderer.addLayer(new HairGeoLayer(
                    renderer,
                    event.getEntityModels(),
                    mc.getModelManager()
            ));

            // 4) Re-agregar armor layer vanilla (encima)
            for (RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> armorLayer : removedArmor) {
                renderer.addLayer(armorLayer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> getLayers(PlayerRenderer renderer) {
        try {
            Field f = LivingEntityRenderer.class.getDeclaredField("layers");
            f.setAccessible(true);
            return (List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>>) f.get(renderer);
        } catch (Throwable t) {
            return null;
        }
    }
}
