package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * Claves de los {@link DamageType} propios del mod. El contenido de cada uno (message_id,
 * exhaustion, scaling) vive en datapack ({@code data/zenkai/damage_type/*.json}) — esta clase
 * solo referencia la clave, exactamente igual que {@code net.minecraft.world.damagesource.
 * DamageTypes} hace con los de vanilla. No hay registro en código: los tipos de daño son un
 * registro data-driven, así que basta con que el JSON exista para que
 * {@code damageSources().source(KEY, ...)} funcione.
 *
 * Existen para que la muerte de un jugador cuente CÓMO murió (un ataque de ki, una explosión,
 * una técnica física concreta...) en vez de caer siempre en el genérico "murió" — ver
 * {@link com.hmc.zenkai.feature.combat.DeathCauseTracker} para por qué hace falta un paso
 * intermedio antes de que el mensaje llegue a {@code LivingEntity.die()}.
 */
public final class ModDamageTypes {
    private ModDamageTypes() {}

    // ── Ki ───────────────────────────────────────────────────────────────────
    /** Impacto directo de un proyectil ki de tipo BLAST (el genérico) — y respaldo de
     *  {@link #forKiHit} para cualquier tipo sin entrada propia (BARRIER/EXPLOSION no llegan
     *  aquí: ni impactan ni pasan por onHitEntity). */
    public static final ResourceKey<DamageType> KI_BLAST = key("ki_blast");
    /** Impacto directo de DISK, WAVE, LAZER, SPIRAL, BIG_BLAST y BURST — un tipo, un DamageType,
     *  para que el mensaje de muerte nombre la técnica y no solo "un ataque de ki" genérico.
     *  Seleccionados por {@link #forKiHit}. */
    public static final ResourceKey<DamageType> KI_DISK = key("ki_disk");
    public static final ResourceKey<DamageType> KI_WAVE = key("ki_wave");
    public static final ResourceKey<DamageType> KI_LAZER = key("ki_lazer");
    public static final ResourceKey<DamageType> KI_SPIRAL = key("ki_spiral");
    public static final ResourceKey<DamageType> KI_BIG_BLAST = key("ki_big_blast");
    public static final ResourceKey<DamageType> KI_BURST = key("ki_burst");
    /** Daño en área de la detonación de una técnica ki (KiProjectileEntity.detonate), con o sin
     *  destrucción de bloques — las dos ramas de detonate() usan esta misma clave, así que una
     *  explosión de ki da el mismo mensaje rompa o no bloques. */
    public static final ResourceKey<DamageType> KI_EXPLOSION = key("ki_explosion");
    /** Zona persistente del efecto LINGERING (KiLingeringZoneEntity). */
    public static final ResourceKey<DamageType> KI_FIRE = key("ki_fire");
    /** Autodetonación al soltar una técnica EXPLOSION a carga máxima (KiFirePacket.selfDamage). */
    public static final ResourceKey<DamageType> KI_SELF_DESTRUCT = key("ki_self_destruct");

    // ── Técnicas físicas ────────────────────────────────────────────────────
    public static final ResourceKey<DamageType> DASH_PUNCH = key("dash_punch");
    public static final ResourceKey<DamageType> HEAVY_BLOW = key("heavy_blow");
    public static final ResourceKey<DamageType> BARRAGE = key("barrage");
    public static final ResourceKey<DamageType> KIAI = key("kiai");

    // ── Combate cuerpo a cuerpo ─────────────────────────────────────────────
    /** Proc de Black Flash (feature.combat.BlackFlash) sobre un golpe de Ki Fist. El golpe base
     *  sigue siendo el playerAttack de vanilla — esta clave solo se usa para ATRIBUIR la muerte
     *  cuando el golpe que la causó fue justo el que tuvo el proc, ver
     *  CombatZenkaiHooks.onDamage/BlackFlash.consumeProc. */
    public static final ResourceKey<DamageType> BLACK_FLASH = key("black_flash");

    /** ¿Qué DamageType usa el impacto DIRECTO de esta técnica ki? BARRIER y EXPLOSION no
     *  aparecen aquí porque nunca llegan a KiProjectileEntity.onHitEntity (no viajan, no
     *  impactan). BLAST es el respaldo para cualquier tipo sin entrada propia. */
    public static ResourceKey<DamageType> forKiHit(KiTechniqueType type) {
        return switch (type) {
            case DISK -> KI_DISK;
            case WAVE -> KI_WAVE;
            case LAZER -> KI_LAZER;
            case SPIRAL -> KI_SPIRAL;
            case BIG_BLAST -> KI_BIG_BLAST;
            case BURST -> KI_BURST;
            default -> KI_BLAST;
        };
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path));
    }
}
