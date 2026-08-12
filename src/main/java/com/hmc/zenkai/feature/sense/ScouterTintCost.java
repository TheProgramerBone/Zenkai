package com.hmc.zenkai.feature.sense;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;

/**
 * Cuánto cuesta teñir el scouter en el banco.
 *
 * EL ALGORITMO ESTÁ EN JAVA, LA ECONOMÍA EN DATAPACK. El datapack no lista un precio por
 * color —serían 16 millones de entradas— sino los cuatro números que gobiernan la curva:
 * energía, mínimo, máximo y escala. La tabla de tintes es fija y son los 16 de vanilla, a
 * propósito: abrirla al registro entero haría que el mismo color costase cosas distintas
 * según qué mods tenga el modpack.
 *
 * CÓMO SE DECIDE EL PRECIO:
 *   color elegido -> tinte vanilla más cercano -> cantidad según lo saturado que sea.
 * La cantidad sale de la SATURACIÓN y no del brillo porque es la saturación la que dice
 * "cuánto pigmento hay aquí": un gris y un blanco son el mismo trabajo, un rojo puro no.
 *
 * La distancia usa pesos 2/4/3 sobre R/G/B en vez de distancia euclídea plana. El ojo
 * distingue mucho mejor el verde que el azul, y sin los pesos un turquesa acaba pidiendo
 * tinte azul cuando cualquiera diría que es cian.
 */
public record ScouterTintCost(int energy, int minMaterials, int maxMaterials, float scale) {

    public static final ScouterTintCost DEFAULT = new ScouterTintCost(1_000, 1, 3, 1.0f);

    /** Saneado al cargar, no al consultar: lo que se guarda, lo que viaja y lo que pinta el
     *  tooltip son el mismo objeto, así que cliente y servidor no pueden discrepar. */
    public static ScouterTintCost clamped(int energy, int min, int max, float scale) {
        int lo = Math.max(0, min);
        int hi = Math.max(lo, max);
        return new ScouterTintCost(Math.max(0, energy), lo, hi,
                Math.max(0.01f, Math.min(8f, scale)));
    }

    // ── Registro ─────────────────────────────────────────────────────────────

    private static volatile ScouterTintCost CURRENT = DEFAULT;

    public static ScouterTintCost get() { return CURRENT; }

    public static void replace(ScouterTintCost v) { CURRENT = v == null ? DEFAULT : v; }

    // ── Cálculo ──────────────────────────────────────────────────────────────

    /** Lo que hay que pagar por un color concreto. */
    public record Quote(DyeColor dye, int count, int energy) {
        public Item item() { return DyeItem.byColor(dye); }

        public int countIn(Inventory inv) {
            Item want = item();
            int n = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(want)) n += s.getCount();
            }
            return n;
        }

        public boolean canAfford(Inventory inv) { return countIn(inv) >= count; }

        /** Cobra. Llamar SOLO tras canAfford y SOLO en servidor. */
        public void consume(Inventory inv) {
            int left = count;
            Item want = item();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(want)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
            inv.setChanged();
        }
    }

    /**
     * El precio de este color. Se llama en el cliente para el tooltip mientras se arrastra el
     * picker y en el servidor para cobrar: mismo método, mismos datos sincronizados, así que
     * no puede enseñar un precio y cobrar otro.
     */
    public Quote quote(int rgb) {
        DyeColor dye = nearestDye(rgb);
        float[] hsv = hsv(rgb);
        int span = maxMaterials - minMaterials;
        int extra = Math.round(hsv[1] * span * scale);
        int count = Math.max(minMaterials, Math.min(maxMaterials, minMaterials + extra));
        return new Quote(dye, count, energy);
    }

    /** ⚠ VERIFICAR 1.21.1: DyeColor#getTextureDiffuseColor() -> int 0xRRGGBB. */
    public static DyeColor nearestDye(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        DyeColor best = DyeColor.WHITE;
        long bestD = Long.MAX_VALUE;
        for (DyeColor d : DyeColor.values()) {
            int c = d.getTextureDiffuseColor() & 0xFFFFFF;
            long dr = r - ((c >> 16) & 0xFF);
            long dg = g - ((c >> 8) & 0xFF);
            long db = b - (c & 0xFF);
            long dist = 2 * dr * dr + 4 * dg * dg + 3 * db * db;
            if (dist < bestD) { bestD = dist; best = d; }
        }
        return best;
    }

    private static float[] hsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float s = max == 0f ? 0f : (max - min) / max;
        return new float[]{0f, s, max};
    }

    // ── Red ──────────────────────────────────────────────────────────────────

    public static void encode(RegistryFriendlyByteBuf buf, ScouterTintCost c) {
        buf.writeVarInt(c.energy());
        buf.writeVarInt(c.minMaterials());
        buf.writeVarInt(c.maxMaterials());
        buf.writeFloat(c.scale());
    }

    public static ScouterTintCost decode(RegistryFriendlyByteBuf buf) {
        return new ScouterTintCost(buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readFloat());
    }
}