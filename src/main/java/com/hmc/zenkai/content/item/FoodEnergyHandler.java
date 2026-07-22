package com.hmc.zenkai.content.item;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * La comida repone ki y estamina además de hambre: cada punto de nutrición devuelve un
 * % del pool máximo (config regen.food.*). Es la vía rápida de recuperación ahora que el
 * regen pasivo es lento y que cargar ki con C exige la habilidad Meditación.
 * Se aplica a CUALQUIER item con el componente FOOD, incluidos los de otros mods.
 * El senzu pasa por aquí también, pero SenzuBean ya rellena los pools al máximo, así que
 * lo que sume este handler queda clampeado y no cambia nada.
 * Para poder comer con la barra de hambre llena, ver AlwaysEdibleHandler.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class FoodEnergyHandler {
    private FoodEnergyHandler() {}

    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        ItemStack stack = event.getItem();
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null || food.nutrition() <= 0) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return;

        int nutrition = food.nutrition();
        int ki = (int) Math.round(att.getEnergyMax()
                * (StatsConfig.foodKiPercentPerNutrition() / 100.0) * nutrition);
        int stamina = (int) Math.round(att.getStaminaMax()
                * (StatsConfig.foodStaminaPercentPerNutrition() / 100.0) * nutrition);

        if (ki <= 0 && stamina <= 0) return;

        if (ki > 0) att.addEnergy(ki);
        if (stamina > 0) att.addStamina(stamina);
        PlayerLifeCycle.syncIfServer(sp);
    }
}