package com.hmc.zenkai.feature.technique.sequence.steps;

import com.hmc.zenkai.feature.technique.sequence.SequenceContext;
import com.hmc.zenkai.feature.technique.sequence.SequenceScheduler;
import com.hmc.zenkai.feature.technique.sequence.SequenceStep;

import java.util.concurrent.CompletableFuture;

/**
 * Modela LA FORMA de un paso de animación (algo se dispara, la secuencia espera N ticks antes
 * de dejar pasar al siguiente paso) — SIN un backend genérico real detrás todavía. A diferencia
 * de {@code VfxStep} (que sí llama directo a un sistema data-driven ya construido), Zenkai NO
 * tiene un packet de "reproduce el clip X por nombre": las animaciones PAL se disparan por
 * ESTADO sincronizado propio de cada feature (ver {@code InstantTransmissionAnimPacket},
 * {@code KiChargeStatePacket}, {@code ActionState}/{@code TechniqueAnimSet} — cada una decide su
 * propio clip a partir de ese estado, ninguna acepta un nombre de clip suelto).
 *
 * Por eso este step recibe {@code onStart} en vez de un nombre de clip: la técnica REAL que
 * algún día use esto tendrá que aportar SU propio mecanismo de disparo existente (poner el
 * ActionState que ya tiene, mandar el packet que ya tiene...), y este step solo orquesta CUÁNDO
 * se llama y cuánto se espera después — igual que {@code AnimationActionEffectKeyframe} de
 * Cobblemon orquesta CUÁNDO sin decidir CÓMO se anima cada Pokémon. Fingir un backend genérico
 * de "clip por nombre" que no existe habría sido peor que dejarlo así de explícito.
 * Ver {@code .claude/pendiente/cobblemon-audit-vfx-animaciones.md} §20 paso 3.
 */
public record AnimationStep(Runnable onStart, int waitTicksAfter) implements SequenceStep {
    @Override
    public CompletableFuture<Void> play(SequenceContext ctx) {
        onStart.run();
        return SequenceScheduler.after(waitTicksAfter);
    }
}
