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
    /**
     * Prueba de DIRECCIÓN DE ARTE (2026-10-26, {@code /zenkaiaura look}, ver
     * .claude/pendiente/aura-direccion-de-arte.md): a diferencia del resto, vale para CUALQUIER aura
     * compuesta, no solo la propia (es un uniform global de los cuatro programas de cuerpo).
     * {@code lookFps} = fotogramas por segundo del movimiento a saltos (0 = continuo);
     * {@code lookBands3} = tres bandas duras (borde, cuerpo, núcleo); {@code lookWhiteCap} = blanco
     * máximo del borde, con saturación conservada (0 = el de siempre). Por DEFECTO está el look APROBADO por
     * el usuario el 2026-10-29 (8 fps, 3 bandas, tope 0,10); {@code /zenkaiaura look reset} vuelve a él.
     */
    public static float lookFps = 8f;
    public static boolean lookBands3 = true;
    public static float lookWhiteCap = 0.10f;
    /** Relleno de la parte alta de la llama (0..1, 0 = solo fresnel): aura_4/aura_11 son densas arriba. */
    public static float lookTopFill;
    /** Calibración contra referencias (AuraShots modo tune): valores del CUERPO que se imponen después
     *  del estilo y la familia, para cualquier aura compuesta. Vacío = nada. Valores dimensionales en
     *  bloques SIN la escala por poder (la pone {@link AuraBodyParams#set}). */
    public static final EnumMap<AuraBodyParams.Key, Float> BODY_OVERRIDES = new EnumMap<>(AuraBodyParams.Key.class);
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
