package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.*;
import java.util.function.Supplier;

/**
 * Una sola pestaña con barra de búsqueda. Son ~157 entradas, así que el scroll es largo; la
 * búsqueda es lo que hace que eso no importe, porque el jugador que sabe lo que quiere escribe
 * en vez de rodar la rueda.
 *
 * El orden tiene DOS niveles y es lo único que estructura la pestaña, ya que la rejilla de
 * vainilla no admite cabeceras de sección:
 *   1. ÍTEMS antes que BLOQUES. Es el corte más visible sin dibujar nada.
 *   2. Dentro de cada mitad, por FAMILIA (ver {@link #FAMILIES}), etapa y variante, de forma
 *      que un material y lo que se fabrica con él salen seguidos.
 *
 * Nada de esto es una lista escrita a mano de 157 entradas: se decide por reglas sobre el id de
 * registro, así que un bloque nuevo cae solo donde le toca. Es justo el fallo que tiene
 * cualquier lista manual a los tres meses.
 */
public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Zenkai.MOD_ID);

    // ── Pestaña ──────────────────────────────────────────────────────────────

    public static final Supplier<CreativeModeTab> CREATIVE_MODE_ITEMS = CREATIVE_MODE_TAB.register("zenkai_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.zenkai"))
                    .icon(() -> new ItemStack(ModItems.SENZU_BEAN.get()))
                    // ⚠ API a verificar al compilar: withSearchBar() en 1.21.1. La caja se
                    // dibuja sobre el fondo estándar; si se solapa con la primera fila hará
                    // falta withBackgroundLocation() y una textura propia.
                    .withSearchBar()
                    .displayItems((params, output) -> fill(output))
                    .build());

    // ── Excepciones ──────────────────────────────────────────────────────────

    /**
     * Armaduras con RACE_ARMOR_MATERIAL que SÍ se muestran. El material se usa para las pieles
     * de raza (decenas de piezas que el jugador nunca coge a mano) y de paso para estas tres,
     * que sí son equipo de verdad.
     */
    private static Set<Item> visibleRaceArmor() {
        return Set.of(
                ModItems.HALO.get(),
                ModItems.WEIGHTED_STRAPS.get(),
                ModItems.WEIGHTED_CAPE.get());
    }

    /** Nunca se muestran: técnicos, de render, o colocados solo por estructura. */
    private static Set<Item> hidden() {
        return Set.of(
                ModItems.ALL_DRAGON_BALLS_ITEM.get(),
                ModItems.HAIR_1.get(),
                ModItems.SSJ1_HAIR1.get(),
                ModItems.KI_BLADE.get(),
                ModItems.KI_SCYTHE.get(),
                ModBlocks.HTC_PORTAL.get().asItem());
    }

    // ── Relleno ──────────────────────────────────────────────────────────────

    private static void fill(CreativeModeTab.Output output) {
        Set<Item> hidden = hidden();
        Set<Item> visibleArmor = visibleRaceArmor();
        boolean mekanism = ModList.get().isLoaded("mekanism");

        List<Item> items = new ArrayList<>();

        for (Supplier<? extends Item> supplier : ModItems.ITEMS.getEntries()) {
            Item item = supplier.get();
            if (hidden.contains(item)) continue;

            // Pieles de raza: decenas de piezas que solo existen para renderizarse puestas.
            if (isRaceArmor(item) && !visibleArmor.contains(item)) continue;

            // Registrado siempre —el registro no admite condiciones y debe coincidir entre
            // cliente y servidor— pero invisible si el mod que le da sentido no está.
            if (!mekanism && item == ModItems.DIRTY_RAW_KATCHIN.get()) continue;   // ⚠ ítem nuevo

            items.add(item);
        }

        items.sort(order());
        items.forEach(output::accept);
    }

    // ── Orden ────────────────────────────────────────────────────────────────

    /**
     * Familias, EN EL ORDEN en que aparecen dentro de su mitad. La primera cuyo nombre esté
     * contenido en el id gana, así que el orden de esta lista importa dos veces: decide la
     * posición y desempata solapes.
     *
     * Lo que no encaje en ninguna cae al final, agrupado por su propio id. Es un fallback
     * sano: un bloque nuevo nunca desaparece, solo queda al fondo hasta que lo clasifiques.
     */
    private static final List<String> FAMILIES = List.of(
            "dragon_ball", "katchin", "structural_concrete", "ajisa", "sacred_stone",
            "namek_crystal", "energy_crystal", "namekian", "scouter", "circuit",
            "spawn_egg", "htc", "kintoun");

    /** Sufijos de pieza y su orden dentro de una variante. El orden es el de vainilla al
     *  mirar un set de construcción: bloque, escaleras, losa, muro, y luego lo redstone. */
    private static final List<Map.Entry<String, Integer>> PARTS = List.of(
            Map.entry("_stairs", 4), Map.entry("_slab", 5), Map.entry("_wall", 6),
            Map.entry("_fence_gate", 8), Map.entry("_fence", 7), Map.entry("_door", 9),
            Map.entry("_trapdoor", 10), Map.entry("_pressure_plate", 11), Map.entry("_button", 12));

    /**
     * Corte principal: 0 = ítems, 1 = bloques.
     *
     * Es lo más parecido a una sección que permite la rejilla de vainilla. Va por delante de la
     * familia a propósito: se ve de un vistazo dónde acaban las cosas que se llevan en la mano
     * y empiezan las que se colocan, aunque eso separe el lingote de katchin de su bloque.
     */
    private static int half(Item item) {
        return item instanceof BlockItem ? 1 : 0;
    }

    private static int familyIndex(String path) {
        for (int i = 0; i < FAMILIES.size(); i++) {
            if (path.contains(FAMILIES.get(i))) return i;
        }
        return FAMILIES.size();
    }

    /** Nombre de familia, o el propio id para los que no encajan (así al menos se agrupan
     *  consigo mismos en vez de mezclarse todos al final). */
    private static String familyName(String path) {
        for (String f : FAMILIES) {
            if (path.contains(f)) return f;
        }
        return path;
    }

    private static String partSuffix(String path) {
        for (var e : PARTS) {
            if (path.endsWith(e.getKey())) return e.getKey();
        }
        return null;
    }

    private static int partRank(String path) {
        for (var e : PARTS) {
            if (path.endsWith(e.getKey())) return e.getValue();
        }
        return 0;
    }

    /** Etapa dentro de la familia: material suelto, mena, bloque base, decoración. */
    private static int stage(Item item, String path) {
        if (!(item instanceof BlockItem)) return 0;
        if (path.contains("_ore")) return 1;
        if (path.endsWith("_block") || path.endsWith("_planks")
                || path.endsWith("_log") || path.endsWith("_wood")) return 2;
        return 3;
    }

    /** Desempate dentro de la etapa: la mena normal antes que la de deepslate, y el tronco
     *  antes que las tablas. Sin esto salía deepslate_katchin_ore delante de katchin_ore. */
    private static int stageSub(Item item, String path) {
        int stage = stage(item, path);
        if (stage == 1) return path.startsWith("deepslate") ? 1 : 0;
        if (stage == 2) {
            if (path.endsWith("_log") || path.endsWith("_wood")) return 0;
            return path.endsWith("_planks") ? 1 : 2;
        }
        return 0;
    }

    /**
     * Nombre de la variante, sin la pieza ni los prefijos que no cambian el material.
     * Es lo que mantiene juntos "cut_katchin" y sus escaleras, y "ajisa_log" con
     * "stripped_ajisa_log". La 's' final se recorta para que "sacred_stone_bricks" y
     * "sacred_stone_brick_stairs" cuenten como la misma variante.
     */
    private static String variant(String path) {
        String suffix = partSuffix(path);
        String v = suffix != null ? path.substring(0, path.length() - suffix.length()) : path;
        if (path.endsWith("_ore")) v = path.substring(0, path.length() - 4);
        if (v.startsWith("deepslate_")) v = v.substring("deepslate_".length());
        if (v.startsWith("stripped_")) v = v.substring("stripped_".length());
        return v.endsWith("s") ? v.substring(0, v.length() - 1) : v;
    }

    /**
     * Mitad → familia → etapa → variante → pieza → id.
     *
     * El id al final no es adorno: garantiza que el orden sea total y estable. Sin él, dos
     * entradas que empaten en lo demás podrían salir en orden distinto entre sesiones.
     *
     * Se ordena por el path del id y NO por el nombre traducido: el nombre depende del idioma
     * cargado y esto también corre en servidor, así que ordenar por él daría pestañas distintas
     * según quién mire.
     */
    private static Comparator<Item> order() {
        return Comparator
                .comparingInt(ModCreativeModeTabs::half)
                .thenComparingInt(i -> familyIndex(path(i)))
                .thenComparing(i -> familyName(path(i)))
                .thenComparingInt(i -> stage(i, path(i)))
                .thenComparingInt(i -> stageSub(i, path(i)))
                .thenComparing(i -> variant(path(i)))
                .thenComparingInt(i -> partRank(path(i)))
                .thenComparing(ModCreativeModeTabs::path);
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private static String path(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id.getPath();
    }

    /** True si el item es una armadura cuyo material es RACE_ARMOR_MATERIAL. */
    private static boolean isRaceArmor(Item item) {
        if (!(item instanceof ArmorItem armor)) return false;
        return armor.getMaterial().equals(ModArmorMaterials.RACE_ARMOR_MATERIAL);
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}