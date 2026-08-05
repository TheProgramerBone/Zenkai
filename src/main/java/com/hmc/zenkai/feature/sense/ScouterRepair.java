package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.ScouterItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Reparación del scouter. Dos vías, mismo material y misma promesa: el aparato vuelve entero
 * CON sus mejoras y su tinte, porque la rotura nunca destruyó el stack.
 *  - Yunque: materiales + niveles de experiencia. Es la vía de urgencia, en cualquier sitio.
 *  - Banco:  solo materiales, con barra de progreso. Es la vía barata, si tienes el banco.
 *
 * NO se toca RepairCost. Con un objeto que se rompe por diseño una y otra vez, el "demasiado
 * caro" acumulativo de vanilla lo mataría a las cinco o seis reparaciones y el jugador
 * perdería mejoras que ya pagó.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ScouterRepair {
    private ScouterRepair() {}

    /** Coste fijo en niveles. No escala con las mejoras: castigar por tener un buen scouter
     *  es exactamente al revés de lo que se quiere premiar. */
    public static final int ANVIL_LEVELS = 5;

    private static final int IRON_COUNT = 3;
    private static final ResourceLocation IRON_TAG =
            ResourceLocation.fromNamespaceAndPath("c", "ingots/iron");

    /** Mismo material en las dos vías. */
    public static ScouterUpgradeCost benchCost() {
        return new ScouterUpgradeCost(
                List.of(new ScouterUpgradeCost.Material(IRON_TAG, true, IRON_COUNT)), 0);
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

        ScouterUpgradeCost.Material iron =
                new ScouterUpgradeCost.Material(IRON_TAG, true, IRON_COUNT);
        if (!iron.matches(right) || right.getCount() < IRON_COUNT) return;

        ItemStack out = left.copy();
        ScouterStacks.repair(out);

        event.setOutput(out);
        event.setMaterialCost(IRON_COUNT);
        event.setCost(ANVIL_LEVELS);
    }
}