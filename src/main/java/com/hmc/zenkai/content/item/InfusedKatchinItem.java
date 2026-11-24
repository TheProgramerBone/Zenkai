package com.hmc.zenkai.content.item;

import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import net.minecraft.world.item.Item;

/**
 * Katchin infundido como ítem PROPIO (rework del 2026-11-23, sección «ANTES DE PASAR A FASE 6» de pendientes-0.7.0):
 * lingote, hilo y tela de cada nivel de infusión (Normal, Súper, Divino, Legendario) son ítems distintos y el nivel va
 * en la clase, no en un componente. Antes era el lingote normal con {@code zenkai:ki_infusion}: no se apilaba con el
 * normal sin avisar, las recetas tenían que envolverse para heredar el tipo y el bloque lo perdía al colocarse.
 *
 * El componente {@code zenkai:ki_infusion} se queda SOLO en la armadura: la máquina de fabricación lee el nivel de este
 * ítem ({@link com.hmc.zenkai.feature.armor.KiInfusion#materialLevel}) y se lo pone a la pieza.
 */
public class InfusedKatchinItem extends Item {

    /** Qué forma del material es (para el arte y para elegir el ítem del mismo nivel). */
    public enum Form { INGOT, THREAD, CLOTH }

    private final KiEnergyType level;
    private final Form form;

    public InfusedKatchinItem(KiEnergyType level, Form form, Properties props) {
        super(props);
        this.level = level;
        this.form = form;
    }

    public KiEnergyType level() { return level; }
    public Form form() { return form; }
}
