package com.hmc.zenkai.feature.sideconfig;

import com.hmc.zenkai.feature.transport.SideMode;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Un block entity con PUERTOS por cara configurables (0.7.0): tanques, cables, tuberías y el Motor de
 * Ki. Es lo único que necesitan la llave (WrenchItem, clic en la cara), su sombreado (WrenchOverlay), la
 * cruz de la pantalla del motor (SideConfigWidget) y {@link SideConfigPacket}.
 *
 * Interfaz SIN clases de Create a propósito: el motor la implementa y el packet, que se registra en
 * todos los entornos, solo nombra esto. Cada bloque guarda sus modos como ya los guardaba; aquí solo
 * se expone la lectura y la escritura con el vocabulario común ({@link SideMode}).
 */
public interface SideConfigurable {

    /** Qué se configura: un bloque puede tener uno (tanque) o los dos (motor). Orden = pestañas. */
    enum Channel {
        ENERGY, FLUID;
        public String langKey() { return "screen.zenkai.side_config.channel." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    List<Channel> sideChannels();

    SideMode sideMode(Channel channel, Direction face);

    /** Solo servidor. Aplica el modo con los mismos efectos que el clic de siempre (capabilities, red, aspecto). */
    void setSideMode(Channel channel, Direction face, SideMode mode);

    /** Cara sin puertos (el eje del motor): se pinta bloqueada y no se puede cambiar. */
    default boolean sideLocked(Direction face) { return false; }

    /**
     * ¿Hace algo este modo en este canal con el estado actual del bloque? Solo para AVISAR en la
     * pantalla (se pinta apagado), nunca para decidir: lo decide el propio bloque al mover cosas.
     */
    default boolean sideModeUseful(Channel channel, SideMode mode) { return true; }
}
