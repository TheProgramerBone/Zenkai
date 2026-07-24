package com.hmc.zenkai.client.gui.wheel;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Nodo del menú radial. El árbol es RECURSIVO y genérico a propósito: la rueda no sabe qué
 * es una forma o un kaioken, solo pinta anillos por profundidad. Reorganizar el menú
 * (mover Kaioken a un submenú "Toggles", añadir Ki Fist...) es tocar WheelMenu, no la GUI.
 * - kind/value: lo que se manda al servidor si es hoja. value es la carga útil ya en texto
 *   (id de forma, u ordinal del escalón) para que el packet sea uno solo.
 * - enabled: se dibuja pero no se puede elegir (forma sin comprar, escalón por encima de
 *   tu nivel). Mostrar lo que te falta es información, ocultarlo no.
 * - active: es el estado actual del jugador -> se resalta.
 */
public record WheelNode(Kind kind, String value, Component label, int color,
                        boolean enabled, boolean active, List<WheelNode> children) {

    public enum Kind { CATEGORY, FORM, KAIOKEN }

    public boolean isLeaf() { return children.isEmpty(); }

    public static WheelNode category(Component label, int color, List<WheelNode> children) {
        return new WheelNode(Kind.CATEGORY, "", label, color, !children.isEmpty(), false, children);
    }

    public static WheelNode leaf(Kind kind, String value, Component label,
                                 int color, boolean enabled, boolean active) {
        return new WheelNode(kind, value, label, color, enabled, active, List.of());
    }
}