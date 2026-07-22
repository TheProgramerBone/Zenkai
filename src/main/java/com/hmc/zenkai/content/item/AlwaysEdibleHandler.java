package com.hmc.zenkai.content.item;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Hace que TODA la comida se pueda comer con la barra de hambre llena.
 * Motivo: desde que la comida repone ki y estamina (FoodEnergyHandler), bloquear el
 * comer por estar saciado dejaría al jugador sin forma de recuperar ki en pleno combate.
 * Comer pasa a ser una decisión de recursos, no solo de hambre.
 * Va en el MOD bus (no en el de juego): ModifyDefaultComponentsEvent se dispara durante
 * la carga del mod, cuando aún se pueden tocar los componentes por defecto de los items.
 * ⚠ SIN VERIFICAR: la API exacta de ModifyDefaultComponentsEvent (getAllItems / modify) y
 * la aridad del constructor de FoodProperties cambian entre versiones de NeoForge/MC. En
 * 1.21.1 debería ser el de 5 argumentos que hay abajo; si tu IDE pide un Optional extra
 * (usingConvertsTo), es que tu mapeo es más nuevo: añádelo como Optional.empty().
 * Si este evento diera problemas, este archivo se puede borrar entero sin romper nada:
 * lo único que se pierde es poder comer con el estómago lleno.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class AlwaysEdibleHandler {
    private AlwaysEdibleHandler() {}

    @SubscribeEvent
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.getAllItems().forEach(item -> {
            FoodProperties food = item.components().get(DataComponents.FOOD);
            if (food == null || food.canAlwaysEat()) return;

            FoodProperties always = new FoodProperties(
                    food.nutrition(),
                    food.saturation(),
                    true,                     // canAlwaysEat
                    food.eatSeconds(),
                    food.usingConvertsTo(),   // p. ej. cubo de leche -> cubo vacío
                    food.effects());

            event.modify(item, builder -> builder.set(DataComponents.FOOD, always));
        });
    }
}