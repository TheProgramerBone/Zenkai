package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

/**
 * Registra la muerte de mascotas con dueño para que puedan revivirse con el deseo.
 * Si el dueño está conectado va directa a su lista; si no, se aparca en {@link OfflinePetDeaths} y se le pasa
 * al volver a conectarse ({@link #onLogin}). El deseo solo ve las mascotas propias del jugador que lo invoca.
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
        // Serializa la mascota tal cual murió (tipo, nombre, variante, dueño, etc.)
        CompoundTag petNbt = new CompoundTag();
        dead.save(petNbt);
        // Quitar UUID para que al revivir reciba uno nuevo (evita choque de "entidad duplicada").
        petNbt.remove("UUID");

        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) { // dueño desconectado: se aparca hasta que vuelva
            OfflinePetDeaths.get(server).add(ownerId, petNbt);
            return;
        }

        PlayerStatsAttachment stats = PlayerStatsAttachment.get(owner);
        stats.addDeadPet(petNbt);
        PlayerLifeCycle.sync(owner);
    }

    /** Al conectarse, el jugador recibe las mascotas que murieron mientras estaba fuera. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var pets = OfflinePetDeaths.get(sp.server).drain(sp.getUUID());
        if (pets.isEmpty()) return;
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(sp);
        for (CompoundTag pet : pets) stats.addDeadPet(pet);
        PlayerLifeCycle.sync(sp);
    }
}

