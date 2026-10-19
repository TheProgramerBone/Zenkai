package com.hmc.zenkai.content.entity;

/**
 * Marcador: la entidad no admite ningún efecto de poción (veneno, wither, levitación...).
 * Lo aplica {@code EffectImmunityHook} vía {@code MobEffectEvent.Applicable}, porque sobrescribir
 * {@code LivingEntity.canBeAffected} está deprecado en NeoForge (ese evento es su sustituto).
 */
public interface EffectImmune {}
