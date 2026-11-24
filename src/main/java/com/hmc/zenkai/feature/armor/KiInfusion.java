package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.item.InfusedKatchinItem;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Infusión de Ki del katchin y de las armaduras (Fase 5c de la 0.7.0; rework del 2026-11-23, sección «ANTES DE PASAR A
 * FASE 6» de pendientes-0.7.0).
 *
 * NIVELES (5): sin infundir / Normal (el enum se llama BASE: «Ki Base» pasó a «Ki Normal» solo en la traducción, sin
 * renombrar ids) / Súper / Divino / Legendario. El «escalón» ({@link #tier}) es 0 para sin infundir y ordinal + 1 para
 * el resto: así «Base» deja de ser a la vez el escalón de ki y el punto de partida de cualquier pieza.
 *
 * MATERIAL: el katchin infundido son ítems PROPIOS ({@link InfusedKatchinItem}: lingote, hilo y tela por nivel, y un bloque
 * por nivel). La infusora convierte el lingote normal, o uno infundido de nivel inferior, en el lingote del nivel del
 * fluido, según sus recetas por datapack ({@link KiInfusionRecipe}). Sin mezcla de niveles en hilo, tela, bloque ni en la máquina de fabricación.
 *
 * ARMADURA: el componente {@code zenkai:ki_infusion} vive SOLO en la armadura. Lo pone la máquina de fabricación al
 * fabricar con material infundido, o su pestaña Infundir sobre una pieza de cualquier mod. Qué da: más durabilidad,
 * menos coste de FE y fluido al imbuir y un TOPE de puntos imbuidos más alto ({@link #pieceCap}). El aspecto no cambia.
 *
 * Tipo de Ki para imbuir una pieza infundida = el de su infusión (decisión del usuario 2026-11-22: «el fluido con el que
 * se hizo el material»); manda sobre la receta {@code zenkai:armor_imbuing} y sobre el tipo automático.
 *
 * Cifras de partida «moderadas» elegidas por el usuario el 2026-11-22; constantes por la regla de pocas opciones. Los
 * topes por escalón van en ServerConfig ({@code imbue.piece_caps}) porque los fija el usuario jugando.
 */
public final class KiInfusion {
    private KiInfusion() {}

    /**
     * Piezas cuyo tope sale SOLO de la infusión, sin el techo de su propio nivel (decisión del usuario 2026-11-23: los
     * sets de Zenkai, que con la regla del más bajo se quedaban en tope Normal o Súper por su receta). Un addon mete aquí
     * su set.
     */
    public static final TagKey<Item> UNCAPPED = tag("infusion_uncapped");
    /** Lingotes de katchin de cualquier nivel (normal incluido): material de las recetas de fabricación metálicas. */
    public static final TagKey<Item> INGOTS = tag("katchin_ingots");
    /** Hilos y telas de katchin de cualquier nivel: material de las recetas de fabricación de ropa. */
    public static final TagKey<Item> THREADS = tag("katchin_threads");
    public static final TagKey<Item> CLOTHS = tag("katchin_cloths");

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path));
    }

    /**
     * mB de fluido por cada lingote de la pestaña Infundir de la Estación de imbuido. La Infusora usa ya el de su receta
     * {@code zenkai:ki_infusion} (las de serie llevan este mismo valor, y FE/tiempo los de abajo como defecto del codec).
     */
    public static final int MB_PER_ITEM = 250;
    /** FE por lingote, repartida en {@link #TICKS}. */
    public static final int FE_PER_ITEM = 10_000;
    /** Ticks por lingote sin mejoras de velocidad (5 s). */
    public static final int TICKS = 100;

    // ── Niveles ──────────────────────────────────────────────────────────────

    /** Escalón de un nivel: 0 sin infundir, 1 Normal … 4 Legendario. */
    public static int tier(@Nullable KiEnergyType t) { return t == null ? 0 : t.ordinal() + 1; }

    /** Infusión de una ARMADURA (componente), o null si no está infundida. */
    @Nullable
    public static KiEnergyType of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ModDataComponents.KI_INFUSION.get());
    }

    /** Nivel de un MATERIAL de katchin (por su ítem), o null si es katchin normal o no es katchin infundido. */
    @Nullable
    public static KiEnergyType materialLevel(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof InfusedKatchinItem i ? i.level() : null;
    }

    public static Item ingot(@Nullable KiEnergyType t) {
        return t == null ? ModItems.KATCHIN_INGOT.get() : ModItems.INFUSED_KATCHIN_INGOTS.get(t).get();
    }

    public static Item thread(@Nullable KiEnergyType t) {
        return t == null ? ModItems.KATCHIN_THREAD.get() : ModItems.INFUSED_KATCHIN_THREADS.get(t).get();
    }

    public static Item cloth(@Nullable KiEnergyType t) {
        return t == null ? ModItems.KATCHIN_CLOTH.get() : ModItems.INFUSED_KATCHIN_CLOTHS.get(t).get();
    }

    public static Item block(@Nullable KiEnergyType t) {
        return t == null ? ModBlocks.KATCHIN_BLOCK.get().asItem() : ModBlocks.INFUSED_KATCHIN_BLOCKS.get(t).get().asItem();
    }

    /** ¿Es un lingote de katchin (normal o infundido)? */
    public static boolean isKatchinIngot(ItemStack s) {
        return !s.isEmpty() && (s.is(ModItems.KATCHIN_INGOT.get())
                || s.getItem() instanceof InfusedKatchinItem i && i.form() == InfusedKatchinItem.Form.INGOT);
    }

    // ── Efectos ──────────────────────────────────────────────────────────────

    /** Multiplicador de durabilidad de la armadura de ese tipo. */
    public static double durabilityMult(@Nullable KiEnergyType t) {
        if (t == null) return 1.0;
        return switch (t) {
            case BASE -> 1.25;
            case SUPER -> 1.5;
            case DIVINE -> 2.0;
            case LEGENDARY -> 2.5;
        };
    }

    /** Multiplicador del coste de imbuir (FE y fluido; la sangre no cambia) de una pieza de ese tipo. */
    public static double imbueCostMult(@Nullable KiEnergyType t) {
        if (t == null) return 1.0;
        return switch (t) {
            case BASE -> 0.9;
            case SUPER -> 0.8;
            case DIVINE -> 0.65;
            case LEGENDARY -> 0.5;
        };
    }

    /** Aplica el descuento de la infusión de la pieza a un coste (hacia arriba, nunca por debajo de 1 si había coste). */
    public static long discounted(long cost, @Nullable KiEnergyType t) {
        if (cost <= 0) return cost;
        return Math.max(1L, (long) Math.ceil(cost * imbueCostMult(t) - 1e-9));
    }

    /**
     * Pone (o sube) la infusión de una armadura y recalcula su durabilidad sobre la de FÁBRICA del ítem (no sobre la que
     * ya tenía: subir de Súper a Divino da ×2, no ×1,5×2). Conserva lo imbuido y el desgaste. Sin tipo, la deja tal cual.
     * La durabilidad va en el componente MAX_DAMAGE del propio stack: vanilla la respeta en el desgaste y en la barra.
     */
    public static ItemStack applyToArmor(ItemStack piece, @Nullable KiEnergyType t) {
        if (t == null || piece.isEmpty()) return piece;
        ItemStack out = piece.copy();
        out.set(ModDataComponents.KI_INFUSION.get(), t);
        int base = out.getPrototype().getOrDefault(DataComponents.MAX_DAMAGE, 0);
        if (base > 0) out.set(DataComponents.MAX_DAMAGE, (int) Math.round(base * durabilityMult(t)));
        return out;
    }

    // ── Tope de puntos por pieza ─────────────────────────────────────────────

    /**
     * Escalón del tope de la pieza: el MÁS BAJO entre el de su infusión y el de su propio nivel (tipo automático por
     * armadura + dureza, {@link ArmorImbuingRecipe#autoTier}), para que una pieza floja infundida en Legendario no
     * aguante 400 puntos. Las del tag {@link #UNCAPPED} (sets de Zenkai) usan solo la infusión.
     */
    public static int capTier(ItemStack piece) {
        int inf = tier(of(piece));
        if (piece.is(UNCAPPED)) return inf;
        return Math.min(inf, tier(ArmorImbuingRecipe.autoTier(piece)));
    }

    /** Puntos totales imbuidos que admite la pieza (ServerConfig {@code imbue.piece_caps}). */
    public static int pieceCap(ItemStack piece) { return ServerConfig.imbuePieceCap(capTier(piece)); }

    /** Puntos totales imbuidos de la pieza (suma de los cinco atributos). */
    public static int usedPoints(ItemStack piece) { return total(AttributeTemplates.imbued(piece)); }

    public static int total(ImbuedAttributes a) {
        int n = 0;
        for (ZenkaiAttributes at : ImbuedAttributes.IMBUABLE) n += a.get(at);
        return n;
    }

    /** ¿Subiría el tope si se infundiera más? (pestaña Atributos: «Infunde la pieza para subir el tope»). */
    public static boolean capRaisable(ItemStack piece) {
        KiEnergyType inf = of(piece);
        if (inf == KiEnergyType.LEGENDARY) return false;
        if (piece.is(UNCAPPED)) return true;
        return tier(inf) < tier(ArmorImbuingRecipe.autoTier(piece));
    }

    // ── Pestaña Infundir de la máquina de fabricación ────────────────────────

    /** Lingotes del nivel que cuesta infundir una pieza según su ranura (propuesta del diseño: 5/8/7/4). */
    public static int ingotsFor(ItemStack piece) {
        Equipable eq = Equipable.get(piece);
        if (eq == null) return 0;
        EquipmentSlot slot = eq.getEquipmentSlot();
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 8;
            case LEGS -> 7;
            case FEET -> 4;
            default -> 0;
        };
    }
}
