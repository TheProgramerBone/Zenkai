package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.BloodItem;
import com.hmc.zenkai.registry.ModDataMaps;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Suelta la sangre del data map {@code zenkai:blood_sources} (Fase 5b, P2) cuando el mob muere a manos de un jugador
 * real. Va en LivingDropsEvent y no en las loot tables para no pisar las de vanilla ni las de otros mods: el data map
 * se amplía sin tocar nada. Los jugadores falsos (granjas automáticas de otros mods) no cuentan, igual que el «muerto
 * por el jugador» de vanilla para la experiencia.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class BloodDrops {
    private BloodDrops() {}

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide) return;
        if (!(e.getSource().getEntity() instanceof Player killer) || killer instanceof FakePlayer) return;
        java.util.List<ModDataMaps.BloodSource> list = dead.getType().builtInRegistryHolder().getData(ModDataMaps.BLOOD_SOURCES);
        if (list == null) return;
        for (ModDataMaps.BloodSource src : list) {   // cada sangre tira su probabilidad por separado
            if (!(src.blood() instanceof BloodItem) || dead.getRandom().nextFloat() >= src.chance()) continue;
            e.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(),
                    BloodItem.of(src.blood(), src.quality(), 1)));
        }
    }
}
