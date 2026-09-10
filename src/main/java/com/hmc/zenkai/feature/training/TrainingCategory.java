package com.hmc.zenkai.feature.training;

/**
 * Las fatigas de entrenamiento NO se comparten entre categorías (pedido explícito del usuario,
 * 2026-09-09) — antes había UN solo contador de fatiga (`TrainingData.fatigue`) que alimentaban
 * TODAS las fuentes de TP por igual, así que hacer 200 golpes al aire dejaba tu eficiencia de
 * Meditation por los suelos aunque nunca hubieras tocado ese minijuego, y viceversa. Cada
 * categoría de aquí tiene su PROPIO par fatiga/carry en {@link TrainingData} y su propio decay —
 * entrenar una no penaliza ni beneficia a las otras.
 *
 * Reparto de fuentes por categoría (qué cuenta para cuál — ver TrainingHooks):
 *  - COMBAT: daño efectivo infligido (sparring, mobs, la sombra de "Train with your shadow"),
 *    golpes al aire con mano vacía, y matar entidades. Las cuatro son variantes de "pegar de
 *    verdad" y comparten fórmula (rawTp escalado por tu propio PL o por daño real), así que
 *    comparten fatiga — separarlas más no tendría un motivo real, ninguna es un minijuego
 *    distinto con su propia GUI.
 *  - MEDITATION: solo `MeditationSessionPacket` (el minijuego de ritmo).
 *  - TARGET_PRACTICE: solo `TargetPracticeSessionPacket` (el minijuego de orbes).
 *
 * Qué entrenamiento NO cuenta para TP en absoluto (aclarado aquí para que quede explícito, no
 * porque haya cambiado con esta sesión): golpear a otro jugador CON `PartyService.
 * friendlyFireBlocked` activo, cualquier daño que `CombatZenkaiHooks` descarte antes de llegar a
 * `grantTraining` (ambiental, /kill, etc. — ver su propio javadoc), y por supuesto cualquier
 * "entrenamiento" fuera de las rutas de arriba (no hay una vía genérica "gana TP haciendo X" sin
 * pasar por una de las cinco fuentes ya listadas).
 */
public enum TrainingCategory {
    COMBAT,
    MEDITATION,
    TARGET_PRACTICE,
}
