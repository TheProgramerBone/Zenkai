package com.hmc.zenkai.feature.technique.sequence.steps;

import com.hmc.zenkai.feature.technique.sequence.SequenceContext;
import com.hmc.zenkai.feature.technique.sequence.SequenceStep;
import com.hmc.zenkai.feature.technique.vfx.ZenkaiVfxEffect;
import com.hmc.zenkai.feature.technique.vfx.ZenkaiVfxRegistry;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/** Dispara un {@link ZenkaiVfxEffect} data-driven (paso 1 del roadmap) en {@code SequenceContext.origin()}.
 *  Instantáneo: el future ya llega completado, un VfxStep nunca hace esperar al siguiente paso. */
public record VfxStep(ResourceLocation effectId, double spreadXZ) implements SequenceStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Sequence");

    @Override
    public CompletableFuture<Void> play(SequenceContext ctx) {
        ZenkaiVfxEffect fx = ZenkaiVfxRegistry.get(effectId);
        if (fx == null) {
            LOGGER.warn("[Zenkai] VfxStep: sin efecto '{}' (datapack roto/borrado).", effectId);
        } else {
            fx.spawn(ctx.level(), ctx.origin(), ctx.rgb(), spreadXZ);
        }
        return CompletableFuture.completedFuture(null);
    }
}
