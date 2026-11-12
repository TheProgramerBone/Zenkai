package com.hmc.zenkai.client.kiweapon;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import org.lwjgl.opengl.GL11;

/**
 * Copia BLANCA del atlas de bloques (donde viven los sprites de todos los ítems, también de otros mods): mismo diseño,
 * mismo alfa, rgb a 255. Con ella el halo de Ki Infuse es una SILUETA plana del color del aura: el render type de vanilla
 * multiplica textura x color, y con la textura real un diamante (verde azulado, oscuro) teñido de aura morada daba una
 * sombra verdosa en vez de un resplandor (capturas 2026-10-20). Se sigue usando un render type de VANILLA a propósito: uno
 * propio se pierde en la pasada de mano con shaderpack (ver lecciones-transversales.md, Shaderpacks).
 *
 * Se lee del atlas de la GPU la primera vez que hace falta y se rehace cuando el atlas se vuelve a coser (recarga de
 * recursos), porque las UV de cada sprite cambian.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ItemSilhouetteAtlas {
    private ItemSilhouetteAtlas() {}

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "dynamic/item_silhouette_atlas");
    private static DynamicTexture texture;
    private static boolean dirty = true;

    @SubscribeEvent
    public static void onStitched(TextureAtlasStitchedEvent e) {
        if (e.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) dirty = true;
    }

    /** La textura blanca, o {@code null} si no se pudo construir (quien llama usa entonces el atlas normal). */
    public static ResourceLocation get() {
        if (!dirty && texture != null) return ID;
        try {
            Minecraft mc = Minecraft.getInstance();
            var atlas = mc.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS);
            if (!(atlas instanceof TextureAtlas)) return null;
            RenderSystem.bindTexture(atlas.getId());
            int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w <= 0 || h <= 0) return null;
            NativeImage img = new NativeImage(w, h, false);
            img.downloadTexture(0, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = (img.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    img.setPixelRGBA(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            if (texture != null) {
                texture.setPixels(img);
                texture.upload();
            } else {
                texture = new DynamicTexture(img);
                mc.getTextureManager().register(ID, texture);
            }
            dirty = false;
            return ID;
        } catch (Throwable t) {
            Zenkai.LOGGER.warn("[Zenkai] No se pudo construir el atlas de siluetas del halo de Ki Infuse", t);
            dirty = false;   // no reintentar cada fotograma
            return null;
        }
    }
}
