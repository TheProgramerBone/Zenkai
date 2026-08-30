package com.hmc.zenkai.content.entity.master;

import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import com.hmc.zenkai.feature.master.MasterService;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/** Kaiosama. Enseña kaioken. Vive en el Otherworld, así que su PL requerido puede ser alto:
 *  llegar hasta él ya es la mitad del filtro. Su servicio entrega el equipo de pesas de
 *  entrenamiento — DE UNA SOLA VEZ por jugador (PlayerStatsAttachment.hasReceivedKaioWeights),
 *  no una granja repetible: pedirlas dos veces no debería duplicar equipo gratis. */
public class KaioEntity extends ZenkaiMasterEntity {

    public KaioEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public String masterId() { return "kaio"; }

    @Override
    protected List<MasterService> services() {
        return List.of(new MasterService() {
            @Override public String id() { return "weights"; }

            @Override public Component label(ServerPlayer sp) {
                boolean given = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get())
                        .hasReceivedKaioWeights();
                return Component.translatable(given
                        ? "master.zenkai.kaio.service.weights.done"
                        : "master.zenkai.kaio.service.weights");
            }

            @Override public boolean claim(ServerPlayer sp) {
                var stats = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
                if (stats.hasReceivedKaioWeights()) {
                    sp.sendSystemMessage(Component.translatable("messages.zenkai.kaio.weights_already")
                            .withStyle(ChatFormatting.GRAY));
                    return false;
                }
                stats.setReceivedKaioWeights(true);
                PlayerLifeCycle.sync(sp);

                ItemStack straps = ModItems.WEIGHTED_STRAPS.get().getDefaultInstance();
                ItemStack cape = ModItems.WEIGHTED_CAPE.get().getDefaultInstance();
                if (!sp.getInventory().add(straps)) sp.drop(straps, false);
                if (!sp.getInventory().add(cape)) sp.drop(cape, false);

                sp.sendSystemMessage(Component.translatable("messages.zenkai.kaio.weights_given")
                        .withStyle(ChatFormatting.GREEN));
                return true;
            }
        });
    }
}
