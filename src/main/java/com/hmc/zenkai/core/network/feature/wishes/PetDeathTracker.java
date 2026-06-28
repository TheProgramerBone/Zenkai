package com.hmc.zenkai.core.network.feature.wishes;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.UUID;

/**
 * Registra la muerte de mascotas con dueño para que puedan revivirse con el deseo.
 * Solo guarda si el dueño está ONLINE en ese momento (limitación conocida).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PetDeathTracker {
    private PetDeathTracker() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;

        // ¿Mascota con dueño? (lobos, gatos, loros; caballos/llamas si implementan OwnableEntity)
        if (!(dead instanceof OwnableEntity ownable)) return;
        UUID ownerId = ownable.getOwnerUUID();
        if (ownerId == null) return;

        var server = dead.getServer();
        if (server == null) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) return; // dueño offline: no se guarda

        // Serializa la mascota tal cual murió (tipo, nombre, variante, dueño, etc.)
        CompoundTag petNbt = new CompoundTag();
        dead.save(petNbt);
        // Quitar UUID para que al revivir reciba uno nuevo (evita choque de "entidad duplicada").
        petNbt.remove("UUID");

        PlayerStatsAttachment stats = PlayerStatsAttachment.get(owner);
        stats.addDeadPet(petNbt);
        PlayerLifeCycle.sync(owner);
    }
}