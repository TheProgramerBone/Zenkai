package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.player.death.PlayerBodyAccess;
import com.hmc.zenkai.feature.player.death.PlayerBodyBury;
import com.hmc.zenkai.feature.player.death.PlayerBodyLoot;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Menú de un cuerpo con la distribución del inventario de vanilla: fila de armadura (casco...botas) y mano
 * secundaria, mochila 3x9, barra, y debajo —si el jugador tenía Curios— UNA fila de 9 ranuras con flechas para pasar de
 * página (un modpack con Artifacts puede tener decenas de ranuras). Luego el inventario de quien mira, como en un cofre.
 *
 * ORDEN de this.slots: 0-3 armadura (casco...botas), 4 mano secundaria, 5-31 mochila, 32-40 barra, 41-49 la PÁGINA de
 * Curios (siempre 9 huecos; los que sobran se desactivan con {@code isActive}) y después los 36 del jugador. Los
 * índices del CONTENEDOR del cuerpo coinciden con los de {@link PlayerBodyEntity} (0-8 barra, 9-35 mochila, 36-39
 * armadura botas→casco, 40 mano secundaria, 41.. Curios).
 *
 * PAGINACIÓN: los 9 huecos de Curios son un {@link PagedCurioView} sobre el contenedor del cuerpo: el hueco i de la página p
 * es la ranura 41 + 9p + i. Cambiar de página solo cambia ese número (un DataSlot sincronizado) y el servidor reenvía lo que
 * ahora ve cada hueco. El icono de cada hueco vacío es el que Curios da a su tipo (se calcula con la página actual).
 *
 * Botones por clickMenuButton (red vanilla, ya validada): 0 take_all, 1 enterrar, 2 y 3 página anterior/siguiente.
 * La XP y «¿está vacío?» viajan por DataSlot; la XP en dos mitades de 15 bits porque DataSlot manda un short.
 */
public class PlayerBodyMenu extends AbstractContainerMenu {

    public static final int BTN_TAKE_ALL = 0, BTN_BURY = 1, BTN_PREV = 2, BTN_NEXT = 3;
    public static final int PAGE = 9;

    /** Geometría compartida con la pantalla (coordenadas locales al panel). */
    public static final class Layout {
        public static final int W = 176, BTN_Y = 17, GRID_X = 8;
        public static final int ARMOR_Y = 38, MAIN_Y = 60, HOTBAR_Y = 118, CURIO_Y = 142;

        /** Y de la primera fila del inventario del jugador. */
        public static int playerY(int curios) {
            int y = CURIO_Y;
            if (curios > 0) y += 12 + 18 + 2;
            return y + 15;   // sitio para la etiqueta del inventario (11 px) sin pisar la fila de arriba
        }

        public static int height(int curios) { return playerY(curios) + 3 * 18 + 4 + 18 + 7; }
    }

    /** Los 9 huecos de la página actual vistos como un contenedor propio. */
    private static final class PagedCurioView implements Container {
        private final Container body;
        private final PlayerBodyMenu menu;
        PagedCurioView(Container body, PlayerBodyMenu menu) { this.body = body; this.menu = menu; }

        private int map(int i) {
            int k = menu.page() * PAGE + i;
            return k < menu.curioCount() ? PlayerBodyEntity.CURIO_BASE + k : -1;
        }
        @Override public int getContainerSize() { return PAGE; }
        @Override public boolean isEmpty() { for (int i = 0; i < PAGE; i++) if (!getItem(i).isEmpty()) return false; return true; }
        @Override public ItemStack getItem(int i) { int m = map(i); return m < 0 ? ItemStack.EMPTY : body.getItem(m); }
        @Override public ItemStack removeItem(int i, int n) { int m = map(i); return m < 0 ? ItemStack.EMPTY : body.removeItem(m, n); }
        @Override public ItemStack removeItemNoUpdate(int i) { int m = map(i); return m < 0 ? ItemStack.EMPTY : body.removeItemNoUpdate(m); }
        @Override public void setItem(int i, ItemStack s) { int m = map(i); if (m >= 0) body.setItem(m, s); }
        @Override public void setChanged() { body.setChanged(); }
        @Override public boolean stillValid(Player p) { return body.stillValid(p); }
        @Override public void clearContent() { }
    }

    private final Container body;                          // servidor: el cuerpo entero; cliente: 41 huecos vanilla
    @Nullable private final PlayerBodyEntity entity;       // null en el cliente
    private final String[] curioIds;
    private final Player viewer;
    private final DataSlot pageData = DataSlot.standalone();
    private final DataSlot xpLo, xpHi, emptyData;

    /** Cliente: los contenedores llegan vacíos y se llenan por la sincronización normal del menú. */
    public PlayerBodyMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(PlayerBodyEntity.CURIO_BASE), null, readIds(buf));
    }

    /** Servidor. */
    public PlayerBodyMenu(int id, Inventory inv, PlayerBodyEntity entity) {
        this(id, inv, entity.viewFor(), entity, idsOf(entity));
    }

    private static String[] readIds(RegistryFriendlyByteBuf buf) {
        buf.readVarInt();                                  // id de la entidad (reservado para la pantalla)
        String[] ids = new String[buf.readVarInt()];
        for (int i = 0; i < ids.length; i++) ids[i] = buf.readUtf();
        return ids;
    }

    private static String[] idsOf(PlayerBodyEntity e) {
        String[] ids = new String[e.curioCount()];
        for (int i = 0; i < ids.length; i++) ids[i] = e.curioId(i);
        return ids;
    }

    private PlayerBodyMenu(int id, Inventory inv, Container body, @Nullable PlayerBodyEntity entity, String[] curioIds) {
        super(ModMenuTypes.PLAYER_BODY.get(), id);
        this.body = body;
        this.entity = entity;
        this.curioIds = curioIds;
        this.viewer = inv.player;

        addDataSlot(pageData);
        xpLo = entity == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return entity.xp() & 0x7FFF; }
            @Override public void set(int v) { }
        };
        xpHi = entity == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return entity.xp() >>> 15; }
            @Override public void set(int v) { }
        };
        emptyData = entity == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return entity.hasItems() || entity.xp() > 0 ? 0 : 1; }
            @Override public void set(int v) { }
        };
        addDataSlot(xpLo);
        addDataSlot(xpHi);
        addDataSlot(emptyData);

        // Armadura: casco, peto, grebas, botas (de izquierda a derecha), y la mano secundaria tras un hueco.
        int[] armorOrder = {39, 38, 37, 36};
        EquipmentSlot[] types = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        // Icono de hueco vacío: los mismos sprites que usa el inventario de vanilla.
        ResourceLocation[] armorIcons = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
                InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};
        for (int i = 0; i < 4; i++) {
            final EquipmentSlot type = types[i];
            Slot armor = new Slot(body, armorOrder[i], Layout.GRID_X + 1 + i * 18, Layout.ARMOR_Y + 1) {
                @Override public boolean mayPlace(ItemStack s) { return s.canEquip(type, viewer); }
                @Override public int getMaxStackSize() { return 1; }
            };
            armor.setBackground(InventoryMenu.BLOCK_ATLAS, armorIcons[i]);
            addSlot(armor);
        }
        Slot offhand = new Slot(body, PlayerBodyEntity.OFFHAND, Layout.GRID_X + 1 + 5 * 18, Layout.ARMOR_Y + 1);
        offhand.setBackground(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
        addSlot(offhand);
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(body, 9 + r * 9 + c, Layout.GRID_X + 1 + c * 18, Layout.MAIN_Y + 1 + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(body, c, Layout.GRID_X + 1 + c * 18, Layout.HOTBAR_Y + 1));

        // Página de Curios: siempre 9 huecos, activos solo los que existen en la página actual.
        Container view = entity != null ? new PagedCurioView(body, this) : new SimpleContainer(PAGE);
        for (int i = 0; i < PAGE; i++) {
            final int idx = i;
            addSlot(new Slot(view, i, Layout.GRID_X + 1 + i * 18, Layout.CURIO_Y + 13) {
                private int k() { return page() * PAGE + idx; }
                @Override public boolean isActive() { return k() < curioCount(); }
                @Override public boolean mayPlace(ItemStack s) { return k() < curioCount() && CuriosCompat.isValidFor(viewer, curioId(k()), s); }
                @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    ResourceLocation icon = k() < curioCount() ? CuriosCompat.slotIcon(curioId(k())) : null;
                    return icon == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
                }
            });
        }

        int py = Layout.playerY(curioIds.length);
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, 9 + r * 9 + c, Layout.GRID_X + 1 + c * 18, py + 1 + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, Layout.GRID_X + 1 + c * 18, py + 1 + 3 * 18 + 4));
    }

    public int curioCount() { return curioIds.length; }
    public String curioId(int k) { return curioIds[k]; }
    public int page() { return pageData.get(); }
    public int pages() { return Math.max(1, (curioIds.length + PAGE - 1) / PAGE); }
    public int storedXp() { return (xpHi.get() << 15) | xpLo.get(); }
    /** Sin objetos ni XP (lo decide el servidor: el cliente no ve las páginas de Curios que no está mirando). */
    public boolean bodyIsEmpty() { return emptyData.get() == 1; }

    /** Nivel equivalente a esos puntos de experiencia (para enseñarlo junto a la cifra). */
    public static int levelOf(int points) {
        int l = 0;
        while (points >= pointsForNext(l)) { points -= pointsForNext(l); l++; }
        return l;
    }
    private static int pointsForNext(int level) { return level >= 30 ? 112 + (level - 30) * 9 : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2; }

    /** Cuántos huecos del cuerpo tiene este menú (vanilla + la página de Curios): el resto de this.slots es del jugador. */
    public int bodySlotCount() { return 41 + PAGE; }

    @Override
    public boolean stillValid(Player p) { return body.stillValid(p); }

    // ── Botones (servidor) ───────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (!(p instanceof ServerPlayer sp) || entity == null || !body.stillValid(p)) return false;
        switch (id) {
            case BTN_TAKE_ALL -> takeAll(sp);
            case BTN_BURY -> { if (PlayerBodyBury.start(sp, entity)) sp.closeContainer(); }
            case BTN_PREV -> pageData.set(Math.max(0, page() - 1));
            case BTN_NEXT -> pageData.set(Math.min(pages() - 1, page() + 1));
            default -> { return false; }
        }
        return true;
    }

    /** take_all: ver {@link PlayerBodyLoot} (armadura libre o claramente mejor, Curios, mano, resto y XP). */
    private void takeAll(ServerPlayer sp) {
        PlayerBodyLoot.takeAll(sp, entity);
        broadcastChanges();
    }

    // ── Shift + clic ─────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int bodyEnd = bodySlotCount();
        if (index < bodyEnd) {
            if (!moveItemStackTo(stack, bodyEnd, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // Del jugador a la mochila y la barra del cuerpo (this.slots 5..40).
            if (!moveItemStackTo(stack, 5, 41, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}
