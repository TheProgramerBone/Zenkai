package com.hmc.zenkai.feature.technique;

/**
 * Tipos que IMPONEN su animación en vez de dejar que el jugador elija un set.
 * POR QUÉ EXISTE
 * --------------
 * Una barrera no se puede lanzar con la pose del Kamehameha, y una autodetonación tampoco.
 * Hasta ahora esto se resolvía con el número mágico `visual == 0` para la barrera, repetido en
 * ClientZenkaiPalTick, TechniqueAnimPreview y el javadoc de TechniqueAnimSet. Al llegar la
 * segunda excepción, el número mágico deja de escalar.
 * CÓMO VIAJA
 * ----------
 * El campo `visual` de ActionState y del packet de carga sigue siendo un int:
 *   > 0  -> número de set de animación (1..N)
 *   <= 0 -> anulación: encode()/decode() de aquí
 * ActionState es estado de RUNTIME, no se guarda en NBT, así que redefinir la codificación no
 * rompe nada guardado.
 */
public enum TechniqueAnimOverride {
    BARRIER,
    EXPLOSION;

    /** Valor de `visual` que representa esta anulación. */
    public int encode() { return -(ordinal() + 1); }

    /** null si el valor es un set normal (> 0). */
    public static TechniqueAnimOverride decode(int visual) {
        if (visual > 0) return null;
        int i = -visual - 1;
        TechniqueAnimOverride[] all = values();
        return (i < 0 || i >= all.length) ? null : all[i];
    }
}