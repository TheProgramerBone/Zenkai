package com.hmc.zenkai.feature.kiweapon;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.KiWeaponItem;
import com.hmc.zenkai.feature.combat.CombatModeServerState;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ciclo de vida del arma de ki. Dos estados, y la distinción es la clave:
 *  - QUERIDA: el interruptor está puesto. Es lo que persiste.
 *  - ACTIVA:  además la mano principal está libre, así que el arma existe de verdad.
 * De ahí sale el comportamiento pedido sin código especial: sacas una espada normal y el arma
 * de ki se desvanece (deja de estar activa, pero sigue querida); vuelves a dejar la mano libre
 * y reaparece sola. El interruptor nunca se toca por el camino, así que el jugador no tiene
 * que reactivarla cada vez que usa una herramienta.
 * DOS GUARDS UNIVERSALES en vez de tapar rutas una a una:
 *  - KiWeaponItem.inventoryTick borra el arma que no esté en la mano de alguien que la quiera.
 *    Cubre arrastrarla en el inventario, meterla en un cofre, /give, o que un mod la mueva.
 *  - Aquí abajo, cualquier ItemEntity que contenga un arma de ki se cancela al aparecer.
 *    Cubre tirarla, morir con ella, dispensers y lo que pase por "aparece en el suelo".
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class KiWeaponServer {
    private KiWeaponServer() {}

    /** ¿Este jugador quiere esta variante ahora mismo? Interruptor + habilidades + combate. */
    public static boolean wants(Player p, String variant) {
        if (!SkillToggles.isOn(p, variant)) return false;
        // Fuera del modo combate no hay arma: es un arma, no un accesorio.
        return !(p instanceof ServerPlayer sp) || CombatModeServerState.isActive(sp.getUUID());
    }

    /** La variante que el jugador quiere, o null. Los dos interruptores son excluyentes. */
    public static String wantedVariant(Player p) {
        if (wants(p, SkillEffects.KI_SWORD))  return SkillEffects.KI_SWORD;
        if (wants(p, SkillEffects.KI_SCYTHE)) return SkillEffects.KI_SCYTHE;
        return null;
    }

    /** El arma de ki que lleva empuñada, o null. ÚNICO lector: lo usa el pipeline de daño. */
    public static KiWeaponItem heldWeapon(Player p) {
        return p.getMainHandItem().getItem() instanceof KiWeaponItem w ? w : null;
    }

    private static Item itemFor(String variant) {
        return SkillEffects.KI_SCYTHE.equals(variant)
                ? ModItems.KI_SCYTHE.get() : ModItems.KI_SWORD.get();
    }

    /**
     * Materializa o retira el arma. Va en el tick y no en el momento de pulsar el interruptor
     * porque la mano puede vaciarse u ocuparse en cualquier momento sin avisar, y el arma
     * tiene que seguir ese vaivén.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        String wanted = wantedVariant(sp);
        ItemStack main = sp.getMainHandItem();

        if (main.getItem() instanceof KiWeaponItem held) {
            // Lleva un arma de ki: si ya no la quiere o cambió de variante, fuera. El
            // inventoryTick también la borraría, pero esperar a eso deja un tick de arma
            // fantasma con la que se podría golpear.
            if (wanted == null || !held.variant().equals(wanted)) {
                sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            return;
        }

        // Solo aparece con la mano LIBRE. Con cualquier otra cosa empuñada se queda esperando.
        if (wanted != null && main.isEmpty()) {
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(itemFor(wanted)));
        }
    }

    /**
     * El arma de ki no toca el suelo jamás. Un solo punto para tirarla a mano, morir con ella,
     * dispensers, o cualquier cosa que acabe generando un ItemEntity.
     */
    @SubscribeEvent
    public static void onItemEntitySpawn(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (e.getEntity() instanceof ItemEntity ie
                && ie.getItem().getItem() instanceof KiWeaponItem) {
            e.setCanceled(true);
        }
    }
}