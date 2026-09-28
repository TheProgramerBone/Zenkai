package com.hmc.zenkai.feature.technique.sequence;

import java.util.concurrent.CompletableFuture;

/**
 * Un paso de {@link TechniqueSequence}. Análogo reducido de {@code ActionEffectKeyframe} de
 * Cobblemon (§4.1 del informe): el future SOLO se completa cuando el paso termina de verdad,
 * nunca antes — un paso instantáneo (p. ej. {@code VfxStep}) devuelve uno ya completado; uno que
 * tarda (p. ej. {@code PauseStep}) lo completa más tarde, desde el tick del servidor.
 * {@link TechniqueSequence#run} encadena estos futures con {@code thenCompose}, así que un paso
 * NUNCA arranca antes de que el anterior haya terminado — es justo lo que hace
 * {@code CompletableFuture.thenRun(...).thenApply(...)} en Cobblemon, sin árbol de callbacks.
 */
public interface SequenceStep {
    CompletableFuture<Void> play(SequenceContext ctx);
}
