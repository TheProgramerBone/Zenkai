package com.hmc.zenkai.feature.technique.sequence;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Lista ordenada de {@link SequenceStep}, análoga reducida de {@code ActionEffectTimeline} de
 * Cobblemon (§4.2 del informe) — sin holds/providers genéricos, sin MoLang: solo la cadena de
 * pasos. Paso 3 del roadmap de {@code .claude/pendiente/cobblemon-audit-vfx-animaciones.md}
 * (§20): ANDAMIAJE, probado por ahora con un caso sintético ("/zenkai debug sequence"), sin
 * ninguna técnica de combate real enganchada todavía — ver el javadoc de
 * {@code ModCommands.debugRunSequence} para por qué (ninguna técnica actual necesita esperar al
 * servidor entre fases; hace falta una mecánica nueva —clash de haces, Genki Dama con ayuda
 * real— para que esto deje de ser un ejercicio de andamiaje).
 *
 * {@code KiFirePacket}/{@code TechniqueDef}/{@code PhysicalCombatServer} NO llaman a esto: es
 * una capa opcional y aditiva (§15 del informe), no un reemplazo del disparo instantáneo actual.
 */
public record TechniqueSequence(List<SequenceStep> steps) {

    public CompletableFuture<Void> run(SequenceContext ctx) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (SequenceStep step : steps) {
            chain = chain.thenCompose(ignored -> step.play(ctx));
        }
        return chain;
    }
}
