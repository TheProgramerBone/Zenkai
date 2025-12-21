package com.hmc.db_renewed.core.network.vehicle;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = DragonBlockRenewed.MOD_ID, value = Dist.CLIENT)
public final class VehicleClientInput {

    private static boolean lastUp, lastDown;
    private static int tickCounter;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Aquí ya no amarras a SpacePodEntity
        if (!(mc.player.getVehicle() instanceof VerticalControlVehicle vehicle)) return;

        boolean up = mc.options.keyJump.isDown();      // SPACE
        boolean down = mc.options.keySprint.isDown();  // CTRL

        // Predicción local (suave)
        vehicle.setVerticalInput(up, down);

        tickCounter++;

        boolean changed = (up != lastUp) || (down != lastDown);
        boolean keepAlive = (up || down) && (tickCounter % 4 == 0);

        if (changed || keepAlive) {
            lastUp = up;
            lastDown = down;
            PacketDistributor.sendToServer(new VehicleControlPayload(up, down));
        }
    }
}
