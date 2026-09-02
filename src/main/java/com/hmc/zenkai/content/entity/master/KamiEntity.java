package com.hmc.zenkai.content.entity.master;

import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.master.MasterService;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Kamisama. Enseña fly, ki_sense, ki_block, ki_control, meditation y run. Su servicio es la
 * única forma de tocar la cola de un saiyan (PlayerStatsAttachment.hasTail(), servicio de
 * OozaruConditions/TailResolver): quitarla o hacerla crecer de vuelta. El ESTILO
 * suelta/cintura, en cambio, es gratis y libre desde la rueda (WheelMenu.tailStyleToggle) —
 * Kami solo decide si hay cola, no cómo se lleva.
 */
public class KamiEntity extends ZenkaiMasterEntity {

    public KamiEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public String masterId() { return "kami"; }

    @Override
    protected List<MasterService> services() {
        return List.of(new MasterService() {
            @Override public String id() { return "tail"; }

            @Override public Component label(ServerPlayer sp) {
                boolean has = PlayerStatsAttachment.get(sp).hasTail();
                return Component.translatable(has
                        ? "master.zenkai.kami.service.tail.remove"
                        : "master.zenkai.kami.service.tail.grow");
            }

            // Solo los Saiyan tienen cola de verdad (PlayerStatsAttachment.hasTail() no existe
            // como concepto para el resto de razas) — sin este candado cualquier raza podía
            // marcarse hasTail=true y quedar con la lógica de cola (OozaruConditions,
            // TailResolver) activa sin sentido.
            @Override public boolean available(ServerPlayer sp) {
                return PlayerStatsAttachment.get(sp).getRace() == Race.SAIYAN;
            }

            @Override public Component tooltip(ServerPlayer sp) {
                return available(sp) ? Component.empty()
                        : Component.translatable("master.zenkai.kami.service.tail.locked");
            }

            @Override public boolean claim(ServerPlayer sp) {
                // Revalida aquí lo mismo que available() ya filtró para la UI: un cliente
                // modificado puede mandar MasterServicePacket sin haber visto la fila oscurecida.
                if (!available(sp)) return false;
                var stats = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
                boolean had = stats.hasTail();
                stats.setHasTail(!had);
                PlayerLifeCycle.sync(sp);
                sp.sendSystemMessage(Component.translatable(had
                                ? "messages.zenkai.kami.tail_removed"
                                : "messages.zenkai.kami.tail_grown")
                        .withStyle(ChatFormatting.GREEN));
                return true;
            }
        });
    }
}
