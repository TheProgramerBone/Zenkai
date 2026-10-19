package com.hmc.zenkai.feature.race.hair.strand;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Un peinado como lista de mechones (modelo común de los códigos de DragonMineZ y Dragon Block C).
 *
 * @param name nombre que trae el código (DMZ guarda uno), o null.
 */
public record StrandSet(@Nullable String name, List<Strand> strands) {

    public StrandSet {
        strands = List.copyOf(strands);
    }

    public int cubeCount() {
        int n = 0;
        for (Strand s : strands) n += s.segments().size();
        return n;
    }

    public boolean isEmpty() {
        return cubeCount() == 0;
    }

    /**
     * Juego de peinados por forma. DMZ guarda hasta 5 (base, SSJ, SSJ2, SSJ3 y, desde su formato
     * v2.2, SSJ4); DBC guarda uno y las variantes SSJ/SSJ2 se HORNEAN deformándolo (DbcHairCodec.deform).
     */
    public record Variants(StrandSet base, @Nullable StrandSet ssj, @Nullable StrandSet ssj2,
                           @Nullable StrandSet ssj3, @Nullable StrandSet ssj4) {

        public Variants(StrandSet base, @Nullable StrandSet ssj, @Nullable StrandSet ssj2, @Nullable StrandSet ssj3) {
            this(base, ssj, ssj2, ssj3, null);
        }

        public static Variants single(StrandSet base) {
            return new Variants(base, null, null, null, null);
        }

        public boolean hasForms() {
            return ssj != null || ssj2 != null || ssj3 != null || ssj4 != null;
        }
    }
}
