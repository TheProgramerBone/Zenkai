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
 *   1. OBJETOS antes que BLOQUES. Es el corte más visible sin dibujar nada.
 *   2. Dentro de cada mitad, el orden de REGISTRO: tal cual se fueron añadiendo los campos en
 *      {@code ModItems.java} y luego en {@code ModBlocks.java} (el ítem de bloque de cada
 *      bloque se registra justo detrás de él, así que ambos archivos comparten un único
 *      registro — {@link ModItems#ITEMS} — y su orden de inserción real ya es "ModItems
 *      primero, ModBlocks después"). No hace falta leer nada de {@code ModBlockEntities}: ese
 *      archivo no registra ítems ni bloques nuevos, solo el {@code BlockEntityType} de bloques
 *      que ya existen, así que no aporta ninguna posición.
 *
 * La única excepción es el grupo de las esferas del dragón (ver {@link #dragonBallsGroup()}):
 * son BlockItem de verdad, pero el jugador las vive como coleccionables, no como algo que
 * coloca, así que se fuerzan a la mitad de objetos y se agrupan entre ellas en vez de quedar
 * sueltas donde les tocaría por registro.
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

    // ── Excepciones de visibilidad ──────────────────────────────────────────

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

    // ── Excepción de sección: esferas del dragón ────────────────────────────

    /**
     * Cada esfera (namekiana o no) es un bloque de verdad —tiene luz, colisión propia, y se
     * coloca— pero para el jugador es una pieza que se recoge, no material de construcción.
     * Se listan en el orden en que deben salir juntas: el de sus bloques en
     * {@code ModBlocks.java}, con {@code ALL_DRAGON_BALLS_ITEM} (que vive en
     * {@code ModItems.java}, no en ModBlocks, porque {@code ALL_DRAGON_BALLS} se registra sin
     * BlockItem automático) intercalado donde cae su propio bloque.
     *
     * Hoy este grupo está completo dentro de {@link #hidden()}, así que no se ve — pero la
     * regla de sección/orden queda correcta por si alguna vez se destapa.
     */
    private static List<Item> dragonBallsGroup() {
        return List.of(
                ModBlocks.DRAGON_BALL_STONE.get().asItem(),
                ModBlocks.DRAGON_BALL_1.get().asItem(),
                ModBlocks.DRAGON_BALL_2.get().asItem(),
                ModBlocks.DRAGON_BALL_3.get().asItem(),
                ModBlocks.DRAGON_BALL_4.get().asItem(),
                ModBlocks.DRAGON_BALL_5.get().asItem(),
                ModBlocks.DRAGON_BALL_6.get().asItem(),
                ModBlocks.DRAGON_BALL_7.get().asItem(),
                ModItems.ALL_DRAGON_BALLS_ITEM.get(),
                ModBlocks.NAMEK_DRAGON_BALL_STONE.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_1.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_2.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_3.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_4.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_5.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_6.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_7.get().asItem());
    }

    /** Posición de cada esfera dentro de {@link #dragonBallsGroup()}, para mantenerlas juntas
     *  y en ese orden dentro de la mitad de objetos. */
    private static Map<Item, Integer> dragonBallRank() {
        List<Item> group = dragonBallsGroup();
        Map<Item, Integer> rank = new HashMap<>();
        for (int i = 0; i < group.size(); i++) rank.put(group.get(i), i);
        return rank;
    }

    /** Semilla namekiana: técnicamente ItemNameBlockItem (coloca el cultivo), pero es una
     *  semilla — un objeto — y ya vive en su sitio natural dentro de ModItems.java, así que
     *  solo hace falta moverla de mitad, no de posición. */
    private static boolean isForcedItem(Item item, Map<Item, Integer> dragonBallRank) {
        return dragonBallRank.containsKey(item) || item == ModItems.NAMEKIAN_HERB_SEEDS.get();
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
            if (!mekanism && item == ModItems.DIRTY_RAW_KATCHIN.get()) continue;

            items.add(item);
        }

        items.sort(order(insertionOrder(), dragonBallRank()));
        items.forEach(output::accept);
    }

    // ── Orden ────────────────────────────────────────────────────────────────

    /** Índice de inserción de cada ítem dentro de {@link ModItems#ITEMS}. Como
     *  {@code registerBlockItem} en ModBlocks.java registra el BlockItem de cada bloque ahí
     *  mismo, este único mapa ya refleja "orden de ModItems.java, luego orden de
     *  ModBlocks.java" sin tener que combinar dos registros a mano. */
    private static Map<Item, Integer> insertionOrder() {
        Map<Item, Integer> order = new HashMap<>();
        int i = 0;
        for (Supplier<? extends Item> supplier : ModItems.ITEMS.getEntries()) {
            order.put(supplier.get(), i++);
        }
        return order;
    }

    /**
     * Mitad → orden de registro (con las esferas del dragón ancladas juntas).
     *
     * Mitad: 0 = objetos, 1 = bloques. {@code isForcedItem} manda las esferas y la semilla
     * namekiana a objetos aunque sean BlockItem.
     *
     * Dentro de cada mitad, las esferas usan como posición la que le tocaría a
     * ALL_DRAGON_BALLS_ITEM (así caen juntas en vez de dispersarse por su propio índice de
     * registro) y se desempatan por su rango dentro del grupo; lo demás usa directamente
     * su índice de inserción. El id final es solo una red de seguridad para que el orden sea
     * total y estable entre sesiones.
     */
    private static Comparator<Item> order(Map<Item, Integer> insertionOrder, Map<Item, Integer> dragonBallRank) {
        int dragonBallsAnchor = insertionOrder.get(ModItems.ALL_DRAGON_BALLS_ITEM.get());
        return Comparator
                .<Item>comparingInt(i -> isForcedItem(i, dragonBallRank) ? 0 : half(i))
                .thenComparingInt(i -> dragonBallRank.containsKey(i) ? dragonBallsAnchor : insertionOrder.get(i))
                .thenComparingInt(i -> dragonBallRank.getOrDefault(i, 0))
                .thenComparing(ModCreativeModeTabs::path);
    }

    private static int half(Item item) {
        return item instanceof BlockItem ? 1 : 0;
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
