package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.ScouterGeoLayer;
import com.hmc.zenkai.feature.race.layer.HairGeoLayer;
import com.hmc.zenkai.feature.race.layer.HaloGeoLayer;
import com.hmc.zenkai.feature.race.layer.RaceSkinGeoArmorLayer;
import com.hmc.zenkai.feature.race.layer.TailGeoLayer;
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

@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class RaceSkinRenderHooks {

    private RaceSkinRenderHooks() {}

    private static final boolean DISABLE_CAPE_LAYER = true;
    private static final boolean DEBUG_LAYER_ORDER = true;

    /**
     * Capas de pelo, cola y halo de cada renderer de jugador (por modelo default/slim): la imagen
     * residual del Paso rápido (AfterImageClient) las vuelve a llamar con la pasada fantasma
     * activa para que la silueta lleve pelo, cola y halo y no solo el cuerpo vanilla.
     */
    private static final java.util.Map<PlayerRenderer,
            List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>>> GHOST_LAYERS =
            new java.util.IdentityHashMap<>();

    public static List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> ghostLayers(PlayerRenderer r) {
        return GHOST_LAYERS.getOrDefault(r, List.of());
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        var mc = Minecraft.getInstance();


        for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;

            // 1) Capturar la lista real de layers y remover armor layer vanilla
            List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> layers = getLayers(renderer);
            if (layers == null) continue;
            if (DISABLE_CAPE_LAYER) {
                layers.removeIf(l -> {
                    String n = l.getClass().getName().toLowerCase();
                    return n.contains("cape");
                });
            }

            List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> removedArmor = new ArrayList<>();

            // Remueve cada una de las HumanoidArmorLayer para reinsertarlas al final
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
            var hairLayer = new HairGeoLayer(
                    renderer,
                    event.getEntityModels(),
                    mc.getModelManager()
            );
            renderer.addLayer(hairLayer);

            // 3b) Cola de Saiyan, mismo criterio que el pelo (solo renderiza si aplica).
            var tailLayer = new TailGeoLayer(
                    renderer,
                    event.getEntityModels(),
                    mc.getModelManager()
            );
            renderer.addLayer(tailLayer);

            // 4) Re-agregar armor layer vanilla (encima)
            for (RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> armorLayer : removedArmor) {
                renderer.addLayer(armorLayer);
            }

            // 5) Halo y scouter van AL FINAL, DESPUÉS de cuanto va antes (pelo incluido).
            //    Antes cada uno se registraba desde su propio @EventBusSubscriber (HaloRenderHooks/
            //    ScouterRenderHooks) escuchando el mismo EntityRenderersEvent.AddLayers: el orden
            //    entre subscribers de clases DISTINTAS no está garantizado por NeoForge, así que
            //    según qué clase se procesara primero, Scouter/Halo podían acabar ANTES que
            //    HairGeoLayer en la lista. Eso importa porque el cristal del scouter usa
            //    RaceRenderTypes.translucent() (ver su javadoc): como cualquier RenderType
            //    "entity translucent" de vanilla SÍ escribe profundidad, si el cristal se dibuja
            //    ANTES que el pelo, el test de profundidad descarta el pelo que debería quedar
            //    detrás de la lente — se ve un hueco/agujero en vez del peinado (bug reportado:
            //    "la textura traslúcida hace que no renderice nada atrás"). Centralizando el
            //    orden aquí, en el mismo sitio que ya reordena la armadura vanilla, Scouter/Halo
            //    quedan SIEMPRE después de Hair/RaceSkin/armadura real — nada necesita dibujarse
            //    detrás de ellos después, así que ese hueco no puede volver a aparecer.
            var haloLayer = new HaloGeoLayer(
                    renderer,
                    event.getEntityModels(),
                    mc.getModelManager()
            );
            renderer.addLayer(haloLayer);
            GHOST_LAYERS.put(renderer, List.of(hairLayer, tailLayer, haloLayer));
            renderer.addLayer(new ScouterGeoLayer(
                    renderer,
                    event.getEntityModels(),
                    mc.getModelManager()
            ));

            if (DEBUG_LAYER_ORDER) {
                logLayerOrder("AFTER_ADDLAYERS", skin.name(), layers);
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

    private static void logLayerOrder(String tag, String skin, List<?> layers) {
        System.out.println("==== [Zenkai] " + tag + " PlayerRenderer layers (" + skin + ") ====");
        for (int i = 0; i < layers.size(); i++) {
            Object l = layers.get(i);
            System.out.println("  [" + i + "] " + l.getClass().getName());
        }
    }
}
