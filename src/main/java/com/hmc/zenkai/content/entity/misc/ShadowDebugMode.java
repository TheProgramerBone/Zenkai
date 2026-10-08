package com.hmc.zenkai.content.entity.misc;

import java.util.Locale;

/**
 * Guion del muñeco de pruebas ("/zenkai debug dummy"): la ÚNICA cosa que hace una Sombra
 * mientras lo tiene puesto. Existe para probar sistemas de combate (choque de haces, guardia,
 * parry...) sin esperar a que la IA normal decida atacar.
 *  - IDLE: quieta, mirándote. Sirve de blanco.
 *  - SPAM_KI: dispara sin parar la técnica elegida, cada N ticks.
 *  - BLOCK: guardia fija permanente.
 *  - MELEE: solo cuerpo a cuerpo (el goal normal, con el resto apagado).
 *  - PHYSICAL: solo técnicas físicas de su ficha (el goal normal).
 *  - FOLLOW: te sigue sin atacar.
 *  - FLEE: se aleja manteniendo distancia.
 */
public enum ShadowDebugMode {
    IDLE, SPAM_KI, BLOCK, MELEE, PHYSICAL, FOLLOW, FLEE;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** @return el modo con ese id, o null. */
    public static ShadowDebugMode byId(String id) {
        for (ShadowDebugMode m : values()) if (m.id().equals(id)) return m;
        return null;
    }

    /** Los modos que ejecuta el ShadowScriptGoal (el resto reutiliza los goals normales). */
    public boolean scripted() {
        return this == IDLE || this == SPAM_KI || this == BLOCK || this == FOLLOW || this == FLEE;
    }
}
