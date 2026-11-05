package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Reloj de las auras, PROPIO DEL CLIENTE (2026-11-01).
 *
 * POR QUÉ. Las auras se animaban con {@code level.getGameTime()}. En un servidor ese número NO es del
 * cliente: el servidor lo reescribe cada ~1 s (ClientboundSetTimePacket) con SU contador. Si el servidor va
 * justo de TPS, o simplemente hay latencia, el reloj da un tirón (o RETROCEDE) cada segundo: el movimiento
 * de las llamas hace un micro-salto periódico y, con TPS bajo, se arrastra más lento que el juego. En
 * un mundo local no pasa, y por eso solo se veía "robótico/lento" en servidores. El reloj de aquí cuenta
 * ticks del CLIENTE, que avanzan a 20/s reales con el servidor que sea, y se para con el juego en pausa.
 *
 * Los shaders de cuerpo usan el uniform estándar {@code GameTime}: {@link #bindShaderTime} lo apunta a este
 * reloj mientras se dibuja el aura y {@link #restoreShaderTime} lo devuelve a lo de vanilla.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraClock {
    private AuraClock() {}

    private static long ticks;

    /** Ticks de cliente desde que se entró al mundo (parados en pausa). */
    public static long ticks() { return ticks; }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) ticks++;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        ticks = 0L;
    }

    /** Apunta el {@code GameTime} de los shaders a este reloj (misma fórmula que vanilla). */
    public static void bindShaderTime(float partial) {
        RenderSystem.setShaderGameTime(ticks, partial);
    }

    /** Devuelve el {@code GameTime} al del nivel, como lo deja vanilla cada fotograma. */
    public static void restoreShaderTime(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) RenderSystem.setShaderGameTime(mc.level.getGameTime(), partial);
    }
}
