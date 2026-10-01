package com.hmc.zenkai.client.aura.compose;

/**
 * Qué GEOMETRÍA y qué SHADER dibuja el cuerpo de una familia de aura. No es un parámetro más: cada
 * valor es un programa distinto con otra deformación de la malla y otro lenguaje de color (ver el
 * comentario de cabecera de cada .vsh). Es lo que hace que dos familias con el mismo poder se lean
 * como energías diferentes y no como la misma llama con otro color.
 * <p>
 * Todas comparten la malla ({@link AuraBodyMesh}, esfera unitaria en VBO) y el contrato de
 * uniforms de {@link AuraBodyUniforms}; lo que cambia es cómo la deforma y la pinta cada shader.
 */
public enum AuraBodyKind {
    /** aura_flame_shell: la llama de vela confirmada el 2026-09-30 — muchas púas triangulares que
     *  trepan, cel de 4 bandas duras. SSJ, golden, arcosiano. */
    FLAME,
    /** aura_body_soft: pocos lóbulos redondos que se mecen y por los que suben abultamientos
     *  lentos, bandas suavizadas e interior de nube. Energía divina y controlada (SSG, SSB). */
    SOFT,
    /** aura_body_dense: masa sin púas movida por ruido de baja frecuencia (se retuerce), interior
     *  profundo y borde fino. Energía oscura y pesada (Ultra Ego, majin malvado). */
    DENSE,
    /** aura_body_flow: NO usa la esfera, sino seis cápsulas colocadas sobre la pose real del cuerpo
     *  ({@link AuraAnchor}) — energía CEÑIDA al contorno que fluye hacia arriba, con motas de luz y
     *  borde plateado (Ultra Instinct, aura_6/aura_12/aura_14). También el contorno fino del
     *  componente de borde (rim). */
    FLOW
}
