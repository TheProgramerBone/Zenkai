package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.SenzuBagItem;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Bolsa de senzu (2026-11-26): senzu de 16 en 16, bolsa hasta 64, meter y sacar como el bundle, comer descuenta una y el
 * cooldown se comparte con la senzu suelta.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SenzuBagGameTests {
    private SenzuBagGameTests() {}

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void bagStoresEatsAndSharesCooldown(GameTestHelper h) {
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.getCooldowns().removeCooldown(ModItems.SENZU_BEAN.get());
        player.getCooldowns().removeCooldown(ModItems.SENZU_BAG.get());
        h.assertTrue(new ItemStack(ModItems.SENZU_BEAN.get()).getMaxStackSize() == 16, "la senzu va de 16 en 16");

        ItemStack bag = new ItemStack(ModItems.SENZU_BAG.get());
        SimpleContainer box = new SimpleContainer(2);
        Slot slot = new Slot(box, 0, 0, 0);

        // Meter: cinco montones de 16 en una bolsa de 64 → caben cuatro, el quinto se queda en la ranura.
        for (int i = 0; i < 5; i++) {
            box.setItem(0, new ItemStack(ModItems.SENZU_BEAN.get(), 16));
            bag.getItem().overrideStackedOnOther(bag, slot, ClickAction.SECONDARY, player);
        }
        h.assertTrue(SenzuBagItem.count(bag) == 64, "llena con 64, no " + SenzuBagItem.count(bag));
        h.assertTrue(box.getItem(0).getCount() == 16, "lo que no cabe se queda");

        // Sacar al cursor: un montón de 16.
        ItemStack[] cursor = {ItemStack.EMPTY};
        SlotAccess access = new SlotAccess() {
            @Override public ItemStack get() { return cursor[0]; }
            @Override public boolean set(ItemStack s) { cursor[0] = s; return true; }
        };
        Slot bagSlot = new Slot(new SimpleContainer(bag), 0, 0, 0);
        bag.getItem().overrideOtherStackedOnMe(bag, ItemStack.EMPTY, bagSlot, ClickAction.SECONDARY, player, access);
        h.assertTrue(cursor[0].is(ModItems.SENZU_BEAN.get()) && cursor[0].getCount() == 16, "saca 16 al cursor");
        h.assertTrue(SenzuBagItem.count(bag) == 48, "quedan 48");

        // Comer: una menos y cooldown en la bolsa Y en la senzu suelta.
        bag.getItem().finishUsingItem(bag, h.getLevel(), player);
        h.assertTrue(SenzuBagItem.count(bag) == 47, "comer gasta una");
        h.assertTrue(player.getCooldowns().isOnCooldown(ModItems.SENZU_BEAN.get())
                && player.getCooldowns().isOnCooldown(ModItems.SENZU_BAG.get()), "cooldown compartido");

        // Vacía: sin componente (se apila igual que una nueva... aunque es de 1).
        bag.set(ModDataComponents.SENZU_COUNT.get(), 1);
        bag.getItem().finishUsingItem(bag, h.getLevel(), player);
        h.assertTrue(!bag.has(ModDataComponents.SENZU_COUNT.get()), "vacía sin componente");
        player.getCooldowns().removeCooldown(ModItems.SENZU_BEAN.get());
        player.getCooldowns().removeCooldown(ModItems.SENZU_BAG.get());
        h.succeed();
    }
}
