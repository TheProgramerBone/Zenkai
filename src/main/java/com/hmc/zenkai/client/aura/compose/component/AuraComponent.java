package com.hmc.zenkai.client.aura.compose.component;

import com.hmc.zenkai.client.aura.compose.AuraFrame;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Una CAPA SECUNDARIA del aura compuesta (motes, y en rondas siguientes electricidad, wisps,
 * contorno, suelo...). Cada jugador tiene sus propias instancias, creadas desde la configuración
 * ya parseada del estilo ({@link AuraComponentConfig#create()}) y recreadas si el estilo cambia.
 * <p>
 * Dos fases por frame, en este orden y fuera de la tarea diferida:
 * <ol>
 *   <li>{@link #update}: simulación (nacer, moverse, morir). Aquí SÍ hay estado.</li>
 *   <li>{@link #submit}: encola su dibujado en {@code KiVfxFrameQueue.submitAura}. La tarea se
 *       reproduce DOS veces (mundo y bloom): dentro solo se lee, nunca se muta.</li>
 * </ol>
 */
public interface AuraComponent {

    void update(AuraFrame f);

    /** @param pose PoseStack del stage, en espacio relativo a la cámara y sin trasladar al jugador. */
    void submit(AuraFrame f, PoseStack pose);

    /** true mientras le quede algo vivo (motes que terminan su vida aunque el aura ya se apagó). */
    boolean alive();

    /** Id del tipo, para el overlay de debug y {@code /zenkaiaura layer}. */
    String type();

    /**
     * Estallido de una transformación (TransformFx): el componente puede soltar una tanda extra
     * (motas, rayos). {@code strength} 0..1 según el tipo de transformación.
     */
    default void onBurst(AuraFrame f, float strength) { }
}
