package com.hmc.zenkai.content.item;

import com.hmc.zenkai.feature.kiweapon.KiWeaponDef;
import com.hmc.zenkai.feature.kiweapon.KiWeaponRegistry;
import com.hmc.zenkai.feature.kiweapon.KiWeaponServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Arma de ki (espada o guadaña). Es un item REGISTRADO de verdad — no un layer visual —
 * porque así la primera persona, el inventario, los NPC y los modificadores de atributo
 * (alcance, velocidad) salen del render vanilla en vez de pelearse con los hooks de brazo.
 *
 * Pero es un item que NO DEBE PODER EXISTIR fuera de la mano de su dueño: no se craftea, no
 * se da, no se tira y no se guarda. En vez de tapar cada ruta de fuga por separado
 * (arrastrarlo en el inventario, meterlo en un cofre, morir, un mod que lo mueva, /give), el
 * guard está donde pasan todas: inventoryTick se ejecuta sobre cada stack del inventario cada
 * tick, así que si el arma no está donde debe o su dueño ya no la quiere, se borra sola.
 *
 * OJO: inventoryTick solo corre para inventarios de jugador. Los mobs que la lleven equipada
 * no se autolimpian, que es justo lo que queremos para los NPC que la usen algún día.
 */
public class KiWeaponItem extends Item {

    private final String variant;   // id del toggle: "ki_sword" / "ki_scythe"

    public KiWeaponItem(Properties properties, String variant) {
        super(properties.stacksTo(1).fireResistant());
        this.variant = variant;
    }

    /** Id del interruptor que invoca esta arma. */
    public String variant() { return variant; }

    /** Números de datapack. Nunca null: cae al FALLBACK si falta el JSON. */
    public KiWeaponDef def() { return KiWeaponRegistry.get(variant); }

    /**
     * EL guard. Se borra si:
     *  - no está en la mano principal (la arrastraron, la metieron en un cofre, la duplicó
     *    algo), o
     *  - su portador ya no tiene el interruptor puesto o perdió las habilidades.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide()) return;
        if (!(entity instanceof Player p) || !isSelected || !KiWeaponServer.wants(p, variant)) {
            stack.setCount(0);
        }
    }

    /** Ni tirable con la tecla de soltar: el respaldo es el guard de arriba, esto solo evita
     *  el parpadeo de verla caer y desaparecer. */
    @Override
    public boolean canFitInsideContainerItems() { return false; }
}