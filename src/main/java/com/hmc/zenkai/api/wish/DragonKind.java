package com.hmc.zenkai.api.wish;

/**
 * Qué dragón concede el deseo. Hoy los dos usan la entidad de Shenlong (Porunga no tiene modelo propio y el
 * bloque de Namek invoca a Shenlong), pero el TIPO ya se distingue: lo fija el bloque que invoca
 * ({@code AllDragonBallsBlock#dragonKind}) y viaja con la entidad, de modo que un deseo registrado solo para
 * {@link #PORUNGA} aparece únicamente al invocar con las esferas de Namek, y cuando Porunga tenga su propia
 * entidad no hará falta tocar ningún addon.
 */
public enum DragonKind {
    /** Esferas de la Tierra. */
    SHENLONG,
    /** Esferas de Namek. */
    PORUNGA
}
