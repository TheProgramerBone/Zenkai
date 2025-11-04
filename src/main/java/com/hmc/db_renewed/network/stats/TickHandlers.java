package com.hmc.db_renewed.network.stats;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.config.StatsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class TickHandlers {

    // ✅ Ahora ResourceLocation, no UUID
    private static final ResourceLocation MOVE_MOD_ID =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "speed_mult");

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        PlayerStatsAttachment att = p.getData(DataAttachments.PLAYER_STATS.get());

        // Regen por tick (config)
        att.addBody(StatsConfig.baseRegenBody());
        att.addStamina(StatsConfig.baseRegenStamina());
        att.addEnergy(StatsConfig.baseRegenEnergy());

        // Movimiento: 1.0 + Speed/100 * scaling (cap al serverconfig)
        double speedStat = att.computeSpeedFinal();
        double moveMult = Math.min(1.0 + (speedStat / 100.0) * StatsConfig.movementScaling(),
                StatsConfig.speedMultiplierCap());

        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveAttr != null) {
            // Elimina anterior (por id) y aplica nuevo como TRANSIENT (se recalcula cada tick)
            moveAttr.removeModifier(MOVE_MOD_ID);
            moveAttr.addTransientModifier(new AttributeModifier(
                    MOVE_MOD_ID,
                    moveMult - 1.0, // multiplicador sobre base
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE // ✅ reemplaza MULTIPLY_BASE
            ));
        }

        // Vuelo: enviar valor efímero que el cliente aplicará a su flyingSpeed
        double flyStat = att.computeFlyFinal();
        double flyMult = Math.min(1.0 + (flyStat / 100.0) * StatsConfig.flyScaling(),
                StatsConfig.flyMultiplierCap());
        att.setTempStat("clientFlyMult", flyMult, 5);

        // Sync sólo en servidor
        if (!p.level().isClientSide()) {
            PlayerLifeCycle.syncIfServer(p); // ✅ asegúrate de tener el import correcto
        }
    }
}