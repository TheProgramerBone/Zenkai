package com.hmc.zenkai.client.gui;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Bloquea el GUI Scale REAL de Minecraft a 2 (el valor con el que se diseñó y probó el conjunto
 * de la GUI del mod) mientras cualquier pantalla propia esté abierta, y lo devuelve al del
 * jugador en cuanto se cierra la última.
 *
 * POR QUÉ ESTO Y NO ENCOGER/ESCALAR EL DIBUJADO (2026-09-27, tres intentos previos fallidos):
 * primero se intentó encoger el panel (PanelSizing) para que cupiera en cualquier GUI Scale —
 * arreglaba el marco, pero decenas de coordenadas de contenido en cada pantalla (texto, barras,
 * filas, hit-tests de clic escritos a mano) asumen un lienzo fijo a GUI Scale 2 y no derivan de
 * ese tamaño, así que el contenido se desalineaba del marco encogido. Escalar solo el DIBUJADO
 * (una transformación de PoseStack) tampoco basta: los clics del ratón llegan en el espacio de
 * coordenadas REAL del GUI Scale actual, y habría que reescribir el mouseClicked/mouseScrolled a
 * mano de cada una de las ~20 pantallas del mod para traducirlos — justo el trabajo disperso que
 * se quería evitar. Forzar el GUI Scale REAL del juego a 2 hace que el propio Minecraft resuelva
 * dibujado Y coordenadas de ratón de forma consistente, sin tocar ni una sola pantalla: es la
 * única solución que cubre cualquier pantalla del mod (incluidas RaceSelectionScreen/
 * StyleSelectionScreen/AppearanceScreen/HeadAppearanceScreen/BodyColorsScreen, que nunca
 * llegaron a tocarse con los intentos anteriores) sin reescribir ninguna.
 *
 * ÁMBITO: cualquier {@link Screen} cuyo paquete empiece por {@code com.hmc.zenkai.client.gui}
 * (cubre {@code .screens}, {@code .screens.wishes}, {@code .wheel} y cualquier futuro subpaquete
 * de pantallas) — ninguna pantalla vanilla ni de otro mod se ve afectada.
 *
 * SIN PARPADEO al cambiar de pestaña (Zenkai → Zenkai): {@link ScreenEvent.Opening} siempre se
 * dispara ANTES que {@link ScreenEvent.Closing} para la misma transición (ver el patch de
 * Minecraft.setScreen en neoforge-userdev), así que si Closing deshiciera sin más lo que Opening
 * acaba de decidir, cada cambio de pestaña desbloquearía y jamás volvería a bloquear hasta la
 * siguiente apertura real. `handledByOpening` hace que Closing sea un no-op cuando Opening ya
 * resolvió esta misma transición — Closing solo actúa de verdad cuando se cierra la ÚLTIMA
 * pantalla (vuelta al juego sin pantalla nueva), el único caso en el que Opening no se dispara.
 *
 * BUG REAL (2026-09-27): `handledByOpening` se ponía en `true` sin condición en cada Opening,
 * pero Closing SOLO se dispara cuando había pantalla previa (`old != null`, ver el mismo patch).
 * Al abrir la PRIMERA pantalla de una sesión (V sin nada abierto: `current == null`) no hay
 * ningún Closing emparejado que vaya a consumir ese flag, así que se quedaba en `true` — y el
 * próximo Closing de verdad (salir con Escape) se descartaba por error, dejando el GUI Scale
 * bloqueado hasta que abrir cualquier OTRA pantalla (con `current != null`) reseteaba el flag de
 * rebote. `handledByOpening` ahora solo se marca cuando Closing va a dispararse de verdad para
 * esta misma transición (`current != null && next != current`), calcado del patch.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiGuiScaleLock {
    private ZenkaiGuiScaleLock() {}

    private static final int LOCKED_SCALE = 2;
    private static final String OWNED_PACKAGE_PREFIX = "com.hmc.zenkai.client.gui";

    private static boolean locked = false;
    private static int savedScale = -1;
    private static boolean handledByOpening = false;

    private static boolean isOurs(@Nullable Screen screen) {
        if (screen == null) return false;
        String pkg = screen.getClass().getPackageName();
        return pkg.equals(OWNED_PACKAGE_PREFIX) || pkg.startsWith(OWNED_PACKAGE_PREFIX + ".");
    }

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        Screen current = event.getCurrentScreen();
        Screen next = event.getNewScreen();
        // Closing solo se dispara cuando había una pantalla previa (ver el patch de
        // Minecraft.setScreen: `if (old != null && p_91153_ != old)`). Marcar esto en `true`
        // incondicionalmente aquí era el bug real: al abrir la PRIMERA pantalla de una sesión
        // (current == null, p.ej. pulsar V sin nada abierto) no hay Closing emparejado que vaya
        // a consumir el flag, así que se quedaba en `true` y el próximo Closing de verdad (el de
        // salir con Escape) se descartaba por error, dejando el GUI Scale bloqueado.
        handledByOpening = current != null && next != current;
        boolean newIsOurs = isOurs(next);
        if (newIsOurs && !locked) lock();
        else if (!newIsOurs && locked) unlock();
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (handledByOpening) {
            handledByOpening = false;
            return;
        }
        // Solo se llega aquí cuando NO hubo Opening para esta transición: se cierra la última
        // pantalla (Escape de vuelta al juego), no un salto a otra pantalla.
        if (locked) unlock();
    }

    private static void lock() {
        Minecraft mc = Minecraft.getInstance();
        savedScale = mc.options.guiScale().get();
        if (savedScale != LOCKED_SCALE) mc.options.guiScale().set(LOCKED_SCALE);
        locked = true;
    }

    private static void unlock() {
        Minecraft mc = Minecraft.getInstance();
        if (savedScale >= 0 && mc.options.guiScale().get() != savedScale) {
            mc.options.guiScale().set(savedScale);
        }
        locked = false;
        savedScale = -1;
    }
}
