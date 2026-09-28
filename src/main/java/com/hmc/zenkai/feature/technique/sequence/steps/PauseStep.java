package com.hmc.zenkai.feature.technique.sequence.steps;

import com.hmc.zenkai.feature.technique.sequence.SequenceContext;
import com.hmc.zenkai.feature.technique.sequence.SequenceScheduler;
import com.hmc.zenkai.feature.technique.sequence.SequenceStep;

import java.util.concurrent.CompletableFuture;

/** Espera N ticks de servidor antes de dejar pasar al siguiente paso. */
public record PauseStep(int ticks) implements SequenceStep {
    @Override
    public CompletableFuture<Void> play(SequenceContext ctx) {
        return SequenceScheduler.after(ticks);
    }
}
