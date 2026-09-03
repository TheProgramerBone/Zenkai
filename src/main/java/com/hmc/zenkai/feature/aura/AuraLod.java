package com.hmc.zenkai.feature.aura;

import java.util.ArrayList;
import java.util.List;

/**
 * Nivel de detalle por distancia.
 * ═══ EL LOD CAMBIA LA REPRESENTACIÓN, NUNCA EL ESTADO ═══
 * No toca AuraState, no consulta AuraCeiling, no recalcula presencia. Recibe un
 * AuraProfile ya resuelto y decide QUÉ GEOMETRÍA conservar y CUÁNTO COMPENSAR para que
 * la lectura se mantenga. Si algún día esta clase empieza a mirar el PL, el LOD se habrá
 * convertido en una segunda fuente de poder y dos jugadores distintos se verán iguales
 * a 30 bloques.
 * ═══ POR QUÉ HAY COMPENSACIÓN ═══
 * Quitar faldones sin más NO es neutral. Se midió la razón de energía luminosa entre un
 * novato (presencia 0.10) y un endgame (presencia 0.98) a tamaño aparente real en las
 * cuatro bandas:
 *   solo quitando faldones     4.21 → 3.87 → 3.55 → 3.16   deriva 25%
 *   con esta compensación      4.39 → 4.39 → 4.35 → 4.40   deriva  0.9%
 * Es decir: sin compensar, a 40 bloques el endgame se lee un 25% menos poderoso frente
 * al novato de lo que se lee a 4. El LOD estaría aplanando la escala de presencia.
 * ═══ QUÉ FALDONES SOBREVIVEN, Y POR QUÉ NO SON LOS OBVIOS ═══
 * Se probaron cuatro selecciones. Conservar el faldón BAJO (índice 0) parece lo
 * razonable — es el que rodea los pies — pero es justo el que rompe la proporción:
 * aporta casi lo mismo a un aura pequeña que a una grande, así que al mantenerlo el
 * novato conserva proporcionalmente más aura que el endgame. Los que sostienen la
 * relación de poder son los ALTOS. Ese fue el hallazgo de la medición y por eso la
 * banda MID descarta el 0 en vez del 3.
 * Nada de esto se ajusta "a ojo": si hay que retocarlo, se vuelve a medir la deriva.
 */
public enum AuraLod {

    /** 0–8 bloques. Los cinco faldones, wisps, chispas, suelo y distorsión. */
    NEAR(8f, 1.00f, 1.00f, true, true, true, true,
            new int[]{0, 1, 2, 3, 4},
            new float[]{1f, 1f, 1f, 1f, 1f}),

    /** 8–18 bloques. Se cae el faldón bajo. Sigue habiendo wisps y chispas. */
    MID(18f, 1.12f, 1.06f, true, true, true, false,
            new int[]{1, 2, 3, 4},
            new float[]{1f, 1f, 1f, 1f}),

    /** 18–32 bloques. Tres faldones aligerados. Se caen wisps y suelo; quedan las
     *  chispas porque son lo que sigue diciendo "ese está forzando algo". */
    FAR(32f, 1.34f, 1.37f, false, true, false, false,
            new int[]{1, 2, 3},
            new float[]{0.75f, 0.75f, 0.625f}),

    /** 32+ bloques. Firma energética: ya no se representa el aura, solo el hecho de que
     *  hay alguien ahí con una cantidad absurda de ki. Dos faldones y núcleo al doble. */
    SIGNATURE(Float.MAX_VALUE, 1.70f, 2.00f, false, false, false, false,
            new int[]{2, 3},
            new float[]{0.5f, 0.5f});

    private final float maxDistance;
    private final float alphaMul;
    private final float coreMul;
    private final boolean wisps;
    private final boolean sparks;
    private final boolean ground;
    private final boolean distortion;
    private final int[] skirtIndices;
    private final float[] countFactors;

    AuraLod(float maxDistance, float alphaMul, float coreMul,
            boolean wisps, boolean sparks, boolean ground, boolean distortion,
            int[] skirtIndices, float[] countFactors) {
        this.maxDistance = maxDistance;
        this.alphaMul = alphaMul;
        this.coreMul = coreMul;
        this.wisps = wisps;
        this.sparks = sparks;
        this.ground = ground;
        this.distortion = distortion;
        this.skirtIndices = skirtIndices;
        this.countFactors = countFactors;
    }

    public float maxDistance() { return maxDistance; }
    public float alphaMul()    { return alphaMul; }
    public float coreMul()     { return coreMul; }
    public boolean wisps()     { return wisps; }
    public boolean sparks()    { return sparks; }
    public boolean ground()    { return ground; }
    public boolean distortion(){ return distortion; }

    /** Banda por distancia al ojo. Se compara en cuadrado para no sacar raíces por
     *  jugador y por frame. */
    public static AuraLod byDistanceSq(double distanceSq) {
        for (AuraLod l : values()) {
            double d = l.maxDistance;
            if (distanceSq <= d * d) return l;
        }
        return SIGNATURE;
    }

    /**
     * Banda efectiva teniendo en cuenta la calidad configurada. {@code qualityFloor} es
     * la banda MÍNIMA que el cliente admite: con calidad baja se fuerza a bandas más
     * agresivas aunque el jugador esté cerca. Nunca al revés — bajar la calidad no puede
     * dar MÁS detalle.
     */
    public static AuraLod effective(double distanceSq, AuraLod qualityFloor) {
        AuraLod byDist = byDistanceSq(distanceSq);
        if (qualityFloor == null) return byDist;
        return byDist.ordinal() >= qualityFloor.ordinal() ? byDist : qualityFloor;
    }

    /** Faldones de esta banda, ya aligerados, a partir del conjunto base. */
    public List<AuraSkirt> skirts(AuraSkirt[] base) {
        List<AuraSkirt> out = new ArrayList<>(skirtIndices.length);
        for (int i = 0; i < skirtIndices.length; i++) {
            int idx = skirtIndices[i];
            if (idx < 0 || idx >= base.length) continue;
            out.add(base[idx].withCountFactor(countFactors[i]));
        }
        return out;
    }

    /**
     * Aplica la compensación al perfil. Solo se mueven alpha y core: la FORMA
     * (mass..density), el tamaño y las densidades de capa quedan intactos, porque
     * cambiarlos sería alterar la lectura en vez de conservarla.
     * Las capas apagadas se ponen a cero aquí en lugar de dejarlo al renderer: así el
     * renderer no tiene que consultar la banda para saber si dibuja wisps, le basta con
     * mirar si el valor es 0.
     */
    public AuraProfile compensate(AuraProfile p) {
        return new AuraProfile(
                p.mass(), p.spike(), p.turbulence(), p.spread(), p.height(), p.density(),
                p.pulseHz(), p.pulseAmp(), p.frameTicks(), p.groundSpin(),
                p.size(),
                p.alpha() * alphaMul,
                p.core() * coreMul,
                wisps ? p.wisps() : 0,
                sparks ? p.sparksPerSecond() : 0f,
                ground ? p.ground() : 0f,
                p.additiveGlow(), p.electricSparks(), p.fireEmbers());
    }

    /** Coste en quads del cono en esta banda. Para presupuestar y para depurar. */
    public int quadCost(AuraSkirt[] base) {
        int total = 0;
        for (AuraSkirt s : skirts(base)) total += s.quadCost();
        return total;
    }
}