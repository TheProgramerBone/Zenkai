package com.hmc.zenkai.feature.technique.sequence;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Cuanto un {@link SequenceStep} necesita para ejecutarse: dónde ({@code origin}), con qué
 * color ({@code rgb}, el mismo principio "plantilla, no color final" que {@code ZenkaiVfxEffect})
 * y, opcionalmente, quién la ejecuta ({@code executor} — un futuro {@code AnimationStep} real lo
 * necesita para disparar SU mecanismo de animación; hoy ningún step lo usa).
 * Análogo reducido de {@code ActionEffectContext} de Cobblemon (§4.2 del informe), sin
 * "providers"/holds genéricos: Zenkai no tiene el problema de resolver "quién es user/target
 * entre docenas de candidatos" que esos resuelven, aquí origin/executor ya vienen fijados.
 */
public record SequenceContext(ServerLevel level, Vec3 origin, int rgb, @Nullable ServerPlayer executor) {}
