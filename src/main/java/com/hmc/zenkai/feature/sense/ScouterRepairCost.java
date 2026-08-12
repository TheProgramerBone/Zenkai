package com.hmc.zenkai.feature.sense;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Coste de reparar un scouter roto. Fuente ÚNICA para las dos vías: el yunque y el banco.
 * Antes esto vivía en ScouterRepair como IRON_COUNT y ANVIL_LEVELS, o sea coste en Java
 * mientras lo demás del banco ya venía de datapack. Ahora sale de
 * data/zenkai/zenkai_scouter_repair.json y viaja al cliente en el mismo paquete que los
 * costes de las mejoras.
 * LAS DOS VÍAS COMPARTEN MATERIAL Y SE DIFERENCIAN EN LA MONEDA:
 *   Yunque — materiales + niveles de experiencia. Sin FE: en un yunque no hay corriente.
 *   Banco  — materiales + FE. Sin niveles.
 * Por eso `cost` (materiales + energía) es un ScouterUpgradeCost normal: el block entity lo
 * cobra con exactamente el mismo código que cobra una mejora, y el yunque solo mira los
 * materiales y añade los niveles.
 * SUELO COMPILADO. El datapack puede subir el precio, no bajarlo: si el JSON falta, está
 * vacío o pide menos, se usa el mínimo de abajo. Es la única excepción a "un JSON incompleto
 * lo deja gratis" que rige en las mejoras, y tiene motivo: una mejora gratis es un
 * desequilibrio, pero una reparación gratis borra la consecuencia de romper el scouter, que
 * es media mecánica de sobrecarga.
 * OJO con el alcance del suelo: sobre `energy` y `anvilLevels` se puede comparar y se aplica
 * un max(). Sobre los MATERIALES no, porque "1 lingote de netherita" es más caro que "3 de
 * hierro" y ningún criterio automático lo sabe. Ahí la regla es binaria: si el JSON declara
 * materiales, mandan los suyos; si no declara ninguno, se usan los de por defecto.
 */
public record ScouterRepairCost(ScouterUpgradeCost cost, int anvilLevels) {

    // ── Mínimos ──────────────────────────────────────────────────────────────
    public static final int MIN_ENERGY = 6_000;
    public static final int MIN_ANVIL_LEVELS = 5;

    private static final ResourceLocation IRON_TAG =
            ResourceLocation.fromNamespaceAndPath("c", "ingots/iron");
    private static final ResourceLocation REDSTONE_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "redstone");

    private static final List<ScouterUpgradeCost.Material> DEFAULT_MATERIALS = List.of(
            new ScouterUpgradeCost.Material(IRON_TAG, true, 3),
            new ScouterUpgradeCost.Material(REDSTONE_ID, false, 1));

    public static final ScouterRepairCost DEFAULT = new ScouterRepairCost(
            new ScouterUpgradeCost(DEFAULT_MATERIALS, MIN_ENERGY), MIN_ANVIL_LEVELS);

    /**
     * Aplica el suelo. Se llama al cargar el datapack, NO al consultar: así lo que se guarda,
     * lo que viaja por red y lo que ve el tooltip son el mismo objeto ya saneado, y no hay
     * forma de que el cliente pinte un precio y el servidor cobre otro.
     */
    public static ScouterRepairCost clamped(List<ScouterUpgradeCost.Material> materials,
                                            int energy, int anvilLevels) {
        List<ScouterUpgradeCost.Material> mats =
                (materials == null || materials.isEmpty()) ? DEFAULT_MATERIALS : List.copyOf(materials);
        return new ScouterRepairCost(
                new ScouterUpgradeCost(mats, Math.max(MIN_ENERGY, energy)),
                Math.max(MIN_ANVIL_LEVELS, anvilLevels));
    }

    /** Materiales, para el yunque y para los tooltips. */
    public List<ScouterUpgradeCost.Material> materials() { return cost.materials(); }

    /** FE que cuesta en el banco. El yunque no gasta. */
    public int energy() { return cost.energy(); }

    // ── Registro (reemplazado en cada /reload y en cada sync) ────────────────

    private static volatile ScouterRepairCost CURRENT = DEFAULT;

    public static ScouterRepairCost get() { return CURRENT; }

    public static void replace(ScouterRepairCost value) {
        CURRENT = value == null ? DEFAULT : value;
    }

    // ── Red ──────────────────────────────────────────────────────────────────

    public static void encode(RegistryFriendlyByteBuf buf, ScouterRepairCost c) {
        ScouterUpgradeCost.encode(buf, c.cost());
        buf.writeVarInt(c.anvilLevels());
    }

    public static ScouterRepairCost decode(RegistryFriendlyByteBuf buf) {
        ScouterUpgradeCost cost = ScouterUpgradeCost.decode(buf);
        return new ScouterRepairCost(cost, buf.readVarInt());
    }

    /** Primer ítem que representa cada material, para pintar iconos sin repetir lógica.
     *  ⚠ VERIFICAR 1.21.1: BuiltInRegistries.ITEM.getTag(TagKey) dentro de displayItems(). */
    public static Item iconOf(ScouterUpgradeCost.Material m) {
        List<Item> items = m.displayItems();
        return items.isEmpty() ? Items.BARRIER : items.getFirst();
    }

    /** Sin uso directo hoy; existe para que TagKey no se resuelva a mano en dos sitios. */
    public static TagKey<Item> ironTag() { return TagKey.create(Registries.ITEM, IRON_TAG); }
}