package com.hmc.zenkai.content.entity.master;

import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import com.hmc.zenkai.feature.master.KorinSenzuData;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Korin. Enseña las habilidades de puño (ki_fist, ki_infuse) y, reparte semillas
 * del ermitaño: es la razón real por la que un jugador sube su torre.
 * El reparto de senzu NO vive aquí. Vive en KorinSenzuManager (tanda 2), por la misma razón
 * por la que los requisitos de admisión viven en MasterManager: la entidad es el punto de
 * contacto, no la regla. Si el reparto estuviera en mobInteract, /zenkai o cualquier otro
 * camino que quiera dar semillas tendría que reimplementar el contador diario.
 */
public class KorinEntity extends ZenkaiMasterEntity {

    public KorinEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public String masterId() { return "korin"; }

    @Override
    protected boolean offerFavor(ServerPlayer sp) {
        KorinSenzuData data = KorinSenzuData.get(sp.server);
        int left = data.remaining(sp.server, sp.getUUID());
        if (left <= 0) {
            sp.sendSystemMessage(Component.translatable("messages.zenkai.korin.no_senzu")
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }

        int given = data.claim(sp.server, sp.getUUID(), left);
        if (given <= 0) return true;

        ItemStack beans = new ItemStack(ModItems.SENZU_BEAN.get(), given);
        // Al inventario, y al suelo solo si no cabe: la torre de Korin está a 200 bloques de
        // altura y un stack que rebota por el borde no se recupera.
        if (!sp.getInventory().add(beans)) sp.drop(beans, false);

        sp.sendSystemMessage(Component.translatable("messages.zenkai.korin.senzu", given)
                .withStyle(ChatFormatting.GREEN));
        sp.level().playSound(null, this.blockPosition(),
                ModSounds.SENZU_EAT.get(), SoundSource.NEUTRAL, 0.6f, 1.4f);
        return true;
    }
}