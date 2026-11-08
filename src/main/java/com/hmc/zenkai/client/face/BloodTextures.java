package com.hmc.zenkai.client.face;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manchas de sangre sobre el CUERPO (2026-11-01): una textura dinámica por (textura del cuerpo, etapa, color), de las
 * MISMAS medidas y UV que la del cuerpo, así que se dibuja re-renderizando ese modelo (ver {@code BloodGeoLayer}).
 *
 * POR QUÉ PROCEDURAL. Cada raza y cada forma tiene su geo y su UV; dibujar a mano un decal por cuerpo no escala. Aquí
 * se parte de la propia textura del cuerpo: solo se pintan píxeles que ya son opacos, así que las manchas caen donde hay
 * piel (y nunca fuera del modelo), con un ruido de valor a escala de 3 px (manchas) mezclado con ruido por píxel
 * (salpicaduras). Las etapas son ANIDADAS (la 2 contiene la 1, la 3 la 2) para que la herida crezca en vez de cambiar
 * de sitio. Cubren aprox. el 4,5 %, 11 % y 20 % de los píxeles del cuerpo (medido sobre la textura humana).
 *
 * Solo hilo de render. La caché se vacía en cada recarga de recursos (RaceTextureCacheReload) y al salir del mundo.
 */
public final class BloodTextures {
    private BloodTextures() {}

    /** Umbral del ruido por etapa (1..3): píxeles con valor MENOR que él se manchan. */
    private static final float[] THRESHOLD = {0f, 0.21f, 0.28f, 0.35f};
    /** Rojo (humano, saiyan, majin) y morado (namekiano y arcosiano), ABGR sin alfa: tres tonos cada uno. */
    private static final int[] RED = {0x9B1212, 0x7A0C0C, 0xB61C1C};
    private static final int[] PURPLE = {0x7B2D9B, 0x5E2078, 0x9444B8};

    /** Canal más alto por debajo del cual un píxel cuenta como «negro» (ropa) y no recibe sangre. */
    private static final int DARK_CUTOFF = 70;

    private record Key(ResourceLocation base, int stage, boolean purple) {}

    private static final Map<Key, ResourceLocation> CACHE = new HashMap<>();
    private static final Set<Key> MISSING = new HashSet<>();
    private static final List<ResourceLocation> REGISTERED = new ArrayList<>();
    private static int counter;

    /** Etapa de herida (0 = ninguna, 1..3) según la fracción de vida que queda. */
    public static int stageFor(float healthFraction) {
        if (healthFraction <= 0.20f) return 3;
        if (healthFraction <= 0.45f) return 2;
        if (healthFraction <= 0.70f) return 1;
        return 0;
    }

    /** Textura de sangre de esa etapa para ese cuerpo, o null si no hay textura base. */
    @Nullable
    public static ResourceLocation get(ResourceLocation base, int stage, boolean purple) {
        if (stage <= 0) return null;
        Key key = new Key(base, stage, purple);
        ResourceLocation done = CACHE.get(key);
        if (done != null) return done;
        if (MISSING.contains(key)) return null;

        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(base);
        if (res.isEmpty()) {
            MISSING.add(key);
            return null;
        }
        NativeImage out;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            out = new NativeImage(img.getWidth(), img.getHeight(), true); // calloc: transparente
            int[] palette = purple ? PURPLE : RED;
            int seed = base.getPath().hashCode();
            float thr = THRESHOLD[Math.min(stage, 3)];
            float scale = img.getWidth() / 256f; // las manchas miden lo mismo en una textura a 512
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int px = img.getPixelRGBA(x, y); // ABGR
                    if (((px >>> 24) & 0xFF) == 0) continue;
                    // Ropa negra (Saiyan/Humano): la sangre roja sobre negro se ve como un parche pintado encima de la
                    // tela. Los píxeles muy oscuros se saltan, así que las manchas quedan solo en piel y tela clara.
                    if (Math.max(px & 0xFF, Math.max((px >> 8) & 0xFF, (px >> 16) & 0xFF)) < DARK_CUTOFF) continue;
                    float v = 0.7f * valueNoise(x / (3f * scale), y / (3f * scale), seed)
                            + 0.3f * hash01(x, y, seed + 11);
                    if (v >= thr) continue;
                    int rgb = palette[Math.floorMod(hash(x, y, seed + 29), palette.length)];
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    out.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
                }
            }
        } catch (Exception e) {
            Zenkai.LOGGER.warn("[Zenkai] No se pudo generar la sangre de {}: {}", base, e.toString());
            MISSING.add(key);
            return null;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "dynamic/blood_" + counter++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
        REGISTERED.add(id);
        CACHE.put(key, id);
        return id;
    }

    /** Libera las texturas dinámicas (recarga de recursos, salir del mundo). */
    public static void clear() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation id : REGISTERED) tm.release(id);
        REGISTERED.clear();
        CACHE.clear();
        MISSING.clear();
    }

    // ── Ruido (misma función en el cálculo de umbrales: ver .claude/mod-updates/0.6.0-dev-notes.md) ──

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static float hash01(int x, int y, int seed) {
        return (hash(x, y, seed) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float smooth(float t) { return t * t * (3f - 2f * t); }

    private static float valueNoise(float fx, float fy, int seed) {
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float tx = smooth(fx - x0), ty = smooth(fy - y0);
        float a = hash01(x0, y0, seed), b = hash01(x0 + 1, y0, seed);
        float c = hash01(x0, y0 + 1, seed), d = hash01(x0 + 1, y0 + 1, seed);
        return (a + (b - a) * tx) * (1f - ty) + (c + (d - c) * tx) * ty;
    }
}
