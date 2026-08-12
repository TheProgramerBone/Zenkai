package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.ScouterItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * Reparación del scouter. Dos vías, mismo material y misma promesa: el aparato vuelve entero
 * CON sus mejoras y su tinte, porque la rotura nunca destruyó el stack.
 *  - Yunque: materiales + niveles de experiencia. Es la vía de urgencia, en cualquier sitio.
 *  - Banco:  materiales + FE, con barra de progreso. Es la vía de infraestructura.
 * ESTA CLASE YA NO DEFINE EL PRECIO. Antes tenía IRON_COUNT y ANVIL_LEVELS y era la fuente
 * real del coste, con el resto del banco leyendo de datapack: dos sistemas para lo mismo.
 * Ahora sale de ScouterRepairCost, que se carga de
 * data/zenkai/zenkai_scouter_repair.json y se sincroniza al cliente.
 * NO se toca RepairCost. Con un objeto que se rompe por diseño una y otra vez, el "demasiado
 * caro" acumulativo de vanilla lo mataría a las cinco o seis reparaciones y el jugador
 * perdería mejoras que ya pagó.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ScouterRepair {
    private ScouterRepair() {}

    /** Coste en el banco: materiales + FE. Lo cobra el block entity con el mismo código que
     *  una mejora, así que no hay una segunda ruta de cobro que mantener. */
    public static ScouterUpgradeCost benchCost() {
        return ScouterRepairCost.get().cost();
    }

    /** Niveles de experiencia del yunque. */
    public static int anvilLevels() {
        return ScouterRepairCost.get().anvilLevels();
    }

    /**
     * ⚠ VERIFICAR NeoForge 1.21.1: al fijar output en AnvilUpdateEvent, AnvilMenu.createResult
     * sale antes de aplicar su propio incremento de RepairCost. Si aun así el resultado sale
     * con "demasiado caro" tras varias reparaciones, hay que quitarle el componente
     * DataComponents.REPAIR_COST al stack de salida aquí mismo.
     */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (!(left.getItem() instanceof ScouterItem)) return;
        if (!ScouterStacks.isBroken(left)) return;

        ScouterRepairCost repair = ScouterRepairCost.get();

        // EL YUNQUE SOLO TIENE UN SLOT DE MATERIAL, así que solo puede cobrar el PRIMERO de
        // la lista. Es una limitación de vanilla, no una decisión: el resto del coste se
        // ignora aquí y sí se cobra entero en el banco. Por eso el material principal debe ir
        // primero en el JSON, y por eso la experiencia existe — es lo que compensa que la vía
        // del yunque cobre menos materia.
        var mats = repair.materials();
        if (mats.isEmpty()) return;
        ScouterUpgradeCost.Material main = mats.getFirst();
        if (!main.matches(right) || right.getCount() < main.count()) return;

        ItemStack out = left.copy();
        ScouterStacks.repair(out);

        event.setOutput(out);
        event.setMaterialCost(main.count());
        event.setCost(repair.anvilLevels());
    }
}