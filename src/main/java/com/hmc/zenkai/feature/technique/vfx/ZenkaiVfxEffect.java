package com.hmc.zenkai.feature.technique.vfx;

import com.hmc.zenkai.content.particle.ZenkaiParticleOptions;
import com.hmc.zenkai.registry.ModParticles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Efecto visual data-driven: qué partícula(s) + sonido dispara un impacto, cargado desde
 * datapack en vez de estar cableado en un {@code switch} de Java. Paso 1 del roadmap de
 * {@code .claude/pendiente/cobblemon-audit-vfx-animaciones.md} (§17/§20) — inspirado en el
 * {@code .particle.json} de Cobblemon, pero deliberadamente MUCHO más simple: sin
 * emisores/curvas/formas paramétricas (eso es Snowstorm, sobre-ingeniería para el volumen de
 * efectos que tiene Zenkai, ver §19 del informe). Ruta:
 * {@code data/<ns>/zenkai_vfx_effects/<...>/<id>.json}, cargado por {@link ZenkaiVfxRegistry}.
 *
 * Plantilla, no instancia: el color (tinte de aura o de la técnica) y la posición los decide
 * SIEMPRE quien llama a {@link #spawn}, nunca este registro — mismo principio que el "Base =
 * plantilla, no color final" de §12.Y.5 del informe. {@code contextSpreadXZ} es la única otra
 * entrada en tiempo de llamada: el radio de dispersión de las chispas depende del tamaño del
 * objetivo/técnica concreta (ver {@code PhysicalCombatServer.impactFx}, primer efecto migrado a
 * este formato), no es una propiedad fija del efecto.
 */
public record ZenkaiVfxEffect(List<ParticleSpec> particles, @Nullable SoundSpec sound,
                               @Nullable List<TintStop> tint, @Nullable String frames) {

    /** Una entrada de la lista {@code particles} del JSON. */
    public record ParticleSpec(Kind type, int count, float scale,
                                boolean spreadFromTarget, double spreadY, double speed) {
        public enum Kind { IMPACT, SPARK, ARC }
    }

    public record SoundSpec(ResourceLocation soundId, float volume, float pitch) {}

    /**
     * Reservado para el futuro (§12.Y.5 del informe), SIN USAR todavía por ningún efecto real:
     * gradiente de color sobre la vida de la partícula, para cuando aparezca la primera técnica
     * que de verdad necesite un tinte que cambie con el tiempo en vez de uno fijo por llamada.
     * {@code t} es la fracción de vida transcurrida (0 = nace, 1 = muere).
     */
    public record TintStop(float t, int rgb) {}

    /**
     * Dispara este efecto en {@code pos}, tintado en {@code rgb} (color de la técnica o del
     * aura del atacante, resuelto por quien llama — este registro nunca decide un color).
     * {@code contextSpreadXZ} solo se aplica a las partículas marcadas
     * {@code spread_from_target: true} en el JSON; las demás (normalmente el destello de
     * impacto, un único punto) ignoran ese valor.
     */
    public void spawn(ServerLevel level, Vec3 pos, int rgb, double contextSpreadXZ) {
        for (ParticleSpec p : particles) {
            ZenkaiParticleOptions opts = switch (p.type()) {
                case IMPACT -> ModParticles.impact(rgb, p.scale());
                case SPARK -> ModParticles.spark(rgb, p.scale());
                case ARC -> ModParticles.arc(rgb, p.scale());
            };
            double dxz = p.spreadFromTarget() ? contextSpreadXZ : 0.0;
            level.sendParticles(opts, pos.x, pos.y, pos.z, p.count(), dxz, p.spreadY(), dxz, p.speed());
        }
        if (sound != null) {
            SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(sound.soundId());
            if (event != null) {
                level.playSound(null, pos.x, pos.y, pos.z, event, SoundSource.PLAYERS,
                        sound.volume(), sound.pitch());
            }
        }
    }
}
