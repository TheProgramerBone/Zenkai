package com.hmc.zenkai.client.hair;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.hair.strand.HairRampColors;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Textura del pelo por mechones para UN color: 64×64, filas = rampa de tonos de HairRampColors
 * (fila 0 = +1, la más clara; fila 63 = -1). StrandGeoWriter apunta cada cara a su franja. Se crea
 * al vuelo la primera vez que se pide un color (DynamicTexture) y se guardan las últimas usadas.
 */
public final class HairRampTextures {
    private HairRampTextures() {}

    private static final int SIZE = 64;
    /** Texturas vivas: un cambio de forma pasa por hasta 48 colores por jugador (HairResolver). */
    private static final int MAX = 160;
    private static final Map<String, ResourceLocation> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() <= MAX) return false;
            Minecraft.getInstance().getTextureManager().release(eldest.getValue());
            return true;
        }
    };

    public static ResourceLocation forColor(int rgb) {
        return forColors(new int[]{rgb});
    }

    /**
     * Una columna por color (StrandColors: columna 0 = color de pelo, el resto colores propios de
     * los mechones); las columnas de más repiten la 0.
     */
    public static ResourceLocation forColors(int[] colors) {
        StringBuilder key = new StringBuilder(colors.length * 7);
        for (int c : colors) key.append(String.format("%06x", c & 0xFFFFFF)).append('_');
        ResourceLocation id = CACHE.get(key.toString());
        if (id != null) return id;
        NativeImage img = new NativeImage(SIZE, SIZE, false);
        Map<Integer, int[]> ramps = new java.util.HashMap<>();
        for (int x = 0; x < SIZE; x++) {
            int base = (x < colors.length ? colors[x] : colors[0]) & 0xFFFFFF;
            int[] ramp = ramps.computeIfAbsent(base, HairRampTextures::ramp);
            for (int y = 0; y < SIZE; y++) img.setPixelRGBA(x, y, ramp[y]);
        }
        // Un nombre corto y estable por clave (la clave entera puede medir 64 colores).
        String name = colors.length == 1 ? String.format("%06x", colors[0] & 0xFFFFFF)
                : "p" + Integer.toHexString(key.toString().hashCode()) + "_" + colors.length;
        id = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "hair_ramp/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
        CACHE.put(key.toString(), id);
        return id;
    }

    /** Los 64 tonos (ABGR, como los guarda NativeImage) de un color. */
    private static int[] ramp(int rgb) {
        // El negro puro no tiene sombras posibles: se aclara un poco antes de sacar la rampa.
        rgb = HairRampColors.lift(rgb);
        int[] out = new int[SIZE];
        for (int y = 0; y < SIZE; y++) {
            float shade = 1f - 2f * y / (SIZE - 1);
            int c = HairRampColors.shade(rgb, shade);
            out[y] = 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
        }
        return out;
    }
}
