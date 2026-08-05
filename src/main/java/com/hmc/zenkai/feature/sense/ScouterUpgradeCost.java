package com.hmc.zenkai.feature.sense;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Coste de UN nivel de UNA mejora. Es lo ÚNICO que el datapack controla (más el coste de
 * energía, que hoy se parsea y se enseña pero no se cobra: no hay sistema de energía todavía).
 *
 * No se usa Ingredient de vanilla: aquí hace falta cantidad, comprobación contra el inventario
 * y una lista de items para pintar el tooltip, y las tres cosas salen más limpias con un
 * record propio que peleando con las Value de Ingredient.
 */
public record ScouterUpgradeCost(List<Material> materials, int energy) {

    public static final ScouterUpgradeCost FREE = new ScouterUpgradeCost(List.of(), 0);

    /** Un item concreto o un tag, con cantidad. */
    public record Material(ResourceLocation id, boolean isTag, int count) {

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            return isTag
                    ? stack.is(TagKey.create(Registries.ITEM, id))
                    : stack.is(BuiltInRegistries.ITEM.get(id));
        }

        /** Cuántas unidades tiene el jugador. */
        public int countIn(Inventory inv) {
            int n = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (matches(s)) n += s.getCount();
            }
            return n;
        }

        /** Items que representan este material, para el icono/nombre del tooltip.
         *  ⚠ VERIFICAR en 1.21.1: BuiltInRegistries.ITEM.getTag(TagKey) -> Optional<HolderSet.Named<Item>>. */
        public List<Item> displayItems() {
            if (!isTag) {
                Item it = BuiltInRegistries.ITEM.get(id);
                return it == null ? List.of() : List.of(it);
            }
            return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                    .map(holders -> holders.stream().map(h -> h.value()).toList())
                    .orElse(List.of());
        }
    }

    // ── Registro (reemplazado en cada /reload y en cada sync) ─────────────────

    private static volatile Map<ScouterUpgrade, List<ScouterUpgradeCost>> REGISTRY =
            new EnumMap<>(ScouterUpgrade.class);

    public static void replaceAll(Map<ScouterUpgrade, List<ScouterUpgradeCost>> costs) {
        REGISTRY = new EnumMap<>(costs);
    }

    public static Map<ScouterUpgrade, List<ScouterUpgradeCost>> snapshot() {
        return new EnumMap<>(REGISTRY);
    }

    /**
     * Coste de SUBIR al nivel indicado (1..maxLevel). FREE si el datapack no lo define:
     * un JSON incompleto deja la mejora gratis, no rompe la partida ni la esconde. El log
     * del cargador ya avisa de lo que falta.
     */
    public static ScouterUpgradeCost forLevel(ScouterUpgrade u, int level) {
        List<ScouterUpgradeCost> list = REGISTRY.get(u);
        if (list == null) return FREE;
        int i = level - 1;
        return (i >= 0 && i < list.size()) ? list.get(i) : FREE;
    }

    // ── Consulta y cobro (embudo único: nadie descuenta items por su cuenta) ──

    public boolean canAfford(Inventory inv) {
        for (Material m : materials) {
            if (m.countIn(inv) < m.count()) return false;
        }
        return true;
    }

    /** Cobra los materiales. Llamar SOLO tras canAfford y SOLO en servidor. */
    public void consume(Inventory inv) {
        for (Material m : materials) {
            int left = m.count();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!m.matches(s)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        inv.setChanged();
    }

    // ── Red ──────────────────────────────────────────────────────────────────

    public static void encode(RegistryFriendlyByteBuf buf, ScouterUpgradeCost c) {
        buf.writeVarInt(c.materials().size());
        for (Material m : c.materials()) {
            buf.writeResourceLocation(m.id());
            buf.writeBoolean(m.isTag());
            buf.writeVarInt(m.count());
        }
        buf.writeVarInt(c.energy());
    }

    public static ScouterUpgradeCost decode(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Material> mats = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            mats.add(new Material(buf.readResourceLocation(), buf.readBoolean(), buf.readVarInt()));
        }
        return new ScouterUpgradeCost(List.copyOf(mats), buf.readVarInt());
    }
}