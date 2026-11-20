package com.hmc.zenkai.feature.player.death;

import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * «take_all» de un cuerpo (botón del menú). Lo que no cabe se queda en el cuerpo.
 *  - Armadura: si tu ranura está LIBRE, se equipa. Si ya llevas algo y {@code permadeath.take_all_swap_armor} está activa,
 *    se intercambia cuando la del cuerpo es estrictamente mejor ({@link ArmorCompare}) Y tu pieza vieja cabe en el inventario;
 *    si no, la del cuerpo va al inventario.
 *  - Mano secundaria: a su ranura si está libre; si no, al inventario.
 *  - Curios: a su ranura libre del mismo tipo (si no, al inventario).
 *  - El resto, al inventario. La XP guardada la reclama quien puede ({@link PlayerBodyAccess#canTakeXp}).
 */
public final class PlayerBodyLoot {
    private PlayerBodyLoot() {}

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    public static void takeAll(ServerPlayer sp, PlayerBodyEntity entity) {
        Container body = entity.inventory();
        Inventory inv = sp.getInventory();
        for (int i = 0; i < PlayerBodyEntity.SLOTS; i++) {
            ItemStack s = body.getItem(i);
            if (s.isEmpty()) continue;
            ItemStack rest = s.copy();
            if (i >= PlayerBodyEntity.ARMOR && i < PlayerBodyEntity.ARMOR + 4) {
                int k = i - PlayerBodyEntity.ARMOR;                 // 0 botas ... 3 casco (el orden de Inventory.armor)
                ItemStack worn = inv.armor.get(k);
                if (worn.isEmpty()) {
                    inv.armor.set(k, rest);
                    rest = ItemStack.EMPTY;
                } else if ((true)
                        && ArmorCompare.better(rest, worn, ARMOR_SLOTS[k]) && inv.add(worn.copy())) {
                    inv.armor.set(k, rest);                          // la tuya ya está en el inventario: se pone la mejor
                    rest = ItemStack.EMPTY;
                }
            } else if (i == PlayerBodyEntity.OFFHAND) {
                if (inv.offhand.get(0).isEmpty()) { inv.offhand.set(0, rest); rest = ItemStack.EMPTY; }
            } else if (i >= PlayerBodyEntity.CURIO_BASE) {
                rest = CuriosCompat.tryEquip(sp, entity.curioId(i - PlayerBodyEntity.CURIO_BASE), rest);
            }
            if (!rest.isEmpty()) inv.add(rest);   // reduce `rest` en el sitio; lo que quede no cabía
            body.setItem(i, rest.isEmpty() ? ItemStack.EMPTY : rest);
        }
        if (entity.xp() > 0 && PlayerBodyAccess.canTakeXp(sp, entity)) {
            sp.giveExperiencePoints(entity.xp());
            entity.setXp(0);
        }
        body.setChanged();
    }
}
