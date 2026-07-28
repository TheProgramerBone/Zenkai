package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

/**
 * Lo que un proyectil infusionado se lleva puesto desde que sale disparado.
 *  - bonusDamage: daño extra que suma al impactar. Se congela en el LANZAMIENTO, no se
 *    recalcula al impactar: el ki ya se pagó, así que cambiar de nivel, de forma o de % de
 *    poder mientras la flecha vuela no debe cambiar lo que hace al llegar.
 *  - refPower: potencia de ki del tirador en ese momento. Es la referencia contra la que se
 *    escala la DEFENSA del objetivo, igual que hace KiProjectileEntity con su kiPower. Sin
 *    esto, una flecha infusionada chocaría contra la defensa completa y contra un enemigo
 *    duro la infusión no se notaría en absoluto.
 *
 * Viaja como attachment de la ENTIDAD proyectil, no como campo de una clase propia, porque
 * el datapack puede marcar proyectiles ajenos (tridente, bola de nieve, los de otro mod) que
 * no podemos reemplazar por una clase nuestra.
 */
public record KiInfusedShot(double bonusDamage, double refPower) {

    /** Proyectil sin infusionar. Es el default del attachment: nunca hay que comprobar null. */
    public static final KiInfusedShot NONE = new KiInfusedShot(0.0, 0.0);

    public static final Codec<KiInfusedShot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("bonus").forGetter(KiInfusedShot::bonusDamage),
            Codec.DOUBLE.fieldOf("ref").forGetter(KiInfusedShot::refPower)
    ).apply(i, KiInfusedShot::new));

    public boolean isInfused() { return bonusDamage > 0.0; }

    /** Lectura segura desde cualquier entidad. NONE si no lleva nada. */
    public static KiInfusedShot of(Entity e) {
        if (e == null) return NONE;
        // ⚠ API a verificar al compilar: getExistingData devuelve Optional en NeoForge 1.21.1.
        return e.getExistingData(ZenkaiDataAttachments.KI_SHOT.get()).orElse(NONE);
    }
}