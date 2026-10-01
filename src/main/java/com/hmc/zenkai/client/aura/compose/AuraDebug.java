package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Estado de DEPURACIÓN del aura compuesta, solo en memoria y solo para el aura PROPIA: lo pone
 * {@code /zenkaiaura} ({@link AuraComposeCommand}) para probar familias, estilos, capas y estados
 * sin tocar la forma ni el servidor. También el overlay de texto con la composición activa.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraDebug {

    private AuraDebug() {}

    /** aura_type forzado para el aura propia (estilo Y firma de forma); null = el de la forma. */
    public static @Nullable String typeOverride;
    /** Familia forzada (estilo desnudo de esa familia); null = la del estilo. */
    public static @Nullable String familyOverride;
    /** Overlay de texto. */
    public static boolean hud;
    /** Estados forzados (true/false); ausente = el real. */
    public static final EnumMap<AuraDynamics.State, Boolean> FORCED = new EnumMap<>(AuraDynamics.State.class);
    /** Capas apagadas ("body" o el tipo de un componente: motes, electricity...). */
    public static final Set<String> DISABLED = new HashSet<>();
    /** Si no es null, solo se dibuja esta capa. */
    public static @Nullable String solo;
    /** aura_type del aura "fantasma" de comparación a tu derecha; null = sin comparación. */
    public static @Nullable String compareType;
    /** {@code /zenkaiaura transform}: reproducir la secuencia de transformación en el próximo frame. */
    public static boolean burstRequested;

    /** Líneas del overlay, rellenadas por el compositor en el frame del jugador local. */
    static final List<String> LINES = new ArrayList<>();

    /** ¿Se dibuja esta capa del aura propia? */
    static boolean layerOn(String layer) {
        if (solo != null) return solo.equals(layer);
        return !DISABLED.contains(layer);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!hud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int y = 4;
        for (String line : LINES) {
            g.drawString(mc.font, line, 4, y, 0xFFFFE080, true);
            y += 10;
        }
    }
}
